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

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@DisplayName("ServiceLoader Integration")
class ServiceLoaderIntegrationTest {

    @Test
    @DisplayName("ServiceLoader finds NatsSovereignBus with priority 100")
    void serviceLoaderFindsNatsBus() {
        // When - ServiceLoader instantiates all implementations
        // NatsSovereignBus uses lazy-init, so it won't connect until first use
        ServiceLoader<SovereignEventBus> loader = ServiceLoader.load(SovereignEventBus.class);

        List<String> found = new ArrayList<>();
        SovereignEventBus highest = null;
        int maxPriority = -1;

        for (SovereignEventBus bus : loader) {
            found.add(bus.getClass().getSimpleName() + " (priority=" + bus.getPriority() + ")");
            if (bus.getPriority() > maxPriority) {
                maxPriority = bus.getPriority();
                highest = bus;
            }
        }

        // Then
        System.out.println("[SPI-TEST] Found implementations: " + found);
        System.out.println("[SPI-TEST] Highest priority: " + (highest != null ? highest.getClass().getSimpleName() : "none"));

        // NatsSovereignBus should be found
        assertThat(found).anyMatch(s -> s.contains("NatsSovereignBus"));

        // NatsSovereignBus should have priority 100
        assertThat(highest).isNotNull();
        assertThat(highest.getClass().getSimpleName()).isEqualTo("NatsSovereignBus");
        assertThat(highest.getPriority()).isEqualTo(100);
    }

    @Test
    @DisplayName("NatsSovereignBus can be instantiated without NATS (lazy-init)")
    void lazyInitDoesNotConnect() {
        // When - Create bus without NATS running
        NatsSovereignBus bus = new NatsSovereignBus();

        // Then - Should not throw, connection is lazy
        assertThat(bus).isNotNull();
        assertThat(bus.getPriority()).isEqualTo(100);
        assertThat(bus.isHealthy()).isFalse(); // Not connected yet

        // Cleanup - no connection to close
    }

    @Test
    @DisplayName("Priority is 100 (higher than InMemory default 0)")
    void priorityIsHigherThanInMemory() {
        // Given
        NatsSovereignBus bus = new NatsSovereignBus();

        // Then
        assertThat(bus.getPriority()).isEqualTo(100);
        assertThat(bus.getPriority()).isGreaterThan(0); // InMemory default
    }
}
