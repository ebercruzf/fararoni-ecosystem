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
package dev.fararoni.core.core.mission.domain;

import java.time.Instant;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record MissionProgressPayload(
    String missionId,
    String agentRole,
    String capability,
    String status,
    String message,
    int retryCount,
    Instant timestamp
) {
    public static final String STATUS_DISPATCHING = "DISPATCHING";
    public static final String STATUS_THINKING = "THINKING";
    public static final String STATUS_EXECUTING = "EXECUTING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REVIEWING = "REVIEWING";
    public static final String STATUS_COMPENSATING = "COMPENSATING";

    public static MissionProgressPayload create(
            String missionId,
            String agentRole,
            String capability,
            String status,
            String message,
            int retryCount) {
        return new MissionProgressPayload(
            missionId,
            agentRole,
            capability,
            status,
            message,
            retryCount,
            Instant.now()
        );
    }
}
