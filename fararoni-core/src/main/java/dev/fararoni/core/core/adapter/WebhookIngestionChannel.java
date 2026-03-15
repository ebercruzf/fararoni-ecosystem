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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class WebhookIngestionChannel extends AbstractIngestionChannel {
    private final int port;
    private final String path;
    private final ObjectMapper objectMapper;
    private HttpServer server;

    public WebhookIngestionChannel(String name, int port, String path, EventBus eventBus) {
        super(name, "webhook", eventBus);
        this.port = port;
        this.path = path.startsWith("/") ? path : "/" + path;
        this.objectMapper = new ObjectMapper();
    }

    public WebhookIngestionChannel(String name, int port, EventBus eventBus) {
        this(name, port, "/webhook", eventBus);
    }

    public WebhookIngestionChannel(String name, int port, String path) {
        this(name, port, path, null);
    }

    @Override
    protected void doStart() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext(path, this::handleWebhook);

        server.createContext("/health", this::handleHealth);

        server.start();

        System.out.printf("[%s] Webhook server started on http://localhost:%d%s%n",
            getName(), port, path);
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            server.stop(1);
            server = null;
            System.out.printf("[%s] Webhook server stopped%n", getName());
        }
    }

    @Override
    protected boolean checkHealth() {
        return server != null;
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"POST".equals(method)) {
            sendResponse(exchange, 405,
                "{\"error\":\"Method Not Allowed\",\"message\":\"Use POST\"}");
            return;
        }

        try {
            String body = readBody(exchange);

            IncomingMessage message = parseWebhookBody(body, exchange);

            dispatchMessage(message);

            sendResponse(exchange, 200,
                "{\"status\":\"accepted\",\"channel\":\"" + getName() + "\"}");
        } catch (Exception e) {
            dispatchError(e);
            sendResponse(exchange, 400,
                "{\"error\":\"Bad Request\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        ChannelStats stats = getStats();
        String response = String.format(
            "{\"status\":\"healthy\",\"channel\":\"%s\",\"messages_received\":%d}",
            getName(), stats != null ? stats.messagesReceived() : 0
        );
        sendResponse(exchange, 200, response);
    }

    private IncomingMessage parseWebhookBody(String body, HttpExchange exchange) throws Exception {
        JsonNode json = objectMapper.readTree(body);

        String sourceId = extractField(json, "source", "sender", "from", "user", "id");
        if (sourceId == null) {
            sourceId = exchange.getRemoteAddress().toString();
        }

        String content = extractField(json, "message", "content", "text", "body", "payload");
        if (content == null) {
            content = body;
        }

        Map<String, String> metadata = new HashMap<>();

        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty() && isRelevantHeader(key)) {
                metadata.put("header_" + key.toLowerCase(), values.get(0));
            }
        });

        json.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!isContentField(key)) {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    metadata.put(key, value.asText());
                } else if (value.isNumber()) {
                    metadata.put(key, value.toString());
                } else if (value.isBoolean()) {
                    metadata.put(key, String.valueOf(value.asBoolean()));
                }
            }
        });

        return new IncomingMessage(
            "webhook",
            getName(),
            sourceId,
            content,
            metadata,
            Instant.now()
        );
    }

    private String extractField(JsonNode json, String... fieldNames) {
        for (String name : fieldNames) {
            if (json.has(name)) {
                JsonNode node = json.get(name);
                if (node.isTextual()) {
                    return node.asText();
                } else if (node.isObject() || node.isArray()) {
                    return node.toString();
                }
            }
        }
        return null;
    }

    private boolean isRelevantHeader(String header) {
        String lower = header.toLowerCase();
        return lower.startsWith("x-") ||
               lower.equals("content-type") ||
               lower.equals("user-agent") ||
               lower.contains("signature") ||
               lower.contains("event");
    }

    private boolean isContentField(String field) {
        return field.equals("message") || field.equals("content") ||
               field.equals("text") || field.equals("body") ||
               field.equals("source") || field.equals("sender") ||
               field.equals("from") || field.equals("user");
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
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }
}
