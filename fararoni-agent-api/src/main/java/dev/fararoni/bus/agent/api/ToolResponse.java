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
package dev.fararoni.bus.agent.api;

import java.time.Instant;

/**
 * Immutable data transfer object representing the result of a tool invocation.
 *
 * <p>This record encapsulates the outcome of executing an action, including
 * success status, result data, error information, and execution metadata.</p>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // On success
 * return ToolResponse.success("File created: /workspace/test.txt");
 *
 * // On error
 * return ToolResponse.error("Permission denied: /etc/passwd");
 *
 * // With full metadata
 * return ToolResponse.success(result, requestId, executionTimeMs);
 * }</pre>
 *
 * <h2>JSON Serialization</h2>
 * <p>When serialized to JSON for the LLM:</p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "result": "File created: /workspace/test.txt",
 *   "requestId": "abc123",
 *   "executionTimeMs": 12
 * }
 * }</pre>
 *
 * @param success          whether the execution was successful
 * @param result           the result data (on success)
 * @param errorMessage     the error message (on failure)
 * @param requestId        the corresponding request ID for tracing
 * @param executionTimeMs  execution duration in milliseconds
 * @param completedAt      timestamp of completion
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ToolRequest
 */
public record ToolResponse(
    boolean success,
    String result,
    String errorMessage,
    String requestId,
    long executionTimeMs,
    Instant completedAt
) {

    /**
     * Compact constructor with defaults.
     */
    public ToolResponse {
        if (completedAt == null) {
            completedAt = Instant.now();
        }
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a successful response with a result.
     *
     * @param result the result message or data
     * @return a success response
     */
    public static ToolResponse success(String result) {
        return new ToolResponse(true, result, null, null, 0, null);
    }

    /**
     * Creates a successful response with full metadata.
     *
     * @param result          the result message
     * @param requestId       the request ID for tracing
     * @param executionTimeMs execution duration
     * @return a success response
     */
    public static ToolResponse success(String result, String requestId, long executionTimeMs) {
        return new ToolResponse(true, result, null, requestId, executionTimeMs, null);
    }

    /**
     * Creates an error response.
     *
     * @param errorMessage the error description
     * @return an error response
     */
    public static ToolResponse error(String errorMessage) {
        return new ToolResponse(false, null, errorMessage, null, 0, null);
    }

    /**
     * Creates an error response with full metadata.
     *
     * @param errorMessage    the error description
     * @param requestId       the request ID for tracing
     * @param executionTimeMs execution duration
     * @return an error response
     */
    public static ToolResponse error(String errorMessage, String requestId, long executionTimeMs) {
        return new ToolResponse(false, null, errorMessage, requestId, executionTimeMs, null);
    }

    /**
     * Creates an error response from an exception.
     *
     * @param exception the exception that occurred
     * @return an error response
     */
    public static ToolResponse fromException(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return error(message);
    }

    /**
     * Creates an error response from an exception with metadata.
     *
     * @param exception       the exception
     * @param requestId       the request ID
     * @param executionTimeMs execution duration
     * @return an error response
     */
    public static ToolResponse fromException(Throwable exception, String requestId, long executionTimeMs) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return error(message, requestId, executionTimeMs);
    }

    // ==================== Convenience Methods ====================

    /**
     * Returns the appropriate message (result on success, error on failure).
     *
     * @return the result or error message
     */
    public String getMessage() {
        return success ? result : errorMessage;
    }

    /**
     * Checks if this is an error response.
     *
     * @return true if the execution failed
     */
    public boolean isError() {
        return !success;
    }

    /**
     * Returns a formatted string for logging.
     *
     * @return log-friendly representation
     */
    public String toLogString() {
        if (success) {
            return String.format("[%s] SUCCESS in %dms: %s",
                requestId != null ? requestId : "?",
                executionTimeMs,
                truncate(result, 100));
        } else {
            return String.format("[%s] ERROR in %dms: %s",
                requestId != null ? requestId : "?",
                executionTimeMs,
                errorMessage);
        }
    }

    /**
     * Creates a new response with the given request ID.
     *
     * @param newRequestId the request ID to set
     * @return a new response with the request ID
     */
    public ToolResponse withRequestId(String newRequestId) {
        return new ToolResponse(success, result, errorMessage,
            newRequestId, executionTimeMs, completedAt);
    }

    /**
     * Creates a new response with the given execution time.
     *
     * @param timeMs the execution time in milliseconds
     * @return a new response with the execution time
     */
    public ToolResponse withExecutionTime(long timeMs) {
        return new ToolResponse(success, result, errorMessage,
            requestId, timeMs, completedAt);
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
