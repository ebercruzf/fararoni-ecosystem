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
package dev.fararoni.bus.gateway.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UniversalMessage(
    @JsonProperty("messageId")
    String messageId,

    @JsonProperty("channelId")
    String channelId,

    @JsonProperty("senderId")
    String senderId,

    @JsonProperty("conversationId")
    String conversationId,

    @JsonProperty("type")
    MessageType type,

    @JsonProperty("textContent")
    String textContent,

    @JsonProperty("mediaContent")
    String mediaContent,

    @JsonProperty("mimeType")
    String mimeType,

    @JsonProperty("metadata")
    Map<String, String> metadata,

    @JsonProperty("timestamp")
    Instant timestamp
) {

    /**
     * Message type enumeration.
     */
    public enum MessageType {
        /** Plain text message */
        TEXT,
        /** Audio/voice message (mediaContent = base64 audio) */
        AUDIO,
        /** Image message (mediaContent = base64 image) */
        IMAGE,
        /** File/document message (mediaContent = base64 file) */
        FILE,
        /** IoT event (action in metadata) */
        IOT_EVENT
    }

    /**
     * Creates a simple text message.
     *
     * @param channelId channel identifier
     * @param senderId  sender identifier
     * @param text      message text
     * @return new UniversalMessage instance
     */
    public static UniversalMessage text(String channelId, String senderId, String text) {
        return new UniversalMessage(
            java.util.UUID.randomUUID().toString(),
            channelId,
            senderId,
            null,
            MessageType.TEXT,
            text,
            null,
            null,
            Map.of(),
            Instant.now()
        );
    }

    /**
     * Creates a response message preserving conversation context.
     *
     * @param original the original message to respond to
     * @param text     response text
     * @return new UniversalMessage instance
     */
    public static UniversalMessage reply(UniversalMessage original, String text) {
        return new UniversalMessage(
            java.util.UUID.randomUUID().toString(),
            original.channelId(),
            "fararoni",
            original.conversationId(),
            MessageType.TEXT,
            text,
            null,
            null,
            Map.of(),
            Instant.now()
        );
    }

    /**
     * Checks if this message contains media.
     *
     * @return true if mediaContent is present and non-empty
     */
    public boolean hasMedia() {
        return mediaContent != null && !mediaContent.isBlank();
    }

    /**
     * Checks if this message is a voice/audio message.
     *
     * @return true if type is AUDIO
     */
    public boolean isAudio() {
        return type == MessageType.AUDIO;
    }

    /**
     * Gets a metadata value.
     *
     * @param key metadata key
     * @return value or null if not present
     */
    public String getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
}
