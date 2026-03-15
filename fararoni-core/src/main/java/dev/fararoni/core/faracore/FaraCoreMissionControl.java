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
package dev.fararoni.core.faracore;

import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.core.agent.dynamic.DynamicAgentFactory;
import dev.fararoni.core.core.agent.dynamic.DynamicSwarmAgent;
import dev.fararoni.core.core.agent.dynamic.LlmInferenceProvider;
import dev.fararoni.core.core.agent.dynamic.OllamaStreamingProvider;
import dev.fararoni.core.core.agent.loader.YamlAgentLoader;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.core.infra.ConfigSentinel;
import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.llm.providers.OllamaProvider;
import dev.fararoni.core.core.mission.api.MissionRecoveryManager;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.engine.MissionTemplateManager;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine;
import dev.fararoni.core.core.mission.persistence.JdbcMissionStateRepository;
import dev.fararoni.core.core.orchestrator.SovereignOrchestrator;
import dev.fararoni.core.core.orchestrator.registry.AgentRegistry;
import dev.fararoni.core.core.orchestrator.registry.AgentRegistryBridge;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.core.swarm.infra.SwarmTransport;
import dev.fararoni.core.cli.ui.SwarmMissionMonitor;
import dev.fararoni.core.ui.OutputCoordinator;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FaraCoreMissionControl {
    private static final Logger LOG = Logger.getLogger(FaraCoreMissionControl.class.getName());

    private final Path workingDirectory;

    private HiveMind hiveMind;
    private AgentRegistry agentRegistry;
    private SovereignOrchestrator sovereignOrchestrator;

    private JdbcMissionStateRepository missionStateRepo;
    private MissionTemplateManager missionTemplateManager;
    private SovereignMissionEngine missionEngine;
    private MissionRecoveryManager missionRecovery;
    private AgentTemplateManager agentTemplateManager;
    private SwarmMissionMonitor missionMonitor;

    private final Map<String, DynamicSwarmAgent> dynamicAgents = new ConcurrentHashMap<>();
    private YamlAgentLoader yamlAgentLoader;
    private final DynamicAgentFactory dynamicAgentFactory = new DynamicAgentFactory();
    private ConfigSentinel configSentinel;

    public FaraCoreMissionControl(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public void initializeMissionSystem(
            SovereignEventBus bus,
            HyperNativeKernel kernel,
            SwarmTransport messageBus,
            Path workingDirectory,
            IndexStore indexStore,
            FararoniCore coreRef) {
        this.agentRegistry = new AgentRegistry();
        System.out.println("[FARARONI] AgentRegistry iniciado (0 agentes - usar /wizard para crear)");

        SovereignOrchestrator.LlmFallbackProvider llmFallback =
            (systemPrompt, userPrompt) -> coreRef.chatWithSystemPrompt(
                systemPrompt,
                userPrompt,
                "ORCH-" + System.nanoTime()
            );

        this.sovereignOrchestrator = new SovereignOrchestrator(
            bus,
            this.agentRegistry,
            llmFallback
        );
        this.sovereignOrchestrator.start();
        System.out.println("[FARARONI]  SovereignOrchestrator activo (fallback: LLM directo)");

        this.yamlAgentLoader = new YamlAgentLoader();
        this.yamlAgentLoader.ensureDirectoriesExist();
        this.yamlAgentLoader.scanAll();
        deployDynamicAgents(coreRef);

        Path configBase = Path.of(System.getProperty("user.home"), ".fararoni", "config");
        this.configSentinel = new ConfigSentinel(coreRef, configBase);
        this.configSentinel.start();
        System.out.println("[FARARONI] ConfigSentinel activo (Hot Reload en instances/)");

        try {
            this.missionStateRepo = new JdbcMissionStateRepository();
            System.out.println("[FARARONI]  MissionStateRepository iniciado (" +
                missionStateRepo.getPoolStats() + ")");

            this.missionTemplateManager = new MissionTemplateManager();
            System.out.println("[FARARONI]  MissionTemplateManager iniciado (" +
                missionTemplateManager.templateCount() + " templates)");

            String ollamaUrl = System.getenv(AppDefaults.ENV_OLLAMA_URL);
            if (ollamaUrl == null || ollamaUrl.isBlank()) {
                ollamaUrl = AppDefaults.DEFAULT_OLLAMA_URL;
            }
            String turtleModel = System.getenv(AppDefaults.ENV_TURTLE_MODEL);
            if (turtleModel == null || turtleModel.isBlank()) {
                turtleModel = AppDefaults.DEFAULT_TURTLE_MODEL;
            }

            OllamaProvider ollamaForAgents = new OllamaProvider(ollamaUrl);
            LlmInferenceProvider agentLlm = new OllamaStreamingProvider(ollamaForAgents, turtleModel);

            LOG.info("Agentes usando StreamingLlmInferenceProvider");
            LOG.info("Ollama URL: " + ollamaUrl);
            LOG.info("Modelo: " + turtleModel);

            this.agentTemplateManager = new AgentTemplateManager(bus, agentLlm);
            System.out.println("[FARARONI]  AgentTemplateManager iniciado (" +
                agentTemplateManager.agentCount() + " agentes activos, LLM inyectado)");

            AgentRegistryBridge.syncAll(this.agentTemplateManager, this.agentRegistry);
            AgentRegistryBridge.subscribeHotReload(bus, this.agentTemplateManager, this.agentRegistry);

            this.missionEngine = new SovereignMissionEngine(
                bus,
                this.missionStateRepo,
                this.missionTemplateManager,
                this.agentTemplateManager
            );
            this.missionEngine.start();
            System.out.println("[FARARONI]  SovereignMissionEngine activo");

            this.missionRecovery = new MissionRecoveryManager(
                this.missionStateRepo,
                bus
            );
            this.missionRecovery.setEngine(this.missionEngine);
            this.missionRecovery.startPatrol();
            System.out.println("[FARARONI]  MissionRecoveryManager activo (patrulla cada 5 min)");

            this.missionMonitor = new SwarmMissionMonitor(bus);
            this.missionMonitor.startListening();
            System.out.println("[FARARONI]  SwarmMissionMonitor activo (telemetria en tiempo real)");
        } catch (Exception e) {
            LOG.warning("[FARARONI] Sistema de Misiones no disponible: " + e.getMessage());
        }
    }

    public void setHiveMind(HiveMind hiveMind) {
        this.hiveMind = hiveMind;
    }

    public HiveMind.MissionResult executeMission(String userRequest) {
        if (hiveMind == null) {
            throw new IllegalStateException("HiveMind not initialized");
        }
        return hiveMind.executeMission(userRequest);
    }

    public CompletableFuture<HiveMind.MissionResult> executeMissionAsync(String userRequest) {
        if (hiveMind == null) {
            throw new IllegalStateException("HiveMind not initialized");
        }
        return hiveMind.executeMissionAsync(userRequest);
    }

    public void deployDynamicAgents() {
        deployDynamicAgents(null);
    }

    private void deployDynamicAgents(FararoniCore coreRef) {
        LOG.info("Desplegando agentes dinamicos...");

        dynamicAgents.values().forEach(agent -> {
            try {
                agent.stop();
            } catch (Exception e) {
                LOG.warning(() -> "Error deteniendo " + agent.getId() + ": " + e.getMessage());
            }
        });
        dynamicAgents.clear();

        if (yamlAgentLoader != null) {
            yamlAgentLoader.clear();
            yamlAgentLoader.scanAll();
        }

        if (yamlAgentLoader == null) {
            LOG.warning("YamlAgentLoader no inicializado");
            return;
        }

        var allInstances = yamlAgentLoader.getAllInstances();
        if (allInstances.isEmpty()) {
            LOG.info("No hay instancias en instances/ (0 agentes)");
            return;
        }

        int deployed = 0;
        for (var entry : allInstances.entrySet()) {
            String instanceId = entry.getKey();
            AgentInstanceConfig config = entry.getValue();

            var templateOpt = yamlAgentLoader.getTemplate(config.templateRef());
            if (templateOpt.isEmpty()) {
                LOG.warning(() -> String.format(
                    "[FASE44] Template '%s' no encontrado para instancia '%s'",
                    config.templateRef(), instanceId
                ));
                continue;
            }

            try {
                if (coreRef == null) {
                    LOG.warning(() -> "[FASE44] Core reference not available for agent " + instanceId);
                    continue;
                }
                DynamicSwarmAgent agent = dynamicAgentFactory.create(
                    templateOpt.get(),
                    config,
                    coreRef
                );

                agent.start();
                dynamicAgents.put(instanceId, agent);
                deployed++;

                LOG.info(() -> String.format(
                    "[FASE44] [OK] Agente desplegado: %s [%s]",
                    instanceId, templateOpt.get().roleName()
                ));
            } catch (Exception e) {
                LOG.severe(() -> String.format(
                    "[FASE44] [ERROR] Fallo creando agente '%s': %s",
                    instanceId, e.getMessage()
                ));
            }
        }

        System.out.printf("[FARARONI] [OK] %d agentes dinamicos desplegados%n", deployed);
    }

    public DynamicSwarmAgent getDynamicAgent(String agentId) {
        return dynamicAgents.get(agentId);
    }

    public Map<String, DynamicSwarmAgent> getAllDynamicAgents() {
        return Collections.unmodifiableMap(dynamicAgents);
    }

    public void setMissionMonitorCoordinator(OutputCoordinator coordinator) {
        if (missionMonitor != null) {
            missionMonitor.setOutputCoordinator(coordinator);
        }
    }

    public boolean removeAgent(String agentId) {
        return AgentRegistryBridge.removeAgent(agentId, agentTemplateManager, agentRegistry);
    }

    public HiveMind getHiveMind() {
        return hiveMind;
    }

    public AgentRegistry getAgentRegistry() {
        return agentRegistry;
    }

    public SovereignOrchestrator getSovereignOrchestrator() {
        return sovereignOrchestrator;
    }

    public SovereignMissionEngine getMissionEngine() {
        return missionEngine;
    }

    public MissionTemplateManager getMissionTemplateManager() {
        return missionTemplateManager;
    }

    public AgentTemplateManager getAgentTemplateManager() {
        return agentTemplateManager;
    }

    public SwarmMissionMonitor getMissionMonitor() {
        return missionMonitor;
    }

    public ConfigSentinel getConfigSentinel() {
        return configSentinel;
    }

    public MissionRecoveryManager getMissionRecovery() {
        return missionRecovery;
    }

    public void shutdownResources() {
        LOG.info("[MissionControl] Shutting down resources...");

        if (configSentinel != null) {
            try {
                configSentinel.stop();
            } catch (Exception e) {
                LOG.warning("Error stopping ConfigSentinel: " + e.getMessage());
            }
        }

        dynamicAgents.values().forEach(agent -> {
            try {
                agent.stop();
            } catch (Exception e) {
                LOG.warning(() -> "Error stopping dynamic agent " + agent.getId() + ": " + e.getMessage());
            }
        });
        dynamicAgents.clear();

        if (missionEngine != null) {
            try {
                missionEngine.stop();
            } catch (Exception e) {
                LOG.warning("Error stopping MissionEngine: " + e.getMessage());
            }
        }

        if (missionRecovery != null) {
            try {
                missionRecovery.shutdown();
            } catch (Exception e) {
                LOG.warning("Error shutting down MissionRecovery: " + e.getMessage());
            }
        }

        if (sovereignOrchestrator != null) {
            try {
                sovereignOrchestrator.stop();
            } catch (Exception e) {
                LOG.warning("Error stopping SovereignOrchestrator: " + e.getMessage());
            }
        }

        if (missionMonitor != null) {
            try {
                missionMonitor.stopListening();
            } catch (Exception e) {
                LOG.warning("Error stopping MissionMonitor: " + e.getMessage());
            }
        }

        LOG.info("[MissionControl] All resources shut down.");
    }
}
