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
package dev.fararoni.bus.agent.api.protocol;

import java.util.Map;

/**
 * Mensaje estructurado para comunicacion entre agentes.
 *
 * <p>Este record define el "lenguaje comun" que hablan los agentes entre si.
 * Va DENTRO del payload del {@code SovereignEnvelope}.</p>
 *
 * <h2>Tipos de Mensaje Estandar</h2>
 * <ul>
 *   <li>{@code TASK_ASSIGNMENT} - Asignacion de tarea</li>
 *   <li>{@code CODE_REVIEW_REQ} - Solicitud de revision de codigo</li>
 *   <li>{@code KNOWLEDGE_QUERY} - Consulta de conocimiento</li>
 *   <li>{@code STATUS_UPDATE} - Actualizacion de estado</li>
 *   <li>{@code FATAL_ERROR} - Error critico</li>
 *   <li>{@code ACK} - Confirmacion de recepcion</li>
 *   <li>{@code NACK} - Rechazo/Error</li>
 * </ul>
 *
 * <h2>Uso Tipico</h2>
 * <pre>{@code
 * // El Supervisor asigna una tarea
 * var message = AgentMessage.taskAssignment(
 *     "SUPERVISOR",
 *     Map.of("priority", "HIGH", "ticket", "JIRA-123"),
 *     "Implementa la funcion de login"
 * );
 *
 * // Enviar via Bus
 * var envelope = SovereignEnvelope.create("user-123", "SUPERVISOR", null, message);
 * bus.publish("agent.dev.007.inbox", envelope);
 * }</pre>
 *
 * @param type                Tipo de mensaje (ej: "TASK_ASSIGNMENT")
 * @param senderRole          Rol del agente emisor (ej: "SUPERVISOR")
 * @param content             Datos estructurados (metadatos del prompt)
 * @param naturalLanguageHint Descripcion en lenguaje natural (opcional)
 * @author Fararoni Framework
 * @since 1.0.0
 */
