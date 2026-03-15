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
package dev.fararoni.core.tokenizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class RemoteTokenizer implements Tokenizer {
    private final String baseUrl;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public RemoteTokenizer(String baseUrl, String apiKey, int connectTimeoutMs, int readTimeoutMs, int maxRetries) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.maxRetries = Math.max(0, maxRetries);
        this.objectMapper = new ObjectMapper();

        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }

    public RemoteTokenizer(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, 10000, 60000, 3);
    }

    @Override
    public List<Integer> encode(String text) {
        if (text == null) {
            throw new TokenizationException("El texto a tokenizar no puede ser nulo");
        }

        if (text.isEmpty()) {
            return List.of();
        }

        return executeWithRetry(() -> doEncode(text), "encode");
    }

    @Override
    public String decode(List<Integer> tokenIds) {
        if (tokenIds == null) {
            throw new TokenizationException("La lista de token IDs no puede ser nula");
        }

        if (tokenIds.isEmpty()) {
            return "";
        }

        for (int i = 0; i < tokenIds.size(); i++) {
            if (tokenIds.get(i) < 0) {
                throw new TokenizationException("Token ID inválido en posición %d: %d".formatted(i, tokenIds.get(i)));
            }
        }

        return executeWithRetry(() -> doDecode(tokenIds), "decode");
    }

    @Override
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        var tokenIds = encode(text);
        return tokenIds.stream()
            .map(id -> "tok_%d".formatted(id))
            .toList();
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        return encode(text).size();
    }

    private List<Integer> doEncode(String text) {
        try {
            var requestBody = createJsonBody("prompt", text);
            var request = buildRequest("/tokenize", requestBody);

            try (var response = httpClient.newCall(request).execute()) {
                return handleEncodeResponse(response);
            }
        } catch (IOException e) {
            throw TokenizationException.encode(text, e);
        }
    }

    private String doDecode(List<Integer> tokenIds) {
        try {
            var requestBody = createJsonBody("tokens", tokenIds);
            var request = buildRequest("/detokenize", requestBody);

            try (var response = httpClient.newCall(request).execute()) {
                return handleDecodeResponse(response);
            }
        } catch (IOException e) {
            throw TokenizationException.decode(tokenIds.toString(), e);
        }
    }

    private String createJsonBody(String key, Object value) throws IOException {
        return objectMapper.writeValueAsString(java.util.Map.of(key, value));
    }

    private Request buildRequest(String endpoint, String jsonBody) {
        var requestBody = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        var requestBuilder = new Request.Builder()
            .url(baseUrl + endpoint)
            .addHeader("Content-Type", "application/json")
            .post(requestBody);

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        }

        return requestBuilder.build();
    }

    private List<Integer> handleEncodeResponse(Response response) throws IOException {
        if (!response.isSuccessful()) {
            var errorBody = response.body() != null ? response.body().string() : "Sin cuerpo de error";
            throw new TokenizationException("Error en tokenización remota: HTTP %d - %s"
                .formatted(response.code(), errorBody));
        }

        var responseBody = response.body();
        if (responseBody == null) {
            throw new TokenizationException("Respuesta vacía del servidor de tokenización");
        }

        var json = objectMapper.readTree(responseBody.string());
        var tokensNode = json.get("tokens");

        if (tokensNode == null || !tokensNode.isArray()) {
            throw new TokenizationException("Respuesta inválida: no contiene array 'tokens'");
        }

        var tokens = new ArrayList<Integer>();
        for (var tokenNode : tokensNode) {
            if (!tokenNode.isInt()) {
                throw new TokenizationException("Token ID inválido en respuesta: " + tokenNode);
            }
            tokens.add(tokenNode.asInt());
        }

        return tokens;
    }

    private String handleDecodeResponse(Response response) throws IOException {
        if (!response.isSuccessful()) {
            var errorBody = response.body() != null ? response.body().string() : "Sin cuerpo de error";
            throw new TokenizationException("Error en detokenización remota: HTTP %d - %s"
                .formatted(response.code(), errorBody));
        }

        var responseBody = response.body();
        if (responseBody == null) {
            throw new TokenizationException("Respuesta vacía del servidor de detokenización");
        }

        var json = objectMapper.readTree(responseBody.string());
        var promptNode = json.get("prompt");

        if (promptNode == null) {
            throw new TokenizationException("Respuesta inválida: no contiene campo 'prompt'");
        }

        return promptNode.asText("");
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation, String operationName) {
        var lastException = (RuntimeException) null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (TokenizationException e) {
                lastException = e;

                if (e.getCause() == null || !(e.getCause() instanceof IOException)) {
                    throw e;
                }

                if (attempt < maxRetries) {
                    try {
                        var waitTime = (long) Math.pow(2, attempt) * 1000;
                        Thread.sleep(Math.min(waitTime, 10000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw TokenizationException.networkError(operationName, ie);
                    }
                }
            } catch (RuntimeException e) {
                lastException = e;
                if (attempt < maxRetries && isRetryableError(e)) {
                    try {
                        Thread.sleep(1000 * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw TokenizationException.networkError(operationName, ie);
                    }
                } else {
                    throw e;
                }
            }
        }

        throw new TokenizationException(
            "Operación %s falló después de %d intentos".formatted(operationName, maxRetries + 1),
            lastException
        );
    }

    private boolean isRetryableError(Throwable e) {
        return e instanceof IOException ||
               (e.getCause() instanceof IOException) ||
               e.getMessage().toLowerCase().contains("timeout") ||
               e.getMessage().toLowerCase().contains("connection");
    }

    public boolean testConnectivity() {
        try {
            var request = new Request.Builder()
                .url(baseUrl + "/health")
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

            try (var response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public String getServerInfo() {
        return "RemoteTokenizer{url=%s, retries=%d}".formatted(baseUrl, maxRetries);
    }

    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
