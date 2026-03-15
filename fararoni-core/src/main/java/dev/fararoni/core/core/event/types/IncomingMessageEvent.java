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
package dev.fararoni.core.core.event.types;

import dev.fararoni.bus.agent.api.io.IncomingMessage;
import dev.fararoni.bus.agent.api.io.IngestionChannel;
import dev.fararoni.core.core.event.AppEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record IncomingMessageEvent(
    String eventId,
    Instant timestamp,
    IncomingMessage message,
    String channelName,
    int processingPriority
) implements AppEvent {
    public IncomingMessageEvent {
        Objects.requireNonNull(message, "message must not be null");
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
        if (channelName == null || channelName.isBlank()) channelName = message.channelName();
        if (processingPriority < 1 || processingPriority > 5) processingPriority = 3;
    }

    public IncomingMessageEvent(IncomingMessage message, String channelName) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            message,
            channelName,
            3
        );
    }

    public IncomingMessageEvent(IncomingMessage message) {
        this(message, message.channelName());
    }

    public IncomingMessageEvent(IncomingMessage message, String channelName, int priority) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            message,
            channelName,
            priority
        );
    }

    public static IncomingMessageEvent urgent(IncomingMessage message, String channelName) {
        return new IncomingMessageEvent(message, channelName, 1);
    }

    public static IncomingMessageEvent background(IncomingMessage message, String channelName) {
        return new IncomingMessageEvent(message, channelName, 5);
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getSource() {
        return "IngestionChannel:" + channelName;
    }

    public boolean isEmail() {
        return message.isEmail();
    }

    public boolean isSlack() {
        return message.isSlack();
    }

    public boolean isJira() {
        return message.isJira();
    }

    public boolean isHighPriority() {
        return processingPriority <= 2;
    }

    public String toLogString() {
        return String.format(
            "IncomingMessageEvent[id=%s, channel=%s, type=%s, source=%s, priority=%d]",
            eventId.substring(0, 8),
            channelName,
            message.channelType(),
            message.sourceId(),
            processingPriority
        );
    }
}
