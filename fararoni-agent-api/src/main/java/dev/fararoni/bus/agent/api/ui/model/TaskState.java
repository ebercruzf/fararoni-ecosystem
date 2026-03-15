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

/**
 * Estados posibles de una tarea en el Live Execution Tree.
 *
 * <p>Estos estados son para <b>renderizado visual</b> del arbol de progreso.
 * Son independientes de {@link AgentState.TaskStatus} que es para logica interna.</p>
 *
 * <h2>Proposito Arquitectonico</h2>
 * <p>Este enum forma parte del patron Render Server:</p>
 * <ul>
 *   <li><b>Enterprise:</b> Asigna estados a las tareas segun su logica</li>
 *   <li><b>Core:</b> Renderiza iconos y colores segun el estado</li>
 * </ul>
 *
 * <h2>Iconografia Visual</h2>
 * <table border="1">
 *   <caption>Iconografia de estados de tarea</caption>
 *   <tr><th>Estado</th><th>Icono</th><th>Color</th><th>Descripcion</th></tr>
 *   <tr><td>PENDING</td><td>&#x25E6;</td><td>Gris</td><td>En cola, aun no inicia</td></tr>
 *   <tr><td>RUNNING</td><td>Spinner</td><td>Cyan</td><td>Ejecutando activamente</td></tr>
 *   <tr><td>SUCCESS</td><td>&#x2714;</td><td>Verde</td><td>Completado exitosamente</td></tr>
 *   <tr><td>WARNING</td><td>&#x26A0;</td><td>Amarillo</td><td>Termino con alertas</td></tr>
 *   <tr><td>FAILURE</td><td>&#x2718;</td><td>Rojo</td><td>Error fatal</td></tr>
 *   <tr><td>SKIPPED</td><td>&#x21B7;</td><td>Gris</td><td>Omitido, no fue necesario</td></tr>
 *   <tr><td>CANCELED</td><td>&#x2296;</td><td>Magenta</td><td>Interrumpido por usuario</td></tr>
 *   <tr><td>INITIALIZING</td><td>&#x1F4E6;</td><td>Magenta</td><td>Arrancando entorno (Bootstrap)</td></tr>
 *   <tr><td>COMPACTING</td><td>&#x1F5DC;</td><td>Blue</td><td>Compactando contexto/tokens</td></tr>
 *   <tr><td>THROTTLING</td><td>&#x1F6A6;</td><td>Yellow</td><td>Rate limit activo (Backoff)</td></tr>
 *   <tr><td>SYSTEM_OP</td><td>&#x2699;</td><td>Cyan</td><td>Ejecutando comando de sistema</td></tr>
 * </table>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // En Enterprise (logica de negocio)
 * TaskNode node = new TaskNode("1", "Leer archivo", TaskState.RUNNING, List.of());
 *
 * // Despues de completar
 * node = new TaskNode("1", "Leer archivo", TaskState.SUCCESS, List.of());
 *
 * // Si hubo advertencias
 * node = new TaskNode("2", "Compilar", TaskState.WARNING, List.of());
 * }</pre>
 *
 * <h2>Comparacion con TaskStatus</h2>
 * <p>{@link AgentState.TaskStatus} tiene 4 estados para logica interna.
 * Este enum tiene 7 estados para feedback visual mas rico.</p>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see TaskNode
 * @see TaskTreeModel
 */
public enum TaskState {

    /**
     * En cola, aun no inicia.
     * <p>Icono: &#x25E6; (circulo vacio) | Color: Gris tenue</p>
     */
    PENDING,

    /**
     * Ejecutando activamente.
     * <p>Icono: Spinner animado (&#x280B; &#x2819; &#x2839;...) | Color: Cyan</p>
     */
    RUNNING,

    /**
     * Completado exitosamente.
     * <p>Icono: &#x2714; (checkmark) | Color: Verde</p>
     */
    SUCCESS,

    /**
     * Termino con alertas o advertencias.
     * <p>Icono: &#x26A0; (triangulo) | Color: Amarillo</p>
     * <p>Ejemplo: "Compilado, pero hay APIs deprecadas"</p>
     */
    WARNING,

    /**
     * Error fatal, la tarea fallo.
     * <p>Icono: &#x2718; (cruz) | Color: Rojo</p>
     */
    FAILURE,

    /**
     * Omitido, no fue necesario ejecutar.
     * <p>Icono: &#x21B7; (flecha curva) o &#x2500; (guion) | Color: Gris</p>
     * <p>Ejemplo: "No hubo cambios en Git, no se hizo commit"</p>
     */
    SKIPPED,

