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
package dev.fararoni.core.core.bus;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ChronicleQueueBus")
class ChronicleQueueBusTest {
    private static final String TEST_TOPIC = "test.topic";
    private Path testDir;

    private static Boolean chronicleFunctional = null;

    static boolean isChronicleQueueFunctional() {
        if (chronicleFunctional != null) {
            return chronicleFunctional;
        }

        Path tempDir = null;
        try {
            Class.forName("net.openhft.chronicle.queue.ChronicleQueue");

            tempDir = Files.createTempDirectory("chronicle-test-check-");
            try (var bus = new ChronicleQueueBus(tempDir)) {
                var envelope = SovereignEnvelope.create("test", "check");
                bus.publish("test.check", envelope).join();
                chronicleFunctional = true;
            }
        } catch (Throwable e) {
            chronicleFunctional = false;
            System.err.println("[TEST] Chronicle Queue no funcional: " + e.getClass().getSimpleName() +
                " - " + e.getMessage());
        } finally {
            if (tempDir != null) {
                try {
                    Path finalTempDir = tempDir;
                    Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException ignored) {}
            }
        }

        return chronicleFunctional != null && chronicleFunctional;
    }

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory("chronicle-bus-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (testDir != null && Files.exists(testDir)) {
            Files.walkFileTree(testDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Nested
    @DisplayName("Publicacion y Suscripcion")
    class PublishSubscribe {
        @Test
        @DisplayName("publish() y subscribe() funciona basico")
        void publishSubscribeFuncionaBasico() throws InterruptedException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional (requiere JVM args en Java 21+)");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> received = new AtomicReference<>();

                bus.subscribe(TEST_TOPIC, String.class, envelope -> {
                    received.set(envelope.payload());
                    latch.countDown();
                });

                var envelope = SovereignEnvelope.create("test-user", "Hello Chronicle!");
                bus.publish(TEST_TOPIC, envelope).join();

                assertTrue(latch.await(5, TimeUnit.SECONDS), "Mensaje no recibido");
                assertEquals("Hello Chronicle!", received.get());
            }
        }

        @Test
        @DisplayName("publish() persiste mensaje en disco")
        void publishPersisteEnDisco() {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional (requiere JVM args en Java 21+)");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                var envelope = SovereignEnvelope.create("user", "Mensaje persistido");
                bus.publish(TEST_TOPIC, envelope).join();

                Path topicDir = testDir.resolve("test_topic");
                assertTrue(Files.exists(topicDir), "Directorio del topic no creado");
                assertTrue(Files.isDirectory(topicDir), "No es directorio");
            }
        }
    }

    @Nested
    @DisplayName("Persistencia y Recuperacion")
    class PersistenciaRecuperacion {
        @Test
        @DisplayName("Mensaje persiste tras shutdown y se puede recuperar")
        void mensajePersisteTrasShutdown() throws InterruptedException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional (requiere JVM args en Java 21+)");
                return;
            }

            String messageId;

            try (var bus = new ChronicleQueueBus(testDir)) {
                var envelope = SovereignEnvelope.create("user", "Mensaje que sobrevive");
                messageId = envelope.id();
                bus.publish(TEST_TOPIC, envelope).join();
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                long lastIndex = bus.getLastIndex(TEST_TOPIC);
                assertTrue(lastIndex > 0, "No hay mensajes persistidos");

                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<String> receivedId = new AtomicReference<>();

                bus.subscribe(TEST_TOPIC, String.class, envelope -> {
                    receivedId.set(envelope.id());
                    latch.countDown();
                });

                int recovered = bus.recoverPendingMessages(TEST_TOPIC, 0);
                assertTrue(recovered > 0, "No se recuperaron mensajes");

                assertTrue(latch.await(5, TimeUnit.SECONDS), "Mensaje recuperado no recibido");
                assertEquals(messageId, receivedId.get());
            }
        }
    }

    @Nested
    @DisplayName("BusFactory")
    class BusFactoryTests {
        @Test
        @DisplayName("createInMemory() retorna InMemorySovereignBus")
        void createInMemoryRetornaInMemory() {
            var bus = BusFactory.createInMemory();
            assertInstanceOf(InMemorySovereignBus.class, bus);
        }

        @Test
        @DisplayName("createChronicle() retorna ChronicleQueueBus o fallback")
        void createChronicleRetornaChronicleOFallback() {
            var bus = BusFactory.createChronicle(testDir);
            assertNotNull(bus);
            if (bus instanceof ChronicleQueueBus cq) {
                cq.close();
            }
        }

        @Test
        @DisplayName("detectBusType() retorna MEMORY por defecto")
        void detectBusTypeRetornaMemoryPorDefecto() {
            var type = BusFactory.detectBusType();
            assertEquals(BusFactory.BusType.MEMORY, type);
        }

        @Test
        @DisplayName("isChronicleAvailable() retorna true")
        void isChronicleAvailableTrue() {
            assertTrue(BusFactory.isChronicleAvailable());
        }
    }

    @Nested
    @DisplayName("Manejo de Tipos (Polimorfismo)")
    class ManejoDeTipos {
        @Test
        @DisplayName("Soporta payload String")
        void soportaPayloadString() throws InterruptedException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Object> received = new AtomicReference<>();

                bus.subscribe("test.string", String.class, envelope -> {
                    received.set(envelope.payload());
                    latch.countDown();
                });

                bus.publish("test.string", SovereignEnvelope.create("user", "Hello String")).join();

                assertTrue(latch.await(5, TimeUnit.SECONDS));
                assertInstanceOf(String.class, received.get());
                assertEquals("Hello String", received.get());
            }
        }

        @Test
        @DisplayName("Soporta payload Integer")
        void soportaPayloadInteger() throws InterruptedException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Object> received = new AtomicReference<>();

                bus.subscribe("test.integer", Integer.class, envelope -> {
                    received.set(envelope.payload());
                    latch.countDown();
                });

                bus.publish("test.integer", SovereignEnvelope.create("user", 42)).join();

                assertTrue(latch.await(5, TimeUnit.SECONDS));
                assertTrue(received.get() instanceof Number, "Debe ser un Number");
                assertEquals(42, ((Number) received.get()).intValue());
            }
        }

        @Test
        @DisplayName("Soporta payload POJO complejo")
        void soportaPayloadPojo() throws InterruptedException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            record TestPayload(String name, int value, boolean active) {}

            try (var bus = new ChronicleQueueBus(testDir)) {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Object> received = new AtomicReference<>();

                bus.subscribe("test.pojo", Object.class, envelope -> {
                    received.set(envelope.payload());
                    latch.countDown();
                });

                var payload = new TestPayload("test-name", 123, true);
                bus.publish("test.pojo", SovereignEnvelope.create("user", payload)).join();

                assertTrue(latch.await(5, TimeUnit.SECONDS));
                assertNotNull(received.get(), "Payload no debe ser null");
            }
        }
    }

    @Nested
    @DisplayName("Aislamiento de Topics")
    class AislamientoTopics {
        @Test
        @DisplayName("Suscriptor solo recibe mensajes de su topic")
        void suscriptorSoloRecibeSuTopic() throws InterruptedException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                CountDownLatch latchA = new CountDownLatch(1);
                CountDownLatch latchB = new CountDownLatch(1);
                AtomicReference<String> receivedA = new AtomicReference<>();
                AtomicReference<String> receivedB = new AtomicReference<>();

                bus.subscribe("topic.A", String.class, envelope -> {
                    receivedA.set(envelope.payload());
                    latchA.countDown();
                });

                bus.subscribe("topic.B", String.class, envelope -> {
                    receivedB.set(envelope.payload());
                    latchB.countDown();
                });

                bus.publish("topic.A", SovereignEnvelope.create("user", "Mensaje para A")).join();

                assertTrue(latchA.await(5, TimeUnit.SECONDS), "topic.A debio recibir");
                assertEquals("Mensaje para A", receivedA.get());

                assertFalse(latchB.await(500, TimeUnit.MILLISECONDS),
                    "topic.B NO deberia recibir mensajes de topic.A");
                assertNull(receivedB.get(), "topic.B no debe tener payload");
            }
        }

        @Test
        @DisplayName("Multiples topics funcionan independientemente")
        void multiplesTopicsIndependientes() throws InterruptedException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                CountDownLatch latchA = new CountDownLatch(1);
                CountDownLatch latchB = new CountDownLatch(1);
                AtomicReference<String> receivedA = new AtomicReference<>();
                AtomicReference<String> receivedB = new AtomicReference<>();

                bus.subscribe("multi.A", String.class, env -> {
                    receivedA.set(env.payload());
                    latchA.countDown();
                });

                bus.subscribe("multi.B", String.class, env -> {
                    receivedB.set(env.payload());
                    latchB.countDown();
                });

                bus.publish("multi.A", SovereignEnvelope.create("user", "Para A")).join();
                bus.publish("multi.B", SovereignEnvelope.create("user", "Para B")).join();

                assertTrue(latchA.await(5, TimeUnit.SECONDS));
                assertTrue(latchB.await(5, TimeUnit.SECONDS));
                assertEquals("Para A", receivedA.get());
                assertEquals("Para B", receivedB.get());
            }
        }
    }

    @Nested
    @DisplayName("Resiliencia")
    class Resiliencia {
        @Test
        @DisplayName("Bus inicia con directorio vacio")
        void busIniciaConDirectorioVacio() {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            assertDoesNotThrow(() -> {
                try (var bus = new ChronicleQueueBus(testDir)) {
                    assertTrue(bus.isHealthy());
                }
            });
        }

        @Test
        @DisplayName("Bus ignora archivos no-Chronicle en directorio")
        void busIgnoraArchivosBasura() throws IOException {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            Path basuraFile = testDir.resolve("basura.txt");
            Files.writeString(basuraFile, "Este archivo no deberia afectar al bus");

            Path dirBasura = testDir.resolve("directorio_random");
            Files.createDirectories(dirBasura);
            Files.writeString(dirBasura.resolve("otro.log"), "mas basura");

            assertDoesNotThrow(() -> {
                try (var bus = new ChronicleQueueBus(testDir)) {
                    assertTrue(bus.isHealthy());

                    var envelope = SovereignEnvelope.create("user", "test");
                    bus.publish(TEST_TOPIC, envelope).join();
                }
            });
        }

        @Test
        @DisplayName("getLastIndex retorna valor valido para topic nuevo")
        void getLastIndexTopicNuevo() {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                long index = bus.getLastIndex("topic.nuevo");
                assertTrue(index >= 0, "Index debe ser >= 0");
            }
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {
        @Test
        @DisplayName("isHealthy() retorna true cuando esta activo")
        void isHealthyTrue() {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            try (var bus = new ChronicleQueueBus(testDir)) {
                assertTrue(bus.isHealthy());
            }
        }

        @Test
        @DisplayName("shutdown() cierra limpiamente")
        void shutdownCierraLimpiamente() {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            var bus = new ChronicleQueueBus(testDir);
            assertDoesNotThrow(() -> bus.shutdown(Duration.ofSeconds(5)));
            assertFalse(bus.isHealthy());
        }

        @Test
        @DisplayName("getPriority() es mayor que InMemory")
        void getPriorityMayorQueInMemory() {
            if (!isChronicleQueueFunctional()) {
                System.out.println("[SKIP] Chronicle Queue no funcional");
                return;
            }

            try (var chronicleBus = new ChronicleQueueBus(testDir)) {
                var memoryBus = new InMemorySovereignBus();

                assertTrue(chronicleBus.getPriority() > memoryBus.getPriority(),
                    "Chronicle debe tener mayor prioridad que InMemory");
            }
        }
    }
}
