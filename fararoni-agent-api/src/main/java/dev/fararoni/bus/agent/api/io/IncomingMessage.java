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
package dev.fararoni.bus.agent.api.io;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mensaje entrante agnostico del proveedor.
 *
 * <p>Este record representa un mensaje recibido de cualquier fuente externa
 * (email, Jira, Slack, webhook) en un formato normalizado que el agente
 * puede procesar sin conocer el origen.</p>
 *
 * <h2>Principio de Diseno: DTO Agnostico</h2>
 * <p>Los campos son genericos y no contienen tipos especificos de ningun
 * proveedor. Esto permite:</p>
 * <ul>
 *   <li>Procesar mensajes de diferentes fuentes con el mismo codigo</li>
 *   <li>Agregar nuevas fuentes sin modificar el procesador</li>
 *   <li>Testing facil con mensajes mock</li>
 * </ul>
 *
 * <h2>Campos Principales</h2>
 * <ul>
 *   <li><b>channelType:</b> Tipo de canal (email, slack, jira, webhook)</li>
 *   <li><b>channelName:</b> Nombre especifico del canal (imap-soporte, slack-alerts)</li>
 *   <li><b>sourceId:</b> Identificador unico del remitente</li>
 *   <li><b>content:</b> Contenido textual del mensaje</li>
 *   <li><b>metadata:</b> Datos adicionales especificos del canal</li>
 * </ul>
 *
 * <h2>Ejemplos por Fuente</h2>
 *
 * <h3>Email (IMAP)</h3>
 * <pre>{@code
 * new IncomingMessage(
 *     "email",
 *     "imap-soporte",
 *     "cliente@empresa.com",
 *     "Urgente: El sistema no funciona...",
 *     Map.of(
 *         "subject", "Problema critico",
 *         "from", "cliente@empresa.com",
 *         "to", "soporte@fararoni.dev",
 *         "messageId", "<abc123@mail.com>",
 *         "hasAttachments", "true"
 *     ),
 *     Instant.now()
 * )
 * }</pre>
 *
 * <h3>Jira Webhook</h3>
 * <pre>{@code
 * new IncomingMessage(
 *     "jira",
 *     "jira-webhook",
 *     "PROJ-123",
 *     "Descripcion del ticket...",
 *     Map.of(
 *         "issueKey", "PROJ-123",
 *         "issueType", "Bug",
 *         "priority", "High",
 *         "reporter", "usuario@empresa.com",
 *         "webhookEvent", "jira:issue_created"
 *     ),
 *     Instant.now()
 * )
 * }</pre>
 *
 * <h3>Slack</h3>
 * <pre>{@code
 * new IncomingMessage(
 *     "slack",
 *     "slack-alertas",
 *     "U1234567890",
 *     "@fararoni ayudame con este error...",
 *     Map.of(
 *         "channel", "C1234567890",
 *         "channelName", "#soporte",
 *         "userName", "juan.perez",
 *         "threadTs", "1234567890.123456"
 *     ),
 *     Instant.now()
 * )
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>Este record es inmutable y thread-safe. El Map de metadata debe ser
 * inmutable (usar Map.of() o Collections.unmodifiableMap()).</p>
 *
 * @param channelType tipo del canal (email, slack, jira, webhook, etc.)
 * @param channelName nombre especifico del canal configurado
 * @param sourceId identificador unico del remitente (email, user ID, ticket ID)
 * @param content contenido textual principal del mensaje
 * @param metadata datos adicionales especificos del canal (headers, attachments info, etc.)
 * @param receivedAt timestamp de cuando se recibio el mensaje
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see IngestionChannel
 */
public record IncomingMessage(
    String channelType,
    String channelName,
    String sourceId,
    String content,
    Map<String, String> metadata,
    Instant receivedAt
) {

    /**
     * Constructor con validacion.
     *
     * @throws NullPointerException si channelType, sourceId o content son null
     */
    public IncomingMessage {
        Objects.requireNonNull(channelType, "channelType must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(content, "content must not be null");

        // Asegurar que metadata es inmutable
        metadata = metadata != null
            ? Collections.unmodifiableMap(metadata)
            : Collections.emptyMap();

        // Default para receivedAt
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }

        // Default para channelName
        if (channelName == null || channelName.isBlank()) {
            channelName = channelType;
        }
    }

    /**
     * Constructor simplificado sin metadata.
     *
     * @param channelType tipo del canal
     * @param sourceId identificador del remitente
     * @param content contenido del mensaje
     */
    public IncomingMessage(String channelType, String sourceId, String content) {
        this(channelType, channelType, sourceId, content, Map.of(), Instant.now());
    }

    /**
     * Obtiene un valor de metadata de forma segura.
     *
     * @param key clave a buscar
     * @return Optional con el valor si existe
     */
    public Optional<String> getMetadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }

    /**
     * Obtiene un valor de metadata con valor por defecto.
     *
     * @param key clave a buscar
     * @param defaultValue valor por defecto si no existe
     * @return el valor encontrado o el default
     */
    public String getMetadataOrDefault(String key, String defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    /**
     * Verifica si el mensaje tiene una clave de metadata.
     *
     * @param key clave a verificar
     * @return true si la clave existe
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    /**
     * Verifica si el mensaje es de tipo email.
     *
     * @return true si channelType es "email"
     */
    public boolean isEmail() {
        return "email".equalsIgnoreCase(channelType);
    }

    /**
     * Verifica si el mensaje es de Slack.
     *
     * @return true si channelType es "slack"
     */
    public boolean isSlack() {
        return "slack".equalsIgnoreCase(channelType);
    }

    /**
     * Verifica si el mensaje es de Jira.
     *
     * @return true si channelType es "jira"
     */
    public boolean isJira() {
        return "jira".equalsIgnoreCase(channelType);
    }

    /**
     * Verifica si el mensaje es un webhook generico.
     *
     * @return true si channelType es "webhook"
     */
    public boolean isWebhook() {
        return "webhook".equalsIgnoreCase(channelType);
    }

    /**
     * Retorna una version truncada del contenido para logging.
     *
     * @param maxLength longitud maxima
     * @return contenido truncado con "..." si excede maxLength
     */
    public String contentPreview(int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength - 3) + "...";
    }

    /**
     * Genera un ID unico para este mensaje.
     *
     * <p>Combina channelType, sourceId y timestamp para crear un ID
     * que puede usarse para deduplicacion.</p>
     *
     * @return ID unico del mensaje
     */
    public String uniqueId() {
        return String.format("%s:%s:%d", channelType, sourceId, receivedAt.toEpochMilli());
    }

    /**
     * Retorna representacion para logging (sin contenido completo).
     *
     * @return string formateado para logs
     */
    public String toLogString() {
        return String.format(
            "IncomingMessage[type=%s, channel=%s, source=%s, contentLen=%d, received=%s]",
            channelType, channelName, sourceId, content.length(), receivedAt
        );
    }
}
