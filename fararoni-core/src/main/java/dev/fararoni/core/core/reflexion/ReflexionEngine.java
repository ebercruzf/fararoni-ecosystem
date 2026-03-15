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
package dev.fararoni.core.core.reflexion;

import dev.fararoni.core.core.reflexion.critics.*;
import dev.fararoni.core.core.reflexion.critics.DiffStrategyCritic;
import dev.fararoni.core.core.reflexion.critics.FailurePatternCritic;
import dev.fararoni.core.core.reflexion.critics.RetryMemoryCritic;
import dev.fararoni.core.core.reflexion.critics.TestOutputCritic;
import dev.fararoni.core.core.reflexion.memory.AttemptMemory;
import dev.fararoni.core.core.reflexion.testoutput.PytestOutputParser;

import java.util.*;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class ReflexionEngine {
    private final List<Critic> critics;

    private final AttemptMemory memory;

    private final FeedbackFormatter formatter;

    private ReflexionEngine(List<Critic> critics, AttemptMemory memory, FeedbackFormatter formatter) {
        this.critics = List.copyOf(critics);
        this.memory = memory;
        this.formatter = formatter;
    }

    public static ReflexionEngine forTestCorrection() {
        AttemptMemory memory = new AttemptMemory();
        PytestOutputParser parser = new PytestOutputParser();

        List<Critic> critics = List.of(
            new TestOutputCritic(parser),
            new FailurePatternCritic(parser),
            new DiffStrategyCritic(parser),
            new RetryMemoryCritic(memory, parser, false)
        );

        return new ReflexionEngine(critics, memory, new FeedbackFormatter());
    }

    public static ReflexionEngine minimal() {
        return new ReflexionEngine(
            List.of(new TestOutputCritic()),
            new AttemptMemory(),
            new FeedbackFormatter()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public ReflexionResult reflect(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response no puede ser null");
        Objects.requireNonNull(context, "context no puede ser null");

        List<Evaluation> evaluations = new ArrayList<>();

        for (Critic critic : critics) {
            try {
                Evaluation eval = critic.evaluate(response, context);
                evaluations.add(eval);
            } catch (Exception e) {
                evaluations.add(new Evaluation.Fail(
                    critic.getName(),
                    "Error ejecutando critic: " + e.getMessage(),
                    Optional.empty(),
                    Optional.empty()
                ));
            }
        }

        return new ReflexionResult(evaluations, formatter);
    }

    public ReflexionResult reflectWithTestOutput(String response, String userPrompt,
                                                  String testOutput, int attemptNumber) {
        EvaluationContext context = EvaluationContext.forTestRetry(
            userPrompt, testOutput, attemptNumber
        );
        return reflect(response, context);
    }

    public List<Critic> getCritics() {
        return critics;
    }

    public AttemptMemory getMemory() {
        return memory;
    }

    public void clearMemory() {
        memory.clearAll();
    }

    public static final class ReflexionResult {
        private final List<Evaluation> evaluations;
        private final FeedbackFormatter formatter;

        ReflexionResult(List<Evaluation> evaluations, FeedbackFormatter formatter) {
            this.evaluations = List.copyOf(evaluations);
            this.formatter = formatter;
        }

        public boolean needsCorrection() {
            return evaluations.stream().anyMatch(e -> e instanceof Evaluation.Fail);
        }

        public boolean hasWarnings() {
            return evaluations.stream().anyMatch(e -> e instanceof Evaluation.Warning);
        }

        public boolean isSuccess() {
            return !needsCorrection() && !hasWarnings();
        }

        public List<Evaluation> getEvaluations() {
            return evaluations;
        }

        public List<Evaluation.Fail> getFailures() {
            return evaluations.stream()
                .filter(e -> e instanceof Evaluation.Fail)
                .map(e -> (Evaluation.Fail) e)
                .toList();
        }

        public List<Evaluation.Warning> getWarnings() {
            return evaluations.stream()
                .filter(e -> e instanceof Evaluation.Warning)
                .map(e -> (Evaluation.Warning) e)
                .toList();
        }

        public String getFormattedFeedback() {
            return formatter.format(evaluations);
        }

        public String getSummary() {
            long fails = evaluations.stream().filter(e -> e instanceof Evaluation.Fail).count();
            long warnings = evaluations.stream().filter(e -> e instanceof Evaluation.Warning).count();
            long passes = evaluations.stream().filter(e -> e instanceof Evaluation.Pass).count();

            return String.format("Reflexion: %d fails, %d warnings, %d passes",
                fails, warnings, passes);
        }
    }

    public static final class Builder {
        private final List<Critic> critics = new ArrayList<>();
        private AttemptMemory memory = new AttemptMemory();
        private FeedbackFormatter formatter = new FeedbackFormatter();

        private Builder() {}

        public Builder addCritic(Critic critic) {
            critics.add(Objects.requireNonNull(critic));
            return this;
        }

        public Builder withDefaultCritics() {
            PytestOutputParser parser = new PytestOutputParser();
            critics.add(new TestOutputCritic(parser));
            critics.add(new FailurePatternCritic(parser));
            critics.add(new DiffStrategyCritic(parser));
            critics.add(new RetryMemoryCritic(memory, parser, false));
            return this;
        }

        public Builder withMemory(AttemptMemory memory) {
            this.memory = Objects.requireNonNull(memory);
            return this;
        }

        public Builder withFormatter(FeedbackFormatter formatter) {
            this.formatter = Objects.requireNonNull(formatter);
            return this;
        }

        public ReflexionEngine build() {
            if (critics.isEmpty()) {
                throw new IllegalStateException("Debe agregar al menos un critic");
            }
            return new ReflexionEngine(critics, memory, formatter);
        }
    }
}
