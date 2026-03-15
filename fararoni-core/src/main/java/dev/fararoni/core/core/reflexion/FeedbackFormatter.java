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

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.1.0
 * @since 1.0.0
 */
public final class FeedbackFormatter {
    private static final int MAX_FAILURES = 3;

    private static final int MAX_EVIDENCE_LENGTH = 500;

    private static final int MAX_TEST_OUTPUT_LENGTH = 1000;

    private final boolean includePass;

    private final boolean includeSkip;

    private final int maxContentLength;

    public FeedbackFormatter() {
        this(false, false, MAX_EVIDENCE_LENGTH);
    }

    public FeedbackFormatter(boolean includePass, boolean includeSkip, int maxContentLength) {
        this.includePass = includePass;
        this.includeSkip = includeSkip;
        this.maxContentLength = maxContentLength > 0 ? maxContentLength : MAX_EVIDENCE_LENGTH;
    }

    public String format(List<Evaluation> evaluations) {
        return formatHybrid(evaluations, null);
    }

    public String formatHybrid(List<Evaluation> evaluations, String rawTestOutput) {
        if (evaluations == null || evaluations.isEmpty()) {
            return "No evaluations to show.";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("## CRITICAL FAILURE ANALYSIS:\n\n");

        List<Evaluation.Fail> fails = evaluations.stream()
            .filter(e -> e instanceof Evaluation.Fail)
            .map(e -> (Evaluation.Fail) e)
            .limit(MAX_FAILURES)
            .toList();

        if (fails.isEmpty()) {
            return "Analysis: Tests failed but no specific failure info available. Review code logic.";
        }

        int count = 1;
        for (Evaluation.Fail fail : fails) {
            sb.append(formatFailHybrid(fail, count));
            count++;
        }

        if (rawTestOutput != null && !rawTestOutput.isBlank()) {
            sb.append("\n## Test Output (truncated):\n");
            sb.append(truncate(rawTestOutput, MAX_TEST_OUTPUT_LENGTH));
            sb.append("\n");
        }

        sb.append("\n## REQUIRED FIX:\n");
        sb.append("1. Identify WHY your code produced the wrong output\n");
        sb.append("2. Compare Expected vs Got values carefully\n");
        sb.append("3. Fix ONLY the specific issue - do NOT rewrite everything\n");

        return sb.toString();
    }

    public String formatLegacy(List<Evaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return "Sin evaluaciones para mostrar.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Feedback de Correccion\n\n");

        List<Evaluation.Fail> fails = evaluations.stream()
            .filter(e -> e instanceof Evaluation.Fail)
            .map(e -> (Evaluation.Fail) e)
            .toList();

        List<Evaluation.Warning> warnings = evaluations.stream()
            .filter(e -> e instanceof Evaluation.Warning)
            .map(e -> (Evaluation.Warning) e)
            .toList();

        List<Evaluation.Pass> passes = evaluations.stream()
            .filter(e -> e instanceof Evaluation.Pass)
            .map(e -> (Evaluation.Pass) e)
            .toList();

        if (!fails.isEmpty()) {
            sb.append("## Problemas Detectados\n\n");
            for (Evaluation.Fail fail : fails) {
                sb.append(formatFailLegacy(fail));
                sb.append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            sb.append("## Advertencias\n\n");
            for (Evaluation.Warning warning : warnings) {
                sb.append(formatWarning(warning));
                sb.append("\n");
            }
        }

        if (includePass && !passes.isEmpty()) {
            sb.append("## Validaciones Exitosas\n\n");
            for (Evaluation.Pass pass : passes) {
                sb.append(String.format("### %s\n\n", pass.criticName()));
                sb.append(String.format("%s\n\n", pass.message()));
            }
        }

        if (!fails.isEmpty()) {
            sb.append("\n---\n\n");
            sb.append("## Instrucciones\n\n");
            sb.append("Corrige el codigo siguiendo las sugerencias anteriores.\n");
        }

        return sb.toString();
    }

    public String formatCompact(List<Evaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return "";
        }

        List<Evaluation.Fail> fails = evaluations.stream()
            .filter(e -> e instanceof Evaluation.Fail)
            .map(e -> (Evaluation.Fail) e)
            .limit(MAX_FAILURES)
            .toList();

        if (fails.isEmpty()) {
            return "All tests passed.";
        }

        return fails.stream()
            .map(f -> String.format("- %s: %s", f.criticName(), f.reason()))
            .collect(Collectors.joining("\n"));
    }

