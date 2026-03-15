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
package dev.fararoni.core.core.gateway;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.config.ChannelsConfig;
import dev.fararoni.core.core.gateway.security.SecureChannelAdapter;
import dev.fararoni.core.core.security.ChannelAccessGuard;
import dev.fararoni.core.core.security.ChannelAccessGuard.ChannelAccessResult;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class MessagingChannelManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(MessagingChannelManager.class.getName());

    private static final String OUTPUT_TOPIC = "agency.output.main";

    private final SovereignEventBus bus;
    private final ChannelAccessGuard guard;
    private final ChannelsConfig config;

    private final Map<String, SecureChannelAdapter> adapters = new ConcurrentHashMap<>();

    private final Map<String, SecureChannelAdapter> adaptersByProtocol = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    public MessagingChannelManager(SovereignEventBus bus, ChannelAccessGuard guard, ChannelsConfig config) {
        this.bus = bus;
        this.guard = guard;
        this.config = config;
    }

    public void registerAdapter(SecureChannelAdapter adapter) {
        adapters.put(adapter.getChannelId(), adapter);
        adaptersByProtocol.put(adapter.getProtocol(), adapter);
        LOG.info(() -> String.format(
            "[MessagingChannelManager] Registrado: %s (%s) - %s",
            adapter.getChannelId(),
            adapter.getProtocol(),
            adapter.getChannelTrustLevel()
        ));
    }

    public void start() {
        if (running) {
            LOG.warning("[MessagingChannelManager] Ya esta corriendo");
            return;
        }

        running = true;

        if (adaptersByProtocol.isEmpty()) {
            LOG.info("[MessagingChannelManager] Sin adaptadores heredados. Egress delegado a Gateway REST.");
            return;
        }

        bus.subscribe(OUTPUT_TOPIC, String.class, this::onAgentResponse);
        LOG.info("[MessagingChannelManager] Suscrito a: " + OUTPUT_TOPIC + " para: " + adaptersByProtocol.keySet());

        for (SecureChannelAdapter adapter : adapters.values()) {
            try {
                adapter.start();
                LOG.info(() -> "[MessagingChannelManager] Iniciado: " + adapter.getChannelId());
            } catch (Exception e) {
                LOG.warning(() -> String.format(
                    "[MessagingChannelManager] Fallo al iniciar %s: %s",
                    adapter.getChannelId(), e.getMessage()
                ));
            }
        }

        LOG.info(() -> String.format(
            "[MessagingChannelManager] Activo con %d canales",
            adapters.size()
        ));
    }

    public void stop() {
        if (!running) return;

        running = false;

        for (SecureChannelAdapter adapter : adapters.values()) {
            try {
                adapter.stop();
                LOG.info(() -> "[MessagingChannelManager] Detenido: " + adapter.getChannelId());
            } catch (Exception e) {
                LOG.warning(() -> "[MessagingChannelManager] Error deteniendo " + adapter.getChannelId());
            }
        }

        LOG.info("[MessagingChannelManager] Todos los canales detenidos");
    }

    public void onMessageReceived(
            SecureChannelAdapter adapter,
            String senderId,
            String senderName,
            String content,
            boolean isGroup,
            String groupId,
            String replyToId) {
        String channelId = adapter.getChannelId();
        String protocol = adapter.getProtocol();

        LOG.fine(() -> String.format(
            "[MessagingChannelManager] Mensaje de %s via %s: %s",
            senderId, channelId, truncate(content, 50)
        ));

        ChannelAccessResult access = guard.checkAccess(senderId, isGroup, groupId);

        switch (access.status()) {
            case ALLOWED -> {
                publishToBus(adapter, senderId, senderName, content, replyToId);
            }

            case DENIED_NEEDS_PAIRING -> {
                String msg = String.format(
                    "Acceso pendiente. Tu codigo de verificacion es: %s\n" +
                    "Espera a que el administrador lo apruebe.",
                    access.pairingCode()
                );
                sendResponse(adapter, replyToId, msg);
            }

            case DENIED_BLOCK -> {
                var pairingResult = guard.initiatePairing(senderId, senderName, protocol);

                if (pairingResult.success()) {
                    String msg = String.format(
                        "No estas autorizado para usar este servicio.\n\n" +
                        "Se ha generado una solicitud de acceso.\n" +
                        "Tu codigo de verificacion es: %s\n\n" +
                        "Comparte este codigo con el administrador para ser aprobado.\n" +
                        "El codigo expira en %d hora(s).",
                        pairingResult.request().code(),
                        ChannelAccessGuard.PAIRING_EXPIRY_HOURS
                    );
                    sendResponse(adapter, replyToId, msg);
                } else {
                    sendResponse(adapter, replyToId,
                        "No estas autorizado. " + pairingResult.errorMessage());
                }
            }

            case IGNORED_GROUP -> {
                LOG.fine(() -> "[MessagingChannelManager] Grupo ignorado: " + groupId);
            }
        }
    }

    private void onAgentResponse(SovereignEnvelope<String> envelope) {
        String protocol = envelope.headers().get("X-Origin-Protocol");
        String replyTo = envelope.headers().get("X-Reply-Channel-Id");

        if (protocol == null || replyTo == null) {
            LOG.fine(() -> "[MCM] Respuesta sin headers de ruteo, ignorando");
            return;
        }

        SecureChannelAdapter adapter = adaptersByProtocol.get(protocol);
        if (adapter == null) {
            LOG.fine(() -> "[MCM] Ignorando mensaje. Protocolo gestionado por otro módulo: " + protocol);
            return;
        }

        LOG.fine(() -> "[MCM] Adapter encontrado: " + adapter.getClass().getSimpleName());

        String content = envelope.payload();
        LOG.fine(() -> "[MCM] Enviando a " + replyTo + ": " + truncate(content, 100));
        sendResponse(adapter, replyTo, content);
    }

    private void publishToBus(
            SecureChannelAdapter adapter,
            String senderId,
            String senderName,
            String content,
            String replyToId) {
        String topic = "agency.input." + adapter.getProtocol().toLowerCase();

        var envelope = SovereignEnvelope.create(
            senderId,
            senderName != null ? senderName : "EXTERNAL_USER",
            java.util.UUID.randomUUID().toString(),
            content
        )
        .withHeader("X-Origin-Protocol", adapter.getProtocol())
        .withHeader("X-Reply-Channel-Id", replyToId)
        .withHeader("X-Security-Level", adapter.getChannelTrustLevel().name())
        .withHeader("X-Channel-Id", adapter.getChannelId());

        bus.publish(topic, envelope);

        LOG.fine(() -> String.format(
            "[MessagingChannelManager] Publicado en %s desde %s",
            topic, senderId
        ));
    }

    private void sendResponse(SecureChannelAdapter adapter, String destinationId, String text) {
        adapter.sendDirectResponse(destinationId, text)
            .exceptionally(ex -> {
                LOG.warning(() -> String.format(
                    "[MessagingChannelManager] Error enviando a %s: %s",
                    destinationId, ex.getMessage()
                ));
                return null;
            });
    }

    public Optional<SecureChannelAdapter> getAdapter(String channelId) {
        return Optional.ofNullable(adapters.get(channelId));
    }

    public Optional<SecureChannelAdapter> getAdapterByProtocol(String protocol) {
        return Optional.ofNullable(adaptersByProtocol.get(protocol));
    }

    public Map<String, SecureChannelAdapter> getAdapters() {
        return Map.copyOf(adapters);
    }

    public boolean isRunning() {
        return running;
    }

    public Map<String, Map<String, Long>> getAllStats() {
        Map<String, Map<String, Long>> allStats = new ConcurrentHashMap<>();
        for (var entry : adapters.entrySet()) {
            allStats.put(entry.getKey(), entry.getValue().getStats());
        }
        return allStats;
    }

    @Override
    public void close() {
        stop();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
