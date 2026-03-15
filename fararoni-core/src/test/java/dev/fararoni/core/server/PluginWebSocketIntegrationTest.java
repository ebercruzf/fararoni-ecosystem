/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import dev.fararoni.core.gateway.OutputDispatcher;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PluginWebSocketIntegrationTest {
    private static final String AUTH_TOKEN = "test-secret-token-12345";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private SovereignEventBus bus;
    private PluginWebSocketBridge bridge;
    private OutputDispatcher dispatcher;
    private Javalin app;
    private int port;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
        bridge = new PluginWebSocketBridge(bus, AUTH_TOKEN);
        dispatcher = new OutputDispatcher(bus)
            .withPluginBridge(bridge);

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });

        app.ws("/api/bus", ws -> {
            ws.onConnect(bridge::onConnect);
            ws.onClose(bridge::onClose);
            ws.onMessage(bridge::onMessage);
        });

        app.start(0);
        port = app.port();

        dispatcher.activate();
    }

    @AfterEach
    void tearDown() {
        if (dispatcher != null) dispatcher.close();
        if (bridge != null) bridge.close();
        if (app != null) app.stop();
    }

    @Test
    @Order(1)
    @DisplayName("1.1 Conexión sin token debe ser rechazada")
    void gatekeeper_noToken_shouldReject() throws Exception {
        AtomicReference<Integer> closeCode = new AtomicReference<>();
        CountDownLatch closeLatch = new CountDownLatch(1);

        WebSocket ws = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(TIMEOUT)
            .buildAsync(
                URI.create("ws://localhost:" + port + "/api/bus?pluginId=test"),
                new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closeCode.set(statusCode);
                        closeLatch.countDown();
                        return null;
                    }
                }
            )
            .get(5, TimeUnit.SECONDS);

        boolean closed = closeLatch.await(3, TimeUnit.SECONDS);
        assertTrue(closed, "WebSocket debe cerrarse");
        assertEquals(4001, closeCode.get(), "Código de cierre debe ser 4001 (Unauthorized)");
    }

    @Test
    @Order(2)
    @DisplayName("1.2 Conexión con token inválido debe ser rechazada")
    void gatekeeper_invalidToken_shouldReject() throws Exception {
        AtomicReference<Integer> closeCode = new AtomicReference<>();
        CountDownLatch closeLatch = new CountDownLatch(1);

        WebSocket ws = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(TIMEOUT)
            .buildAsync(
                URI.create("ws://localhost:" + port + "/api/bus?pluginId=test&token=wrong-token"),
                new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closeCode.set(statusCode);
                        closeLatch.countDown();
                        return null;
                    }
                }
            )
            .get(5, TimeUnit.SECONDS);

        boolean closed = closeLatch.await(3, TimeUnit.SECONDS);
        assertTrue(closed, "WebSocket debe cerrarse");
        assertEquals(4001, closeCode.get(), "Token inválido debe resultar en código 4001");
    }

    @Test
    @Order(3)
    @DisplayName("1.3 Conexión con token válido debe ser aceptada")
    void gatekeeper_validToken_shouldAccept() throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        CountDownLatch connectLatch = new CountDownLatch(1);

        WebSocket ws = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(TIMEOUT)
            .buildAsync(
                URI.create("ws://localhost:" + port + "/api/bus?pluginId=vscode&token=" + AUTH_TOKEN),
                new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messages.add(data.toString());
                        connectLatch.countDown();
                        webSocket.request(1);
                        return null;
                    }
                }
            )
            .get(5, TimeUnit.SECONDS);

        boolean received = connectLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Debe recibir mensaje de bienvenida");

        String welcomeMsg = messages.get(0);
        JsonNode json = MAPPER.readTree(welcomeMsg);
        assertEquals("connected", json.get("type").asText());
        assertEquals("vscode", json.get("pluginId").asText());

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    @Test
    @Order(4)
    @DisplayName("2.1 Mensaje de plugin debe tener metadata origin_protocol=PLUGIN")
    void protocolIsolation_pluginMessage_hasCorrectMetadata() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(1);
        AtomicReference<SovereignEnvelope<String>> capturedEnvelope = new AtomicReference<>();

        bus.subscribe("sys.test", String.class, envelope -> {
            capturedEnvelope.set(envelope);
            messageLatch.countDown();
        });

        WebSocket ws = connectPlugin("test-plugin");

        String publishMsg = """
            {
                "action": "publish",
                "topic": "sys.test",
                "payload": "hola desde plugin",
                "metadata": {
                    "file": "/src/App.java"
                }
            }
            """;
        ws.sendText(publishMsg, true);

        boolean received = messageLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Bus debe recibir el mensaje");

        SovereignEnvelope<String> envelope = capturedEnvelope.get();
        assertNotNull(envelope);
        assertEquals("hola desde plugin", envelope.payload());
        assertEquals("PLUGIN", envelope.headers().get("origin_protocol"));
        assertEquals("test-plugin", envelope.userId());
        assertEquals("/src/App.java", envelope.headers().get("file"));

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    @Test
    @Order(5)
    @DisplayName("3.1 Plugin suscrito a sys.output debe recibir mensajes publicados")
    void loopback_subscribedPlugin_receivesMessages() throws Exception {
        List<String> receivedMessages = new CopyOnWriteArrayList<>();
        CountDownLatch msgLatch = new CountDownLatch(2);

        WebSocket ws = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(TIMEOUT)
            .buildAsync(
                URI.create("ws://localhost:" + port + "/api/bus?pluginId=listener&token=" + AUTH_TOKEN),
                new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(10);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        receivedMessages.add(data.toString());
                        msgLatch.countDown();
                        webSocket.request(1);
                        return null;
                    }
                }
            )
            .get(5, TimeUnit.SECONDS);

        Thread.sleep(200);

        String subscribeMsg = """
            {"action": "subscribe", "topic": "sys.output"}
            """;
        ws.sendText(subscribeMsg, true);
        Thread.sleep(200);

        SovereignEnvelope<String> systemEnvelope = SovereignEnvelope.create(
            "system", "SYSTEM", null, "Respuesta del sistema"
        );
        bus.publish("sys.output", systemEnvelope);

        boolean received = msgLatch.await(3, TimeUnit.SECONDS);
        assertTrue(receivedMessages.size() >= 1, "Plugin debe recibir mensajes");

        boolean hasWelcome = receivedMessages.stream()
            .anyMatch(m -> m.contains("\"type\":\"connected\""));
        assertTrue(hasWelcome, "Debe recibir mensaje de bienvenida");

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    @Test
    @Order(6)
    @DisplayName("4.1 Desconexión abrupta no debe crashear el servidor")
    void resilience_abruptDisconnect_serverSurvives() throws Exception {
        WebSocket plugin1 = connectPlugin("plugin-1");
        WebSocket plugin2 = connectPlugin("plugin-2");

        Thread.sleep(200);
        assertEquals(2, bridge.getConnectionCount(), "Deben haber 2 plugins conectados");

        plugin1.abort();

        Thread.sleep(500);

        assertTrue(app.jettyServer().server().isRunning(), "Servidor debe seguir corriendo");

        CountDownLatch ackLatch = new CountDownLatch(1);

        WebSocket plugin3 = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(TIMEOUT)
            .buildAsync(
                URI.create("ws://localhost:" + port + "/api/bus?pluginId=plugin-3&token=" + AUTH_TOKEN),
                new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(10);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        if (data.toString().contains("pong")) {
                            ackLatch.countDown();
                        }
                        webSocket.request(1);
                        return null;
                    }
                }
            )
            .get(5, TimeUnit.SECONDS);

        Thread.sleep(200);
        plugin3.sendText("{\"action\": \"ping\"}", true);

        boolean pongReceived = ackLatch.await(3, TimeUnit.SECONDS);
        assertTrue(pongReceived, "Servidor debe responder a ping después de desconexión abrupta");

        plugin2.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        plugin3.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    @Test
    @Order(7)
    @DisplayName("4.2 Publicar a plugin desconectado no debe generar excepción")
    void resilience_publishToDisconnectedPlugin_noException() throws Exception {
        WebSocket plugin = connectPlugin("temp-plugin");
        Thread.sleep(200);

        plugin.sendText("{\"action\": \"subscribe\", \"topic\": \"sys.notify\"}", true);
        Thread.sleep(200);

        plugin.abort();
        Thread.sleep(300);

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "system", "SYSTEM", null, "Mensaje para nadie"
        ).withHeader("origin_protocol", "CLI");

        assertDoesNotThrow(() -> {
            bridge.broadcastToPlugins("sys.notify", envelope);
        }, "Broadcast a plugins desconectados no debe fallar");

        assertTrue(app.jettyServer().server().isRunning(), "Servidor debe seguir corriendo");
    }

    @Test
    @Order(8)
    @DisplayName("5.1 OutputDispatcher enruta PLUGIN a PluginWebSocketBridge")
    void dispatcher_pluginProtocol_routesToBridge() throws Exception {
        List<String> pluginMessages = new CopyOnWriteArrayList<>();
        CountDownLatch msgLatch = new CountDownLatch(1);

        WebSocket plugin = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(TIMEOUT)
            .buildAsync(
                URI.create("ws://localhost:" + port + "/api/bus?pluginId=routing-test&token=" + AUTH_TOKEN),
                new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(10);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        String msg = data.toString();
                        if (msg.contains("broadcast") || msg.contains("envelope")) {
                            pluginMessages.add(msg);
                            msgLatch.countDown();
                        }
                        webSocket.request(1);
                        return null;
                    }
                }
            )
            .get(5, TimeUnit.SECONDS);

        Thread.sleep(200);

        SovereignEnvelope<String> pluginEnvelope = SovereignEnvelope.create(
            "system", "AGENT", null, "Respuesta para plugin"
        ).withHeader("origin_protocol", "PLUGIN");

        dispatcher.dispatch(pluginEnvelope);

        boolean received = msgLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Plugin debe recibir mensaje enrutado");
        assertFalse(pluginMessages.isEmpty(), "Lista de mensajes no debe estar vacía");

        plugin.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    @Test
    @Order(9)
    @DisplayName("5.2 OutputDispatcher con CLI va a stdout, no a plugins")
    void dispatcher_cliProtocol_goesToStdout() throws Exception {
        List<String> cliOutput = new ArrayList<>();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream captureStream = new java.io.PrintStream(baos);

        OutputDispatcher cliDispatcher = new OutputDispatcher(bus)
            .withCliOutput(captureStream);
        cliDispatcher.activate();

        SovereignEnvelope<String> cliEnvelope = SovereignEnvelope.create(
            "user", "CLI", null, "Output para terminal"
        ).withHeader("origin_protocol", "CLI");

        cliDispatcher.dispatch(cliEnvelope);

        String output = baos.toString();
        assertTrue(output.contains("Output para terminal"), "Mensaje CLI debe ir a stdout");
        assertEquals(0, bridge.getConnectionCount(), "No debe haber plugins involucrados");

        cliDispatcher.close();
    }

    @Test
    @Order(10)
    @DisplayName("5.3 OutputDispatcher con MATRIX publica en sys.output.matrix")
    void dispatcher_matrixProtocol_publishesToMatrixTopic() throws Exception {
        CountDownLatch matrixLatch = new CountDownLatch(1);
        AtomicReference<SovereignEnvelope<String>> capturedMatrix = new AtomicReference<>();

        bus.subscribe("sys.output.matrix", String.class, envelope -> {
            capturedMatrix.set(envelope);
            matrixLatch.countDown();
        });

        SovereignEnvelope<String> matrixEnvelope = SovereignEnvelope.create(
            "bot", "MATRIX", null, "Mensaje para Matrix room"
        ).withHeader("origin_protocol", "MATRIX")
         .withHeader("reply_channel_id", "!abc123:matrix.org");

        dispatcher.dispatch(matrixEnvelope);

        boolean received = matrixLatch.await(3, TimeUnit.SECONDS);
        assertTrue(received, "Mensaje Matrix debe publicarse en topic");

        SovereignEnvelope<String> captured = capturedMatrix.get();
        assertNotNull(captured);
        assertEquals("!abc123:matrix.org", captured.headers().get("matrix_room_id"));
    }

    @Test
    @Order(11)
    @DisplayName("6.1 Dispatcher rastrea estadísticas por protocolo")
    void dispatcher_tracksStats_byProtocol() throws Exception {
        for (int i = 0; i < 3; i++) {
            dispatcher.dispatch(createEnvelope("CLI", "msg-" + i));
        }
        for (int i = 0; i < 2; i++) {
            dispatcher.dispatch(createEnvelope("VOICE", "voice-" + i));
        }
        dispatcher.dispatch(createEnvelope("UNKNOWN_PROTO", "unknown"));

        var stats = dispatcher.getDispatchStats();
        assertEquals(3L, stats.get(OutputDispatcher.OriginProtocol.CLI));
        assertTrue(dispatcher.getTotalDispatched() >= 6, "Total debe ser al menos 6");
    }

    private WebSocket connectPlugin(String pluginId) throws Exception {
        CountDownLatch connectLatch = new CountDownLatch(1);

        WebSocket ws = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(TIMEOUT)
            .buildAsync(
                URI.create("ws://localhost:" + port + "/api/bus?pluginId=" + pluginId + "&token=" + AUTH_TOKEN),
                new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        connectLatch.countDown();
                        webSocket.request(10);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        webSocket.request(1);
                        return null;
                    }
                }
            )
            .get(5, TimeUnit.SECONDS);

        connectLatch.await(3, TimeUnit.SECONDS);
        return ws;
    }

    private SovereignEnvelope<String> createEnvelope(String protocol, String payload) {
        return SovereignEnvelope.create("test", protocol, null, payload)
            .withHeader("origin_protocol", protocol);
    }
}
