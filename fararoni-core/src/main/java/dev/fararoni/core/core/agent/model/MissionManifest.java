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
package dev.fararoni.core.core.agent.model;

import java.util.List;
import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record MissionManifest(
    String missionId,
    String description,
    String version,
    List<AgentEntry> agents,
    Map<String, String> metadata
) {
    public MissionManifest {
        if (missionId == null || missionId.isBlank()) {
            throw new IllegalArgumentException("missionId no puede ser null o vacio");
        }
        if (description == null) {
            description = "";
        }
        if (version == null || version.isBlank()) {
            version = "1.0";
        }
        agents = agents != null ? List.copyOf(agents) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public AgentEntry getAgent(String agentId) {
        return agents.stream()
            .filter(a -> a.id().equals(agentId))
            .findFirst()
            .orElse(null);
    }

    public List<AgentEntry> getRootAgents() {
        return agents.stream()
            .filter(a -> a.dependsOn() == null || a.dependsOn().isEmpty())
            .toList();
    }

    public boolean isValidFlow() {
        for (AgentEntry agent : agents) {
            if (agent.dependsOn() != null) {
                for (String dep : agent.dependsOn()) {
                    if (getAgent(dep) == null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public record AgentEntry(
        String id,
        String templateRef,
        AgentInstanceConfig.WiringConfig wiring,
        AgentInstanceConfig.RoutingConfig routing,
        List<String> dependsOn,
        Map<String, String> variables
    ) {
        public AgentEntry {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id de agente no puede ser null o vacio");
            }
            if (templateRef == null || templateRef.isBlank()) {
                throw new IllegalArgumentException("templateRef no puede ser null o vacio");
            }
            if (wiring == null) {
                wiring = AgentInstanceConfig.WiringConfig.defaults();
            }
            if (routing == null) {
                routing = AgentInstanceConfig.RoutingConfig.defaults();
            }
            dependsOn = dependsOn != null ? List.copyOf(dependsOn) : List.of();
            variables = variables != null ? Map.copyOf(variables) : Map.of();
        }

        public AgentInstanceConfig toInstanceConfig() {
            return new AgentInstanceConfig(id, templateRef, wiring, routing, variables);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String missionId;
        private String description = "";
        private String version = "1.0";
        private List<AgentEntry> agents = List.of();
        private Map<String, String> metadata = Map.of();

        public Builder missionId(String missionId) {
            this.missionId = missionId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder agents(List<AgentEntry> agents) {
            this.agents = agents;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public MissionManifest build() {
            return new MissionManifest(missionId, description, version, agents, metadata);
        }
    }
}
