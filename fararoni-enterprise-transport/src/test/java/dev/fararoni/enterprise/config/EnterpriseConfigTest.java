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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@DisplayName("EnterpriseConfig")
class EnterpriseConfigTest {

    @Test
    @DisplayName("defaults() creates config with default values")
    void defaultConfig() {
        // When
        EnterpriseConfig config = EnterpriseConfig.defaults();

        // Then
        assertThat(config.getNatsUrl()).isEqualTo("nats://localhost:4222");
        assertThat(config.isJetStreamEnabled()).isTrue();
        assertThat(config.getJetStreamName()).isEqualTo("fararoni-events");
        assertThat(config.getRedisUrl()).isEqualTo("redis://localhost:6379");
        assertThat(config.getIdempotencyTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("builder() allows custom configuration")
    void customConfig() {
        // When
        EnterpriseConfig config = EnterpriseConfig.builder()
            .natsUrl("nats://prod-cluster:4222")
            .natsUser("admin")
            .natsPassword("secret")
            .jetStreamEnabled(true)
            .jetStreamReplicas(3)
            .redisUrl("redis://redis-cluster:6379")
            .idempotencyTtl(Duration.ofMinutes(10))
            .build();

        // Then
        assertThat(config.getNatsUrl()).isEqualTo("nats://prod-cluster:4222");
        assertThat(config.getNatsUser()).isEqualTo("admin");
        assertThat(config.getNatsPassword()).isEqualTo("secret");
        assertThat(config.hasNatsCredentials()).isTrue();
        assertThat(config.getJetStreamReplicas()).isEqualTo(3);
        assertThat(config.getIdempotencyTtl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("hasNatsCredentials() returns true with user/password")
    void hasCredentialsWithUserPassword() {
        // Given
        EnterpriseConfig config = EnterpriseConfig.builder()
            .natsUser("user")
            .natsPassword("pass")
            .build();

        // Then
        assertThat(config.hasNatsCredentials()).isTrue();
    }

    @Test
    @DisplayName("hasNatsCredentials() returns true with token")
    void hasCredentialsWithToken() {
        // Given
        EnterpriseConfig config = EnterpriseConfig.builder()
            .natsToken("my-secret-token")
            .build();

        // Then
        assertThat(config.hasNatsCredentials()).isTrue();
    }

    @Test
    @DisplayName("hasNatsCredentials() returns false without credentials")
    void noCredentials() {
        // Given
        EnterpriseConfig config = EnterpriseConfig.defaults();

        // Then
        assertThat(config.hasNatsCredentials()).isFalse();
    }

    @Test
    @DisplayName("connection timeout has default value")
    void connectionTimeout() {
        // Given
        EnterpriseConfig config = EnterpriseConfig.defaults();

        // Then
        assertThat(config.getNatsConnectionTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.getNatsReconnectWait()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.getNatsMaxReconnects()).isEqualTo(60);
    }
}