public record AgentMessage(
    String type,
    String senderRole,
    Map<String, Object> content,
    String naturalLanguageHint
) {

    // =========================================================================
    // TIPOS DE MENSAJE ESTANDAR
    // =========================================================================

    public static final String TYPE_TASK_ASSIGNMENT = "TASK_ASSIGNMENT";
    public static final String TYPE_CODE_REVIEW_REQ = "CODE_REVIEW_REQ";
    public static final String TYPE_KNOWLEDGE_QUERY = "KNOWLEDGE_QUERY";
    public static final String TYPE_STATUS_UPDATE = "STATUS_UPDATE";
    public static final String TYPE_FATAL_ERROR = "FATAL_ERROR";
    public static final String TYPE_ACK = "ACK";
    public static final String TYPE_NACK = "NACK";

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    /**
     * Crea un mensaje de asignacion de tarea.
     *
     * @param senderRole Rol del emisor
     * @param metadata   Metadatos de la tarea (priority, ticket, etc.)
     * @param hint       Descripcion de la tarea
     * @return AgentMessage configurado
     */
    public static AgentMessage taskAssignment(
            String senderRole,
            Map<String, Object> metadata,
            String hint) {
        return new AgentMessage(TYPE_TASK_ASSIGNMENT, senderRole, metadata, hint);
    }

    /**
     * Crea una solicitud de revision de codigo.
     *
     * @param senderRole Rol del emisor
     * @param codePath   Ruta del codigo a revisar
     * @param hint       Instrucciones adicionales
     * @return AgentMessage configurado
     */
    public static AgentMessage codeReviewRequest(
            String senderRole,
            String codePath,
            String hint) {
        return new AgentMessage(
            TYPE_CODE_REVIEW_REQ,
            senderRole,
            Map.of("code_path", codePath),
            hint
        );
    }

    /**
     * Crea una consulta de conocimiento (broadcast).
     *
     * @param senderRole Rol del emisor
     * @param topic      Tema de la consulta
     * @return AgentMessage configurado
     */
    public static AgentMessage knowledgeQuery(String senderRole, String topic) {
        return new AgentMessage(
            TYPE_KNOWLEDGE_QUERY,
            senderRole,
            Map.of("topic", topic),
            topic
        );
    }

    /**
     * Crea una actualizacion de estado.
     *
     * @param senderRole Rol del emisor
     * @param status     Estado actual (ej: "IN_PROGRESS", "COMPLETED")
     * @param message    Mensaje descriptivo
     * @return AgentMessage configurado
     */
    public static AgentMessage statusUpdate(
            String senderRole,
            String status,
            String message) {
        return new AgentMessage(
            TYPE_STATUS_UPDATE,
            senderRole,
            Map.of("status", status),
            message
        );
    }

    /**
     * Crea un reporte de error fatal.
     *
     * @param senderRole    Rol del emisor
     * @param errorCode     Codigo de error
     * @param errorMessage  Mensaje de error
     * @return AgentMessage configurado
     */
    public static AgentMessage fatalError(
            String senderRole,
            String errorCode,
            String errorMessage) {
        return new AgentMessage(
            TYPE_FATAL_ERROR,
            senderRole,
            Map.of("error_code", errorCode, "error_message", errorMessage),
            errorMessage
        );
    }

    /**
     * Crea una confirmacion de recepcion (ACK).
     *
     * @param senderRole      Rol del emisor
     * @param originalMsgId   ID del mensaje original
     * @return AgentMessage configurado
     */
    public static AgentMessage ack(String senderRole, String originalMsgId) {
        return new AgentMessage(
            TYPE_ACK,
            senderRole,
            Map.of("original_msg_id", originalMsgId),
            null
        );
    }

    /**
     * Crea un rechazo/error (NACK).
     *
     * @param senderRole    Rol del emisor
     * @param originalMsgId ID del mensaje original
     * @param reason        Razon del rechazo
     * @return AgentMessage configurado
     */
    public static AgentMessage nack(
            String senderRole,
            String originalMsgId,
            String reason) {
        return new AgentMessage(
            TYPE_NACK,
            senderRole,
            Map.of("original_msg_id", originalMsgId, "reason", reason),
            reason
        );
    }

    // =========================================================================
    // METODOS DE CONSULTA
    // =========================================================================

    /**
     * Verifica si es un mensaje de asignacion de tarea.
     *
     * @return true si type == TASK_ASSIGNMENT
     */
    public boolean isTaskAssignment() {
        return TYPE_TASK_ASSIGNMENT.equals(type);
    }

    /**
     * Verifica si es una confirmacion.
     *
     * @return true si type == ACK
     */
    public boolean isAck() {
        return TYPE_ACK.equals(type);
    }

    /**
     * Verifica si es un rechazo.
     *
     * @return true si type == NACK
     */
    public boolean isNack() {
        return TYPE_NACK.equals(type);
    }

    /**
     * Verifica si es un error fatal.
     *
     * @return true si type == FATAL_ERROR
     */
    public boolean isFatalError() {
        return TYPE_FATAL_ERROR.equals(type);
    }

    /**
     * Obtiene un valor del contenido.
     *
     * @param key Clave del valor
     * @param <V> Tipo del valor
     * @return Valor o null si no existe
     */
    @SuppressWarnings("unchecked")
    public <V> V getContent(String key) {
        return content != null ? (V) content.get(key) : null;
    }

    /**
     * Obtiene un valor del contenido con valor por defecto.
     *
     * @param key          Clave del valor
     * @param defaultValue Valor por defecto
     * @param <V>          Tipo del valor
     * @return Valor o defaultValue si no existe
     */
    @SuppressWarnings("unchecked")
    public <V> V getContent(String key, V defaultValue) {
        if (content == null) return defaultValue;
        V value = (V) content.get(key);
        return value != null ? value : defaultValue;
    }
}
