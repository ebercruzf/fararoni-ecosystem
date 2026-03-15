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

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.model.AgentTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class AgentRegistryBridge {
    private static final Logger LOG = Logger.getLogger(AgentRegistryBridge.class.getName());

    private AgentRegistryBridge() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int syncAll(AgentTemplateManager atm, AgentRegistry registry) {
        if (atm == null || registry == null) return 0;

        List<String> agentIds = atm.listAgentIds();
        int synced = 0;

        for (String agentId : agentIds) {
            AgentTemplate template = atm.getTemplate(agentId);
            if (template == null) continue;

            registerTemplateInRegistry(template, registry);
            synced++;
        }

        if (synced > 0) {
            LOG.info("[REGISTRY-BRIDGE] Sincronizados " + synced + " agentes de ATM → AgentRegistry");
            System.out.println("[FARARONI]  AgentRegistryBridge: " + synced +
                " agentes sincronizados en The Barracks");
        }

        return synced;
    }

    public static void subscribeHotReload(SovereignEventBus bus,
                                          AgentTemplateManager atm,
                                          AgentRegistry registry) {
        if (bus == null || atm == null || registry == null) return;

        bus.subscribe("agency.agent.loaded", String.class, envelope -> {
            try {
                String agentId = envelope.payload();
                if (agentId == null || agentId.isBlank()) return;

                AgentTemplate template = atm.getTemplate(agentId);
                if (template == null) {
                    LOG.warning("[REGISTRY-BRIDGE] Evento agency.agent.loaded para '" +
                        agentId + "' pero template no encontrado en ATM");
                    return;
                }

                registerTemplateInRegistry(template, registry);
                LOG.info("[REGISTRY-BRIDGE] Hot Reload: agente '" + agentId +
                    "' sincronizado en AgentRegistry");
            } catch (Exception e) {
                LOG.warning("[REGISTRY-BRIDGE] Error procesando agency.agent.loaded: " +
                    e.getMessage());
            }
        });

        bus.subscribe("agency.agent.removed", String.class, envelope -> {
            try {
                String agentId = envelope.payload();
                if (agentId == null || agentId.isBlank()) return;

                boolean removed = registry.unregister(agentId);
                if (removed) {
                    LOG.info("[REGISTRY-BRIDGE] Hot Reload: agente '" + agentId +
                        "' des-registrado de AgentRegistry");
                }
            } catch (Exception e) {
                LOG.warning("[REGISTRY-BRIDGE] Error procesando agency.agent.removed: " +
                    e.getMessage());
            }
        });

        LOG.info("[REGISTRY-BRIDGE] Suscrito a agency.agent.loaded/removed para Hot Reload sync");
    }

    public static boolean removeAgent(String agentId,
                                      AgentTemplateManager atm,
                                      AgentRegistry registry) {
        if (agentId == null || agentId.isBlank()) return false;

        boolean removedFromAtm = false;
        boolean removedFromRegistry = false;

        if (atm != null) {
            removedFromAtm = atm.removeAgentById(agentId);
        }

        if (registry != null) {
            removedFromRegistry = registry.unregister(agentId);
        }

        boolean removed = removedFromAtm || removedFromRegistry;

        if (removed) {
            LOG.info("[REGISTRY-BRIDGE] Agente '" + agentId +
                "' eliminado (ATM=" + removedFromAtm + ", Registry=" + removedFromRegistry + ")");
        } else {
            LOG.info("[REGISTRY-BRIDGE] Agente '" + agentId + "' no encontrado en ningun registro");
        }

        return removed;
    }

    private static void registerTemplateInRegistry(AgentTemplate template, AgentRegistry registry) {
        String agentId = template.id();
        String name = template.name() != null ? template.name() : agentId;

        Set<String> skills = template.capabilities() != null
            ? new HashSet<>(template.capabilities())
            : Set.of();

        Set<String> tools = template.allowedTools() != null && !template.allowedTools().isEmpty()
            ? new HashSet<>(template.allowedTools())
            : Set.of();

        if (!tools.isEmpty()) {
            registry.registerHybrid(agentId, name, skills, tools);
        } else {
            registry.registerVirtual(agentId, name, skills, tools);
        }
    }
}
