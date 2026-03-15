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
import java.util.Objects;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record FileWriteIntentEvent(
    String eventId,
    String agentId,
    String missionId,
    String targetPath,
    String content,
    String forceReason,
    Instant timestamp
) {
    public static final String TOPIC = "safety.file.intent";

    public FileWriteIntentEvent {
        Objects.requireNonNull(eventId, "eventId no puede ser null");
        Objects.requireNonNull(agentId, "agentId no puede ser null");
        Objects.requireNonNull(targetPath, "targetPath no puede ser null");
        Objects.requireNonNull(content, "content no puede ser null");
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
    }

    public static FileWriteIntentEvent create(
            String agentId,
            String missionId,
            String targetPath,
            String content) {
        return new FileWriteIntentEvent(
            UUID.randomUUID().toString(),
            agentId,
            missionId,
            targetPath,
            content,
            null,
            Instant.now()
        );
    }

    public static FileWriteIntentEvent createForced(
            String agentId,
            String missionId,
            String targetPath,
            String content,
            String forceReason) {
        return new FileWriteIntentEvent(
            UUID.randomUUID().toString(),
            agentId,
            missionId,
            targetPath,
            content,
            forceReason,
            Instant.now()
        );
    }

    public boolean isForced() {
        return forceReason != null && !forceReason.isBlank();
    }
}
