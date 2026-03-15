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

import java.util.Set;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public enum ChannelTrustLevel {
    SECURE_ENCRYPTED(Set.of("*"), 100),

    TRUSTED_DEVICE(Set.of(
        "/task", "/ask", "/wizard", "/config", "/status",
        "/security", "/debug", "/logs", "/metrics", "/help",
        "/swarm", "/run", "/reconfig", "/export"
    ), 75),

    UNTRUSTED_EXTERNAL(Set.of(
        "/task", "/ask", "/status", "/help"
    ), 25);

    private final Set<String> allowedCommands;
    private final int trustScore;

    ChannelTrustLevel(Set<String> allowedCommands, int trustScore) {
        this.allowedCommands = allowedCommands;
        this.trustScore = trustScore;
    }

    public boolean canExecuteCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }

        if (allowedCommands.contains("*")) {
            return true;
        }

        String baseCommand = command.trim().split("\\s+")[0].toLowerCase();
        return allowedCommands.contains(baseCommand);
    }

    public boolean isAtLeast(ChannelTrustLevel other) {
        return this.trustScore >= other.trustScore;
    }

    public int getTrustScore() {
        return trustScore;
    }

    public Set<String> getAllowedCommands() {
        return allowedCommands;
    }

    public String getDescription() {
        return switch (this) {
            case SECURE_ENCRYPTED -> "Canal encriptado E2EE (Matrix, Signal)";
            case TRUSTED_DEVICE -> "Dispositivo local de confianza (CLI)";
            case UNTRUSTED_EXTERNAL -> "Canal externo no verificado (WhatsApp, Telegram)";
        };
    }

    public String getBadge() {
        return switch (this) {
            case SECURE_ENCRYPTED -> "[SEC]";
            case TRUSTED_DEVICE -> "[TRU]";
            case UNTRUSTED_EXTERNAL -> "[!]";
        };
    }

    @Override
    public String toString() {
        return String.format("%s %s (trust=%d)", getBadge(), name(), trustScore);
    }
}
