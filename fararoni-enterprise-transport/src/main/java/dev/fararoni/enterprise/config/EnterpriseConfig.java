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
package dev.fararoni.enterprise.config;

import java.time.Duration;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class EnterpriseConfig {

    private final String natsUrl;
    private final String natsUser;
    private final String natsPassword;
    private final String natsToken;
    private final Duration natsConnectionTimeout;
    private final Duration natsReconnectWait;
    private final int natsMaxReconnects;

    private final boolean jetStreamEnabled;
    private final String jetStreamName;
    private final int jetStreamReplicas;

    private final String redisUrl;
    private final Duration idempotencyTtl;
    private final int idempotencyMaxSize;

    private EnterpriseConfig(Builder builder) {
        this.natsUrl = builder.natsUrl;
        this.natsUser = builder.natsUser;
        this.natsPassword = builder.natsPassword;
        this.natsToken = builder.natsToken;
        this.natsConnectionTimeout = builder.natsConnectionTimeout;
        this.natsReconnectWait = builder.natsReconnectWait;
        this.natsMaxReconnects = builder.natsMaxReconnects;
        this.jetStreamEnabled = builder.jetStreamEnabled;
        this.jetStreamName = builder.jetStreamName;
        this.jetStreamReplicas = builder.jetStreamReplicas;
        this.redisUrl = builder.redisUrl;
        this.idempotencyTtl = builder.idempotencyTtl;
        this.idempotencyMaxSize = builder.idempotencyMaxSize;
    }

    /**
     * Creates a configuration from environment variables.
     *
     * @return Configuration loaded from environment
     */
    public static EnterpriseConfig fromEnvironment() {
        return builder()
            .natsUrl(env("NATS_URL", "nats://localhost:4222"))
            .natsUser(env("NATS_USER", null))
            .natsPassword(env("NATS_PASSWORD", null))
            .natsToken(env("NATS_TOKEN", null))
            .jetStreamEnabled(Boolean.parseBoolean(env("JETSTREAM_ENABLED", "true")))
            .jetStreamName(env("JETSTREAM_NAME", "fararoni-events"))
            .jetStreamReplicas(Integer.parseInt(env("JETSTREAM_REPLICAS", "1")))
            .redisUrl(env("REDIS_URL", "redis://localhost:6379"))
            .build();
    }

    /**
     * Creates a default configuration for local development.
     *
     * @return Default local configuration
     */
    public static EnterpriseConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    public String getNatsUrl() { return natsUrl; }
    public String getNatsUser() { return natsUser; }
    public String getNatsPassword() { return natsPassword; }
    public String getNatsToken() { return natsToken; }
    public Duration getNatsConnectionTimeout() { return natsConnectionTimeout; }
    public Duration getNatsReconnectWait() { return natsReconnectWait; }
    public int getNatsMaxReconnects() { return natsMaxReconnects; }
    public boolean isJetStreamEnabled() { return jetStreamEnabled; }
    public String getJetStreamName() { return jetStreamName; }
    public int getJetStreamReplicas() { return jetStreamReplicas; }
    public String getRedisUrl() { return redisUrl; }
    public Duration getIdempotencyTtl() { return idempotencyTtl; }
    public int getIdempotencyMaxSize() { return idempotencyMaxSize; }

    public boolean hasNatsCredentials() {
        return (natsUser != null && natsPassword != null) || natsToken != null;
    }

    /**
     * Builder for EnterpriseConfig.
     */
    public static class Builder {
        private String natsUrl = "nats://localhost:4222";
        private String natsUser;
        private String natsPassword;
        private String natsToken;
        private Duration natsConnectionTimeout = Duration.ofSeconds(5);
        private Duration natsReconnectWait = Duration.ofSeconds(2);
        private int natsMaxReconnects = 60;
        private boolean jetStreamEnabled = true;
        private String jetStreamName = "fararoni-events";
        private int jetStreamReplicas = 1;
        private String redisUrl = "redis://localhost:6379";
        private Duration idempotencyTtl = Duration.ofMinutes(5);
        private int idempotencyMaxSize = 50_000;

        public Builder natsUrl(String natsUrl) {
            this.natsUrl = Objects.requireNonNull(natsUrl);
            return this;
        }

        public Builder natsUser(String natsUser) {
            this.natsUser = natsUser;
            return this;
        }

        public Builder natsPassword(String natsPassword) {
            this.natsPassword = natsPassword;
            return this;
        }

        public Builder natsToken(String natsToken) {
            this.natsToken = natsToken;
            return this;
        }

        public Builder natsConnectionTimeout(Duration timeout) {
            this.natsConnectionTimeout = Objects.requireNonNull(timeout);
            return this;
        }

        public Builder natsReconnectWait(Duration wait) {
            this.natsReconnectWait = Objects.requireNonNull(wait);
            return this;
        }

        public Builder natsMaxReconnects(int max) {
            this.natsMaxReconnects = max;
            return this;
        }

        public Builder jetStreamEnabled(boolean enabled) {
            this.jetStreamEnabled = enabled;
            return this;
        }

        public Builder jetStreamName(String name) {
            this.jetStreamName = Objects.requireNonNull(name);
            return this;
        }

        public Builder jetStreamReplicas(int replicas) {
            this.jetStreamReplicas = replicas;
            return this;
        }

        public Builder redisUrl(String redisUrl) {
            this.redisUrl = Objects.requireNonNull(redisUrl);
            return this;
        }

        public Builder idempotencyTtl(Duration ttl) {
            this.idempotencyTtl = Objects.requireNonNull(ttl);
            return this;
        }

        public Builder idempotencyMaxSize(int size) {
            this.idempotencyMaxSize = size;
            return this;
        }

        public EnterpriseConfig build() {
            return new EnterpriseConfig(this);
        }
    }
}
