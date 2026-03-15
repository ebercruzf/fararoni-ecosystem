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
 * Clasificacion de riesgo para operaciones/herramientas.
 *
 * <p>Usado para auditoria y para determinar el nivel de logging
 * y alertas cuando se ejecuta o se deniega una operacion.</p>
 *
 * <h2>Niveles de Riesgo</h2>
 * <ul>
 *   <li>{@link #SAFE} - Sin riesgo (GetTime, Echo)</li>
 *   <li>{@link #MODERATE} - Riesgo moderado (WebSearch, ReadFile)</li>
 *   <li>{@link #CRITICAL} - Riesgo critico (WriteFile, ExecShell)</li>
 *   <li>{@link #NUCLEAR} - Riesgo catastrofico (Shutdown, Format)</li>
 * </ul>
 *
 * @author Fararoni Framework
 * @since 1.0.0
 */
public enum RiskLevel {

    /**
     * Sin riesgo. Operaciones inocuas.
     * Ejemplos: GetTime, Echo, GetVersion
     */
    SAFE("Sin riesgo", 0),

    /**
     * Riesgo moderado. Lectura de datos.
     * Ejemplos: WebSearch, ReadFile, ListDirectory
     */
    MODERATE("Riesgo moderado", 1),

    /**
     * Riesgo critico. Modificacion de datos.
     * Ejemplos: WriteFile, ExecuteShell, DeleteFile
     */
    CRITICAL("Riesgo critico", 2),

    /**
     * Riesgo catastrofico. Operaciones irreversibles.
     * Ejemplos: Shutdown, Format, DropDatabase
     */
    NUCLEAR("Riesgo catastrofico", 3);

    private final String description;
    private final int severity;

    RiskLevel(String description, int severity) {
        this.description = description;
        this.severity = severity;
    }

    /**
     * Obtiene la descripcion del nivel de riesgo.
     *
     * @return Descripcion legible
     */
    public String getDescription() {
        return description;
    }

    /**
     * Obtiene la severidad numerica.
     *
     * @return Numero de 0 (SAFE) a 3 (NUCLEAR)
     */
    public int getSeverity() {
        return severity;
    }

    /**
     * Verifica si este nivel requiere auditoria.
     *
     * @return true si es CRITICAL o NUCLEAR
     */
    public boolean requiresAudit() {
        return this == CRITICAL || this == NUCLEAR;
    }

    /**
     * Verifica si este nivel requiere confirmacion humana.
     *
     * @return true si es NUCLEAR
     */
    public boolean requiresHumanConfirmation() {
        return this == NUCLEAR;
    }
}
