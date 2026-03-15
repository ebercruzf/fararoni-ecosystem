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
package dev.fararoni.core.core.event.types;

import dev.fararoni.core.core.event.AppEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record LlmTokenEvent(
    String eventId,
    Instant timestamp,
    String token,
    String requestId,
    int tokenIndex,
    boolean isLast
) implements AppEvent {
    public LlmTokenEvent {
        if (eventId == null) eventId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
    }

    public LlmTokenEvent(String token, String requestId) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            token,
            requestId,
            -1,
            false
        );
    }

    public LlmTokenEvent(String token, String requestId, int tokenIndex) {
        this(
            UUID.randomUUID().toString(),
            Instant.now(),
            token,
            requestId,
            tokenIndex,
            false
        );
    }

    public static LlmTokenEvent lastToken(String requestId, int totalTokens) {
        return new LlmTokenEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            "",
            requestId,
            totalTokens,
            true
        );
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getSource() {
        return "LlmClient";
    }
}
