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
package dev.fararoni.core.client;

import dev.fararoni.bus.agent.api.client.StreamParser;
import dev.fararoni.core.config.CliConfig;
import dev.fararoni.core.core.audit.AuditLogger;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.GenerationResponse;
import dev.fararoni.core.model.Message;
import dev.fararoni.core.tokenizer.Tokenizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class VllmClient implements LlmClient {
    private final String baseUrl;
    private String apiKey;
    private final String defaultModel;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CliConfig config;
    private final Tokenizer tokenizer;
    private final AuditLogger auditLogger;
    private final StreamParser streamParser;

    private final boolean isReasoningModel;

    private final boolean isOllama;

    public VllmClient(String baseUrl, String apiKey, String defaultModel,
                      CliConfig config, Tokenizer tokenizer, StreamParser streamParser) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.config = config;
        this.tokenizer = tokenizer;
        this.objectMapper = new ObjectMapper();
        this.auditLogger = AuditLogger.getInstance();
        this.streamParser = streamParser != null ? streamParser : new OpenAiStreamParser();

        String modelLower = defaultModel.toLowerCase();
        this.isReasoningModel = modelLower.contains("qwen3") ||
                                modelLower.contains("deepseek-r1") ||
                                modelLower.contains("r1:") ||
                                modelLower.contains("o1-") ||
                                modelLower.contains("o1:") ||
                                modelLower.contains("-thinking");

        this.isOllama = this.baseUrl.contains(":11434");

        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs(), TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(new RetryInterceptor(config.maxRetries()))
            .addInterceptor(new LoggingInterceptor(config.enableDebugMode()))
            .build();
    }

    public VllmClient(String baseUrl, String apiKey, String defaultModel, CliConfig config, Tokenizer tokenizer) {
        this(baseUrl, apiKey, defaultModel, config, tokenizer, new OpenAiStreamParser());
    }

    @Override
    public GenerationResponse generate(GenerationRequest request) {
        return executeWithRetry(() -> doGenerate(request), "generate");
    }

    @Override
    public CompletableFuture<GenerationResponse> generateAsync(GenerationRequest request) {
        return CompletableFuture.supplyAsync(() -> generate(request));
    }

    @Override
    public void generateStream(GenerationRequest request,
                              Consumer<String> onToken,
                              Consumer<Throwable> onError,
                              Runnable onComplete) {
        try {
            doGenerateStream(request, onToken, onError, onComplete);
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    @Override
    public List<Integer> generateTokens(List<Integer> inputTokens, int maxTokens) {
        var request = GenerationRequest.builder()
            .model(defaultModel)
            .promptTokenIds(inputTokens)
            .maxTokens(maxTokens)
            .build();

        var response = generate(request);
        return response.tokenIds() != null ? response.tokenIds() : List.of();
    }

    @Override
    public void generateTokensStream(List<Integer> inputTokens, int maxTokens,
                                   Consumer<Integer> onToken,
                                   Consumer<Throwable> onError,
                                   Runnable onComplete) {
        var request = GenerationRequest.builder()
            .model(defaultModel)
            .promptTokenIds(inputTokens)
            .maxTokens(maxTokens)
            .stream(true)
            .build();

        generateStream(request,
            text -> {
                if (!text.isEmpty()) {
                    var tokens = tokenizer.encode(text);
                    tokens.forEach(onToken);
                }
            },
            onError,
            onComplete
        );
    }

    @Override
    public void generateWithChunking(GenerationRequest request,
                                   Consumer<ChunkResult> onChunk,
                                   Consumer<Throwable> onError,
                                   Runnable onComplete) {
        try {
            int estimatedTokens = estimateRequestTokens(request);
            if (estimatedTokens <= config.contextWindow()) {
                var response = generate(request);
                var chunkResult = new ChunkResult(1, 1, response.text(), response.usage(),
                    response.latencyMs(), true, null);
                onChunk.accept(chunkResult);
                onComplete.run();
                return;
            }

            performChunkedGeneration(request, onChunk, onError, onComplete);
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    @Override
    public ServerStatus checkServerStatus() {
        String[] healthEndpoints = {"/health", "/", "/v1/models"};

        for (String endpoint : healthEndpoints) {
            try {
                var request = new Request.Builder()
                    .url(baseUrl + endpoint)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();

                try (var response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        var modelInfo = getModelInfoInternal();
                        String version = "unknown";

                        if (endpoint.equals("/") && response.body() != null) {
                            String body = response.body().string();
                            if (body.contains("Ollama")) {
                                version = "Ollama";
                            }
                        }

                        return ServerStatus.healthy(version, modelInfo.name(), modelInfo.contextLength());
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return ServerStatus.error("Server not responding on any health endpoint");
    }

    @Override
    public ModelInfo getModelInfo() {
        return getModelInfoInternal();
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    public JsonNode generateWithTools(com.fasterxml.jackson.databind.node.ObjectNode payload) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(payload);

        var httpRequest = new Request.Builder()
            .url(baseUrl + "/v1/chat/completions")
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();

        try (var response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "sin cuerpo";
                throw new IOException("Tool calling error HTTP " + response.code() + ": " + errorBody);
            }

            if (response.body() == null) {
                throw new IOException("Respuesta vacia del servidor");
            }

            return objectMapper.readTree(response.body().string());
        }
    }

    public void updateApiKey(String newApiKey) {
        this.apiKey = newApiKey;
    }

    public StreamParser getStreamParser() {
        return streamParser;
    }

    public String getProviderName() {
        return streamParser.getProviderName();
    }

    public boolean isReasoningModel() {
        return isReasoningModel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModelName() {
        return defaultModel;
    }

    public CliConfig getConfig() {
        return config;
    }

    private GenerationResponse doGenerate(GenerationRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            var finalRequest = prepareRequest(request);

            validateContextSize(finalRequest);

            var jsonBody = objectMapper.writeValueAsString(finalRequest);
            var endpoint = finalRequest.isChatMode() ? "/v1/chat/completions" : "/v1/completions";

            var httpRequest = new Request.Builder()
                .url(baseUrl + endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

            try (var response = httpClient.newCall(httpRequest).execute()) {
                var result = parseGenerationResponse(response, startTime, finalRequest.isChatMode());

                if (result.usage() != null) {
                    auditLogger.logLlmCall(
                        defaultModel,
                        result.usage().promptTokens(),
                        result.usage().completionTokens(),
                        result.latencyMs(),
                        true
                    );
                }

                return result;
            }
        } catch (IOException e) {
            auditLogger.logError(AuditLogger.Category.LLM_CALL, "Error de red en llamada LLM", e);
            throw LlmClientException.networkError("generate", e);
        }
    }

    private void doGenerateStream(GenerationRequest request,
                                 Consumer<String> onToken,
                                 Consumer<Throwable> onError,
                                 Runnable onComplete) {
        try {
            var finalRequest = prepareRequest(request).withStream(true);
            validateContextSize(finalRequest);

            var jsonBody = objectMapper.writeValueAsString(finalRequest);

            var endpoint = finalRequest.isChatMode() ? "/v1/chat/completions" : "/v1/completions";

            var httpRequest = new Request.Builder()
                .url(baseUrl + endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

            var latch = new CountDownLatch(1);
            var errorOccurred = new AtomicBoolean(false);

            var factory = EventSources.createFactory(httpClient);
            factory.newEventSource(httpRequest, new EventSourceListener() {
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if (streamParser.isEndOfStream(data)) {
                        latch.countDown();
                        return;
                    }

                    streamParser.parseChunk(data).ifPresent(content -> {
                        if (!content.isEmpty()) {
                            onToken.accept(content);
                        }
                    });
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    errorOccurred.set(true);
                    onError.accept(t);
                    latch.countDown();
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    latch.countDown();
                }
            });

            var completed = latch.await(config.readTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!completed) {
                throw LlmClientException.timeoutError("streaming", config.readTimeoutMs());
            }

            if (!errorOccurred.get()) {
                onComplete.run();
            }
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private void performChunkedGeneration(GenerationRequest request,
                                        Consumer<ChunkResult> onChunk,
                                        Consumer<Throwable> onError,
                                        Runnable onComplete) {
        try {
            var chunks = createInputChunks(request);
            var responses = new ArrayList<String>();

            for (int i = 0; i < chunks.size(); i++) {
                var chunk = chunks.get(i);
                var isLast = i == chunks.size() - 1;

                if (i > 0) {
                    chunk = addContinuationMarker(chunk, responses);
                }

                try {
                    var response = generate(chunk);
                    var content = response.text();
                    responses.add(content);

                    var chunkResult = new ChunkResult(
                        i + 1,
                        chunks.size(),
                        content,
                        response.usage(),
                        response.latencyMs(),
                        isLast,
                        isLast ? null : "chunk_" + (i + 1)
                    );

                    onChunk.accept(chunkResult);

                    if (!isLast) {
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    onError.accept(new LlmClientException(LlmClientException.ErrorType.MODEL_ERROR,
                        "chunk_" + i, "Error procesando chunk " + (i + 1), e));
                    return;
                }
            }

            onComplete.run();
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private List<GenerationRequest> createInputChunks(GenerationRequest request) {
        var chunks = new ArrayList<GenerationRequest>();
        var maxTokensPerChunk = config.contextWindow() - request.maxTokens() - 200;

        if (request.isChatMode()) {
            var messages = request.messages();
            var currentChunk = new ArrayList<Message>();
            int currentTokens = 0;

            var systemMessage = messages.stream()
                .filter(Message::isSystem)
                .findFirst()
                .orElse(null);

            if (systemMessage != null) {
                currentChunk.add(systemMessage);
                currentTokens += tokenizer.countTokens(systemMessage.content());
            }

            for (var message : messages) {
                if (message.isSystem()) continue;

                int messageTokens = tokenizer.countTokens(message.content());

                if (currentTokens + messageTokens > maxTokensPerChunk) {
                    if (currentChunk.size() > 1) {
                        chunks.add(request.builder()
                            .messages(new ArrayList<>(currentChunk))
                            .build());
                    }

                    currentChunk.clear();
                    if (systemMessage != null) {
                        currentChunk.add(systemMessage);
                        currentTokens = tokenizer.countTokens(systemMessage.content());
                    } else {
                        currentTokens = 0;
                    }
                }

                currentChunk.add(message);
                currentTokens += messageTokens;
            }

            if (currentChunk.size() > 1) {
                chunks.add(request.builder()
                    .messages(new ArrayList<>(currentChunk))
                    .build());
            }
        } else if (request.hasPrompt()) {
            var textChunks = tokenizer.chunkText(request.prompt(), maxTokensPerChunk);
            for (var textChunk : textChunks) {
                chunks.add(request.builder()
                    .prompt(textChunk)
                    .build());
            }
        }

        return chunks;
    }

    private GenerationRequest addContinuationMarker(GenerationRequest chunk, List<String> previousResponses) {
        var continuationPrompt = """
            [CONTINUACIÓN]

            Contexto previo: %s

            Continúa exactamente donde terminó la respuesta anterior, manteniendo coherencia y fluidez.
            No repitas contenido previo.
            """.formatted(String.join(" ", previousResponses.subList(Math.max(0, previousResponses.size() - 2), previousResponses.size())));

        if (chunk.isChatMode()) {
            var messages = new ArrayList<>(chunk.messages());
            messages.add(Message.system(continuationPrompt));
            return chunk.builder().messages(messages).build();
        } else {
            var newPrompt = continuationPrompt + "\n\n" + chunk.prompt();
            return chunk.builder().prompt(newPrompt).build();
        }
    }

    private GenerationRequest prepareRequest(GenerationRequest request) {
        if (request.model() == null || request.model().isBlank()) {
            return request.withModel(defaultModel);
        }
        return request;
    }

    private void validateContextSize(GenerationRequest request) {
        int estimatedTokens = estimateRequestTokens(request);
        if (estimatedTokens > config.contextWindow()) {
            throw LlmClientException.contextExceededError(estimatedTokens, config.contextWindow());
        }
    }

    private int estimateRequestTokens(GenerationRequest request) {
        int estimate = 0;

        if (request.hasTokenIds()) {
            estimate += request.promptTokenIds().size();
        } else if (request.hasPrompt()) {
            estimate += tokenizer.countTokens(request.prompt());
        } else if (request.hasMessages()) {
            estimate += request.messages().stream()
                .mapToInt(m -> tokenizer.countTokens(m.content()))
                .sum();
        }

        return estimate + request.maxTokens();
    }

    private GenerationResponse parseGenerationResponse(Response response, long startTime, boolean isChatMode) throws IOException {
        if (!response.isSuccessful()) {
            var errorBody = response.body() != null ? response.body().string() : "Sin cuerpo de error";

            if (response.code() == 429) {
                long retryAfterSeconds = parseRetryAfterHeader(response);
                throw LlmClientException.rateLimitError(retryAfterSeconds);
            }

            throw LlmClientException.httpError("generate", response.code(), errorBody);
        }

        var responseBody = response.body();
        if (responseBody == null) {
            throw new LlmClientException(LlmClientException.ErrorType.PARSE_ERROR, "Respuesta vacía del servidor");
        }

        var json = objectMapper.readTree(responseBody.string());
        var latencyMs = System.currentTimeMillis() - startTime;

        String text;
        if (isChatMode) {
            var messageNode = json.path("choices").get(0).path("message");
            text = messageNode.path("content").asText("");

            String reasoning = messageNode.path("reasoning").asText("");
            if (!reasoning.isEmpty()) {
                if (Boolean.getBoolean("FARARONI_SHOW_REASONING")) {
                    System.out.println("\n\033[3;90m[THOUGHT] " + reasoning + "\033[0m\n");
                }
                if (text.isEmpty()) {
                    text = reasoning;
                }
            }
        } else {
            text = json.path("choices").get(0).path("text").asText("");
        }

        var usageNode = json.path("usage");
        var usage = GenerationResponse.Usage.of(
            usageNode.path("prompt_tokens").asInt(0),
            usageNode.path("completion_tokens").asInt(0),
            latencyMs
        );

        var finishReason = json.path("choices").get(0).path("finish_reason").asText("stop");

        return new GenerationResponse(text, null, usage, latencyMs, finishReason, false, null);
    }

    @Deprecated(since = "1.1.0", forRemoval = true)
    private String extractContentFromStreamChunk(JsonNode json, boolean isChatMode) {
        if (isChatMode) {
            return json.path("choices").get(0).path("delta").path("content").asText("");
        } else {
            return json.path("choices").get(0).path("text").asText("");
        }
    }

    private long parseRetryAfterHeader(Response response) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                return Long.parseLong(retryAfter.trim());
            } catch (NumberFormatException e) {
                if (config.enableDebugMode()) {
                    System.err.println("[DEBUG] Retry-After no es un número: " + retryAfter);
                }
            }
        }
        return 60;
    }

    private ModelInfo getModelInfoInternal() {
        var features = new java.util.ArrayList<>(List.of("chat", "completion", "streaming", "tokenization"));
        if (isReasoningModel) {
            features.add("reasoning");
        }

        return new ModelInfo(
            defaultModel,
            "transformer",
            config.contextWindow(),
            151936,
            List.copyOf(features)
        );
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation, String operationName) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                return operation.get();
            } catch (LlmClientException e) {
                lastException = e;

                if (!e.isRetryable() || attempt >= config.maxRetries()) {
                    throw e;
                }

                try {
                    Thread.sleep(e.getRetryDelayMs() * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmClientException(LlmClientException.ErrorType.GENERAL,
                        operationName, "Operación interrumpida", ie);
                }
            } catch (Exception e) {
                lastException = e;

                if (attempt >= config.maxRetries()) {
                    throw new LlmClientException(LlmClientException.ErrorType.GENERAL,
                        operationName, "Operación falló después de reintentos", e);
                }

                try {
                    Thread.sleep(1000 * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmClientException(LlmClientException.ErrorType.GENERAL,
                        operationName, "Operación interrumpida", ie);
                }
            }
        }

        throw new LlmClientException(LlmClientException.ErrorType.GENERAL,
            operationName, "Falló después de " + config.maxRetries() + " reintentos", lastException);
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }

        return url;
    }

    private String detectProvider() {
        if (baseUrl.contains("anthropic")) {
            return "anthropic";
        } else if (baseUrl.contains("openai")) {
            return "openai";
        } else if (baseUrl.contains("runpod")) {
            return "runpod";
        }
        return "anthropic";
    }

    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;

        public RetryInterceptor(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            var request = chain.request();
            var response = chain.proceed(request);

            int attempts = 0;
            while (!response.isSuccessful() && attempts < maxRetries && isRetryableStatus(response.code())) {
                response.close();

                try {
                    Thread.sleep(1000 * (attempts + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                response = chain.proceed(request);
                attempts++;
            }

            return response;
        }

        private boolean isRetryableStatus(int code) {
            return code >= 500 || code == 429 || code == 408;
        }
    }

    private static class LoggingInterceptor implements Interceptor {
        private final boolean enableLogging;

        public LoggingInterceptor(boolean enableLogging) {
            this.enableLogging = enableLogging;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            var request = chain.request();

            if (enableLogging) {
                System.out.printf("[HTTP] %s %s%n", request.method(), request.url());
            }

            var response = chain.proceed(request);

            if (enableLogging) {
                System.out.printf("[HTTP] %d %s (%.2f segundos)%n",
                    response.code(), request.url(),
                    (System.currentTimeMillis() - System.currentTimeMillis()) / 1000.0);
            }

            return response;
        }
    }
}
