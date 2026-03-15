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
public record AuditEvent(
    String eventId,
    Instant timestamp,
    String user,
    String action,
    String detail,
    Map<String, Object> context
) implements AppEvent {
    public AuditEvent {
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
        if (user == null) user = "anonymous";
        if (context == null) context = Map.of();
    }

    public static AuditEvent userAction(String user, String action, String detail) {
        return new AuditEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            user,
            action,
            detail,
            Map.of()
        );
    }

    public static AuditEvent commandExecuted(String command) {
        return new AuditEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            System.getProperty("user.name", "unknown"),
            "COMMAND_EXECUTED",
            command,
            Map.of()
        );
    }

    public static AuditEvent fileAccessed(String filePath, String operation) {
        return new AuditEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            System.getProperty("user.name", "unknown"),
            "FILE_" + operation.toUpperCase(),
            filePath,
            Map.of("operation", operation)
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
        return "AUDIT";
    }

    public String getFormattedMessage() {
        return String.format("[AUDIT] User: %s | Action: %s | Detail: %s",
            user, action, detail);
    }
}
