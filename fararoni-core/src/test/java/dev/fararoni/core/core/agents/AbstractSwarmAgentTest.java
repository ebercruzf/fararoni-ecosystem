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
package dev.fararoni.core.core.agents;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.protocol.AgentMessage;
import dev.fararoni.bus.agent.api.ui.model.AgentState.AgentExecutionState;

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

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbstractSwarmAgentTest {
    @Mock
    private SovereignEventBus mockBus;

    @Captor
    private ArgumentCaptor<SovereignEnvelope<AgentMessage>> envelopeCaptor;

    private TestableSwarmAgent agent;

    private static class TestableSwarmAgent extends AbstractSwarmAgent {
        public TestableSwarmAgent(String role, SovereignEventBus bus) {
            super(role, bus);
        }

        public void testLogAction(String action) {
            logAction(action);
        }

        public void testLogThinking(String thought) {
            logThinking(thought);
        }

        public void testLogSuccess(String result) {
            logSuccess(result);
        }

        public void testLogError(String error) {
            logError(error);
        }

        public void testLogIdle(String waiting) {
            logIdle(waiting);
        }

        public void testUpdateStatus(String action, AgentExecutionState state) {
            updateStatus(action, state);
        }
    }

    @BeforeEach
    void setUp() {
        when(mockBus.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
        agent = new TestableSwarmAgent("TEST_AGENT", mockBus);
    }

    @Test
    @DisplayName("Constructor debe inicializar agentId, role y state")
    void constructor_shouldInitializeAllFields() {
        assertNotNull(agent.getAgentId(), "agentId no debe ser null");
        assertEquals(8, agent.getAgentId().length(), "agentId debe tener 8 caracteres");
        assertEquals("TEST_AGENT", agent.getRole(), "role debe coincidir");
        assertNotNull(agent.getState(), "state no debe ser null");
    }

    @Test
    @DisplayName("TELEMETRY_TOPIC debe ser 'sys.telemetry.agents'")
    void telemetryTopic_shouldBeCorrect() {
        assertEquals("sys.telemetry.agents", AbstractSwarmAgent.TELEMETRY_TOPIC);
    }

    @Test
    @DisplayName("logAction() debe publicar estado EXECUTING al bus")
    void logAction_shouldPublishExecutingState() {
        agent.testLogAction("Procesando datos...");

        verify(mockBus).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        SovereignEnvelope<AgentMessage> envelope = envelopeCaptor.getValue();
        AgentMessage message = envelope.payload();

        assertEquals(AgentMessage.TYPE_STATUS_UPDATE, message.type());
        assertEquals("TEST_AGENT", message.senderRole());
        assertEquals("EXECUTING", message.getContent("status"));
        assertEquals("Procesando datos...", message.naturalLanguageHint());
    }

    @Test
    @DisplayName("logThinking() debe publicar estado PLANNING al bus")
    void logThinking_shouldPublishPlanningState() {
        agent.testLogThinking("Analizando estructura...");

        verify(mockBus).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        AgentMessage message = envelopeCaptor.getValue().payload();

        assertEquals("PLANNING", message.getContent("status"));
        assertEquals("Analizando estructura...", message.naturalLanguageHint());
    }

    @Test
    @DisplayName("logSuccess() debe publicar estado COMPLETED al bus")
    void logSuccess_shouldPublishCompletedState() {
        agent.testLogSuccess("Tarea completada exitosamente");

        verify(mockBus).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        AgentMessage message = envelopeCaptor.getValue().payload();

        assertEquals("COMPLETED", message.getContent("status"));
        assertEquals("Tarea completada exitosamente", message.naturalLanguageHint());
    }

    @Test
    @DisplayName("logError() debe publicar estado FAILED al bus")
    void logError_shouldPublishFailedState() {
        agent.testLogError("Error de conexión");

        verify(mockBus).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        AgentMessage message = envelopeCaptor.getValue().payload();

        assertEquals("FAILED", message.getContent("status"));
        assertEquals("Error de conexión", message.naturalLanguageHint());
    }

    @Test
    @DisplayName("logIdle() debe publicar estado PAUSED al bus")
    void logIdle_shouldPublishPausedState() {
        agent.testLogIdle("Esperando eventos...");

        verify(mockBus).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        AgentMessage message = envelopeCaptor.getValue().payload();

        assertEquals("PAUSED", message.getContent("status"));
        assertEquals("Esperando eventos...", message.naturalLanguageHint());
    }

    @Test
    @DisplayName("updateStatus() debe actualizar AgentState interno")
    void updateStatus_shouldUpdateInternalState() {
        agent.testUpdateStatus("Ejecutando operación", AgentExecutionState.EXECUTING);

        assertEquals("Ejecutando operación", agent.getState().getCurrentAction());
        assertEquals(AgentExecutionState.EXECUTING, agent.getState().getExecutionState());
    }

    @Test
    @DisplayName("Múltiples llamadas deben actualizar el estado progresivamente")
    void multipleUpdates_shouldTrackStateProgression() {
        agent.testLogAction("Iniciando...");
        agent.testLogThinking("Analizando...");
        agent.testLogAction("Ejecutando...");
        agent.testLogSuccess("Completado");

        verify(mockBus, times(4)).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), any());

        assertEquals(AgentExecutionState.COMPLETED, agent.getState().getExecutionState());
        assertEquals("Completado", agent.getState().getCurrentAction());
    }

    @Test
    @DisplayName("Envelope debe contener userId='system' y traceId con agentId")
    void envelope_shouldContainCorrectMetadata() {
        agent.testLogAction("Test");

        verify(mockBus).publish(eq(AbstractSwarmAgent.TELEMETRY_TOPIC), envelopeCaptor.capture());

        SovereignEnvelope<AgentMessage> envelope = envelopeCaptor.getValue();

        assertEquals("system", envelope.userId());
        assertTrue(envelope.traceId().startsWith("trace-"), "traceId debe empezar con 'trace-'");
        assertTrue(envelope.traceId().contains(agent.getAgentId().substring(0, 4)),
            "traceId debe contener parte del agentId");
    }

    @Test
    @DisplayName("Diferentes agentes deben tener diferentes agentIds")
    void differentAgents_shouldHaveDifferentIds() {
        TestableSwarmAgent agent1 = new TestableSwarmAgent("AGENT_1", mockBus);
        TestableSwarmAgent agent2 = new TestableSwarmAgent("AGENT_2", mockBus);

        assertNotEquals(agent1.getAgentId(), agent2.getAgentId(),
            "Cada agente debe tener un ID único");
    }
}
