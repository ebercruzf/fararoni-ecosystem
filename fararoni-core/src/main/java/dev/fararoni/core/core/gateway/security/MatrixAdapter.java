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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class MatrixAdapter implements SecureChannelAdapter {
    private static final String PROTOCOL = "MATRIX";

    private final String channelId;
    private volatile boolean active = false;

    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private volatile long lastActivityMs = 0;

    public MatrixAdapter(String channelId) {
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
        return ChannelTrustLevel.SECURE_ENCRYPTED;
    }

    @Override
    public void start() throws ChannelConnectionException {
        throw new ChannelConnectionException(
            "MatrixAdapter no implementado. Ver FASE 45.5 en " +
            "docs/arquitectura/manuales/FASE-45-WHATSAPP-MULTICANAL-PLAN-IMPLEMENTACION.md"
        );
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
        throw new UnsupportedOperationException("MatrixAdapter.normalize() no implementado");
    }

    @Override
    public CompletableFuture<Void> sendDirectResponse(String destinationId, String text) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("MatrixAdapter.sendDirectResponse() no implementado")
        );
    }

    @Override
    public CompletableFuture<Void> sendMediaResponse(String destinationId, Path mediaFile, String caption) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("MatrixAdapter.sendMediaResponse() no implementado")
        );
    }

    @Override
    public Map<String, Long> getStats() {
        return Map.of(
            "messages_received", messagesReceived.get(),
            "messages_sent", messagesSent.get(),
            "errors", errors.get(),
            "last_activity_ms", lastActivityMs
        );
    }
}
