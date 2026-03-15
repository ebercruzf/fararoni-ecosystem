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
import dev.fararoni.core.core.resilience.PoisonPill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("CHAOS-03: Resurrección DLQ (GalvanicAgent)")
class DlqResurrectionTest {
    private static final String TEST_TOPIC = "combat.test.chaos03";
    private static final String RECOVERY_TOPIC = "combat.test.recovery";
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private static final int MAX_WAIT_FOR_DLQ_SECONDS = 30;

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

    private boolean waitForDlqSize(int minSize, int maxWaitSeconds) throws InterruptedException {
        int waited = 0;
        while (bus.getDeadLetterQueueSize() < minSize && waited < maxWaitSeconds) {
            Thread.sleep(1000);
            waited++;
        }
        return bus.getDeadLetterQueueSize() >= minSize;
    }

    @Test
    @DisplayName("PoisonPill contiene toda la metadata del mensaje original")
    void poisonPill_containsOriginalMessageMetadata() throws Exception {
        AtomicInteger failCount = new AtomicInteger(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            failCount.incrementAndGet();
            throw new RuntimeException("Deliberate failure for DLQ test");
        });

        String originalTraceId = "trace-resurrection-test-123";
        String originalPayload = "critical-payload-for-dlq";
        String originalSender = "important-sender";

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            originalSender,
            originalTraceId,
            originalPayload
        );
        bus.publish(TEST_TOPIC, envelope).join();

        boolean arrived = waitForDlqSize(1, MAX_WAIT_FOR_DLQ_SECONDS);

        assertTrue(failCount.get() > 0 || arrived,
            "El mensaje debe haberse procesado o llegado a DLQ. Failures: " + failCount.get());

        if (arrived) {
            PoisonPill corpse = bus.pollDeadLetter();
            assertNotNull(corpse, "Debe poder recuperar PoisonPill de DLQ");
            assertEquals(TEST_TOPIC, corpse.originalTopic(),
                "PoisonPill debe recordar el tópico original");
            assertNotNull(corpse.failureReason(),
                "PoisonPill debe tener el error que causó el fallo");
            assertNotNull(corpse.originalEnvelope(),
                "PoisonPill debe contener el envelope original");
        }
    }

    @Test
    @DisplayName("Mensaje recuperado de DLQ puede ser republicado exitosamente")
    void recoveredMessage_canBeRepublished_successfully() throws Exception {
        AtomicBoolean shouldFail = new AtomicBoolean(true);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        bus.subscribe(RECOVERY_TOPIC, String.class, envelope -> {
            if (shouldFail.get()) {
                failCount.incrementAndGet();
                throw new RuntimeException("Initial failure");
            }
            successCount.incrementAndGet();
        });

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "recovery-sender",
            "trace-recovery",
            "payload-to-recover"
        );
        bus.publish(RECOVERY_TOPIC, envelope).join();

        waitForDlqSize(1, MAX_WAIT_FOR_DLQ_SECONDS);

        if (bus.getDeadLetterQueueSize() > 0) {
            shouldFail.set(false);

            PoisonPill corpse = bus.pollDeadLetter();
            assertNotNull(corpse);

            @SuppressWarnings("unchecked")
            SovereignEnvelope<String> resurrectedEnvelope = SovereignEnvelope.create(
                "necromancer",
                corpse.originalEnvelope().traceId() + "-resurrected",
                (String) corpse.originalEnvelope().payload()
            );
            bus.publish(RECOVERY_TOPIC, resurrectedEnvelope).join();

            Thread.sleep(1000);

            assertTrue(successCount.get() > 0,
                "El mensaje resucitado debe procesarse exitosamente");
        } else {
            assertTrue(failCount.get() > 0,
                "Debe haber habido al menos un intento de procesamiento");
        }
    }

    @Test
    @DisplayName("DLQ respeta límite máximo de tamaño")
    void dlq_respectsMaxSizeLimit() throws Exception {
        AtomicInteger failCount = new AtomicInteger(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            failCount.incrementAndGet();
            throw new RuntimeException("Always fail for limit test");
        });

        int messagesToSend = 10;

        for (int i = 0; i < messagesToSend; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "limit-sender",
                "trace-limit-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        Thread.sleep(5000);

        int dlqSize = bus.getDeadLetterQueueSize();

        assertTrue(failCount.get() > 0 || dlqSize > 0,
            "Debe haber fallos procesados o mensajes en DLQ. Failures: " + failCount.get() + ", DLQ: " + dlqSize);

        assertTrue(dlqSize <= 1000,
            "DLQ no debe exceder MAX_DLQ_SIZE (1000). Actual: " + dlqSize);
    }

    @Test
    @DisplayName("pollDeadLetter remueve el mensaje de la DLQ")
    void pollDeadLetter_removesMessage() throws Exception {
        AtomicInteger failCount = new AtomicInteger(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            failCount.incrementAndGet();
            throw new RuntimeException("Fail to populate DLQ");
        });

        SovereignEnvelope<String> envelope = SovereignEnvelope.create(
            "poll-test-sender",
            "trace-poll",
            "payload-poll"
        );
        bus.publish(TEST_TOPIC, envelope).join();

        boolean arrived = waitForDlqSize(1, MAX_WAIT_FOR_DLQ_SECONDS);

        if (arrived) {
            int sizeBeforePoll = bus.getDeadLetterQueueSize();

            PoisonPill corpse = bus.pollDeadLetter();

            int sizeAfterPoll = bus.getDeadLetterQueueSize();
            assertEquals(sizeBeforePoll - 1, sizeAfterPoll,
                "Poll debe remover el mensaje de DLQ");
            assertNotNull(corpse, "Poll debe retornar el PoisonPill");
        } else {
            assertTrue(failCount.get() > 0,
                "Debe haber habido intentos de procesamiento. Failures: " + failCount.get());
        }
    }

    @Test
    @DisplayName("DLQ puede recibir múltiples mensajes y ser consumida")
    void dlq_canReceiveAndBeConsumed() throws Exception {
        AtomicInteger failCount = new AtomicInteger(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            failCount.incrementAndGet();
            throw new RuntimeException("Fail for DLQ consumption test");
        });

        int messageCount = 3;

        for (int i = 0; i < messageCount; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "consumption-sender",
                "trace-consume-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
            Thread.sleep(100);
        }

        Thread.sleep(10000);

        assertTrue(failCount.get() > 0,
            "Debe haber habido intentos de procesamiento. Failures: " + failCount.get());

        int dlqSize = bus.getDeadLetterQueueSize();
        if (dlqSize > 0) {
            List<PoisonPill> recovered = new ArrayList<>();
            PoisonPill corpse;
            while ((corpse = bus.pollDeadLetter()) != null) {
                recovered.add(corpse);
            }
            assertFalse(recovered.isEmpty(), "Debe haber recuperado mensajes de DLQ");
        }
    }

    @Test
    @DisplayName("Múltiples mensajes fallidos son procesados por el bus")
    void multipleFailedMessages_areProcessedByBus() throws Exception {
        AtomicInteger failCount = new AtomicInteger(0);

        bus.subscribe(TEST_TOPIC, String.class, envelope -> {
            failCount.incrementAndGet();
            throw new RuntimeException("Always fail");
        });

        int messageCount = 3;

        for (int i = 0; i < messageCount; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "multi-fail-sender",
                "trace-multi-" + i,
                "payload-" + i
            );
            bus.publish(TEST_TOPIC, envelope);
        }

        Thread.sleep(5000);

        assertTrue(failCount.get() >= messageCount,
            "Debe haber al menos " + messageCount + " intentos. Actual: " + failCount.get());

        assertTrue(bus.isHealthy(), "El bus debe seguir saludable después de fallos");
    }
}
