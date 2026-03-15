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
package dev.fararoni.core.server;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class PluginWebSocketBridgeTest {
    private SovereignEventBus bus;
    private PluginWebSocketBridge bridge;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
        bridge = new PluginWebSocketBridge(bus);
    }

    @Test
    void constructor_withBus_createsSuccessfully() {
        assertNotNull(bridge);
        assertEquals(0, bridge.getConnectionCount());
    }

    @Test
    void constructor_withAuthToken_createsSuccessfully() {
        String authToken = "secret-token-123";

        PluginWebSocketBridge securedBridge = new PluginWebSocketBridge(bus, authToken);

        assertNotNull(securedBridge);
        assertEquals(0, securedBridge.getConnectionCount());
    }

    @Test
    void getConnectedPluginIds_initiallyEmpty() {
        Set<String> pluginIds = bridge.getConnectedPluginIds();

        assertTrue(pluginIds.isEmpty());
    }

    @Test
    void getConnectionCount_initiallyZero() {
        int count = bridge.getConnectionCount();

        assertEquals(0, count);
    }

    @Test
    void close_clearsAllConnections() {
        bridge.close();

        assertEquals(0, bridge.getConnectionCount());
        assertTrue(bridge.getConnectedPluginIds().isEmpty());
    }

    @Test
    void broadcastToAllPlugins_withNoConnections_doesNotThrow() {
        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "TEST", null, "Test payload"
        );

        assertDoesNotThrow(() -> bridge.broadcastToAllPlugins(envelope));
    }

    @Test
    void broadcastToPlugins_withNoSubscribers_doesNotThrow() {
        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "testUser", "TEST", null, "Test payload"
        );

        assertDoesNotThrow(() -> bridge.broadcastToPlugins("test.topic", envelope));
    }
}
