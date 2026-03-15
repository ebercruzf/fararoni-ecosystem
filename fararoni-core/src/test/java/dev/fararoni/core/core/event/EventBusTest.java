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
package dev.fararoni.core.core.event;

import dev.fararoni.core.core.event.types.AuditEvent;
import dev.fararoni.core.core.event.types.ProcessLifecycleEvent;
import dev.fararoni.core.core.event.types.SystemErrorEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("EventBus - Sistema de Eventos")
class EventBusTest {
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    @Nested
    @DisplayName("Publicación y Suscripción Básica")
    class BasicPubSub {
        @Test
        @DisplayName("Suscriptor recibe evento publicado")
        void subscriberReceivesPublishedEvent() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> receivedMessage = new AtomicReference<>();

            eventBus.subscribe(AuditEvent.class, event -> {
                receivedMessage.set(event.detail());
                latch.countDown();
            });

            eventBus.publish(AuditEvent.commandExecuted("/test"));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Evento debería ser recibido");
            assertEquals("/test", receivedMessage.get());
        }

        @Test
        @DisplayName("Múltiples suscriptores reciben el mismo evento")
        void multipleSubscribersReceiveEvent() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(3);
            AtomicInteger counter = new AtomicInteger(0);

            for (int i = 0; i < 3; i++) {
                eventBus.subscribe(AuditEvent.class, event -> {
                    counter.incrementAndGet();
                    latch.countDown();
                });
            }

            eventBus.publish(AuditEvent.commandExecuted("/multi"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(3, counter.get());
        }

        @Test
        @DisplayName("Suscriptor solo recibe eventos de su tipo")
        void subscriberOnlyReceivesMatchingType() throws InterruptedException {
            CountDownLatch auditLatch = new CountDownLatch(1);
            CountDownLatch errorLatch = new CountDownLatch(1);
            AtomicInteger auditCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            eventBus.subscribe(AuditEvent.class, event -> {
                auditCount.incrementAndGet();
                auditLatch.countDown();
            });

            eventBus.subscribe(SystemErrorEvent.class, event -> {
                errorCount.incrementAndGet();
                errorLatch.countDown();
            });

            eventBus.publish(AuditEvent.commandExecuted("/test"));

            assertTrue(auditLatch.await(2, TimeUnit.SECONDS));
            Thread.sleep(200);

            assertEquals(1, auditCount.get());
            assertEquals(0, errorCount.get());
        }
    }

    @Nested
    @DisplayName("Listeners Globales")
    class GlobalListeners {
        @Test
        @DisplayName("Listener global recibe todos los tipos de eventos")
        void globalListenerReceivesAllEvents() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(3);
            AtomicInteger counter = new AtomicInteger(0);

            eventBus.subscribe(event -> {
                counter.incrementAndGet();
                latch.countDown();
            });

            eventBus.publish(AuditEvent.commandExecuted("/cmd1"));
            eventBus.publish(ProcessLifecycleEvent.start("TEST", "123"));
            eventBus.publish(SystemErrorEvent.warning("Test", "Warning message"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(3, counter.get());
        }
    }

    @Nested
    @DisplayName("Tipos de Eventos")
    class EventTypes {
        @Test
        @DisplayName("AuditEvent contiene información correcta")
        void auditEventHasCorrectInfo() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AuditEvent> received = new AtomicReference<>();

            eventBus.subscribe(AuditEvent.class, event -> {
                received.set(event);
                latch.countDown();
            });

