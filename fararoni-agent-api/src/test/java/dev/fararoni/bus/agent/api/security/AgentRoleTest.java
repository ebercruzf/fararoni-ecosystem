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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para AgentRole.
 * Valida: jerarquia de roles, canAccess(), isHigherThan().
 */
@DisplayName("AgentRole")
class AgentRoleTest {

    @Nested
    @DisplayName("Jerarquia de Niveles")
    class JerarquiaNiveles {

        @Test
        @DisplayName("OBSERVER tiene nivel 0")
        void observerNivel0() {
            assertEquals(0, AgentRole.OBSERVER.getLevel());
        }

        @Test
        @DisplayName("JUNIOR_AGENT tiene nivel 1")
        void juniorAgentNivel1() {
            assertEquals(1, AgentRole.JUNIOR_AGENT.getLevel());
        }

        @Test
        @DisplayName("SENIOR_AGENT tiene nivel 2")
        void seniorAgentNivel2() {
            assertEquals(2, AgentRole.SENIOR_AGENT.getLevel());
        }

        @Test
        @DisplayName("SYSTEM_ARCH tiene nivel 3")
        void systemArchNivel3() {
            assertEquals(3, AgentRole.SYSTEM_ARCH.getLevel());
        }

        @Test
        @DisplayName("ADMIN tiene nivel 99")
        void adminNivel99() {
            assertEquals(99, AgentRole.ADMIN.getLevel());
        }

        @Test
        @DisplayName("Niveles estan ordenados correctamente")
        void nivelesOrdenados() {
            assertTrue(AgentRole.OBSERVER.getLevel() < AgentRole.JUNIOR_AGENT.getLevel());
            assertTrue(AgentRole.JUNIOR_AGENT.getLevel() < AgentRole.SENIOR_AGENT.getLevel());
            assertTrue(AgentRole.SENIOR_AGENT.getLevel() < AgentRole.SYSTEM_ARCH.getLevel());
            assertTrue(AgentRole.SYSTEM_ARCH.getLevel() < AgentRole.ADMIN.getLevel());
        }
    }

    @Nested
    @DisplayName("canAccess()")
    class CanAccess {

        @Test
        @DisplayName("Rol puede acceder a si mismo")
        void rolPuedeAccederASiMismo() {
            for (AgentRole role : AgentRole.values()) {
                assertTrue(role.canAccess(role),
                    role + " debe poder acceder a recursos que requieren " + role);
            }
        }

        @Test
        @DisplayName("ADMIN puede acceder a todo")
        void adminPuedeAccederATodo() {
            for (AgentRole required : AgentRole.values()) {
                assertTrue(AgentRole.ADMIN.canAccess(required),
                    "ADMIN debe poder acceder a " + required);
            }
        }

        @Test
        @DisplayName("OBSERVER no puede acceder a roles superiores")
        void observerNoPuedeAccederASuperiores() {
            assertFalse(AgentRole.OBSERVER.canAccess(AgentRole.JUNIOR_AGENT));
            assertFalse(AgentRole.OBSERVER.canAccess(AgentRole.SENIOR_AGENT));
            assertFalse(AgentRole.OBSERVER.canAccess(AgentRole.SYSTEM_ARCH));
            assertFalse(AgentRole.OBSERVER.canAccess(AgentRole.ADMIN));
        }

        @ParameterizedTest
        @CsvSource({
            "SENIOR_AGENT, JUNIOR_AGENT, true",
            "SENIOR_AGENT, OBSERVER, true",
            "JUNIOR_AGENT, SENIOR_AGENT, false",
            "SYSTEM_ARCH, SENIOR_AGENT, true",
            "SYSTEM_ARCH, ADMIN, false"
        })
        @DisplayName("Matriz de acceso rol -> requerido")
        void matrizAcceso(AgentRole userRole, AgentRole requiredRole, boolean expected) {
            assertEquals(expected, userRole.canAccess(requiredRole),
                userRole + ".canAccess(" + requiredRole + ") debe ser " + expected);
        }
    }

    @Nested
    @DisplayName("isHigherThan()")
    class IsHigherThan {

        @Test
        @DisplayName("Rol no es superior a si mismo")
        void rolNoEsSuperiorASiMismo() {
            for (AgentRole role : AgentRole.values()) {
                assertFalse(role.isHigherThan(role),
                    role + " no debe ser superior a si mismo");
            }
        }

        @Test
        @DisplayName("ADMIN es superior a todos excepto si mismo")
        void adminEsSuperiorATodos() {
            assertTrue(AgentRole.ADMIN.isHigherThan(AgentRole.OBSERVER));
            assertTrue(AgentRole.ADMIN.isHigherThan(AgentRole.JUNIOR_AGENT));
            assertTrue(AgentRole.ADMIN.isHigherThan(AgentRole.SENIOR_AGENT));
            assertTrue(AgentRole.ADMIN.isHigherThan(AgentRole.SYSTEM_ARCH));
        }

        @Test
        @DisplayName("OBSERVER no es superior a nadie")
        void observerNoEsSuperior() {
            for (AgentRole role : AgentRole.values()) {
                assertFalse(AgentRole.OBSERVER.isHigherThan(role));
            }
        }
    }

    @Nested
    @DisplayName("fromLevel()")
    class FromLevel {

        @Test
        @DisplayName("Retorna rol correcto para cada nivel")
        void retornaRolCorrecto() {
            assertEquals(AgentRole.OBSERVER, AgentRole.fromLevel(0));
            assertEquals(AgentRole.JUNIOR_AGENT, AgentRole.fromLevel(1));
            assertEquals(AgentRole.SENIOR_AGENT, AgentRole.fromLevel(2));
            assertEquals(AgentRole.SYSTEM_ARCH, AgentRole.fromLevel(3));
            assertEquals(AgentRole.ADMIN, AgentRole.fromLevel(99));
        }

        @Test
        @DisplayName("Retorna OBSERVER para nivel desconocido")
        void retornaObserverParaNivelDesconocido() {
            assertEquals(AgentRole.OBSERVER, AgentRole.fromLevel(-1));
            assertEquals(AgentRole.OBSERVER, AgentRole.fromLevel(50));
            assertEquals(AgentRole.OBSERVER, AgentRole.fromLevel(100));
        }
    }

    @Nested
    @DisplayName("Descripciones")
    class Descripciones {

        @Test
        @DisplayName("Cada rol tiene descripcion no vacia")
        void cadaRolTieneDescripcion() {
            for (AgentRole role : AgentRole.values()) {
                assertNotNull(role.getDescription());
                assertFalse(role.getDescription().isBlank(),
                    role + " debe tener descripcion no vacia");
            }
        }
    }
}
