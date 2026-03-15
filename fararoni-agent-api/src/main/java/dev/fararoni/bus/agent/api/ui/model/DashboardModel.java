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
package dev.fararoni.bus.agent.api.ui.model;

import dev.fararoni.bus.agent.api.interaction.AgentUserInterface;

import java.util.List;
import java.util.Objects;

/**
 * Modelo de datos para el Dashboard principal del Agente Fararoni.
 *
 * <h2>Proposito</h2>
 * <p>Encapsula toda la informacion necesaria para renderizar el dashboard
 * del agente en la terminal. El modulo Enterprise construye este modelo
 * con datos de estado, y el Core lo renderiza usando JLine.</p>
 *
 * <h2>Patron Render Server</h2>
 * <p>Este modelo es el ejemplo principal del patron:</p>
 * <ul>
 *   <li>Enterprise decide QUE mostrar (construye este record)</li>
 *   <li>Core decide COMO mostrarlo (usa JLine, colores, layout)</li>
 * </ul>
 *
 * <h2>Layout Visual Tipico</h2>
 * <pre>
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    FARARONI AGENT DASHBOARD                       ║
 * ╠══════════════════════════════════════════════════════════════════╣
 * ║  Status:        THINKING                                          ║
 * ║  Tokens:        4,500 / 8,000 (56%)                              ║
 * ║  Skills:        [RAG] [GitSkill] [OracleDB]                      ║
 * ║  Current Task:  Analizando User.java                             ║
 * ╠══════════════════════════════════════════════════════════════════╣
 * ║  LOGS RECIENTES                                                   ║
 * ║  [10:45:32] INFO  Conectado a repositorio                        ║
 * ║  [10:45:33] INFO  Cargando archivos del contexto                 ║
 * ║  [10:45:35] WARN  Archivo grande detectado: User.java (2.5 KB)   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 * </pre>
 *
 * <h2>Ejemplo de Uso (desde Enterprise)</h2>
 * <pre>{@code
 * // Enterprise construye el modelo con datos reales
 * var dashboard = new DashboardModel(
 *     "THINKING",
 *     4500,
 *     8000,
 *     List.of("RAG", "GitSkill", "OracleDB"),
 *     "Analizando User.java",
 *     List.of(
 *         LogEntry.info("Conectado a repositorio"),
 *         LogEntry.info("Cargando archivos del contexto"),
 *         LogEntry.warn("Archivo grande detectado: User.java")
 *     )
 * );
 *
 * // Enterprise pide a Core que renderice (sin saber como)
 * context.getUI().renderDashboard(dashboard);
 * }</pre>
 *
 * <h2>Estados Posibles (agentStatus)</h2>
 * <ul>
 *   <li><b>IDLE:</b> Agente esperando input del usuario</li>
 *   <li><b>THINKING:</b> Procesando con el LLM</li>
 *   <li><b>EXECUTING:</b> Ejecutando una herramienta/skill</li>
 *   <li><b>ANALYZING:</b> Analizando codigo o documentos</li>
 *   <li><b>WAITING:</b> Esperando respuesta externa (BD, API)</li>
 *   <li><b>ERROR:</b> En estado de error</li>
 * </ul>
 *
 * @param agentStatus  estado actual del agente (IDLE, THINKING, EXECUTING, etc.)
 * @param tokensUsed   tokens consumidos en la sesion actual
 * @param tokensLimit  limite maximo de tokens disponibles
 * @param activeSkills lista de skills actualmente cargados/activos
 * @param currentTask  descripcion breve de la tarea actual (puede ser null si IDLE)
 * @param recentLogs   ultimas entradas de log para mostrar (maximo ~5-10)
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see LogEntry
 * @see AgentUserInterface#renderDashboard(DashboardModel)
 */
