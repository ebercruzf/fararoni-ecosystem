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
package dev.fararoni.core.core.engine;

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.EvaluationContext;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ReflexionGuard {
    private final ReflexionEngine engine;
    private final int maxRetries;
    private final boolean logWarnings;

    private ReflexionGuard(ReflexionEngine engine, int maxRetries, boolean logWarnings) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.maxRetries = maxRetries;
        this.logWarnings = logWarnings;
    }

    public GuardResult validate(String response, EvaluationContext context) {
        ReflexionResult result = engine.reflect(response, context);
        return new GuardResult(response, result);
    }

    public boolean isAcceptable(String response, EvaluationContext context) {
        return engine.quickCheck(response, context);
    }

    public GuardResult guardWithRetry(Supplier<String> generator, EvaluationContext context) {
        Objects.requireNonNull(generator, "generator must not be null");
        Objects.requireNonNull(context, "context must not be null");

        GuardResult bestResult = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String response = generator.get();
            GuardResult result = validate(response, context);

            if (result.isAccepted()) {
                return result;
            }

            if (bestResult == null ||
                result.getFailureCount() < bestResult.getFailureCount()) {
                bestResult = result;
            }

            if (!result.reflexionResult().shouldRetry(attempt)) {
                break;
            }
        }

        return bestResult;
    }

    public GuardResult guardWithFeedback(
        Function<String, String> generator,
        String initialPrompt,
        EvaluationContext context
    ) {
        Objects.requireNonNull(generator, "generator must not be null");
        Objects.requireNonNull(initialPrompt, "initialPrompt must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String currentPrompt = initialPrompt;
        GuardResult bestResult = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String response = generator.apply(currentPrompt);
            GuardResult result = validate(response, context);

            if (result.isAccepted()) {
                return result;
            }

            if (bestResult == null ||
                result.getFailureCount() < bestResult.getFailureCount()) {
                bestResult = result;
            }

            if (!result.reflexionResult().shouldRetry(attempt)) {
                break;
            }

            String feedback = result.reflexionResult().getFeedbackForLlm();
            currentPrompt = initialPrompt + "\n\n" + feedback;
        }

        return bestResult;
    }

    public ReflexionEngine getEngine() {
        return engine;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public ReflexionGuard addCritic(Critic critic) {
        engine.addCritic(critic);
        return this;
    }

    public static ReflexionGuard standard() {
        return new ReflexionGuard(ReflexionEngine.standard(), 3, true);
    }

    public static ReflexionGuard forCode() {
        return new ReflexionGuard(ReflexionEngine.forCode(), 3, true);
    }

    public static ReflexionGuard forSecurity() {
        return new ReflexionGuard(ReflexionEngine.forSecurity(), 2, true);
    }

    public static ReflexionGuard withEngine(ReflexionEngine engine) {
        return new ReflexionGuard(engine, 3, true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ReflexionEngine engine;
        private int maxRetries = 3;
        private boolean logWarnings = true;

        private Builder() {}

        public Builder engine(ReflexionEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder logWarnings(boolean log) {
            this.logWarnings = log;
            return this;
        }

        public ReflexionGuard build() {
            if (engine == null) {
                engine = ReflexionEngine.standard();
            }
            return new ReflexionGuard(engine, maxRetries, logWarnings);
        }
    }

    public record GuardResult(
        String response,
        ReflexionResult reflexionResult
    ) {
        public boolean isAccepted() {
            return !reflexionResult.requiresRegeneration();
        }

        public boolean isPerfect() {
            return reflexionResult.isClean();
        }

        public int getFailureCount() {
            return reflexionResult.failures().size();
        }

        public int getWarningCount() {
            return reflexionResult.warnings().size();
        }

        public Optional<String> getAcceptedResponse() {
            return isAccepted() ? Optional.of(response) : Optional.empty();
        }

        public String getResponseOrDefault(String defaultValue) {
            return isAccepted() ? response : defaultValue;
        }

        public String getFeedback() {
            return reflexionResult.getFeedbackForLlm();
        }

        @Override
        public String toString() {
            return String.format("GuardResult[accepted=%s, fails=%d, warns=%d]",
                isAccepted(), getFailureCount(), getWarningCount());
        }
    }
}
