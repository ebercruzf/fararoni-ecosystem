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
package dev.fararoni.core.core.resilience;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.protocol.AgentMessage;
import dev.fararoni.bus.agent.api.ui.model.AgentState.AgentExecutionState;
import dev.fararoni.core.core.agents.AbstractSwarmAgent;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GalvanicAgentStateBroadcastingTest {
    @Mock
    private SovereignEventBus mockBus;

    @Captor
    private ArgumentCaptor<SovereignEnvelope<AgentMessage>> envelopeCaptor;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    private GalvanicAgent galvanic;

    @BeforeEach
    void setUp() {
        when(mockBus.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        galvanic = new GalvanicAgent(mockBus);
    }

    @Test
    @DisplayName("GalvanicAgent debe heredar de AbstractSwarmAgent")
    void galvanic_shouldExtendAbstractSwarmAgent() {
        assertTrue(galvanic instanceof AbstractSwarmAgent,
            "GalvanicAgent debe heredar de AbstractSwarmAgent");
    }

    @Test
    @DisplayName("GalvanicAgent debe tener rol 'GALVANIC'")
    void galvanic_shouldHaveCorrectRole() {
        assertEquals("GALVANIC", galvanic.getRole());
    }

    @Test
    @DisplayName("GalvanicAgent debe tener agentId de 8 caracteres")
    void galvanic_shouldHaveValidAgentId() {
        assertNotNull(galvanic.getAgentId());
        assertEquals(8, galvanic.getAgentId().length());
    }

    @Test
    @DisplayName("start() debe publicar estado EXECUTING al iniciar")
    void start_shouldPublishExecutingState() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());

        galvanic.start();

        verify(mockBus, atLeast(1)).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        List<SovereignEnvelope<AgentMessage>> envelopes = envelopeCaptor.getAllValues();

        AgentMessage firstMessage = envelopes.get(0).payload();
        assertEquals(AgentMessage.TYPE_STATUS_UPDATE, firstMessage.type());
        assertEquals("GALVANIC", firstMessage.senderRole());
        assertEquals("EXECUTING", firstMessage.getContent("status"));
        assertTrue(firstMessage.naturalLanguageHint().contains("Iniciando"),
            "Mensaje debe indicar que está iniciando");
    }

    @Test
    @DisplayName("start() debe publicar estado PAUSED (idle) después de suscribirse")
    void start_shouldPublishIdleStateAfterSubscription() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());

        galvanic.start();

        verify(mockBus, atLeast(2)).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        List<SovereignEnvelope<AgentMessage>> envelopes = envelopeCaptor.getAllValues();

        AgentMessage secondMessage = envelopes.get(1).payload();
        assertEquals("PAUSED", secondMessage.getContent("status"));
        assertTrue(secondMessage.naturalLanguageHint().contains("Vigilando"),
            "Mensaje debe indicar que está vigilando DLQ");
    }

    @Test
    @DisplayName("start() debe suscribirse al DLQ topic")
    void start_shouldSubscribeToDlqTopic() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());

        galvanic.start();

        verify(mockBus).subscribe(eq("sys.dlq.main"), eq(PoisonPill.class), any());
    }

    @Test
    @DisplayName("start() no debe publicar si ya está corriendo")
    void start_shouldNotPublishIfAlreadyRunning() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());

        galvanic.start();
        int firstCallCount = mockitoPublishCallCount();

        galvanic.start();

        assertEquals(firstCallCount, mockitoPublishCallCount(),
            "No debe publicar nuevos estados si ya está corriendo");
    }

    @Test
    @DisplayName("stop() debe publicar estado EXECUTING al detenerse")
    void stop_shouldPublishExecutingState() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());
        galvanic.start();

        clearInvocations(mockBus);
        when(mockBus.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        galvanic.stop();

        verify(mockBus).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        AgentMessage message = envelopeCaptor.getValue().payload();
        assertEquals("EXECUTING", message.getContent("status"));
        assertTrue(message.naturalLanguageHint().contains("Detenido"));
    }

    @Test
    @DisplayName("isRunning() debe retornar false antes de start()")
    void isRunning_shouldReturnFalseBeforeStart() {
        assertFalse(galvanic.isRunning());
    }

    @Test
    @DisplayName("isRunning() debe retornar true después de start()")
    void isRunning_shouldReturnTrueAfterStart() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());

        galvanic.start();

        assertTrue(galvanic.isRunning());
    }

    @Test
    @DisplayName("isRunning() debe retornar false después de stop()")
    void isRunning_shouldReturnFalseAfterStop() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());

        galvanic.start();
        galvanic.stop();

        assertFalse(galvanic.isRunning());
    }

    @Test
    @DisplayName("getState() debe reflejar el estado actual del agente")
    void getState_shouldReflectCurrentAgentState() {
        doNothing().when(mockBus).subscribe(anyString(), any(), any());

        galvanic.start();

        assertEquals(AgentExecutionState.PAUSED, galvanic.getState().getExecutionState(),
            "Estado final después de start() debe ser PAUSED (vigilando)");
    }

    @Test
    @DisplayName("Procesar PoisonPill debe publicar secuencia de estados: PLANNING -> EXECUTING/FAILED -> PAUSED")
    @SuppressWarnings("unchecked")
    void processPoisonPill_shouldPublishStateSequence() {
        ArgumentCaptor<Consumer<SovereignEnvelope<PoisonPill>>> consumerCaptor =
            ArgumentCaptor.forClass(Consumer.class);

        doNothing().when(mockBus).subscribe(eq("sys.dlq.main"), eq(PoisonPill.class), consumerCaptor.capture());

        galvanic.start();

        SovereignEnvelope<?> originalEnvelope = SovereignEnvelope.create("test", "trace-1", "test-payload");
        PoisonPill poisonPill = new PoisonPill(
            "test.topic",
            "NullPointerException",
            "java.lang.NullPointerException\n\tat Test.java:1",
            "TestConsumer",
            Instant.now(),
            originalEnvelope
        );
        SovereignEnvelope<PoisonPill> dlqEnvelope = SovereignEnvelope.create("dlq", "dlq-trace", poisonPill);

        clearInvocations(mockBus);
        when(mockBus.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        Consumer<SovereignEnvelope<PoisonPill>> ritualHandler = consumerCaptor.getValue();
        ritualHandler.accept(dlqEnvelope);

        verify(mockBus, atLeast(3)).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        List<SovereignEnvelope<AgentMessage>> envelopes = envelopeCaptor.getAllValues();

        assertEquals("PLANNING", envelopes.get(0).payload().getContent("status"));
        assertTrue(envelopes.get(0).payload().naturalLanguageHint().contains("Analizando"));

        assertEquals("FAILED", envelopes.get(1).payload().getContent("status"));
        assertTrue(envelopes.get(1).payload().naturalLanguageHint().contains("Muerte definitiva"));

        assertEquals("PAUSED", envelopes.get(2).payload().getContent("status"));
        assertTrue(envelopes.get(2).payload().naturalLanguageHint().contains("Vigilando"));
    }

    @Test
    @DisplayName("Procesar PoisonPill con error transitorio debe intentar resurrección")
    @SuppressWarnings("unchecked")
    void processPoisonPill_transientError_shouldAttemptResurrection() {
        ArgumentCaptor<Consumer<SovereignEnvelope<PoisonPill>>> consumerCaptor =
            ArgumentCaptor.forClass(Consumer.class);

        doNothing().when(mockBus).subscribe(eq("sys.dlq.main"), eq(PoisonPill.class), consumerCaptor.capture());

        galvanic.start();

        SovereignEnvelope<?> originalEnvelope = SovereignEnvelope.create("test", "trace-1", "test-payload");
        PoisonPill poisonPill = new PoisonPill(
            "test.topic",
            "Connection timeout after 30s",
            "java.util.concurrent.TimeoutException\n\tat Net.java:1",
            "TestConsumer",
            Instant.now(),
            originalEnvelope
        );
        SovereignEnvelope<PoisonPill> dlqEnvelope = SovereignEnvelope.create("dlq", "dlq-trace", poisonPill);

        clearInvocations(mockBus);
        when(mockBus.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        Consumer<SovereignEnvelope<PoisonPill>> ritualHandler = consumerCaptor.getValue();
        ritualHandler.accept(dlqEnvelope);

        verify(mockBus, atLeast(3)).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        List<SovereignEnvelope<AgentMessage>> envelopes = envelopeCaptor.getAllValues();

        boolean foundResurrection = envelopes.stream()
            .map(SovereignEnvelope::payload)
            .anyMatch(msg -> "EXECUTING".equals(msg.getContent("status")) &&
                           msg.naturalLanguageHint().contains("Resucitando"));

        assertTrue(foundResurrection, "Debe haber intentado resurrección para error transitorio");

        verify(mockBus).publish(eq("test.topic"), any());
    }

    private int mockitoPublishCallCount() {
        return mockingDetails(mockBus).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("publish"))
            .mapToInt(inv -> 1)
            .sum();
    }
}
