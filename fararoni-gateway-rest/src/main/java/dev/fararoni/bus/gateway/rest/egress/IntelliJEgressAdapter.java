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
package dev.fararoni.bus.gateway.rest.egress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class IntelliJEgressAdapter {

    private static final Logger LOG = Logger.getLogger(IntelliJEgressAdapter.class.getName());

    /** Topic de salida donde escuchar respuestas */
    public static final String OUTPUT_TOPIC = "agency.output.main";

    /** Protocolo esperado para activar este adapter */
    public static final String PROTOCOL_INTELLIJ = "INTELLIJ";

    /** Timeout por defecto para HTTP POST */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /** Header para URL de callback */
    private static final String HEADER_CALLBACK_URL = "X-Callback-Url";

    /** Header alternativo (metadata) */
    private static final String METADATA_CALLBACK_URL = "callback_url";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SovereignEventBus bus;

    private final AtomicLong totalDelivered = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalChunks = new AtomicLong(0);

    private volatile boolean running = false;

    /**
     * Crea un nuevo IntelliJEgressAdapter.
     *
     * @param bus el bus soberano para suscripción
     */
    public IntelliJEgressAdapter(SovereignEventBus bus) {
        this.bus = bus;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Constructor para testing sin bus.
     */
    public IntelliJEgressAdapter() {
        this(null);
    }

    /**
     * Inicia el adapter y se suscribe al topic de salida.
     *
     * <p>Filtra mensajes donde {@code X-Origin-Protocol = "INTELLIJ"}.</p>
     */
    public void start() {
        if (running) {
            LOG.warning("[IntelliJ-Egress] Already running");
            return;
        }

        if (bus != null) {
            bus.subscribe(OUTPUT_TOPIC, String.class, this::handleOutput);
            LOG.info("[IntelliJ-Egress] Subscribed to " + OUTPUT_TOPIC);
        }

        running = true;
        LOG.info("[IntelliJ-Egress] Started - Listening for IntelliJ callbacks");
    }

    /**
     * Detiene el adapter.
     */
    public void stop() {
        if (!running) return;
        running = false;
        LOG.info("[IntelliJ-Egress] Stopped (delivered: " + totalDelivered.get() +
                 ", failed: " + totalFailed.get() + ", chunks: " + totalChunks.get() + ")");
    }

    /**
     * Procesa un envelope del bus.
     *
     * <p>Solo procesa si el protocolo es INTELLIJ.</p>
     *
     * @param envelope el envelope con la respuesta
     */
    public void handleOutput(SovereignEnvelope<String> envelope) {
        if (!running) return;

        Map<String, String> headers = envelope.headers();

        // Verificar que es para IntelliJ
        String protocol = headers.getOrDefault("X-Origin-Protocol", "");
        if (!PROTOCOL_INTELLIJ.equalsIgnoreCase(protocol)) {
            // No es para nosotros, ignorar silenciosamente
            return;
        }

        // Extraer callback_url (puede estar en header o metadata)
        String callbackUrl = headers.get(HEADER_CALLBACK_URL);
        if (callbackUrl == null || callbackUrl.isBlank()) {
            callbackUrl = headers.get(METADATA_CALLBACK_URL);
        }

        if (callbackUrl == null || callbackUrl.isBlank()) {
            LOG.warning("[IntelliJ-Egress] Missing callback_url in envelope " + envelope.traceId());
            totalFailed.incrementAndGet();
            return;
        }

        // Extraer metadatos
        String intent = headers.getOrDefault("X-Intent", "CHAT_RESPONSE");
        String filePath = headers.get("X-File-Path");
        boolean isFinal = "true".equalsIgnoreCase(headers.getOrDefault("X-Is-Final", "true"));
        boolean isChunk = "true".equalsIgnoreCase(headers.get("X-Is-Chunk"));

        if (isChunk) {
            totalChunks.incrementAndGet();
        }

        // Construir payload JSON
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("traceId", envelope.traceId());
        payload.put("intent", intent);
        payload.put("content", envelope.payload() != null ? envelope.payload() : "");
        payload.put("isFinal", isFinal);
        payload.put("timestamp", System.currentTimeMillis());

        if (filePath != null && !filePath.isBlank()) {
            payload.put("filePath", filePath);
        }

        // Enviar HTTP POST asíncrono
        deliverToPlugin(callbackUrl, payload, envelope.traceId());
    }

    /**
     * Envía el payload al CallbackServer del plugin via HTTP POST.
     *
     * <p>Operación asíncrona (fire-and-forget) con logging de resultado.</p>
     *
     * @param callbackUrl URL del CallbackServer (ej: http://localhost:9999/push)
     * @param payload     JSON a enviar
     * @param traceId     ID para logging
     */
    private void deliverToPlugin(String callbackUrl, ObjectNode payload, String traceId) {
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[IntelliJ-Egress] Failed to serialize payload", e);
            totalFailed.incrementAndGet();
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(callbackUrl))
            .timeout(DEFAULT_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("X-Fararoni-Egress", "IntelliJ")
            .header("X-Trace-Id", traceId)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        LOG.fine("[IntelliJ-Egress] Delivering to " + callbackUrl + " (trace: " + traceId + ")");

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    totalDelivered.incrementAndGet();
                    LOG.info("[IntelliJ-Egress] Delivered to plugin (HTTP " + response.statusCode() +
                             ", trace: " + traceId + ")");
                } else {
                    totalFailed.incrementAndGet();
                    LOG.warning("[IntelliJ-Egress] Plugin rejected (HTTP " + response.statusCode() +
                                ", trace: " + traceId + ")");
                }
            })
            .exceptionally(ex -> {
                totalFailed.incrementAndGet();
                LOG.log(Level.WARNING, "[IntelliJ-Egress] Delivery failed to " + callbackUrl +
                        " (trace: " + traceId + ")", ex);
                return null;
            });
    }

    /**
     * Retorna el total de entregas exitosas.
     *
     * @return contador de entregas exitosas
     */
    public long getTotalDelivered() {
        return totalDelivered.get();
    }

    /**
     * Retorna el total de entregas fallidas.
     *
     * @return contador de fallos
     */
    public long getTotalFailed() {
        return totalFailed.get();
    }

    /**
     * Retorna el total de chunks de streaming enviados.
     *
     * @return contador de chunks
     */
    public long getTotalChunks() {
        return totalChunks.get();
    }

    /**
     * Verifica si el adapter está corriendo.
     *
     * @return true si está activo
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Envía directamente una respuesta al plugin.
     *
     * <p>Útil para casos donde se quiere bypasear el bus.</p>
     *
     * @param callbackUrl URL del plugin
     * @param intent      tipo de respuesta
     * @param content     contenido a enviar
     * @param filePath    archivo relacionado (puede ser null)
     * @param isFinal     true si es el último chunk
     */
    public void sendDirect(String callbackUrl, String intent, String content,
                           String filePath, boolean isFinal) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("traceId", java.util.UUID.randomUUID().toString().substring(0, 8));
        payload.put("intent", intent);
        payload.put("content", content);
        payload.put("isFinal", isFinal);
        payload.put("timestamp", System.currentTimeMillis());

        if (filePath != null) {
            payload.put("filePath", filePath);
        }

        deliverToPlugin(callbackUrl, payload, payload.get("traceId").asText());
    }
}
