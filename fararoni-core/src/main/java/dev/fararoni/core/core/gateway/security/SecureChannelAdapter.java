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

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public sealed interface SecureChannelAdapter
    permits MatrixAdapter, TelegramAdapter, CliAdapter,
            GenericWebhookAdapter {
    String getChannelId();

    String getProtocol();

    ChannelTrustLevel getChannelTrustLevel();

    void start() throws ChannelConnectionException;

    void stop();

    boolean isActive();

    SovereignEnvelope<String> normalize(Object rawInput, Map<String, String> metadata);

    CompletableFuture<Void> sendDirectResponse(String destinationId, String text);

    CompletableFuture<Void> sendMediaResponse(String destinationId, Path mediaFile, String caption);

    Map<String, Long> getStats();

    class ChannelConnectionException extends Exception {
        public ChannelConnectionException(String message) {
            super(message);
        }

        public ChannelConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
