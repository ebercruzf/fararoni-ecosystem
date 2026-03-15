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

import dev.fararoni.core.core.persona.Persona;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ReActResult(
    List<ReActStep> steps,
    String finalAnswer,
    Persona persona,
    Duration totalDuration,
    boolean completed
) {
    public ReActResult {
        Objects.requireNonNull(steps, "steps must not be null");
        steps = Collections.unmodifiableList(steps);
        if (totalDuration == null) {
            totalDuration = Duration.ZERO;
        }
    }

    public static ReActResult success(List<ReActStep> steps, String finalAnswer, Duration duration) {
        return new ReActResult(steps, finalAnswer, null, duration, true);
    }

    public static ReActResult error(List<ReActStep> steps, String errorMessage, Duration duration) {
        return new ReActResult(steps, null, null, duration, false);
    }

    public static ReActResult timeout(List<ReActStep> steps, Duration duration) {
        return new ReActResult(steps, null, null, duration, false);
    }

    public boolean isSuccess() {
        return completed && finalAnswer != null;
    }

    public boolean hasError() {
        return steps.stream().anyMatch(ReActStep::isError);
    }

    public boolean isTimeout() {
        return !completed && !hasError();
    }

    public int stepCount() {
        return steps.size();
    }

    public int toolCallCount() {
        return (int) steps.stream()
            .filter(s -> s.type() == ReActStep.StepType.TOOL_CALL)
            .count();
    }

    public Optional<ReActStep> getLastStep() {
        return steps.isEmpty() ? Optional.empty() : Optional.of(steps.get(steps.size() - 1));
    }

    public Optional<ReActStep> getFirstError() {
        return steps.stream()
            .filter(ReActStep::isError)
            .findFirst();
    }

    public Optional<String> getErrorMessage() {
        return getFirstError()
            .flatMap(ReActStep::getObservationMessage);
    }

    public List<String> getToolsUsed() {
        return steps.stream()
            .filter(s -> s.type() == ReActStep.StepType.TOOL_CALL)
            .flatMap(s -> s.getToolName().stream())
            .distinct()
            .toList();
    }

    public String getFinalAnswerOrError() {
        if (finalAnswer != null) {
            return finalAnswer;
        }
        return getErrorMessage().orElse("Execution did not complete successfully");
    }

    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("ReActResult {\n");
        sb.append("  status: ").append(isSuccess() ? "SUCCESS" :
            (hasError() ? "ERROR" : "TIMEOUT")).append("\n");
        sb.append("  steps: ").append(stepCount()).append("\n");
        sb.append("  toolCalls: ").append(toolCallCount()).append("\n");
        sb.append("  duration: ").append(totalDuration.toMillis()).append("ms\n");

        if (persona != null) {
            sb.append("  persona: ").append(persona.id()).append("\n");
        }

        if (finalAnswer != null) {
            sb.append("  answer: ").append(truncate(finalAnswer, 100)).append("\n");
        }

        if (hasError()) {
            sb.append("  error: ").append(getErrorMessage().orElse("?")).append("\n");
        }

        sb.append("}");
        return sb.toString();
    }

    public String toDetailedLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ReAct Execution Log ===\n\n");

        for (ReActStep step : steps) {
            sb.append(step.toSummary()).append("\n");

            if (step.thought() != null) {
                sb.append("  Thought: ").append(step.thought()).append("\n");
            }

            if (step.hasAction()) {
                sb.append("  Action: ").append(step.action().toolName())
                  .append(".").append(step.action().action())
                  .append(" ").append(step.action().params()).append("\n");
            }

            if (step.hasObservation()) {
                sb.append("  Observation: ").append(
                    truncate(step.observation().getMessage(), 200)).append("\n");
            }

            sb.append("\n");
        }

        sb.append("=== Summary ===\n");
        sb.append(toSummary());

        return sb.toString();
    }

    public String toPromptFormat() {
        return steps.stream()
            .map(ReActStep::toPromptFormat)
            .collect(Collectors.joining("\n"));
    }

    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() <= len ? s : s.substring(0, len - 3) + "...";
    }
}
