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
package dev.fararoni.core.core.resilience;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record PoisonPill(
    String originalTopic,
    String failureReason,
    String stackTrace,
    String failingComponent,
    Instant timeOfDeath,
    SovereignEnvelope<?> originalEnvelope
) {

    public static PoisonPill create(
            String topic,
            Throwable error,
            Object consumer,
            SovereignEnvelope<?> envelope) {

        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));

        return new PoisonPill(
            topic,
            error.getMessage() != null ? error.getMessage() : error.getClass().getName(),
            sw.toString(),
            consumer != null ? consumer.getClass().getSimpleName() : "Unknown",
            Instant.now(),
            envelope
        );
    }

    public boolean isTransientError() {
        if (failureReason == null) {
            return false;
        }
        String lower = failureReason.toLowerCase();
        return lower.contains("timeout")
            || lower.contains("connection")
            || lower.contains("network")
            || lower.contains("unavailable")
            || lower.contains("retry");
    }

    public String getOriginalMessageId() {
        return originalEnvelope != null ? originalEnvelope.id() : null;
    }

    public String getTraceId() {
        return originalEnvelope != null ? originalEnvelope.traceId() : null;
    }

    public long ageMs() {
        return Instant.now().toEpochMilli() - timeOfDeath.toEpochMilli();
    }

    public String toShortString() {
        return String.format(
            "PoisonPill[topic=%s, reason=%s, component=%s, msgId=%s]",
            originalTopic,
            failureReason != null && failureReason.length() > 50
                ? failureReason.substring(0, 50) + "..."
                : failureReason,
            failingComponent,
            getOriginalMessageId()
        );
    }
}
