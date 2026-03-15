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
package dev.fararoni.bus.gateway.rest;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.gateway.rest.dto.ChannelEndpoint;
import dev.fararoni.bus.gateway.rest.egress.HttpEgressDispatcher;
import dev.fararoni.bus.gateway.rest.egress.IntelliJEgressAdapter;
import dev.fararoni.bus.gateway.rest.ingress.RestIngressServer;
import dev.fararoni.bus.gateway.rest.security.RateLimiter;
import dev.fararoni.bus.spi.FararoniModule;
import dev.fararoni.bus.spi.ModuleContext;
import dev.fararoni.bus.spi.ModuleHealth;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class OmniChannelGatewayModule implements FararoniModule {

    private static final Logger LOG = Logger.getLogger(OmniChannelGatewayModule.class.getName());

    /** Module identifier */
    public static final String MODULE_ID = "gateway-rest-omnichannel";

    /** Output topic for egress subscription */
    private static final String OUTPUT_TOPIC = "agency.output.main";

    private SovereignEventBus bus;
    private ModuleContext context;
    private RestIngressServer ingressServer;
    private HttpEgressDispatcher egressDispatcher;
    private IntelliJEgressAdapter intellijEgressAdapter;  // [FASE 7.8.5]
    private RateLimiter rateLimiter;

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public int priority() {
        return 50; // Core infrastructure priority
    }

    @Override
    public String getDescription() {
        return "OmniChannel HTTP Gateway for external Sidecars (WhatsApp, Telegram, Slack, IoT)";
    }

    @Override
    public boolean prerequisitesMet(ModuleContext context) {
        // 1. Check system property first: -Dfararoni.gateway.enabled=false
        String sysPropEnabled = System.getProperty("fararoni.gateway.enabled");
        if ("false".equalsIgnoreCase(sysPropEnabled)) {
            LOG.info("[GATEWAY] Module disabled via -Dfararoni.gateway.enabled=false (Modo Headless/Obrero)");
            return false;
        }

        // 2. Check if gateway is enabled in config (modules.yml)
        boolean enabled = context.getBoolean("gateway.rest.enabled", true);
        if (!enabled) {
            LOG.info("[GATEWAY] Module disabled in configuration");
            return false;
        }

        // 3. Check if bus is available
        if (context.bus() == null) {
            LOG.warning("[GATEWAY] SovereignEventBus not available");
            return false;
        }

        return true;
    }

    @Override
    public void initialize(ModuleContext context) {
        this.context = context;
        this.bus = context.bus();

        LOG.info("[GATEWAY] Initializing OmniChannel Gateway Module...");

        // Load configuration - System property has priority over config file
        int port = resolvePort(context);
        String apiToken = context.getString("gateway.rest.security.api_token");
        String metaVerifyToken = context.getString("gateway.meta.verify_token");
        String adminToken = context.getString("gateway.rest.security.admin_token");
        int rateLimitCapacity = context.getInt("gateway.rest.limits.rate_limit_capacity", RateLimiter.DEFAULT_CAPACITY);
        int rateLimitRefill = context.getInt("gateway.rest.limits.rate_limit_refill", RateLimiter.DEFAULT_REFILL_RATE);

        // Initialize rate limiter
        this.rateLimiter = new RateLimiter(rateLimitCapacity, rateLimitRefill);

        // Initialize ingress server with admin token
        this.ingressServer = new RestIngressServer(bus, port, apiToken, metaVerifyToken, adminToken, rateLimiter);

        // Initialize egress dispatcher
        this.egressDispatcher = new HttpEgressDispatcher();

        // [FASE 7.8.5] Initialize IntelliJ egress adapter
        this.intellijEgressAdapter = new IntelliJEgressAdapter(bus);

        // Load channel configurations
        loadChannelConfigurations();

        LOG.info("[GATEWAY] Module initialized (port: " + port + ", rate limit: " +
                 rateLimitCapacity + "/" + rateLimitRefill + "/sec)");
        LOG.info("[GATEWAY] Admin endpoints: " + (adminToken != null ? "SECURED" : "DEV MODE (no token)"));
    }

    @Override
    public void start() {
        LOG.info("[GATEWAY] Starting OmniChannel Gateway Module...");

        // 1. Start ingress server
        ingressServer.start();

        // 2. Subscribe to output topic for egress dispatch
        bus.subscribe(OUTPUT_TOPIC, String.class, this::onOutputMessage);

        // 3. [FASE 7.8.5] Start IntelliJ egress adapter
        if (intellijEgressAdapter != null) {
            intellijEgressAdapter.start();
            LOG.info("[GATEWAY] IntelliJ Egress Adapter started (callback-based delivery)");
        }

        LOG.info("[GATEWAY] Module started successfully");
        LOG.info("[GATEWAY] Ingress: http://localhost:" + ingressServer.getPort() + "/gateway/v1/inbound");
        LOG.info("[GATEWAY] Health:  http://localhost:" + ingressServer.getPort() + "/gateway/v1/health");
        LOG.info("[GATEWAY] Channels configured: " + egressDispatcher.getChannelCount());
    }

    @Override
    public void stop() {
        LOG.info("[GATEWAY] Stopping OmniChannel Gateway Module...");

        // Stop ingress server
        if (ingressServer != null) {
            ingressServer.stop();
        }

        // Shutdown egress dispatcher
        if (egressDispatcher != null) {
            egressDispatcher.shutdown();
        }

        // [FASE 7.8.5] Stop IntelliJ egress adapter
        if (intellijEgressAdapter != null) {
            intellijEgressAdapter.stop();
        }

        LOG.info("[GATEWAY] Module stopped");
    }

    @Override
    public ModuleHealth getHealth() {
        if (ingressServer == null || !ingressServer.isRunning()) {
            return ModuleHealth.FAILED;
        }

        // Check if rate limiter is heavily throttling
        if (rateLimiter.getThrottleRate() > 0.5) {
            return ModuleHealth.DEGRADED;
        }

        return ModuleHealth.HEALTHY;
    }

    /**
     * Handles outbound messages from the bus.
     */
    private void onOutputMessage(SovereignEnvelope<String> envelope) {
        egressDispatcher.dispatch(envelope);
    }

    /**
     * Loads channel configurations from modules.yml.
     */
    @SuppressWarnings("unchecked")
    private void loadChannelConfigurations() {
        Map<String, Object> channels = context.getMap("channels");
        if (channels.isEmpty()) {
            LOG.info("[GATEWAY] No channels configured in modules.yml");
            return;
        }

        for (Map.Entry<String, Object> entry : channels.entrySet()) {
            String channelId = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }

            Map<String, Object> channelConfig = (Map<String, Object>) entry.getValue();

            boolean enabled = getBoolean(channelConfig, "enabled", true);
            String egressUrl = getString(channelConfig, "egress_url", null);
            String trustLevelStr = getString(channelConfig, "trust_level", "UNTRUSTED_EXTERNAL");
            List<String> capabilities = getList(channelConfig, "capabilities");
            int timeoutMs = getInt(channelConfig, "timeout_ms", ChannelEndpoint.DEFAULT_TIMEOUT_MS);
            int retryCount = getInt(channelConfig, "retry_count", ChannelEndpoint.DEFAULT_RETRY_COUNT);
            String authToken = getString(channelConfig, "auth_token", null);

            if (egressUrl == null || egressUrl.isBlank()) {
                LOG.warning("[GATEWAY] Channel " + channelId + " has no egress_url, skipping");
                continue;
            }

            ChannelEndpoint.TrustLevel trustLevel;
            try {
                trustLevel = ChannelEndpoint.TrustLevel.valueOf(trustLevelStr);
            } catch (IllegalArgumentException e) {
                trustLevel = ChannelEndpoint.TrustLevel.UNTRUSTED_EXTERNAL;
            }

            ChannelEndpoint endpoint = new ChannelEndpoint(
                channelId,
                enabled,
                trustLevel,
                egressUrl,
                capabilities,
                timeoutMs,
                retryCount,
                authToken
            );

            egressDispatcher.registerChannel(endpoint);
        }
    }

    // Helper methods for type-safe config access
    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        return defaultValue;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value instanceof String s) return s;
        return defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of("text");
    }

    /**
     * Resolves Gateway port with priority:
     * 1. System property: -Dfararoni.gateway.port=7072
     * 2. Config file: gateway.rest.port in modules.yml
     * 3. Default: 7071
     */
    private int resolvePort(ModuleContext context) {
        // 1. System property has highest priority
        String sysPropPort = System.getProperty("fararoni.gateway.port");
        if (sysPropPort != null && !sysPropPort.isBlank()) {
            try {
                int port = Integer.parseInt(sysPropPort.trim());
                if (port > 0 && port <= 65535) {
                    LOG.info("[GATEWAY] Using port from -Dfararoni.gateway.port: " + port);
                    return port;
                }
            } catch (NumberFormatException e) {
                LOG.warning("[GATEWAY] Invalid port in -Dfararoni.gateway.port: " + sysPropPort);
            }
        }

        // 2. Config file
        return context.getInt("gateway.rest.port", RestIngressServer.DEFAULT_PORT);
    }
}
