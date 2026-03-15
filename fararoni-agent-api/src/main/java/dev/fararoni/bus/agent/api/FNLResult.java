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

import dev.fararoni.bus.agent.api.saga.CompensationInstruction;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Generic result type for all FNL operations.
 *
 * <p>This is the standard return type for all FNL tool methods. It encapsulates
 * success/failure status, data, error messages, and optional Saga compensation
 * instructions. This is superior to MCP's untyped JSON responses.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Success with data
 * return FNLResult.success("File created successfully");
 *
 * // Success with Saga compensation
 * return FNLResult.successWithSaga(
 *     "File written",
 *     new CompensationInstruction("FileSkill", "delete", Map.of("path", path))
 * );
 *
 * // Failure
 * return FNLResult.failure("Permission denied: " + path);
 * }</pre>
 *
 * <h2>Monad-like Operations</h2>
 * <pre>{@code
 * FNLResult<String> result = readFile(path);
 *
 * // Map on success
 * FNLResult<Integer> length = result.map(String::length);
 *
 * // FlatMap for chaining
 * FNLResult<String> processed = result.flatMap(this::processContent);
 *
 * // Get with default
 * String content = result.orElse("default");
 * }</pre>
 *
 * <h2>Enterprise Value vs MCP</h2>
 * <ul>
 *   <li>Type-safe: Compiler catches type mismatches</li>
 *   <li>Saga support: Built-in compensation instructions</li>
 *   <li>Timestamps: Automatic timing for auditing</li>
 *   <li>Functional: map/flatMap for clean code</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @param <T> The type of data returned on success
 * @author Eber Cruz
 * @since 1.0.0
 * @see CompensationInstruction
 * @see ToolResponse
 */
public record FNLResult<T>(
    boolean success,
    T data,
    String error,
    Instant timestamp,
    CompensationInstruction undoInstruction
) {

    /**
     * Compact constructor with validation and defaults.
     */
    public FNLResult {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        // Success should have data, failure should have error
        if (success && data == null && error != null) {
            throw new IllegalArgumentException("Success result should not have error message");
        }
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a successful result with data.
     *
     * @param data the result data
     * @param <T> type of data
     * @return success result
     */
    public static <T> FNLResult<T> success(T data) {
        return new FNLResult<>(true, data, null, Instant.now(), null);
    }

    /**
     * Creates a successful result with data and Saga compensation instruction.
     *
     * <p>Use this when the operation modifies state and may need to be undone
     * if a later step in the Saga fails.</p>
     *
     * @param data the result data
     * @param undoInstruction how to undo this operation
     * @param <T> type of data
     * @return success result with compensation
     */
    public static <T> FNLResult<T> successWithSaga(T data, CompensationInstruction undoInstruction) {
        Objects.requireNonNull(undoInstruction, "undoInstruction cannot be null for Saga");
        return new FNLResult<>(true, data, null, Instant.now(), undoInstruction);
    }

    /**
     * Creates a failure result with error message.
     *
     * @param error the error message
     * @param <T> type of data (unused for failure)
     * @return failure result
     */
    public static <T> FNLResult<T> failure(String error) {
        Objects.requireNonNull(error, "error message cannot be null");
        return new FNLResult<>(false, null, error, Instant.now(), null);
    }

    /**
     * Creates a failure result from an exception.
     *
     * @param exception the exception that occurred
     * @param <T> type of data (unused for failure)
     * @return failure result
     */
    public static <T> FNLResult<T> failure(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return new FNLResult<>(false, null, message, Instant.now(), null);
    }

    // ==================== Functional Operations ====================

    /**
     * Maps the data if successful, passes through failure unchanged.
     *
     * @param mapper function to transform the data
     * @param <U> new data type
     * @return transformed result
     */
    public <U> FNLResult<U> map(Function<T, U> mapper) {
        if (success) {
            return FNLResult.success(mapper.apply(data));
        }
        return FNLResult.failure(error);
    }

    /**
     * FlatMaps the data if successful, passes through failure unchanged.
     *
     * @param mapper function that returns a new FNLResult
     * @param <U> new data type
     * @return transformed result
     */
    public <U> FNLResult<U> flatMap(Function<T, FNLResult<U>> mapper) {
        if (success) {
            return mapper.apply(data);
        }
        return FNLResult.failure(error);
    }

    /**
     * Returns the data if successful, or the default value if failed.
     *
     * @param defaultValue value to return on failure
     * @return data or default
     */
    public T orElse(T defaultValue) {
        return success ? data : defaultValue;
    }

    /**
     * Returns the data wrapped in Optional.
     *
     * @return Optional containing data if successful, empty otherwise
     */
    public Optional<T> toOptional() {
        return success ? Optional.ofNullable(data) : Optional.empty();
    }

    /**
     * Checks if this result has a Saga compensation instruction.
     *
     * @return true if compensation is available
     */
    public boolean hasSagaCompensation() {
        return undoInstruction != null;
    }

    /**
     * Gets the compensation instruction as Optional.
     *
     * @return Optional containing instruction if available
     */
    public Optional<CompensationInstruction> getCompensation() {
        return Optional.ofNullable(undoInstruction);
    }

    // ==================== Conversion ====================

    /**
     * Converts this FNLResult to the legacy ToolResponse format.
     *
     * <p>Use this for backward compatibility with existing code.</p>
     *
     * @return equivalent ToolResponse
     */
    public ToolResponse toToolResponse() {
        if (success) {
            return ToolResponse.success(data != null ? data.toString() : null);
        }
        return ToolResponse.error(error);
    }

    /**
     * Creates an FNLResult from a legacy ToolResponse.
     *
     * @param response the legacy response
     * @return equivalent FNLResult
     */
    public static FNLResult<String> fromToolResponse(ToolResponse response) {
        if (response.success()) {
            return FNLResult.success(response.result());
        }
        return FNLResult.failure(response.errorMessage());
    }
}
