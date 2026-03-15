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
package dev.fararoni.core.gateway;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.server.PluginWebSocketBridge;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class OutputDispatcher implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(OutputDispatcher.class.getName());

    public enum OriginProtocol {
        VOICE,
        CLI,
        PLUGIN,
        MATRIX,
        UNKNOWN
    }

    private final SovereignEventBus bus;
    private PluginWebSocketBridge pluginBridge;
    private Consumer<String> ttsHandler;
    private PrintStream cliOutput;
    private final Map<OriginProtocol, Consumer<SovereignEnvelope<String>>> customHandlers =
        new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Map<OriginProtocol, Long> dispatchCounts = new ConcurrentHashMap<>();

    public OutputDispatcher(SovereignEventBus bus) {
        this.bus = bus;
        this.cliOutput = System.out;

        for (OriginProtocol protocol : OriginProtocol.values()) {
            dispatchCounts.put(protocol, 0L);
        }
    }

    public void activate() {
        if (active.getAndSet(true)) {
            LOG.warning("[OutputDispatcher] Ya está activo");
            return;
        }

        bus.subscribe("sys.output", String.class, this::dispatch);
        bus.subscribe("sys.output.voice", String.class, this::dispatch);
        bus.subscribe("sys.output.cli", String.class, this::dispatch);
        bus.subscribe("sys.output.plugin", String.class, this::dispatch);

        LOG.info("[OutputDispatcher] Activado, escuchando sys.output.*");
    }

    public void deactivate() {
        if (!active.getAndSet(false)) {
            return;
        }

        LOG.info(() -> String.format(
            "[OutputDispatcher] Desactivado. Dispatch counts: %s",
            dispatchCounts
        ));
    }

    public OutputDispatcher withPluginBridge(PluginWebSocketBridge bridge) {
        this.pluginBridge = bridge;
        return this;
    }

    public OutputDispatcher withTtsHandler(Consumer<String> handler) {
        this.ttsHandler = handler;
        return this;
    }

    public OutputDispatcher withCliOutput(PrintStream output) {
        this.cliOutput = output;
        return this;
    }

    public OutputDispatcher withCustomHandler(OriginProtocol protocol,
                                               Consumer<SovereignEnvelope<String>> handler) {
        customHandlers.put(protocol, handler);
        return this;
    }

    public void dispatch(SovereignEnvelope<String> envelope) {
        if (!active.get()) {
            return;
        }

        try {
            OriginProtocol protocol = extractProtocol(envelope);
            String payload = envelope.payload();

            dispatchCounts.merge(protocol, 1L, Long::sum);

            Consumer<SovereignEnvelope<String>> customHandler = customHandlers.get(protocol);
            if (customHandler != null) {
                customHandler.accept(envelope);
                return;
            }

            switch (protocol) {
                case VOICE -> dispatchToVoice(envelope, payload);
                case CLI -> dispatchToCli(envelope, payload);
                case PLUGIN -> dispatchToPlugin(envelope);
                case MATRIX -> dispatchToMatrix(envelope, payload);
                default -> dispatchToDefault(envelope, payload);
            }

            LOG.fine(() -> String.format(
                "[OutputDispatcher] Mensaje despachado: %s -> %s",
                envelope.id(), protocol
            ));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[OutputDispatcher] Error despachando mensaje", e);
        }
    }

    private OriginProtocol extractProtocol(SovereignEnvelope<String> envelope) {
        String protocolStr = envelope.headers().get("origin_protocol");
        if (protocolStr == null || protocolStr.isBlank()) {
            return OriginProtocol.UNKNOWN;
        }

        try {
            return OriginProtocol.valueOf(protocolStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.fine(() -> "[OutputDispatcher] Protocolo desconocido: " + protocolStr);
            return OriginProtocol.UNKNOWN;
        }
    }

    private void dispatchToVoice(SovereignEnvelope<String> envelope, String payload) {
        if (ttsHandler != null) {
            ttsHandler.accept(payload);
            LOG.fine("[OutputDispatcher] Enviado a TTS");
        } else {
            bus.publish("sys.output.voice.tts", envelope);
            LOG.fine("[OutputDispatcher] Publicado en sys.output.voice.tts (sin TTS handler)");
        }
    }

    private void dispatchToCli(SovereignEnvelope<String> envelope, String payload) {
        if (cliOutput != null) {
            cliOutput.println(payload);
        }
    }

    private void dispatchToPlugin(SovereignEnvelope<String> envelope) {
        if (pluginBridge != null) {
            String replyChannelId = envelope.headers().get("reply_channel_id");

            if (replyChannelId != null && !replyChannelId.isBlank()) {
                pluginBridge.broadcastToPlugins("sys.output.plugin." + replyChannelId, envelope);
            } else {
                pluginBridge.broadcastToAllPlugins(envelope);
            }
        } else {
            LOG.fine("[OutputDispatcher] PluginBridge no configurado, mensaje descartado");
        }
    }

    private void dispatchToMatrix(SovereignEnvelope<String> envelope, String payload) {
        String roomId = envelope.headers().get("reply_channel_id");
        if (roomId != null && !roomId.isBlank()) {
            SovereignEnvelope<String> matrixEnvelope = envelope
                .withHeader("matrix_room_id", roomId);
            bus.publish("sys.output.matrix", matrixEnvelope);
        } else {
            bus.publish("sys.output.matrix", envelope);
        }
        LOG.fine("[OutputDispatcher] Publicado en sys.output.matrix");
    }

    private void dispatchToDefault(SovereignEnvelope<String> envelope, String payload) {
        if (cliOutput != null) {
            cliOutput.println("[UNKNOWN PROTOCOL] " + payload);
        }
        LOG.fine("[OutputDispatcher] Protocolo desconocido, enviado a CLI");
    }

    public Map<OriginProtocol, Long> getDispatchStats() {
        return Map.copyOf(dispatchCounts);
    }

    public long getTotalDispatched() {
        return dispatchCounts.values().stream().mapToLong(Long::longValue).sum();
    }

    public boolean isActive() {
        return active.get();
    }

    @Override
    public void close() {
        deactivate();
    }
}
