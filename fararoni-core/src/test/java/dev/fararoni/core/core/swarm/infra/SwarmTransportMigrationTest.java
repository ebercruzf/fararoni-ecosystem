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
package dev.fararoni.core.core.swarm.infra;

import dev.fararoni.core.core.bus.InMemorySovereignBus;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
class SwarmTransportMigrationTest {
    private static SwarmMessage msg(String from, String to, String type, String content) {
        return new SwarmMessage(from, to, type, content);
    }

    @Nested
    @DisplayName("MessageBus como SwarmTransport")
    class MessageBusAsSwarmTransport {
        private SwarmTransport transport;

        @BeforeEach
        void setup() {
            transport = new MessageBus();
        }

        @Test
        @DisplayName("Debe registrar y verificar agentes")
        void testRegisterAgent() {
            transport.register("TEST_AGENT");

            assertTrue(transport.isRegistered("TEST_AGENT"));
            assertTrue(transport.getRegisteredAgents().contains("TEST_AGENT"));
        }

        @Test
        @DisplayName("Debe enviar y recibir mensajes")
        void testSendReceive() throws InterruptedException {
            transport.register("SENDER");
            transport.register("RECEIVER");

            SwarmMessage message = msg("SENDER", "RECEIVER", "TEST", "Hello");
            transport.send(message);

            SwarmMessage received = transport.receive("RECEIVER", 1000, TimeUnit.MILLISECONDS);

            assertNotNull(received);
            assertEquals("SENDER", received.senderId());
            assertEquals("RECEIVER", received.receiverId());
            assertEquals("TEST", received.type());
            assertEquals("Hello", received.content());
        }

        @Test
        @DisplayName("Debe soportar broadcast")
        void testBroadcast() throws InterruptedException {
            transport.register("AGENT_A");
            transport.register("AGENT_B");

            transport.broadcast("SYSTEM", "PING", "broadcast test");

            SwarmMessage msgA = transport.receive("AGENT_A", 1000, TimeUnit.MILLISECONDS);
            SwarmMessage msgB = transport.receive("AGENT_B", 1000, TimeUnit.MILLISECONDS);

            assertNotNull(msgA);
            assertNotNull(msgB);
            assertEquals("PING", msgA.type());
            assertEquals("PING", msgB.type());
        }

        @Test
        @DisplayName("Debe proporcionar métricas")
        void testMetrics() {
            transport.register("A");
            transport.register("B");
            transport.send(msg("A", "B", "TEST", "msg"));

            SwarmTransport.BusMetrics metrics = transport.getMetrics();

            assertNotNull(metrics);
            assertEquals(1, metrics.messagesSent());
            assertEquals(2, metrics.registeredAgents());
        }
    }

    @Nested
    @DisplayName("ProtocolTranslator")
    class ProtocolTranslatorTests {
        @Test
        @DisplayName("Debe traducir Legacy → Aegis sin pérdida")
        void testLegacyToAegis() {
            SwarmMessage legacy = SwarmMessage.builder()
                .from("PM")
                .to("DEV")
                .type("TASK")
                .content("Implementar feature X")
                .correlationId("corr-123")
                .build();

            SovereignEnvelope<SwarmMessage> envelope = ProtocolTranslator.toSovereign(legacy);

            assertNotNull(envelope);
            assertEquals("PM", envelope.senderRole());
            assertEquals("corr-123", envelope.correlationId());
            assertTrue(envelope.traceId().startsWith("trace-swarm-"));

            SwarmMessage payload = envelope.payload();
            assertEquals(legacy.id(), payload.id());
            assertEquals(legacy.senderId(), payload.senderId());
            assertEquals(legacy.receiverId(), payload.receiverId());
            assertEquals(legacy.type(), payload.type());
            assertEquals(legacy.content(), payload.content());
        }

        @Test
        @DisplayName("Debe traducir Aegis → Legacy sin pérdida")
        void testAegisToLegacy() {
            SwarmMessage original = msg("QA", "PM", "REPORT", "Tests passed");
            SovereignEnvelope<SwarmMessage> envelope = ProtocolTranslator.toSovereign(original);

            SwarmMessage extracted = ProtocolTranslator.fromSovereign(envelope);

            assertEquals(original.id(), extracted.id());
            assertEquals(original.senderId(), extracted.senderId());
            assertEquals(original.receiverId(), extracted.receiverId());
            assertEquals(original.type(), extracted.type());
            assertEquals(original.content(), extracted.content());
        }

        @Test
        @DisplayName("Debe generar tópicos correctos")
        void testTopicGeneration() {
            assertEquals("agent.swarm.pm.inbox", ProtocolTranslator.toTopic("PM"));
            assertEquals("agent.swarm.dev.inbox", ProtocolTranslator.toTopic("DEV"));
            assertEquals("agent.swarm.broadcast", ProtocolTranslator.toTopic(null));
            assertEquals("agent.swarm.broadcast", ProtocolTranslator.toTopic(""));
        }

        @Test
        @DisplayName("Debe extraer agentId de tópicos")
        void testAgentIdExtraction() {
            assertEquals("pm", ProtocolTranslator.extractAgentId("agent.swarm.pm.inbox"));
            assertEquals("dev", ProtocolTranslator.extractAgentId("agent.swarm.dev.inbox"));
            assertNull(ProtocolTranslator.extractAgentId("other.topic"));
        }

