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
import dev.fararoni.core.core.reflexion.Evaluation;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.reflexion.critics.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ReflexionEngine {
    private final List<Critic> critics;
    private final Map<Critic.CriticCategory, List<Critic>> criticsByCategory;
    private final boolean parallelEvaluation;
    private final boolean stopOnFirstFail;
    private final int maxRetries;

    private final Map<String, EvaluationStats> statsPerCritic;

    private ReflexionEngine(Builder builder) {
        this.critics = new CopyOnWriteArrayList<>(builder.critics);
        this.parallelEvaluation = builder.parallelEvaluation;
        this.stopOnFirstFail = builder.stopOnFirstFail;
        this.maxRetries = builder.maxRetries;
        this.statsPerCritic = new ConcurrentHashMap<>();

        this.criticsByCategory = critics.stream()
            .filter(Critic::isEnabled)
            .collect(Collectors.groupingBy(
                Critic::getCategory,
                () -> new EnumMap<>(Critic.CriticCategory.class),
                Collectors.toList()
            ));
    }

    public ReflexionResult reflect(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Instant start = Instant.now();
        List<Evaluation> evaluations = new ArrayList<>();

        if (parallelEvaluation) {
            evaluations = evaluateInParallel(response, context);
        } else {
            evaluations = evaluateSequentially(response, context);
        }

        Duration duration = Duration.between(start, Instant.now());

        return buildResult(evaluations, duration);
    }

    public ReflexionResult reflectByCategory(String response, EvaluationContext context,
                                              Critic.CriticCategory category) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(category, "category must not be null");

        Instant start = Instant.now();
        List<Critic> categoryCritics = criticsByCategory.getOrDefault(category, List.of());

        List<Evaluation> evaluations = categoryCritics.stream()
            .filter(Critic::isEnabled)
            .map(c -> safeEvaluate(c, response, context))
            .toList();

        Duration duration = Duration.between(start, Instant.now());

        return buildResult(evaluations, duration);
    }

    public boolean quickCheck(String response, EvaluationContext context) {
        for (Critic critic : critics) {
            if (!critic.isEnabled()) continue;

            Evaluation eval = safeEvaluate(critic, response, context);
            if (eval.isBlocking()) {
                return false;
            }
        }
        return true;
    }

    public ReflexionEngine addCritic(Critic critic) {
        Objects.requireNonNull(critic, "critic must not be null");
        critics.add(critic);

        criticsByCategory.computeIfAbsent(critic.getCategory(), k -> new ArrayList<>())
            .add(critic);

        return this;
    }

    public boolean removeCritic(String criticName) {
        return critics.removeIf(c -> c.getName().equals(criticName));
    }

    public List<Critic> getCritics() {
        return Collections.unmodifiableList(critics);
    }

    public List<Critic> getCriticsByCategory(Critic.CriticCategory category) {
        return criticsByCategory.getOrDefault(category, List.of());
    }

    public Map<String, EvaluationStats> getStats() {
        return Collections.unmodifiableMap(statsPerCritic);
    }

    public void resetStats() {
        statsPerCritic.clear();
    }

    private List<Evaluation> evaluateSequentially(String response, EvaluationContext context) {
        List<Evaluation> evaluations = new ArrayList<>();

        for (Critic critic : critics) {
            if (!critic.isEnabled()) continue;

            Evaluation eval = safeEvaluate(critic, response, context);
            evaluations.add(eval);
            updateStats(critic.getName(), eval);

            if (stopOnFirstFail && eval.isBlocking()) {
                break;
            }
        }

        return evaluations;
    }

    private List<Evaluation> evaluateInParallel(String response, EvaluationContext context) {
        return critics.parallelStream()
            .filter(Critic::isEnabled)
            .map(c -> {
                Evaluation eval = safeEvaluate(c, response, context);
                updateStats(c.getName(), eval);
                return eval;
            })
            .toList();
    }

    private Evaluation safeEvaluate(Critic critic, String response, EvaluationContext context) {
        try {
            return critic.evaluate(response, context);
        } catch (Exception e) {
            return new Evaluation.Warning(
                critic.getName(),
                List.of("Error en evaluacion: " + e.getMessage()),
                List.of("Verificar configuracion del critico")
            );
        }
    }

    private void updateStats(String criticName, Evaluation eval) {
        statsPerCritic.computeIfAbsent(criticName, k -> new EvaluationStats())
            .record(eval);
    }

    private ReflexionResult buildResult(List<Evaluation> evaluations, Duration duration) {
        List<Evaluation> failures = evaluations.stream()
            .filter(Evaluation::isBlocking)
            .toList();

        List<Evaluation> warnings = evaluations.stream()
            .filter(Evaluation::hasWarnings)
            .toList();

        List<Evaluation> passes = evaluations.stream()
            .filter(Evaluation::isPassed)
            .toList();

        return new ReflexionResult(
            evaluations,
            failures,
            warnings,
            passes,
            duration,
            maxRetries
        );
    }

    public static ReflexionEngine standard() {
        return builder()
            .addCritic(new AssumptionCritic())
            .addCritic(new CompletenessCritic())
            .addCritic(new SecurityCritic())
            .build();
    }

    public static ReflexionEngine forCode() {
        return builder()
            .addCritic(new SyntaxCritic())
            .addCritic(new SecurityCritic())
            .addCritic(new CompletenessCritic().withCheckCodeBlocks(true))
            .build();
    }

    public static ReflexionEngine forSecurity() {
        return builder()
            .addCritic(new SecurityCritic().withFailOnCritical(true))
            .addCritic(new PiiDetectionCritic().withFailOnFinancial(true))
            .withStopOnFirstFail(true)
            .build();
    }

    public static ReflexionEngine minimal() {
        return builder()
            .addCritic(new CompletenessCritic())
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Critic> critics = new ArrayList<>();
        private boolean parallelEvaluation = false;
        private boolean stopOnFirstFail = false;
        private int maxRetries = 3;

        private Builder() {}

        public Builder addCritic(Critic critic) {
            critics.add(Objects.requireNonNull(critic, "critic must not be null"));
            return this;
        }

        public Builder addCritics(List<Critic> critics) {
            this.critics.addAll(critics);
            return this;
        }

        public Builder withParallelEvaluation(boolean parallel) {
            this.parallelEvaluation = parallel;
            return this;
        }

        public Builder withStopOnFirstFail(boolean stop) {
            this.stopOnFirstFail = stop;
            return this;
        }

        public Builder withMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ReflexionEngine build() {
            return new ReflexionEngine(this);
        }
    }

    public static final class EvaluationStats {
        private int totalEvaluations;
        private int passes;
        private int warnings;
        private int failures;
        private int skips;

        public synchronized void record(Evaluation eval) {
            totalEvaluations++;
            switch (eval) {
                case Evaluation.Pass p -> passes++;
                case Evaluation.Warning w -> warnings++;
                case Evaluation.Fail f -> failures++;
                case Evaluation.Skip s -> skips++;
            }
        }

        public int getTotalEvaluations() { return totalEvaluations; }
        public int getPasses() { return passes; }
        public int getWarnings() { return warnings; }
        public int getFailures() { return failures; }
        public int getSkips() { return skips; }

        public double getPassRate() {
            int evaluated = totalEvaluations - skips;
            return evaluated > 0 ? (double) passes / evaluated : 0.0;
        }

        @Override
        public String toString() {
            return String.format("Stats[total=%d, pass=%d, warn=%d, fail=%d, skip=%d, rate=%.1f%%]",
                totalEvaluations, passes, warnings, failures, skips, getPassRate() * 100);
        }
    }
}
