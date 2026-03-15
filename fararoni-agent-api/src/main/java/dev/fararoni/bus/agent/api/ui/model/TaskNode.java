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

import java.util.List;
import java.util.Objects;

/**
 * Nodo del arbol de ejecucion para el Live Progress Tree.
 *
 * <p>Representa una tarea individual con posibles sub-tareas (hijos).
 * La estructura recursiva permite arboles de cualquier profundidad.</p>
 *
 * <h2>Proposito Arquitectonico</h2>
 * <p>Este record forma parte del patron Render Server:</p>
 * <ul>
 *   <li><b>Enterprise:</b> Construye el arbol de nodos con la logica de negocio</li>
 *   <li><b>Core:</b> Renderiza el arbol visualmente con JLine</li>
 * </ul>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // Crear nodos hoja (sin hijos)
 * var child1 = TaskNode.leaf("1.1", "Leer archivo A", TaskState.SUCCESS);
 * var child2 = TaskNode.leaf("1.2", "Leer archivo B", TaskState.RUNNING);
 *
 * // Crear nodo padre con hijos
 * var parent = new TaskNode("1", "Leer archivos", TaskState.RUNNING,
 *                           List.of(child1, child2));
 *
 * // Arbol completo
 * var root = new TaskNode("0", "Analizar proyecto", TaskState.RUNNING,
 *                         List.of(parent, otroNodo));
 * }</pre>
 *
 * <h2>Renderizado Visual</h2>
 * <pre>
 * &#x251C;&#x2500; &#x2714; Leer archivos
 * &#x2502;   &#x251C;&#x2500; &#x2714; Leer archivo A
 * &#x2502;   &#x2514;&#x2500; &#x280B; Leer archivo B
 * &#x2514;&#x2500; &#x25E6; Procesar datos
 * </pre>
 *
 * <h2>Inmutabilidad</h2>
 * <p>Este record es inmutable. Para actualizar el estado de un nodo,
 * crea una nueva instancia con el nuevo estado.</p>
 *
 * @param id       Identificador unico del nodo (para tracking interno)
 * @param label    Texto descriptivo de la tarea (mostrado al usuario)
 * @param state    Estado actual de la tarea (determina icono y color)
 * @param children Lista de sub-tareas (puede ser vacia, nunca null)
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see TaskState
 * @see TaskTreeModel
 */
public record TaskNode(
    String id,
    String label,
    TaskState state,
    List<TaskNode> children
) {

    /**
     * Constructor compacto que valida y normaliza parametros.
     *
     * @throws NullPointerException si id, label o state son null
     */
    public TaskNode {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(label, "label no puede ser null");
        Objects.requireNonNull(state, "state no puede ser null");

        // Normalizar children a lista vacia si es null
        if (children == null) {
            children = List.of();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Crea un nodo hoja (sin hijos).
     *
     * <p>Atajo para crear nodos simples sin sub-tareas.</p>
     *
     * @param id    identificador unico
     * @param label texto descriptivo
     * @param state estado actual
     * @return nuevo nodo sin hijos
     */
    public static TaskNode leaf(String id, String label, TaskState state) {
        return new TaskNode(id, label, state, List.of());
    }

    /**
     * Crea un nodo pendiente (estado PENDING).
     *
     * @param id    identificador unico
     * @param label texto descriptivo
     * @return nuevo nodo en estado PENDING sin hijos
     */
    public static TaskNode pending(String id, String label) {
        return new TaskNode(id, label, TaskState.PENDING, List.of());
    }

    /**
     * Crea un nodo en ejecucion (estado RUNNING).
     *
     * @param id    identificador unico
     * @param label texto descriptivo
     * @return nuevo nodo en estado RUNNING sin hijos
     */
    public static TaskNode running(String id, String label) {
        return new TaskNode(id, label, TaskState.RUNNING, List.of());
    }

    /**
     * Crea un nodo completado (estado SUCCESS).
     *
     * @param id    identificador unico
     * @param label texto descriptivo
     * @return nuevo nodo en estado SUCCESS sin hijos
     */
    public static TaskNode success(String id, String label) {
        return new TaskNode(id, label, TaskState.SUCCESS, List.of());
    }

    /**
     * Crea un nodo fallido (estado FAILURE).
     *
     * @param id    identificador unico
     * @param label texto descriptivo
     * @return nuevo nodo en estado FAILURE sin hijos
     */
    public static TaskNode failure(String id, String label) {
        return new TaskNode(id, label, TaskState.FAILURE, List.of());
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Verifica si este nodo tiene sub-tareas.
     *
     * @return true si children no esta vacio
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Verifica si este nodo es una hoja (sin hijos).
     *
     * @return true si children esta vacio
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /**
     * Cuenta el numero total de nodos en este subarbol.
     *
     * <p>Incluye este nodo mas todos sus descendientes.</p>
     *
     * @return numero total de nodos
     */
    public int totalCount() {
        int count = 1; // Este nodo
        for (TaskNode child : children) {
            count += child.totalCount();
        }
        return count;
    }

    /**
     * Crea una copia de este nodo con un nuevo estado.
     *
     * @param newState nuevo estado para el nodo
     * @return nuevo nodo con el estado actualizado
     */
    public TaskNode withState(TaskState newState) {
        return new TaskNode(id, label, newState, children);
    }

    /**
     * Crea una copia de este nodo con nuevos hijos.
     *
     * @param newChildren nueva lista de hijos
     * @return nuevo nodo con los hijos actualizados
     */
    public TaskNode withChildren(List<TaskNode> newChildren) {
        return new TaskNode(id, label, state, newChildren);
    }

    /**
     * Crea una copia de este nodo con un nuevo label.
     *
     * @param newLabel nuevo texto descriptivo
     * @return nuevo nodo con el label actualizado
     */
    public TaskNode withLabel(String newLabel) {
        return new TaskNode(id, newLabel, state, children);
    }
}
