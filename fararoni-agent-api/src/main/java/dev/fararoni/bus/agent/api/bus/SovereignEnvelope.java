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
package dev.fararoni.bus.agent.api.bus;

import dev.fararoni.bus.agent.api.security.HmacMessageSigner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * El atomo indivisible de comunicacion en Aegis v2.0.
 *
 * <p>Lleva la carga util (Payload) y todo el contexto de observabilidad/seguridad.
 * Es inmutable por diseno para garantizar thread-safety.</p>
 *
 * <h2>Campos de Infraestructura (Bus)</h2>
 * <ul>
 *   <li>{@code id} - UUID unico del envelope</li>
 *   <li>{@code idempotencyKey} - [V3] Hash SHA-256 para deduplicacion blindada</li>
 *   <li>{@code schemaVersion} - [V3] Version del schema para migraciones</li>
 *   <li>{@code traceId} - ID global de traza (Zipkin/Jaeger)</li>
 *   <li>{@code correlationId} - ID de la mision/conversacion</li>
 *   <li>{@code replyTo} - Topico de respuesta (Request/Reply pattern)</li>
 * </ul>
 *
 * <h2>Campos de Seguridad (RBAC + HMAC)</h2>
 * <ul>
 *   <li>{@code userId} - Identidad para control de acceso</li>
 *   <li>{@code senderRole} - Rol del agente emisor</li>
 *   <li>{@code signature} - Firma HMAC-SHA256 del payload</li>
 * </ul>
 *
 * <h2>Campos de Ciclo de Vida (Resiliencia + 40.1)</h2>
 * <ul>
 *   <li>{@code ttlMs} - Time-To-Live en milisegundos (evita mensajes zombi)</li>
 *   <li>{@code retryCount} - Contador de reintentos (Circuit Breaker)</li>
 *   <li>{@code hopCount} - Contador de saltos entre agentes (anti-bucle, FASE 40.1)</li>
 * </ul>
 *
 * <h2>Campos de Negocio</h2>
 * <ul>
 *   <li>{@code headers} - Metadatos extensibles sin modificar el record</li>
 *   <li>{@code payload} - Objeto de negocio real (generico)</li>
 * </ul>
 *
 * <h2>Integracion con HmacMessageSigner</h2>
 * <pre>
 * // Firmar un envelope
 * String sig = HmacMessageSigner.sign(payload.toString(), timestamp.toEpochMilli());
 * envelope = envelope.withSignature(sig);
 *
 * // Verificar un envelope
 * boolean valid = HmacMessageSigner.verify(
 *     envelope.payload().toString(),
 *     envelope.timestamp().toEpochMilli(),
 *     envelope.signature()
 * );
 * </pre>
 *
 * @param <T> Tipo del payload (generico para flexibilidad)
 * @author Fararoni Framework
 * @since 1.0.0
 * @see HmacMessageSigner
 */
