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

import dev.fararoni.bus.agent.api.bus.BusOverloadException;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("CHAOS-02: Avalancha de Mensajes (Backpressure)")
class BackpressureTest {
    private static final String TEST_TOPIC = "combat.test.chaos02";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private static final int BUS_MAX_CAPACITY = 10_000;

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
    @DisplayName("InFlightCount nunca excede MAX_CAPACITY bajo carga alta")
    void inFlightCount_neverExceedsMaxCapacity_underHighLoad() throws Exception {
        Semaphore processingGate = new Semaphore(0);
        AtomicInteger messagesReceived = new AtomicInteger(0);
        AtomicLong maxObservedInFlight = new AtomicLong(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            messagesReceived.incrementAndGet();
            long current = bus.getInFlightCount();
            maxObservedInFlight.updateAndGet(max -> Math.max(max, current));
            try {
                processingGate.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        int messagesToSend = 100;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < messagesToSend; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "stress-sender",
                "trace-backpressure-" + i,
                "payload-" + i
            );
            futures.add(bus.publish(TEST_TOPIC, envelope));
        }

        Thread.sleep(200);

        assertTrue(bus.getInFlightCount() <= BUS_MAX_CAPACITY,
            "InFlight debe estar <= " + BUS_MAX_CAPACITY);

        processingGate.release(messagesToSend);

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);

        assertEquals(messagesToSend, messagesReceived.get(),
            "Todos los mensajes deben haber sido procesados");

        assertTrue(maxObservedInFlight.get() <= BUS_MAX_CAPACITY,
            "Máximo in-flight observado (" + maxObservedInFlight.get() + ") debe ser <= " + BUS_MAX_CAPACITY);
    }

    @Test
    @DisplayName("Bus se recupera después de pico de carga")
    void bus_recoversAfterLoadSpike() throws Exception {
        AtomicInteger processed = new AtomicInteger(0);
        Semaphore slowdown = new Semaphore(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            int count = processed.incrementAndGet();
            if (count <= 50) {
                try {
                    slowdown.tryAcquire(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        int spikeSize = 100;
        for (int i = 0; i < spikeSize; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "spike-sender",
                "trace-spike-" + i,
                "spike-payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        Thread.sleep(6000);

        assertTrue(bus.isHealthy(), "El bus debe estar saludable después del pico");
        assertEquals(spikeSize, processed.get(), "Todos los mensajes deben haberse procesado");
    }

    @Test
    @DisplayName("Health check reporta estado correcto según carga")
    void healthCheck_reportsCorrectState() throws Exception {
        assertTrue(bus.isHealthy(), "Bus debe iniciar healthy");
        assertEquals(0, bus.getInFlightCount(), "InFlight debe ser 0 inicialmente");

        AtomicInteger processed = new AtomicInteger(0);
        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            processed.incrementAndGet();
        });

        for (int i = 0; i < 10; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "health-sender",
                "trace-health-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        Thread.sleep(500);

        assertTrue(bus.isHealthy(), "Bus debe estar healthy con carga baja");
        assertEquals(10, processed.get(), "Todos los mensajes deben procesarse");

        long inFlight = bus.getInFlightCount();
        assertTrue(inFlight <= 10,
            "InFlight no debe exceder mensajes enviados. Actual: " + inFlight);
    }

    @Test
    @DisplayName("Mensajes no se pierden durante backpressure")
    void messages_areNotLost_duringBackpressure() throws Exception {
        AtomicInteger processedCount = new AtomicInteger(0);
        ConcurrentHashMap<String, Boolean> receivedPayloads = new ConcurrentHashMap<>();

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            receivedPayloads.put(envelope.payload(), true);
            processedCount.incrementAndGet();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        int totalMessages = 200;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalMessages; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "reliable-sender",
                "trace-reliable-" + i,
                "payload-" + i
            );
            futures.add(bus.publish(TEST_TOPIC, envelope));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);

        int maxWait = 60;
        int waited = 0;
        while (processedCount.get() < totalMessages && waited < maxWait) {
            Thread.sleep(1000);
            waited++;
        }

        assertEquals(totalMessages, processedCount.get(),
            "No debe haber pérdida de mensajes");
        assertEquals(totalMessages, receivedPayloads.size(),
            "Cada mensaje debe haberse recibido exactamente una vez");
    }

    @Test
    @DisplayName("Múltiples productores no causan deadlock")
    void multipleProducers_noDeadlock() throws Exception {
        AtomicInteger processed = new AtomicInteger(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            processed.incrementAndGet();
        });

        int producerCount = 10;
        int messagesPerProducer = 50;
        ExecutorService producers = Executors.newFixedThreadPool(producerCount);
        CountDownLatch allDone = new CountDownLatch(producerCount);

        for (int p = 0; p < producerCount; p++) {
            final int producerId = p;
            producers.submit(() -> {
                try {
                    for (int m = 0; m < messagesPerProducer; m++) {
                        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                            "producer-" + producerId,
                            "trace-p" + producerId + "-m" + m,
                            "payload-p" + producerId + "-m" + m
                        );
                        bus.publish(TEST_TOPIC, envelope).join();
                    }
                } finally {
                    allDone.countDown();
                }
            });
        }

        assertTrue(allDone.await(30, TimeUnit.SECONDS),
            "Todos los productores deben terminar sin deadlock");

        producers.shutdown();
        assertTrue(producers.awaitTermination(5, TimeUnit.SECONDS));

        Thread.sleep(2000);

        int expectedTotal = producerCount * messagesPerProducer;
        assertEquals(expectedTotal, processed.get(),
            "Todos los mensajes de todos los productores deben procesarse");
    }
}
