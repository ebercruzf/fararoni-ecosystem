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
package dev.fararoni.core.core.session;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ErrorContext(
    String errorType,
    String message,
    String filepath,
    int lineNumber,
    String stackTraceSummary,
    long occurredAtEpochMs
) implements Promptable {
    private static final int MAX_STACK_TRACE_LENGTH = 500;

    public static ErrorContext fromException(Throwable exception) {
        if (exception == null) {
            return null;
        }

        String errorType = exception.getClass().getSimpleName();
        String message = exception.getMessage() != null ? exception.getMessage() : "No message";

        String filepath = null;
        int lineNumber = 0;
        StackTraceElement[] stackTrace = exception.getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            StackTraceElement first = stackTrace[0];
            filepath = first.getFileName();
            lineNumber = first.getLineNumber();
        }

        String stackTraceSummary = truncateStackTrace(exception);

        return new ErrorContext(
            errorType,
            message,
            filepath,
            lineNumber,
            stackTraceSummary,
            System.currentTimeMillis()
        );
    }

    public static ErrorContext fromBuildError(String errorType, String message,
                                               String filepath, int lineNumber) {
        return new ErrorContext(
            errorType,
            message,
            filepath,
            lineNumber,
            null,
            System.currentTimeMillis()
        );
    }

    public Instant occurredAt() {
        return Instant.ofEpochMilli(occurredAtEpochMs);
    }

    @JsonIgnore
    public Duration getAge() {
        return Duration.between(occurredAt(), Instant.now());
    }

    public boolean isRecent() {
        return getAge().toMinutes() < 5;
    }

    @Override
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType).append("] ").append(message);

        if (filepath != null) {
            sb.append(" en ").append(filepath);
            if (lineNumber > 0) {
                sb.append(":").append(lineNumber);
            }
        }

        Duration age = getAge();
        sb.append(" (hace ").append(formatDuration(age)).append(")");

        return sb.toString();
    }

    @Override
    public String toStablePrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR DETECTADO:\n");
        sb.append("- Tipo: ").append(errorType).append("\n");
        sb.append("- Mensaje: ").append(message).append("\n");

        if (filepath != null) {
            sb.append("- Archivo: ").append(filepath);
            if (lineNumber > 0) {
                sb.append(":").append(lineNumber);
            }
            sb.append("\n");
        }

        if (stackTraceSummary != null && !stackTraceSummary.isEmpty()) {
            sb.append("- Stack trace:\n").append(stackTraceSummary).append("\n");
        }

        return sb.toString();
    }

    @Deprecated
    public String toPromptContext() {
        return toStablePrompt();
    }

    private static String truncateStackTrace(Throwable exception) {
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        String fullTrace = sw.toString();

        if (fullTrace.length() <= MAX_STACK_TRACE_LENGTH) {
            return fullTrace;
        }

        return fullTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "... [truncado]";
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + " segundos";
        } else if (seconds < 3600) {
            return (seconds / 60) + " minutos";
        } else {
            return (seconds / 3600) + " horas";
        }
    }
}
