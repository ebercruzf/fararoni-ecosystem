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
package dev.fararoni.enterprise.nats;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.enterprise.config.EnterpriseConfig;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.impl.NatsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class NatsSovereignBus implements SovereignEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(NatsSovereignBus.class);

    /** SPI Priority - Higher than InMemorySovereignBus (0) */
    private static final int PRIORITY = 100;

    /** Queue group name for load balancing across instances */
    private static final String QUEUE_GROUP = "fararoni-workers";

    /** Topic for Dead Letter Queue */
    public static final String DLQ_TOPIC = "sys.dlq.main";

    /** Topic priorities for load shedding (0=critical, 3=low) */
    private static final Map<String, Integer> TOPIC_PRIORITIES = Map.of(
        "agency.mission.start", 0,
        "agency.mission.resume", 0,
        "swarm.result", 1,
        "swarm.task", 1,
        "swarm.compensation", 1,
        "swarm.telemetry", 3,
        "sys.heartbeat", 2,
        "sys.dlq", 0
    );

    private final EnterpriseConfig config;
    private final NatsConnectionFactory connectionFactory;
    private final NatsSerializer serializer;

    private Connection connection;
    private JetStream jetStream;
    private final Map<String, Dispatcher> dispatchers;
    private final Map<String, JetStreamSubscription> jsSubscriptions;
    private final AtomicLong inFlightCount;
    private final AtomicBoolean running;

    /** Flag for lazy initialization */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Creates a NatsSovereignBus with default configuration from environment.
     * Connection is established lazily on first use.
     */
    public NatsSovereignBus() {
        this(EnterpriseConfig.fromEnvironment());
    }

    /**
     * Creates a NatsSovereignBus with the given configuration.
     * Connection is established lazily on first use.
     *
     * @param config Enterprise configuration
     */
    public NatsSovereignBus(EnterpriseConfig config) {
        this.config = config;
        this.connectionFactory = new NatsConnectionFactory(config);
        this.serializer = new NatsSerializer();
        this.dispatchers = new ConcurrentHashMap<>();
        this.jsSubscriptions = new ConcurrentHashMap<>();
        this.inFlightCount = new AtomicLong(0);
        this.running = new AtomicBoolean(false);

        // Lazy init - don't connect until first use
        LOG.info("[NATS-BUS] Created (lazy-init) - URL: {}", config.getNatsUrl());
    }

    /**
     * Ensures the NATS connection is initialized (lazy).
     * Called before any operation that requires the connection.
     */
    private synchronized void ensureInitialized() {
        if (initialized.get()) {
            return;
        }

        try {
            this.connection = connectionFactory.createConnection();
            this.jetStream = connectionFactory.getJetStream(connection);
            this.running.set(true);
            this.initialized.set(true);

            LOG.info("[NATS-BUS] Connected - URL: {}, JetStream: {}",
                config.getNatsUrl(), config.isJetStreamEnabled());

        } catch (Exception e) {
            LOG.error("[NATS-BUS] Failed to connect: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to NATS: " + config.getNatsUrl(), e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> publish(String topic, SovereignEnvelope<T> envelope) {
        ensureInitialized();

        if (!running.get()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Bus is shutting down"));
        }

        return CompletableFuture.runAsync(() -> {
            try {
                inFlightCount.incrementAndGet();

                // Generate final idempotency key if temporary
                SovereignEnvelope<T> finalEnvelope = envelope;
                if (envelope.hasTemporaryIdempotencyKey()) {
                    finalEnvelope = envelope.withFinalIdempotencyKey(topic);
                }

                // Serialize envelope
                byte[] data = serializer.serialize(finalEnvelope);

                // Publish to NATS (JetStream if enabled, Core otherwise)
                if (config.isJetStreamEnabled() && jetStream != null) {
                    try {
                        jetStream.publish(topic, data);
                    } catch (Exception jsEx) {
                        // Fallback to Core NATS if JetStream fails
                        LOG.debug("[NATS-BUS] JetStream publish failed, falling back to Core NATS: {}", topic);
                        connection.publish(topic, data);
                    }
                } else {
                    connection.publish(topic, data);
                }

                LOG.debug("[NATS-BUS] Published to {}: {}", topic, finalEnvelope.id());

            } catch (Exception e) {
                LOG.error("[NATS-BUS] Failed to publish to {}: {}", topic, e.getMessage(), e);
                throw new RuntimeException("Failed to publish message", e);
            } finally {
                inFlightCount.decrementAndGet();
            }
        });
    }

    @Override
    public <T, R> CompletableFuture<R> request(
            String topic,
            SovereignEnvelope<T> envelope,
            Class<R> responseType,
            Duration timeout) {

        ensureInitialized();

        if (!running.get()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Bus is shutting down"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                inFlightCount.incrementAndGet();

                // Serialize request
                byte[] requestData = serializer.serialize(envelope);

                // Native NATS request/reply
                Message response = connection.request(topic, requestData, timeout);

                if (response == null) {
                    throw new RuntimeException("Request timeout: " + topic);
                }

                // Deserialize response
                return serializer.deserializeResponse(response.getData(), responseType);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", e);
            } catch (Exception e) {
                LOG.error("[NATS-BUS] Request failed to {}: {}", topic, e.getMessage(), e);
                throw new RuntimeException("Request failed", e);
            } finally {
                inFlightCount.decrementAndGet();
            }
        });
    }

    @Override
    public <T> void subscribe(String topic, Class<T> payloadType, Consumer<SovereignEnvelope<T>> consumer) {
        ensureInitialized();

        if (!running.get()) {
            throw new IllegalStateException("Bus is shutting down");
        }

        // Create message handler (shared between JetStream and Core NATS)
        MessageHandler messageHandler = msg -> {
            try {
                inFlightCount.incrementAndGet();

                // Deserialize envelope
                SovereignEnvelope<T> envelope = serializer.deserialize(msg.getData(), payloadType);

                // Check TTL
                if (envelope.isExpired()) {
                    LOG.warn("[NATS-BUS] Message expired (TTL): {}", envelope.id());
                    sendToDeadLetter(topic, envelope, "Expired (TTL)");
                    return;
                }

                // Check max retries
                if (envelope.isMaxRetriesExceeded()) {
                    LOG.warn("[NATS-BUS] Max retries exceeded: {}", envelope.id());
                    sendToDeadLetter(topic, envelope, "Max retries exceeded");
                    return;
                }

                // Check max hops (anti-loop)
                if (envelope.isMaxHopsExceeded()) {
                    LOG.error("[NATS-BUS] Max hops exceeded (anti-loop): {}", envelope.id());
                    sendToDeadLetter(topic, envelope, "Max hops exceeded - possible infinite loop");
                    return;
                }

                // Process message
                consumer.accept(envelope);

                LOG.debug("[NATS-BUS] Processed message from {}: {}", msg.getSubject(), envelope.id());

            } catch (Exception e) {
                LOG.error("[NATS-BUS] Error processing message from {}: {}",
                    msg.getSubject(), e.getMessage(), e);
            } finally {
                inFlightCount.decrementAndGet();
            }
        };

        // Bifurcate: JetStream vs Core NATS
        if (config.isJetStreamEnabled() && jetStream != null) {
            subscribeWithJetStream(topic, messageHandler);
        } else {
            subscribeWithCoreNats(topic, messageHandler);
        }
    }

    /**
     * Subscribes using JetStream for guaranteed message delivery.
     * Messages are persisted and can be replayed if consumer is offline.
     *
     * @param topic Topic to subscribe to
     * @param handler Message handler
     */
    private void subscribeWithJetStream(String topic, MessageHandler handler) {
        try {
            // Create durable consumer name from topic (replace invalid chars)
            String durableName = "fararoni-" + topic.replace(".", "-").replace(">", "all").replace("*", "any");

            // Configure consumer for queue group load balancing
            ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                .durable(durableName)
                .deliverGroup(QUEUE_GROUP)
                .build();

            PushSubscribeOptions options = PushSubscribeOptions.builder()
                .configuration(consumerConfig)
                .build();

            // Subscribe using JetStream API
            JetStreamSubscription subscription = jetStream.subscribe(
                topic,
                QUEUE_GROUP,
                connection.createDispatcher(),
                handler,
                false,  // autoAck
                options
            );

            jsSubscriptions.put(topic, subscription);
            LOG.info("[NATS-JS] Subscribed with persistence to: {} (durable: {}, queue: {})",
                topic, durableName, QUEUE_GROUP);

        } catch (Exception e) {
            LOG.warn("[NATS-JS] JetStream subscription failed for {}: {}. Falling back to Core NATS.",
                topic, e.getMessage());
            // Fallback to Core NATS if JetStream fails
            subscribeWithCoreNats(topic, handler);
        }
    }

    /**
     * Subscribes using Core NATS for fast fire-and-forget messaging.
     * No persistence - messages are lost if consumer is offline.
     *
     * @param topic Topic to subscribe to
     * @param handler Message handler
     */
    private void subscribeWithCoreNats(String topic, MessageHandler handler) {
        // Create dispatcher for this topic (supports wildcards: *, >)
        Dispatcher dispatcher = connection.createDispatcher(handler);

        // Subscribe with Queue Group for load balancing
        dispatcher.subscribe(topic, QUEUE_GROUP);
        dispatchers.put(topic, dispatcher);

        LOG.info("[NATS-CORE] Subscribed to: {} (queue: {})", topic, QUEUE_GROUP);
    }

    /**
     * Sends a failed message to the Dead Letter Queue.
     *
     * @param originalTopic Original topic
     * @param envelope Failed envelope
     * @param reason Failure reason
     */
    private <T> void sendToDeadLetter(String originalTopic, SovereignEnvelope<T> envelope, String reason) {
        try {
            // Create DLQ envelope with failure metadata
            var dlqEnvelope = envelope
                .withHeader("dlq.original_topic", originalTopic)
                .withHeader("dlq.reason", reason)
                .withHeader("dlq.timestamp", java.time.Instant.now().toString());

            byte[] data = serializer.serialize(dlqEnvelope);
            connection.publish(DLQ_TOPIC, data);

            LOG.warn("[NATS-BUS] Sent to DLQ: {} (reason: {})", envelope.id(), reason);

        } catch (Exception e) {
            LOG.error("[NATS-BUS] Failed to send to DLQ: {}", e.getMessage(), e);
        }
    }

    @Override
    public long getInFlightCount() {
        return inFlightCount.get();
    }

    @Override
    public boolean isHealthy() {
        return running.get() &&
               connection != null &&
               connection.getStatus() == Connection.Status.CONNECTED;
    }

    /**
     * Gets the topic priority for load shedding.
     *
     * @param topic Topic name
     * @return Priority (0=critical, 3=low)
     */
    public int getTopicPriority(String topic) {
        Integer exact = TOPIC_PRIORITIES.get(topic);
        if (exact != null) {
            return exact;
        }

        // Check prefixes
        for (Map.Entry<String, Integer> entry : TOPIC_PRIORITIES.entrySet()) {
            if (topic.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return 1; // Default priority
    }

    @Override
    public void shutdown(Duration timeout) {
        LOG.info("[NATS-BUS] Shutting down...");
        running.set(false);

        // Close all JetStream subscriptions
        jsSubscriptions.forEach((topic, sub) -> {
            try {
                sub.unsubscribe();
            } catch (Exception e) {
                LOG.warn("[NATS-JS] Error closing subscription for {}: {}", topic, e.getMessage());
            }
        });
        jsSubscriptions.clear();

        // Close all Core NATS dispatchers
        dispatchers.forEach((topic, d) -> {
            try {
                d.unsubscribe(topic);
            } catch (Exception e) {
                LOG.warn("[NATS-CORE] Error closing dispatcher for {}: {}", topic, e.getMessage());
            }
        });
        dispatchers.clear();

        // Close connection
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LOG.info("[NATS-BUS] Shutdown complete");
    }

    /**
     * Returns SPI priority (100) to override InMemorySovereignBus (0).
     *
     * @return Priority level (higher = preferred)
     */
    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /**
     * Gets the underlying NATS connection for advanced usage.
     *
     * @return NATS connection
     */
    public Connection getNatsConnection() {
        return connection;
    }

    /**
     * Gets the JetStream instance for advanced usage.
     *
     * @return JetStream instance (may be null if disabled)
     */
    public JetStream getJetStream() {
        return jetStream;
    }
}
