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

import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.GenerationResponse;
import dev.fararoni.core.model.Message;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public interface LlmClient {
    public default String getCompletion(String prompt) {
        var request = GenerationRequest.builder()
                .messages(java.util.List.of(Message.user(prompt)))
                .maxTokens(1000)
                .temperature(0.7)
                .build();

        try {
            return generate(request).text();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    GenerationResponse generate(GenerationRequest request);

    CompletableFuture<GenerationResponse> generateAsync(GenerationRequest request);

    void generateStream(GenerationRequest request,
                       Consumer<String> onToken,
                       Consumer<Throwable> onError,
                       Runnable onComplete);

    List<Integer> generateTokens(List<Integer> inputTokens, int maxTokens);

    void generateTokensStream(List<Integer> inputTokens, int maxTokens,
                             Consumer<Integer> onToken,
                             Consumer<Throwable> onError,
                             Runnable onComplete);

    void generateWithChunking(GenerationRequest request,
                             Consumer<ChunkResult> onChunk,
                             Consumer<Throwable> onError,
                             Runnable onComplete);

    default float[] getEmbeddings(String text) {
        return new float[0];
    }

    default java.util.List<float[]> getEmbeddingsBatch(java.util.List<String> texts) {
        return texts.stream().map(this::getEmbeddings).toList();
    }

    ServerStatus checkServerStatus();

    ModelInfo getModelInfo();

    void close();

    record ChunkResult(
        int chunkIndex,
        int totalChunks,
        String content,
        GenerationResponse.Usage usage,
        long latencyMs,
        boolean isComplete,
        String continuationId
    ) {}

    record ServerStatus(
        boolean isAlive,
        String version,
        String modelName,
        int maxContextLength,
        double loadAverage,
        String errorMessage
    ) {
        public static ServerStatus healthy(String version, String modelName, int maxContextLength) {
            return new ServerStatus(true, version, modelName, maxContextLength, 0.0, null);
        }

        public static ServerStatus error(String errorMessage) {
            return new ServerStatus(false, null, null, 0, 0.0, errorMessage);
        }
    }

    record ModelInfo(
        String name,
        String architecture,
        int contextLength,
        int vocabularySize,
        List<String> supportedFeatures
    ) {}
}
