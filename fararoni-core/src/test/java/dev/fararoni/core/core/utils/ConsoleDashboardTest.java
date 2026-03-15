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
package dev.fararoni.core.core.utils;

import dev.fararoni.core.core.bus.SovereignBusFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsoleDashboard Tests")
class ConsoleDashboardTest {
    private ConsoleDashboard dashboard;

    @BeforeEach
    void setUp() {
        SovereignBusFactory.reset();
        dashboard = new ConsoleDashboard();
    }

    @AfterEach
    void tearDown() {
        dashboard.stop();
        SovereignBusFactory.reset();
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {
        @Test
        @DisplayName("start() inicia el dashboard sin errores")
        void startShouldInitializeWithoutErrors() {
            assertDoesNotThrow(() -> dashboard.start());
        }

        @Test
        @DisplayName("stop() detiene el dashboard sin errores")
        void stopShouldTerminateWithoutErrors() {
            dashboard.start();
            assertDoesNotThrow(() -> dashboard.stop());
        }

        @Test
        @DisplayName("start() es idempotente")
        void startShouldBeIdempotent() {
            dashboard.start();
            assertDoesNotThrow(() -> dashboard.start());
        }

        @Test
        @DisplayName("stop() es seguro llamar multiples veces")
        void stopShouldBeSafeToCallMultipleTimes() {
            dashboard.start();
            dashboard.stop();
            assertDoesNotThrow(() -> dashboard.stop());
        }
    }

    @Nested
    @DisplayName("Plain Text Generation")
    class PlainTextTests {
        @Test
        @DisplayName("generatePlainText() retorna contenido valido")
        void generatePlainTextShouldReturnValidContent() {
            SovereignBusFactory.init();

            String text = dashboard.generatePlainText();

            assertNotNull(text);
            assertTrue(text.contains("FARARONI"));
            assertTrue(text.contains("BUS DETECTADO"));
            assertTrue(text.contains("PRIORIDAD"));
            assertTrue(text.contains("ESTADO"));
            assertTrue(text.contains("MODO"));
        }

        @Test
        @DisplayName("generatePlainText() incluye buses detectados")
        void generatePlainTextShouldIncludeDetectedBuses() {
            SovereignBusFactory.init();

            String text = dashboard.generatePlainText();

            assertTrue(text.contains("InMemory") || text.contains("ONLINE"));
        }

        @Test
        @DisplayName("generatePlainText() muestra timestamp")
        void generatePlainTextShouldShowTimestamp() {
            SovereignBusFactory.init();

            String text = dashboard.generatePlainText();

            assertTrue(text.contains("Timestamp"));
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {
        @Test
        @DisplayName("setUseFullClear() se puede configurar")
        void setUseFullClearShouldBeConfigurable() {
            assertDoesNotThrow(() -> dashboard.setUseFullClear(false));
            assertDoesNotThrow(() -> dashboard.setUseFullClear(true));
        }

        @Test
        @DisplayName("setCapabilityManager() acepta null")
        void setCapabilityManagerShouldAcceptNull() {
            assertDoesNotThrow(() -> dashboard.setCapabilityManager(null));
        }
    }

    @Nested
    @DisplayName("Print Once")
    class PrintOnceTests {
        @Test
        @DisplayName("printOnce() ejecuta sin errores")
        void printOnceShouldExecuteWithoutErrors() {
            SovereignBusFactory.init();
            assertDoesNotThrow(() -> dashboard.printOnce());
        }
    }
}
