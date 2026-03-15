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
package dev.fararoni.core.core.llm.providers;

import dev.fararoni.core.core.llm.ChatSession;
import dev.fararoni.core.core.llm.InferenceParameters;
import dev.fararoni.core.core.llm.LLMProvider;
import dev.fararoni.core.core.llm.StreamingLlmCallback;
import dev.fararoni.core.core.llm.streaming.StreamingFileParser;
import dev.fararoni.core.core.llm.streaming.StreamingFileWriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class OllamaProvider implements LLMProvider {
    private static final Logger logger = Logger.getLogger(OllamaProvider.class.getName());

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration DEFAULT_INFERENCE_TIMEOUT = Duration.ofSeconds(120);

    private static final Duration STREAMING_ACTIVITY_TIMEOUT = Duration.ofSeconds(30);

    private static final int METRICS_INTERVAL_TOKENS = 50;

    public OllamaProvider(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

        logger.info("[OLLAMA] Provider inicializado: " + this.baseUrl +
                    " (connectTimeout=" + CONNECT_TIMEOUT.toSeconds() + "s)");
    }

    @Override
    public String generate(List<ChatSession.ChatMessage> history, double temperature) {
        StringBuilder messagesJson = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            ChatSession.ChatMessage msg = history.get(i);
            if (i > 0) messagesJson.append(",");
            messagesJson.append(String.format(
                "{\"role\":\"%s\",\"content\":%s}",
                msg.role().name().toLowerCase(),
                escapeJson(msg.content())
            ));
        }
        messagesJson.append("]");

        String requestBody = String.format("""
            {
                "model": "qwen2.5-coder:1.5b",
                "messages": %s,
                "stream": false,
                "options": {
                    "temperature": %.2f
                }
            }
            """, messagesJson, temperature);

        return sendRequest("/api/chat", requestBody, Duration.ofSeconds(60));
    }

    @Override
    public String generateWithParams(String prompt, InferenceParameters params) {
        String model = params.getModel().orElse("qwen2.5-coder:1.5b");

        StringBuilder options = new StringBuilder();
        options.append(String.format("\"temperature\":%.2f", params.getTemperature()));
        options.append(String.format(",\"top_p\":%.2f", params.getTopP()));
        options.append(String.format(",\"repeat_penalty\":%.2f", params.getRepeatPenalty()));
        options.append(String.format(",\"num_ctx\":%d", params.getNumCtx()));

        if (params.getSeed().isPresent()) {
            options.append(String.format(",\"seed\":%d", params.getSeed().get()));
        }

        String stopJson = "";
        if (params.getStopSequence().isPresent()) {
            stopJson = String.format(",\"stop\":[\"%s\"]", params.getStopSequence().get());
        }

        String requestBody;
        if (params.hasGrammar()) {
            requestBody = String.format("""
                {
                    "model": "%s",
                    "prompt": %s,
                    "stream": false,
                    "options": {%s},
                    "grammar": %s
                    %s
                }
                """, model, escapeJson(prompt), options, escapeJson(params.getGrammar()), stopJson);
            return sendRequest("/api/generate", requestBody, params.getTimeout());
        } else {
            requestBody = String.format("""
                {
                    "model": "%s",
                    "messages": [{"role":"user","content":%s}],
                    "stream": false,
                    "options": {%s}
                    %s
                }
                """, model, escapeJson(prompt), options, stopJson);
            return sendRequest("/api/chat", requestBody, params.getTimeout());
        }
    }

    public String inferStreaming(String model, String systemPrompt, String userMessage,
                                  StreamingLlmCallback callback) {
        boolean useThinking = Boolean.getBoolean("FARARONI_SHOW_REASONING");
        String thinkParam = useThinking ? ",\"think\": true" : "";

        String requestBody = String.format("""
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": %s},
                    {"role": "user", "content": %s}
                ],
                "stream": true%s,
                "options": {
                    "temperature": 0.7,
                    "num_ctx": 32768,
                    "num_predict": 4096
                }
            }
            """, model, escapeJson(systemPrompt), escapeJson(userMessage), thinkParam);

        return sendStreamingRequest("/api/chat", requestBody, callback);
    }

    public String inferStreamingWithParams(InferenceParameters params, String prompt,
                                            StreamingLlmCallback callback) {
        String model = params.getModel().orElse("qwen2.5-coder:32b");

        StringBuilder options = new StringBuilder();
        options.append(String.format("\"temperature\":%.2f", params.getTemperature()));
        options.append(String.format(",\"top_p\":%.2f", params.getTopP()));
        options.append(String.format(",\"num_ctx\":%d", params.getNumCtx()));
        options.append(",\"num_predict\":4096");

        String requestBody = String.format("""
            {
                "model": "%s",
                "messages": [{"role": "user", "content": %s}],
                "stream": true,
                "options": {%s}
            }
            """, model, escapeJson(prompt), options);

        return sendStreamingRequest("/api/chat", requestBody, callback);
    }

    public String inferStreamingParallel(String model, String systemPrompt, String userMessage,
                                          Path outputDir, StreamingLlmCallback callback) {
        StreamingFileWriter writer = new StreamingFileWriter(outputDir, (path, bytes) -> {
            if (callback != null) {
                callback.onFileDetected(path.toString(), "");
            }
        });

        StreamingFileParser parser = new StreamingFileParser(
            path -> writer.startFile(path),
            (path, chunk) -> writer.writeChunk(path, chunk),
            path -> writer.endFile(path)
        );

        writer.start();

        StringBuilder fullResponse = new StringBuilder();
        long startTime = System.currentTimeMillis();
        long lastActivityTime = System.currentTimeMillis();
        int tokenCount = 0;

        try {
            String requestBody = buildStreamingRequestBody(model, systemPrompt, userMessage);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(10))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            logger.info("[STREAMING-PARALLEL] Iniciando con escritura paralela a: " + outputDir);

            HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String error = new String(response.body().readAllBytes());
                throw new RuntimeException("Ollama error " + response.statusCode() + ": " + error);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!writer.isHealthy()) {
                        logger.severe("[STREAMING-PARALLEL] CRITICAL: Writer thread murio inesperadamente");
                        throw new RuntimeException(
                            "[CRITICAL] El pipeline de escritura falló. Abortando para evitar pérdida de datos.");
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastActivityTime > STREAMING_ACTIVITY_TIMEOUT.toMillis()) {
                        logger.warning("[STREAMING-PARALLEL] Activity timeout: " +
                            STREAMING_ACTIVITY_TIMEOUT.toSeconds() + "s sin tokens");
                        if (callback != null) {
                            callback.onError(new StreamingActivityTimeoutException(
                                "Sin tokens por " + STREAMING_ACTIVITY_TIMEOUT.toSeconds() + "s"));
                        }
                        break;
                    }

                    String token = extractStreamingToken(line);
                    if (token != null && !token.isEmpty()) {
                        lastActivityTime = System.currentTimeMillis();
                        tokenCount++;
                        fullResponse.append(token);

                        parser.onToken(token);

                        if (callback != null) {
                            callback.onToken(token);
                        }

                        if (tokenCount % METRICS_INTERVAL_TOKENS == 0) {
                            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                            double tokensPerSecond = elapsed > 0 ? tokenCount / elapsed : 0;
                            if (callback != null) {
                                callback.onMetrics(tokensPerSecond, tokenCount);
                            }
                            final int tc = tokenCount;
                            final double tps = tokensPerSecond;
                            logger.fine(() -> String.format(
                                "[STREAMING-PARALLEL] %d tokens, %.1f tok/s", tc, tps));
                        }
                    }

                    if (line.contains("\"done\":true") || line.contains("\"done\": true")) {
                        break;
                    }
                }
            }

            parser.flush();

            double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
            double finalTps = totalTime > 0 ? tokenCount / totalTime : 0;

            logger.info(String.format("[STREAMING-PARALLEL] Completado: %d tokens en %.1fs (%.1f tok/s)",
                tokenCount, totalTime, finalTps));

            if (callback != null) {
                callback.onComplete(fullResponse.toString());
            }

            return fullResponse.toString();
        } catch (HttpConnectTimeoutException e) {
            logger.severe("[STREAMING-PARALLEL-OFFLINE] Ollama no responde en " +
                CONNECT_TIMEOUT.toSeconds() + "s");
            if (callback != null) {
                callback.onError(new OllamaOfflineException("Ollama no responde", e));
            }
            throw new OllamaOfflineException("Ollama no responde. Ejecute 'ollama serve' primero.", e);
        } catch (ConnectException e) {
            logger.severe("[STREAMING-PARALLEL-OFFLINE] No se puede conectar a " + baseUrl);
            if (callback != null) {
                callback.onError(new OllamaOfflineException("No se puede conectar", e));
            }
            throw new OllamaOfflineException("No se puede conectar a Ollama: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[STREAMING-PARALLEL-ERROR] Error I/O", e);
            if (callback != null) {
                callback.onError(e);
            }
            throw new RuntimeException("Error de streaming paralelo: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (callback != null) {
                callback.onError(e);
            }
            throw new RuntimeException("Streaming paralelo interrumpido", e);
        } finally {
            writer.shutdown();
        }
    }

    private String buildStreamingRequestBody(String model, String systemPrompt, String userMessage) {
        return String.format("""
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": %s},
                    {"role": "user", "content": %s}
                ],
                "stream": true,
                "options": {
                    "temperature": 0.7,
                    "num_ctx": 32768,
                    "num_predict": 4096
                }
            }
            """, model, escapeJson(systemPrompt), escapeJson(userMessage));
    }

    private String sendStreamingRequest(String endpoint, String body, StreamingLlmCallback callback) {
        StringBuilder fullResponse = new StringBuilder();
        long startTime = System.currentTimeMillis();
        int tokenCount = 0;
        long lastActivityTime = System.currentTimeMillis();

        StringBuilder fileBuffer = new StringBuilder();
        String currentFilePath = null;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            logger.info("[STREAMING] Iniciando inferencia streaming a " + endpoint);

            HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String error = new String(response.body().readAllBytes());
                throw new RuntimeException("Ollama error " + response.statusCode() + ": " + error);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastActivityTime > STREAMING_ACTIVITY_TIMEOUT.toMillis()) {
                        logger.warning("[STREAMING] Activity timeout: " +
                            STREAMING_ACTIVITY_TIMEOUT.toSeconds() + "s sin tokens");
                        callback.onError(new StreamingActivityTimeoutException(
                            "Sin tokens por " + STREAMING_ACTIVITY_TIMEOUT.toSeconds() + " segundos"));
                        break;
                    }

                    String token = extractStreamingToken(line);
                    if (token != null && !token.isEmpty()) {
                        lastActivityTime = System.currentTimeMillis();
                        tokenCount++;
                        fullResponse.append(token);
                        fileBuffer.append(token);

                        callback.onToken(token);

                        String bufferStr = fileBuffer.toString();
                        if (bufferStr.contains(">>>FILE:") && bufferStr.contains("\n")) {
                            int fileStart = bufferStr.indexOf(">>>FILE:");
                            int pathEnd = bufferStr.indexOf("\n", fileStart);
                            if (pathEnd > fileStart) {
                                String newPath = bufferStr.substring(fileStart + 8, pathEnd).trim();
                                if (currentFilePath != null && !currentFilePath.equals(newPath)) {
                                    String fileContent = extractFileContent(fileBuffer.toString(), currentFilePath);
                                    if (fileContent != null) {
                                        callback.onFileDetected(currentFilePath, fileContent);
                                        logger.info("[STREAMING] Archivo detectado: " + currentFilePath);
                                    }
                                }
                                currentFilePath = newPath;
                            }
                        }

                        if (tokenCount % METRICS_INTERVAL_TOKENS == 0) {
                            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                            double tokensPerSecond = elapsed > 0 ? tokenCount / elapsed : 0;
                            callback.onMetrics(tokensPerSecond, tokenCount);

                            final int tc = tokenCount;
                            final double tps = tokensPerSecond;
                            logger.fine(() -> String.format("[STREAMING] %d tokens, %.1f tok/s", tc, tps));
                        }
                    }

                    if (line.contains("\"done\":true") || line.contains("\"done\": true")) {
                        break;
                    }
                }
            }

            double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
            double finalTps = totalTime > 0 ? tokenCount / totalTime : 0;

            logger.info(String.format("[STREAMING] Completado: %d tokens en %.1fs (%.1f tok/s)",
                tokenCount, totalTime, finalTps));

            if (currentFilePath != null) {
                String fileContent = extractFileContent(fileBuffer.toString(), currentFilePath);
                if (fileContent != null) {
                    callback.onFileDetected(currentFilePath, fileContent);
                }
            }

            callback.onComplete(fullResponse.toString());
            return fullResponse.toString();
        } catch (HttpConnectTimeoutException e) {
            logger.severe("[STREAMING-OFFLINE] Ollama no responde");
            callback.onError(new OllamaOfflineException("Ollama no responde", e));
            throw new OllamaOfflineException("Ollama no responde", e);
        } catch (ConnectException e) {
            logger.severe("[STREAMING-OFFLINE] No se puede conectar a " + baseUrl);
            callback.onError(new OllamaOfflineException("No se puede conectar", e));
            throw new OllamaOfflineException("No se puede conectar a Ollama", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[STREAMING-ERROR] Error I/O", e);
            callback.onError(e);
            throw new RuntimeException("Error de streaming con Ollama: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.onError(e);
            throw new RuntimeException("Streaming interrumpido", e);
        }
    }

    private String extractStreamingToken(String ndjsonLine) {
        if (ndjsonLine == null || ndjsonLine.isBlank()) {
            return null;
        }

        try {
            JsonNode json = objectMapper.readTree(ndjsonLine);

            if (json.has("message")) {
                JsonNode message = json.path("message");
                StringBuilder result = new StringBuilder();

                String thinking = message.path("thinking").asText("");
                if (!thinking.isEmpty() && Boolean.getBoolean("FARARONI_SHOW_REASONING")) {
                    result.append("[THOUGHT]").append(thinking);
                }

                String content = message.path("content").asText("");
                if (!content.isEmpty()) {
                    result.append(content);
                }

                return result.isEmpty() ? null : result.toString();
            }

            String response = json.path("response").asText("");
            return response.isEmpty() ? null : response;
        } catch (Exception e) {
            return extractStreamingTokenLegacy(ndjsonLine);
        }
    }

    private String extractStreamingTokenLegacy(String jsonLine) {
        int contentStart = jsonLine.indexOf("\"content\":\"");
        if (contentStart == -1) {
            contentStart = jsonLine.indexOf("\"content\": \"");
            if (contentStart == -1) return null;
            contentStart += 12;
        } else {
            contentStart += 11;
        }

        StringBuilder token = new StringBuilder();
        boolean escaped = false;
        for (int i = contentStart; i < jsonLine.length(); i++) {
            char c = jsonLine.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> token.append('\n');
                    case 't' -> token.append('\t');
                    case 'r' -> token.append('\r');
                    case '"' -> token.append('"');
                    case '\\' -> token.append('\\');
                    case 'u' -> {
                        if (i + 4 < jsonLine.length()) {
                            String hex = jsonLine.substring(i + 1, i + 5);
                            try {
                                char unicodeChar = (char) Integer.parseInt(hex, 16);
                                token.append(unicodeChar);
                                i += 4;
                            } catch (NumberFormatException e) {
                                token.append('u');
                            }
                        } else {
                            token.append('u');
                        }
                    }
                    default -> token.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                token.append(c);
            }
        }

        return token.toString();
    }

    private String extractFileContent(String buffer, String filePath) {
        String marker = ">>>FILE:" + filePath;
        int start = buffer.indexOf(marker);
        if (start == -1) return null;

        start = buffer.indexOf("\n", start);
        if (start == -1) return null;
        start++;

        int end = buffer.indexOf(">>>FILE:", start);
        if (end == -1) {
            end = buffer.length();
        }

        return buffer.substring(start, end).trim();
    }

    public static class StreamingActivityTimeoutException extends RuntimeException {
        public StreamingActivityTimeoutException(String message) {
            super(message);
        }
    }

    private String sendRequest(String endpoint, String body, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .timeout(timeout != null ? timeout : DEFAULT_INFERENCE_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            logger.fine(() -> String.format("Ollama request to %s: %s", endpoint, body));

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama error " + response.statusCode() + ": " + response.body());
            }

            String responseBody = response.body();
            logger.fine(() -> "Ollama response: " + responseBody);

            if (endpoint.contains("chat")) {
                return extractChatContent(responseBody);
            } else {
                return extractGenerateContent(responseBody);
            }
        } catch (HttpConnectTimeoutException e) {
            logger.severe("[OLLAMA-OFFLINE] Servicio Ollama no responde en " +
                         CONNECT_TIMEOUT.toSeconds() + "s. Esta ejecutando 'ollama serve'?");
            throw new OllamaOfflineException("Ollama no responde. Ejecute 'ollama serve' primero.", e);
        } catch (HttpTimeoutException e) {
            logger.warning("[OLLAMA-TIMEOUT] Inferencia excedió timeout de " + timeout);
            throw new RuntimeException("Timeout en inferencia Ollama: " + timeout, e);
        } catch (ConnectException e) {
            logger.severe("[OLLAMA-OFFLINE] No se puede conectar a " + baseUrl +
                         ". Puerto cerrado o servicio no iniciado.");
            throw new OllamaOfflineException("No se puede conectar a Ollama: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[OLLAMA-ERROR] Error de I/O con Ollama", e);
            throw new RuntimeException("Error de conexión con Ollama: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Petición a Ollama interrumpida", e);
        }
    }

    public static class OllamaOfflineException extends RuntimeException {
        public OllamaOfflineException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private String extractChatContent(String json) {
        int contentStart = json.indexOf("\"content\":\"");
        if (contentStart == -1) {
            contentStart = json.indexOf("\"content\": \"");
            if (contentStart == -1) {
                logger.warning("No se encontro 'content' en respuesta Ollama: " + json);
                return "";
            }
            contentStart += 12;
        } else {
            contentStart += 11;
        }

        StringBuilder content = new StringBuilder();
        boolean escaped = false;
        for (int i = contentStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> content.append('\n');
                    case 't' -> content.append('\t');
                    case 'r' -> content.append('\r');
                    case '"' -> content.append('"');
                    case '\\' -> content.append('\\');
                    default -> content.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                content.append(c);
            }
        }

        return content.toString();
    }

    private String extractGenerateContent(String json) {
        int responseStart = json.indexOf("\"response\":\"");
        if (responseStart == -1) {
            responseStart = json.indexOf("\"response\": \"");
            if (responseStart == -1) {
                logger.warning("No se encontro 'response' en respuesta Ollama: " + json);
                return "";
            }
            responseStart += 13;
        } else {
            responseStart += 12;
        }

        StringBuilder content = new StringBuilder();
        boolean escaped = false;
        for (int i = responseStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> content.append('\n');
                    case 't' -> content.append('\t');
                    case 'r' -> content.append('\r');
                    case '"' -> content.append('"');
                    case '\\' -> content.append('\\');
                    default -> content.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                content.append(c);
            }
        }

        return content.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
