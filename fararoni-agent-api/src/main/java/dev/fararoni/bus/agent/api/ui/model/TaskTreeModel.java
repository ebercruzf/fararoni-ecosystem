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

/**
 * Modelo completo del arbol de ejecucion para el Live Progress Tree.
 *
 * <p>Este record contiene toda la informacion necesaria para que el Core
 * renderice el arbol de progreso en tiempo real usando JLine Display.</p>
 *
 * <h2>Proposito Arquitectonico</h2>
 * <p>Este modelo es el contrato entre Enterprise y Core:</p>
 * <ul>
 *   <li><b>Enterprise:</b> Construye y actualiza este modelo durante la ejecucion</li>
 *   <li><b>Core:</b> Lo recibe via {@code context.getUI().updateProgress(model)}</li>
 * </ul>
 *
 * <h2>Ejemplo de Renderizado</h2>
 * <pre>
 * &#x280B; Actualizando checklist... (2m 24s &#x00B7; 5.4k tokens)
 * &#x251C;&#x2500; &#x2714; Leer archivos Java
 * &#x251C;&#x2500; &#x21B7; Leer archivos Python (Skipped)
 * &#x251C;&#x2500; &#x26A0; Compilar (Warning: APIs deprecadas)
 * &#x2514;&#x2500; &#x280B; Generando AST...
 * </pre>
 *
 * <h2>Flujo de Uso Tipico</h2>
 * <pre>{@code
 * // 1. Crear modelo inicial
 * var model = TaskTreeModel.pending("Iniciando analisis...");
 * context.getUI().updateProgress(model);
 *
 * // 2. Agregar tareas
 * var nodes = List.of(
 *     TaskNode.running("1", "Leyendo archivos"),
 *     TaskNode.pending("2", "Procesando")
 * );
 * model = TaskTreeModel.of("Analizando proyecto", TaskState.RUNNING,
 *                          "10s · 1.2k tokens", nodes);
 * context.getUI().updateProgress(model);
 *
 * // 3. Actualizar conforme progresan
 * // ... (actualizar estados de nodos)
 *
 * // 4. Finalizar
 * context.getUI().onProcessComplete("Analisis completado");
 * }</pre>
 *
 * <h2>Compactacion</h2>
 * <p>El flag {@code compacted} indica si el Core debe colapsar el arbol
 * a una sola linea de resumen al finalizar, liberando espacio visual.</p>
 *
 * @param rootStatus Mensaje principal del header (ej: "Actualizando checklist...")
 * @param rootState  Estado general de la operacion (determina icono del header)
 * @param metaInfo   Metadatos como tiempo y tokens (ej: "2m 24s . 5.4k tokens")
 * @param nodes      Lista de tareas de primer nivel
 * @param compacted  Si true, el renderer debe colapsar a linea simple al finalizar
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see TaskNode
 * @see TaskState
 * @see AgentUserInterface#updateProgress(TaskTreeModel)
 */
