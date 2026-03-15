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

    protected final AgentTemplate template;

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

        for (String capability : capabilities) {
            String taskTopic = TASK_TOPIC_PREFIX + capability;
            String compTopic = COMPENSATION_TOPIC_PREFIX + capability;

            bus.subscribe(taskTopic, Object.class, this::handleTaskEnvelope);
            subscribedTopics.add(taskTopic);

            bus.subscribe(compTopic, Object.class, this::handleCompensationEnvelope);
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

                if (result.success()) {
                    System.out.println("[AGENT:" + agentId + "] [OK] COMPLETED: " + result.message());
                    state = AgentState.COMPLETED;
                    publishTelemetry("COMPLETED", result.message());
                    publishResult(envelope, result, "success");
                } else {
                    System.out.println("[AGENT:" + agentId + "] [ERROR] FAILED: " + result.message());
                    state = AgentState.FAILED;
                    publishTelemetry("FAILED", result.message());
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

    public String getSystemPrompt() {
        if (template != null && template.systemPrompt() != null) {
            return template.systemPrompt();
        }
        return "You are " + role + " agent. Process the task and return results.";
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
