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
package dev.fararoni.bus.gateway.rest.dto;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public record ChannelEndpoint(
    String channelId,
    boolean enabled,
    TrustLevel trustLevel,
    String egressUrl,
    List<String> capabilities,
    int timeoutMs,
    int retryCount,
    String authToken
) {

    /**
     * Trust level for channel security classification.
     */
    public enum TrustLevel {
        /**
         * Untrusted external channel (public internet).
         * Messages may be logged by third parties.
         * Example: WhatsApp, Telegram public bots
         */
        UNTRUSTED_EXTERNAL,

        /**
         * Secure encrypted channel.
         * End-to-end encryption, minimal metadata exposure.
         * Example: Matrix, Signal
         */
        SECURE_ENCRYPTED,

        /**
         * Secure internal network.
         * Trusted local network, no public exposure.
         * Example: IoT devices, Home Assistant
         */
        SECURE_INTERNAL
    }

    /**
     * Default timeout for HTTP requests (5 seconds).
     */
    public static final int DEFAULT_TIMEOUT_MS = 5000;

    /**
     * Default retry count on failure.
     */
    public static final int DEFAULT_RETRY_COUNT = 3;

    /**
     * Creates an endpoint with default values.
     *
     * @param channelId  channel identifier
     * @param egressUrl  URL to POST responses to
     * @param trustLevel security trust level
     * @return new ChannelEndpoint with defaults
     */
    public static ChannelEndpoint create(String channelId, String egressUrl, TrustLevel trustLevel) {
        return new ChannelEndpoint(
            channelId,
            true,
            trustLevel,
            egressUrl,
            List.of("text"),
            DEFAULT_TIMEOUT_MS,
            DEFAULT_RETRY_COUNT,
            null
        );
    }

    /**
     * Checks if this channel supports a specific capability.
     *
     * @param capability capability to check (text, audio, image, file)
     * @return true if supported
     */
    public boolean supports(String capability) {
        return capabilities != null && capabilities.contains(capability.toLowerCase());
    }

    /**
     * Checks if this channel supports audio messages.
     *
     * @return true if audio is in capabilities
     */
    public boolean supportsAudio() {
        return supports("audio");
    }

    /**
     * Checks if this channel supports image messages.
     *
     * @return true if image is in capabilities
     */
    public boolean supportsImage() {
        return supports("image");
    }

    /**
     * Returns a disabled copy of this endpoint.
     *
     * @return new ChannelEndpoint with enabled=false
     */
    public ChannelEndpoint disable() {
        return new ChannelEndpoint(
            channelId, false, trustLevel, egressUrl,
            capabilities, timeoutMs, retryCount, authToken
        );
    }
}
