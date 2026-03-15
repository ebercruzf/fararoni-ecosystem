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
package dev.fararoni.core.core.swarm.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record SwarmMessage(
    String id,
    String senderId,
    String receiverId,
    String type,
    String content,
    Map<String, Object> metadata,
    Instant timestamp,
    String correlationId
) {
    public static final String TYPE_USER_REQUEST = "USER_REQUEST";
    public static final String TYPE_REQUIREMENTS = "REQUIREMENTS";
    public static final String TYPE_BLUEPRINT = "BLUEPRINT";
    public static final String TYPE_CODE_DRAFT = "CODE_DRAFT";
    public static final String TYPE_TEST_RESULT = "TEST_RESULT";
    public static final String TYPE_BUG_REPORT = "BUG_REPORT";
    public static final String TYPE_APPROVAL = "APPROVAL";
    public static final String TYPE_CODE_APPROVED = "CODE_APPROVED";
    public static final String TYPE_FINAL_DELIVERY = "FINAL_DELIVERY";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_ACKNOWLEDGE = "ACK";

    public static final String TYPE_FUNCTIONAL_SPEC = "FUNCTIONAL_SPEC";
    public static final String TYPE_SYSTEM_DESIGN = "SYSTEM_DESIGN";
    public static final String TYPE_PROJECT_REJECTED = "PROJECT_REJECTED";
    public static final String TYPE_VERIFY_DEPLOYMENT = "VERIFY_DEPLOYMENT";
    public static final String TYPE_FIX_APPLIED = "FIX_APPLIED";
    public static final String TYPE_SRE_FAILURE = "SRE_FAILURE";

    public static final String TYPE_MISSION_CONFIG = "MISSION_CONFIG";

    public SwarmMessage {
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(receiverId, "receiverId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(content, "content must not be null");

        id = id != null ? id : UUID.randomUUID().toString();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        timestamp = timestamp != null ? timestamp : Instant.now();
        correlationId = correlationId != null ? correlationId : id;
    }

    public SwarmMessage(String senderId, String receiverId, String type, String content,
                        Map<String, Object> metadata) {
        this(null, senderId, receiverId, type, content, metadata, null, null);
    }

    public SwarmMessage(String senderId, String receiverId, String type, String content) {
        this(null, senderId, receiverId, type, content, null, null, null);
    }

    public SwarmMessage reply(String newSenderId, String newType, String newContent) {
        return new SwarmMessage(
            null,
            newSenderId,
            this.senderId,
            newType,
            newContent,
            this.metadata,
            null,
            this.id
        );
    }

    public SwarmMessage forward(String newReceiverId) {
        return new SwarmMessage(
            null,
            this.senderId,
            newReceiverId,
            this.type,
            this.content,
            this.metadata,
            null,
            this.correlationId
        );
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, T defaultValue) {
        Object value = metadata.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public boolean isType(String expectedType) {
        return type.equals(expectedType);
    }

    @JsonIgnore
    public boolean isError() {
        return TYPE_ERROR.equals(type);
    }

    @JsonIgnore
    public long ageMs() {
        return Instant.now().toEpochMilli() - timestamp.toEpochMilli();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String senderId;
        private String receiverId;
        private String type;
        private String content;
        private java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        private Instant timestamp;
        private String correlationId;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder from(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder to(String receiverId) {
            this.receiverId = receiverId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public SwarmMessage build() {
            return new SwarmMessage(id, senderId, receiverId, type, content,
                metadata, timestamp, correlationId);
        }
    }

    public static SwarmMessage userRequest(String content) {
        return new SwarmMessage("USER", "PM", TYPE_USER_REQUEST, content);
    }

    public static SwarmMessage error(String senderId, String receiverId, String errorMessage) {
        return builder()
            .from(senderId)
            .to(receiverId)
            .type(TYPE_ERROR)
            .content(errorMessage)
            .build();
    }

    public static SwarmMessage ack(String senderId, String receiverId, String originalMessageId) {
        return builder()
            .from(senderId)
            .to(receiverId)
            .type(TYPE_ACKNOWLEDGE)
            .content("ACK")
            .correlationId(originalMessageId)
            .build();
    }

    @Override
    public String toString() {
        return String.format("SwarmMessage[%s: %s → %s, type=%s, age=%dms]",
            id.substring(0, Math.min(8, id.length())),
            senderId, receiverId, type, ageMs());
    }
}
