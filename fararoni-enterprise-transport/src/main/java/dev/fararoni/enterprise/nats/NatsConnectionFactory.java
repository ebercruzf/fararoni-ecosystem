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

import dev.fararoni.enterprise.config.EnterpriseConfig;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class NatsConnectionFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NatsConnectionFactory.class);

    /**
     * Default subjects for JetStream stream.
     * Uses wildcards for flexible topic routing.
     */
    private static final List<String> DEFAULT_SUBJECTS = List.of(
        "skill.*",           // All skills (single token)
        "skill.>",           // All skill sub-hierarchies
        "agent.>",           // All agent topics
        "event.>",           // All events
        "sys.>",             // System topics (DLQ, heartbeat, etc.)
        "swarm.>",           // Swarm communication
        "agency.>"           // Agency/mission topics
    );

    private final EnterpriseConfig config;

    /**
     * Creates a factory with the given configuration.
     *
     * @param config Enterprise configuration
     */
    public NatsConnectionFactory(EnterpriseConfig config) {
        this.config = config;
    }

    /**
     * Creates a factory with default configuration from environment.
     */
    public NatsConnectionFactory() {
        this(EnterpriseConfig.fromEnvironment());
    }

    /**
     * Creates a new NATS connection.
     *
     * @return Connected NATS connection
     * @throws NatsConnectionException if connection fails
     */
    public Connection createConnection() {
        try {
            Options.Builder optionsBuilder = new Options.Builder()
                .server(config.getNatsUrl())
                .connectionTimeout(config.getNatsConnectionTimeout())
                .reconnectWait(config.getNatsReconnectWait())
                .maxReconnects(config.getNatsMaxReconnects())
                .connectionListener((conn, type) -> {
                    LOG.info("[NATS] Connection event: {}", type);
                })
                .errorListener(new NatsErrorListener());

            // Authentication
            if (config.getNatsToken() != null) {
                optionsBuilder.token(config.getNatsToken().toCharArray());
            } else if (config.getNatsUser() != null && config.getNatsPassword() != null) {
                optionsBuilder.userInfo(config.getNatsUser(), config.getNatsPassword());
            }

            Connection connection = Nats.connect(optionsBuilder.build());
            LOG.info("[NATS] Connected to: {}", config.getNatsUrl());

            return connection;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NatsConnectionException("Failed to connect to NATS: " + config.getNatsUrl(), e);
        }
    }

    /**
     * Gets or creates a JetStream instance with default stream configuration.
     *
     * @param connection Active NATS connection
     * @return JetStream instance
     * @throws NatsConnectionException if JetStream setup fails
     */
    public JetStream getJetStream(Connection connection) {
        try {
            if (config.isJetStreamEnabled()) {
                ensureStreamExists(connection);
            }
            return connection.jetStream();
        } catch (IOException e) {
            throw new NatsConnectionException("Failed to get JetStream", e);
        }
    }

    /**
     * Ensures the JetStream stream exists, creating it if necessary.
     *
     * @param connection Active NATS connection
     */
    private void ensureStreamExists(Connection connection) {
        try {
            JetStreamManagement jsm = connection.jetStreamManagement();

            // Check if stream exists
            try {
                StreamInfo info = jsm.getStreamInfo(config.getJetStreamName());
                LOG.info("[NATS] Stream '{}' exists with {} messages",
                    config.getJetStreamName(), info.getStreamState().getMsgCount());
                return;
            } catch (Exception e) {
                // Stream doesn't exist, create it
                LOG.info("[NATS] Creating stream: {}", config.getJetStreamName());
            }

            // Create stream configuration
            StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(config.getJetStreamName())
                .subjects(DEFAULT_SUBJECTS)
                .storageType(StorageType.File)
                .replicas(config.getJetStreamReplicas())
                .build();

            jsm.addStream(streamConfig);
            LOG.info("[NATS] Stream '{}' created with subjects: {}",
                config.getJetStreamName(), DEFAULT_SUBJECTS);

        } catch (Exception e) {
            LOG.warn("[NATS] Failed to ensure stream exists: {}. Continuing without JetStream persistence.",
                e.getMessage());
        }
    }

    /**
     * Gets the configuration.
     *
     * @return Enterprise configuration
     */
    public EnterpriseConfig getConfig() {
        return config;
    }

    /**
     * Exception thrown when NATS connection fails.
     */
    public static class NatsConnectionException extends RuntimeException {
        public NatsConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * NATS error listener for logging connection issues.
     */
    private static class NatsErrorListener implements io.nats.client.ErrorListener {
        @Override
        public void errorOccurred(Connection conn, String error) {
            LOG.error("[NATS] Error: {}", error);
        }

        @Override
        public void exceptionOccurred(Connection conn, Exception exp) {
            LOG.error("[NATS] Exception: {}", exp.getMessage(), exp);
        }

        @Override
        public void slowConsumerDetected(Connection conn, io.nats.client.Consumer consumer) {
            LOG.warn("[NATS] Slow consumer detected");
        }
    }
}
