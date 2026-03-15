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
package dev.fararoni.core.core.orchestrator.registry;

import dev.fararoni.core.core.orchestrator.domain.AgentProfile;
import dev.fararoni.core.core.orchestrator.domain.AgentProfile.AgentStatus;
import dev.fararoni.core.core.orchestrator.domain.AgentType;
import dev.fararoni.core.core.orchestrator.domain.HardwareCapabilities;
import dev.fararoni.core.core.orchestrator.domain.MissionRequirement;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class AgentRegistry {
    private static final Logger LOG = Logger.getLogger(AgentRegistry.class.getName());

    private final Map<String, AgentProfile> agents = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> agentsBySkill = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> agentsByLocation = new ConcurrentHashMap<>();

    private final Map<String, Set<String>> agentsByTool = new ConcurrentHashMap<>();

    public void registerVirtual(
            String agentId,
            String name,
            Set<String> skills,
            Set<String> tools) {
        AgentProfile profile = new AgentProfile(
            agentId,
            name,
            AgentType.VIRTUAL,
            skills,
            tools,
            null,
            AgentStatus.IDLE,
            0,
            Instant.now()
        );

        registerInternal(profile);
    }

    public void registerHybrid(
            String agentId,
            String name,
            Set<String> skills,
            Set<String> tools) {
        AgentProfile profile = new AgentProfile(
            agentId,
            name,
            AgentType.HYBRID,
            skills,
            tools,
            null,
            AgentStatus.IDLE,
            0,
            Instant.now()
        );

        registerInternal(profile);
    }

    public void registerEmbodied(
            String agentId,
            String name,
            Set<String> skills,
            HardwareCapabilities hardware) {
        AgentProfile profile = new AgentProfile(
            agentId,
            name,
            AgentType.EMBODIED,
            skills,
            Set.of(),
            hardware,
            AgentStatus.IDLE,
            0,
            Instant.now()
        );

        registerInternal(profile);
    }

    private void registerInternal(AgentProfile profile) {
        AgentProfile previous = agents.put(profile.id(), profile);

        if (profile.cognitiveSkills() != null) {
            for (String skill : profile.cognitiveSkills()) {
                agentsBySkill.computeIfAbsent(skill, k -> ConcurrentHashMap.newKeySet())
                    .add(profile.id());
            }
        }

        if (profile.availableTools() != null) {
            for (String tool : profile.availableTools()) {
                agentsByTool.computeIfAbsent(tool, k -> ConcurrentHashMap.newKeySet())
                    .add(profile.id());
            }
        }

        if (profile.type() == AgentType.EMBODIED && profile.hardware() != null) {
            String location = profile.hardware().location();
            if (location != null) {
                agentsByLocation.computeIfAbsent(location, k -> ConcurrentHashMap.newKeySet())
                    .add(profile.id());
            }
        }

        if (previous == null) {
            LOG.info(() -> String.format(
                "[REGISTRY] Nuevo agente: %s [%s] - Tipo: %s, Skills: %s, Tools: %s",
                profile.id(), profile.name(), profile.type(),
                profile.cognitiveSkills(), profile.availableTools()
            ));
        }
    }

    @Deprecated(since = "FASE 43.2.1")
    public void registerHeartbeat(
            String agentId,
            String name,
            Set<String> capabilities,
            AgentStatus status,
            int loadFactor) {
        registerHeartbeat(agentId, name, capabilities, Set.of(), status, loadFactor);
    }

    @Deprecated(since = "FASE 43.2.1")
    public void registerHeartbeat(
            String agentId,
            String name,
            Set<String> capabilities,
            Set<String> hardwareAccess,
            AgentStatus status,
            int loadFactor) {
        AgentProfile profile = new AgentProfile(
            agentId,
            name,
            hardwareAccess.isEmpty() ? AgentType.VIRTUAL : AgentType.HYBRID,
            capabilities,
            hardwareAccess,
            null,
            status,
            loadFactor,
            Instant.now()
        );

        registerInternal(profile);
    }

    @Deprecated(since = "FASE 43.2.1")
    public void registerAgent(
            String agentId,
            String name,
            String description,
            Set<String> capabilities,
            AgentStatus status,
            int loadFactor) {
        registerHeartbeat(agentId, name, capabilities, status, loadFactor);
    }

    @Deprecated(since = "FASE 43.2.1")
    public void registerAgent(
            String agentId,
            String name,
            String description,
            Set<String> capabilities,
            Set<String> hardwareAccess,
            AgentStatus status,
            int loadFactor) {
        registerHeartbeat(agentId, name, capabilities, hardwareAccess, status, loadFactor);
    }

    public Optional<AgentProfile> findBestAgentFor(MissionRequirement requirement) {
        return agents.values().stream()
            .filter(a -> a.canHandle(requirement))
            .sorted((a1, a2) -> Integer.compare(a1.loadFactor(), a2.loadFactor()))
            .findFirst();
    }

    public List<AgentProfile> findAllAgentsFor(MissionRequirement requirement) {
        return agents.values().stream()
            .filter(a -> a.canHandle(requirement))
            .sorted((a1, a2) -> Integer.compare(a1.loadFactor(), a2.loadFactor()))
            .collect(Collectors.toList());
    }

    public List<AgentProfile> findBySkill(String skill) {
        Set<String> ids = agentsBySkill.get(skill);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
            .map(agents::get)
            .filter(a -> a != null && a.status() != AgentStatus.OFFLINE)
            .collect(Collectors.toList());
    }

    public List<AgentProfile> findByLocation(String location) {
        Set<String> ids = agentsByLocation.get(location);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
            .map(agents::get)
            .filter(a -> a != null && a.status() != AgentStatus.OFFLINE)
            .collect(Collectors.toList());
    }

    public List<AgentProfile> findByTool(String tool) {
        Set<String> ids = agentsByTool.get(tool);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
            .map(agents::get)
            .filter(a -> a != null && a.status() != AgentStatus.OFFLINE)
            .collect(Collectors.toList());
    }

    @Deprecated(since = "FASE 43.2.1")
    public Optional<AgentProfile> findBestAgentFor(String requiredCapability) {
        return findBestAgentFor(MissionRequirement.ofSkill(requiredCapability));
    }

    @Deprecated(since = "FASE 43.2.1")
    public Optional<AgentProfile> findBestAgentForWithHardware(
            String requiredCapability,
            String requiredHardware) {
        MissionRequirement req = MissionRequirement.builder()
            .addSkill(requiredCapability)
            .addTool(requiredHardware)
            .build();
        return findBestAgentFor(req);
    }

    @Deprecated(since = "FASE 43.2.1")
    public List<AgentProfile> findAgentsWithHardware(String hardware) {
        return findByTool(hardware);
    }

    @Deprecated(since = "FASE 43.2.1")
    public List<AgentProfile> findSquadFor(String capability) {
        return findBySkill(capability);
    }

    public boolean unregister(String agentId) {
        AgentProfile removed = agents.remove(agentId);
        if (removed == null) {
            return false;
        }

        if (removed.cognitiveSkills() != null) {
            for (String skill : removed.cognitiveSkills()) {
                Set<String> ids = agentsBySkill.get(skill);
                if (ids != null) ids.remove(agentId);
            }
        }

        if (removed.availableTools() != null) {
            for (String tool : removed.availableTools()) {
                Set<String> ids = agentsByTool.get(tool);
                if (ids != null) ids.remove(agentId);
            }
        }

        if (removed.hardware() != null && removed.hardware().location() != null) {
            Set<String> ids = agentsByLocation.get(removed.hardware().location());
            if (ids != null) ids.remove(agentId);
        }

        LOG.info(() -> "[REGISTRY] Agente eliminado: " + agentId);
        return true;
    }

    public Optional<AgentProfile> getById(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public int evictZombies(long timeoutSeconds) {
        Instant threshold = Instant.now().minusSeconds(timeoutSeconds);
        int[] evicted = {0};

        agents.replaceAll((id, profile) -> {
            if (profile.lastHeartbeat().isBefore(threshold)
                && profile.status() != AgentStatus.OFFLINE) {
                evicted[0]++;
                LOG.warning(() -> "[REGISTRY] Zombie detectado: " + id);
                return new AgentProfile(
                    profile.id(),
                    profile.name(),
                    profile.type(),
                    profile.cognitiveSkills(),
                    profile.availableTools(),
                    profile.hardware(),
                    AgentStatus.OFFLINE,
                    profile.loadFactor(),
                    profile.lastHeartbeat()
                );
            }
            return profile;
        });

        return evicted[0];
    }

    public int purgeOffline() {
        List<String> toRemove = agents.values().stream()
            .filter(a -> a.status() == AgentStatus.OFFLINE)
            .map(AgentProfile::id)
            .collect(Collectors.toList());

        toRemove.forEach(this::unregister);

        if (!toRemove.isEmpty()) {
            LOG.info(() -> "[REGISTRY] Purgados " + toRemove.size() + " agentes OFFLINE");
        }
        return toRemove.size();
    }

    public Map<String, AgentProfile> getSnapshot() {
        return Map.copyOf(agents);
    }

    public int size() {
        return agents.size();
    }

    public boolean contains(String agentId) {
        return agents.containsKey(agentId);
    }

    public Map<AgentStatus, Long> getStatsByStatus() {
        return agents.values().stream()
            .collect(Collectors.groupingBy(
                AgentProfile::status,
                Collectors.counting()
            ));
    }

    public Map<AgentType, Long> getStatsByType() {
        return agents.values().stream()
            .collect(Collectors.groupingBy(
                AgentProfile::type,
                Collectors.counting()
            ));
    }

    public Map<String, Integer> getIndexStats() {
        return Map.of(
            "skills", agentsBySkill.size(),
            "locations", agentsByLocation.size(),
            "tools", agentsByTool.size()
        );
    }
}