public record DashboardModel(
    String agentStatus,
    int tokensUsed,
    int tokensLimit,
    List<String> activeSkills,
    String currentTask,
    List<LogEntry> recentLogs
) {
    // ========================================================================
    // CONSTANTES DE ESTADO
    // ========================================================================

    /** Estado: Agente inactivo, esperando input. */
    public static final String STATUS_IDLE = "IDLE";

    /** Estado: Agente procesando con LLM. */
    public static final String STATUS_THINKING = "THINKING";

    /** Estado: Agente ejecutando herramienta. */
    public static final String STATUS_EXECUTING = "EXECUTING";

    /** Estado: Agente analizando codigo. */
    public static final String STATUS_ANALYZING = "ANALYZING";

    /** Estado: Agente esperando respuesta externa. */
    public static final String STATUS_WAITING = "WAITING";

    /** Estado: Agente en error. */
    public static final String STATUS_ERROR = "ERROR";

    // ========================================================================
    // VALIDACION EN CONSTRUCTOR CANONICO
    // ========================================================================

    /**
     * Constructor canonico con validacion y copias defensivas.
     *
     * @param agentStatus  estado del agente (no puede ser null)
     * @param tokensUsed   tokens usados (no puede ser negativo)
     * @param tokensLimit  limite de tokens (debe ser >= tokensUsed)
     * @param activeSkills skills activos (no puede ser null, se hace copia)
     * @param currentTask  tarea actual (puede ser null)
     * @param recentLogs   logs recientes (no puede ser null, se hace copia)
     * @throws NullPointerException si agentStatus, activeSkills o recentLogs son null
     * @throws IllegalArgumentException si tokensUsed &lt; 0 o tokensLimit &lt; tokensUsed
     */
    public DashboardModel {
        Objects.requireNonNull(agentStatus, "agentStatus no puede ser null");
        Objects.requireNonNull(activeSkills, "activeSkills no puede ser null");
        Objects.requireNonNull(recentLogs, "recentLogs no puede ser null");

        if (tokensUsed < 0) {
            throw new IllegalArgumentException("tokensUsed no puede ser negativo: " + tokensUsed);
        }

        if (tokensLimit < tokensUsed) {
            throw new IllegalArgumentException(
                "tokensLimit (" + tokensLimit + ") no puede ser menor que tokensUsed (" + tokensUsed + ")"
            );
        }

        // Copias defensivas inmutables
        activeSkills = List.copyOf(activeSkills);
        recentLogs = List.copyOf(recentLogs);
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Crea un dashboard en estado IDLE (sin tarea activa).
     *
     * @param tokensUsed   tokens consumidos
     * @param tokensLimit  limite de tokens
     * @param activeSkills skills cargados
     * @return DashboardModel en estado IDLE
     */
    public static DashboardModel idle(int tokensUsed, int tokensLimit, List<String> activeSkills) {
        return new DashboardModel(
            STATUS_IDLE,
            tokensUsed,
            tokensLimit,
            activeSkills,
            null,
            List.of()
        );
    }

    /**
     * Crea un dashboard en estado THINKING.
     *
     * @param tokensUsed   tokens consumidos
     * @param tokensLimit  limite de tokens
     * @param activeSkills skills cargados
     * @param task         descripcion de la tarea
     * @return DashboardModel en estado THINKING
     */
    public static DashboardModel thinking(int tokensUsed, int tokensLimit,
                                          List<String> activeSkills, String task) {
        return new DashboardModel(
            STATUS_THINKING,
            tokensUsed,
            tokensLimit,
            activeSkills,
            task,
            List.of()
        );
    }

    // ========================================================================
    // METODOS DE UTILIDAD
    // ========================================================================

    /**
     * Calcula el porcentaje de tokens usados.
     *
     * @return porcentaje de 0 a 100
     */
    public int getTokenUsagePercent() {
        if (tokensLimit == 0) return 0;
        return (int) ((tokensUsed * 100.0) / tokensLimit);
    }

    /**
     * Verifica si el agente esta en un estado activo (no IDLE).
     *
     * @return true si esta procesando algo
     */
    public boolean isActive() {
        return !STATUS_IDLE.equalsIgnoreCase(agentStatus);
    }

    /**
     * Verifica si el agente esta en estado de error.
     *
     * @return true si agentStatus es ERROR
     */
    public boolean isError() {
        return STATUS_ERROR.equalsIgnoreCase(agentStatus);
    }

    /**
     * Verifica si hay una tarea actual definida.
     *
     * @return true si currentTask no es null ni vacio
     */
    public boolean hasCurrentTask() {
        return currentTask != null && !currentTask.isBlank();
    }

    /**
     * Obtiene los tokens restantes disponibles.
     *
     * @return tokensLimit - tokensUsed
     */
    public int getTokensRemaining() {
        return tokensLimit - tokensUsed;
    }

    /**
     * Crea una copia con un nuevo log agregado.
     *
     * <p>Mantiene solo los ultimos N logs para evitar crecimiento ilimitado.</p>
     *
     * @param log     nuevo log a agregar
     * @param maxLogs maximo de logs a mantener
     * @return nuevo DashboardModel con el log agregado
     */
    public DashboardModel withLog(LogEntry log, int maxLogs) {
        var newLogs = new java.util.ArrayList<>(recentLogs);
        newLogs.add(log);

        // Mantener solo los ultimos N
        while (newLogs.size() > maxLogs) {
            newLogs.remove(0);
        }

        return new DashboardModel(
            agentStatus,
            tokensUsed,
            tokensLimit,
            activeSkills,
            currentTask,
            newLogs
        );
    }

    /**
     * Crea una copia con estado actualizado.
     *
     * @param newStatus nuevo estado del agente
     * @return nuevo DashboardModel con el estado actualizado
     */
    public DashboardModel withStatus(String newStatus) {
        return new DashboardModel(
            newStatus,
            tokensUsed,
            tokensLimit,
            activeSkills,
            currentTask,
            recentLogs
        );
    }

    /**
     * Crea una copia con tokens actualizados.
     *
     * @param newTokensUsed nuevo conteo de tokens
     * @return nuevo DashboardModel con tokens actualizados
     */
    public DashboardModel withTokens(int newTokensUsed) {
        return new DashboardModel(
            agentStatus,
            newTokensUsed,
            tokensLimit,
            activeSkills,
            currentTask,
            recentLogs
        );
    }
}
