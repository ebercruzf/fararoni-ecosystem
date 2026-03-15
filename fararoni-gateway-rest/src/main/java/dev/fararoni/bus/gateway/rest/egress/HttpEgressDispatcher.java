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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.gateway.rest.dto.ChannelEndpoint;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class HttpEgressDispatcher {

    private static final Logger LOG = Logger.getLogger(HttpEgressDispatcher.class.getName());

    /** Topic to subscribe for outbound messages */
    public static final String OUTPUT_TOPIC = "agency.output.main";

    /** Default HTTP timeout */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, ChannelEndpoint> routingTable;

    // Metrics
    private final AtomicLong totalDispatched = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final Map<String, AtomicLong> channelCounts = new ConcurrentHashMap<>();

    /**
     * Creates a new HttpEgressDispatcher.
     *
     * @param routingTable map of channelId to ChannelEndpoint
     */
    public HttpEgressDispatcher(Map<String, ChannelEndpoint> routingTable) {
        this.routingTable = new ConcurrentHashMap<>(routingTable != null ? routingTable : Map.of());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Creates a dispatcher with empty routing table.
     */
    public HttpEgressDispatcher() {
        this(Map.of());
    }

    /**
     * Dispatches a message to the appropriate Sidecar.
     *
     * <p>This method is designed to be called from a bus subscription.
     * It extracts the channel from headers and forwards to the configured
     * Sidecar endpoint.</p>
     *
     * @param envelope the envelope containing the response
     */
    public void dispatch(SovereignEnvelope<?> envelope) {
        // Check both headers for compatibility
        String channelId = envelope.headers().get("X-Origin-Protocol");
        if (channelId == null || channelId.isBlank()) {
            channelId = envelope.headers().get("X-Origin-Channel");
        }
        if (channelId == null || channelId.isBlank()) {
            LOG.warning("[GATEWAY-EGRESS] Missing X-Origin-Protocol header, cannot dispatch");
            return;
        }

        channelId = channelId.toLowerCase();
        ChannelEndpoint endpoint = routingTable.get(channelId);

        if (endpoint == null) {
            LOG.warning("[GATEWAY-EGRESS] No route configured for channel: " + channelId);
            return;
        }

        if (!endpoint.enabled()) {
            LOG.fine("[GATEWAY-EGRESS] Channel disabled: " + channelId);
            return;
        }

        String recipient = envelope.headers().get("X-Reply-Channel-Id");
        if (recipient == null) {
            recipient = envelope.headers().get("X-Sender-Id");
        }
        if (recipient == null || recipient.isBlank()) {
            LOG.warning("[GATEWAY-EGRESS] Missing recipient (X-Reply-Channel-Id), cannot dispatch");
            return;
        }

        String payload = envelope.payload() != null ? envelope.payload().toString() : "";
        String conversationId = envelope.headers().get("X-Conversation-Id");
        String messageType = envelope.headers().getOrDefault("X-Message-Type", "TEXT");

        // Build egress payload
        String jsonPayload;
        try {
            Map<String, Object> body = Map.of(
                "recipient", recipient,
                "message", payload,
                "conversationId", conversationId != null ? conversationId : "",
                "type", messageType
            );
            jsonPayload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[GATEWAY-EGRESS] Failed to serialize payload", e);
            return;
        }

        // Build HTTP request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint.egressUrl()))
            .timeout(Duration.ofMillis(endpoint.timeoutMs() > 0 ? endpoint.timeoutMs() : DEFAULT_TIMEOUT.toMillis()))
            .header("Content-Type", "application/json");

        // Add auth token if configured
        if (endpoint.authToken() != null && !endpoint.authToken().isBlank()) {
            String token = resolveToken(endpoint.authToken());
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        HttpRequest request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();

        // Fire async (non-blocking)
        String finalChannelId = channelId;
        String finalRecipient = recipient;

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .thenAccept(response -> {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    totalDispatched.incrementAndGet();
                    channelCounts.computeIfAbsent(finalChannelId, k -> new AtomicLong()).incrementAndGet();
                    LOG.info("[GATEWAY-EGRESS] Dispatched to " + finalChannelId + "/" +
                             truncate(finalRecipient, 20) + " (HTTP " + response.statusCode() + ")");
                } else {
                    totalFailed.incrementAndGet();
                    LOG.warning("[GATEWAY-EGRESS] Failed to dispatch to " + finalChannelId +
                                ": HTTP " + response.statusCode());
                }
            })
            .exceptionally(ex -> {
                totalFailed.incrementAndGet();
                LOG.log(Level.WARNING, "[GATEWAY-EGRESS] Error dispatching to " + finalChannelId +
                        "/" + endpoint.egressUrl(), ex);
                return null;
            });
    }

    /**
     * Registers a new channel endpoint.
     *
     * @param endpoint the channel endpoint to register
     */
    public void registerChannel(ChannelEndpoint endpoint) {
        routingTable.put(endpoint.channelId().toLowerCase(), endpoint);
        LOG.info("[GATEWAY-EGRESS] Registered channel: " + endpoint.channelId() +
                 " -> " + endpoint.egressUrl());
    }

    /**
     * Unregisters a channel endpoint.
     *
     * @param channelId the channel ID to unregister
     */
    public void unregisterChannel(String channelId) {
        ChannelEndpoint removed = routingTable.remove(channelId.toLowerCase());
        if (removed != null) {
            LOG.info("[GATEWAY-EGRESS] Unregistered channel: " + channelId);
        }
    }

    /**
     * Enables or disables a channel.
     *
     * @param channelId the channel ID
     * @param enabled   true to enable, false to disable
     */
    public void setChannelEnabled(String channelId, boolean enabled) {
        ChannelEndpoint endpoint = routingTable.get(channelId.toLowerCase());
        if (endpoint != null) {
            ChannelEndpoint updated = new ChannelEndpoint(
                endpoint.channelId(),
                enabled,
                endpoint.trustLevel(),
                endpoint.egressUrl(),
                endpoint.capabilities(),
                endpoint.timeoutMs(),
                endpoint.retryCount(),
                endpoint.authToken()
            );
            routingTable.put(channelId.toLowerCase(), updated);
            LOG.info("[GATEWAY-EGRESS] Channel " + channelId + " " + (enabled ? "enabled" : "disabled"));
        }
    }

    /**
     * Returns the number of registered channels.
     *
     * @return channel count
     */
    public int getChannelCount() {
        return routingTable.size();
    }

    /**
     * Returns total messages dispatched successfully.
     *
     * @return success count
     */
    public long getTotalDispatched() {
        return totalDispatched.get();
    }

    /**
     * Returns total failed dispatches.
     *
     * @return failure count
     */
    public long getTotalFailed() {
        return totalFailed.get();
    }

    /**
     * Returns dispatch count for a specific channel.
     *
     * @param channelId the channel ID
     * @return dispatch count for that channel
     */
    public long getChannelDispatchCount(String channelId) {
        AtomicLong count = channelCounts.get(channelId.toLowerCase());
        return count != null ? count.get() : 0;
    }

    /**
     * Shuts down the dispatcher.
     */
    public void shutdown() {
        LOG.info("[GATEWAY-EGRESS] Shutting down (dispatched: " + totalDispatched.get() +
                 ", failed: " + totalFailed.get() + ")");
    }

    /**
     * Resolves a token that may be an environment variable reference.
     */
    private String resolveToken(String token) {
        if (token.startsWith("env:")) {
            String envVar = token.substring(4);
            String value = System.getenv(envVar);
            return value != null ? value : "";
        }
        return token;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
