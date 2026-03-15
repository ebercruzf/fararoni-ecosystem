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
package dev.fararoni.core.core.telemetry;

import dev.fararoni.core.core.session.SessionModeManager;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("TelemetryQueue Tests")
class TelemetryQueueTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        TelemetryQueue.resetForTesting();
        SessionModeManager.reset();
        WorkspaceManager.reset();
    }

    @AfterEach
    void tearDown() {
        TelemetryQueue.resetForTesting();
        SessionModeManager.reset();
        WorkspaceManager.reset();
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {
        @Test
        @DisplayName("getInstance debe retornar la misma instancia")
        void getInstance_ShouldReturnSameInstance() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

            TelemetryQueue q1 = TelemetryQueue.getInstance();
            TelemetryQueue q2 = TelemetryQueue.getInstance();

            assertSame(q1, q2);
        }

        @Test
        @DisplayName("reset debe permitir nueva instancia")
        void reset_ShouldAllowNewInstance() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

            TelemetryQueue q1 = TelemetryQueue.getInstance();
            TelemetryQueue.resetForTesting();
            TelemetryQueue q2 = TelemetryQueue.getInstance();

            assertNotSame(q1, q2);
        }
    }

    @Nested
    @DisplayName("Enqueue Operations")
    class EnqueueTests {
        @Test
        @DisplayName("enqueue debe agregar evento a la cola")
        void enqueue_ShouldAddEventToQueue() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            var event = TelemetryQueue.TelemetryEvent.create("TEST", "{\"key\":\"value\"}");
            boolean result = queue.enqueue(event);

            assertTrue(result);
            assertEquals(1, queue.size());
            assertFalse(queue.isEmpty());
        }

        @Test
        @DisplayName("enqueue con tipo y payload debe crear evento")
        void enqueue_WithTypeAndPayload_ShouldCreateEvent() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            boolean result = queue.enqueue("FEEDBACK", "{\"rating\":5}");

            assertTrue(result);
            assertEquals(1, queue.size());

            var event = queue.peek();
            assertNotNull(event);
            assertEquals("FEEDBACK", event.eventType());
            assertEquals("{\"rating\":5}", event.payload());
        }

        @Test
        @DisplayName("enqueue null debe retornar false")
        void enqueue_Null_ShouldReturnFalse() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            boolean result = queue.enqueue((TelemetryQueue.TelemetryEvent) null);

            assertFalse(result);
            assertEquals(0, queue.size());
        }

        @Test
        @DisplayName("enqueue multiples eventos debe mantener orden FIFO")
        void enqueue_MultipleEvents_ShouldMaintainFifoOrder() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("TYPE_1", "payload1");
            queue.enqueue("TYPE_2", "payload2");
            queue.enqueue("TYPE_3", "payload3");

            assertEquals(3, queue.size());
            assertEquals("TYPE_1", queue.poll().eventType());
            assertEquals("TYPE_2", queue.poll().eventType());
            assertEquals("TYPE_3", queue.poll().eventType());
        }
    }

    @Nested
    @DisplayName("Poll and Peek Operations")
    class PollPeekTests {
        @Test
        @DisplayName("peek no debe remover el evento")
        void peek_ShouldNotRemoveEvent() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();
            queue.enqueue("TEST", "payload");

            var event1 = queue.peek();
            var event2 = queue.peek();

            assertEquals(1, queue.size());
            assertEquals(event1, event2);
        }

        @Test
        @DisplayName("poll debe remover el evento")
        void poll_ShouldRemoveEvent() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();
            queue.enqueue("TEST", "payload");

            var event = queue.poll();

            assertNotNull(event);
            assertEquals(0, queue.size());
            assertTrue(queue.isEmpty());
        }

        @Test
        @DisplayName("poll en cola vacia debe retornar null")
        void poll_EmptyQueue_ShouldReturnNull() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            var event = queue.poll();

            assertNull(event);
        }
    }

    @Nested
    @DisplayName("Flush Operations")
    class FlushTests {
        @Test
        @DisplayName("flush con sender exitoso debe vaciar cola")
        void flush_SuccessfulSender_ShouldEmptyQueue() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT1", "payload1");
            queue.enqueue("EVENT2", "payload2");
            queue.enqueue("EVENT3", "payload3");

            var result = queue.flush(event -> true);

            assertEquals(3, result.sent());
            assertEquals(0, result.failed());
            assertEquals(0, result.dropped());
            assertTrue(result.isSuccess());
            assertTrue(queue.isEmpty());
        }

        @Test
        @DisplayName("flush con sender fallido debe mantener eventos para reintento")
        void flush_FailedSender_ShouldKeepEventsForRetry() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT1", "payload1");
            queue.enqueue("EVENT2", "payload2");

            var result = queue.flush(event -> false);

            assertEquals(0, result.sent());
            assertEquals(2, result.failed());
            assertEquals(0, result.dropped());
            assertFalse(result.isSuccess());
            assertEquals(2, queue.size());
        }

        @Test
        @DisplayName("flush con sender parcial debe procesar correctamente")
        void flush_PartialSuccess_ShouldHandleCorrectly() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("SUCCESS", "payload1");
            queue.enqueue("FAIL", "payload2");
            queue.enqueue("SUCCESS", "payload3");

            AtomicInteger callCount = new AtomicInteger(0);
            var result = queue.flush(event -> {
                callCount.incrementAndGet();
                return event.eventType().equals("SUCCESS");
            });

            assertEquals(3, callCount.get());
            assertEquals(2, result.sent());
            assertEquals(1, result.failed());
            assertEquals(1, queue.size());
        }

        @Test
        @DisplayName("flush con sender que lanza excepcion debe manejar error")
        void flush_SenderThrowsException_ShouldHandleError() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT1", "payload1");

            var result = queue.flush(event -> {
                throw new RuntimeException("Network error");
            });

            assertEquals(0, result.sent());
            assertEquals(1, result.failed());
            assertEquals(1, queue.size());
        }

        @Test
        @DisplayName("flush con cola vacia debe retornar resultado vacio")
        void flush_EmptyQueue_ShouldReturnEmptyResult() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            var result = queue.flush(event -> true);

            assertEquals(0, result.sent());
            assertEquals(0, result.failed());
            assertEquals(0, result.dropped());
            assertEquals(0, result.total());
        }

        @Test
        @DisplayName("flush con null sender debe retornar resultado vacio")
        void flush_NullSender_ShouldReturnEmptyResult() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();
            queue.enqueue("EVENT1", "payload1");

            var result = queue.flush(null);

            assertEquals(0, result.sent());
            assertEquals(1, queue.size());
        }
    }

    @Nested
    @DisplayName("Retry Logic")
    class RetryTests {
        @Test
        @DisplayName("evento debe incrementar retry count en cada fallo")
        void event_ShouldIncrementRetryOnFailure() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT", "payload");

            queue.flush(event -> false);
            var event1 = queue.peek();
            assertEquals(1, event1.retryCount());

            queue.flush(event -> false);
            var event2 = queue.peek();
            assertEquals(2, event2.retryCount());
        }

        @Test
        @DisplayName("evento debe ser descartado despues de MAX_RETRIES")
        void event_ShouldBeDroppedAfterMaxRetries() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT", "payload");

            for (int i = 0; i < 5; i++) {
                queue.flush(event -> false);
            }

            var result = queue.flush(event -> false);

            assertEquals(0, result.failed());
            assertEquals(1, result.dropped());
            assertTrue(queue.isEmpty());
        }
    }

    @Nested
    @DisplayName("Persistence")
    class PersistenceTests {
        @Test
        @DisplayName("persistNow debe guardar eventos en disco")
        void persistNow_ShouldSaveEventsToDisk() throws IOException {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT1", "payload1");
            queue.enqueue("EVENT2", "payload2");
            queue.persistNow();

            Path queueFile = tempDir.resolve("queue.json");
            assertTrue(Files.exists(queueFile));

            String content = Files.readString(queueFile);
            assertTrue(content.contains("EVENT1"));
            assertTrue(content.contains("EVENT2"));
        }

        @Test
        @DisplayName("eventos deben cargarse del disco al iniciar")
        void events_ShouldLoadFromDiskOnInit() throws IOException {
            Path queueFile = tempDir.resolve("queue.json");
            String json = """
                [
                    {
                        "id": "test-id-1",
                        "timestamp": 1704672000000,
                        "eventType": "PERSISTED",
                        "payload": "{\\"loaded\\": true}",
                        "retryCount": 0
                    }
                ]
                """;
            Files.writeString(queueFile, json);

            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            assertEquals(1, queue.size());
            var event = queue.peek();
            assertNotNull(event);
            assertEquals("test-id-1", event.id());
            assertEquals("PERSISTED", event.eventType());
        }

        @Test
        @DisplayName("close debe persistir eventos pendientes")
        void close_ShouldPersistPendingEvents() throws IOException {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("CLOSING", "payload");
            queue.close();

            Path queueFile = tempDir.resolve("queue.json");
            assertTrue(Files.exists(queueFile));

            String content = Files.readString(queueFile);
            assertTrue(content.contains("CLOSING"));
        }
    }

    @Nested
    @DisplayName("Incognito Mode")
    class IncognitoModeTests {
        @Test
        @DisplayName("en modo incognito no debe persistir")
        void incognitoMode_ShouldNotPersist() {
            SessionModeManager.initialize(new String[]{"--incognito"});
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

            TelemetryQueue queue = TelemetryQueue.getInstance();

            assertFalse(queue.isPersistEnabled());
            assertNull(queue.getQueuePath());
        }

        @Test
        @DisplayName("en modo incognito debe funcionar en memoria")
        void incognitoMode_ShouldWorkInMemory() {
            SessionModeManager.initialize(new String[]{"--incognito"});
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});

            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("INCOGNITO_EVENT", "payload");
            assertEquals(1, queue.size());

            var event = queue.poll();
            assertNotNull(event);
            assertEquals("INCOGNITO_EVENT", event.eventType());
        }
    }

    @Nested
    @DisplayName("Queue Limit")
    class QueueLimitTests {
        @Test
        @DisplayName("cola debe descartar eventos antiguos cuando esta llena")
        void queue_ShouldDropOldEventsWhenFull() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            for (int i = 0; i < 1005; i++) {
                queue.enqueue("EVENT_" + i, "payload");
            }

            assertEquals(1000, queue.size());

            var firstEvent = queue.peek();
            assertNotNull(firstEvent);
            assertEquals("EVENT_5", firstEvent.eventType());
        }
    }

    @Nested
    @DisplayName("Clear Operations")
    class ClearTests {
        @Test
        @DisplayName("clear debe vaciar la cola")
        void clear_ShouldEmptyQueue() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT1", "payload1");
            queue.enqueue("EVENT2", "payload2");

            int cleared = queue.clear();

            assertEquals(2, cleared);
            assertTrue(queue.isEmpty());
        }

        @Test
        @DisplayName("clear debe persistir cola vacia")
        void clear_ShouldPersistEmptyQueue() throws IOException {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT", "payload");
            queue.persistNow();
            queue.clear();

            Path queueFile = tempDir.resolve("queue.json");
            String content = Files.readString(queueFile);
            assertEquals("[ ]", content.trim());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {
        @Test
        @DisplayName("getStats debe retornar estadisticas correctas")
        void getStats_ShouldReturnCorrectStats() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT1", "payload1");
            queue.enqueue("EVENT2", "payload2");
            queue.flush(event -> event.eventType().equals("EVENT1"));

            var stats = queue.getStats();

            assertEquals(1, stats.pending());
            assertEquals(2, stats.enqueued());
            assertEquals(1, stats.sent());
            assertTrue(stats.persistEnabled());
        }

        @Test
        @DisplayName("getSummary debe ser informativo")
        void getSummary_ShouldBeInformative() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT", "payload");
            var stats = queue.getStats();
            String summary = stats.getSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("pending"));
            assertTrue(summary.contains("enqueued"));
        }
    }

    @Nested
    @DisplayName("TelemetryEvent Record")
    class TelemetryEventTests {
        @Test
        @DisplayName("create debe generar ID unico")
        void create_ShouldGenerateUniqueId() {
            var event1 = TelemetryQueue.TelemetryEvent.create("TYPE", "payload");
            var event2 = TelemetryQueue.TelemetryEvent.create("TYPE", "payload");

            assertNotEquals(event1.id(), event2.id());
        }

        @Test
        @DisplayName("create debe establecer timestamp actual")
        void create_ShouldSetCurrentTimestamp() {
            long before = System.currentTimeMillis();
            var event = TelemetryQueue.TelemetryEvent.create("TYPE", "payload");
            long after = System.currentTimeMillis();

            assertTrue(event.timestamp() >= before);
            assertTrue(event.timestamp() <= after);
        }

        @Test
        @DisplayName("incrementRetry debe crear nueva instancia")
        void incrementRetry_ShouldCreateNewInstance() {
            var original = TelemetryQueue.TelemetryEvent.create("TYPE", "payload");
            var incremented = original.incrementRetry();

            assertNotSame(original, incremented);
            assertEquals(0, original.retryCount());
            assertEquals(1, incremented.retryCount());
            assertEquals(original.id(), incremented.id());
        }

        @Test
        @DisplayName("isExpired debe detectar eventos viejos")
        void isExpired_ShouldDetectOldEvents() {
            long oldTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000);
            var oldEvent = new TelemetryQueue.TelemetryEvent(
                    "id", oldTimestamp, "TYPE", "payload", 0
            );

            assertTrue(oldEvent.isExpired());

            var newEvent = TelemetryQueue.TelemetryEvent.create("TYPE", "payload");
            assertFalse(newEvent.isExpired());
        }
    }

    @Nested
    @DisplayName("FlushResult Record")
    class FlushResultTests {
        @Test
        @DisplayName("isSuccess debe ser true cuando no hay fallos")
        void isSuccess_ShouldBeTrueWhenNoFailures() {
            var result = new TelemetryQueue.FlushResult(5, 0, 0);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("isSuccess debe ser false cuando hay fallos")
        void isSuccess_ShouldBeFalseWhenFailures() {
            var result1 = new TelemetryQueue.FlushResult(3, 2, 0);
            var result2 = new TelemetryQueue.FlushResult(3, 0, 1);

            assertFalse(result1.isSuccess());
            assertFalse(result2.isSuccess());
        }

        @Test
        @DisplayName("total debe sumar todos los resultados")
        void total_ShouldSumAllResults() {
            var result = new TelemetryQueue.FlushResult(3, 2, 1);
            assertEquals(6, result.total());
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {
        @Test
        @DisplayName("enqueue debe ser thread-safe")
        void enqueue_ShouldBeThreadSafe() throws InterruptedException {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            int threadCount = 10;
            int eventsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < eventsPerThread; i++) {
                            queue.enqueue("THREAD_" + threadId, "event_" + i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(queue.size() <= 1000);
            assertTrue(queue.size() >= Math.min(threadCount * eventsPerThread, 1000));
        }
    }

    @Nested
    @DisplayName("Get Pending Events")
    class GetPendingEventsTests {
        @Test
        @DisplayName("getPendingEvents debe retornar lista inmutable")
        void getPendingEvents_ShouldReturnImmutableList() {
            WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
            TelemetryQueue queue = TelemetryQueue.getInstance();

            queue.enqueue("EVENT", "payload");
            var events = queue.getPendingEvents();

            assertEquals(1, events.size());
            assertThrows(UnsupportedOperationException.class, () ->
                    events.add(TelemetryQueue.TelemetryEvent.create("NEW", "payload"))
            );
        }
    }
}
