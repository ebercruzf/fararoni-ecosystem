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

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import java.util.concurrent.CompletableFuture;

/**
 * Inspector de seguridad para validacion RBAC.
 *
 * <p>Define el contrato para verificar si un mensaje tiene permiso
 * para acceder a un topico/capacidad. Es agnostico a la implementacion
 * (puede ser memoria, LDAP, OAuth2, etc.).</p>
 *
 * <h2>Comportamiento Grado Militar</h2>
 * <ul>
 *   <li><b>Deny-by-Default:</b> Si no hay policy definida, se rechaza</li>
 *   <li><b>Asincrono:</b> Permite validacion remota sin bloquear</li>
 *   <li><b>Auditoria:</b> Cada rechazo debe ser logueado</li>
 * </ul>
 *
 * <h2>Implementaciones</h2>
 * <ul>
 *   <li>{@code InMemorySecurityInspector} - fararoni-core (desarrollo)</li>
 *   <li>{@code KeycloakSecurityInspector} - fararoni-enterprise (produccion)</li>
 * </ul>
 *
 * <h2>Uso Tipico</h2>
 * <pre>{@code
 * SecurityInspector inspector = new InMemorySecurityInspector();
 *
 * // Definir policies
 * inspector.definePolicy(CapabilityMetadata.create(
 *     "skill.fs.write",
 *     AgentRole.SENIOR_AGENT,
 *     RiskLevel.CRITICAL
 * ));
 *
 * // Validar en el Gateway
 * inspector.inspect("skill.fs.write", envelope)
 *     .thenRun(() -> executeCapability())
 *     .exceptionally(ex -> {
 *         log.warn("Acceso denegado: {}", ex.getMessage());
 *         return null;
 *     });
 * }</pre>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 * @see CapabilityMetadata
 * @see AgentRole
 */
public interface SecurityInspector {

    /**
     * Valida si el envelope tiene permiso para acceder al topico.
     *
     * <p>El metodo es asincrono para permitir validaciones remotas
     * (ej: consulta a LDAP, OAuth2 token validation).</p>
     *
     * @param topic    Topico/capacidad a validar (ej: "skill.fs.write")
     * @param envelope Sobre con userId y contexto
     * @return Future que completa si es valido, o falla con SecurityException si no
     */
    CompletableFuture<Void> inspect(String topic, SovereignEnvelope<?> envelope);

    /**
     * Registra una policy de seguridad.
     *
     * <p>Define que rol se necesita para acceder a una capacidad.</p>
     *
     * @param metadata Metadatos de la capacidad
     */
    void definePolicy(CapabilityMetadata metadata);

    /**
     * Verifica si existe una policy para el topico.
     *
     * @param topic Topico a verificar
     * @return true si hay policy definida
     */
    default boolean hasPolicy(String topic) {
        return false;
    }

    /**
     * Obtiene la policy para un topico.
     *
     * @param topic Topico a buscar
     * @return Metadata o null si no existe
     */
    default CapabilityMetadata getPolicy(String topic) {
        return null;
    }
}