    /**
     * Interrumpido por el usuario.
     * <p>Icono: &#x2296; (circulo con linea) | Color: Magenta</p>
     * <p>Ejemplo: "El usuario presiono Ctrl+C"</p>
     */
    CANCELED,

    // ════════════════════════════════════════════════════════════════════════
    // ESTADOS "FARARONI ENTERPRISE" (Identidad Propia)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Arrancando entorno (Bootstrap/Initialization).
     * <p>Icono: &#x1F4E6; (caja/paquete) | Color: Magenta</p>
     * <p>Ejemplo: "Initializing SPI modules...", "Loading configuration..."</p>
     *
     * <p><b>Equivalente en otros CLIs:</b> "Hatching" en Claude.
     * Usamos terminologia de ingenieria en lugar de biologia.</p>
     *
     * @since 1.0.0
     */
    INITIALIZING,

    /**
     * Compactando contexto o tokens (Memory Management).
     * <p>Icono: &#x1F5DC; (prensa/compresor) | Color: Blue</p>
     * <p>Ejemplo: "Compacting context (-2k tks)...", "Optimizing memory..."</p>
     *
     * <p><b>Equivalente en otros CLIs:</b> "Pruning" en Claude.
     * Usamos terminologia de GC y bases de datos.</p>
     *
     * @since 1.0.0
     */
    COMPACTING,

    /**
     * Rate limit activo, esperando para reintentar (API Backoff).
     * <p>Icono: &#x1F6A6; (semaforo) | Color: Yellow</p>
     * <p>Ejemplo: "Rate limit. Retrying in 5s...", "API throttled..."</p>
     *
     * <p><b>Equivalente en otros CLIs:</b> "Cooling Down" en Claude.
     * Usamos terminologia de ingenieria de redes.</p>
     *
     * @since 1.0.0
     */
    THROTTLING,

    /**
     * Ejecutando comando de sistema (Tool Execution).
     * <p>Icono: &#x2699; (engranaje) | Color: Cyan</p>
     * <p>Ejemplo: "Running mvn clean install...", "Executing git status..."</p>
     *
     * <p>A diferencia de RUNNING (IA pensando), SYSTEM_OP indica que
     * la IA esta esperando que el SO complete una operacion.</p>
     *
     * @since 1.0.0
     */
    SYSTEM_OP;

    /**
     * Verifica si este estado indica que la tarea esta activa.
     *
     * <p>Los estados activos son aquellos donde hay trabajo en progreso:</p>
     * <ul>
     *   <li>RUNNING: IA procesando</li>
     *   <li>SYSTEM_OP: Ejecutando comando de sistema</li>
     *   <li>INITIALIZING: Arrancando entorno</li>
     *   <li>COMPACTING: Compactando memoria</li>
     * </ul>
     *
     * @return true si la tarea esta en progreso
     */
    public boolean isActive() {
        return this == RUNNING || this == SYSTEM_OP ||
               this == INITIALIZING || this == COMPACTING;
    }

    /**
     * Verifica si este estado indica que la tarea esta esperando.
     *
     * <p>Estado de espera pasiva (no hay trabajo activo):</p>
     * <ul>
     *   <li>THROTTLING: Esperando rate limit</li>
     * </ul>
     *
     * @return true si es THROTTLING
     * @since 1.0.0
     */
    public boolean isWaiting() {
        return this == THROTTLING;
    }

    /**
     * Verifica si este estado indica mantenimiento del sistema.
     *
     * <p>Estados de mantenimiento interno:</p>
     * <ul>
     *   <li>INITIALIZING: Bootstrap/arranque</li>
     *   <li>COMPACTING: Gestion de memoria</li>
     * </ul>
     *
     * @return true si es INITIALIZING o COMPACTING
     * @since 1.0.0
     */
    public boolean isMaintenance() {
        return this == INITIALIZING || this == COMPACTING;
    }

    /**
     * Verifica si este estado indica que la tarea termino.
     *
     * @return true si es SUCCESS, WARNING, FAILURE, SKIPPED o CANCELED
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == WARNING || this == FAILURE ||
               this == SKIPPED || this == CANCELED;
    }

    /**
     * Verifica si este estado indica exito (parcial o total).
     *
     * @return true si es SUCCESS o WARNING
     */
    public boolean isSuccessful() {
        return this == SUCCESS || this == WARNING;
    }

    /**
     * Verifica si este estado indica un problema.
     *
     * @return true si es FAILURE o CANCELED
     */
    public boolean isProblematic() {
        return this == FAILURE || this == CANCELED;
    }
}
