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
package dev.fararoni.bus.agent.api;

import java.time.Duration;
import java.time.Instant;

/**
 * Skill dinamico con capacidad de Health Check para sidecars.
 *
 * <p>Extiende {@link ToolSkill} con metodos para verificar la disponibilidad
 * en tiempo real de servicios externos (sidecars). Permite al Kernel descubrir
 * automaticamente que capacidades tiene disponibles.</p>
 *
 * <h2>Arquitectura de Grado Militar</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                      DYNAMIC SKILL                                   │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │                                                                      │
 * │  SkillWatcher (cada 5s)                                             │
 * │          │                                                           │
 * │          ▼                                                           │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  1. refreshAvailability() -> HTTP ping a sidecar            │    │
 * │  │  2. Si responde OK -> isAvailable() = true                  │    │
 * │  │  3. Si falla -> isAvailable() = false                       │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                      │
 * │  Kernel consulta:                                                    │
 * │    "¿Qué puedo hacer hoy?"                                          │
 * │          │                                                           │
 * │          ▼                                                           │
 * │  SkillRegistry.getAvailableSkills()                                 │
 * │    -> [MailSkill(online), SlackSkill(offline), DBSkill(online)]     │
 * │                                                                      │
 * └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Uso</h2>
 * <pre>{@code
 * public class MailSidecarSkill implements DynamicSkill {
 *     private final String endpoint = "http://localhost:3004";
 *     private volatile boolean available = false;
 *     private Instant lastCheck = Instant.EPOCH;
 *
 *     @Override
 *     public boolean checkHealth() {
 *         // HTTP GET to endpoint/health
 *         return httpClient.get(endpoint + "/health").isOk();
 *     }
 *
 *     @Override
 *     public void refreshAvailability() {
 *         this.available = checkHealth();
 *         this.lastCheck = Instant.now();
 *     }
 *
 *     @Override
 *     public boolean isAvailable() {
 *         return available;
 *     }
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see ToolSkill
 * @see SidecarBridgeSkill
 */
public interface DynamicSkill extends ToolSkill {

    /**
     * Realiza un health check al sidecar/servicio externo.
     *
     * <p>Este metodo hace una verificacion activa (ej: HTTP ping) para
     * determinar si el sidecar esta respondiendo. Debe ser rapido
     * (timeout < 500ms) para no bloquear el SkillWatcher.</p>
     *
     * @return true si el sidecar responde correctamente
     */
    boolean checkHealth();

    /**
     * Actualiza el estado de disponibilidad basado en health check.
     *
     * <p>Llamado periodicamente por el {@code SkillWatcher} (cada 5 segundos).
     * Debe actualizar el estado interno que retorna {@link #isAvailable()}.</p>
     *
     * <p>Implementacion tipica:</p>
     * <pre>{@code
     * public void refreshAvailability() {
     *     this.lastKnownStatus = checkHealth();
     *     this.lastCheckTime = Instant.now();
     * }
     * }</pre>
     */
    void refreshAvailability();

    /**
     * Obtiene el endpoint del sidecar.
     *
     * <p>Usado para logging y diagnostico.</p>
     *
     * @return URL base del sidecar (ej: "http://localhost:3004")
     */
    String getSidecarEndpoint();

    /**
     * Obtiene el timestamp del ultimo health check.
     *
     * @return Instant del ultimo check, o {@link Instant#EPOCH} si nunca se ha chequeado
     */
    default Instant getLastCheckTime() {
        return Instant.EPOCH;
    }

    /**
     * Obtiene el timeout para el health check.
     *
     * @return Duration del timeout (default: 500ms)
     */
    default Duration getHealthCheckTimeout() {
        return Duration.ofMillis(500);
    }

    /**
     * Indica si este skill requiere que el sidecar este online para funcionar.
     *
     * <p>Algunos skills pueden funcionar en modo degradado sin el sidecar.
     * Por defecto, se requiere que este online.</p>
     *
     * @return true si requiere sidecar online
     */
    default boolean requiresSidecar() {
        return true;
    }

    /**
     * Categoria del sidecar para agrupacion logica.
     *
     * <p>Categorias sugeridas:</p>
     * <ul>
     *   <li>IO - Entrada/Salida (Telegram, WhatsApp, Discord)</li>
     *   <li>COLLABORATION - Colaboracion (Slack, Email)</li>
     *   <li>DATA - Datos (Database, ERP)</li>
     *   <li>INTEGRATION - Integraciones (APIs externas)</li>
     * </ul>
     *
     * @return categoria del sidecar
     */
    default SidecarCategory getCategory() {
        return SidecarCategory.INTEGRATION;
    }

    /**
     * Categorias de sidecars para Agent Mesh.
     */
    enum SidecarCategory {
        /** Entrada/Salida - Telegram, WhatsApp, Discord */
        IO,
        /** Colaboracion - Slack, Email */
        COLLABORATION,
        /** Datos - Database, ERP */
        DATA,
        /** Integraciones - APIs externas */
        INTEGRATION
    }
}
