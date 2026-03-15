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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class SidecarBridgeSkill implements DynamicSkill {
    private static final Logger LOG = Logger.getLogger(SidecarBridgeSkill.class.getName());

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(500))
        .build();

    protected final String sidecarEndpoint;

    protected final String busTopic;

    protected final SidecarCategory category;

    private volatile boolean lastKnownStatus = false;

    private volatile Instant lastCheckTime = Instant.EPOCH;

    private volatile int consecutiveFailures = 0;

    private static final int FAILURE_THRESHOLD = 3;

    protected SidecarBridgeSkill(String sidecarEndpoint, String busTopic, SidecarCategory category) {
        this.sidecarEndpoint = sidecarEndpoint;
        this.busTopic = busTopic;
        this.category = category;
    }

    @Override
    public boolean checkHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sidecarEndpoint + "/health"))
                .timeout(getHealthCheckTimeout())
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            boolean healthy = response.statusCode() >= 200 && response.statusCode() < 300;

            if (healthy) {
                consecutiveFailures = 0;
                LOG.fine(() -> "[SIDECAR-BRIDGE] " + getSkillName() + " health check OK");
            } else {
                consecutiveFailures++;
                LOG.warning(() -> "[SIDECAR-BRIDGE] " + getSkillName() + " health check failed: HTTP " + response.statusCode());
            }

            return healthy;
        } catch (Exception e) {
            consecutiveFailures++;
            LOG.fine(() -> "[SIDECAR-BRIDGE] " + getSkillName() + " health check error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void refreshAvailability() {
        boolean wasAvailable = lastKnownStatus;
        lastKnownStatus = checkHealth();
        lastCheckTime = Instant.now();

        if (wasAvailable && !lastKnownStatus) {
            LOG.warning("[SIDECAR-BRIDGE] " + getSkillName() + " -> OFFLINE (" + sidecarEndpoint + ")");
        } else if (!wasAvailable && lastKnownStatus) {
            LOG.info("[SIDECAR-BRIDGE] " + getSkillName() + " -> ONLINE (" + sidecarEndpoint + ")");
        }
    }

    @Override
    public boolean isAvailable() {
        if (lastCheckTime.equals(Instant.EPOCH)) {
            refreshAvailability();
        }
        return lastKnownStatus;
    }

    @Override
    public String getSidecarEndpoint() {
        return sidecarEndpoint;
    }

    @Override
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }

    @Override
    public SidecarCategory getCategory() {
        return category;
    }

    public Object execute(Map<String, Object> intent) {
        if (!isAvailable()) {
            LOG.warning("[SIDECAR-BRIDGE] " + getSkillName() + " no disponible - rechazando ejecucion");
            return Map.of(
                "success", false,
                "error", "Sidecar no disponible: " + sidecarEndpoint
            );
        }

        try {
            Object payload = translateIntent(intent);

            return sendToSidecar(payload);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[SIDECAR-BRIDGE] Error ejecutando " + getSkillName(), e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    public CompletableFuture<Object> executeAsync(Map<String, Object> intent) {
        return CompletableFuture.supplyAsync(() -> execute(intent));
    }

    protected abstract Object translateIntent(Map<String, Object> intent);

    protected Object sendToSidecar(Object payload) throws Exception {
        String json = serializePayload(payload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(sidecarEndpoint + "/execute"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return Map.of(
                "success", true,
                "response", response.body()
            );
        } else {
            return Map.of(
                "success", false,
                "error", "HTTP " + response.statusCode() + ": " + response.body()
            );
        }
    }

    protected String serializePayload(Object payload) {
        if (payload instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> map = (Map<?, ?>) payload;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(escapeJson((String) value)).append("\"");
                } else if (value == null) {
                    sb.append("null");
                } else {
                    sb.append(value);
                }
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        return payload.toString();
    }

    private String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    @Override
    public String getDescription() {
        return "Sidecar bridge to " + sidecarEndpoint;
    }

    @Override
    public int getPriority() {
        return 50;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public String getBusTopic() {
        return busTopic;
    }
}