            eventBus.publish(AuditEvent.userAction("testUser", "LOGIN", "Successful login"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            AuditEvent event = received.get();
            assertNotNull(event);
            assertEquals("testUser", event.user());
            assertEquals("LOGIN", event.action());
            assertEquals("Successful login", event.detail());
            assertNotNull(event.eventId());
            assertNotNull(event.timestamp());
        }

        @Test
        @DisplayName("ProcessLifecycleEvent captura ciclo de vida")
        void processLifecycleEventCapturesLifecycle() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ProcessLifecycleEvent> received = new AtomicReference<>();

            eventBus.subscribe(ProcessLifecycleEvent.class, event -> {
                received.set(event);
                latch.countDown();
            });

            eventBus.publish(ProcessLifecycleEvent.start("BUILD", "build-123"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            ProcessLifecycleEvent event = received.get();
            assertNotNull(event);
            assertEquals("BUILD", event.operationName());
            assertEquals("build-123", event.processId());
            assertEquals(ProcessLifecycleEvent.Stage.START, event.stage());
        }

        @Test
        @DisplayName("SystemErrorEvent captura errores con severidad")
        void systemErrorEventCapturesErrors() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<SystemErrorEvent> received = new AtomicReference<>();

            eventBus.subscribe(SystemErrorEvent.class, event -> {
                received.set(event);
                latch.countDown();
            });

            Exception testError = new RuntimeException("Test error");
            eventBus.publish(SystemErrorEvent.critical("TestService", testError, "Critical failure"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            SystemErrorEvent event = received.get();
            assertNotNull(event);
            assertEquals("TestService", event.source());
            assertEquals(AppEvent.Severity.CRITICAL, event.severity());
            assertTrue(event.isCritical());
            assertNotNull(event.error());
        }
    }

    @Nested
    @DisplayName("Métricas del EventBus")
    class EventBusMetrics {
        @Test
        @DisplayName("Métricas rastrean eventos publicados")
        void metricsTrackPublishedEvents() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(5);

            eventBus.subscribe(AuditEvent.class, event -> latch.countDown());

            for (int i = 0; i < 5; i++) {
                eventBus.publish(AuditEvent.commandExecuted("/cmd" + i));
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS));

            EventBus.EventBusMetrics metrics = eventBus.getMetrics();
            assertEquals(5, metrics.publishedEvents());
        }

        @Test
        @DisplayName("Métricas rastrean conteo de listeners")
        void metricsTrackListenerCount() {
            eventBus.subscribe(AuditEvent.class, e -> {});
            eventBus.subscribe(AuditEvent.class, e -> {});
            eventBus.subscribe(SystemErrorEvent.class, e -> {});
            eventBus.subscribe(e -> {});

            EventBus.EventBusMetrics metrics = eventBus.getMetrics();
            assertTrue(metrics.typedListenerCount() >= 3);
            assertTrue(metrics.globalListenerCount() >= 1);
        }
    }

    @Nested
    @DisplayName("Concurrencia y Virtual Threads")
    class ConcurrencyTests {
        @Test
        @DisplayName("Maneja múltiples publicaciones concurrentes")
        void handlesMultipleConcurrentPublications() throws InterruptedException {
            int eventCount = 100;
            CountDownLatch latch = new CountDownLatch(eventCount);
            AtomicInteger received = new AtomicInteger(0);

            eventBus.subscribe(AuditEvent.class, event -> {
                received.incrementAndGet();
                latch.countDown();
            });

            for (int i = 0; i < eventCount; i++) {
                final int idx = i;
                Thread.ofVirtual().start(() ->
                    eventBus.publish(AuditEvent.commandExecuted("/concurrent-" + idx))
                );
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Todos los eventos deberían ser procesados");
            assertEquals(eventCount, received.get());
        }

        @Test
        @DisplayName("Ambos listeners reciben el evento (ejecución paralela con Virtual Threads)")
        void bothListenersReceiveEvent() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            AtomicBoolean listener1Called = new AtomicBoolean(false);
            AtomicBoolean listener2Called = new AtomicBoolean(false);

            eventBus.subscribe(AuditEvent.class, event -> {
                listener1Called.set(true);
                latch.countDown();
            });

            eventBus.subscribe(AuditEvent.class, event -> {
                listener2Called.set(true);
                latch.countDown();
            });

            eventBus.publish(AuditEvent.commandExecuted("/test"));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Ambos listeners deberían ejecutarse");
            assertTrue(listener1Called.get(), "Listener 1 debería ser llamado");
            assertTrue(listener2Called.get(), "Listener 2 debería ser llamado");
        }
    }

    @Nested
    @DisplayName("Modo Debug")
    class DebugMode {
        @Test
        @DisplayName("setDebugMode no causa errores")
        void setDebugModeWorks() {
            assertDoesNotThrow(() -> {
                eventBus.setDebugMode(true);
                eventBus.setDebugMode(false);
            });
        }
    }

    @Nested
    @DisplayName("Manejo de Errores en Listeners")
    class ErrorHandling {
        @Test
        @DisplayName("Error en un listener no afecta a otros")
        void errorInOneListenerDoesNotAffectOthers() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean secondListenerCalled = new AtomicBoolean(false);

            eventBus.subscribe(AuditEvent.class, event -> {
                throw new RuntimeException("Listener error");
            });

            eventBus.subscribe(AuditEvent.class, event -> {
                secondListenerCalled.set(true);
                latch.countDown();
            });

            eventBus.publish(AuditEvent.commandExecuted("/test"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(secondListenerCalled.get(),
                "Segundo listener debería ejecutarse a pesar del error en el primero");
        }
    }

    @Nested
    @DisplayName("Shutdown")
    class ShutdownTests {
        @Test
        @DisplayName("shutdown() completa sin errores")
        void shutdownCompletesWithoutErrors() {
            EventBus bus = new EventBus();
            bus.subscribe(AuditEvent.class, e -> {});

            assertDoesNotThrow(bus::shutdown);
        }
    }
}
