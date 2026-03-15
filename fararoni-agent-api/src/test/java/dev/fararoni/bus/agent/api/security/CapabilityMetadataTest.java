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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para CapabilityMetadata.
 * Valida: factory methods, withers, delegacion a RiskLevel.
 */
@DisplayName("CapabilityMetadata")
class CapabilityMetadataTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("create(name, role, risk) usa timeout por defecto")
        void createBasico() {
            var metadata = CapabilityMetadata.create(
                "skill.fs.write",
                AgentRole.SENIOR_AGENT,
                RiskLevel.CRITICAL
            );

            assertEquals("skill.fs.write", metadata.name());
            assertEquals(AgentRole.SENIOR_AGENT, metadata.requiredRole());
            assertEquals(RiskLevel.CRITICAL, metadata.riskLevel());
            assertEquals(CapabilityMetadata.DEFAULT_TIMEOUT, metadata.timeout());
            assertEquals("", metadata.description());
        }

        @Test
        @DisplayName("create(name, role, risk, description) incluye descripcion")
        void createConDescripcion() {
            var metadata = CapabilityMetadata.create(
                "skill.web.search",
                AgentRole.JUNIOR_AGENT,
                RiskLevel.MODERATE,
                "Busqueda en internet"
            );

            assertEquals("skill.web.search", metadata.name());
            assertEquals("Busqueda en internet", metadata.description());
        }

        @Test
        @DisplayName("of() permite todos los parametros")
        void ofCompleto() {
            var timeout = Duration.ofMinutes(5);
            var metadata = CapabilityMetadata.of(
                "skill.deep.research",
                AgentRole.SYSTEM_ARCH,
                RiskLevel.MODERATE,
                timeout,
                "Investigacion profunda"
            );

            assertEquals("skill.deep.research", metadata.name());
            assertEquals(AgentRole.SYSTEM_ARCH, metadata.requiredRole());
            assertEquals(RiskLevel.MODERATE, metadata.riskLevel());
            assertEquals(timeout, metadata.timeout());
            assertEquals("Investigacion profunda", metadata.description());
        }
    }

    @Nested
    @DisplayName("Constantes")
    class Constantes {

        @Test
        @DisplayName("DEFAULT_TIMEOUT es 30 segundos")
        void defaultTimeout() {
            assertEquals(Duration.ofSeconds(30), CapabilityMetadata.DEFAULT_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Delegacion a RiskLevel")
    class DelegacionRiskLevel {

        @Test
        @DisplayName("requiresAudit() delega a RiskLevel")
        void requiresAuditDelega() {
            var safe = CapabilityMetadata.create("safe", AgentRole.OBSERVER, RiskLevel.SAFE);
            var critical = CapabilityMetadata.create("critical", AgentRole.ADMIN, RiskLevel.CRITICAL);
            var nuclear = CapabilityMetadata.create("nuclear", AgentRole.ADMIN, RiskLevel.NUCLEAR);

            assertFalse(safe.requiresAudit());
            assertTrue(critical.requiresAudit());
            assertTrue(nuclear.requiresAudit());
        }

        @Test
        @DisplayName("requiresHumanConfirmation() delega a RiskLevel")
        void requiresHumanConfirmationDelega() {
            var critical = CapabilityMetadata.create("critical", AgentRole.ADMIN, RiskLevel.CRITICAL);
            var nuclear = CapabilityMetadata.create("nuclear", AgentRole.ADMIN, RiskLevel.NUCLEAR);

            assertFalse(critical.requiresHumanConfirmation());
            assertTrue(nuclear.requiresHumanConfirmation());
        }
    }

    @Nested
    @DisplayName("withTimeout()")
    class WithTimeout {

        @Test
        @DisplayName("Crea nueva instancia con timeout diferente")
        void withTimeoutCreaNuevaInstancia() {
            var original = CapabilityMetadata.create("test", AgentRole.JUNIOR_AGENT, RiskLevel.SAFE);
            var modified = original.withTimeout(Duration.ofMinutes(10));

            assertNotSame(original, modified);
            assertEquals(CapabilityMetadata.DEFAULT_TIMEOUT, original.timeout());
            assertEquals(Duration.ofMinutes(10), modified.timeout());
        }

        @Test
        @DisplayName("Mantiene otros campos iguales")
        void withTimeoutMantieneOtrosCampos() {
            var original = CapabilityMetadata.create(
                "skill.test",
                AgentRole.SENIOR_AGENT,
                RiskLevel.CRITICAL,
                "Descripcion original"
            );
            var modified = original.withTimeout(Duration.ofSeconds(5));

            assertEquals(original.name(), modified.name());
            assertEquals(original.requiredRole(), modified.requiredRole());
            assertEquals(original.riskLevel(), modified.riskLevel());
            assertEquals(original.description(), modified.description());
        }
    }

    @Nested
    @DisplayName("Record Accessors")
    class RecordAccessors {

        @Test
        @DisplayName("Todos los accessors funcionan correctamente")
        void accessorsFuncionan() {
            var timeout = Duration.ofMinutes(2);
            var metadata = new CapabilityMetadata(
                "test.capability",
                AgentRole.SYSTEM_ARCH,
                RiskLevel.NUCLEAR,
                timeout,
                "Test capability"
            );

            assertEquals("test.capability", metadata.name());
            assertEquals(AgentRole.SYSTEM_ARCH, metadata.requiredRole());
            assertEquals(RiskLevel.NUCLEAR, metadata.riskLevel());
            assertEquals(timeout, metadata.timeout());
            assertEquals("Test capability", metadata.description());
        }
    }

    @Nested
    @DisplayName("Casos de Uso Tipicos")
    class CasosDeUso {

        @Test
        @DisplayName("fs_write: SENIOR_AGENT + CRITICAL")
        void fsWriteConfig() {
            var metadata = CapabilityMetadata.create(
                "skill.fs_write",
                AgentRole.SENIOR_AGENT,
                RiskLevel.CRITICAL,
                "Escribir archivos en disco"
            );

            assertTrue(metadata.requiresAudit());
            assertFalse(metadata.requiresHumanConfirmation());
            assertTrue(AgentRole.SENIOR_AGENT.canAccess(metadata.requiredRole()));
            assertFalse(AgentRole.JUNIOR_AGENT.canAccess(metadata.requiredRole()));
        }

        @Test
        @DisplayName("fs_read: JUNIOR_AGENT + MODERATE")
        void fsReadConfig() {
            var metadata = CapabilityMetadata.create(
                "skill.fs_read",
                AgentRole.JUNIOR_AGENT,
                RiskLevel.MODERATE,
                "Leer archivos"
            );

            assertFalse(metadata.requiresAudit());
            assertTrue(AgentRole.JUNIOR_AGENT.canAccess(metadata.requiredRole()));
            assertTrue(AgentRole.OBSERVER.canAccess(metadata.requiredRole()) == false);
        }

        @Test
        @DisplayName("shell_execute: SYSTEM_ARCH + NUCLEAR")
        void shellExecuteConfig() {
            var metadata = CapabilityMetadata.create(
                "skill.shell_execute",
                AgentRole.SYSTEM_ARCH,
                RiskLevel.NUCLEAR,
                "Ejecutar comandos de sistema"
            );

            assertTrue(metadata.requiresAudit());
            assertTrue(metadata.requiresHumanConfirmation());
            assertTrue(AgentRole.ADMIN.canAccess(metadata.requiredRole()));
            assertFalse(AgentRole.SENIOR_AGENT.canAccess(metadata.requiredRole()));
        }
    }
}
