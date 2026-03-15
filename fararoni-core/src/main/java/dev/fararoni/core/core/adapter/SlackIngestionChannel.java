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
package dev.fararoni.core.core.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.io.IncomingMessage;
import dev.fararoni.core.core.event.EventBus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SlackIngestionChannel extends AbstractIngestionChannel {
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
        "app_mention",
        "message",
        "message.channels",
        "message.groups",
        "message.im",
        "reaction_added",
        "reaction_removed"
    );

    private static final String SLACK_SIGNATURE_HEADER = "X-Slack-Signature";
    private static final String SLACK_TIMESTAMP_HEADER = "X-Slack-Request-Timestamp";
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    private final int port;
    private final String path;
    private final String botToken;
    private final String signingSecret;
    private final ObjectMapper objectMapper;
    private HttpServer server;

    public SlackIngestionChannel(String name, int port, String path,
                                  String botToken, String signingSecret, EventBus eventBus) {
        super(name, "slack", eventBus);
        this.port = port;
        this.path = path.startsWith("/") ? path : "/" + path;
        this.botToken = botToken;
        this.signingSecret = Objects.requireNonNull(signingSecret, "signingSecret required for security");
        this.objectMapper = new ObjectMapper();
    }

    public SlackIngestionChannel(String name, int port, String botToken,
                                  String signingSecret, EventBus eventBus) {
        this(name, port, "/slack/events", botToken, signingSecret, eventBus);
    }

    @Override
    protected void doStart() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext(path, this::handleSlackEvent);
        server.createContext("/health", this::handleHealth);

        server.start();

        System.out.printf("[%s] Slack Events server started on http://localhost:%d%s%n",
            getName(), port, path);
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            server.stop(1);
            server = null;
            System.out.printf("[%s] Slack Events server stopped%n", getName());
        }
    }

    @Override
    protected boolean checkHealth() {
        return server != null;
    }

    private void handleSlackEvent(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        String body = readBody(exchange);

        if (!verifySlackSignature(exchange, body)) {
            sendResponse(exchange, 401, "{\"error\":\"Invalid signature\"}");
            return;
        }

        try {
            JsonNode json = objectMapper.readTree(body);
            String type = json.path("type").asText("");

            if ("url_verification".equals(type)) {
                String challenge = json.path("challenge").asText("");
                sendResponse(exchange, 200, "{\"challenge\":\"" + challenge + "\"}");
                return;
            }

            if ("event_callback".equals(type)) {
                JsonNode event = json.path("event");
                String eventType = event.path("type").asText("");

                if (isBotMessage(event)) {
                    sendResponse(exchange, 200, "{\"status\":\"ignored\",\"reason\":\"bot message\"}");
                    return;
                }

                if (!SUPPORTED_EVENTS.contains(eventType)) {
                    sendResponse(exchange, 200,
                        "{\"status\":\"ignored\",\"reason\":\"unsupported event\"}");
                    return;
                }

                IncomingMessage message = parseSlackEvent(event, json);
                dispatchMessage(message);

                sendResponse(exchange, 200, "{\"status\":\"accepted\"}");
                return;
            }

            sendResponse(exchange, 200, "{\"status\":\"ignored\"}");
        } catch (Exception e) {
            dispatchError(e);
            sendResponse(exchange, 400,
                "{\"error\":\"Bad Request\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private boolean verifySlackSignature(HttpExchange exchange, String body) {
        try {
            String signature = exchange.getRequestHeaders().getFirst(SLACK_SIGNATURE_HEADER);
            String timestamp = exchange.getRequestHeaders().getFirst(SLACK_TIMESTAMP_HEADER);

            if (signature == null || timestamp == null) {
                return false;
            }

            long ts = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - ts) > TIMESTAMP_TOLERANCE_SECONDS) {
                return false;
            }

            String baseString = "v0:" + timestamp + ":" + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "v0=" + HexFormat.of().formatHex(hash);

            return signature.equals(expectedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBotMessage(JsonNode event) {
        if (event.has("bot_id")) {
            return true;
        }
        String subtype = event.path("subtype").asText("");
        return "bot_message".equals(subtype);
    }

    private IncomingMessage parseSlackEvent(JsonNode event, JsonNode payload) {
        String eventType = event.path("type").asText("");
        String user = event.path("user").asText("");
        String channel = event.path("channel").asText("");
        String text = event.path("text").asText("");
        String ts = event.path("ts").asText("");

        Map<String, String> metadata = new HashMap<>();
        metadata.put("eventType", eventType);
        metadata.put("channel", channel);
        metadata.put("user", user);
        metadata.put("ts", ts);

        String threadTs = event.path("thread_ts").asText("");
        if (!threadTs.isBlank()) {
            metadata.put("threadTs", threadTs);
        }

        String team = payload.path("team_id").asText("");
        if (!team.isBlank()) {
            metadata.put("team", team);
        }

        String eventId = payload.path("event_id").asText("");
        if (!eventId.isBlank()) {
            metadata.put("eventId", eventId);
        }

        if (eventType.startsWith("reaction_")) {
            metadata.put("reaction", event.path("reaction").asText(""));
            metadata.put("item_channel", event.path("item").path("channel").asText(""));
            metadata.put("item_ts", event.path("item").path("ts").asText(""));

            text = ":" + event.path("reaction").asText("") + ":";
        }

        String content = buildContent(eventType, text, event);

        return new IncomingMessage(
            "slack",
            getName(),
            user,
            content,
            metadata,
            Instant.now()
        );
    }

    private String buildContent(String eventType, String text, JsonNode event) {
        return switch (eventType) {
            case "app_mention" -> "Mencion: " + text;
            case "reaction_added" -> "Reaccion agregada: :" + event.path("reaction").asText("") + ":";
            case "reaction_removed" -> "Reaccion removida: :" + event.path("reaction").asText("") + ":";
            default -> text;
        };
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        ChannelStats stats = getStats();
        String response = String.format(
            "{\"status\":\"healthy\",\"channel\":\"%s\",\"type\":\"slack\",\"messages_received\":%d}",
            getName(), stats != null ? stats.messagesReceived() : 0
        );
        sendResponse(exchange, 200, response);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n");
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    protected String getBotToken() {
        return botToken;
    }
}
