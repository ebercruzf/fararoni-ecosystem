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
package dev.fararoni.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record GenerationRequest(
    String model,
    String prompt,
    @JsonProperty("prompt_token_ids") List<Integer> promptTokenIds,
    @JsonProperty("max_tokens") int maxTokens,
    double temperature,
    @JsonProperty("top_p") double topP,
    boolean stream,
    List<Message> messages,
    @JsonProperty("context_window") int contextWindow,
    @JsonProperty("continuation_marker") String continuationMarker,
    boolean think
) {
    public GenerationRequest {
        if (maxTokens < 1 || maxTokens > 100000) {
            throw new IllegalArgumentException("maxTokens debe estar entre 1 y 100,000. Recibido: " + maxTokens);
        }

        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("Temperature debe estar entre 0.0 y 2.0. Recibido: " + temperature);
        }

        if (topP < 0.0 || topP > 1.0) {
            throw new IllegalArgumentException("topP debe estar entre 0.0 y 1.0. Recibido: " + topP);
        }

        if (contextWindow < 512 || contextWindow > 200000) {
            throw new IllegalArgumentException("contextWindow debe estar entre 512 y 200,000. Recibido: " + contextWindow);
        }

        if (prompt != null && !prompt.isBlank() && messages != null && !messages.isEmpty()) {
            throw new IllegalArgumentException("No se puede especificar tanto 'prompt' como 'messages' simultáneamente");
        }

        if (promptTokenIds != null && !promptTokenIds.isEmpty() && prompt != null) {
            if (promptTokenIds.stream().anyMatch(id -> id < 0)) {
                throw new IllegalArgumentException("Los token IDs no pueden ser negativos");
            }
        }

        promptTokenIds = promptTokenIds != null ? List.copyOf(promptTokenIds) : null;
        messages = messages != null ? List.copyOf(messages) : null;

        if (continuationMarker != null && continuationMarker.length() > 100) {
            throw new IllegalArgumentException("continuationMarker no puede ser mayor a 100 caracteres");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private String prompt;
        private List<Integer> promptTokenIds;
        private int maxTokens = 2048;
        private double temperature = 0.7;
        private double topP = 0.9;
        private boolean stream = false;
        private List<Message> messages;
        private int contextWindow = 4096;
        private String continuationMarker;
        private boolean think = false;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder promptTokenIds(List<Integer> ids) {
            this.promptTokenIds = ids != null ? new ArrayList<>(ids) : null;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            if (maxTokens < 1) {
                throw new IllegalArgumentException("maxTokens debe ser positivo");
            }
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temp) {
            if (temp < 0.0 || temp > 2.0) {
                throw new IllegalArgumentException("Temperature debe estar entre 0.0 y 2.0");
            }
            this.temperature = temp;
            return this;
        }

        public Builder topP(double topP) {
            if (topP < 0.0 || topP > 1.0) {
                throw new IllegalArgumentException("topP debe estar entre 0.0 y 1.0");
            }
            this.topP = topP;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder think(boolean think) {
            this.think = think;
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages = messages != null ? new ArrayList<>(messages) : null;
            return this;
        }

        public Builder contextWindow(int contextWindow) {
            if (contextWindow < 512) {
                throw new IllegalArgumentException("contextWindow debe ser al menos 512 tokens");
            }
            this.contextWindow = contextWindow;
            return this;
        }

        public Builder continuationMarker(String marker) {
            this.continuationMarker = marker;
            return this;
        }

        public GenerationRequest build() {
            return new GenerationRequest(
                model,
                prompt,
                promptTokenIds,
                maxTokens,
                temperature,
                topP,
                stream,
                messages,
                contextWindow,
                continuationMarker,
                think
            );
        }
    }

    public boolean hasPrompt() {
        return prompt != null && !prompt.isBlank();
    }

    public boolean hasMessages() {
        return messages != null && !messages.isEmpty();
    }

    public boolean hasTokenIds() {
        return promptTokenIds != null && !promptTokenIds.isEmpty();
    }

    public boolean isChatMode() {
        return hasMessages();
    }

    public boolean isCompletionMode() {
        return hasPrompt() && !hasMessages();
    }

    public GenerationRequest withStream(boolean newStream) {
        return new GenerationRequest(
            model, prompt, promptTokenIds, maxTokens, temperature, topP,
            newStream, messages, contextWindow, continuationMarker, think
        );
    }

    public GenerationRequest withModel(String newModel) {
        return new GenerationRequest(
            newModel, prompt, promptTokenIds, maxTokens, temperature, topP,
            stream, messages, contextWindow, continuationMarker, think
        );
    }

    public GenerationRequest withMaxTokens(int newMaxTokens) {
        return new GenerationRequest(
            model, prompt, promptTokenIds, newMaxTokens, temperature, topP,
            stream, messages, contextWindow, continuationMarker, think
        );
    }

    public GenerationRequest withThink(boolean newThink) {
        return new GenerationRequest(
            model, prompt, promptTokenIds, maxTokens, temperature, topP,
            stream, messages, contextWindow, continuationMarker, newThink
        );
    }

    public int estimatedInputTokens() {
        if (hasTokenIds()) {
            return promptTokenIds.size();
        }

        int estimate = 0;
        if (hasPrompt()) {
            estimate += prompt.length() / 4;
        }

        if (hasMessages()) {
            estimate += messages.stream()
                .mapToInt(msg -> msg.content().length() / 4)
                .sum();
        }

        return Math.max(estimate, 1);
    }
}
