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

import java.time.Duration;

/**
 * Metadatos de una capacidad/herramienta ("Visa" de permisos).
 *
 * <p>Define los requisitos de seguridad para ejecutar una herramienta:
 * que rol se necesita, que tan peligrosa es, y cuanto tiempo puede tardar.</p>
 *
 * <h2>Uso Tipico</h2>
 * <pre>{@code
 * // Definir policy para fs_write
 * var metadata = CapabilityMetadata.create(
 *     "skill.fs.write",
 *     AgentRole.SENIOR_AGENT,
 *     RiskLevel.CRITICAL
 * );
 *
 * inspector.definePolicy(metadata);
 * }</pre>
 *
 * @param name         Nombre de la capacidad (ej: "skill.fs.write")
 * @param requiredRole Rol minimo requerido para ejecutar
 * @param riskLevel    Nivel de riesgo de la operacion
 * @param timeout      Timeout maximo permitido
 * @param description  Descripcion legible de la capacidad
 * @author Fararoni Framework
 * @since 1.0.0
 */
public record CapabilityMetadata(
    String name,
    AgentRole requiredRole,
    RiskLevel riskLevel,
    Duration timeout,
    String description
) {

    /** Timeout por defecto: 30 segundos */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Factory method simplificado con timeout por defecto.
     *
     * @param name Nombre de la capacidad
     * @param role Rol requerido
     * @param risk Nivel de riesgo
     * @return Nuevo CapabilityMetadata
     */
    public static CapabilityMetadata create(String name, AgentRole role, RiskLevel risk) {
        return new CapabilityMetadata(name, role, risk, DEFAULT_TIMEOUT, "");
    }

    /**
     * Factory method con descripcion.
     *
     * @param name        Nombre de la capacidad
     * @param role        Rol requerido
     * @param risk        Nivel de riesgo
     * @param description Descripcion de la capacidad
     * @return Nuevo CapabilityMetadata
     */
    public static CapabilityMetadata create(String name, AgentRole role, RiskLevel risk, String description) {
        return new CapabilityMetadata(name, role, risk, DEFAULT_TIMEOUT, description);
    }

    /**
     * Factory method completo.
     *
     * @param name        Nombre de la capacidad
     * @param role        Rol requerido
     * @param risk        Nivel de riesgo
     * @param timeout     Timeout maximo
     * @param description Descripcion de la capacidad
     * @return Nuevo CapabilityMetadata
     */
    public static CapabilityMetadata of(
            String name,
            AgentRole role,
            RiskLevel risk,
            Duration timeout,
            String description) {
        return new CapabilityMetadata(name, role, risk, timeout, description);
    }

    /**
     * Verifica si esta capacidad requiere auditoria.
     *
     * @return true si el riskLevel es CRITICAL o NUCLEAR
     */
    public boolean requiresAudit() {
        return riskLevel.requiresAudit();
    }

    /**
     * Verifica si esta capacidad requiere confirmacion humana.
     *
     * @return true si el riskLevel es NUCLEAR
     */
    public boolean requiresHumanConfirmation() {
        return riskLevel.requiresHumanConfirmation();
    }

    /**
     * Crea copia con diferente timeout.
     *
     * @param newTimeout Nuevo timeout
     * @return Nueva instancia con timeout modificado
     */
    public CapabilityMetadata withTimeout(Duration newTimeout) {
        return new CapabilityMetadata(name, requiredRole, riskLevel, newTimeout, description);
    }
}