public record TaskTreeModel(
    String rootStatus,
    TaskState rootState,
    String metaInfo,
    List<TaskNode> nodes,
    boolean compacted
) {

    /**
     * Constructor compacto con validaciones y normalizacion.
     */
    public TaskTreeModel {
        // Normalizar nulls a valores por defecto
        if (rootStatus == null) rootStatus = "";
        if (rootState == null) rootState = TaskState.PENDING;
        if (metaInfo == null) metaInfo = "";
        if (nodes == null) nodes = List.of();
    }

    // ════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Crea un modelo simple sin compactacion.
     *
     * <p>Atajo para el caso comun de crear un modelo activo.</p>
     *
     * @param status mensaje principal
     * @param state  estado general
     * @param meta   metadatos (tiempo, tokens, etc.)
     * @param nodes  lista de tareas
     * @return nuevo modelo sin compactacion
     */
    public static TaskTreeModel of(String status, TaskState state, String meta, List<TaskNode> nodes) {
        return new TaskTreeModel(status, state, meta, nodes, false);
    }

    /**
     * Crea un modelo en estado inicial (PENDING).
     *
     * <p>Util para mostrar el estado antes de comenzar la ejecucion.</p>
     *
     * @param status mensaje inicial
     * @return nuevo modelo en estado PENDING sin tareas
     */
    public static TaskTreeModel pending(String status) {
        return new TaskTreeModel(status, TaskState.PENDING, "", List.of(), false);
    }

    /**
     * Crea un modelo en ejecucion (RUNNING).
     *
     * @param status mensaje de estado
     * @param meta   metadatos
     * @param nodes  lista de tareas
     * @return nuevo modelo en estado RUNNING
     */
    public static TaskTreeModel running(String status, String meta, List<TaskNode> nodes) {
        return new TaskTreeModel(status, TaskState.RUNNING, meta, nodes, false);
    }

    /**
     * Crea un modelo completado (SUCCESS).
     *
     * @param status mensaje de exito
     * @param meta   metadatos finales
     * @param nodes  lista de tareas completadas
     * @return nuevo modelo en estado SUCCESS
     */
    public static TaskTreeModel success(String status, String meta, List<TaskNode> nodes) {
        return new TaskTreeModel(status, TaskState.SUCCESS, meta, nodes, false);
    }

    /**
     * Crea un modelo fallido (FAILURE).
     *
     * @param status mensaje de error
     * @param meta   metadatos
     * @param nodes  lista de tareas (algunas posiblemente fallidas)
     * @return nuevo modelo en estado FAILURE
     */
    public static TaskTreeModel failure(String status, String meta, List<TaskNode> nodes) {
        return new TaskTreeModel(status, TaskState.FAILURE, meta, nodes, false);
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Verifica si el arbol tiene tareas.
     *
     * @return true si nodes no esta vacio
     */
    public boolean hasNodes() {
        return !nodes.isEmpty();
    }

    /**
     * Verifica si el arbol esta vacio (sin tareas).
     *
     * @return true si nodes esta vacio
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Cuenta el numero de tareas de primer nivel.
     *
     * @return numero de nodos en la lista principal
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Cuenta el numero total de nodos (incluyendo anidados).
     *
     * @return numero total de nodos en todo el arbol
     */
    public int totalNodeCount() {
        int count = 0;
        for (TaskNode node : nodes) {
            count += node.totalCount();
        }
        return count;
    }

    /**
     * Verifica si la operacion esta activa (RUNNING).
     *
     * @return true si rootState es RUNNING
     */
    public boolean isActive() {
        return rootState == TaskState.RUNNING;
    }

    /**
     * Verifica si la operacion termino.
     *
     * @return true si rootState es terminal (SUCCESS, FAILURE, etc.)
     */
    public boolean isComplete() {
        return rootState.isTerminal();
    }

    // ════════════════════════════════════════════════════════════════════════
    // BUILDER-STYLE METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Crea una copia con nuevo estado.
     *
     * @param newState nuevo estado general
     * @return nuevo modelo con el estado actualizado
     */
    public TaskTreeModel withState(TaskState newState) {
        return new TaskTreeModel(rootStatus, newState, metaInfo, nodes, compacted);
    }

    /**
     * Crea una copia con nuevo mensaje.
     *
     * @param newStatus nuevo mensaje principal
     * @return nuevo modelo con el mensaje actualizado
     */
    public TaskTreeModel withStatus(String newStatus) {
        return new TaskTreeModel(newStatus, rootState, metaInfo, nodes, compacted);
    }

    /**
     * Crea una copia con nuevos metadatos.
     *
     * @param newMeta nuevos metadatos
     * @return nuevo modelo con los metadatos actualizados
     */
    public TaskTreeModel withMeta(String newMeta) {
        return new TaskTreeModel(rootStatus, rootState, newMeta, nodes, compacted);
    }

    /**
     * Crea una copia con nuevas tareas.
     *
     * @param newNodes nueva lista de tareas
     * @return nuevo modelo con las tareas actualizadas
     */
    public TaskTreeModel withNodes(List<TaskNode> newNodes) {
        return new TaskTreeModel(rootStatus, rootState, metaInfo, newNodes, compacted);
    }

    /**
     * Crea una copia marcada para compactacion.
     *
     * @return nuevo modelo con compacted=true
     */
    public TaskTreeModel asCompacted() {
        return new TaskTreeModel(rootStatus, rootState, metaInfo, nodes, true);
    }
}
