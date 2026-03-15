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
package dev.fararoni.core.core.reflexion.critics;

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.Evaluation;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import dev.fararoni.core.core.reflexion.testoutput.FailurePatternMatcher;
import dev.fararoni.core.core.reflexion.testoutput.PytestOutputParser;
import dev.fararoni.core.core.reflexion.testoutput.TestFailure;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class FailurePatternCritic implements Critic {
    private static final String NAME = "FailurePatternCritic";

    private final PytestOutputParser parser;

    private final boolean includeAllPatterns;

    private final int maxFailuresToAnalyze;

    public FailurePatternCritic() {
        this(new PytestOutputParser(), true, 10);
    }

    public FailurePatternCritic(PytestOutputParser parser) {
        this(parser, true, 10);
    }

    public FailurePatternCritic(PytestOutputParser parser, boolean includeAllPatterns, int maxFailuresToAnalyze) {
        this.parser = Objects.requireNonNull(parser, "parser no puede ser null");
        this.includeAllPatterns = includeAllPatterns;
        this.maxFailuresToAnalyze = maxFailuresToAnalyze > 0 ? maxFailuresToAnalyze : 10;
    }

    public FailurePatternCritic withAllPatterns(boolean includeAll) {
        return new FailurePatternCritic(this.parser, includeAll, this.maxFailuresToAnalyze);
    }

    public FailurePatternCritic withMaxFailures(int max) {
        return new FailurePatternCritic(this.parser, this.includeAllPatterns, max);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response no puede ser null");
        Objects.requireNonNull(context, "context no puede ser null");

        Optional<String> testOutput = context.getMetadata(EvaluationContext.KEY_TEST_OUTPUT);

        if (testOutput.isEmpty() || testOutput.get().isBlank()) {
            return Evaluation.skip(NAME, "No hay test output en el contexto");
        }

        String output = testOutput.get();

        if (!parser.hasFailures(output)) {
            return Evaluation.pass(NAME, "Todos los tests pasaron - no hay patrones que analizar");
        }

        List<TestFailure> failures = parser.parse(output);

        if (failures.isEmpty()) {
            return Evaluation.skip(NAME, "No se pudieron extraer failures para analisis de patrones");
        }

        Map<FailurePattern, List<TestFailure>> patternGroups = analyzePatterns(failures);

        String reason = generateReason(patternGroups);
        String suggestion = generatePatternSuggestions(patternGroups);
        String evidence = generateEvidence(patternGroups);

        return new Evaluation.Fail(NAME, reason, Optional.of(evidence), Optional.of(suggestion));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Detecta patrones de fallo (OFF_BY_ONE, STRING_TYPO, etc.) y genera sugerencias especificas";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.CODE;
    }

    Map<FailurePattern, List<TestFailure>> analyzePatterns(List<TestFailure> failures) {
        int limit = Math.min(failures.size(), maxFailuresToAnalyze);
        List<TestFailure> toAnalyze = failures.subList(0, limit);

        return toAnalyze.stream()
            .collect(Collectors.groupingBy(FailurePatternMatcher::match));
    }

    FailurePattern getDominantPattern(Map<FailurePattern, List<TestFailure>> patternGroups) {
        return patternGroups.entrySet().stream()
            .filter(e -> e.getKey() != FailurePattern.UNKNOWN)
            .max((e1, e2) -> {
                int cmp = Integer.compare(e1.getValue().size(), e2.getValue().size());
                if (cmp != 0) return cmp;
                return Boolean.compare(e1.getKey().isHighSeverity(), e2.getKey().isHighSeverity());
            })
            .map(Map.Entry::getKey)
            .orElse(FailurePattern.UNKNOWN);
    }

    private String generateReason(Map<FailurePattern, List<TestFailure>> patternGroups) {
        int totalFailures = patternGroups.values().stream().mapToInt(List::size).sum();
        FailurePattern dominant = getDominantPattern(patternGroups);

        if (dominant == FailurePattern.UNKNOWN) {
            return String.format("%d tests fallaron sin patron identificable", totalFailures);
        }

        long dominantCount = patternGroups.getOrDefault(dominant, List.of()).size();

        return String.format("%d tests fallaron - Patron dominante: %s (%d)",
            totalFailures, dominant.name(), dominantCount);
    }

    private String generatePatternSuggestions(Map<FailurePattern, List<TestFailure>> patternGroups) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Sugerencias Basadas en Patrones\n\n");

        patternGroups.entrySet().stream()
            .filter(e -> e.getKey() != FailurePattern.UNKNOWN || patternGroups.size() == 1)
            .sorted((e1, e2) -> {
                int sevCmp = Boolean.compare(e2.getKey().isHighSeverity(), e1.getKey().isHighSeverity());
                if (sevCmp != 0) return sevCmp;
                return Integer.compare(e2.getValue().size(), e1.getValue().size());
            })
            .forEach(entry -> {
                FailurePattern pattern = entry.getKey();
                int count = entry.getValue().size();

                sb.append(String.format("### %s (%d tests)\n", pattern.name(), count));
                sb.append(String.format("- **Descripcion:** %s\n", pattern.getDescription()));
                sb.append(String.format("- **Sugerencia:** %s\n", pattern.getSuggestion()));
                sb.append("\n");
            });

        return sb.toString();
    }

    private String generateEvidence(Map<FailurePattern, List<TestFailure>> patternGroups) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Analisis de Failures por Patron\n\n");

        patternGroups.entrySet().stream()
            .filter(e -> e.getKey() != FailurePattern.UNKNOWN || patternGroups.size() == 1)
            .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
            .limit(3)
            .forEach(entry -> {
                FailurePattern pattern = entry.getKey();
                List<TestFailure> failures = entry.getValue();

                sb.append(String.format("### %s\n", pattern.name()));

                failures.stream().limit(3).forEach(f -> {
                    sb.append(String.format("- **%s**: ", f.testName()));
                    if (f.hasComparison()) {
                        sb.append(String.format("expected=%s, actual=%s", f.expected(), f.actual()));
                    } else {
                        sb.append(f.errorType());
                    }
                    sb.append("\n");
                });

                if (failures.size() > 3) {
                    sb.append(String.format("- ... y %d mas\n", failures.size() - 3));
                }
                sb.append("\n");
            });

        return sb.toString();
    }

    public Map<FailurePattern, Integer> getPatternSummary(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) {
            return Map.of();
        }

        List<TestFailure> failures = parser.parse(testOutput);
        Map<FailurePattern, List<TestFailure>> groups = analyzePatterns(failures);

        Map<FailurePattern, Integer> summary = new EnumMap<>(FailurePattern.class);
        groups.forEach((pattern, list) -> summary.put(pattern, list.size()));

        return summary;
    }

    public FailurePattern getDominantPattern(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) {
            return FailurePattern.UNKNOWN;
        }

        List<TestFailure> failures = parser.parse(testOutput);
        if (failures.isEmpty()) {
            return FailurePattern.UNKNOWN;
        }

        return getDominantPattern(analyzePatterns(failures));
    }

    public Optional<String> generatePatternFeedback(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) {
            return Optional.empty();
        }

        List<TestFailure> failures = parser.parse(testOutput);
        if (failures.isEmpty()) {
            return Optional.empty();
        }

        Map<FailurePattern, List<TestFailure>> groups = analyzePatterns(failures);

        String feedback = generatePatternSuggestions(groups) + "\n" + generateEvidence(groups);
        return Optional.of(feedback);
    }
}
