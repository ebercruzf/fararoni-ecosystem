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

/**
 * Roles de agentes ordenados por jerarquia de privilegios.
 *
 * <p>Un rol superior puede hacer todo lo que puede hacer un rol inferior.
 * Por ejemplo, ADMIN puede hacer todo lo de SYSTEM_ARCH, que puede hacer
 * todo lo de SENIOR_AGENT, etc.</p>
 *
 * <h2>Jerarquia de Privilegios</h2>
 * <pre>
 * ADMIN (99)
 *   └── SYSTEM_ARCH (3)
 *         └── SENIOR_AGENT (2)
 *               └── JUNIOR_AGENT (1)
 *                     └── OBSERVER (0)
 * </pre>
 *
 * <h2>Uso Tipico</h2>
 * <pre>{@code
 * AgentRole userRole = AgentRole.SENIOR_AGENT;
 * AgentRole requiredRole = AgentRole.JUNIOR_AGENT;
 *
 * if (userRole.canAccess(requiredRole)) {
 *     // Permitido - SENIOR puede hacer lo de JUNIOR
 * }
 * }</pre>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 */
public enum AgentRole {

    /**
     * Solo lectura. No puede ejecutar herramientas.
     */
    OBSERVER(0, "Solo lectura"),

    /**
     * Busquedas basicas, sin escritura en disco.
     */
    JUNIOR_AGENT(1, "Busquedas basicas"),

    /**
     * Escritura en disco limitada (dentro del sandbox).
     */
    SENIOR_AGENT(2, "Escritura sandbox"),

    /**
     * Acceso total al sistema de archivos.
     */
    SYSTEM_ARCH(3, "Acceso total filesystem"),

    /**
     * Dios. Kill switch, configuracion, todo.
     */
    ADMIN(99, "Administrador total");

    private final int level;
    private final String description;

    AgentRole(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * Obtiene el nivel numerico del rol.
     *
     * @return Nivel de privilegio (mayor = mas privilegios)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Obtiene la descripcion del rol.
     *
     * @return Descripcion legible
     */
    public String getDescription() {
        return description;
    }

    /**
     * Verifica si este rol puede acceder a recursos que requieren otro rol.
     *
     * @param requiredRole Rol minimo requerido
     * @return true si este rol tiene suficientes privilegios
     */
    public boolean canAccess(AgentRole requiredRole) {
        return this.level >= requiredRole.level;
    }

    /**
     * Verifica si este rol es estrictamente superior a otro.
     *
     * @param other Otro rol
     * @return true si este rol tiene mas privilegios
     */
    public boolean isHigherThan(AgentRole other) {
        return this.level > other.level;
    }

    /**
     * Obtiene el rol por nivel numerico.
     *
     * @param level Nivel de privilegio
     * @return Rol correspondiente o OBSERVER si no existe
     */
    public static AgentRole fromLevel(int level) {
        for (AgentRole role : values()) {
            if (role.level == level) {
                return role;
            }
        }
        return OBSERVER;
    }
}
