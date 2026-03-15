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
public record MissionTemplate(
    String missionId,
    String description,
    int maxIterations,
    String initialStepId,
    Map<String, MissionStep> steps,
    int minBlueprintFiles
) {
    public static final int DEFAULT_MAX_ITERATIONS = 20;

    public static final int DEFAULT_MIN_BLUEPRINT_FILES = 0;

    public MissionTemplate {
        Objects.requireNonNull(missionId, "missionId cannot be null");
        Objects.requireNonNull(initialStepId, "initialStepId cannot be null");
        Objects.requireNonNull(steps, "steps cannot be null");

        if (missionId.isBlank()) {
            throw new IllegalArgumentException("missionId cannot be blank");
        }
        if (maxIterations <= 0) {
            throw new IllegalArgumentException("maxIterations must be > 0, got: " + maxIterations);
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps cannot be empty - at least one step required");
        }
        if (!steps.containsKey(initialStepId)) {
            throw new IllegalArgumentException(
                "initialStepId '" + initialStepId + "' does not exist in steps: " + steps.keySet()
            );
        }

        for (MissionStep step : steps.values()) {
            for (String nextStepId : step.transitions().values()) {
                if (nextStepId != null
                    && !nextStepId.startsWith("end_")
                    && !steps.containsKey(nextStepId)) {
                    throw new IllegalArgumentException(
                        "Step '" + step.stepId() + "' has invalid transition to '" + nextStepId +
                        "' which does not exist in steps: " + steps.keySet()
                    );
                }
            }
        }
    }

    public MissionStep getStep(String stepId) {
        return steps.get(stepId);
    }

    public MissionStep getInitialStep() {
        return steps.get(initialStepId);
    }

    public boolean hasStep(String stepId) {
        return steps.containsKey(stepId);
    }

    public int stepCount() {
        return steps.size();
    }

    public static MissionTemplate create(
            String missionId,
            String description,
            String initialStepId,
            Map<String, MissionStep> steps) {
        return new MissionTemplate(
            missionId,
            description,
            DEFAULT_MAX_ITERATIONS,
            initialStepId,
            steps,
            DEFAULT_MIN_BLUEPRINT_FILES
        );
    }
}
