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
package dev.fararoni.core.core.utils;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class RetryEngine {
    private static final int DEFAULT_MAX_RETRIES = 3;

    private static final long DEFAULT_INITIAL_DELAY_MS = 200;

    private static final long DEFAULT_MAX_DELAY_MS = 10_000;

    private static final double DEFAULT_JITTER_PERCENT = 0.20;

    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double jitterPercent;
    private final Predicate<Exception> retryPredicate;

    private RetryEngine(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.initialDelayMs = builder.initialDelayMs;
        this.maxDelayMs = builder.maxDelayMs;
        this.jitterPercent = builder.jitterPercent;
        this.retryPredicate = builder.retryPredicate;
    }

    public static <T> T execute(
            ThrowingSupplier<T> operation,
            Predicate<Exception> retryOn,
            String description) {
        return builder()
            .retryOn(retryOn)
            .execute(operation, description);
    }

    public static <T> T executeAny(ThrowingSupplier<T> operation, String description) {
        return execute(operation, ex -> true, description);
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T run(ThrowingSupplier<T> operation, String description) {
        Objects.requireNonNull(operation, "operation no puede ser null");

        Exception lastException = null;
        long currentDelay = initialDelayMs;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;

                if (!retryPredicate.test(e)) {
                    throw wrapException(e, description, attempt, false);
                }

                if (attempt >= maxRetries) {
                    break;
                }

                sleep(applyJitter(currentDelay));

                currentDelay = Math.min((long) (currentDelay * BACKOFF_MULTIPLIER), maxDelayMs);
            }
        }

        throw new RetryExhaustedException(
            formatMessage(description, maxRetries + 1, true),
            lastException
        );
    }

    private long applyJitter(long baseDelay) {
        if (jitterPercent <= 0) {
            return baseDelay;
        }

        double jitterRange = baseDelay * jitterPercent;
        double jitter = ThreadLocalRandom.current().nextDouble(-jitterRange, jitterRange);
        return Math.max(1, (long) (baseDelay + jitter));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }

    private RuntimeException wrapException(Exception e, String description, int attempt, boolean exhausted) {
        if (e instanceof RuntimeException && !exhausted) {
            return (RuntimeException) e;
        }
        return new RuntimeException(formatMessage(description, attempt + 1, exhausted), e);
    }

    private String formatMessage(String description, int attempts, boolean exhausted) {
        String desc = description != null ? description : "Operation";
        if (exhausted) {
            return String.format("%s failed after %d attempts", desc, attempts);
        }
        return String.format("%s failed on attempt %d", desc, attempts);
    }

    public static final class Builder {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long initialDelayMs = DEFAULT_INITIAL_DELAY_MS;
        private long maxDelayMs = DEFAULT_MAX_DELAY_MS;
        private double jitterPercent = DEFAULT_JITTER_PERCENT;
        private Predicate<Exception> retryPredicate = ex -> true;

        private Builder() {}

        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries debe ser >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialDelayMs(long initialDelayMs) {
            if (initialDelayMs < 0) {
                throw new IllegalArgumentException("initialDelayMs debe ser >= 0");
            }
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder maxDelayMs(long maxDelayMs) {
            if (maxDelayMs < 0) {
                throw new IllegalArgumentException("maxDelayMs debe ser >= 0");
            }
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public Builder jitterPercent(double jitterPercent) {
            if (jitterPercent < 0 || jitterPercent > 1) {
                throw new IllegalArgumentException("jitterPercent debe estar entre 0 y 1");
            }
            this.jitterPercent = jitterPercent;
            return this;
        }

        public Builder retryOn(Predicate<Exception> retryPredicate) {
            this.retryPredicate = Objects.requireNonNull(retryPredicate);
            return this;
        }

        public RetryEngine build() {
            return new RetryEngine(this);
        }

        public <T> T execute(ThrowingSupplier<T> operation, String description) {
            return build().run(operation, description);
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static final class RetryExhaustedException extends RuntimeException {
        public RetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public double getJitterPercent() {
        return jitterPercent;
    }
}
