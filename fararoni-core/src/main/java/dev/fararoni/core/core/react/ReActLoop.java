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
package dev.fararoni.core.core.react;

import dev.fararoni.core.core.engine.ReflexionGuard;
import dev.fararoni.core.core.engine.ReflexionResult;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.model.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ReActLoop {
    private static final Logger LOG = Logger.getLogger(ReActLoop.class.getName());

    private static final int DEFAULT_MAX_TURNS = 5;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_RETRIES = 0;
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

    private final ReActEngine engine;
    private final ReflexionGuard reflexionGuard;
    private final int maxTurns;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryDelay;
    private final Predicate<ReActResult> retryCondition;
    private final Predicate<ReActResult> stopCondition;
    private final Consumer<ReActStep> stepCallback;
    private final Consumer<RetryEvent> retryCallback;
    private final ExecutorService executor;
    private final boolean validateFinalAnswer;

    private ReActLoop(Builder builder) {
        this.engine = Objects.requireNonNull(builder.engine, "engine required");
        this.reflexionGuard = builder.reflexionGuard;
        this.maxTurns = builder.maxTurns > 0 ? builder.maxTurns : DEFAULT_MAX_TURNS;
        this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        this.maxRetries = builder.maxRetries >= 0 ? builder.maxRetries : DEFAULT_MAX_RETRIES;
        this.retryDelay = builder.retryDelay != null ? builder.retryDelay : DEFAULT_RETRY_DELAY;
        this.retryCondition = builder.retryCondition != null ? builder.retryCondition :
            result -> !result.isSuccess() && result.hasError();
        this.stopCondition = builder.stopCondition;
        this.stepCallback = builder.stepCallback;
        this.retryCallback = builder.retryCallback;
        this.executor = builder.executor != null ? builder.executor :
            Executors.newVirtualThreadPerTaskExecutor();
        this.validateFinalAnswer = builder.validateFinalAnswer;

        if (stepCallback != null) {
            engine.setStepListener(stepCallback);
        }
    }

    public ReActResult run(String task) {
        return run(task, List.of());
    }

    public ReActResult run(String task, List<Message> history) {
        Objects.requireNonNull(task, "task must not be null");

        LOG.info(() -> "[ReActLoop] Starting: " + truncate(task, 60));
        Instant startTime = Instant.now();

        ReActResult result = null;
        int attempt = 0;

        while (attempt <= maxRetries) {
            attempt++;
            final int currentAttempt = attempt;

            if (attempt > 1) {
                LOG.info(() -> "[ReActLoop] Retry attempt " + currentAttempt);
                if (retryCallback != null) {
                    retryCallback.accept(new RetryEvent(attempt, result, retryDelay));
                }
                sleep(retryDelay);
            }

            try {
                result = executeWithTimeout(task, history);

                if (stopCondition != null && stopCondition.test(result)) {
                    LOG.fine("[ReActLoop] Stop condition met");
                    break;
                }

                if (validateFinalAnswer && reflexionGuard != null && result.isSuccess()) {
                    ReflexionResult validation = validateResult(result);
                    if (validation.requiresRegeneration()) {
                        LOG.info("[ReActLoop] Validation failed, retrying...");
                        continue;
                    }
                }

                if (!retryCondition.test(result)) {
                    break;
                }
            } catch (TimeoutException e) {
                LOG.warning("[ReActLoop] Timeout after " + timeout);
                result = ReActResult.timeout(List.of(), Duration.between(startTime, Instant.now()));
                break;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[ReActLoop] Error during execution", e);
                result = ReActResult.error(List.of(), e.getMessage(),
                    Duration.between(startTime, Instant.now()));

                if (!retryCondition.test(result)) {
                    break;
                }
            }
        }

        Duration totalDuration = Duration.between(startTime, Instant.now());
        final int finalAttempt = attempt;
        LOG.info(() -> String.format("[ReActLoop] Completed in %dms after %d attempt(s)",
            totalDuration.toMillis(), finalAttempt));

        return result != null ? result :
            ReActResult.error(List.of(), "No result produced", totalDuration);
    }

    public CompletableFuture<ReActResult> runAsync(String task) {
        return runAsync(task, List.of());
    }

    public CompletableFuture<ReActResult> runAsync(String task, List<Message> history) {
        return CompletableFuture.supplyAsync(() -> run(task, history), executor);
    }

    public List<ReActResult> runBatch(List<String> tasks) {
        List<CompletableFuture<ReActResult>> futures = tasks.stream()
            .map(this::runAsync)
            .toList();

        return futures.stream()
            .map(f -> {
                try {
                    return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    return ReActResult.error(List.of(), e.getMessage(), Duration.ZERO);
                }
            })
            .toList();
    }

    private ReActResult executeWithTimeout(String task, List<Message> history)
            throws TimeoutException {
        CompletableFuture<ReActResult> future = CompletableFuture.supplyAsync(
            () -> engine.execute(task, history), executor);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Execution timed out after " + timeout);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Execution interrupted", e);
        }
    }

    private ReflexionResult validateResult(ReActResult result) {
        if (reflexionGuard == null || result.finalAnswer() == null) {
            return ReflexionResult.empty();
        }

        EvaluationContext ctx = EvaluationContext.builder()
            .responseType(EvaluationContext.ResponseType.TEXT)
            .metadata("source", "ReActLoop")
            .build();

        return reflexionGuard.validate(result.finalAnswer(), ctx).reflexionResult();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() <= len ? s : s.substring(0, len - 3) + "...";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ReActLoop withDefaults(ReActEngine engine) {
        return builder().engine(engine).build();
    }

    public static ReActLoop withRetries(ReActEngine engine, int maxRetries) {
        return builder()
            .engine(engine)
            .maxRetries(maxRetries)
            .build();
    }

    public static ReActLoop withTimeout(ReActEngine engine, Duration timeout) {
        return builder()
            .engine(engine)
            .timeout(timeout)
            .build();
    }

    public static final class Builder {
        private ReActEngine engine;
        private ReflexionGuard reflexionGuard;
        private int maxTurns = DEFAULT_MAX_TURNS;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration retryDelay = DEFAULT_RETRY_DELAY;
        private Predicate<ReActResult> retryCondition;
        private Predicate<ReActResult> stopCondition;
        private Consumer<ReActStep> stepCallback;
        private Consumer<RetryEvent> retryCallback;
        private ExecutorService executor;
        private boolean validateFinalAnswer = false;

        private Builder() {}

        public Builder engine(ReActEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder reflexionGuard(ReflexionGuard guard) {
            this.reflexionGuard = guard;
            return this;
        }

        public Builder maxTurns(int turns) {
            this.maxTurns = turns;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }

        public Builder retryDelay(Duration delay) {
            this.retryDelay = delay;
            return this;
        }

        public Builder retryCondition(Predicate<ReActResult> condition) {
            this.retryCondition = condition;
            return this;
        }

        public Builder stopCondition(Predicate<ReActResult> condition) {
            this.stopCondition = condition;
            return this;
        }

        public Builder onStep(Consumer<ReActStep> callback) {
            this.stepCallback = callback;
            return this;
        }

        public Builder onRetry(Consumer<RetryEvent> callback) {
            this.retryCallback = callback;
            return this;
        }

        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }

        public Builder validateFinalAnswer(boolean validate) {
            this.validateFinalAnswer = validate;
            return this;
        }

        public ReActLoop build() {
            return new ReActLoop(this);
        }
    }

    public record RetryEvent(
        int attemptNumber,
        ReActResult previousResult,
        Duration delayBeforeRetry
    ) {}

    public static class TimeoutException extends Exception {
        public TimeoutException(String message) {
            super(message);
        }
    }
}
