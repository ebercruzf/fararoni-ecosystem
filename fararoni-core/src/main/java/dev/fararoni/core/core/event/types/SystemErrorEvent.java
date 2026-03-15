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

import dev.fararoni.core.core.event.AppEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record SystemErrorEvent(
    String eventId,
    Instant timestamp,
    String source,
    Throwable error,
    AppEvent.Severity severity,
    String userMessage,
    Map<String, Object> metadata
) implements AppEvent {
    public SystemErrorEvent {
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
        if (source == null) source = "unknown";
        if (severity == null) severity = Severity.ERROR;
        if (metadata == null) metadata = Map.of();
    }

    public SystemErrorEvent(String source, Throwable error, String userMessage) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            source,
            error,
            Severity.ERROR,
            userMessage,
            Map.of()
        );
    }

    public SystemErrorEvent(String source, Throwable error, Severity severity, String userMessage) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            source,
            error,
            severity,
            userMessage,
            Map.of()
        );
    }

    public static SystemErrorEvent critical(String source, Throwable error, String message) {
        return new SystemErrorEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            source,
            error,
            Severity.CRITICAL,
            message,
            Map.of()
        );
    }

    public static SystemErrorEvent warning(String source, String message) {
        return new SystemErrorEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            source,
            null,
            Severity.WARNING,
            message,
            Map.of()
        );
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
        return source;
    }

    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity).append("] ");
        sb.append("[").append(source).append("] ");
        sb.append(userMessage);
        if (error != null) {
            sb.append(" - Cause: ").append(error.getMessage());
        }
        return sb.toString();
    }

    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }
}
