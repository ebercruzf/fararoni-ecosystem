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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class MetaEnterpriseEgressAdapter {

    private static final Logger LOG = Logger.getLogger(MetaEnterpriseEgressAdapter.class.getName());

    /** Base URL para Graph API de Meta */
    private static final String GRAPH_API_BASE = "https://graph.facebook.com";

    /** Versión de la API (actualizar según Meta) */
    private static final String DEFAULT_API_VERSION = "v18.0";

    /** Timeout para requests HTTP */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final String phoneId;
    private final String accessToken;
    private final String apiVersion;
    private final HttpClient httpClient;

    /**
     * Constructor con parámetros completos.
     *
     * @param phoneId     Phone Number ID de Meta Business
     * @param accessToken Token de acceso de Graph API
     * @param apiVersion  Versión de la API (ej: "v18.0")
     */
    public MetaEnterpriseEgressAdapter(String phoneId, String accessToken, String apiVersion) {
        this.phoneId = phoneId;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion != null ? apiVersion : DEFAULT_API_VERSION;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

        LOG.info("[META-EGRESS] Initialized for phone: " + phoneId);
    }

    /**
     * Constructor con versión de API por defecto.
     *
     * @param phoneId     Phone Number ID de Meta Business
     * @param accessToken Token de acceso de Graph API
     */
    public MetaEnterpriseEgressAdapter(String phoneId, String accessToken) {
        this(phoneId, accessToken, DEFAULT_API_VERSION);
    }

    /**
     * Envía un mensaje de texto a un destinatario.
     *
     * <p>Formato requerido por Meta:</p>
     * <pre>
     * POST /v18.0/{PHONE_ID}/messages
     * Authorization: Bearer {TOKEN}
     * Content-Type: application/json
     *
     * {
     *   "messaging_product": "whatsapp",
     *   "to": "5212291234567",
     *   "type": "text",
     *   "text": {"body": "Hola mundo"}
     * }
     * </pre>
     *
     * @param recipient número de teléfono del destinatario (sin @ ni sufijos)
     * @param message   texto del mensaje
     * @return CompletableFuture con el resultado
     */
    public CompletableFuture<SendResult> sendText(String recipient, String message) {
        // Limpiar número (quitar @s.whatsapp.net si viene del bus)
        String cleanRecipient = recipient.replace("@s.whatsapp.net", "")
                                          .replace("+", "")
                                          .trim();

        // Construir payload JSON
        String payload = """
            {
              "messaging_product": "whatsapp",
              "recipient_type": "individual",
              "to": "%s",
              "type": "text",
              "text": {
                "preview_url": false,
                "body": "%s"
              }
            }
            """.formatted(cleanRecipient, escapeJson(message));

        // Construir URL
        String url = String.format("%s/%s/%s/messages", GRAPH_API_BASE, apiVersion, phoneId);

        // Crear request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .timeout(HTTP_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        LOG.fine("[META-EGRESS] Sending to " + cleanRecipient);

        // Enviar de forma asíncrona
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    LOG.info("[META-EGRESS] Sent to " + cleanRecipient + " -> HTTP " + response.statusCode());
                    return new SendResult(true, response.statusCode(), response.body());
                } else {
                    LOG.warning("[META-EGRESS] Failed to send: HTTP " + response.statusCode() +
                                " - " + response.body());
                    return new SendResult(false, response.statusCode(), response.body());
                }
            })
            .exceptionally(ex -> {
                LOG.log(Level.SEVERE, "[META-EGRESS] Error sending message", ex);
                return new SendResult(false, -1, ex.getMessage());
            });
    }

    /**
     * Envía un mensaje de texto de forma síncrona (bloquea hasta completar).
     *
     * @param recipient número de teléfono del destinatario
     * @param message   texto del mensaje
     * @return resultado del envío
     */
    public SendResult sendTextSync(String recipient, String message) {
        try {
            return sendText(recipient, message).get();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[META-EGRESS] Sync send failed", e);
            return new SendResult(false, -1, e.getMessage());
        }
    }

    /**
     * Envía un mensaje de plantilla (template) predefinida.
     *
     * <p>Los templates deben estar aprobados previamente por Meta.</p>
     *
     * @param recipient    número del destinatario
     * @param templateName nombre del template aprobado
     * @param languageCode código de idioma (ej: "es", "en")
     * @return CompletableFuture con el resultado
     */
    public CompletableFuture<SendResult> sendTemplate(
            String recipient, String templateName, String languageCode) {

        String cleanRecipient = recipient.replace("@s.whatsapp.net", "")
                                          .replace("+", "")
                                          .trim();

        String payload = """
            {
              "messaging_product": "whatsapp",
              "to": "%s",
              "type": "template",
              "template": {
                "name": "%s",
                "language": {"code": "%s"}
              }
            }
            """.formatted(cleanRecipient, templateName, languageCode);

        String url = String.format("%s/%s/%s/messages", GRAPH_API_BASE, apiVersion, phoneId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .timeout(HTTP_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                if (success) {
                    LOG.info("[META-EGRESS] Template '" + templateName + "' sent to " + cleanRecipient);
                } else {
                    LOG.warning("[META-EGRESS] Template failed: " + response.body());
                }
                return new SendResult(success, response.statusCode(), response.body());
            })
            .exceptionally(ex -> new SendResult(false, -1, ex.getMessage()));
    }

    /**
     * Verifica si el adaptador está correctamente configurado.
     *
     * @return true si tiene phoneId y accessToken
     */
    public boolean isConfigured() {
        return phoneId != null && !phoneId.isBlank()
            && accessToken != null && !accessToken.isBlank();
    }

    /**
     * Escapa caracteres especiales para JSON.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Resultado de un envío.
     *
     * @param success    true si el mensaje fue aceptado por Meta
     * @param statusCode código HTTP de respuesta
     * @param body       cuerpo de la respuesta (incluye message ID si exitoso)
     */
    public record SendResult(boolean success, int statusCode, String body) {}
}