    private String formatFailHybrid(Evaluation.Fail fail, int index) {
        StringBuilder sb = new StringBuilder();

        String criticType = extractCriticType(fail.criticName());

        if (criticType.equals("TestOutput")) {
            fail.evidence().ifPresent(evidence -> {
                sb.append(extractDiffLines(evidence));
                sb.append("\n");
            });
        }
        else if (criticType.equals("Pattern") || criticType.equals("FailurePattern")) {
            sb.append("Pattern: ").append(fail.reason()).append("\n");
            fail.suggestedFix().ifPresent(fix ->
                sb.append("Fix: ").append(truncate(fix, 150)).append("\n")
            );
        }
        else {
            sb.append(fail.criticName()).append(": ").append(fail.reason()).append("\n");
            fail.evidence().ifPresent(evidence ->
                sb.append(truncate(evidence, maxContentLength)).append("\n")
            );
        }

        return sb.toString();
    }

    private String extractDiffLines(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "";
        }

        StringBuilder diff = new StringBuilder();
        String[] lines = evidence.split("\n");
        int count = 0;

        for (String line : lines) {
            if (count >= 10) break;

            String trimmed = line.trim();
            String lower = trimmed.toLowerCase();

            if (lower.contains("assertionerror") && (trimmed.contains("==") || trimmed.contains("!="))) {
                diff.append("ASSERTION FAILED: ").append(trimmed).append("\n");
                count++;
            }
            else if (trimmed.startsWith("E") && (trimmed.contains("!=") || trimmed.contains("=="))) {
                diff.append("MISMATCH: ").append(trimmed.substring(1).trim()).append("\n");
                count++;
            }
            else if (lower.contains("expected") || lower.contains("got") || lower.contains("actual")) {
                diff.append(trimmed).append("\n");
                count++;
            }
            else if (trimmed.startsWith(">") || trimmed.startsWith("<") || trimmed.startsWith("+") || trimmed.startsWith("-")) {
                diff.append(trimmed).append("\n");
                count++;
            }
        }

        if (diff.isEmpty()) {
            return truncate(evidence, maxContentLength);
        }

        return diff.toString();
    }

    private String extractCriticType(String criticName) {
        if (criticName == null) return "Unknown";
        if (criticName.contains("TestOutput")) return "TestOutput";
        if (criticName.contains("Pattern")) return "Pattern";
        if (criticName.contains("Diff")) return "Diff";
        if (criticName.contains("Retry")) return "Retry";
        return "Other";
    }

    private String formatFailLegacy(Evaluation.Fail fail) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("### %s\n\n", fail.criticName()));
        sb.append(String.format("**Problema:** %s\n\n", fail.reason()));

        fail.evidence().ifPresent(evidence -> {
            sb.append("**Detalle:**\n");
            sb.append(truncate(evidence, maxContentLength));
            sb.append("\n\n");
        });

        fail.suggestedFix().ifPresent(fix -> {
            sb.append("**Sugerencia:**\n");
            sb.append(truncate(fix, maxContentLength));
            sb.append("\n");
        });

        return sb.toString();
    }

    private String formatWarning(Evaluation.Warning warning) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("### %s\n\n", warning.criticName()));

        if (!warning.issues().isEmpty()) {
            sb.append("**Issues:**\n");
            for (String issue : warning.issues()) {
                sb.append(String.format("- %s\n", truncate(issue, 500)));
            }
            sb.append("\n");
        }

        if (!warning.suggestions().isEmpty()) {
            sb.append("**Sugerencias:**\n");
            for (String suggestion : warning.suggestions()) {
                sb.append(String.format("- %s\n", truncate(suggestion, 500)));
            }
        }

        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "... (truncated)";
    }

    public FeedbackFormatter withIncludePass(boolean include) {
        return new FeedbackFormatter(include, this.includeSkip, this.maxContentLength);
    }

    public FeedbackFormatter withIncludeSkip(boolean include) {
        return new FeedbackFormatter(this.includePass, include, this.maxContentLength);
    }

    public FeedbackFormatter withMaxContentLength(int max) {
        return new FeedbackFormatter(this.includePass, this.includeSkip, max);
    }
}
