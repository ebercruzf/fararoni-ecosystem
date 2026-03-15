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
package dev.fararoni.core.core.gateway.security;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class CliAdapter implements SecureChannelAdapter {
    private static final String PROTOCOL = "CLI";
    private static final String DEFAULT_CHANNEL_ID = "cli-local";

    private final String channelId;
    private volatile boolean active = false;

    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private volatile long lastActivityMs = 0;

    public CliAdapter() {
        this(DEFAULT_CHANNEL_ID);
    }

    public CliAdapter(String channelId) {
        this.channelId = channelId;
    }

    @Override
    public String getChannelId() {
        return channelId;
    }

    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    @Override
    public ChannelTrustLevel getChannelTrustLevel() {
        return ChannelTrustLevel.TRUSTED_DEVICE;
    }

    @Override
    public void start() {
        active = true;
        lastActivityMs = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        active = false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public SovereignEnvelope<String> normalize(Object rawInput, Map<String, String> metadata) {
        String input = rawInput != null ? rawInput.toString() : "";
        String userId = metadata.getOrDefault("user", System.getProperty("user.name"));

        messagesReceived.incrementAndGet();
        lastActivityMs = System.currentTimeMillis();

        return SovereignEnvelope.create(
            userId,
            "CLI_USER",
            UUID.randomUUID().toString(),
            input
        )
        .withHeader("X-Origin-Protocol", PROTOCOL)
        .withHeader("X-Security-Level", getChannelTrustLevel().name())
        .withHeader("X-Reply-Channel-Id", channelId);
    }

    @Override
    public CompletableFuture<Void> sendDirectResponse(String destinationId, String text) {
        System.out.println(text);
        messagesSent.incrementAndGet();
        lastActivityMs = System.currentTimeMillis();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> sendMediaResponse(String destinationId, Path mediaFile, String caption) {
        String output = caption != null
            ? String.format("[Archivo: %s]\n%s", mediaFile, caption)
            : String.format("[Archivo: %s]", mediaFile);
        System.out.println(output);
        messagesSent.incrementAndGet();
        lastActivityMs = System.currentTimeMillis();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Map<String, Long> getStats() {
        return Map.of(
            "messages_received", messagesReceived.get(),
            "messages_sent", messagesSent.get(),
            "errors", 0L,
            "last_activity_ms", lastActivityMs
        );
    }
}
