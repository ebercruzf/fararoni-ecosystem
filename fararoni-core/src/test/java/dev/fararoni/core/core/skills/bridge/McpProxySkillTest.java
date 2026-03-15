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
package dev.fararoni.core.core.skills.bridge;

import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpProxySkill Tests")
class McpProxySkillTest {
    private InMemorySovereignBus bus;
    private McpProxySkill proxy;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
        proxy = new McpProxySkill(bus);
    }

    @Nested
    @DisplayName("DynamicSkill Interface")
    class DynamicSkillInterfaceTests {
        @Test
        @DisplayName("getSkillName() retorna MCP_EXTENDER")
        void getSkillNameShouldReturnMcpExtender() {
            assertEquals("MCP_EXTENDER", proxy.getSkillName());
        }

        @Test
        @DisplayName("getDescription() retorna descripcion valida")
        void getDescriptionShouldReturnValidDescription() {
            String desc = proxy.getDescription();

            assertNotNull(desc);
            assertTrue(desc.contains("MCP"));
        }

        @Test
        @DisplayName("getSidecarEndpoint() retorna endpoint del bus")
        void getSidecarEndpointShouldReturnBusEndpoint() {
            String endpoint = proxy.getSidecarEndpoint();

            assertNotNull(endpoint);
            assertTrue(endpoint.startsWith("bus://"));
            assertTrue(endpoint.contains("mcp.execute"));
        }
    }

    @Nested
    @DisplayName("Health Check")
    class HealthCheckTests {
        @Test
        @DisplayName("checkHealth() retorna false sin heartbeats")
        void checkHealthShouldReturnFalseWithoutHeartbeats() {
            assertFalse(proxy.checkHealth());
        }

        @Test
        @DisplayName("isAvailable() refleja estado del sidecar")
        void isAvailableShouldReflectSidecarState() {
            assertFalse(proxy.isAvailable());
        }

        @Test
        @DisplayName("getLastCheckTime() retorna EPOCH inicialmente")
        void getLastCheckTimeShouldReturnEpochInitially() {
            assertEquals(Instant.EPOCH, proxy.getLastCheckTime());
        }
    }

    @Nested
    @DisplayName("Priority")
    class PriorityTests {
        @Test
        @DisplayName("getPriority() retorna 0 cuando offline")
        void getPriorityShouldReturnZeroWhenOffline() {
            assertEquals(0, proxy.getPriority());
        }
    }

    @Nested
    @DisplayName("Execution")
    class ExecutionTests {
        @Test
        @DisplayName("execute() retorna error cuando sidecar offline")
        void executeShouldReturnErrorWhenOffline() {
            Map<String, Object> intent = Map.of("method", "tools/list");

            Object result = proxy.execute(intent);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals(false, resultMap.get("success"));
            assertTrue(resultMap.get("error").toString().contains("offline"));
        }
    }

    @Nested
    @DisplayName("Topics")
    class TopicsTests {
        @Test
        @DisplayName("TOPIC_MCP_EXECUTE tiene valor correcto")
        void topicMcpExecuteShouldHaveCorrectValue() {
            assertEquals("mcp.execute", McpProxySkill.TOPIC_MCP_EXECUTE);
        }

        @Test
        @DisplayName("TOPIC_MCP_RESPONSE_PREFIX tiene valor correcto")
        void topicMcpResponsePrefixShouldHaveCorrectValue() {
            assertEquals("mcp.response.", McpProxySkill.TOPIC_MCP_RESPONSE_PREFIX);
        }

        @Test
        @DisplayName("TOPIC_MCP_HEARTBEAT tiene valor correcto")
        void topicMcpHeartbeatShouldHaveCorrectValue() {
            assertEquals("system.skills.online", McpProxySkill.TOPIC_MCP_HEARTBEAT);
        }
    }
}
