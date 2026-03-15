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
package dev.fararoni.core.core.swarm.infra;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class ProtocolTranslator {
    private static final Logger LOG = Logger.getLogger(ProtocolTranslator.class.getName());

    public static final String TOPIC_PREFIX = "agent.swarm.";

    public static final String TOPIC_SUFFIX_INBOX = ".inbox";

    public static final String TOPIC_BROADCAST = "agent.swarm.broadcast";

    public static final String TRACE_PREFIX = "trace-swarm-";

    private ProtocolTranslator() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    public static SovereignEnvelope<SwarmMessage> toSovereign(SwarmMessage legacy) {
        if (legacy == null) {
            throw new NullPointerException("SwarmMessage cannot be null");
        }

        SovereignEnvelope<SwarmMessage> envelope = SovereignEnvelope.create(
            legacy.senderId(),
            legacy.senderId(),
            TRACE_PREFIX + legacy.id(),
            legacy
        );

        if (legacy.correlationId() != null) {
            envelope = envelope.withCorrelation(legacy.correlationId());
        }

        final SovereignEnvelope<SwarmMessage> finalEnvelope = envelope;
        LOG.fine(() -> String.format(
            "[TRANSLATOR] Legacy→Aegis: %s→%s [%s] → envelope %s",
            legacy.senderId(), legacy.receiverId(), legacy.type(), finalEnvelope.id()));

        return finalEnvelope;
    }

    public static SwarmMessage fromSovereign(SovereignEnvelope<SwarmMessage> envelope) {
        if (envelope == null) {
            throw new NullPointerException("SovereignEnvelope cannot be null");
        }

        SwarmMessage payload = envelope.payload();
        if (payload == null) {
            throw new IllegalArgumentException("Envelope payload is null");
        }

        LOG.fine(() -> String.format(
            "[TRANSLATOR] Aegis→Legacy: envelope %s → %s→%s [%s]",
            envelope.id(), payload.senderId(), payload.receiverId(), payload.type()));

        return payload;
    }

    @SuppressWarnings("unchecked")
    public static SwarmMessage tryExtractSwarmMessage(SovereignEnvelope<?> envelope) {
        if (envelope == null || envelope.payload() == null) {
            return null;
        }

        Object payload = envelope.payload();
        if (payload instanceof SwarmMessage swarmMsg) {
            return swarmMsg;
        }

        LOG.fine(() -> "[TRANSLATOR] Payload no es SwarmMessage: " + payload.getClass().getName());
        return null;
    }

    public static String toTopic(String receiverId) {
        if (receiverId == null || receiverId.isBlank()) {
            return TOPIC_BROADCAST;
        }
        return TOPIC_PREFIX + receiverId.toLowerCase() + TOPIC_SUFFIX_INBOX;
    }

    public static String broadcastTopic() {
        return TOPIC_BROADCAST;
    }

    public static String extractAgentId(String topic) {
        if (topic == null || !topic.startsWith(TOPIC_PREFIX)) {
            return null;
        }

        String withoutPrefix = topic.substring(TOPIC_PREFIX.length());
        if (withoutPrefix.endsWith(TOPIC_SUFFIX_INBOX)) {
            return withoutPrefix.substring(0, withoutPrefix.length() - TOPIC_SUFFIX_INBOX.length());
        }

        return withoutPrefix;
    }

    public static boolean isSwarmTopic(String topic) {
        return topic != null && topic.startsWith(TOPIC_PREFIX);
    }

    public static boolean isBroadcastTopic(String topic) {
        return TOPIC_BROADCAST.equals(topic);
    }
}
