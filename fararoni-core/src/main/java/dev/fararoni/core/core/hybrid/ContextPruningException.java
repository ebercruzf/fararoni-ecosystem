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
package dev.fararoni.core.core.hybrid;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ContextPruningException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final PruningFailureReason reason;

    private final int contextSize;

    public ContextPruningException(String message) {
        super(message);
        this.reason = PruningFailureReason.UNKNOWN;
        this.contextSize = -1;
    }

    public ContextPruningException(String message, Throwable cause) {
        super(message, cause);
        this.reason = PruningFailureReason.AST_PARSING_FAILED;
        this.contextSize = -1;
    }

    public ContextPruningException(String message, PruningFailureReason reason) {
        super(message);
        this.reason = reason;
        this.contextSize = -1;
    }

    public ContextPruningException(String message, PruningFailureReason reason, int contextSize) {
        super(message);
        this.reason = reason;
        this.contextSize = contextSize;
    }

    public PruningFailureReason getReason() {
        return reason;
    }

    public int getContextSize() {
        return contextSize;
    }

    public boolean isOverflow() {
        return reason == PruningFailureReason.CONTEXT_OVERFLOW;
    }

    public boolean isParsingError() {
        return reason == PruningFailureReason.AST_PARSING_FAILED;
    }

    public static ContextPruningException overflow(int currentSize, int maxSize) {
        return new ContextPruningException(
            String.format("Context overflow: %d chars > %d max", currentSize, maxSize),
            PruningFailureReason.CONTEXT_OVERFLOW,
            currentSize
        );
    }

    public static ContextPruningException methodNotFound(String methodName) {
        return new ContextPruningException(
            "Target method not found: " + methodName,
            PruningFailureReason.TARGET_NOT_FOUND
        );
    }

    public static ContextPruningException parsingFailed(Throwable cause) {
        return new ContextPruningException(
            "AST parsing failed: " + cause.getMessage(),
            cause
        );
    }

    public enum PruningFailureReason {
        CONTEXT_OVERFLOW,
        AST_PARSING_FAILED,
        TARGET_NOT_FOUND,
        NO_INTERFACES_FOUND,
        UNKNOWN
    }
}
