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
package dev.fararoni.core.core.adapter;

import dev.fararoni.bus.agent.api.io.IncomingMessage;
import dev.fararoni.bus.agent.api.io.IngestionChannel;
import dev.fararoni.core.core.event.EventBus;
import dev.fararoni.core.core.event.types.IncomingMessageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("IngestionChannel - Canales de Ingestion")
class IngestionChannelTest {
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
    @DisplayName("AbstractIngestionChannel - Clase Base")
    class AbstractIngestionChannelTests {
        @Test
        @DisplayName("Implementación básica tiene propiedades correctas")
        void basicImplementation_hasCorrectProperties() {
            TestIngestionChannel channel = new TestIngestionChannel("test-channel", "test", eventBus);

            assertEquals("test-channel", channel.getName());
            assertEquals("test", channel.getType());
            assertFalse(channel.isRunning());
        }

        @Test
        @DisplayName("start() cambia estado a running")
        void start_changesStateToRunning() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);

            channel.start();

            assertTrue(channel.isRunning());
        }

        @Test
        @DisplayName("stop() cambia estado a not running")
        void stop_changesStateToNotRunning() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);

            channel.start();
            channel.stop();

            assertFalse(channel.isRunning());
        }

        @Test
        @DisplayName("double start() lanza IllegalStateException")
        void doubleStart_throwsIllegalStateException() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);

            channel.start();
            assertThrows(IllegalStateException.class, () -> channel.start());
            assertTrue(channel.isRunning());
        }

        @Test
        @DisplayName("double stop() no causa error")
        void doubleStop_doesNotCauseError() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);

            channel.start();
            channel.stop();
            assertDoesNotThrow(() -> channel.stop());
            assertFalse(channel.isRunning());
        }

        @Test
        @DisplayName("close() invoca stop()")
        void close_invokesStop() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);
            channel.start();

            channel.close();

            assertFalse(channel.isRunning());
        }

        @Test
        @DisplayName("isHealthy() retorna estado correcto")
        void isHealthy_returnsCorrectState() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);

            assertFalse(channel.isHealthy());

            channel.start();
            assertTrue(channel.isHealthy());

            channel.stop();
            assertFalse(channel.isHealthy());
        }
    }

    @Nested
    @DisplayName("Message Handlers")
    class MessageHandlerTests {
        @Test
        @DisplayName("onMessage() handler recibe mensajes")
        void onMessage_handlerReceivesMessages() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<IncomingMessage> received = new AtomicReference<>();

            channel.onMessage(msg -> {
                received.set(msg);
                latch.countDown();
            });

            channel.start();
            channel.simulateMessage(new IncomingMessage("test", "src", "Hello"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(received.get());
            assertEquals("Hello", received.get().content());
        }

        @Test
        @DisplayName("Múltiples handlers reciben el mismo mensaje")
        void multipleHandlers_receiveTheSameMessage() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);
            CountDownLatch latch = new CountDownLatch(2);
            AtomicReference<String> handler1 = new AtomicReference<>();
            AtomicReference<String> handler2 = new AtomicReference<>();

            channel.onMessage(msg -> {
                handler1.set(msg.content());
                latch.countDown();
            });
            channel.onMessage(msg -> {
                handler2.set(msg.content());
                latch.countDown();
            });

            channel.start();
            channel.simulateMessage(new IncomingMessage("test", "src", "Hello"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals("Hello", handler1.get());
            assertEquals("Hello", handler2.get());
        }

        @Test
        @DisplayName("onError() handler recibe errores")
        void onError_handlerReceivesErrors() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> received = new AtomicReference<>();

            channel.onError(error -> {
                received.set(error);
                latch.countDown();
            });

            channel.start();
            channel.simulateError(new RuntimeException("Test error"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(received.get());
            assertEquals("Test error", received.get().getMessage());
        }
    }

    @Nested
    @DisplayName("EventBus Integration")
    class EventBusIntegrationTests {
        @Test
        @DisplayName("dispatchMessage() publica evento en EventBus")
        void dispatchMessage_publishesEventToEventBus() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test-channel", "test", eventBus);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<IncomingMessageEvent> received = new AtomicReference<>();

            eventBus.subscribe(IncomingMessageEvent.class, event -> {
                received.set(event);
                latch.countDown();
            });

            channel.start();
            channel.simulateMessage(new IncomingMessage("test", "src", "Hello from EventBus"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(received.get());
            assertEquals("test-channel", received.get().channelName());
            assertEquals("Hello from EventBus", received.get().message().content());
        }

        @Test
        @DisplayName("Canal sin EventBus no lanza excepción")
        void channelWithoutEventBus_doesNotThrow() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", null);

            channel.start();
            assertDoesNotThrow(() ->
                channel.simulateMessage(new IncomingMessage("test", "src", "Hello"))
            );
        }
    }

    @Nested
    @DisplayName("ChannelStats")
    class ChannelStatsTests {
        @Test
        @DisplayName("getStats() retorna estadísticas")
        void getStats_returnsStatistics() throws Exception {
            TestIngestionChannel channel = new TestIngestionChannel("test", "test", eventBus);
            channel.start();

            channel.simulateMessage(new IncomingMessage("test", "src1", "Msg1"));
            channel.simulateMessage(new IncomingMessage("test", "src2", "Msg2"));

            Thread.sleep(100);

            IngestionChannel.ChannelStats stats = channel.getStats();

            assertNotNull(stats);
            assertTrue(stats.messagesReceived() >= 2, "Debe tener al menos 2 mensajes recibidos");
            assertTrue(stats.uptimeMs() >= 0, "Uptime debe ser >= 0");
        }
    }

    static class TestIngestionChannel extends AbstractIngestionChannel {
        private boolean healthy = false;

        public TestIngestionChannel(String name, String type, EventBus eventBus) {
            super(name, type, eventBus);
        }

        @Override
        protected void doStart() {
            healthy = true;
        }

        @Override
        protected void doStop() {
            healthy = false;
        }

        @Override
        protected boolean checkHealth() {
            return healthy;
        }

        public void simulateMessage(IncomingMessage message) {
            dispatchMessage(message);
        }

        public void simulateError(Throwable error) {
            dispatchError(error);
        }
    }
}
