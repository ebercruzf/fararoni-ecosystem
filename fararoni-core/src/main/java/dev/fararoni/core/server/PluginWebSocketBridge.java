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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.security.HmacMessageSigner;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class PluginWebSocketBridge implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(PluginWebSocketBridge.class.getName());

    private final Set<WsContext> activeConnections = new CopyOnWriteArraySet<>();

    private final Map<String, WsContext> connectionsByPluginId = new ConcurrentHashMap<>();

    private final Map<String, Set<WsContext>> topicSubscriptions = new ConcurrentHashMap<>();

    private final SovereignEventBus bus;

    private final ObjectMapper mapper;

    private final String authToken;

    private Consumer<SovereignEnvelope<String>> busSubscriber;

    public PluginWebSocketBridge(SovereignEventBus bus, String authToken) {
        this.bus = bus;
        this.authToken = authToken;
        this.mapper = new ObjectMapper();

        subscribeToOutputTopics();

        LOG.info("[PluginWebSocketBridge] Iniciado. Auth: " + (authToken != null ? "ACTIVO" : "DESACTIVADO"));
    }

    public PluginWebSocketBridge(SovereignEventBus bus) {
        this(bus, null);
    }

    public void onConnect(WsConnectContext ctx) {
        if (authToken != null) {
            String token = ctx.queryParam("token");
            if (token == null || !authToken.equals(token)) {
                LOG.warning("[PluginWebSocketBridge] Conexión rechazada: token inválido");
                ctx.closeSession(4001, "Unauthorized: Invalid or missing token");
                return;
            }
        }

        String rawPluginId = ctx.queryParam("pluginId");
        final String pluginId = (rawPluginId == null || rawPluginId.isBlank())
            ? "plugin-" + System.currentTimeMillis()
            : rawPluginId;

        activeConnections.add(ctx);
        connectionsByPluginId.put(pluginId, ctx);

        LOG.info(() -> String.format(
            "[PluginWebSocketBridge] Plugin conectado: %s (total: %d)",
            pluginId, activeConnections.size()
        ));

        sendToPlugin(ctx, Map.of(
            "type", "connected",
            "pluginId", pluginId,
            "message", "Conectado al bus de eventos Fararoni",
            "protocol_version", "1.0"
        ));
    }

    public void onClose(WsCloseContext ctx) {
        activeConnections.remove(ctx);

        connectionsByPluginId.entrySet().removeIf(entry -> entry.getValue().equals(ctx));

        topicSubscriptions.values().forEach(set -> set.remove(ctx));

        LOG.info(() -> String.format(
            "[PluginWebSocketBridge] Plugin desconectado. Activos: %d",
            activeConnections.size()
        ));
    }

    public void onMessage(WsMessageContext ctx) {
        String message = ctx.message();

        try {
            JsonNode json = mapper.readTree(message);
            String action = json.has("action") ? json.get("action").asText() : "";

            switch (action) {
                case "publish" -> handlePublish(ctx, json);
                case "subscribe" -> handleSubscribe(ctx, json);
                case "unsubscribe" -> handleUnsubscribe(ctx, json);
                case "ping" -> handlePing(ctx);
                default -> sendError(ctx, "Acción desconocida: " + action);
            }
        } catch (JsonProcessingException e) {
            LOG.warning(() -> "[PluginWebSocketBridge] JSON inválido: " + e.getMessage());
            sendError(ctx, "JSON inválido: " + e.getMessage());
        }
    }

    private void handlePublish(WsContext ctx, JsonNode json) {
        String topic = json.has("topic") ? json.get("topic").asText() : "";
        String payload = json.has("payload") ? json.get("payload").asText() : "";

        if (topic.isBlank()) {
            sendError(ctx, "Campo 'topic' requerido");
            return;
        }

        String pluginId = getPluginIdForContext(ctx);

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            pluginId,
            "PLUGIN",
            null,
            payload
        );

        envelope = envelope.withHeader("origin_protocol", "PLUGIN");
        if (json.has("metadata")) {
            JsonNode metadata = json.get("metadata");
            if (metadata.isObject()) {
                var fields = metadata.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    envelope = envelope.withHeader(field.getKey(), field.getValue().asText());
                }
            }
        }

        String signature = HmacMessageSigner.sign(payload, envelope.timestamp().toEpochMilli());
        envelope = envelope.withSignature(signature);

        bus.publish(topic, envelope);

        LOG.fine(() -> String.format(
            "[PluginWebSocketBridge] Mensaje publicado: %s -> %s",
            pluginId, topic
        ));

        sendToPlugin(ctx, Map.of(
            "type", "ack",
            "action", "publish",
            "topic", topic,
            "messageId", envelope.id()
        ));
    }

    private void handleSubscribe(WsContext ctx, JsonNode json) {
        String topic = json.has("topic") ? json.get("topic").asText() : "";

        if (topic.isBlank()) {
            sendError(ctx, "Campo 'topic' requerido");
            return;
        }

        topicSubscriptions
            .computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>())
            .add(ctx);

        LOG.fine(() -> String.format(
            "[PluginWebSocketBridge] Suscripción agregada: %s",
            topic
        ));

        sendToPlugin(ctx, Map.of(
            "type", "ack",
            "action", "subscribe",
            "topic", topic
        ));
    }

    private void handleUnsubscribe(WsContext ctx, JsonNode json) {
        String topic = json.has("topic") ? json.get("topic").asText() : "";

        if (topic.isBlank()) {
            sendError(ctx, "Campo 'topic' requerido");
            return;
        }

        Set<WsContext> subs = topicSubscriptions.get(topic);
        if (subs != null) {
            subs.remove(ctx);
        }

        sendToPlugin(ctx, Map.of(
            "type", "ack",
            "action", "unsubscribe",
            "topic", topic
        ));
    }

    private void handlePing(WsContext ctx) {
        sendToPlugin(ctx, Map.of(
            "type", "pong",
            "timestamp", System.currentTimeMillis()
        ));
    }

    public void broadcastToPlugins(String topic, SovereignEnvelope<?> envelope) {
        Set<WsContext> subscribers = topicSubscriptions.get(topic);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> message = Map.of(
                "type", "envelope",
                "topic", topic,
                "envelope", Map.of(
                    "id", envelope.id(),
                    "userId", envelope.userId() != null ? envelope.userId() : "",
                    "senderRole", envelope.senderRole() != null ? envelope.senderRole() : "",
                    "payload", envelope.payload() != null ? envelope.payload().toString() : "",
                    "timestamp", envelope.timestamp().toEpochMilli(),
                    "hopCount", envelope.hopCount(),
                    "headers", envelope.headers()
                )
            );

            String jsonMessage = mapper.writeValueAsString(message);

            for (WsContext ctx : subscribers) {
                if (ctx.session.isOpen()) {
                    try {
                        ctx.send(jsonMessage);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "[PluginWebSocketBridge] Error enviando a plugin", e);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "[PluginWebSocketBridge] Error serializando envelope", e);
        }
    }

    public void broadcastToAllPlugins(SovereignEnvelope<?> envelope) {
        for (WsContext ctx : activeConnections) {
            if (ctx.session.isOpen()) {
                try {
                    String json = mapper.writeValueAsString(Map.of(
                        "type", "broadcast",
                        "envelope", Map.of(
                            "id", envelope.id(),
                            "payload", envelope.payload() != null ? envelope.payload().toString() : "",
                            "timestamp", envelope.timestamp().toEpochMilli()
                        )
                    ));
                    ctx.send(json);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[PluginWebSocketBridge] Error en broadcast", e);
                }
            }
        }
    }

    private void subscribeToOutputTopics() {
        busSubscriber = envelope -> {
            String originProtocol = envelope.headers().get("origin_protocol");
            if ("PLUGIN".equals(originProtocol)) {
                return;
            }

            broadcastToPlugins("sys.output", envelope);
        };

        bus.subscribe("sys.output", String.class, busSubscriber);
        bus.subscribe("sys.output.voice", String.class, busSubscriber);
        bus.subscribe("sys.output.cli", String.class, busSubscriber);

        LOG.info("[PluginWebSocketBridge] Suscrito a sys.output.*");
    }

    private void sendToPlugin(WsContext ctx, Object message) {
        if (ctx.session.isOpen()) {
            try {
                ctx.send(mapper.writeValueAsString(message));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[PluginWebSocketBridge] Error enviando mensaje", e);
            }
        }
    }

    private void sendError(WsContext ctx, String errorMessage) {
        sendToPlugin(ctx, Map.of(
            "type", "error",
            "message", errorMessage,
            "timestamp", System.currentTimeMillis()
        ));
    }

    private String getPluginIdForContext(WsContext ctx) {
        for (Map.Entry<String, WsContext> entry : connectionsByPluginId.entrySet()) {
            if (entry.getValue().equals(ctx)) {
                return entry.getKey();
            }
        }
        return "unknown-plugin";
    }

    public int getConnectionCount() {
        return activeConnections.size();
    }

    public Set<String> getConnectedPluginIds() {
        return Set.copyOf(connectionsByPluginId.keySet());
    }

    @Override
    public void close() {
        for (WsContext ctx : activeConnections) {
            if (ctx.session.isOpen()) {
                ctx.closeSession(1001, "Server shutting down");
            }
        }
        activeConnections.clear();
        connectionsByPluginId.clear();
        topicSubscriptions.clear();

        LOG.info("[PluginWebSocketBridge] Cerrado");
    }
}
