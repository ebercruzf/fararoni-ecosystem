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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class OpenAIProvider implements LLMProvider {
    private static final Logger logger = Logger.getLogger(OpenAIProvider.class.getName());

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public OpenAIProvider(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key es requerida para proveedor de nube");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public String generate(List<ChatSession.ChatMessage> history, double temperature) {
        StringBuilder messagesJson = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            ChatSession.ChatMessage msg = history.get(i);
            if (i > 0) messagesJson.append(",");
            messagesJson.append(String.format(
                "{\"role\":\"%s\",\"content\":%s}",
                mapRole(msg.role()),
                escapeJson(msg.content())
            ));
        }
        messagesJson.append("]");

        String requestBody = String.format("""
            {
                "model": "gpt-4o",
                "messages": %s,
                "temperature": %.2f
            }
            """, messagesJson, temperature);

        return sendRequest(requestBody, Duration.ofSeconds(120));
    }

    @Override
    public String generateWithParams(String prompt, InferenceParameters params) {
        String model = params.getModel().orElse("gpt-4o");

        String messagesJson = String.format("""
            [
                {"role":"system","content":"You are an AI assistant."},
                {"role":"user","content":%s}
            ]
            """, escapeJson(prompt));

        double frequencyPenalty = 0.0;
        if (params.getRepeatPenalty() > 1.0) {
            frequencyPenalty = Math.min(2.0, (params.getRepeatPenalty() - 1.0) * 3);
        }

        String requestBody = String.format("""
            {
                "model": "%s",
                "messages": %s,
                "temperature": %.2f,
                "max_tokens": %d,
                "top_p": %.2f,
                "frequency_penalty": %.2f
            }
            """, model, messagesJson.trim(),
            params.getTemperature(),
            params.getMaxTokens(),
            params.getTopP(),
            frequencyPenalty);

        return sendRequest(requestBody, params.getTimeout());
    }

    private String sendRequest(String body, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            logger.fine(() -> "OpenAI request: " + body);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                throw new RuntimeException("Rate limit excedido. Espera antes de reintentar.");
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("Error de API " + response.statusCode() + ": " + response.body());
            }

            String responseBody = response.body();
            logger.fine(() -> "OpenAI response: " + responseBody);

            return extractContent(responseBody);
        } catch (IOException e) {
            throw new RuntimeException("Error de conexion con API de nube: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Peticion a API de nube interrumpida", e);
        }
    }

    private String extractContent(String json) {
        int messageStart = json.indexOf("\"message\"");
        if (messageStart == -1) {
            logger.warning("No se encontro 'message' en respuesta: " + json);
            return "";
        }

        int contentStart = json.indexOf("\"content\":", messageStart);
        if (contentStart == -1) {
            logger.warning("No se encontro 'content' en respuesta: " + json);
            return "";
        }

        int valueStart = json.indexOf("\"", contentStart + 10);
        if (valueStart == -1) {
            if (json.indexOf("null", contentStart) != -1) {
                return "";
            }
            logger.warning("Formato inesperado en 'content': " + json);
            return "";
        }
        valueStart++;

        StringBuilder content = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart; i < json.length(); i++) {
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

    private String mapRole(ChatSession.ChatRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
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
