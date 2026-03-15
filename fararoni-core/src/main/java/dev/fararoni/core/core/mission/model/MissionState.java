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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record MissionState(
    String executionId,
    String originalCorrelationId,
    String missionId,
    String currentStepId,
    int iterations,
    ExecutionStatus status,
    String payloadJson,
    Instant createdAt,
    Instant updatedAt,
    List<ExecutedStep> executedSteps,
    Map<String, Integer> retryCounts
) {
    public MissionState {
        Objects.requireNonNull(executionId, "executionId cannot be null");
        Objects.requireNonNull(missionId, "missionId cannot be null");
        Objects.requireNonNull(currentStepId, "currentStepId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

        executedSteps = executedSteps != null
            ? List.copyOf(executedSteps)
            : List.of();

        retryCounts = retryCounts != null
            ? Map.copyOf(retryCounts)
            : Map.of();
    }

    public static MissionState start(
            String executionId,
            String originalCorrelationId,
            String missionId,
            String initialStepId,
            String payloadJson) {
        Instant now = Instant.now();
        return new MissionState(
            executionId,
            originalCorrelationId,
            missionId,
            initialStepId,
            0,
            ExecutionStatus.RUNNING,
            payloadJson,
            now,
            now,
            List.of(),
            Map.of()
        );
    }

    public MissionState recordStepAndAdvance(
            String completedStepId,
            String capability,
            String resultPayload,
            String nextStepId) {
        return recordStepAndAdvance(completedStepId, capability, resultPayload, nextStepId, false);
    }

    public MissionState recordStepAndAdvance(
            String completedStepId,
            String capability,
            String resultPayload,
            String nextStepId,
            boolean incrementRetry) {
        ExecutedStep executed = new ExecutedStep(
            completedStepId,
            capability,
            resultPayload,
            Instant.now()
        );

        List<ExecutedStep> newHistory = new ArrayList<>(this.executedSteps);
        newHistory.add(executed);

        Map<String, Integer> newRetryCounts = new HashMap<>(this.retryCounts);
        if (incrementRetry) {
            newRetryCounts.merge(completedStepId, 1, Integer::sum);
        }

        return new MissionState(
            executionId,
            originalCorrelationId,
            missionId,
            nextStepId,
            iterations + 1,
            status,
            payloadJson,
            createdAt,
            Instant.now(),
            newHistory,
            Collections.unmodifiableMap(newRetryCounts)
        );
    }

    public int getRetriesFor(String stepId) {
        return retryCounts.getOrDefault(stepId, 0);
    }

    public MissionState terminate(ExecutionStatus finalStatus) {
        if (!finalStatus.isTerminal()) {
            throw new IllegalArgumentException(
                "terminate() requires terminal status, got: " + finalStatus
            );
        }
        return new MissionState(
            executionId,
            originalCorrelationId,
            missionId,
            currentStepId,
            iterations,
            finalStatus,
            payloadJson,
            createdAt,
            Instant.now(),
            executedSteps,
            retryCounts
        );
    }

    public MissionState pause() {
        return new MissionState(
            executionId,
            originalCorrelationId,
            missionId,
            currentStepId,
            iterations,
            ExecutionStatus.PAUSED,
            payloadJson,
            createdAt,
            Instant.now(),
            executedSteps,
            retryCounts
        );
    }

    public MissionState resume() {
        if (status != ExecutionStatus.PAUSED) {
            throw new IllegalStateException(
                "Cannot resume mission not in PAUSED state, current: " + status
            );
        }
        return new MissionState(
            executionId,
            originalCorrelationId,
            missionId,
            currentStepId,
            iterations,
            ExecutionStatus.RUNNING,
            payloadJson,
            createdAt,
            Instant.now(),
            executedSteps,
            retryCounts
        );
    }

    public List<ExecutedStep> getStepsToCompensate() {
        List<ExecutedStep> toCompensate = new ArrayList<>(executedSteps);
        Collections.reverse(toCompensate);
        return Collections.unmodifiableList(toCompensate);
    }

    public boolean hasStepsToCompensate() {
        return !executedSteps.isEmpty();
    }

    public boolean isTerminated() {
        return status.isTerminal();
    }

    public boolean isRunning() {
        return status == ExecutionStatus.RUNNING;
    }

    public boolean hasExceededMaxIterations(int maxIterations) {
        return iterations >= maxIterations;
    }

    public record ExecutedStep(
        String stepId,
        String capability,
        String resultPayload,
        Instant executedAt
    ) {
        public ExecutedStep {
            Objects.requireNonNull(stepId, "stepId cannot be null");
            Objects.requireNonNull(capability, "capability cannot be null");
            Objects.requireNonNull(executedAt, "executedAt cannot be null");
        }
    }

    public enum ExecutionStatus {
        RUNNING(false),

        PAUSED(false),

        COMPLETED(true),

        FAILED(true),

        ROLLED_BACK(true),

        CRASHED(true);

        private final boolean terminal;

        ExecutionStatus(boolean terminal) {
            this.terminal = terminal;
        }

        public boolean isTerminal() {
            return terminal;
        }
    }
}
