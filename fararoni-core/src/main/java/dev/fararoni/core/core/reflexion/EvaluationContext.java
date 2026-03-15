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
package dev.fararoni.core.core.reflexion;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class EvaluationContext {
    public static final String KEY_TEST_OUTPUT = "testOutput";

    public static final String KEY_ATTEMPT_NUMBER = "attemptNumber";

    private final String userPrompt;
    private final List<String> conversationHistory;
    private final ResponseType responseType;
    private final Map<String, Object> metadata;
    private final String sessionId;
    private final int turnNumber;

    private EvaluationContext(Builder builder) {
        this.userPrompt = builder.userPrompt;
        this.conversationHistory = builder.conversationHistory != null
            ? List.copyOf(builder.conversationHistory)
            : List.of();
        this.responseType = builder.responseType != null
            ? builder.responseType
            : ResponseType.TEXT;
        this.metadata = builder.metadata != null
            ? Collections.unmodifiableMap(new HashMap<>(builder.metadata))
            : Map.of();
        this.sessionId = builder.sessionId;
        this.turnNumber = builder.turnNumber;
    }

    public String getUserPrompt() {
        return userPrompt != null ? userPrompt : "";
    }

    public List<String> getConversationHistory() {
        return conversationHistory;
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMetadata(String key) {
        return Optional.ofNullable((T) metadata.get(key));
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadataOrDefault(String key, T defaultValue) {
        Object value = metadata.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public boolean expectsCode() {
        return responseType == ResponseType.CODE;
    }

    public boolean expectsJson() {
        return responseType == ResponseType.JSON;
    }

    public boolean hasHistory() {
        return !conversationHistory.isEmpty();
    }

    public Optional<String> getLastHistoryMessage() {
        if (conversationHistory.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(conversationHistory.get(conversationHistory.size() - 1));
    }

    public EvaluationContext withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(this.metadata);
        newMetadata.put(key, value);

        return builder()
            .userPrompt(this.userPrompt)
            .conversationHistory(this.conversationHistory)
            .responseType(this.responseType)
            .sessionId(this.sessionId)
            .turnNumber(this.turnNumber)
            .metadata(newMetadata)
            .build();
    }

    @Override
    public String toString() {
        return String.format(
            "EvaluationContext[type=%s, promptLen=%d, historySize=%d, turn=%d]",
            responseType,
            userPrompt != null ? userPrompt.length() : 0,
            conversationHistory.size(),
            turnNumber
        );
    }

    public enum ResponseType {
        TEXT,
        CODE,
        JSON,
        MARKDOWN,
        MIXED
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EvaluationContext empty() {
        return builder().build();
    }

    public static EvaluationContext ofPrompt(String userPrompt) {
        return builder().userPrompt(userPrompt).build();
    }

    public static EvaluationContext forCode(String userPrompt, String language) {
        return builder()
            .userPrompt(userPrompt)
            .responseType(ResponseType.CODE)
            .metadata("language", language)
            .build();
    }

    public static EvaluationContext forTestRetry(String userPrompt, String testOutput, int attemptNumber) {
        return builder()
            .userPrompt(userPrompt)
            .responseType(ResponseType.CODE)
            .metadata(KEY_TEST_OUTPUT, testOutput)
            .metadata(KEY_ATTEMPT_NUMBER, attemptNumber)
            .build();
    }

    public static EvaluationContext forTestRetry(String userPrompt, String testOutput,
                                                  int attemptNumber, List<String> history) {
        return builder()
            .userPrompt(userPrompt)
            .responseType(ResponseType.CODE)
            .conversationHistory(history)
            .metadata(KEY_TEST_OUTPUT, testOutput)
            .metadata(KEY_ATTEMPT_NUMBER, attemptNumber)
            .build();
    }

    public static final class Builder {
        private String userPrompt;
        private List<String> conversationHistory;
        private ResponseType responseType;
        private Map<String, Object> metadata;
        private String sessionId;
        private int turnNumber;

        private Builder() {
            this.metadata = new HashMap<>();
        }

        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }

        public Builder conversationHistory(List<String> history) {
            this.conversationHistory = history;
            return this;
        }

        public Builder responseType(ResponseType responseType) {
            this.responseType = responseType;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder turnNumber(int turnNumber) {
            this.turnNumber = turnNumber;
            return this;
        }

        public EvaluationContext build() {
            return new EvaluationContext(this);
        }
    }
}
