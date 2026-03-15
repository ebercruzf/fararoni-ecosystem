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
package dev.fararoni.core.core.sati;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @since 1.0.0
 */
public class SATIRouter {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SATIRouter.class);

    private final Map<String, SidecarNode> registry = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SATI_REGISTRY_TOPIC = "sati.registry";
    private static final String LEGACY_REGISTRY_TOPIC = "fararoni.registry.updates";

    public SATIRouter(SovereignEventBus bus) {
        subscribeToRegistry(bus);
        LOG.info("[SATI] Router inicializado. Escuchando en '{}'", SATI_REGISTRY_TOPIC);
    }

    public SATIRouter() {
        LOG.info("[SATI] Router inicializado en modo standalone");
    }

    private void subscribeToRegistry(SovereignEventBus bus) {
        bus.subscribe(SATI_REGISTRY_TOPIC, String.class, envelope -> {
            try {
                JsonNode node = mapper.readTree(envelope.payload());
                processHeartbeat(node);
            } catch (Exception e) {
                LOG.warn("[SATI] Error procesando heartbeat: {}", e.getMessage());
            }
        });

        bus.subscribe(LEGACY_REGISTRY_TOPIC, String.class, envelope -> {
            try {
                JsonNode node = mapper.readTree(envelope.payload());
                processHeartbeat(node);
            } catch (Exception e) {
                LOG.debug("[SATI] Error procesando heartbeat legacy: {}", e.getMessage());
            }
        });
    }

    public void processHeartbeat(String heartbeatJson) {
        try {
            JsonNode node = mapper.readTree(heartbeatJson);
            processHeartbeat(node);
        } catch (Exception e) {
            LOG.warn("[SATI] Error parseando heartbeat: {}", e.getMessage());
        }
    }

    private void processHeartbeat(JsonNode node) {
        String id = node.has("sidecar_id") ? node.get("sidecar_id").asText() :
                    node.has("id") ? node.get("id").asText() : "unknown";
        String type = node.has("type") ? node.get("type").asText() : "UNKNOWN";
        String target = node.has("target") ? node.get("target").asText() : "";
        int port = node.has("port") ? node.get("port").asInt() : 0;
        int priority = node.has("priority") ? node.get("priority").asInt() : 50;
        String status = node.has("status") ? node.get("status").asText() : "UNKNOWN";

        long latencyUs = -1;
        double loadFactor = 0.5;
        int activeRequests = 0;
        long totalRequests = 0;
        long totalErrors = 0;

        if (node.has("metrics")) {
            JsonNode metrics = node.get("metrics");
            latencyUs = metrics.has("latency_us") ? metrics.get("latency_us").asLong() : -1;
            loadFactor = metrics.has("load_factor") ? metrics.get("load_factor").asDouble() : 0.5;
            activeRequests = metrics.has("active_requests") ? metrics.get("active_requests").asInt() : 0;
            totalRequests = metrics.has("total_requests") ? metrics.get("total_requests").asLong() : 0;
            totalErrors = metrics.has("total_errors") ? metrics.get("total_errors").asLong() : 0;
        }

        SidecarNode sidecar = new SidecarNode(
            id, type, target, port, priority,
            latencyUs, System.currentTimeMillis(), status,
            loadFactor, activeRequests, totalRequests, totalErrors
        );

        registry.put(id, sidecar);
        LOG.debug("[SATI] Nodo {} actualizado: latency={}us, status={}, score={:.0f}",
            id, latencyUs, status, sidecar.getScore());
    }

    public Optional<String> getBestSidecar() {
        return registry.values().stream()
                .filter(SidecarNode::isHealthy)
                .max(Comparator.comparingDouble(SidecarNode::getScore))
                .map(SidecarNode::id);
    }

    public Optional<String> getBestSidecarByType(String type) {
        return registry.values().stream()
                .filter(SidecarNode::isHealthy)
                .filter(s -> type.equals(s.type()))
                .max(Comparator.comparingDouble(SidecarNode::getScore))
                .map(SidecarNode::id);
    }

    public List<String> getAvailableSidecars() {
        return registry.values().stream()
                .filter(SidecarNode::isAvailable)
                .sorted(Comparator.comparingDouble(SidecarNode::getScore).reversed())
                .map(SidecarNode::id)
                .collect(Collectors.toList());
    }

    public Optional<SidecarNode> getSidecar(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public int getTotalSidecars() {
        return registry.size();
    }

    public int getHealthySidecars() {
        return (int) registry.values().stream().filter(SidecarNode::isHealthy).count();
    }

    public void printSwarmStatus() {
        System.out.println("\n========================================");
        System.out.println("  ESTADO DEL ENJAMBRE S.A.T.I.");
        System.out.println("========================================");
        System.out.printf("  Total: %d | Saludables: %d%n", getTotalSidecars(), getHealthySidecars());
        System.out.println("----------------------------------------");

        registry.values().stream()
            .sorted(Comparator.comparingDouble(SidecarNode::getScore).reversed())
            .forEach(s -> {
                String healthIcon = s.isHealthy() ? "[OK]" : s.isAvailable() ? "[!!]" : "[XX]";
                System.out.printf("  %s %-20s | P:%3d | L:%6dus | Load:%.2f | %s%n",
                    healthIcon, s.id(), s.priority(), s.lastLatencyUs(),
                    s.loadFactor(), s.status());
            });

        System.out.println("========================================\n");
    }

    public Map<String, Object> getSwarmStatusMap() {
        List<Map<String, Object>> nodes = registry.values().stream()
            .sorted(Comparator.comparingDouble(SidecarNode::getScore).reversed())
            .map(s -> Map.<String, Object>of(
                "id", s.id(),
                "type", s.type(),
                "priority", s.priority(),
                "latency_us", s.lastLatencyUs(),
                "status", s.status(),
                "healthy", s.isHealthy(),
                "score", s.getScore(),
                "age_ms", s.getAgeMs()
            ))
            .collect(Collectors.toList());

        return Map.of(
            "total", getTotalSidecars(),
            "healthy", getHealthySidecars(),
            "nodes", nodes,
            "timestamp", System.currentTimeMillis()
        );
    }

    public void pruneStaleNodes() {
        long staleThreshold = System.currentTimeMillis() - 60_000;

        List<String> toRemove = registry.entrySet().stream()
            .filter(e -> e.getValue().lastSeen() < staleThreshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        toRemove.forEach(id -> {
            registry.remove(id);
            LOG.info("[SATI] Nodo {} eliminado por inactividad", id);
        });
    }
}
