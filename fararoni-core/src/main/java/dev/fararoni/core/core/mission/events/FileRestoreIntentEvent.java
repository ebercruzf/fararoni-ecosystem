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
package dev.fararoni.core.core.mission.events;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record FileRestoreIntentEvent(
    String eventId,

    String agentId,

    String missionId,

    String targetPath,

    String reason,

    Instant timestamp
) {
    public static final String TOPIC = "safety.file.restore.intent";

    public static FileRestoreIntentEvent create(
            String agentId,
            String missionId,
            String targetPath,
            String reason) {
        return new FileRestoreIntentEvent(
            UUID.randomUUID().toString(),
            agentId,
            missionId,
            targetPath,
            reason,
            Instant.now()
        );
    }

    public static FileRestoreIntentEvent create(
            String agentId,
            String missionId,
            String targetPath) {
        return create(agentId, missionId, targetPath, "SAGA Rollback - Misión fallida");
    }

    public String toLogString() {
        return String.format("[RESTORE] agent=%s path=%s reason='%s'",
            agentId, targetPath, reason);
    }

    public boolean isEmergency() {
        if (reason == null) return false;
        String lower = reason.toLowerCase();
        return lower.contains("panic") ||
               lower.contains("emergency") ||
               lower.contains("critical") ||
               lower.contains("corruption");
    }
}
