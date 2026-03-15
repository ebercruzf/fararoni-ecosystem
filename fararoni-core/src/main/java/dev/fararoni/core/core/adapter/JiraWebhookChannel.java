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
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class JiraWebhookChannel extends AbstractIngestionChannel {
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
        "jira:issue_created",
        "jira:issue_updated",
        "jira:issue_deleted",
        "comment_created",
        "comment_updated",
        "comment_deleted",
        "issuelink_created",
        "issuelink_deleted"
    );

    private final int port;
    private final String path;
    private final String secretToken;
    private final ObjectMapper objectMapper;
    private HttpServer server;

    public JiraWebhookChannel(String name, int port, String path, String secretToken, EventBus eventBus) {
        super(name, "jira", eventBus);
        this.port = port;
        this.path = path.startsWith("/") ? path : "/" + path;
        this.secretToken = secretToken;
        this.objectMapper = new ObjectMapper();
    }

    public JiraWebhookChannel(String name, int port, EventBus eventBus) {
        this(name, port, "/jira", null, eventBus);
    }

    public JiraWebhookChannel(String name, int port, String secretToken, EventBus eventBus) {
        this(name, port, "/jira", secretToken, eventBus);
    }

    @Override
    protected void doStart() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext(path, this::handleJiraWebhook);
        server.createContext("/health", this::handleHealth);

        server.start();

        System.out.printf("[%s] Jira webhook server started on http://localhost:%d%s%n",
            getName(), port, path);
    }

    @Override
    protected void doStop() throws Exception {
        if (server != null) {
            server.stop(1);
            server = null;
            System.out.printf("[%s] Jira webhook server stopped%n", getName());
        }
    }

    @Override
    protected boolean checkHealth() {
        return server != null;
    }

    private void handleJiraWebhook(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        if (secretToken != null && !secretToken.isBlank()) {
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("token=" + secretToken)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
        }

        try {
            String body = readBody(exchange);
            JsonNode json = objectMapper.readTree(body);

            String webhookEvent = json.path("webhookEvent").asText("");

            if (!SUPPORTED_EVENTS.contains(webhookEvent)) {
                sendResponse(exchange, 200,
                    "{\"status\":\"ignored\",\"reason\":\"unsupported event: " + webhookEvent + "\"}");
                return;
            }

            IncomingMessage message = parseJiraWebhook(json, webhookEvent);
            dispatchMessage(message);

            sendResponse(exchange, 200,
                "{\"status\":\"accepted\",\"event\":\"" + webhookEvent + "\"}");
        } catch (Exception e) {
            dispatchError(e);
            sendResponse(exchange, 400,
                "{\"error\":\"Bad Request\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private IncomingMessage parseJiraWebhook(JsonNode json, String webhookEvent) {
        JsonNode issue = json.path("issue");
        JsonNode fields = issue.path("fields");

        String issueKey = issue.path("key").asText("UNKNOWN");

        String content = buildContent(webhookEvent, fields, json);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("webhookEvent", webhookEvent);
        metadata.put("issueKey", issueKey);
        metadata.put("issueId", issue.path("id").asText(""));
        metadata.put("project", fields.path("project").path("key").asText(""));

        metadata.put("issueType", fields.path("issuetype").path("name").asText(""));
        metadata.put("priority", fields.path("priority").path("name").asText(""));
        metadata.put("status", fields.path("status").path("name").asText(""));
        metadata.put("summary", fields.path("summary").asText(""));

        JsonNode reporter = fields.path("reporter");
        if (!reporter.isMissingNode()) {
            metadata.put("reporter", reporter.path("emailAddress").asText(
                reporter.path("displayName").asText("")));
        }

        JsonNode assignee = fields.path("assignee");
        if (!assignee.isMissingNode() && !assignee.isNull()) {
            metadata.put("assignee", assignee.path("emailAddress").asText(
                assignee.path("displayName").asText("")));
        }

        JsonNode user = json.path("user");
        if (!user.isMissingNode()) {
            metadata.put("triggerUser", user.path("emailAddress").asText(
                user.path("displayName").asText("")));
        }

        metadata.put("timestamp", json.path("timestamp").asText(Instant.now().toString()));

        return new IncomingMessage(
            "jira",
            getName(),
            issueKey,
            content,
            metadata,
            Instant.now()
        );
    }

    private String buildContent(String webhookEvent, JsonNode fields, JsonNode json) {
        StringBuilder content = new StringBuilder();

        String summary = fields.path("summary").asText("(sin titulo)");

        switch (webhookEvent) {
            case "jira:issue_created" -> {
                content.append("Nuevo ticket: ").append(summary).append("\n\n");
                String description = fields.path("description").asText("");
                if (!description.isBlank()) {
                    content.append(description);
                }
            }
            case "jira:issue_updated" -> {
                content.append("Ticket actualizado: ").append(summary).append("\n\n");
                JsonNode changelog = json.path("changelog");
                if (!changelog.isMissingNode()) {
                    content.append("Cambios:\n");
                    for (JsonNode item : changelog.path("items")) {
                        String field = item.path("field").asText("");
                        String fromVal = item.path("fromString").asText("");
                        String toVal = item.path("toString").asText("");
                        content.append("- ").append(field)
                               .append(": ").append(fromVal)
                               .append(" → ").append(toVal).append("\n");
                    }
                }
            }
            case "comment_created", "comment_updated" -> {
                content.append("Comentario en: ").append(summary).append("\n\n");
                JsonNode comment = json.path("comment");
                content.append(comment.path("body").asText(""));
            }
            default -> {
                content.append("Evento: ").append(webhookEvent).append("\n");
                content.append("Ticket: ").append(summary);
            }
        }

        return content.toString();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        ChannelStats stats = getStats();
        String response = String.format(
            "{\"status\":\"healthy\",\"channel\":\"%s\",\"type\":\"jira\",\"messages_received\":%d}",
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
}
