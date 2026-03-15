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

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ReActStep(
    int stepNumber,
    StepType type,
    String thought,
    ToolRequest action,
    ToolResponse observation,
    String rawLlmOutput,
    Instant timestamp,
    Duration duration
) {
    public enum StepType {
        THINKING,

        TOOL_CALL,

        FINAL_ANSWER,

        ERROR
    }

    public ReActStep {
        if (stepNumber < 1) {
            throw new IllegalArgumentException("stepNumber must be >= 1");
        }
        Objects.requireNonNull(type, "type must not be null");
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (duration == null) {
            duration = Duration.ZERO;
        }
    }

    public static ReActStep thinking(int stepNumber, String thought, String rawOutput) {
        return new ReActStep(
            stepNumber,
            StepType.THINKING,
            thought,
            null,
            null,
            rawOutput,
            Instant.now(),
            Duration.ZERO
        );
    }

    public static ReActStep toolCall(int stepNumber, String thought, ToolRequest action,
                                      ToolResponse observation, String rawOutput, Duration duration) {
        return new ReActStep(
            stepNumber,
            StepType.TOOL_CALL,
            thought,
            action,
            observation,
            rawOutput,
            Instant.now(),
            duration
        );
    }

    public static ReActStep finalAnswer(int stepNumber, String answer, String rawOutput) {
        return new ReActStep(
            stepNumber,
            StepType.FINAL_ANSWER,
            null,
            null,
            ToolResponse.success(answer),
            rawOutput,
            Instant.now(),
            Duration.ZERO
        );
    }

    public static ReActStep error(int stepNumber, String errorMessage, String rawOutput) {
        return new ReActStep(
            stepNumber,
            StepType.ERROR,
            null,
            null,
            ToolResponse.error(errorMessage),
            rawOutput,
            Instant.now(),
            Duration.ZERO
        );
    }

    public boolean hasAction() {
        return action != null;
    }

    public boolean hasObservation() {
        return observation != null;
    }

    public boolean isObservationSuccessful() {
        return observation != null && observation.success();
    }

    public boolean isFinalAnswer() {
        return type == StepType.FINAL_ANSWER;
    }

    public boolean isError() {
        return type == StepType.ERROR;
    }

    public boolean isTerminal() {
        return type == StepType.FINAL_ANSWER || type == StepType.ERROR;
    }

    public Optional<String> getObservationMessage() {
        return Optional.ofNullable(observation).map(ToolResponse::getMessage);
    }

    public Optional<String> getToolName() {
        return Optional.ofNullable(action).map(ToolRequest::toolName);
    }

    public Optional<String> getActionName() {
        return Optional.ofNullable(action).map(ToolRequest::action);
    }

    public String toSummary() {
        return switch (type) {
            case THINKING -> String.format(
                "Step %d [THINKING]: %s",
                stepNumber,
                truncate(thought, 80)
            );
            case TOOL_CALL -> String.format(
                "Step %d [TOOL_CALL]: %s.%s -> %s (%dms)",
                stepNumber,
                getToolName().orElse("?"),
                getActionName().orElse("?"),
                isObservationSuccessful() ? "OK" : "ERROR",
                duration.toMillis()
            );
            case FINAL_ANSWER -> String.format(
                "Step %d [FINAL_ANSWER]: %s",
                stepNumber,
                truncate(getObservationMessage().orElse(""), 80)
            );
            case ERROR -> String.format(
                "Step %d [ERROR]: %s",
                stepNumber,
                getObservationMessage().orElse("Unknown error")
            );
        };
    }

    public String toPromptFormat() {
        StringBuilder sb = new StringBuilder();

        if (thought != null && !thought.isBlank()) {
            sb.append("Thought: ").append(thought).append("\n");
        }

        if (action != null) {
            sb.append("Action: ").append(action.toolName())
              .append(".").append(action.action())
              .append(" ").append(action.params())
              .append("\n");
        }

        if (observation != null) {
            sb.append("Observation: ").append(observation.getMessage()).append("\n");
        }

        return sb.toString();
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
}