        @Test
        @DisplayName("Debe identificar tópicos del Swarm")
        void testSwarmTopicIdentification() {
            assertTrue(ProtocolTranslator.isSwarmTopic("agent.swarm.pm.inbox"));
            assertTrue(ProtocolTranslator.isSwarmTopic("agent.swarm.broadcast"));
            assertFalse(ProtocolTranslator.isSwarmTopic("sys.telemetry.agents"));
        }
    }

    @Nested
    @DisplayName("SovereignBridgeBus como SwarmTransport")
    class SovereignBridgeBusTests {
        private InMemorySovereignBus sovereignBus;
        private SwarmTransport transport;

        @BeforeEach
        void setup() {
            sovereignBus = new InMemorySovereignBus();
            transport = new SovereignBridgeBus(sovereignBus);
        }

        @Test
        @DisplayName("Debe registrar y verificar agentes")
        void testRegisterAgent() {
            transport.register("TEST_AGENT");

            assertTrue(transport.isRegistered("TEST_AGENT"));
            assertTrue(transport.getRegisteredAgents().contains("TEST_AGENT"));
        }

        @Test
        @DisplayName("Debe enviar y recibir mensajes via bridge")
        void testSendReceiveViaBridge() throws InterruptedException {
            transport.register("SENDER");
            transport.register("RECEIVER");

            SwarmMessage message = msg("SENDER", "RECEIVER", "TEST", "Hello via Bridge");
            transport.send(message);

            SwarmMessage received = transport.receive("RECEIVER", 1000, TimeUnit.MILLISECONDS);

            assertNotNull(received, "El mensaje debe llegar al receptor");
            assertEquals("SENDER", received.senderId());
            assertEquals("RECEIVER", received.receiverId());
            assertEquals("TEST", received.type());
            assertEquals("Hello via Bridge", received.content());
        }

        @Test
        @DisplayName("Debe soportar broadcast via bridge")
        void testBroadcastViaBridge() throws InterruptedException {
            transport.register("AGENT_A");
            transport.register("AGENT_B");

            transport.broadcast("SYSTEM", "PING", "broadcast via bridge");

            SwarmMessage msgA = transport.receive("AGENT_A", 1000, TimeUnit.MILLISECONDS);
            SwarmMessage msgB = transport.receive("AGENT_B", 1000, TimeUnit.MILLISECONDS);

            assertNotNull(msgA, "AGENT_A debe recibir broadcast");
            assertNotNull(msgB, "AGENT_B debe recibir broadcast");
            assertEquals("PING", msgA.type());
            assertEquals("PING", msgB.type());
        }

        @Test
        @DisplayName("Debe proporcionar métricas")
        void testMetricsViaBridge() {
            transport.register("A");
            transport.register("B");
            transport.send(msg("A", "B", "TEST", "msg"));

            SwarmTransport.BusMetrics metrics = transport.getMetrics();

            assertNotNull(metrics);
            assertEquals(1, metrics.messagesSent());
            assertEquals(2, metrics.registeredAgents());
        }

        @Test
        @DisplayName("Debe notificar listeners globales")
        void testSubscribeAll() throws InterruptedException {
            transport.register("A");
            transport.register("B");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<SwarmMessage> captured = new AtomicReference<>();

            transport.subscribeAll(message -> {
                captured.set(message);
                latch.countDown();
            });

            transport.send(msg("A", "B", "NOTIFY", "test notification"));

            assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
            assertNotNull(captured.get());
            assertEquals("NOTIFY", captured.get().type());
        }
    }

    @Nested
    @DisplayName("Intercambiabilidad Legacy ↔ Bridge")
    class InterchangeabilityTests {
        @Test
        @DisplayName("Ambos transportes deben comportarse igual para operaciones básicas")
        void testBasicOperationsParity() throws InterruptedException {
            SwarmTransport legacy = new MessageBus();
            SwarmTransport bridge = new SovereignBridgeBus(new InMemorySovereignBus());

            for (SwarmTransport transport : new SwarmTransport[]{legacy, bridge}) {
                transport.register("PM");
                transport.register("DEV");

                SwarmMessage message = msg("PM", "DEV", "TASK", "Do something");
                transport.send(message);

                SwarmMessage received = transport.receive("DEV", 1000, TimeUnit.MILLISECONDS);

                assertNotNull(received, "Transporte " + transport.getClass().getSimpleName() + " debe entregar mensaje");
                assertEquals("TASK", received.type());
                assertEquals("Do something", received.content());

                transport.reset();
            }
        }

        @Test
        @DisplayName("Polimorfismo: método que acepta SwarmTransport funciona con ambos")
        void testPolymorphism() {
            assertDoesNotThrow(() -> useTransport(new MessageBus()));
            assertDoesNotThrow(() -> useTransport(new SovereignBridgeBus(new InMemorySovereignBus())));
        }

        private void useTransport(SwarmTransport transport) throws InterruptedException {
            transport.register("TEST");
            transport.send(msg("SYSTEM", "TEST", "PING", ""));
            SwarmMessage message = transport.receive("TEST", 500, TimeUnit.MILLISECONDS);
            assertNotNull(message);
        }
    }
}
