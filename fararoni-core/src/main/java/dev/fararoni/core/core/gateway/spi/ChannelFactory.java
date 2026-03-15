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
package dev.fararoni.core.core.gateway.spi;

import com.fasterxml.jackson.databind.JsonNode;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.gateway.security.SecureChannelAdapter;
import dev.fararoni.core.core.security.ChannelAccessGuard;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface ChannelFactory {
    String getSupportedType();

    SecureChannelAdapter create(
        String channelId,
        JsonNode config,
        SovereignEventBus bus,
        ChannelAccessGuard guard
    ) throws ChannelCreationException;

    default boolean validateConfig(JsonNode config) {
        return config != null;
    }

    default String[] getRequiredFields() {
        return new String[0];
    }

    class ChannelCreationException extends Exception {
        public ChannelCreationException(String message) {
            super(message);
        }

        public ChannelCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
