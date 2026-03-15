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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ProcessLifecycleEvent(
    String eventId,
    Instant timestamp,
    String processId,
    String operationName,
    Stage stage,
    AppEvent.Severity severity,
    String message,
    Duration duration
) implements AppEvent {
    public enum Stage {
        START,
        PROGRESS,
        SUCCESS,
        FAILURE,
        CANCELLED
    }

    public ProcessLifecycleEvent {
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
        if (severity == null) severity = Severity.INFO;
    }

    public static ProcessLifecycleEvent start(String operationName, String processId) {
        return new ProcessLifecycleEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            processId,
            operationName,
            Stage.START,
            Severity.INFO,
            "Iniciando " + operationName,
            null
        );
    }

    public static ProcessLifecycleEvent progress(String operationName, String processId, String message) {
        return new ProcessLifecycleEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            processId,
            operationName,
            Stage.PROGRESS,
            Severity.DEBUG,
            message,
            null
        );
    }

    public static ProcessLifecycleEvent success(String operationName, String processId) {
        return new ProcessLifecycleEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            processId,
            operationName,
            Stage.SUCCESS,
            Severity.INFO,
            "Completado " + operationName,
            null
        );
    }

    public static ProcessLifecycleEvent success(String operationName, String processId, Duration duration) {
        return new ProcessLifecycleEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            processId,
            operationName,
            Stage.SUCCESS,
            Severity.INFO,
            "Completado " + operationName + " en " + formatDuration(duration),
            duration
        );
    }

    public static ProcessLifecycleEvent failure(String operationName, String processId, String errorMessage) {
        return new ProcessLifecycleEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            processId,
            operationName,
            Stage.FAILURE,
            Severity.ERROR,
            "Falló " + operationName + ": " + errorMessage,
            null
        );
    }

    public static ProcessLifecycleEvent cancelled(String operationName, String processId) {
        return new ProcessLifecycleEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            processId,
            operationName,
            Stage.CANCELLED,
            Severity.WARNING,
            "Cancelado " + operationName,
            null
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
        return operationName;
    }

    public boolean isTerminal() {
        return stage == Stage.SUCCESS || stage == Stage.FAILURE || stage == Stage.CANCELLED;
    }

    public boolean isSuccess() {
        return stage == Stage.SUCCESS;
    }

    public String getStageIcon() {
        return switch (stage) {
            case START -> "▶";
            case PROGRESS -> "⏳";
            case SUCCESS -> "✓";
            case FAILURE -> "✗";
            case CANCELLED -> "⊘";
        };
    }

    private static String formatDuration(Duration d) {
        if (d == null) return "N/A";
        long ms = d.toMillis();
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return String.format("%.1fs", ms / 1000.0);
        return String.format("%.1fmin", ms / 60000.0);
    }

    public String getFormattedMessage() {
        return String.format("[%s] %s %s [%s]",
            getStageIcon(), operationName, message, processId.substring(0, 8));
    }
}
