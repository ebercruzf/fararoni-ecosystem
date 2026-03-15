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

import dev.fararoni.bus.agent.api.DynamicSkill;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class McpProxySkill implements DynamicSkill {
    private static final Logger LOG = Logger.getLogger(McpProxySkill.class.getName());

    public static final String TOPIC_MCP_EXECUTE = "mcp.execute";

    public static final String TOPIC_MCP_RESPONSE_PREFIX = "mcp.response.";

    public static final String TOPIC_MCP_HEARTBEAT = "system.skills.online";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final long HEARTBEAT_TIMEOUT_MS = 10_000;

    private final SovereignEventBus bus;

    private volatile Instant lastHeartbeat = Instant.EPOCH;

    private final AtomicBoolean sidecarOnline = new AtomicBoolean(false);

    private final Map<String, CompletableFuture<String>> pendingResponses = new ConcurrentHashMap<>();

    public McpProxySkill(SovereignEventBus bus) {
        this.bus = bus;
        subscribeToHeartbeats();
    }

    private void subscribeToHeartbeats() {
        bus.subscribe(TOPIC_MCP_HEARTBEAT, String.class, envelope -> {
            String payload = envelope.payload();
            if (payload != null && payload.contains("MCP")) {
                lastHeartbeat = Instant.now();
                if (!sidecarOnline.get()) {
                    sidecarOnline.set(true);
                    LOG.info("[MCP-PROXY] Sidecar MCP detectado ONLINE");
                }
            }
        });

        bus.subscribe(TOPIC_MCP_RESPONSE_PREFIX + "*", String.class, envelope -> {
            String correlationId = envelope.correlationId();
            if (correlationId != null) {
                CompletableFuture<String> pending = pendingResponses.remove(correlationId);
                if (pending != null) {
                    pending.complete(envelope.payload());
                }
            }
        });
    }

    @Override
    public boolean checkHealth() {
        return !lastHeartbeat.equals(Instant.EPOCH) &&
               Duration.between(lastHeartbeat, Instant.now()).toMillis() < HEARTBEAT_TIMEOUT_MS;
    }

    @Override
    public void refreshAvailability() {
        boolean wasOnline = sidecarOnline.get();
        boolean isNowOnline = checkHealth();
        sidecarOnline.set(isNowOnline);

        if (wasOnline && !isNowOnline) {
            LOG.warning("[MCP-PROXY] Sidecar MCP -> OFFLINE (sin heartbeat)");
        } else if (!wasOnline && isNowOnline) {
            LOG.info("[MCP-PROXY] Sidecar MCP -> ONLINE");
        }
    }

    @Override
    public String getSidecarEndpoint() {
        return "bus://" + TOPIC_MCP_EXECUTE;
    }

    @Override
    public Instant getLastCheckTime() {
        return lastHeartbeat;
    }

    @Override
    public SidecarCategory getCategory() {
        return SidecarCategory.INTEGRATION;
    }

    @Override
    public boolean isAvailable() {
        return sidecarOnline.get();
    }

    @Override
    public String getSkillName() {
        return "MCP_EXTENDER";
    }

    @Override
    public String getDescription() {
        return "Proxy to MCP Sidecar for external integrations (Postgres, Slack, etc.)";
    }

    @Override
    public int getPriority() {
        return sidecarOnline.get() ? 100 : 0;
    }

    public Object execute(Map<String, Object> intent) {
        if (!sidecarOnline.get()) {
            LOG.warning("[MCP-PROXY] Sidecar MCP no disponible");
            return Map.of(
                "success", false,
                "error", "MCP Sidecar offline"
            );
        }

        try {
            String correlationId = UUID.randomUUID().toString();
            CompletableFuture<String> responseFuture = new CompletableFuture<>();
            pendingResponses.put(correlationId, responseFuture);

            String payload = serializeIntent(intent);
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "mcp-proxy",
                correlationId,
                payload
            ).withCorrelation(correlationId);

            bus.publish(TOPIC_MCP_EXECUTE, envelope);
            LOG.fine("[MCP-PROXY] Request enviado: " + correlationId);

            String response = responseFuture
                .orTimeout(REQUEST_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .join();

            return Map.of(
                "success", true,
                "response", response
            );
        } catch (Exception e) {
            LOG.warning("[MCP-PROXY] Error ejecutando request: " + e.getMessage());
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments) {
        return CompletableFuture.supplyAsync(() -> execute(Map.of(
            "method", "tools/call",
            "tool", toolName,
            "arguments", arguments
        )));
    }

    public CompletableFuture<Object> readResource(String uri) {
        return CompletableFuture.supplyAsync(() -> execute(Map.of(
            "method", "resources/read",
            "uri", uri
        )));
    }

    public CompletableFuture<Object> listTools() {
        return CompletableFuture.supplyAsync(() -> execute(Map.of(
            "method", "tools/list"
        )));
    }

    private String serializeIntent(Map<String, Object> intent) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : intent.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Map) {
                sb.append(serializeIntent((Map<String, Object>) value));
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