public record SovereignEnvelope<T>(
    // 1. IDENTIDAD E IDEMPOTENCIA
    String id,
    String idempotencyKey,  // [FASE 55.3] Hash SHA-256 para deduplicacion blindada
    String schemaVersion,   // [FASE 55.3] Version del schema (migraciones futuras)

    // 2. TRAZABILIDAD (Observabilidad Distribuida)
    String traceId,
    String correlationId,
    String replyTo,
    String senderRole,      // [FASE 30.1.2] Rol del agente emisor

    // 3. SEGURIDAD (RBAC + HMAC)
    String userId,
    String signature,       // [FASE 40.1] Firma HMAC-SHA256 (nullable para compatibilidad)

    // 4. CICLO DE VIDA (Resiliencia + 40.1)
    Instant timestamp,
    long ttlMs,             // [FASE 30.1.2] Time-To-Live (default 30s)
    int retryCount,         // [FASE 30.1.2] Contador de reintentos
    int hopCount,           // [FASE 40.1] Contador de saltos anti-bucle

    // 5. METADATOS Y CARGA UTIL
    Map<String, String> headers,
    T payload
) {

    /** TTL por defecto: 30 segundos (Fail Fast) */
    public static final long DEFAULT_TTL_MS = 30_000;

    /** Maximo de reintentos antes de DLQ */
    public static final int MAX_RETRY_COUNT = 3;

    /** [FASE 40.1] Maximo de saltos entre agentes antes de matar el mensaje */
    public static final int MAX_HOP_COUNT = 50;

    /**
     * [FASE 55.3] Version actual del schema del envelope.
     *
     * <p>Usado para migraciones futuras. Cuando se deserializa un envelope
     * con version anterior, se puede aplicar logica de upgrade.</p>
     */
    public static final String CURRENT_SCHEMA_VERSION = "3.0";

    /**
     * [FASE 55.3] Prefijo para claves de idempotencia externa (webhooks).
     *
     * <p>Cuando un mensaje viene de un sistema externo (WhatsApp message_id,
     * Stripe event_id), usamos su ID como clave de idempotencia con este prefijo.</p>
     */
    public static final String EXTERNAL_KEY_PREFIX = "EXT:";

    // =========================================================================
    // [FASE 55.3] MÉTODOS DE IDEMPOTENCIA BLINDADA
    // =========================================================================

    /**
     * [FASE 55.3] Genera clave de idempotencia SHA-256 del payload + topic.
     *
     * <p><b>Algoritmo:</b></p>
     * <ol>
     *   <li>Si hay externalKey (webhook), usar "EXT:" + externalKey</li>
     *   <li>Si no, serializar payload con ORDER_MAP_ENTRIES_BY_KEYS</li>
     *   <li>Concatenar: topic + "|" + serializedPayload</li>
     *   <li>Calcular SHA-256 del string concatenado</li>
     *   <li>Codificar en hexadecimal</li>
     * </ol>
     *
     * <p><b>Por qué SHA-256 y no hashCode():</b></p>
     * <ul>
     *   <li>hashCode() tiene colisiones frecuentes (32 bits)</li>
     *   <li>hashCode() no es consistente entre JVMs</li>
     *   <li>SHA-256 tiene 2^256 espacio = colisiones despreciables</li>
     * </ul>
     *
     * @param topic       Topico de destino (parte del hash)
     * @param payload     Payload a hashear
     * @param externalKey Clave externa opcional (webhook message_id)
     * @return Clave de idempotencia (EXT:xxx o SHA-256 hex)
     */
    public static String generateIdempotencyKey(String topic, Object payload, String externalKey) {
        // Prioridad 1: Clave externa (webhooks - sobrevive retries)
        if (externalKey != null && !externalKey.isBlank()) {
            return EXTERNAL_KEY_PREFIX + externalKey;
        }

        // Prioridad 2: Hash SHA-256 del contenido serializado
        try {
            String serialized = serializePayloadDeterministic(payload);
            String data = topic + "|" + serialized;
            return hashSHA256(data);
        } catch (Exception e) {
            // Fallback seguro: UUID (sin garantía de idempotencia)
            return "FALLBACK:" + UUID.randomUUID().toString();
        }
    }

    /**
     * [FASE 55.3] Serializa payload de forma determinística.
     *
     * <p>Las claves del Map se ordenan alfabéticamente usando TreeMap
     * para garantizar que el mismo objeto siempre produce el mismo string.</p>
     *
     * <p><b>Tipos soportados:</b></p>
     * <ul>
     *   <li>null → "null"</li>
     *   <li>String → el string tal cual</li>
     *   <li>Map → claves ordenadas recursivamente</li>
     *   <li>Otros → toString()</li>
     * </ul>
     *
     * @param payload Objeto a serializar
     * @return String determinístico del payload
     */
    @SuppressWarnings("unchecked")
    private static String serializePayloadDeterministic(Object payload) {
        if (payload == null) {
            return "null";
        }
        if (payload instanceof String s) {
            return s;
        }
        if (payload instanceof Map<?, ?> map) {
            // TreeMap ordena las claves alfabéticamente
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                // Recursivo para Maps anidados
                if (value instanceof Map) {
                    sorted.put(key, serializePayloadDeterministic(value));
                } else {
                    sorted.put(key, value);
                }
            }
            return sorted.toString();
        }
        // Fallback: toString() - suficientemente determinístico para la mayoría de casos
        return payload.toString();
    }

    /**
     * [FASE 55.3] Calcula hash SHA-256 de un string.
     *
     * @param data String a hashear
     * @return Hash en formato hexadecimal (64 caracteres)
     */
    private static String hashSHA256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en Java
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Crea un envelope con valores por defecto.
     *
     * @param userId  Usuario que origina la peticion
     * @param payload Carga util del mensaje
     * @param <T>     Tipo del payload
     * @return Nuevo SovereignEnvelope configurado
     */
    public static <T> SovereignEnvelope<T> create(String userId, T payload) {
        return create(userId, null, null, payload);
    }

    /**
     * Crea un envelope con traceId especifico.
     *
     * @param userId  Usuario que origina la peticion
     * @param traceId ID de traza existente (o null para generar nuevo)
     * @param payload Carga util del mensaje
     * @param <T>     Tipo del payload
     * @return Nuevo SovereignEnvelope configurado
     */
    public static <T> SovereignEnvelope<T> create(String userId, String traceId, T payload) {
        return create(userId, null, traceId, payload);
    }

    /**
     * Crea un envelope completo con senderRole.
     *
     * <p>[FASE 55.3] La idempotencyKey se genera automáticamente pero es
     * temporal (basada en timestamp). Usar {@link #withFinalIdempotencyKey}
     * cuando se conozca el topic de destino para idempotencia real.</p>
     *
     * @param userId     Usuario que origina la peticion
     * @param senderRole Rol del agente emisor (ej: "ARCHITECT", "DEVELOPER")
     * @param traceId    ID de traza existente (o null para generar nuevo)
     * @param payload    Carga util del mensaje
     * @param <T>        Tipo del payload
     * @return Nuevo SovereignEnvelope configurado
     */
    public static <T> SovereignEnvelope<T> create(
            String userId,
            String senderRole,
            String traceId,
            T payload) {
        String tempKey = "TEMP:" + UUID.randomUUID().toString();
        return new SovereignEnvelope<>(
            UUID.randomUUID().toString(),
            tempKey,              // [FASE 55.3] Temporal hasta withFinalIdempotencyKey()
            CURRENT_SCHEMA_VERSION,
            traceId != null ? traceId : UUID.randomUUID().toString(),
            null,       // correlationId
            null,       // replyTo
            senderRole,
            userId,
            null,       // signature [FASE 40.1] - se agrega despues con withSignature()
            Instant.now(),
            DEFAULT_TTL_MS,
            0,          // retryCount
            0,          // hopCount [FASE 40.1]
            new HashMap<>(),
            payload
        );
    }

    /**
     * [FASE 55.3] Crea un envelope seguro con idempotencia completa.
     *
     * <p>Este es el factory recomendado para mensajes de negocio. Genera
     * la idempotencyKey SHA-256 inmediatamente basada en topic + payload.</p>
     *
     * @param userId      Usuario que origina la peticion
     * @param senderRole  Rol del agente emisor
     * @param traceId     ID de traza existente (o null para generar nuevo)
     * @param topic       Topic de destino (incluido en el hash)
     * @param payload     Carga util del mensaje
     * @param <T>         Tipo del payload
     * @return Nuevo SovereignEnvelope con idempotencyKey blindada
     */
    public static <T> SovereignEnvelope<T> createSecure(
            String userId,
            String senderRole,
            String traceId,
            String topic,
            T payload) {
        return createSecure(userId, senderRole, traceId, topic, payload, null);
    }

    /**
     * [FASE 55.3] Crea un envelope seguro con clave externa (webhooks).
     *
     * <p>Usar este factory cuando el mensaje viene de un sistema externo
     * que ya proporciona un ID único (WhatsApp message_id, Stripe event_id).
     * La clave externa tiene prioridad sobre el hash SHA-256.</p>
     *
     * @param userId      Usuario que origina la peticion
     * @param senderRole  Rol del agente emisor
     * @param traceId     ID de traza existente (o null para generar nuevo)
     * @param topic       Topic de destino
     * @param payload     Carga util del mensaje
     * @param externalKey Clave externa del webhook (message_id, event_id)
     * @param <T>         Tipo del payload
     * @return Nuevo SovereignEnvelope con idempotencyKey externa
     */
    public static <T> SovereignEnvelope<T> createSecure(
            String userId,
            String senderRole,
            String traceId,
            String topic,
            T payload,
            String externalKey) {
        String idempotencyKey = generateIdempotencyKey(topic, payload, externalKey);
        return new SovereignEnvelope<>(
            UUID.randomUUID().toString(),
            idempotencyKey,
            CURRENT_SCHEMA_VERSION,
            traceId != null ? traceId : UUID.randomUUID().toString(),
            null,       // correlationId
            null,       // replyTo
            senderRole,
            userId,
            null,       // signature
            Instant.now(),
            DEFAULT_TTL_MS,
            0,          // retryCount
            0,          // hopCount
            new HashMap<>(),
            payload
        );
    }

    /**
     * Crea un envelope con TTL personalizado.
     *
     * @param userId     Usuario que origina la peticion
     * @param senderRole Rol del agente emisor
     * @param traceId    ID de traza
     * @param ttlMs      Time-To-Live en milisegundos
     * @param payload    Carga util
     * @param <T>        Tipo del payload
     * @return Nuevo SovereignEnvelope configurado
     */
    public static <T> SovereignEnvelope<T> createWithTtl(
            String userId,
            String senderRole,
            String traceId,
            long ttlMs,
            T payload) {
        String tempKey = "TEMP:" + UUID.randomUUID().toString();
        return new SovereignEnvelope<>(
            UUID.randomUUID().toString(),
            tempKey,
            CURRENT_SCHEMA_VERSION,
            traceId != null ? traceId : UUID.randomUUID().toString(),
            null,
            null,
            senderRole,
            userId,
            null,       // signature [FASE 40.1]
            Instant.now(),
            ttlMs,
            0,          // retryCount
            0,          // hopCount [FASE 40.1]
            new HashMap<>(),
            payload
        );
    }

    /**
     * [FASE 55.3] Genera la idempotencyKey final basada en el topic de destino.
     *
     * <p>El Bus DEBE llamar este método antes de publicar para que la
     * idempotencyKey incluya el topic real de destino.</p>
     *
     * <p><b>Flujo típico:</b></p>
     * <pre>{@code
     * var envelope = SovereignEnvelope.create(userId, payload);
     * // ... modificaciones con with*() ...
     * envelope = envelope.withFinalIdempotencyKey(topic);
     * bus.publish(topic, envelope);
     * }</pre>
     *
     * @param topic Topic de destino para incluir en el hash
     * @return Nueva instancia con idempotencyKey SHA-256 final
     */
    public SovereignEnvelope<T> withFinalIdempotencyKey(String topic) {
        String finalKey = generateIdempotencyKey(topic, this.payload, null);
        return new SovereignEnvelope<>(
            id, finalKey, schemaVersion, traceId, correlationId, replyTo, senderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount, headers, payload
        );
    }

    /**
     * [FASE 55.3] Establece una clave de idempotencia externa.
     *
     * <p>Usar cuando el mensaje viene de un webhook con su propio ID único.</p>
     *
     * @param externalKey Clave externa (WhatsApp message_id, Stripe event_id)
     * @return Nueva instancia con idempotencyKey = "EXT:" + externalKey
     */
    public SovereignEnvelope<T> withExternalIdempotencyKey(String externalKey) {
        String finalKey = EXTERNAL_KEY_PREFIX + externalKey;
        return new SovereignEnvelope<>(
            id, finalKey, schemaVersion, traceId, correlationId, replyTo, senderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount, headers, payload
        );
    }

    /**
     * Crea copia con topico de respuesta (Request/Reply pattern).
     *
     * @param replyTopic Topico donde esperar la respuesta
     * @return Nueva instancia con replyTo configurado
     */
    public SovereignEnvelope<T> withReplyTo(String replyTopic) {
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, correlationId, replyTopic, senderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount, headers, payload
        );
    }

    /**
     * Crea copia con correlationId para vincular mensajes.
     *
     * @param newCorrelationId ID de correlacion
     * @return Nueva instancia con correlationId configurado
     */
    public SovereignEnvelope<T> withCorrelation(String newCorrelationId) {
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, newCorrelationId, replyTo, senderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount, headers, payload
        );
    }

    /**
     * Crea copia con senderRole especifico.
     *
     * @param newSenderRole Rol del emisor
     * @return Nueva instancia con senderRole configurado
     */
    public SovereignEnvelope<T> withSenderRole(String newSenderRole) {
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, correlationId, replyTo, newSenderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount, headers, payload
        );
    }

    /**
     * [FASE 40.1] Crea copia con firma HMAC.
     *
     * <p>Usar con {@code HmacMessageSigner.sign()} para firmar el mensaje:</p>
     * <pre>{@code
     * String sig = HmacMessageSigner.sign(payload.toString(), timestamp.toEpochMilli());
     * envelope = envelope.withSignature(sig);
     * }</pre>
     *
     * @param newSignature Firma HMAC-SHA256 en Base64
     * @return Nueva instancia con signature configurado
     * @see HmacMessageSigner
     */
    public SovereignEnvelope<T> withSignature(String newSignature) {
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, correlationId, replyTo, senderRole,
            userId, newSignature, timestamp, ttlMs, retryCount, hopCount, headers, payload
        );
    }

    /**
     * Crea copia con header adicional.
     *
     * @param key   Clave del header
     * @param value Valor del header
     * @return Nueva instancia con header agregado
     */
    public SovereignEnvelope<T> withHeader(String key, String value) {
        var newHeaders = new HashMap<>(this.headers);
        newHeaders.put(key, value);
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, correlationId, replyTo, senderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount, newHeaders, payload
        );
    }

    /**
     * Crea copia con multiples headers adicionales.
     *
     * @param additionalHeaders Headers a agregar
     * @return Nueva instancia con headers combinados
     */
    public SovereignEnvelope<T> withHeaders(Map<String, String> additionalHeaders) {
        var newHeaders = new HashMap<>(this.headers);
        newHeaders.putAll(additionalHeaders);
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, correlationId, replyTo, senderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount, newHeaders, payload
        );
    }

    // =========================================================================
    // METODOS DE CICLO DE VIDA (FASE 30.1.2 + FASE 40.1)
    // =========================================================================

    /**
     * Incrementa el contador de reintentos.
     *
     * <p>Usado por el Bus cuando un mensaje falla y se reencola.</p>
     *
     * @return Nueva instancia con retryCount + 1
     */
    public SovereignEnvelope<T> incrementRetry() {
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, correlationId, replyTo, senderRole,
            userId, signature, timestamp, ttlMs, retryCount + 1, hopCount, headers, payload
        );
    }

    /**
     * [FASE 40.1] Incrementa el contador de saltos entre agentes.
     *
     * <p>Usado cuando un agente reenvía el mensaje a otro agente.
     * Previene bucles infinitos (ej: Agente A -> B -> A -> B...)</p>
     *
     * <p><b>Importante:</b> Si hopCount >= MAX_HOP_COUNT, el mensaje
     * debe ser rechazado o enviado a DLQ.</p>
     *
     * @return Nueva instancia con hopCount + 1
     * @throws IllegalStateException si hopCount >= MAX_HOP_COUNT (bucle detectado)
     */
    public SovereignEnvelope<T> incrementHop() {
        if (hopCount >= MAX_HOP_COUNT) {
            throw new IllegalStateException(
                "Kill Switch activado: Bucle infinito detectado. " +
                "hopCount=" + hopCount + " >= MAX_HOP_COUNT=" + MAX_HOP_COUNT
            );
        }
        return new SovereignEnvelope<>(
            id, idempotencyKey, schemaVersion, traceId, correlationId, replyTo, senderRole,
            userId, signature, timestamp, ttlMs, retryCount, hopCount + 1, headers, payload
        );
    }

    // =========================================================================
    // [FASE 55.3] MÉTODOS DE CONSULTA DE IDEMPOTENCIA
    // =========================================================================

    /**
     * [FASE 55.3] Verifica si la idempotencyKey es externa (webhook).
     *
     * @return true si la clave empieza con "EXT:"
     */
    public boolean hasExternalIdempotencyKey() {
        return idempotencyKey != null && idempotencyKey.startsWith(EXTERNAL_KEY_PREFIX);
    }

    /**
     * [FASE 55.3] Verifica si la idempotencyKey es temporal.
     *
     * <p>Las claves temporales empiezan con "TEMP:" y deben ser
     * finalizadas con {@link #withFinalIdempotencyKey} antes de publicar.</p>
     *
     * @return true si la clave es temporal
     */
    public boolean hasTemporaryIdempotencyKey() {
        return idempotencyKey != null && idempotencyKey.startsWith("TEMP:");
    }

    /**
     * [FASE 55.3] Verifica si la idempotencyKey es final (SHA-256).
     *
     * @return true si la clave es un hash SHA-256 (64 caracteres hex)
     */
    public boolean hasFinalIdempotencyKey() {
        return idempotencyKey != null
            && !idempotencyKey.startsWith("TEMP:")
            && !idempotencyKey.startsWith("FALLBACK:");
    }

    /**
     * Verifica si el mensaje ha expirado.
     *
     * <p>Un mensaje expirado NO debe ser entregado. Debe descartarse
     * o enviarse a la DLQ.</p>
     *
     * @return true si el TTL ha pasado
     */
    public boolean isExpired() {
        return ageMs() > ttlMs;
    }

    /**
     * Verifica si se ha excedido el maximo de reintentos.
     *
     * @return true si retryCount >= MAX_RETRY_COUNT
     */
    public boolean isMaxRetriesExceeded() {
        return retryCount >= MAX_RETRY_COUNT;
    }

    /**
     * [FASE 40.1] Verifica si se ha excedido el maximo de saltos.
     *
     * <p>Un mensaje que excede el limite de saltos indica un bucle
     * infinito y debe ser descartado.</p>
     *
     * @return true si hopCount >= MAX_HOP_COUNT
     */
    public boolean isMaxHopsExceeded() {
        return hopCount >= MAX_HOP_COUNT;
    }

    /**
     * [FASE 40.1] Verifica si el mensaje tiene firma valida.
     *
     * <p>Nota: Este metodo solo verifica que existe una firma,
     * no la valida criptograficamente. Usar {@code HmacMessageSigner.verify()}
     * para validacion completa.</p>
     *
     * @return true si tiene signature no nulo y no vacio
     */
    public boolean hasSignature() {
        return signature != null && !signature.isBlank();
    }

    // =========================================================================
    // METODOS DE CONSULTA
    // =========================================================================

    /**
     * Verifica si este envelope espera respuesta.
     *
     * @return true si tiene replyTo configurado
     */
    public boolean expectsReply() {
        return replyTo != null && !replyTo.isBlank();
    }

    /**
     * Obtiene la edad del mensaje en milisegundos.
     *
     * @return Milisegundos desde la creacion
     */
    public long ageMs() {
        return Instant.now().toEpochMilli() - timestamp.toEpochMilli();
    }

    /**
     * Calcula el tiempo restante antes de expirar.
     *
     * @return Milisegundos restantes (negativo si ya expiro)
     */
    public long remainingTtlMs() {
        return ttlMs - ageMs();
    }
}
