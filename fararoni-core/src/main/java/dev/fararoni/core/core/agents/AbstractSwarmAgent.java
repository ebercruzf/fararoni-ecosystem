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
import dev.fararoni.bus.agent.api.ui.model.AgentState;
import dev.fararoni.bus.agent.api.ui.model.AgentState.AgentExecutionState;

import java.util.UUID;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public abstract class AbstractSwarmAgent {
    protected final String agentId;

    protected final String role;

    protected final SovereignEventBus bus;

    protected final AgentState state;

    public static final String TELEMETRY_TOPIC = "sys.telemetry.agents";

    protected AbstractSwarmAgent(String role, SovereignEventBus bus) {
        this.agentId = UUID.randomUUID().toString().substring(0, 8);
        this.role = role;
        this.bus = bus;
        this.state = new AgentState("Role: " + role);
    }

    protected void updateStatus(String action, AgentExecutionState execState) {
        state.setCurrentAction(action);
        state.setExecutionState(execState);

        var message = AgentMessage.statusUpdate(
            this.role,
            execState.name(),
            action
        );

        var envelope = SovereignEnvelope.create(
            "system",
            "trace-" + agentId,
            message
        );

        bus.publish(TELEMETRY_TOPIC, envelope);
    }

    protected void logThinking(String thought) {
        updateStatus(thought, AgentExecutionState.PLANNING);
    }

    protected void logAction(String action) {
        updateStatus(action, AgentExecutionState.EXECUTING);
    }

    protected void logSuccess(String result) {
        updateStatus(result, AgentExecutionState.COMPLETED);
    }

    protected void logError(String error) {
        updateStatus(error, AgentExecutionState.FAILED);
    }

    protected void logIdle(String waitingFor) {
        updateStatus(waitingFor, AgentExecutionState.PAUSED);
    }

    public String getAgentId() {
        return agentId;
    }

    public String getRole() {
        return role;
    }

    public AgentState getState() {
        return state;
    }

    protected int beginTask(String description) {
        state.addTask(description);
        int index = state.getTasks().size() - 1;

        state.startTask(index);

        updateStatus(description, AgentExecutionState.EXECUTING);

        return index;
    }

    protected void finishTask(int taskIndex, String result) {
        state.completeTask(taskIndex);

        if (taskIndex >= 0 && taskIndex < state.getTasks().size()) {
            state.getTasks().get(taskIndex).setDetails(result);
        }

        updateStatus(result, AgentExecutionState.COMPLETED);
    }

    protected void failTask(int taskIndex, String error) {
        state.failTask(taskIndex, error);

        updateStatus("Error: " + error, AgentExecutionState.FAILED);
    }
}
