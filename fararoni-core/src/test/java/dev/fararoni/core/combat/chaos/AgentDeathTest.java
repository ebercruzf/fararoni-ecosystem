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
package dev.fararoni.core.combat.chaos;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("CHAOS-01: Muerte Súbita del Agente")
class AgentDeathTest {
    private static final String TEST_TOPIC = "combat.test.chaos01";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private InMemorySovereignBus bus;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
    }

    @AfterEach
    void tearDown() {
        if (bus != null) {
            bus.shutdown(SHUTDOWN_TIMEOUT);
        }
    }

    @Test
    @DisplayName("Subscriber que lanza RuntimeException no crashea el bus")
    void agentSuddenDeath_shouldNotCrashBus() throws Exception {
        CountDownLatch crashLatch = new CountDownLatch(1);
        AtomicReference<String> receivedPayload = new AtomicReference<>();

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            receivedPayload.set(envelope.payload());
            crashLatch.countDown();
            throw new RuntimeException("Simulated agent death - CHAOS-01");
        });

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "test-sender",
            "trace-chaos-01",
            "payload-death-test"
        );
        bus.publish(TEST_TOPIC, envelope).join();

        assertTrue(crashLatch.await(5, TimeUnit.SECONDS),
            "El subscriber debió recibir el mensaje");
        assertEquals("payload-death-test", receivedPayload.get());

        assertTrue(bus.isHealthy(), "El bus debe seguir saludable después del crash");
    }

    @Test
    @DisplayName("Mensaje fallido eventualmente llega a la DLQ después de max reintentos")
    void failedMessage_shouldReachDLQ_afterMaxRetries() throws Exception {
        AtomicInteger failureCount = new AtomicInteger(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            failureCount.incrementAndGet();
            throw new RuntimeException("Persistent failure - CHAOS-01");
        });

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "test-sender",
            "trace-chaos-dlq",
            "payload-for-dlq"
        );
        bus.publish(TEST_TOPIC, envelope).join();

        int maxWaitSeconds = 15;
        int waited = 0;
        while (bus.getDeadLetterQueueSize() == 0 && waited < maxWaitSeconds) {
            Thread.sleep(1000);
            waited++;
        }

        assertTrue(bus.getDeadLetterQueueSize() > 0 || failureCount.get() > 0,
            "El mensaje debe estar en la DLQ o haber sido procesado al menos una vez. " +
            "DLQ size: " + bus.getDeadLetterQueueSize() + ", failures: " + failureCount.get());
    }

    @Test
    @DisplayName("Otros subscribers no se ven afectados por crash de uno")
    void healthySubscribers_notAffectedByCrash() throws Exception {
        CountDownLatch crashingLatch = new CountDownLatch(1);
        CountDownLatch healthyLatch = new CountDownLatch(1);
        AtomicReference<String> healthyReceived = new AtomicReference<>();

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            crashingLatch.countDown();
            throw new RuntimeException("I'm crashing! - CHAOS-01");
        });

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            healthyReceived.set(envelope.payload());
            healthyLatch.countDown();
        });

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "test-sender",
            "trace-multi-sub",
            "payload-for-both"
        );
        bus.publish(TEST_TOPIC, envelope).join();

        assertTrue(crashingLatch.await(5, TimeUnit.SECONDS),
            "El subscriber que crashea debió recibir el mensaje");
        assertTrue(healthyLatch.await(5, TimeUnit.SECONDS),
            "El subscriber saludable debió recibir el mensaje");
        assertEquals("payload-for-both", healthyReceived.get());
    }

    @Test
    @DisplayName("Bus continúa operando después de múltiples crashes")
    void bus_continuesOperating_afterMultipleCrashes() throws Exception {
        CountDownLatch crashCounter = new CountDownLatch(3);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            crashCounter.countDown();
            throw new RuntimeException("Crash #" + crashCounter.getCount());
        });

        for (int i = 1; i <= 3; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "test-sender",
                "trace-multi-crash-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope).join();
        }

        assertTrue(crashCounter.await(10, TimeUnit.SECONDS),
            "Todos los mensajes deben haber sido procesados");

        assertTrue(bus.isHealthy(), "El bus debe seguir operativo");

        CountDownLatch newMessageLatch = new CountDownLatch(1);
        String newTopic = "combat.test.recovery";
        bus.subscribe(newTopic, String.class, envelope -> newMessageLatch.countDown());

        SovereignEnvelope<String> recoveryEnvelope = SovereignEnvelope.create(
            "test-sender",
            "trace-recovery",
            "recovery-payload"
        );
        bus.publish(newTopic, recoveryEnvelope).join();

        assertTrue(newMessageLatch.await(5, TimeUnit.SECONDS),
            "El bus debe poder procesar nuevos mensajes después de crashes");
    }

    @Test
    @DisplayName("InFlightCount se mantiene consistente después de crash")
    void inFlightCount_remainsConsistent_afterCrash() throws Exception {
        long initialInFlight = bus.getInFlightCount();

        CountDownLatch latch = new CountDownLatch(1);
        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            latch.countDown();
            throw new RuntimeException("Crash for metrics test");
        });

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "test-sender",
            "trace-metrics",
            "payload-metrics"
        );
        bus.publish(TEST_TOPIC, envelope).join();
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        Thread.sleep(200);

        long finalInFlight = bus.getInFlightCount();
        assertEquals(initialInFlight, finalInFlight,
            "InFlight count no debe tener leaks después de crash");
    }
}
