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
package dev.fararoni.core.core.skills.bridge;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CapabilityManager {
    private static final Logger LOG = Logger.getLogger(CapabilityManager.class.getName());

    public static final String TOPIC_HANDSHAKE_HELLO = "system.handshake.hello";

    public static final String TOPIC_HANDSHAKE_ACK = "system.handshake.ack";

    public static final String TOPIC_HANDSHAKE_BYE = "system.handshake.bye";

    private final SovereignEventBus bus;

    private final Map<String, SidecarInfo> registeredSidecars = new ConcurrentHashMap<>();

    private final Map<String, String> toolToSidecar = new ConcurrentHashMap<>();

    private final Map<String, PriorityBlockingQueue<SidecarRoute>> toolProviders = new ConcurrentHashMap<>();

    private final List<Consumer<CapabilityEvent>> listeners = new ArrayList<>();

    private String minRequiredVersion = "1.0.0";

    public CapabilityManager(SovereignEventBus bus) {
        this.bus = bus;
        subscribeToHandshakes();
    }

    private void subscribeToHandshakes() {
        bus.subscribe(TOPIC_HANDSHAKE_HELLO, String.class, envelope -> {
            handleHandshake(envelope.payload(), envelope.correlationId());
        });

        bus.subscribe(TOPIC_HANDSHAKE_BYE, String.class, envelope -> {
            handleDisconnect(envelope.payload());
        });

        LOG.info("[CAPABILITY-MGR] Escuchando handshakes en: " + TOPIC_HANDSHAKE_HELLO);
    }

    public void handleHandshake(String jsonPayload, String correlationId) {
        try {
            SidecarHandshake handshake = parseHandshake(jsonPayload);

            if (!isVersionCompatible(handshake.version())) {
                LOG.warning("[CAPABILITY-MGR] Sidecar " + handshake.sidecarId() +
                    " rechazado: version incompatible (" + handshake.version() + ")");
                sendAck(correlationId, "REJECTED_VERSION_MISMATCH",
                    "Version minima requerida: " + minRequiredVersion);
                return;
            }

            SidecarInfo info = new SidecarInfo(
                handshake.sidecarId(),
                handshake.type(),
                handshake.priority(),
                handshake.capabilities(),
                handshake.version(),
                Instant.now()
            );
            registeredSidecars.put(handshake.sidecarId(), info);

            for (SidecarCapability cap : handshake.capabilities()) {
                toolToSidecar.put(cap.name(), handshake.sidecarId());
            }

            SidecarRoute route = SidecarRoute.from(info);
            for (SidecarCapability cap : handshake.capabilities()) {
                PriorityBlockingQueue<SidecarRoute> queue = toolProviders
                    .computeIfAbsent(cap.name(), k -> new PriorityBlockingQueue<>());

                queue.removeIf(r -> r.sidecarId().equals(handshake.sidecarId()));
                queue.add(route);

                LOG.info("[CAPABILITY-MGR] Herramienta '" + cap.name() +
                    "' ahora tiene " + queue.size() + " proveedores");
            }

            notifyListeners(new CapabilityEvent(
                CapabilityEventType.SIDECAR_CONNECTED,
                handshake.sidecarId(),
                handshake.capabilities()
            ));

            sendAck(correlationId, "ACCEPTED", null);

            LOG.info("[CAPABILITY-MGR] Sidecar '" + handshake.sidecarId() +
                "' conectado con " + handshake.capabilities().size() + " herramientas");
        } catch (Exception e) {
            LOG.warning("[CAPABILITY-MGR] Error procesando handshake: " + e.getMessage());
            sendAck(correlationId, "REJECTED_INVALID_FORMAT", e.getMessage());
        }
    }

    private void handleDisconnect(String jsonPayload) {
        try {
            Pattern pattern = Pattern.compile("\"sidecar_id\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(jsonPayload);
            if (matcher.find()) {
                String sidecarId = matcher.group(1);
                removeSidecar(sidecarId);
            }
        } catch (Exception e) {
            LOG.warning("[CAPABILITY-MGR] Error procesando desconexion: " + e.getMessage());
        }
    }

    public void removeSidecar(String sidecarId) {
        SidecarInfo info = registeredSidecars.remove(sidecarId);
        if (info != null) {
            for (SidecarCapability cap : info.capabilities()) {
                toolToSidecar.remove(cap.name());
            }

            for (SidecarCapability cap : info.capabilities()) {
                PriorityBlockingQueue<SidecarRoute> queue = toolProviders.get(cap.name());
                if (queue != null) {
                    queue.removeIf(r -> r.sidecarId().equals(sidecarId));
                    if (queue.isEmpty()) {
                        toolProviders.remove(cap.name());
                        LOG.warning("[CAPABILITY-MGR] Capacidad '" + cap.name() + "' sin proveedores");
                    }
                }
            }

            notifyListeners(new CapabilityEvent(
                CapabilityEventType.SIDECAR_DISCONNECTED,
                sidecarId,
                info.capabilities()
            ));

            LOG.info("[CAPABILITY-MGR] Sidecar '" + sidecarId + "' desconectado");
        }
    }

    private void sendAck(String correlationId, String status, String message) {
        String payload = String.format(
            "{\"status\":\"%s\"%s}",
            status,
            message != null ? ",\"message\":\"" + escapeJson(message) + "\"" : ""
        );

        SovereignEnvelope<String> ack = SovereignEnvelope.create(
            "capability-manager",
            correlationId != null ? correlationId : UUID.randomUUID().toString(),
            payload
        );

        bus.publish(TOPIC_HANDSHAKE_ACK, ack);
    }

    @Deprecated
    public String getSidecarForTool(String toolName) {
        return toolToSidecar.get(toolName);
    }

    public Optional<SidecarRoute> getBestProvider(String capability) {
        PriorityBlockingQueue<SidecarRoute> queue = toolProviders.get(capability);

        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }

        SidecarRoute best = queue.peek();

        if (best != null && best.isIsolated()) {
            return queue.stream()
                .filter(r -> !r.isIsolated())
                .findFirst();
        }

        return Optional.ofNullable(best);
    }

    public List<SidecarRoute> getAllProvidersForTool(String capability) {
        PriorityBlockingQueue<SidecarRoute> queue = toolProviders.get(capability);

        if (queue == null || queue.isEmpty()) {
            return Collections.emptyList();
        }

        return queue.stream()
            .filter(r -> !r.isIsolated())
            .sorted()
            .collect(Collectors.toList());
    }

    public boolean hasActiveProvider(String capability) {
        PriorityBlockingQueue<SidecarRoute> queue = toolProviders.get(capability);
        return queue != null && queue.stream().anyMatch(r -> !r.isIsolated());
    }

    public int getProviderCount(String capability) {
        PriorityBlockingQueue<SidecarRoute> queue = toolProviders.get(capability);
        return queue == null ? 0 : (int) queue.stream().filter(r -> !r.isIsolated()).count();
    }

    public void reportFailure(String capability, String sidecarId) {
        PriorityBlockingQueue<SidecarRoute> providers = toolProviders.get(capability);

        if (providers != null) {
            providers.removeIf(p -> p.sidecarId().equals(sidecarId));

            LOG.warning("[CIRCUIT-BREAKER] Sidecar '" + sidecarId +
                "' removido de capacidad '" + capability + "' por fallo");

            if (providers.isEmpty()) {
                LOG.severe("[CIRCUIT-BREAKER] ALERTA: Capacidad '" + capability +
                    "' sin proveedores disponibles");
            }
        }
    }

    public void isolateSidecar(String capability, String sidecarId) {
        PriorityBlockingQueue<SidecarRoute> providers = toolProviders.get(capability);

        if (providers != null) {
            List<SidecarRoute> routes = new ArrayList<>(providers);
            providers.clear();

            for (SidecarRoute route : routes) {
                if (route.sidecarId().equals(sidecarId)) {
                    providers.add(route.isolated());
                } else {
                    providers.add(route);
                }
            }

            LOG.info("[CIRCUIT-BREAKER] Sidecar '" + sidecarId +
                "' aislado para capacidad '" + capability + "'");
        }
    }

    public void rehabilitateSidecar(String capability, String sidecarId) {
        PriorityBlockingQueue<SidecarRoute> providers = toolProviders.get(capability);

        if (providers != null) {
            List<SidecarRoute> routes = new ArrayList<>(providers);
            providers.clear();

            for (SidecarRoute route : routes) {
                if (route.sidecarId().equals(sidecarId) && route.isIsolated()) {
                    providers.add(route.rehabilitated());
                } else {
                    providers.add(route);
                }
            }

            LOG.info("[CIRCUIT-BREAKER] Sidecar '" + sidecarId +
                "' rehabilitado para capacidad '" + capability + "'");
        }
    }

    public Map<String, List<String>> getCapabilitySnapshot() {
        Map<String, List<String>> snapshot = new HashMap<>();

        for (Map.Entry<String, PriorityBlockingQueue<SidecarRoute>> entry : toolProviders.entrySet()) {
            List<String> providers = entry.getValue().stream()
                .map(r -> String.format("%s(p:%d,h:%.2f%s)",
                    r.sidecarId(), r.priority(), r.healthScore(),
                    r.isIsolated() ? ",ISOLATED" : ""))
                .collect(Collectors.toList());
            snapshot.put(entry.getKey(), providers);
        }

        return snapshot;
    }

    public List<String> getAvailableTools() {
        return new ArrayList<>(toolToSidecar.keySet());
    }

    public SidecarInfo getSidecarInfo(String sidecarId) {
        return registeredSidecars.get(sidecarId);
    }

    public Map<String, SidecarInfo> getAllSidecars() {
        return Collections.unmodifiableMap(registeredSidecars);
    }

    public boolean hasTool(String toolName) {
        return toolToSidecar.containsKey(toolName);
    }

    public void addListener(Consumer<CapabilityEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<CapabilityEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(CapabilityEvent event) {
        for (Consumer<CapabilityEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.warning("[CAPABILITY-MGR] Error en listener: " + e.getMessage());
            }
        }
    }

    public void setMinRequiredVersion(String version) {
        this.minRequiredVersion = version;
    }

    private boolean isVersionCompatible(String version) {
        try {
            String[] required = minRequiredVersion.split("\\.");
            String[] actual = version.split("\\.");

            for (int i = 0; i < Math.min(required.length, actual.length); i++) {
                int req = Integer.parseInt(required[i]);
                int act = Integer.parseInt(actual[i]);
                if (act < req) return false;
                if (act > req) return true;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private SidecarHandshake parseHandshake(String json) {
        String sidecarId = extractString(json, "sidecar_id");
        String type = extractString(json, "type");
        int priority = extractInt(json, "priority", 50);
        String version = extractString(json, "version");
        List<SidecarCapability> capabilities = extractCapabilities(json);

        return new SidecarHandshake(sidecarId, type, priority, capabilities, version);
    }

    private String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private int extractInt(String json, String key, int defaultValue) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultValue;
    }

    private List<SidecarCapability> extractCapabilities(String json) {
        List<SidecarCapability> caps = new ArrayList<>();
        Pattern arrayPattern = Pattern.compile("\"capabilities\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher arrayMatcher = arrayPattern.matcher(json);
        if (arrayMatcher.find()) {
            String arrayContent = arrayMatcher.group(1);
            Pattern capPattern = Pattern.compile("\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"description\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}");
            Matcher capMatcher = capPattern.matcher(arrayContent);
            while (capMatcher.find()) {
                caps.add(new SidecarCapability(capMatcher.group(1), capMatcher.group(2)));
            }
        }
        return caps;
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ESTADO DE CAPACIDADES ===\n");
        sb.append("-".repeat(60)).append("\n");
        sb.append(String.format("%-20s | %-15s | %-8s | %s\n", "SKILL", "ORIGEN", "ESTADO", "TOOLS"));
        sb.append("-".repeat(60)).append("\n");

        for (SidecarInfo info : registeredSidecars.values()) {
            sb.append(String.format("%-20s | %-15s | %-8s | %d detectadas\n",
                info.type(),
                info.sidecarId(),
                "ONLINE",
                info.capabilities().size()
            ));
        }

        if (registeredSidecars.isEmpty()) {
            sb.append("(Sin sidecars conectados)\n");
        }

        sb.append("-".repeat(60)).append("\n");
        return sb.toString();
    }

    public record SidecarHandshake(
        String sidecarId,
        String type,
        int priority,
        List<SidecarCapability> capabilities,
        String version
    ) {}

    public record SidecarCapability(
        String name,
        String description
    ) {}

    public record SidecarInfo(
        String sidecarId,
        String type,
        int priority,
        List<SidecarCapability> capabilities,
        String version,
        Instant connectedAt
    ) {}

    public enum CapabilityEventType {
        SIDECAR_CONNECTED,
        SIDECAR_DISCONNECTED,
        CAPABILITY_ADDED,
        CAPABILITY_REMOVED
    }

    public record CapabilityEvent(
        CapabilityEventType type,
        String sidecarId,
        List<SidecarCapability> capabilities
    ) {}
}
