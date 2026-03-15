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
package dev.fararoni.core.core.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.context.ExecutionContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0 (FASE 92)
 */
public class AnthropicClient implements AgentClient {
    private static final Logger logger = Logger.getLogger(AnthropicClient.class.getName());
    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;
    private final ObjectMapper mapper;

    public AnthropicClient(String apiKey, String modelName) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(120))
                .build();
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.mapper = new ObjectMapper();
    }

    @Override
    public AgentResponse generateWithTools(String systemPrompt, String userPrompt, List<ObjectNode> tools) {
        try {
            return generateWithTools(systemPrompt, userPrompt, tools, ExecutionContext.immortal());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentResponse("Error de conexión", null);
        }
    }

    @Override
    public AgentResponse generateWithTools(String systemPrompt, String userPrompt,
                                           List<ObjectNode> tools, ExecutionContext ctx)
            throws InterruptedException {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", this.modelName);
            payload.put("max_tokens", 8192);

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                payload.put("system", systemPrompt);
            }

            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "user").put("content", userPrompt);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode claudeTools = convertToolsToClaudeFormat(tools);
                payload.set("tools", claudeTools);
                payload.set("tool_choice", mapper.createObjectNode().put("type", "auto"));
            }

            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AppDefaults.DEFAULT_CLAUDE_BASE_URL + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", AppDefaults.ANTHROPIC_API_VERSION)
                    .timeout(Duration.ofSeconds(300))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);

            if (response.statusCode() != 200) {
                logger.warning("[CLAUDE-API] Error " + response.statusCode() + ": " + response.body());
                return new AgentResponse("Error Claude API: " + response.statusCode(), null);
            }

            return parseClaudeResponse(response.body());
        } catch (IOException e) {
            logger.warning("[CLAUDE-API] Error de conexión: " + e.getMessage());
            return new AgentResponse("Error de conexión Claude: " + e.getMessage(), null);
        }
    }

    @Override
    public AgentResponse generateWithFullHistory(ArrayNode messages, List<ObjectNode> tools, String toolChoice) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", this.modelName);
            payload.put("max_tokens", 8192);

            String systemPrompt = null;
            ArrayNode claudeMessages = mapper.createArrayNode();

            for (int i = 0; i < messages.size(); i++) {
                JsonNode msg = messages.get(i);
                String role = msg.path("role").asText("");

                switch (role) {
                    case "system" -> {
                        systemPrompt = msg.path("content").asText("");
                    }
                    case "user" -> {
                        claudeMessages.addObject()
                            .put("role", "user")
                            .put("content", msg.path("content").asText(""));
                    }
                    case "assistant" -> {
                        if (msg.has("tool_calls") && msg.path("tool_calls").isArray()) {
                            ObjectNode assistantMsg = mapper.createObjectNode();
                            assistantMsg.put("role", "assistant");
                            ArrayNode content = assistantMsg.putArray("content");

                            String textContent = msg.path("content").asText("");
                            if (!textContent.isBlank()) {
                                content.addObject().put("type", "text").put("text", textContent);
                            }

                            for (JsonNode tc : msg.path("tool_calls")) {
                                ObjectNode toolUse = content.addObject();
                                toolUse.put("type", "tool_use");
                                toolUse.put("id", tc.path("id").asText(""));
                                toolUse.put("name", tc.path("function").path("name").asText(""));
                                String argsStr = tc.path("function").path("arguments").asText("{}");
                                try {
                                    toolUse.set("input", mapper.readTree(argsStr));
                                } catch (Exception e) {
                                    toolUse.set("input", mapper.createObjectNode());
                                }
                            }
                            claudeMessages.add(assistantMsg);
                        } else {
                            claudeMessages.addObject()
                                .put("role", "assistant")
                                .put("content", msg.path("content").asText(""));
                        }
                    }
                    case "tool" -> {
                        ObjectNode toolResultMsg = mapper.createObjectNode();
                        toolResultMsg.put("role", "user");
                        ArrayNode content = toolResultMsg.putArray("content");
                        ObjectNode toolResult = content.addObject();
                        toolResult.put("type", "tool_result");
                        toolResult.put("tool_use_id", msg.path("tool_call_id").asText(""));
                        toolResult.put("content", msg.path("content").asText(""));
                        claudeMessages.add(toolResultMsg);
                    }
                    default -> {
                    }
                }
            }

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                payload.put("system", systemPrompt);
            }
            payload.set("messages", claudeMessages);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode claudeTools = convertToolsToClaudeFormat(tools);
                payload.set("tools", claudeTools);

                ObjectNode tcNode = mapper.createObjectNode();
                if ("required".equals(toolChoice)) {
                    tcNode.put("type", "any");
                } else {
                    tcNode.put("type", toolChoice != null ? toolChoice : "auto");
                }
                payload.set("tool_choice", tcNode);
            }

            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AppDefaults.DEFAULT_CLAUDE_BASE_URL + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", AppDefaults.ANTHROPIC_API_VERSION)
                    .timeout(Duration.ofSeconds(300))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = sendAsyncWithRetry(request);

            if (response.statusCode() != 200) {
                logger.warning("[CLAUDE-API] Error " + response.statusCode() + ": " + response.body());
                return new AgentResponse("Error Claude API: " + response.statusCode(), null);
            }

            return parseClaudeResponse(response.body());
        } catch (CancellationException e) {
            return new AgentResponse("Solicitud Claude cancelada", null);
        } catch (ExecutionException e) {
            return new AgentResponse("Error de conexión Claude: " + e.getCause().getMessage(), null);
        } catch (IOException e) {
            return new AgentResponse("Error de conexión Claude", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentResponse("Error de conexión Claude", null);
        }
    }

    @Override
    public String generateStrict(String prompt, List<String> stopSequences, double temperature) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", this.modelName);
            payload.put("max_tokens", 8192);
            payload.put("temperature", temperature);

            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "user").put("content", prompt);

            if (stopSequences != null && !stopSequences.isEmpty()) {
                ArrayNode stopArray = payload.putArray("stop_sequences");
                for (String seq : stopSequences) {
                    stopArray.add(seq);
                }
            }

            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AppDefaults.DEFAULT_CLAUDE_BASE_URL + "/v1/messages"))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", AppDefaults.ANTHROPIC_API_VERSION)
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = sendWithRetry(request);

            if (response.statusCode() != 200) {
                return "Error Claude: " + response.statusCode();
            }

            JsonNode root = mapper.readTree(response.body());
            if (root.has("content") && root.path("content").isArray()) {
                for (JsonNode block : root.path("content")) {
                    if ("text".equals(block.path("type").asText(""))) {
                        return block.path("text").asText("");
                    }
                }
            }
            return "";
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: " + e.getMessage();
        }
    }

    private static final int MAX_RETRIES = 3;
    private static final int[] RETRY_WAIT_SECONDS = {30, 60, 90};

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                int waitSecs = RETRY_WAIT_SECONDS[attempt - 1];
                logger.info("[CLAUDE-API] Rate limit (429). Reintento " + attempt + "/" + MAX_RETRIES
                    + " en " + waitSecs + "s...");
                System.out.println("[Claude] Rate limit alcanzado. Reintentando en " + waitSecs + " segundos... ("
                    + attempt + "/" + MAX_RETRIES + ")");
                Thread.sleep(waitSecs * 1000L);
            }
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429) {
                return response;
            }
        }
        return response;
    }

    private HttpResponse<String> sendAsyncWithRetry(HttpRequest request) throws IOException, InterruptedException, ExecutionException {
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                int waitSecs = RETRY_WAIT_SECONDS[attempt - 1];
                logger.info("[CLAUDE-API] Rate limit (429). Reintento " + attempt + "/" + MAX_RETRIES
                    + " en " + waitSecs + "s...");
                System.out.println("[Claude] Rate limit alcanzado. Reintentando en " + waitSecs + " segundos... ("
                    + attempt + "/" + MAX_RETRIES + ")");
                Thread.sleep(waitSecs * 1000L);
            }
            response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get();
            if (response.statusCode() != 429) {
                return response;
            }
        }
        return response;
    }

    private AgentResponse parseClaudeResponse(String jsonResponse) throws IOException {
        JsonNode root = mapper.readTree(jsonResponse);
        JsonNode contentArray = root.path("content");

        if (!contentArray.isArray()) {
            return new AgentResponse("Respuesta Claude inválida", null);
        }

        for (JsonNode block : contentArray) {
            if ("tool_use".equals(block.path("type").asText(""))) {
                String name = block.path("name").asText("");
                JsonNode inputNode = block.path("input");
                String argumentsJson = mapper.writeValueAsString(inputNode);
                return new AgentResponse(null, new ToolCall(name, argumentsJson));
            }
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode block : contentArray) {
            if ("text".equals(block.path("type").asText(""))) {
                if (!text.isEmpty()) text.append("\n");
                text.append(block.path("text").asText(""));
            }
        }

        return new AgentResponse(text.toString(), null);
    }

    private ArrayNode convertToolsToClaudeFormat(List<ObjectNode> openAiTools) {
        ArrayNode claudeTools = mapper.createArrayNode();
        for (ObjectNode tool : openAiTools) {
            JsonNode fn = tool.path("function");
            if (fn.isMissingNode()) {
                claudeTools.add(tool);
                continue;
            }
            ObjectNode claudeTool = mapper.createObjectNode();
            claudeTool.put("name", fn.path("name").asText(""));
            claudeTool.put("description", fn.path("description").asText(""));
            if (fn.has("parameters")) {
                claudeTool.set("input_schema", fn.path("parameters"));
            } else {
                ObjectNode emptySchema = mapper.createObjectNode();
                emptySchema.put("type", "object");
                emptySchema.set("properties", mapper.createObjectNode());
                claudeTool.set("input_schema", emptySchema);
            }
            claudeTools.add(claudeTool);
        }
        return claudeTools;
    }

    public String getModelName() {
        return modelName;
    }
}
