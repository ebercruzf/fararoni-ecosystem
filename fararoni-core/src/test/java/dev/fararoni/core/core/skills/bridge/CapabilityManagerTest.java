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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CapabilityManager Tests")
class CapabilityManagerTest {
    private InMemorySovereignBus bus;
    private CapabilityManager manager;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
        manager = new CapabilityManager(bus);
    }

    @Nested
    @DisplayName("Handshake Processing")
    class HandshakeTests {
        @Test
        @DisplayName("handleHandshake() registra sidecar correctamente")
        void handleHandshakeShouldRegisterSidecar() {
            String handshakeJson = """
                {
                    "sidecar_id": "test-sidecar-01",
                    "type": "INFRA_EXTENDER",
                    "priority": 100,
                    "capabilities": [
                        {"name": "sql_query", "description": "Acceso a Postgres"}
                    ],
                    "version": "1.0.0"
                }
                """;

            manager.handleHandshake(handshakeJson, "corr-123");

            CapabilityManager.SidecarInfo info = manager.getSidecarInfo("test-sidecar-01");
            assertNotNull(info);
            assertEquals("test-sidecar-01", info.sidecarId());
            assertEquals("INFRA_EXTENDER", info.type());
            assertEquals(100, info.priority());
            assertEquals(1, info.capabilities().size());
        }

        @Test
        @DisplayName("handleHandshake() registra herramientas en toolToSidecar")
        void handleHandshakeShouldRegisterTools() {
            String handshakeJson = """
                {
                    "sidecar_id": "mcp-bridge",
                    "type": "INTEGRATION",
                    "priority": 100,
                    "capabilities": [
                        {"name": "sql_query", "description": "SQL"},
                        {"name": "slack_notify", "description": "Slack"}
                    ],
                    "version": "1.0.0"
                }
                """;

            manager.handleHandshake(handshakeJson, "corr-456");

            assertEquals("mcp-bridge", manager.getSidecarForTool("sql_query"));
            assertEquals("mcp-bridge", manager.getSidecarForTool("slack_notify"));
            assertTrue(manager.hasTool("sql_query"));
            assertTrue(manager.hasTool("slack_notify"));
        }

        @Test
        @DisplayName("handleHandshake() rechaza version incompatible")
        void handleHandshakeShouldRejectIncompatibleVersion() {
            manager.setMinRequiredVersion("2.0.0");

            String handshakeJson = """
                {
                    "sidecar_id": "old-sidecar",
                    "type": "LEGACY",
                    "priority": 50,
                    "capabilities": [],
                    "version": "1.0.0"
                }
                """;

            manager.handleHandshake(handshakeJson, "corr-789");

            assertNull(manager.getSidecarInfo("old-sidecar"));
        }
    }

    @Nested
    @DisplayName("Sidecar Management")
    class SidecarManagementTests {
        @Test
        @DisplayName("removeSidecar() elimina sidecar y sus herramientas")
        void removeSidecarShouldRemoveToolsAlso() {
            String handshakeJson = """
                {
                    "sidecar_id": "temp-sidecar",
                    "type": "TEMP",
                    "priority": 50,
                    "capabilities": [
                        {"name": "temp_tool", "description": "Temporal"}
                    ],
                    "version": "1.0.0"
                }
                """;
            manager.handleHandshake(handshakeJson, "corr-temp");

            assertTrue(manager.hasTool("temp_tool"));

            manager.removeSidecar("temp-sidecar");

            assertNull(manager.getSidecarInfo("temp-sidecar"));
            assertFalse(manager.hasTool("temp_tool"));
        }

        @Test
        @DisplayName("getAllSidecars() retorna mapa inmutable")
        void getAllSidecarsShouldReturnImmutableMap() {
            Map<String, CapabilityManager.SidecarInfo> sidecars = manager.getAllSidecars();

            assertThrows(UnsupportedOperationException.class, () -> {
                sidecars.put("hacker", null);
            });
        }
    }

    @Nested
    @DisplayName("Queries")
    class QueryTests {
        @Test
        @DisplayName("getAvailableTools() retorna lista de herramientas")
        void getAvailableToolsShouldReturnToolList() {
            String handshakeJson = """
                {
                    "sidecar_id": "tool-provider",
                    "type": "PROVIDER",
                    "priority": 100,
                    "capabilities": [
                        {"name": "tool_a", "description": "A"},
                        {"name": "tool_b", "description": "B"}
                    ],
                    "version": "1.0.0"
                }
                """;
            manager.handleHandshake(handshakeJson, "corr-tools");

            List<String> tools = manager.getAvailableTools();

            assertEquals(2, tools.size());
            assertTrue(tools.contains("tool_a"));
            assertTrue(tools.contains("tool_b"));
        }

        @Test
        @DisplayName("getSidecarForTool() retorna null para herramienta inexistente")
        void getSidecarForToolShouldReturnNullForUnknown() {
            assertNull(manager.getSidecarForTool("nonexistent_tool"));
        }
    }

    @Nested
    @DisplayName("Listeners")
    class ListenerTests {
        @Test
        @DisplayName("addListener() notifica en conexion de sidecar")
        void listenerShouldBeNotifiedOnConnect() {
            AtomicReference<CapabilityManager.CapabilityEvent> receivedEvent = new AtomicReference<>();

            manager.addListener(event -> receivedEvent.set(event));

            String handshakeJson = """
                {
                    "sidecar_id": "listener-test",
                    "type": "TEST",
                    "priority": 50,
                    "capabilities": [],
                    "version": "1.0.0"
                }
                """;
            manager.handleHandshake(handshakeJson, "corr-listener");

            assertNotNull(receivedEvent.get());
            assertEquals(CapabilityManager.CapabilityEventType.SIDECAR_CONNECTED,
                receivedEvent.get().type());
            assertEquals("listener-test", receivedEvent.get().sidecarId());
        }

        @Test
        @DisplayName("listener notifica en desconexion de sidecar")
        void listenerShouldBeNotifiedOnDisconnect() {
            AtomicReference<CapabilityManager.CapabilityEvent> lastEvent = new AtomicReference<>();

            String handshakeJson = """
                {
                    "sidecar_id": "disconnect-test",
                    "type": "TEST",
                    "priority": 50,
                    "capabilities": [],
                    "version": "1.0.0"
                }
                """;
            manager.handleHandshake(handshakeJson, "corr-disc");

            manager.addListener(event -> lastEvent.set(event));

            manager.removeSidecar("disconnect-test");

            assertNotNull(lastEvent.get());
            assertEquals(CapabilityManager.CapabilityEventType.SIDECAR_DISCONNECTED,
                lastEvent.get().type());
        }
    }

    @Nested
    @DisplayName("Summary Generation")
    class SummaryTests {
        @Test
        @DisplayName("generateSummary() incluye sidecars conectados")
        void generateSummaryShouldIncludeConnectedSidecars() {
            String handshakeJson = """
                {
                    "sidecar_id": "summary-sidecar",
                    "type": "SUMMARY_TEST",
                    "priority": 75,
                    "capabilities": [
                        {"name": "test_tool", "description": "Test"}
                    ],
                    "version": "1.2.3"
                }
                """;
            manager.handleHandshake(handshakeJson, "corr-summary");

            String summary = manager.generateSummary();

            assertTrue(summary.contains("summary-sidecar"));
            assertTrue(summary.contains("ONLINE"));
            assertTrue(summary.contains("1 detectadas"));
        }

        @Test
        @DisplayName("generateSummary() muestra mensaje cuando no hay sidecars")
        void generateSummaryShouldShowEmptyMessage() {
            String summary = manager.generateSummary();

            assertTrue(summary.contains("Sin sidecars conectados"));
        }
    }
}
