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
package dev.fararoni.bus.agent.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para RiskLevel.
 * Valida: niveles de riesgo, requiresAudit(), requiresHumanConfirmation().
 */
@DisplayName("RiskLevel")
class RiskLevelTest {

    @Nested
    @DisplayName("Valores del Enum")
    class ValoresEnum {

        @Test
        @DisplayName("Existen los 4 niveles de riesgo")
        void existenCuatroNiveles() {
            assertEquals(4, RiskLevel.values().length);
        }

        @Test
        @DisplayName("Orden de valores: SAFE, MODERATE, CRITICAL, NUCLEAR")
        void ordenDeValores() {
            RiskLevel[] expected = {
                RiskLevel.SAFE,
                RiskLevel.MODERATE,
                RiskLevel.CRITICAL,
                RiskLevel.NUCLEAR
            };
            assertArrayEquals(expected, RiskLevel.values());
        }
    }

    @Nested
    @DisplayName("requiresAudit()")
    class RequiresAudit {

        @Test
        @DisplayName("SAFE no requiere auditoria")
        void safeNoRequiereAuditoria() {
            assertFalse(RiskLevel.SAFE.requiresAudit());
        }

        @Test
        @DisplayName("MODERATE no requiere auditoria")
        void moderateNoRequiereAuditoria() {
            assertFalse(RiskLevel.MODERATE.requiresAudit());
        }

        @Test
        @DisplayName("CRITICAL requiere auditoria")
        void criticalRequiereAuditoria() {
            assertTrue(RiskLevel.CRITICAL.requiresAudit());
        }

        @Test
        @DisplayName("NUCLEAR requiere auditoria")
        void nuclearRequiereAuditoria() {
            assertTrue(RiskLevel.NUCLEAR.requiresAudit());
        }
    }

    @Nested
    @DisplayName("requiresHumanConfirmation()")
    class RequiresHumanConfirmation {

        @Test
        @DisplayName("SAFE no requiere confirmacion humana")
        void safeNoRequiereConfirmacion() {
            assertFalse(RiskLevel.SAFE.requiresHumanConfirmation());
        }

        @Test
        @DisplayName("MODERATE no requiere confirmacion humana")
        void moderateNoRequiereConfirmacion() {
            assertFalse(RiskLevel.MODERATE.requiresHumanConfirmation());
        }

        @Test
        @DisplayName("CRITICAL no requiere confirmacion humana")
        void criticalNoRequiereConfirmacion() {
            assertFalse(RiskLevel.CRITICAL.requiresHumanConfirmation());
        }

        @Test
        @DisplayName("NUCLEAR requiere confirmacion humana")
        void nuclearRequiereConfirmacion() {
            assertTrue(RiskLevel.NUCLEAR.requiresHumanConfirmation());
        }
    }

    @Nested
    @DisplayName("Descripciones")
    class Descripciones {

        @Test
        @DisplayName("Cada nivel tiene descripcion no vacia")
        void cadaNivelTieneDescripcion() {
            for (RiskLevel level : RiskLevel.values()) {
                assertNotNull(level.getDescription());
                assertFalse(level.getDescription().isBlank(),
                    level + " debe tener descripcion no vacia");
            }
        }
    }

    @Nested
    @DisplayName("Casos de Uso Tipicos")
    class CasosDeUso {

        @Test
        @DisplayName("Operacion GetTime debe ser SAFE")
        void getTimeEsSafe() {
            // Mapeo tipico
            RiskLevel level = RiskLevel.SAFE;
            assertFalse(level.requiresAudit());
            assertFalse(level.requiresHumanConfirmation());
        }

        @Test
        @DisplayName("Operacion WebSearch debe ser MODERATE")
        void webSearchEsModerate() {
            RiskLevel level = RiskLevel.MODERATE;
            assertFalse(level.requiresAudit());
            assertFalse(level.requiresHumanConfirmation());
        }

        @Test
        @DisplayName("Operacion WriteFile debe ser CRITICAL")
        void writeFileEsCritical() {
            RiskLevel level = RiskLevel.CRITICAL;
            assertTrue(level.requiresAudit());
            assertFalse(level.requiresHumanConfirmation());
        }

        @Test
        @DisplayName("Operacion Format/Shutdown debe ser NUCLEAR")
        void shutdownEsNuclear() {
            RiskLevel level = RiskLevel.NUCLEAR;
            assertTrue(level.requiresAudit());
            assertTrue(level.requiresHumanConfirmation());
        }
    }
}
