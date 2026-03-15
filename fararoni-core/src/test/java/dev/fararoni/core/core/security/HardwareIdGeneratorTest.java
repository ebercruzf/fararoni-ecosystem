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
package dev.fararoni.core.core.security;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("HardwareIdGenerator Tests")
class HardwareIdGeneratorTest {
    @BeforeEach
    void setUp() {
        HardwareIdGenerator.clearCache();
    }

    @AfterEach
    void tearDown() {
        HardwareIdGenerator.clearCache();
    }

    @Nested
    @DisplayName("ID Generation")
    class GenerationTests {
        @Test
        @DisplayName("generateHardwareId debe retornar ID no nulo")
        void generateHardwareId_ShouldReturnNonNull() {
            String hwid = HardwareIdGenerator.generateHardwareId();

            assertNotNull(hwid);
            assertFalse(hwid.isEmpty());
        }

        @Test
        @DisplayName("generateHardwareId debe retornar 64 caracteres hex (SHA-256)")
        void generateHardwareId_ShouldReturn64HexChars() {
            String hwid = HardwareIdGenerator.generateHardwareId();

            assertEquals(64, hwid.length(),
                    "SHA-256 debe producir 64 caracteres hexadecimales");
            assertTrue(hwid.matches("[0-9a-f]+"),
                    "Debe contener solo caracteres hexadecimales");
        }

        @Test
        @DisplayName("generateHardwareId debe ser consistente (mismo valor)")
        void generateHardwareId_ShouldBeConsistent() {
            String hwid1 = HardwareIdGenerator.generateHardwareId();
            String hwid2 = HardwareIdGenerator.generateHardwareId();

            assertEquals(hwid1, hwid2,
                    "Múltiples llamadas deben retornar el mismo ID");
        }

        @Test
        @DisplayName("generateHardwareId debe usar cache")
        void generateHardwareId_ShouldUseCache() {
            long start1 = System.nanoTime();
            HardwareIdGenerator.generateHardwareId();
            long elapsed1 = System.nanoTime() - start1;

            long start2 = System.nanoTime();
            HardwareIdGenerator.generateHardwareId();
            long elapsed2 = System.nanoTime() - start2;

            assertTrue(elapsed2 < elapsed1 / 2,
                    "Llamada cacheada debe ser mucho más rápida");
        }

        @Test
        @DisplayName("clearCache debe invalidar el cache")
        void clearCache_ShouldInvalidateCache() {
            String hwid1 = HardwareIdGenerator.generateHardwareId();
            HardwareIdGenerator.clearCache();
            String hwid2 = HardwareIdGenerator.generateHardwareId();

            assertEquals(hwid1, hwid2,
                    "Después de clear, debe regenerar el mismo ID");
        }
    }

    @Nested
    @DisplayName("Summary Generation")
    class SummaryTests {
        @Test
        @DisplayName("getHardwareIdSummary debe retornar resumen corto")
        void getHardwareIdSummary_ShouldReturnShortSummary() {
            String summary = HardwareIdGenerator.getHardwareIdSummary();

            assertNotNull(summary);
            assertTrue(summary.endsWith("..."),
                    "Resumen debe terminar con ...");
            assertTrue(summary.length() < 25,
                    "Resumen debe ser corto para logging");
        }

        @Test
        @DisplayName("getHardwareIdSummary debe contener inicio del ID")
        void getHardwareIdSummary_ShouldContainIdPrefix() {
            String hwid = HardwareIdGenerator.generateHardwareId();
            String summary = HardwareIdGenerator.getHardwareIdSummary();

            String prefix = hwid.substring(0, 16);
            assertTrue(summary.startsWith(prefix),
                    "Resumen debe comenzar con los primeros 16 caracteres del ID");
        }
    }

    @Nested
    @DisplayName("Verification")
    class VerificationTests {
        @Test
        @DisplayName("verifyHardwareId con ID correcto debe retornar true")
        void verifyHardwareId_WithCorrectId_ShouldReturnTrue() {
            String hwid = HardwareIdGenerator.generateHardwareId();

            assertTrue(HardwareIdGenerator.verifyHardwareId(hwid));
        }

        @Test
        @DisplayName("verifyHardwareId con ID incorrecto debe retornar false")
        void verifyHardwareId_WithWrongId_ShouldReturnFalse() {
            assertFalse(HardwareIdGenerator.verifyHardwareId("wrong-id"));
            assertFalse(HardwareIdGenerator.verifyHardwareId("0".repeat(64)));
        }

        @Test
        @DisplayName("verifyHardwareId con null debe retornar false")
        void verifyHardwareId_WithNull_ShouldReturnFalse() {
            assertFalse(HardwareIdGenerator.verifyHardwareId(null));
        }

        @Test
        @DisplayName("verifyHardwareId con vacío debe retornar false")
        void verifyHardwareId_WithEmpty_ShouldReturnFalse() {
            assertFalse(HardwareIdGenerator.verifyHardwareId(""));
        }
    }

    @Nested
    @DisplayName("Determinism")
    class DeterminismTests {
        @Test
        @DisplayName("ID debe ser determinista basado en hardware")
        void hardwareId_ShouldBeDeterministic() {
            String hwid1 = HardwareIdGenerator.generateHardwareId();
            HardwareIdGenerator.clearCache();

            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            String hwid2 = HardwareIdGenerator.generateHardwareId();

            assertEquals(hwid1, hwid2,
                    "ID debe ser determinista en el mismo equipo");
        }
    }

    @Nested
    @DisplayName("Security Properties")
    class SecurityTests {
        @Test
        @DisplayName("ID no debe contener información legible")
        void hardwareId_ShouldNotContainReadableInfo() {
            String hwid = HardwareIdGenerator.generateHardwareId();
            String username = System.getProperty("user.name", "");

            assertFalse(hwid.toLowerCase().contains(username.toLowerCase()),
                    "ID no debe contener username legible");
        }

        @Test
        @DisplayName("ID debe parecer aleatorio")
        void hardwareId_ShouldLookRandom() {
            String hwid = HardwareIdGenerator.generateHardwareId();

            long uniqueChars = hwid.chars().distinct().count();

            assertTrue(uniqueChars > 8,
                    "ID debe tener buena distribución de caracteres");
        }
    }
}
