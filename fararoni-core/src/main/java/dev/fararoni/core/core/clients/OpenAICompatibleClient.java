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
 * @version 2.0.0 (Agentic Upgrade)
 */
public class OpenAICompatibleClient implements AgentClient {
    private static final Logger logger = Logger.getLogger(OpenAICompatibleClient.class.getName());
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final ObjectMapper mapper;
    private final int requestTimeoutSeconds;

    public OpenAICompatibleClient(String baseUrl, String apiKey, String modelName) {
        this(baseUrl, apiKey, modelName, 300);
    }

    public OpenAICompatibleClient(String baseUrl, String apiKey, String modelName, int requestTimeoutSeconds) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(requestTimeoutSeconds / 3, 120)))
                .build();
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.mapper = new ObjectMapper();
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    @Override
    public AgentResponse generateWithTools(String systemPrompt, String userPrompt, List<ObjectNode> tools) {
        try {
            return generateWithTools(systemPrompt, userPrompt, tools, ExecutionContext.immortal());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Conexión interrumpida", e);
        }
    }

    public AgentResponse generateWithTools(String systemPrompt, String userPrompt,
                                           List<ObjectNode> tools, ExecutionContext ctx)
            throws InterruptedException {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", this.modelName);
            payload.put("temperature", 0.1);

            boolean showThinking = Boolean.getBoolean("FARARONI_SHOW_REASONING");
            if (showThinking) {
                payload.put("think", true);
                logger.info("[THINKING] Enviando think:true a Ollama");
            }

            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = payload.putArray("tools");
                toolsArray.addAll(tools);
                payload.put("tool_choice", "auto");
            }

            String jsonBody = mapper.writeValueAsString(payload);

            int toolCount = (tools != null) ? tools.size() : 0;
            logger.info("[PAYLOAD-DIAG] model=" + this.modelName
                + " systemPrompt=" + (systemPrompt != null ? systemPrompt.length() : 0) + " chars"
                + " userPrompt=" + (userPrompt != null ? userPrompt.length() : 0) + " chars"
                + " tools=" + toolCount
                + " totalPayload=" + jsonBody.length() + " bytes (" + (jsonBody.length() / 1024) + " KB)");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            logger.info("[PHOENIX-HTTP] Request enviado asincronamente. Esperando respuesta...");

            ctx.onCancel(reason -> {
                logger.info("[PHOENIX-HTTP] [CUT] Cortando conexion por: " + reason);
                future.cancel(true);
            });

            long llmStartMs = System.currentTimeMillis();
            HttpResponse<String> response = future.get();
            long llmDurationMs = System.currentTimeMillis() - llmStartMs;

            if (response.statusCode() != 200) {
                String errorBody = response.body() != null ? response.body() : "sin cuerpo";
                logger.severe("[HTTP-ERROR] " + this.modelName + " HTTP " + response.statusCode()
                    + " payload=" + jsonBody.length() + " bytes: " + errorBody);
                throw new RuntimeException("LLM API HTTP " + response.statusCode()
                    + " (payload=" + (jsonBody.length() / 1024) + "KB): " + errorBody);
            }

            AgentResponse base = parseAgentResponse(response.body());
            double tokPerSec = extractTokPerSec(response.body(), llmDurationMs);
            return new AgentResponse(base.textContent(), base.toolCall(), tokPerSec);
        } catch (CancellationException e) {
            logger.info("[PHOENIX-HTTP] Solicitud cancelada por el usuario.");
            throw new InterruptedException("Solicitud de red abortada: " + ctx.getCancelReason());
        } catch (ExecutionException e) {
            logger.severe("[PHOENIX-HTTP] Error en transporte: " + e.getCause().getMessage());
            throw new RuntimeException("Error de transporte LLM: " + e.getCause().getMessage(), e.getCause());
        } catch (IOException e) {
            logger.severe("Fallo crítico en transporte LLM: " + e.getMessage());
            throw new RuntimeException("Fallo de conexión LLM: " + e.getMessage(), e);
        }
    }

    private AgentResponse parseAgentResponse(String jsonResponse) throws IOException {
        JsonNode root = mapper.readTree(jsonResponse);
        if (!root.has("choices") || root.path("choices").isEmpty()) {
            return new AgentResponse("Error: Respuesta vacía del modelo", null);
        }

        JsonNode choice = root.path("choices").get(0);
        JsonNode message = choice.path("message");

        if (message.has("tool_calls") && !message.path("tool_calls").isEmpty()) {
            JsonNode toolCall = message.path("tool_calls").get(0);
            String functionName = toolCall.path("function").path("name").asText();
            String arguments = toolCall.path("function").path("arguments").asText();

            logger.info("[CLIENT] [ACTIVE] Tool Call Nativo detectado (Qwen 3/GPT-4 style): " + functionName);
            return new AgentResponse(null, new ToolCall(functionName, arguments));
        }

        String content = message.path("content").asText();

        String reasoning = message.path("reasoning").asText(null);
        if (reasoning != null && !reasoning.isEmpty() && Boolean.getBoolean("FARARONI_SHOW_REASONING")) {
            System.out.println("\n\033[3;90m[THOUGHT] " + reasoning + "\033[0m\n");
        }

        boolean seemsLikeToolUse = content != null && (
                content.contains("restore_solution") ||
                content.contains("fs_write") ||
                content.contains("fs_patch") ||
                content.contains("fs_mkdir") ||
                content.contains("fs_read") ||
                content.contains("ShellCommand") ||
                content.contains("web_fetch") ||
                content.contains("web_search") ||
                content.contains("gitaction") ||
                content.trim().startsWith("{") ||
                content.contains("```json"));

        if (seemsLikeToolUse) {
            logger.info("[CLIENT] [PENDING] Analizando respuesta textual buscando herramientas...");

            ToolCall prioritizedTool = findPrioritizedToolCall(content);
            if (prioritizedTool != null) {
                logger.info("[CLIENT] [OK] Tool priorizada encontrada: " + prioritizedTool.functionName());
                return new AgentResponse(null, prioritizedTool);
            }

            String cleanJson = extractJsonBlock(content);
            if (cleanJson != null) {
                try {
                    JsonNode potentialTool = mapper.readTree(cleanJson);

                    if (potentialTool.has("name") && potentialTool.has("arguments")) {
                        String toolName = potentialTool.get("name").asText();
                        String args = potentialTool.get("arguments").toString();

                        if (potentialTool.get("arguments").isTextual()) {
                            args = potentialTool.get("arguments").asText();
                        }

                        logger.info("[CLIENT] [OK] JSON generico recuperado: " + toolName);
                        return new AgentResponse(null, new ToolCall(toolName, args));
                    }

                    if (potentialTool.has("exercise_id")) {
                        logger.info("[CLIENT] [OK] JSON legacy (exercise_id) recuperado.");
                        return new AgentResponse(null, new ToolCall("restore_solution", cleanJson));
                    }
                } catch (Exception e) {
                    logger.warning("[CLIENT] [WARN] Fallo el parseo del JSON generico: " + e.getMessage());
                }
            }
        }

        return new AgentResponse(content, null);
    }

    private double extractTokPerSec(String jsonResponse, long wallClockMs) {
        try {
            JsonNode root = mapper.readTree(jsonResponse);

            int evalCount = root.path("eval_count").asInt(0);
            long evalDuration = root.path("eval_duration").asLong(0);
            if (evalCount > 0 && evalDuration > 0) {
                double tokPerSec = evalCount / (evalDuration / 1_000_000_000.0);
                logger.fine("[TOK/S] Ollama native: " + String.format("%.1f", tokPerSec) + " tok/s (eval_count=" + evalCount + ")");
                return tokPerSec;
            }

            int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
            if (completionTokens > 0 && wallClockMs > 0) {
                double seconds = wallClockMs / 1000.0;
                double tokPerSec = completionTokens / seconds;
                logger.fine("[TOK/S] Wall-clock: " + String.format("%.1f", tokPerSec) + " tok/s (tokens=" + completionTokens + " duration=" + wallClockMs + "ms)");
                return tokPerSec;
            }
        } catch (Exception e) {
            logger.fine("[TOK/S] No se pudo extraer telemetria: " + e.getMessage());
        }
        return 0.0;
    }

    private String extractJsonBlock(String content) {
        String trimmed = content.trim();

        int jsonStart = content.indexOf("```json");
        if (jsonStart != -1) {
            int end = content.indexOf("```", jsonStart + 7);
            if (end != -1) {
                return content.substring(jsonStart + 7, end).trim();
            }
        }

        int blockStart = content.indexOf("```");
        if (blockStart != -1) {
            int end = content.indexOf("```", blockStart + 3);
            if (end != -1) {
                String potential = content.substring(blockStart + 3, end).trim();
                if (potential.startsWith("{")) return potential;
            }
        }

        int firstBrace = content.indexOf("{");
        if (firstBrace != -1) {
            int depth = 0;
            boolean inString = false;
            boolean escape = false;

            for (int i = firstBrace; i < content.length(); i++) {
                char c = content.charAt(i);

                if (escape) {
                    escape = false;
                    continue;
                }

                if (c == '\\' && inString) {
                    escape = true;
                    continue;
                }

                if (c == '"') {
                    inString = !inString;
                    continue;
                }

                if (!inString) {
                    if (c == '{') depth++;
                    if (c == '}') depth--;

                    if (depth == 0) {
                        return content.substring(firstBrace, i + 1);
                    }
                }
            }
        }

        return null;
    }

    private ToolCall findPrioritizedToolCall(String content) {
        if (content == null) return null;

        String[] priorityOrder = {"fs_patch", "fs_write", "fs_read", "ShellCommand", "web_fetch", "web_search", "gitaction", "fs_mkdir"};

        for (String targetTool : priorityOrder) {
            String pattern = "\"name\":\\s*\"" + targetTool + "\"";
            int toolIndex = -1;

            int searchStart = 0;
            while (searchStart < content.length()) {
                int nameIndex = content.indexOf("\"name\"", searchStart);
                if (nameIndex == -1) break;

                int colonIndex = content.indexOf(":", nameIndex + 6);
                if (colonIndex == -1) break;

                int valueStart = colonIndex + 1;
                while (valueStart < content.length() && Character.isWhitespace(content.charAt(valueStart))) {
                    valueStart++;
                }

                if (valueStart < content.length() && content.charAt(valueStart) == '"') {
                    int valueEnd = content.indexOf('"', valueStart + 1);
                    if (valueEnd != -1) {
                        String toolName = content.substring(valueStart + 1, valueEnd);
                        if (targetTool.equals(toolName)) {
                            toolIndex = nameIndex;
                            break;
                        }
                    }
                }

                searchStart = colonIndex + 1;
            }

            if (toolIndex != -1) {
                int jsonStart = content.lastIndexOf('{', toolIndex);
                if (jsonStart != -1) {
                    String jsonFromHere = content.substring(jsonStart);
                    String cleanJson = extractJsonBlock(jsonFromHere);
                    if (cleanJson != null) {
                        try {
                            JsonNode toolNode = mapper.readTree(cleanJson);
                            if (toolNode.has("name") && toolNode.has("arguments")) {
                                String args = toolNode.get("arguments").toString();
                                if (toolNode.get("arguments").isTextual()) {
                                    args = toolNode.get("arguments").asText();
                                }
                                return new ToolCall(targetTool, args);
                            }
                        } catch (Exception e) {
                            logger.fine("[CLIENT] Error parseando JSON de " + targetTool + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        return null;
    }

    @Override
    public AgentResponse generateWithFullHistory(ArrayNode messages, List<ObjectNode> tools) {
        return generateWithFullHistory(messages, tools, "auto");
    }

    @Override
    public AgentResponse generateWithFullHistory(ArrayNode messages, List<ObjectNode> tools, String toolChoice) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", this.modelName);
            payload.put("temperature", 0.1);
            payload.set("messages", messages);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = payload.putArray("tools");
                toolsArray.addAll(tools);
                payload.put("tool_choice", toolChoice != null ? toolChoice : "auto");
            }

            String jsonBody = mapper.writeValueAsString(payload);

            int toolCount = (tools != null) ? tools.size() : 0;
            logger.info("[REACT-PAYLOAD-DIAG] model=" + this.modelName
                + " messages=" + messages.size()
                + " tools=" + toolCount
                + " toolChoice=" + toolChoice
                + " totalPayload=" + jsonBody.length() + " bytes (" + (jsonBody.length() / 1024) + " KB)");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            logger.info("[REACT-HTTP] Enviando request con " + messages.size() + " mensajes (full history)");

            CompletableFuture<HttpResponse<String>> future =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            long llmStartMs = System.currentTimeMillis();
            HttpResponse<String> response = future.get();
            long llmDurationMs = System.currentTimeMillis() - llmStartMs;

            if (response.statusCode() != 200) {
                String errorBody = response.body() != null ? response.body() : "sin cuerpo";
                logger.severe("[REACT-HTTP-ERROR] " + this.modelName + " HTTP " + response.statusCode()
                    + " payload=" + jsonBody.length() + " bytes: " + errorBody);
                throw new RuntimeException("LLM API HTTP " + response.statusCode()
                    + " (payload=" + (jsonBody.length() / 1024) + "KB): " + errorBody);
            }

            AgentResponse base = parseAgentResponse(response.body());
            double tokPerSec = extractTokPerSec(response.body(), llmDurationMs);
            return new AgentResponse(base.textContent(), base.toolCall(), tokPerSec);
        } catch (CancellationException e) {
            logger.info("[REACT-HTTP] Solicitud cancelada.");
            throw new RuntimeException("Solicitud cancelada por el usuario", e);
        } catch (ExecutionException e) {
            logger.severe("[REACT-HTTP] Error en transporte: " + e.getCause().getMessage());
            throw new RuntimeException("Error de transporte LLM: " + e.getCause().getMessage(), e.getCause());
        } catch (IOException e) {
            logger.severe("[REACT-HTTP] Fallo crítico: " + e.getMessage());
            throw new RuntimeException("Fallo de conexión LLM: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Conexión interrumpida", e);
        }
    }

    @Override
    public String generateStrict(String prompt, List<String> stopSequences, double temperature) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", this.modelName);
            payload.put("temperature", temperature);

            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "user").put("content", prompt);

            if (stopSequences != null && !stopSequences.isEmpty()) {
                ArrayNode stopArray = payload.putArray("stop");
                for (String seq : stopSequences) {
                    stopArray.add(seq);
                }
            }

            String jsonBody = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(Math.min(requestTimeoutSeconds, 120)))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            logger.info("[STRICT] Enviando request con temp=" + temperature + ", stops=" + stopSequences);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.severe("[STRICT] Error en API: " + response.body());
                return "Error: " + response.statusCode();
            }

            JsonNode root = mapper.readTree(response.body());
            if (root.has("choices") && !root.path("choices").isEmpty()) {
                String content = root.path("choices").get(0).path("message").path("content").asText();
                logger.info("[STRICT] Respuesta recibida: " + content.length() + " chars");
                return content;
            }

            return "";
        } catch (IOException | InterruptedException e) {
            logger.severe("[STRICT] Fallo en transporte: " + e.getMessage());
            Thread.currentThread().interrupt();
            return "Error: " + e.getMessage();
        }
    }
}
