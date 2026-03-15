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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("CHAOS-04: Memory Leak Detector")
class MemoryLeakTest {
    private static final String TEST_TOPIC = "combat.test.chaos04";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private InMemorySovereignBus bus;
    private MemoryMXBean memoryBean;

    @BeforeEach
    void setUp() {
        bus = new InMemorySovereignBus();
        memoryBean = ManagementFactory.getMemoryMXBean();
    }

    @AfterEach
    void tearDown() {
        if (bus != null) {
            bus.shutdown(SHUTDOWN_TIMEOUT);
        }
    }

    @Test
    @DisplayName("InFlight vuelve a 0 después de procesar mensajes")
    void inFlight_returnsToZero_afterProcessing() throws Exception {
        long initialInFlight = bus.getInFlightCount();
        assertEquals(0, initialInFlight, "InFlight inicial debe ser 0");

        AtomicInteger processed = new AtomicInteger(0);
        CountDownLatch allProcessed = new CountDownLatch(100);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            processed.incrementAndGet();
            allProcessed.countDown();
        });

        for (int i = 0; i < 100; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "leak-test-sender",
                "trace-leak-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        assertTrue(allProcessed.await(30, TimeUnit.SECONDS),
            "Todos los mensajes deben procesarse");

        Thread.sleep(500);

        long finalInFlight = bus.getInFlightCount();
        assertEquals(0, finalInFlight,
            "InFlight debe volver a 0 después de procesar todos los mensajes");
    }

    @Test
    @DisplayName("Memoria no crece linealmente con mensajes procesados")
    void memory_doesNotGrowLinearly_withMessages() throws Exception {
        System.gc();
        Thread.sleep(100);
        long baselineMemory = getUsedHeapMemory();

        AtomicInteger processed = new AtomicInteger(0);
        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            processed.incrementAndGet();
        });

        int batches = 5;
        int messagesPerBatch = 1000;
        long[] memoryAfterBatch = new long[batches];

        for (int batch = 0; batch < batches; batch++) {
            for (int i = 0; i < messagesPerBatch; i++) {
                SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                    "memory-sender",
                    "trace-batch" + batch + "-msg" + i,
                    "payload-batch" + batch + "-" + i
                );
                bus.publish(TEST_TOPIC, envelope);
            }

            Thread.sleep(500);

            System.gc();
            Thread.sleep(100);
            memoryAfterBatch[batch] = getUsedHeapMemory();
        }

        long firstBatchMemory = memoryAfterBatch[0];
        long lastBatchMemory = memoryAfterBatch[batches - 1];
        double growthRatio = (double) lastBatchMemory / firstBatchMemory;

        assertTrue(growthRatio < 1.5,
            String.format("Memoria no debe crecer >50%%. Ratio: %.2f (first: %d, last: %d)",
                growthRatio, firstBatchMemory, lastBatchMemory));

        assertEquals(batches * messagesPerBatch, processed.get(),
            "Todos los mensajes deben haberse procesado");
    }

    @Test
    @DisplayName("DLQ no crece más allá de MAX_DLQ_SIZE")
    void dlq_respectsMaxSize() throws Exception {
        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            throw new RuntimeException("Intentional failure for DLQ test");
        });

        int messagesToSend = 100;

        for (int i = 0; i < messagesToSend; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "dlq-flood-sender",
                "trace-dlq-flood-" + i,
                "payload-dlq-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        Thread.sleep(5000);

        int dlqSize = bus.getDeadLetterQueueSize();
        assertTrue(dlqSize <= 1000,
            "DLQ no debe exceder MAX_DLQ_SIZE (1000). Actual: " + dlqSize);
    }

    @Test
    @DisplayName("Shutdown limpia recursos correctamente")
    void shutdown_cleansResources() throws Exception {
        AtomicInteger processed = new AtomicInteger(0);
        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            processed.incrementAndGet();
        });

        for (int i = 0; i < 50; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "shutdown-sender",
                "trace-shutdown-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        Thread.sleep(500);

        long memoryBeforeShutdown = getUsedHeapMemory();
        bus.shutdown(Duration.ofSeconds(5));

        System.gc();
        Thread.sleep(200);
        long memoryAfterShutdown = getUsedHeapMemory();

        assertTrue(memoryAfterShutdown <= memoryBeforeShutdown * 1.1,
            String.format("Memoria no debe crecer después de shutdown. Before: %d, After: %d",
                memoryBeforeShutdown, memoryAfterShutdown));
    }

    @Test
    @DisplayName("Mensajes grandes no causan OOM")
    void largeMessages_doNotCauseOom() throws Exception {
        AtomicInteger processed = new AtomicInteger(0);
        CountDownLatch allDone = new CountDownLatch(10);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            processed.incrementAndGet();
            allDone.countDown();
        });

        String largePayload = "X".repeat(1024 * 1024);

        for (int i = 0; i < 10; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "large-msg-sender",
                "trace-large-" + i,
                largePayload + "-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        assertTrue(allDone.await(30, TimeUnit.SECONDS),
            "Mensajes grandes deben procesarse sin OOM");
        assertEquals(10, processed.get());

        System.gc();
        Thread.sleep(500);

        assertTrue(bus.isHealthy(), "Bus debe seguir saludable después de mensajes grandes");
    }

    @Test
    @DisplayName("Subscriptions repetidas al mismo topic no causan leak")
    void repeatedSubscriptions_noLeak() throws Exception {
        long initialMemory = getUsedHeapMemory();

        for (int i = 0; i < 100; i++) {
            bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            });
        }

        for (int i = 0; i < 50; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "multi-sub-sender",
                "trace-multi-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        Thread.sleep(1000);

        System.gc();
        Thread.sleep(200);
        long finalMemory = getUsedHeapMemory();

        double growthMB = (finalMemory - initialMemory) / (1024.0 * 1024.0);
        assertTrue(growthMB < 50,
            String.format("Memoria no debe crecer más de 50MB con subscriptions. Creció: %.2fMB", growthMB));
    }

    @Test
    @DisplayName("Futures de publish se completan y liberan")
    void publishFutures_completeAndRelease() throws Exception {
        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
        });

        long initialMemory = getUsedHeapMemory();

        for (int i = 0; i < 1000; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "future-sender",
                "trace-future-" + i,
                "payload-" + i
            );

            bus.publish(TEST_TOPIC, envelope).join();
        }

        System.gc();
        Thread.sleep(200);
        long finalMemory = getUsedHeapMemory();

        double growthMB = (finalMemory - initialMemory) / (1024.0 * 1024.0);
        assertTrue(growthMB < 20,
            String.format("Futures completados deben liberarse. Crecimiento: %.2fMB", growthMB));
    }

    private long getUsedHeapMemory() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
}
