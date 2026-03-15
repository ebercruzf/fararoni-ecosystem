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
package dev.fararoni.core.core.gateway;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class CircuitBreaker {
    private final int failureThreshold;
    private final long timeoutDurationMs;
    private final long retryTimeoutMs;

    private volatile State state;
    private volatile int failureCount;
    private volatile LocalDateTime lastFailureTime;
    private volatile LocalDateTime lastSuccessTime;

    public CircuitBreaker(int failureThreshold, long timeoutDurationMs) {
        this.failureThreshold = failureThreshold;
        this.timeoutDurationMs = timeoutDurationMs;
        this.retryTimeoutMs = timeoutDurationMs * 2;
        this.state = State.CLOSED;
        this.failureCount = 0;
    }

    public boolean isOpen() {
        if (state == State.OPEN) {
            if (lastFailureTime != null &&
                ChronoUnit.MILLIS.between(lastFailureTime, LocalDateTime.now()) > retryTimeoutMs) {
                state = State.HALF_OPEN;
                return false;
            }
            return true;
        }
        return false;
    }

    public synchronized void recordSuccess() {
        failureCount = 0;
        lastSuccessTime = LocalDateTime.now();

        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
        }
    }

    public synchronized void recordFailure() {
        recordFailure("Operacion fallida");
    }

    public synchronized void recordFailure(String reason) {
        failureCount++;
        lastFailureTime = LocalDateTime.now();

        if (failureCount >= failureThreshold) {
            state = State.OPEN;
        }
    }

    public synchronized void reset() {
        state = State.CLOSED;
        failureCount = 0;
        lastFailureTime = null;
    }

    public State getState() {
        isOpen();
        return state;
    }

    public <T> T execute(Supplier<T> operation) throws CircuitBreakerOpenException {
        if (isOpen()) {
            throw new CircuitBreakerOpenException(
                "Circuit breaker ABIERTO. Fallos: " + failureCount + "/" + failureThreshold
            );
        }

        try {
            T result = operation.get();
            recordSuccess();
            return result;
        } catch (Exception e) {
            recordFailure(e.getMessage());
            throw e;
        }
    }

    public void executeVoid(Runnable operation) throws CircuitBreakerOpenException {
        if (isOpen()) {
            throw new CircuitBreakerOpenException(
                "Circuit breaker ABIERTO. Fallos: " + failureCount + "/" + failureThreshold
            );
        }

        try {
            operation.run();
            recordSuccess();
        } catch (Exception e) {
            recordFailure(e.getMessage());
            throw e;
        }
    }

    public <T> T executeWithFallback(Supplier<T> operation, Supplier<T> fallback) {
        try {
            return execute(operation);
        } catch (Exception e) {
            return fallback.get();
        }
    }

    public boolean isHealthy() {
        return state == State.CLOSED && failureCount < failureThreshold / 2;
    }

    public Stats getStats() {
        return new Stats(
            getState(),
            failureCount,
            failureThreshold,
            lastFailureTime,
            lastSuccessTime
        );
    }

    public enum State {
        CLOSED("Cerrado - Operacional"),

        OPEN("Abierto - Bloqueado"),

        HALF_OPEN("Semi-Abierto - Probando");

        private final String description;

        State(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }

    public record Stats(
        State state,
        int failureCount,
        int failureThreshold,
        LocalDateTime lastFailureTime,
        LocalDateTime lastSuccessTime
    ) {
        public boolean isHealthy() {
            return state == State.CLOSED && failureCount < failureThreshold / 2;
        }
    }

    public static class Factory {
        public static CircuitBreaker conservative() {
            return new CircuitBreaker(2, 60_000);
        }

        public static CircuitBreaker standard() {
            return new CircuitBreaker(3, 30_000);
        }

        public static CircuitBreaker aggressive() {
            return new CircuitBreaker(5, 15_000);
        }

        public static CircuitBreaker custom(int failureThreshold, long timeoutMs) {
            return new CircuitBreaker(failureThreshold, timeoutMs);
        }
    }
}
