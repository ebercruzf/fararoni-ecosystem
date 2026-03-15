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
public class OpenAiStreamParser implements StreamParser {
    private static final String DONE_MARKER = "[DONE]";
    private static final String PROVIDER_NAME = "openai";

    public static final String THOUGHT_PREFIX = "[THOUGHT]";

    private final ObjectMapper objectMapper;
    private final boolean chatMode;

    private final boolean includeReasoning;

    public OpenAiStreamParser() {
        this(true, false);
    }

    public OpenAiStreamParser(boolean chatMode) {
        this(chatMode, false);
    }

    public OpenAiStreamParser(boolean chatMode, boolean includeReasoning) {
        this.objectMapper = new ObjectMapper();
        this.chatMode = chatMode;
        this.includeReasoning = includeReasoning;
    }

    @Override
    public Optional<String> parseChunk(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Optional.empty();
        }

        if (isEndOfStream(rawData)) {
            return Optional.empty();
        }

        try {
            JsonNode json = objectMapper.readTree(rawData);
            StringBuilder result = new StringBuilder();

            boolean showReasoning = includeReasoning || Boolean.getBoolean("FARARONI_SHOW_REASONING");
            if (showReasoning && chatMode) {
                String reasoning = extractReasoning(json);
                if (reasoning != null && !reasoning.isEmpty()) {
                    result.append(THOUGHT_PREFIX).append(reasoning);
                }
            }

            String content = extractContent(json);
            if (content != null && !content.isEmpty()) {
                result.append(content);
            }

            if (!result.isEmpty()) {
                return Optional.of(result.toString());
            }
        } catch (Exception e) {
        }

        return Optional.empty();
    }

    @Override
    public boolean isEndOfStream(String rawData) {
        if (rawData == null) {
            return false;
        }
        return rawData.trim().equals(DONE_MARKER);
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
        if (rawData == null || rawData.isBlank() || isEndOfStream(rawData)) {
            return Optional.empty();
        }

        try {
            JsonNode json = objectMapper.readTree(rawData);

            String finishReason = null;
            JsonNode choicesNode = json.path("choices");
            if (choicesNode.isArray() && !choicesNode.isEmpty()) {
                finishReason = choicesNode.get(0).path("finish_reason").asText(null);
            }

            JsonNode usageNode = json.path("usage");
            Integer promptTokens = usageNode.has("prompt_tokens")
                ? usageNode.get("prompt_tokens").asInt()
                : null;
            Integer completionTokens = usageNode.has("completion_tokens")
                ? usageNode.get("completion_tokens").asInt()
                : null;

            String modelName = json.has("model")
                ? json.get("model").asText()
                : null;

            if (finishReason != null || promptTokens != null || modelName != null) {
                return Optional.of(new StreamMetadata(
                    finishReason,
                    promptTokens,
                    completionTokens,
                    modelName
                ));
            }
        } catch (Exception e) {
        }

        return Optional.empty();
    }

    private String extractContent(JsonNode json) {
        JsonNode choicesNode = json.path("choices");

        if (!choicesNode.isArray() || choicesNode.isEmpty()) {
            return null;
        }

        JsonNode firstChoice = choicesNode.get(0);

        if (chatMode) {
            return firstChoice.path("delta").path("content").asText(null);
        } else {
            return firstChoice.path("text").asText(null);
        }
    }

    private String extractReasoning(JsonNode json) {
        JsonNode choicesNode = json.path("choices");

        if (!choicesNode.isArray() || choicesNode.isEmpty()) {
            String thinking = json.path("thinking").asText(null);
            if (thinking != null && !thinking.isEmpty()) {
                return thinking;
            }
            thinking = json.path("message").path("thinking").asText(null);
            return thinking;
        }

        JsonNode firstChoice = choicesNode.get(0);
        JsonNode delta = firstChoice.path("delta");

        String reasoning = delta.path("reasoning").asText(null);
        if (reasoning != null && !reasoning.isEmpty()) {
            return reasoning;
        }

        return delta.path("thinking").asText(null);
    }

    public boolean isChatMode() {
        return chatMode;
    }

    public boolean isReasoningEnabled() {
        return includeReasoning || Boolean.getBoolean("FARARONI_SHOW_REASONING");
    }
}
