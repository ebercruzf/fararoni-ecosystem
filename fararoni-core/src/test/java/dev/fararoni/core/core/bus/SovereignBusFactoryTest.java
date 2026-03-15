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
package dev.fararoni.core.core.bus;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SovereignBusFactory Tests")
class SovereignBusFactoryTest {
    @BeforeEach
    void setUp() {
        SovereignBusFactory.reset();
    }

    @AfterEach
    void tearDown() {
        SovereignBusFactory.reset();
    }

    @Nested
    @DisplayName("Inicializacion")
    class InitTests {
        @Test
        @DisplayName("init() debe cargar buses via SPI")
        void initShouldLoadBusesViaSpi() {
            SovereignBusFactory.init();

            List<SovereignEventBus> buses = SovereignBusFactory.getAllDetectedBuses();

            assertNotNull(buses);
            assertFalse(buses.isEmpty(), "Debe haber al menos un bus detectado");
        }

        @Test
        @DisplayName("init() debe incluir InMemory como fallback")
        void initShouldIncludeInMemoryFallback() {
            SovereignBusFactory.init();

            List<SovereignEventBus> buses = SovereignBusFactory.getAllDetectedBuses();

            boolean hasInMemory = buses.stream()
                .anyMatch(b -> b instanceof InMemorySovereignBus);

            assertTrue(hasInMemory, "Debe tener InMemorySovereignBus como fallback");
        }

        @Test
        @DisplayName("init() es idempotente")
        void initShouldBeIdempotent() {
            SovereignBusFactory.init();
            int firstCount = SovereignBusFactory.getAllDetectedBuses().size();

            SovereignBusFactory.init();
            int secondCount = SovereignBusFactory.getAllDetectedBuses().size();

            assertEquals(firstCount, secondCount, "Multiples init() no deben duplicar buses");
        }
    }

    @Nested
    @DisplayName("Seleccion de Bus")
    class BusSelectionTests {
        @Test
        @DisplayName("resolveBestBus() retorna bus no nulo")
        void resolveBestBusShouldReturnNonNull() {
            SovereignEventBus bus = SovereignBusFactory.resolveBestBus();

            assertNotNull(bus, "Bus seleccionado no debe ser nulo");
        }

        @Test
        @DisplayName("getActiveBus() retorna el bus dominante")
        void getActiveBusShouldReturnDominantBus() {
            SovereignBusFactory.init();

            SovereignEventBus active = SovereignBusFactory.getActiveBus();

            assertNotNull(active, "Bus activo no debe ser nulo");
        }

        @Test
        @DisplayName("refreshActiveBus() actualiza seleccion")
        void refreshActiveBusShouldUpdateSelection() {
            SovereignBusFactory.init();
            SovereignEventBus first = SovereignBusFactory.getActiveBus();

            SovereignBusFactory.refreshActiveBus();
            SovereignEventBus second = SovereignBusFactory.getActiveBus();

            assertNotNull(second);
            assertEquals(first.getClass(), second.getClass());
        }
    }

    @Nested
    @DisplayName("Lista de Buses")
    class BusListTests {
        @Test
        @DisplayName("getAllDetectedBuses() retorna lista inmutable")
        void getAllDetectedBusesShouldReturnImmutableList() {
            SovereignBusFactory.init();

            List<SovereignEventBus> buses = SovereignBusFactory.getAllDetectedBuses();

            assertThrows(UnsupportedOperationException.class, () -> {
                buses.add(new InMemorySovereignBus());
            });
        }

        @Test
        @DisplayName("getAllDetectedBuses() llama init() si no esta inicializado")
        void getAllDetectedBusesShouldAutoInit() {
            List<SovereignEventBus> buses = SovereignBusFactory.getAllDetectedBuses();

            assertNotNull(buses);
            assertFalse(buses.isEmpty());
        }
    }

    @Nested
    @DisplayName("Reset y Ambiente")
    class ResetAndEnvironmentTests {
        @Test
        @DisplayName("reset() limpia todo el estado")
        void resetShouldClearAllState() {
            SovereignBusFactory.init();
            assertFalse(SovereignBusFactory.getAllDetectedBuses().isEmpty());

            SovereignBusFactory.reset();

            assertNotNull(SovereignBusFactory.getActiveBus());
        }

        @Test
        @DisplayName("isDevelopmentMode() detecta ambiente")
        void isDevelopmentModeShouldDetectEnvironment() {
            boolean isDev = SovereignBusFactory.isDevelopmentMode();

            assertTrue(isDev);
        }
    }

    @Nested
    @DisplayName("Guarded Bus")
    class GuardedBusTests {
        @Test
        @DisplayName("resolveGuardedBus() retorna bus funcional")
        void resolveGuardedBusShouldReturnFunctionalBus() {
            SovereignEventBus bus = SovereignBusFactory.resolveGuardedBus();

            assertNotNull(bus, "Guarded bus no debe ser nulo");
        }
    }
}
