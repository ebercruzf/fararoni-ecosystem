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
package dev.fararoni.core.core.mission.model;

import java.util.Map;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record MissionStep(
    String stepId,
    String requiredCapability,
    String systemPromptOverride,
    Map<String, String> transitions,
    boolean compensationStep,
    int maxRetries
) {
    public static final int DEFAULT_MAX_RETRIES = 5;

    public static final String END_SUCCESS = "end_success";

    public static final String END_FAILURE = "end_failure";

    public static final String END_ROLLBACK = "end_rollback";

    public MissionStep {
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(requiredCapability, "requiredCapability cannot be null");
        Objects.requireNonNull(transitions, "transitions cannot be null");

        if (stepId.isBlank()) {
            throw new IllegalArgumentException("stepId cannot be blank");
        }
        if (requiredCapability.isBlank()) {
            throw new IllegalArgumentException("requiredCapability cannot be blank");
        }
        if (transitions.isEmpty()) {
            throw new IllegalArgumentException("transitions cannot be empty - at least one transition required");
        }
        if (maxRetries < 0) {
            maxRetries = 0;
        }
    }

    public MissionStep(
            String stepId,
            String requiredCapability,
            String systemPromptOverride,
            Map<String, String> transitions,
            boolean compensationStep) {
        this(stepId, requiredCapability, systemPromptOverride, transitions, compensationStep, DEFAULT_MAX_RETRIES);
    }

    public String getNextStep(String result) {
        String next = transitions.get(result);
        if (next != null) {
            return next;
        }
        return transitions.get("default");
    }

    public java.util.Optional<String> getFallbackStep() {
        return java.util.Optional.ofNullable(transitions.get("fallback"));
    }

    public boolean hasRetryLimit() {
        return maxRetries > 0;
    }

    public boolean isTerminal() {
        return transitions.values().stream()
            .allMatch(t -> t == null || t.startsWith("end_"));
    }

    public boolean isTerminalTransition(String result) {
        String next = getNextStep(result);
        return next == null || next.startsWith("end_");
    }

    public boolean requiresCompensation() {
        return !compensationStep;
    }
}
