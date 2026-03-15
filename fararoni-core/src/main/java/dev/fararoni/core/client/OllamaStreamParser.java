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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.client.StreamParser;

import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class OllamaStreamParser implements StreamParser {
    private static final String PROVIDER_NAME = "ollama";

    private final ObjectMapper objectMapper;
    private final boolean chatMode;

    public OllamaStreamParser() {
        this(true);
    }

    public OllamaStreamParser(boolean chatMode) {
        this.objectMapper = new ObjectMapper();
        this.chatMode = chatMode;
    }

    @Override
    public Optional<String> parseChunk(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode json = objectMapper.readTree(rawData);

            String content = extractContent(json);

            if (content != null && !content.isEmpty()) {
                return Optional.of(content);
            }
        } catch (Exception e) {
        }

        return Optional.empty();
    }

    @Override
    public boolean isEndOfStream(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return false;
        }

        try {
            JsonNode json = objectMapper.readTree(rawData);
            return json.path("done").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsChatMode() {
        return true;
    }

    @Override
    public Optional<StreamMetadata> parseMetadata(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode json = objectMapper.readTree(rawData);

            if (!json.path("done").asBoolean(false)) {
                return Optional.empty();
            }

            String finishReason = json.has("done_reason")
                ? json.get("done_reason").asText()
                : "stop";

            Integer promptTokens = json.has("prompt_eval_count")
                ? json.get("prompt_eval_count").asInt()
                : null;
            Integer completionTokens = json.has("eval_count")
                ? json.get("eval_count").asInt()
                : null;

            String modelName = json.has("model")
                ? json.get("model").asText()
                : null;

            return Optional.of(new StreamMetadata(
                finishReason,
                promptTokens,
                completionTokens,
                modelName
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String extractContent(JsonNode json) {
        if (chatMode) {
            return json.path("message").path("content").asText(null);
        } else {
            return json.path("response").asText(null);
        }
    }

    public Optional<OllamaMetrics> parseOllamaMetrics(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode json = objectMapper.readTree(rawData);

            if (!json.path("done").asBoolean(false)) {
                return Optional.empty();
            }

            return Optional.of(new OllamaMetrics(
                json.path("total_duration").asLong(0),
                json.path("load_duration").asLong(0),
                json.path("prompt_eval_count").asInt(0),
                json.path("prompt_eval_duration").asLong(0),
                json.path("eval_count").asInt(0),
                json.path("eval_duration").asLong(0)
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public boolean isChatMode() {
        return chatMode;
    }

    public record OllamaMetrics(
        long totalDuration,
        long loadDuration,
        int promptEvalCount,
        long promptEvalDuration,
        int evalCount,
        long evalDuration
    ) {
        public boolean likelyCacheHit() {
            return promptEvalDuration < 10_000_000 && promptEvalCount > 0;
        }

        public double tokensPerSecond() {
            if (evalDuration == 0 || evalCount == 0) {
                return 0.0;
            }
            double seconds = evalDuration / 1_000_000_000.0;
            return evalCount / seconds;
        }

        public double promptEvalMs() {
            return promptEvalDuration / 1_000_000.0;
        }

        public double evalMs() {
            return evalDuration / 1_000_000.0;
        }
    }
}
