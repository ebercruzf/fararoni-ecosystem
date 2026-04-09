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
import dev.fararoni.core.core.mission.model.AgentTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class ReactiveSwarmAgent {

    private static final Logger LOG = Logger.getLogger(ReactiveSwarmAgent.class.getName());

    protected static final String TASK_TOPIC_PREFIX = "swarm.task.";
    protected static final String COMPENSATION_TOPIC_PREFIX = "swarm.compensation.";
    protected static final String RESULT_TOPIC_PREFIX = "swarm.result.";
    protected static final String TELEMETRY_TOPIC = "swarm.telemetry";
    protected static final String DLQ_TOPIC = "swarm.dlq";

    protected final String agentId;
    protected final String role;
    protected final List<String> capabilities;
    protected final SovereignEventBus bus;
    protected final ExecutorService executor;
    protected volatile AgentState state;
    protected volatile boolean running;

    private final List<String> subscribedTopics = new ArrayList<>();

    private final List<java.util.concurrent.Flow.Subscription> busSubscriptions =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    protected final AgentTemplate template;

    private static volatile dev.fararoni.core.core.prompt.PromptCompactor sharedPromptCompactor;

    private static volatile String currentModelId;

    public static void setSharedPromptCompactor(dev.fararoni.core.core.prompt.PromptCompactor compactor) {
        sharedPromptCompactor = compactor;
        LOG.info("ReactiveSwarmAgent: PromptCompactor configured (Protocol Sharding active)");
    }

    public static void setCurrentModelId(String modelId) {
        currentModelId = modelId;
        LOG.fine("ReactiveSwarmAgent: Current model updated to: " + modelId);
    }

    public static String getCurrentModelId() {
        return currentModelId;
    }

    protected ReactiveSwarmAgent(AgentTemplate template, SovereignEventBus bus) {
        this.template = template;
        this.agentId = template.id() != null ? template.id() : UUID.randomUUID().toString();
        this.role = template.role();
        this.capabilities = template.capabilities() != null ?
            List.copyOf(template.capabilities()) : List.of(template.role());
        this.bus = bus;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.state = AgentState.IDLE;
        this.running = false;

        LOG.info("ReactiveSwarmAgent created from template: id=" + agentId +
                 " role=" + role + " capabilities=" + capabilities);
    }

    @Deprecated
    protected ReactiveSwarmAgent(String role, SovereignEventBus bus) {
        this.template = null;
        this.agentId = UUID.randomUUID().toString();
        this.role = role;
        this.capabilities = List.of(role);
        this.bus = bus;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.state = AgentState.IDLE;
        this.running = false;

        LOG.info("ReactiveSwarmAgent created (legacy): role=" + role + " id=" + agentId);
    }

    public void start() {
        if (running) {
            LOG.warning("Agent already running: " + agentId);
            return;
        }

        running = true;
        state = AgentState.IDLE;
        subscribedTopics.clear();

        if (template != null && template.metadata() != null) {
            Object execMode = template.metadata().get("executionMode");
            if ("direct".equals(String.valueOf(execMode))) {
                LOG.info("Agent '" + agentId + "' en modo STANDBY (executionMode=direct). "
                    + "No se suscribe al bus de tareas. Invocable solo directamente.");
                publishTelemetry("STANDBY",
                    "Agent registered in standby mode (executionMode=direct, capabilities="
                    + capabilities + ")");
                return;
            }
        }

        for (String capability : capabilities) {
            String taskTopic = TASK_TOPIC_PREFIX + capability;
            String compTopic = COMPENSATION_TOPIC_PREFIX + capability;

            var taskSub = bus.subscribe(taskTopic, Object.class, this::handleTaskEnvelope);
            if (taskSub != null) busSubscriptions.add(taskSub);
            subscribedTopics.add(taskTopic);

            var compSub = bus.subscribe(compTopic, Object.class, this::handleCompensationEnvelope);
            if (compSub != null) busSubscriptions.add(compSub);
            subscribedTopics.add(compTopic);

            LOG.info("Agent '" + agentId + "' subscribed to: " + taskTopic);
        }

        LOG.info("Agent started: id=" + agentId +
                 " role=" + role +
                 " capabilities=" + capabilities +
                 " topics=" + subscribedTopics.size());

        publishTelemetry("STARTED", "Agent initialized with " + capabilities.size() + " capabilities");
    }

    public void stop() {
        shutdown();
    }

    public void shutdown() {
        if (!running) {
            return;
        }

        LOG.info("Shutting down agent: " + agentId);

        running = false;
        state = AgentState.STOPPED;

        int subCount = busSubscriptions.size();
        for (var sub : busSubscriptions) {
            try {
                sub.cancel();
            } catch (Exception e) {
                LOG.warning("Error cancelling subscription for agent " + agentId
                    + ": " + e.getMessage());
            }
        }
        busSubscriptions.clear();
        LOG.info("Agent '" + agentId + "' cancelled " + subCount + " bus subscriptions");

        int topicCount = subscribedTopics.size();
        subscribedTopics.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.warning("Executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOG.info("Agent shutdown complete: id=" + agentId +
                 " topics_released=" + topicCount);
        publishTelemetry("STOPPED", "Agent shutdown");
    }

    @SuppressWarnings("unchecked")
    private void handleTaskEnvelope(SovereignEnvelope<Object> envelope) {
        System.out.println("[AGENT:" + agentId + "] Tarea recibida!");
        System.out.println("[AGENT:" + agentId + "] CorrelationId: " + envelope.correlationId());

        if (!running) {
            System.out.println("[AGENT:" + agentId + "] [WARN] Ignorando - agente detenido");
            LOG.warning("Ignoring task - agent not running: " + role);
            return;
        }

        executor.submit(() -> {
            System.out.println("[AGENT:" + agentId + "] THINKING...");
            state = AgentState.THINKING;
            publishTelemetry("THINKING", "Processing task");

            try {
                System.out.println("[AGENT:" + agentId + "] EXECUTING...");
                state = AgentState.EXECUTING;
                publishTelemetry("EXECUTING", "Executing task");

                AgentResult result = processTask(envelope);

                String promptInfo = " [prompt=" + lastPromptVariant
                        + (lastPromptModel != null ? " model=" + lastPromptModel : "") + "]";

                if (result.success()) {
                    System.out.println("[AGENT:" + agentId + "] [OK] COMPLETED: " + result.message());
                    state = AgentState.COMPLETED;
                    publishTelemetry("COMPLETED" + promptInfo, result.message());
                    publishResult(envelope, result, "success");
                } else {
                    System.out.println("[AGENT:" + agentId + "] [ERROR] FAILED: " + result.message());
                    state = AgentState.FAILED;
                    publishTelemetry("FAILED" + promptInfo, result.message());
                    publishResult(envelope, result, "failure");
                }

            } catch (Exception e) {
                System.out.println("[AGENT:" + agentId + "] [ERROR] " + e.getMessage());
                LOG.log(Level.SEVERE, "Task failed: " + role, e);
                state = AgentState.FAILED;
                publishTelemetry("ERROR", e.getMessage());
                publishResult(envelope, AgentResult.failure(e.getMessage()), "failure");
            } finally {
                state = AgentState.IDLE;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleCompensationEnvelope(SovereignEnvelope<Object> envelope) {
        if (!running) {
            LOG.warning("Ignoring compensation - agent not running: " + role);
            return;
        }

        executor.submit(() -> {
            state = AgentState.COMPENSATING;
            publishTelemetry("COMPENSATING", "Executing rollback");

            try {
                AgentResult result = compensateTask(envelope);

                if (result.success()) {
                    publishTelemetry("COMPENSATION_COMPLETED", result.message());
                    publishResult(envelope, result, "compensation_success");
                } else {
                    LOG.severe("Compensation failed - sending to DLQ: " + role);
                    publishToDLQ(envelope, result.message());
                }

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Compensation failed: " + role, e);
                publishToDLQ(envelope, e.getMessage());
            } finally {
                state = AgentState.IDLE;
            }
        });
    }

    protected abstract AgentResult processTask(SovereignEnvelope<?> envelope);

    protected abstract AgentResult compensateTask(SovereignEnvelope<?> envelope);

    protected void publishResult(SovereignEnvelope<?> original, AgentResult result, String status) {
        String topic = RESULT_TOPIC_PREFIX + role;

        SovereignEnvelope<AgentResultPayload> resultEnvelope = SovereignEnvelope.createSecure(
            original.userId(),
            role,
            original.traceId(),
            topic,
            new AgentResultPayload(
                original.id(),
                role,
                status,
                result.message(),
                result.data()
            )
        ).withCorrelation(original.correlationId());

        bus.publish(topic, resultEnvelope);
    }

    protected void publishToDLQ(SovereignEnvelope<?> original, String reason) {
        SovereignEnvelope<DLQPayload> dlqEnvelope = SovereignEnvelope.createSecure(
            original.userId(),
            role,
            original.traceId(),
            DLQ_TOPIC,
            new DLQPayload(original.id(), role, reason, original.payload())
        );

        bus.publish(DLQ_TOPIC, dlqEnvelope);
    }

    protected void publishTelemetry(String action, String message) {
        if (bus == null) return;

        try {
            SovereignEnvelope<TelemetryPayload> telemetry = SovereignEnvelope.createSecure(
                "system",
                role,
                null,
                TELEMETRY_TOPIC,
                new TelemetryPayload(agentId, role, action, message, state.name())
            );

            bus.publish(TELEMETRY_TOPIC, telemetry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to publish telemetry", e);
        }
    }

    protected void logThinking(String thought) {
        publishTelemetry("THINKING", thought);
    }

    protected void logAction(String action) {
        publishTelemetry("ACTION", action);
    }

    public String getAgentId() {
        return agentId;
    }

    public String getRole() {
        return role;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public AgentTemplate getTemplate() {
        return template;
    }

    private static final String SOVEREIGN_RIGOR_DIRECTIVE = """

        ═══════════════════════════════════════════════════════════════
        DIRECTIVA DE RIGOR SOBERANO (aplica a todos los agentes)
        ═══════════════════════════════════════════════════════════════
        PROHIBIDO SUPONER. Si no lo verificaste, no lo afirmes.
        - Antes de reportar éxito: ¿qué evidencia concreta lo confirma?
        - Antes de proponer un cambio: ¿leíste el código actual o estás
          adivinando?
        - Antes de descartar una opción: ¿la evaluaste o solo la ignoraste?
        - Si no puedes verificar algo, dilo explícitamente en lugar de
          asumir que funciona.
        Ser riguroso NO contradice ser eficiente. Suponer y fallar
        desperdicia más tiempo que verificar y acertar.
        ═══════════════════════════════════════════════════════════════
        """;

    public enum PromptVariant { PROSE_CLOUD, JSON_LOCAL, JSON_EDGE }
    private volatile PromptVariant lastPromptVariant = PromptVariant.PROSE_CLOUD;
    private volatile String lastPromptModel;

    public PromptVariant getLastPromptVariant() { return lastPromptVariant; }
    public String getLastPromptModel() { return lastPromptModel; }

    public String getSystemPrompt() {
        String base;
        if (template != null && template.systemPrompt() != null) {
            base = template.systemPrompt();
        } else {
            base = "You are " + role + " agent. Process the task and return results.";
        }

        if (sharedPromptCompactor != null && currentModelId != null
                && sharedPromptCompactor.requiresCompaction(currentModelId)) {
            lastPromptModel = currentModelId;
            lastPromptVariant = sharedPromptCompactor.isEdgeModel(currentModelId)
                    ? PromptVariant.JSON_EDGE : PromptVariant.JSON_LOCAL;
            LOG.fine(() -> "[PROTOCOL-SHARDING] " + agentId + " → " + lastPromptVariant
                    + " (modelo: " + currentModelId + ")");

            return sharedPromptCompactor.compact(base, currentModelId, agentId);
        }

        lastPromptModel = currentModelId;
        lastPromptVariant = PromptVariant.PROSE_CLOUD;
        return base + SOVEREIGN_RIGOR_DIRECTIVE;
    }

    public List<String> getSubscribedTopics() {
        return List.copyOf(subscribedTopics);
    }

    public AgentState getState() {
        return state;
    }

    public boolean isRunning() {
        return running;
    }

    public enum AgentState {
        IDLE,
        THINKING,
        EXECUTING,
        COMPENSATING,
        COMPLETED,
        FAILED,
        STOPPED
    }

    public record AgentResult(boolean success, String message, Object data) {
        public static AgentResult success(String message) {
            return new AgentResult(true, message, null);
        }

        public static AgentResult success(String message, Object data) {
            return new AgentResult(true, message, data);
        }

        public static AgentResult failure(String message) {
            return new AgentResult(false, message, null);
        }
    }

    public record AgentResultPayload(
        String originalEnvelopeId,
        String agentRole,
        String status,
        String message,
        Object data
    ) {}

    public record TelemetryPayload(
        String agentId,
        String role,
        String action,
        String message,
        String state
    ) {}

    public record DLQPayload(
        String originalEnvelopeId,
        String agentRole,
        String reason,
        Object originalPayload
    ) {}
}
