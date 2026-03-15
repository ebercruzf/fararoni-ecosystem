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
package dev.fararoni.core.core.persistence;

import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.time.Instant;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record JournalEntry(
    long sequenceId,
    Instant timestamp,
    SwarmMessage message,
    long checksum
) {
    public boolean isReplay() {
        return message != null &&
               Boolean.TRUE.equals(message.getMetadata("IS_REPLAY"));
    }

    public JournalEntry asReplay() {
        if (message != null) {
            message.metadata().put("IS_REPLAY", true);
        }
        return this;
    }
}
