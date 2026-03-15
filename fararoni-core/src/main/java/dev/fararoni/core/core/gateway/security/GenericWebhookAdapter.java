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
package dev.fararoni.core.core.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.security.ChannelAccessGuard;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class GenericWebhookAdapter implements SecureChannelAdapter {
    private static final Logger LOG = Logger.getLogger(GenericWebhookAdapter.class.getName());

    private final String channelId;
    private final String protocol;
    private final ChannelTrustLevel trustLevel;

    private final String mappingText;

    private final String mappingSender;

    private final String mappingReplyTo;

    private final String outboundUrl;

    private final String outboundTemplate;

    private final SovereignEventBus bus;
    private final ChannelAccessGuard guard;
    private final HttpClient httpClient;

    private volatile boolean active = false;
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private volatile long lastActivityMs = 0;

    public GenericWebhookAdapter(
            String channelId,
            JsonNode config,
            SovereignEventBus bus,
            ChannelAccessGuard guard) {
        this.channelId = channelId;
        this.bus = bus;
        this.guard = guard;

        this.protocol = getConfigString(config, "protocol", "GENERIC_WEBHOOK");
        this.mappingText = getConfigString(config, "mapping_text", "$.text");
        this.mappingSender = getConfigString(config, "mapping_sender", "$.sender");
        this.mappingReplyTo = getConfigString(config, "mapping_reply_to", "$.channel");
        this.outboundUrl = getConfigString(config, "outbound_url", null);
        this.outboundTemplate = getConfigString(config, "outbound_template", "{\"text\": \"${message}\"}");

        String trustConfig = getConfigString(config, "trust_level", "UNTRUSTED_EXTERNAL");
        this.trustLevel = ChannelTrustLevel.valueOf(trustConfig);

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        LOG.info(() -> String.format(
            "[GenericWebhook] Creado: %s (%s) - mappings: text=%s, sender=%s",
            channelId, protocol, mappingText, mappingSender
        ));
    }

    @Override
    public String getChannelId() {
        return channelId;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public ChannelTrustLevel getChannelTrustLevel() {
        return trustLevel;
    }

    @Override
    public void start() throws ChannelConnectionException {
        active = true;
        LOG.info(() -> "[GenericWebhook] Iniciado: " + channelId);
    }

    @Override
    public void stop() {
        active = false;
        LOG.info(() -> "[GenericWebhook] Detenido: " + channelId);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public void onWebhook(String jsonBody) {
        if (!active) {
            LOG.warning(() -> "[GenericWebhook] Webhook ignorado - adapter inactivo: " + channelId);
            return;
        }

        try {
            String text = extractJsonPath(jsonBody, mappingText);
            String sender = extractJsonPath(jsonBody, mappingSender);
            String replyTo = extractJsonPath(jsonBody, mappingReplyTo);

            if (text == null || text.isBlank()) {
                LOG.fine(() -> "[GenericWebhook] Mensaje vacio, ignorando");
                return;
            }

            messagesReceived.incrementAndGet();
            lastActivityMs = System.currentTimeMillis();

            LOG.info(() -> String.format(
                "[GenericWebhook] [%s] Mensaje de %s: %s",
                protocol, sender, truncate(text, 50)
            ));

            var envelope = normalize(text, Map.of(
                "sender", sender != null ? sender : "anonymous",
                "replyTo", replyTo != null ? replyTo : sender
            ));

            String topic = "agency.input." + protocol.toLowerCase();
            bus.publish(topic, envelope);
        } catch (Exception e) {
            errors.incrementAndGet();
            LOG.warning(() -> "[GenericWebhook] Error procesando webhook: " + e.getMessage());
        }
    }

    @Override
    public SovereignEnvelope<String> normalize(Object rawInput, Map<String, String> metadata) {
        String text = rawInput.toString();
        String sender = metadata.getOrDefault("sender", "anonymous");
        String replyTo = metadata.getOrDefault("replyTo", sender);

        return SovereignEnvelope.create(
            sender,
            "EXTERNAL_USER",
            UUID.randomUUID().toString(),
            text
        )
        .withHeader("X-Origin-Protocol", protocol)
        .withHeader("X-Reply-Channel-Id", replyTo)
        .withHeader("X-Security-Level", trustLevel.name())
        .withHeader("X-Channel-Id", channelId);
    }

    @Override
    public CompletableFuture<Void> sendDirectResponse(String destinationId, String text) {
        if (outboundUrl == null || outboundUrl.isBlank()) {
            LOG.warning(() -> "[GenericWebhook] Sin outbound_url configurada para: " + channelId);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                String body = outboundTemplate.replace("${message}", escapeJson(text));

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(outboundUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    messagesSent.incrementAndGet();
                    lastActivityMs = System.currentTimeMillis();
                    LOG.fine(() -> "[GenericWebhook] Respuesta enviada a: " + destinationId);
                } else {
                    errors.incrementAndGet();
                    LOG.warning(() -> String.format(
                        "[GenericWebhook] Error HTTP %d enviando a %s",
                        response.statusCode(), outboundUrl
                    ));
                }
            } catch (Exception e) {
                errors.incrementAndGet();
                LOG.warning(() -> "[GenericWebhook] Error enviando respuesta: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendMediaResponse(String destinationId, Path mediaFile, String caption) {
        LOG.warning(() -> "[GenericWebhook] sendMediaResponse no soportado para: " + channelId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Map<String, Long> getStats() {
        return Map.of(
            "messages_received", messagesReceived.get(),
            "messages_sent", messagesSent.get(),
            "errors", errors.get(),
            "last_activity_ms", lastActivityMs
        );
    }

    private String extractJsonPath(String json, String path) {
        try {
            Object result = JsonPath.read(json, path);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            LOG.fine(() -> "[GenericWebhook] JSONPath no encontrado: " + path);
            return null;
        }
    }

    private String getConfigString(JsonNode config, String key, String defaultValue) {
        if (config != null && config.has(key)) {
            return config.get(key).asText(defaultValue);
        }
        return defaultValue;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
