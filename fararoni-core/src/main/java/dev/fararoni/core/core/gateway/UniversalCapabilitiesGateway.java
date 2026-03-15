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
import dev.fararoni.bus.agent.api.security.SecurityInspector;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class UniversalCapabilitiesGateway {
    private static final Logger LOG = Logger.getLogger(UniversalCapabilitiesGateway.class.getName());

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final SovereignEventBus bus;
    private final SecurityInspector security;
    private final Map<String, CircuitBreaker> breakers;

    public UniversalCapabilitiesGateway(SovereignEventBus bus, SecurityInspector security) {
        this.bus = bus;
        this.security = security;
        this.breakers = new ConcurrentHashMap<>();
    }

    public UniversalCapabilitiesGateway(SovereignEventBus bus) {
        this(bus, null);
    }

    @SuppressWarnings("unchecked")
    public <T, R> CompletableFuture<R> executeCapability(
            String agentId,
            String capability,
            T payload) {
        return executeCapability(agentId, capability, payload, (Class<R>) Object.class, DEFAULT_TIMEOUT);
    }

    public <T, R> CompletableFuture<R> executeCapability(
            String agentId,
            String capability,
            T payload,
            Class<R> responseType,
            Duration timeout) {
        String topic = "skill." + capability;

        if (security != null) {
            var envelope = SovereignEnvelope.create(agentId, payload);
            return security.inspect(topic, envelope)
                .thenCompose(v -> doExecute(agentId, topic, payload, responseType, timeout));
        }

        return doExecute(agentId, topic, payload, responseType, timeout);
    }

    private <T, R> CompletableFuture<R> doExecute(
            String agentId,
            String topic,
            T payload,
            Class<R> responseType,
            Duration timeout) {
        CircuitBreaker breaker = breakers.computeIfAbsent(topic,
            k -> CircuitBreaker.Factory.standard()
        );

        if (breaker.isOpen()) {
            LOG.warning("[GATEWAY] Circuit breaker ABIERTO para: " + topic);
            return CompletableFuture.failedFuture(
                new ServiceUnavailableException("Circuito abierto: " + topic)
            );
        }

        var envelope = SovereignEnvelope.create(agentId, payload);

        return bus.request(topic, envelope, responseType, timeout)
            .handle((result, ex) -> {
                if (ex != null) {
                    breaker.recordFailure(ex.getMessage());
                    LOG.warning("[GATEWAY] Fallo en " + topic + ": " + ex.getMessage());
                    throw new CompletionException(ex);
                }

                breaker.recordSuccess();
                LOG.fine("[GATEWAY] Exito en " + topic);
                return result;
            });
    }

    public <T> CompletableFuture<Void> fireAndForget(
            String agentId,
            String capability,
            T payload) {
        String topic = "skill." + capability;

        if (security != null) {
            var envelope = SovereignEnvelope.create(agentId, payload);
            return security.inspect(topic, envelope)
                .thenCompose(v -> {
                    var e = SovereignEnvelope.create(agentId, payload);
                    return bus.publish(topic, e);
                });
        }

        var envelope = SovereignEnvelope.create(agentId, payload);
        return bus.publish(topic, envelope);
    }

    public CircuitBreaker getCircuitBreaker(String capability) {
        return breakers.get("skill." + capability);
    }

    public void resetCircuitBreaker(String capability) {
        CircuitBreaker breaker = breakers.get("skill." + capability);
        if (breaker != null) {
            breaker.reset();
            LOG.info("[GATEWAY] Circuit breaker reiniciado para: " + capability);
        }
    }

    public Map<String, CircuitBreaker.State> getAllCircuitStates() {
        Map<String, CircuitBreaker.State> states = new ConcurrentHashMap<>();
        breakers.forEach((topic, breaker) ->
            states.put(topic.replace("skill.", ""), breaker.getState())
        );
        return states;
    }

    public boolean isHealthy() {
        if (!bus.isHealthy()) {
            return false;
        }

        long openBreakers = breakers.values().stream()
            .filter(b -> b.getState() == CircuitBreaker.State.OPEN)
            .count();

        return openBreakers < (breakers.size() / 2.0);
    }

    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) {
            super(message);
        }
    }
}
