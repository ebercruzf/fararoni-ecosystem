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

import dev.fararoni.core.core.reflexion.Evaluation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ReflexionResult(
    List<Evaluation> allEvaluations,
    List<Evaluation> failures,
    List<Evaluation> warnings,
    List<Evaluation> passes,
    Duration evaluationDuration,
    int maxRetries
) {
    public static ReflexionResult empty() {
        return new ReflexionResult(List.of(), List.of(), List.of(), List.of(), Duration.ZERO, 0);
    }

    public ReflexionResult {
        allEvaluations = allEvaluations != null ? List.copyOf(allEvaluations) : List.of();
        failures = failures != null ? List.copyOf(failures) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
        passes = passes != null ? List.copyOf(passes) : List.of();
        evaluationDuration = evaluationDuration != null ? evaluationDuration : Duration.ZERO;
    }

    public boolean requiresRegeneration() {
        return !failures.isEmpty();
    }

    public boolean isClean() {
        return failures.isEmpty() && warnings.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    public int getTotalEvaluations() {
        return allEvaluations.size();
    }

    public double getPassRate() {
        if (allEvaluations.isEmpty()) return 1.0;
        return (double) passes.size() / allEvaluations.size();
    }

    public String getFeedbackForLlm() {
        if (failures.isEmpty() && warnings.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Feedback de Evaluacion\n\n");

        if (!failures.isEmpty()) {
            sb.append("### Errores (requieren correccion)\n");
            for (Evaluation fail : failures) {
                sb.append("- ").append(fail.toSummary()).append("\n");
                if (fail instanceof Evaluation.Fail f) {
                    f.suggestedFix().ifPresent(fix ->
                        sb.append("  - **Sugerencia:** ").append(fix).append("\n"));
                }
            }
            sb.append("\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("### Advertencias (considerar mejoras)\n");
            for (Evaluation warn : warnings) {
                sb.append("- ").append(warn.toSummary()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Por favor, corrige los errores indicados en tu respuesta.\n");

        return sb.toString();
    }

    public String getCompactFeedback() {
        if (failures.isEmpty() && warnings.isEmpty()) {
            return "OK";
        }

        List<String> issues = failures.stream()
            .map(Evaluation::toSummary)
            .toList();

        if (!issues.isEmpty()) {
            return "ERRORS: " + String.join("; ", issues);
        }

        return "WARNINGS: " + warnings.stream()
            .map(Evaluation::toSummary)
            .collect(Collectors.joining("; "));
    }

    public Optional<Evaluation.Fail> getFirstFailure() {
        return failures.stream()
            .filter(e -> e instanceof Evaluation.Fail)
            .map(e -> (Evaluation.Fail) e)
            .findFirst();
    }

    public List<String> getAllSuggestions() {
        return failures.stream()
            .filter(e -> e instanceof Evaluation.Fail)
            .map(e -> (Evaluation.Fail) e)
            .map(Evaluation.Fail::suggestedFix)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .toList();
    }

    public boolean shouldRetry(int currentAttempt) {
        return requiresRegeneration() && currentAttempt < maxRetries;
    }

    public int getRemainingRetries(int currentAttempt) {
        return Math.max(0, maxRetries - currentAttempt);
    }

    public String toSummary() {
        return String.format(
            "ReflexionResult[pass=%d, warn=%d, fail=%d, duration=%dms, needsRegen=%s]",
            passes.size(),
            warnings.size(),
            failures.size(),
            evaluationDuration.toMillis(),
            requiresRegeneration()
        );
    }

    public String toDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Reflexion Report ===\n");
        sb.append(String.format("Total evaluations: %d\n", allEvaluations.size()));
        sb.append(String.format("Pass rate: %.1f%%\n", getPassRate() * 100));
        sb.append(String.format("Duration: %d ms\n", evaluationDuration.toMillis()));
        sb.append(String.format("Status: %s\n\n",
            requiresRegeneration() ? "NEEDS REGENERATION" :
                hasWarnings() ? "PASSED WITH WARNINGS" : "CLEAN"));

        if (!failures.isEmpty()) {
            sb.append("--- Failures ---\n");
            failures.forEach(f -> sb.append(f.toSummary()).append("\n"));
            sb.append("\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("--- Warnings ---\n");
            warnings.forEach(w -> sb.append(w.toSummary()).append("\n"));
            sb.append("\n");
        }

        if (!passes.isEmpty()) {
            sb.append("--- Passed ---\n");
            passes.forEach(p -> sb.append(p.toSummary()).append("\n"));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toSummary();
    }
}
