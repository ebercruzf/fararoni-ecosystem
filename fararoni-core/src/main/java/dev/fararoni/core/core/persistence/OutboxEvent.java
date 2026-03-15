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

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.CRC32;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record OutboxEvent(
    String id,
    String traceId,
    String correlationId,
    String topic,
    String senderRole,
    String replyTo,
    String payloadJson,
    String payloadType,
    OutboxStatus status,
    int retryCount,
    int maxRetries,
    long ttlMs,
    Instant createdAt,
    Instant dispatchedAt,
    Instant expiresAt,
    String lastError,
    String checksum
) {
    public enum OutboxStatus {
        PENDING,
        DISPATCHED,
        FAILED,
        EXPIRED
    }

    public static OutboxEvent fromEnvelope(String topic, SovereignEnvelope<?> envelope, String payloadJson) {
        Instant now = Instant.now();
        return new OutboxEvent(
            envelope.id(),
            envelope.traceId(),
            envelope.correlationId(),
            topic,
            envelope.senderRole(),
            envelope.replyTo(),
            payloadJson,
            envelope.payload() != null ? envelope.payload().getClass().getName() : "null",
            OutboxStatus.PENDING,
            envelope.retryCount(),
            SovereignEnvelope.MAX_RETRY_COUNT,
            envelope.ttlMs(),
            now,
            null,
            now.plusMillis(envelope.ttlMs()),
            null,
            calculateChecksum(payloadJson)
        );
    }

    public boolean canRetry() {
        return retryCount < maxRetries && !isExpired();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    private static String calculateChecksum(String input) {
        if (input == null) return "0";
        CRC32 crc = new CRC32();
        crc.update(input.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(crc.getValue());
    }
}
