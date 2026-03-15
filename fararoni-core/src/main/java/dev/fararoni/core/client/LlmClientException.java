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
package dev.fararoni.core.client;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LlmClientException extends RuntimeException {
    private final ErrorType errorType;
    private final int httpCode;
    private final String operation;
    private final long retryAfterSeconds;

    public LlmClientException(String message) {
        super(message);
        this.errorType = ErrorType.GENERAL;
        this.httpCode = 0;
        this.operation = null;
        this.retryAfterSeconds = 0;
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = ErrorType.GENERAL;
        this.httpCode = 0;
        this.operation = null;
        this.retryAfterSeconds = 0;
    }

    public LlmClientException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.httpCode = 0;
        this.operation = null;
        this.retryAfterSeconds = 0;
    }

    public LlmClientException(ErrorType errorType, String operation, String message) {
        super("Error en %s: %s".formatted(operation, message));
        this.errorType = errorType;
        this.httpCode = 0;
        this.operation = operation;
        this.retryAfterSeconds = 0;
    }

    public LlmClientException(ErrorType errorType, String operation, int httpCode, String message) {
        super("Error en %s (HTTP %d): %s".formatted(operation, httpCode, message));
        this.errorType = errorType;
        this.httpCode = httpCode;
        this.operation = operation;
        this.retryAfterSeconds = 0;
    }

    public LlmClientException(ErrorType errorType, String operation, int httpCode, String message, long retryAfterSeconds) {
        super("Error en %s (HTTP %d): %s".formatted(operation, httpCode, message));
        this.errorType = errorType;
        this.httpCode = httpCode;
        this.operation = operation;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public LlmClientException(ErrorType errorType, String operation, String message, Throwable cause) {
        super("Error en %s: %s".formatted(operation, message), cause);
        this.errorType = errorType;
        this.httpCode = 0;
        this.operation = operation;
        this.retryAfterSeconds = 0;
    }

    public ErrorType getErrorType() { return errorType; }
    public int getHttpCode() { return httpCode; }
    public String getOperation() { return operation; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }

    public enum ErrorType {
        NETWORK,
        AUTHENTICATION,
        VALIDATION,
        RATE_LIMIT,
        CONTEXT_EXCEEDED,
        MODEL_ERROR,
        TIMEOUT,
        PARSE_ERROR,
        GENERAL
    }

    public static LlmClientException networkError(String operation, Throwable cause) {
        return new LlmClientException(ErrorType.NETWORK, operation, "Error de conectividad", cause);
    }

    public static LlmClientException authenticationError(String message) {
        return new LlmClientException(ErrorType.AUTHENTICATION, "authentication", message);
    }

    public static LlmClientException rateLimitError() {
        return new LlmClientException(ErrorType.RATE_LIMIT, "Límite de velocidad excedido. Intenta más tarde.");
    }

    public static LlmClientException rateLimitError(long retryAfterSeconds) {
        return new LlmClientException(
            ErrorType.RATE_LIMIT,
            "generate",
            429,
            "Límite de cuota alcanzado (Rate Limit). Espera " + retryAfterSeconds + " segundos.",
            retryAfterSeconds
        );
    }

    public static LlmClientException rateLimitError(String operation, long retryAfterSeconds, String message) {
        return new LlmClientException(
            ErrorType.RATE_LIMIT,
            operation,
            429,
            message,
            retryAfterSeconds
        );
    }

    public static LlmClientException contextExceededError(int actualTokens, int maxTokens) {
        return new LlmClientException(ErrorType.CONTEXT_EXCEEDED,
            "El contexto excede el límite: %d tokens > %d tokens máximo".formatted(actualTokens, maxTokens));
    }

    public static LlmClientException timeoutError(String operation, long timeoutMs) {
        return new LlmClientException(ErrorType.TIMEOUT, operation,
            "Operación excedió timeout de %d ms".formatted(timeoutMs));
    }

    public static LlmClientException validationError(String field, String message) {
        return new LlmClientException(ErrorType.VALIDATION, "validation",
            "Campo %s: %s".formatted(field, message));
    }

    public static LlmClientException httpError(String operation, int httpCode, String message) {
        var errorType = switch (httpCode) {
            case 401, 403 -> ErrorType.AUTHENTICATION;
            case 429 -> ErrorType.RATE_LIMIT;
            case 400 -> ErrorType.VALIDATION;
            case 408, 504 -> ErrorType.TIMEOUT;
            default -> httpCode >= 500 ? ErrorType.MODEL_ERROR : ErrorType.GENERAL;
        };

        return new LlmClientException(errorType, operation, httpCode, message);
    }

    public static LlmClientException parseError(String operation, String content, Throwable cause) {
        return new LlmClientException(ErrorType.PARSE_ERROR, operation,
            "Error parseando respuesta: " + content, cause);
    }

    public boolean isRetryable() {
        return switch (errorType) {
            case NETWORK, TIMEOUT, RATE_LIMIT -> true;
            case MODEL_ERROR -> httpCode >= 500;
            default -> false;
        };
    }

    public boolean isAuthenticationError() {
        return errorType == ErrorType.AUTHENTICATION;
    }

    public boolean isNetworkError() {
        return errorType == ErrorType.NETWORK;
    }

    public boolean isContextError() {
        return errorType == ErrorType.CONTEXT_EXCEEDED;
    }

    public long getRetryDelayMs() {
        if (retryAfterSeconds > 0) {
            return retryAfterSeconds * 1000;
        }

        return switch (errorType) {
            case NETWORK, TIMEOUT -> 1000;
            case RATE_LIMIT -> 5000;
            case MODEL_ERROR -> 2000;
            default -> 0;
        };
    }

    public boolean isRateLimitError() {
        return errorType == ErrorType.RATE_LIMIT;
    }
}
