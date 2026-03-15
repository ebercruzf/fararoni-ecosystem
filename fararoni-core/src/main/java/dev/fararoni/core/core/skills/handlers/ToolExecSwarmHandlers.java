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
package dev.fararoni.core.core.skills.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.mission.engine.MissionTemplateManager;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.MissionCompletedPayload;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.MissionFailedPayload;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.MissionStartPayload;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.ui.SwarmSpinner;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ToolExecSwarmHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecSwarmHandlers.class.getName());
    private final ObjectMapper mapper;
    private final SovereignEventBus sovereignBus;
    private final MissionTemplateManager missionTemplateManager;
    private final AgentTemplateManager agentTemplateManager;

    private static final Map<String, CompletableFuture<String>> pendingMissions = new ConcurrentHashMap<>();
    private static final long MISSION_TIMEOUT_SECONDS = 300;

    private static final boolean SWARM_ASYNC_MODE = Boolean.parseBoolean(
        System.getenv().getOrDefault("FARARONI_SWARM_ASYNC", "true")
    );

    public ToolExecSwarmHandlers(ObjectMapper mapper, SovereignEventBus sovereignBus,
                                  MissionTemplateManager missionTemplateManager,
                                  AgentTemplateManager agentTemplateManager) {
        this.mapper = mapper;
        this.sovereignBus = sovereignBus;
        this.missionTemplateManager = missionTemplateManager;
        this.agentTemplateManager = agentTemplateManager;
    }

    public void initializeMissionListeners() {
        if (sovereignBus == null) return;

        sovereignBus.subscribe("agency.mission.completed", MissionCompletedPayload.class, envelope -> {
            MissionCompletedPayload payload = envelope.payload();
            String correlationId = envelope.correlationId();
            CompletableFuture<String> future = pendingMissions.remove(correlationId);
            if (future != null) {
                logger.info("[SWARM]Misión completada: " + correlationId);
                future.complete(payload.result());
            } else {
                logger.warning("[SWARM]Respuesta huérfana (sin future): " + correlationId);
            }
        });

        sovereignBus.subscribe("agency.mission.failed", MissionFailedPayload.class, envelope -> {
            MissionFailedPayload payload = envelope.payload();
            String correlationId = envelope.correlationId();
            CompletableFuture<String> future = pendingMissions.remove(correlationId);
            if (future != null) {
                logger.warning("[SWARM]Misión fallida: " + correlationId + " - " + payload.reason());
                future.completeExceptionally(new RuntimeException("Mission failed: " + payload.reason()));
            }
        });

        logger.info("[SWARM]Listeners de misiones inicializados (SwarmHandlers)");
    }

    public ToolExecutionResult handleStartMission(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("mission_goal")) {
            return new ToolExecutionResult(false,
                "Error: start_mission requiere 'mission_goal' (descripción de la misión)",
                Optional.empty(), Optional.empty());
        }

        String missionGoal = args.get("mission_goal").asText();

        if (missionGoal == null || missionGoal.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: mission_goal no puede estar vacío",
                Optional.empty(), Optional.empty());
        }

        int defconLevel = args.has("defcon_level") ? args.get("defcon_level").asInt() : 5;

        int templateCount = missionTemplateManager != null ? missionTemplateManager.templateCount() : 0;
        int agentCount = agentTemplateManager != null ? agentTemplateManager.agentCount() : 0;
        List<String> templates = missionTemplateManager != null
            ? missionTemplateManager.getAvailableTemplateIds()
            : List.of();
        List<String> agents = agentTemplateManager != null
            ? agentTemplateManager.listAgentIds()
            : List.of();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  SWARM ACTIVADO - Sistema de Misiones Iniciado                   ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  DEFCON: " + defconLevel + " | Agentes: " + agentCount + " | Templates: " + templateCount);
        System.out.println("║  Plantillas: " + templates);
        System.out.println("║  Agentes: " + agents);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println("[SWARM] Mision: " + (missionGoal.length() > 60
            ? missionGoal.substring(0, 57) + "..."
            : missionGoal));

        logger.info("[SWARM] Iniciando misión DEFCON-" + defconLevel + ": " + missionGoal);

        String uiHint = missionGoal.length() > 40
            ? missionGoal.substring(0, 37) + "..."
            : missionGoal;
        SwarmSpinner spinner = SwarmSpinner.forMission(uiHint);

        if (sovereignBus != null) {
            return handleStartMissionViaBus(missionGoal, defconLevel, uiHint, spinner);
        } else {
            logger.warning("[SWARM] start_mission invocado pero Bus no está configurado");
            spinner.stop(false, "Sistema de agentes no disponible");
            return new ToolExecutionResult(false,
                "Error: El sistema de agentes no está habilitado.\n\n" +
                "SOLUCION: Usa las herramientas fs_write/fs_read/fs_patch para cambios simples, " +
                "o contacta al administrador para habilitar el sistema de misiones.",
                Optional.empty(), Optional.empty());
        }
    }

    private ToolExecutionResult handleStartMissionViaBus(String missionGoal, int defconLevel,
                                                          String uiHint, SwarmSpinner spinner) {
        if (missionTemplateManager == null) {
            spinner.stop(false, "Sin gestor de plantillas");
            return new ToolExecutionResult(false,
                "Error: MissionTemplateManager no está disponible. " +
                "El sistema de misiones YAML no está configurado.",
                Optional.empty(), Optional.empty());
        }

        String defconStr = String.valueOf(defconLevel);
        String missionTemplateId = missionTemplateManager.getAvailableTemplateIds().stream()
            .filter(id -> id.contains(defconStr) || id.contains("defcon" + defconStr))
            .findFirst()
            .orElse(null);

        if (missionTemplateId == null || !missionTemplateManager.hasTemplate(missionTemplateId)) {
            List<String> plantillasActivas = missionTemplateManager.getAvailableTemplateIds();
            spinner.stop(false, "Plantilla no encontrada");

            String errorMessage = String.format(
                "[WARN] Plantilla no encontrada para DEFCON %d. " +
                "Las plantillas dinamicas cargadas actualmente son: %s. " +
                "Por favor, vuelve a ejecutar start_mission usando uno de estos IDs exactos " +
                "en el campo 'mission_id', o indica al usuario que cree la plantilla deseada.",
                defconLevel, plantillasActivas);

            System.out.println("[SWARM] [WARN] LLM solicito plantilla inexistente. Instruyendo autocorreccion...");
            System.out.println("[SWARM] Plantillas disponibles: " + plantillasActivas);

            return new ToolExecutionResult(false, errorMessage, Optional.empty(), Optional.empty());
        }

        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingMissions.put(correlationId, future);

        try {
            Map<String, Object> missionContext = Map.of(
                "goal", missionGoal,
                "defconLevel", defconLevel,
                "uiHint", uiHint
            );

            MissionStartPayload payload = new MissionStartPayload(missionTemplateId, missionContext);

            SovereignEnvelope<MissionStartPayload> envelope = SovereignEnvelope.createSecure(
                "cli-user",
                "tool-executor",
                UUID.randomUUID().toString(),
                SovereignMissionEngine.TOPIC_MISSION_START,
                payload
            ).withCorrelation(correlationId);

            System.out.println("[SWARM] [START] Publicando mision al bus: " + correlationId);
            System.out.println("[SWARM] Goal: " + missionGoal);
            System.out.println("[SWARM] Template resuelto dinamicamente: " + missionTemplateId);
            System.out.println("[SWARM] Modo: " + (SWARM_ASYNC_MODE ? "ASYNC (Fire-and-Forget)" : "SYNC (Blocking)"));
            logger.info("[SWARM]Publicando misión al bus: " + correlationId);
            sovereignBus.publish(SovereignMissionEngine.TOPIC_MISSION_START, envelope);

            if (SWARM_ASYNC_MODE) {
                spinner.stop(true, "Mision despachada: " + correlationId);
                System.out.println("[SWARM] Mision despachada (async): " + correlationId);
                logger.info("[ETAPA2] Fire-and-Forget: " + correlationId);

                String llmPromptResponse = String.format(
                    "[OK] **Mision iniciada en segundo plano**\n\n" +
                    "Template: `%s` | DEFCON: %d\n" +
                    "ID: `%s`\n\n" +
                    "Los agentes estan trabajando. Puedes seguir usando el chat mientras tanto. " +
                    "Veras el progreso en la terminal y recibiras notificacion cuando termine.",
                    missionTemplateId, defconLevel, correlationId
                );

                return new ToolExecutionResult(true, llmPromptResponse,
                    Optional.empty(), Optional.of(correlationId));
            } else {
                System.out.println("[SWARM] Esperando respuesta de agentes (timeout: " + MISSION_TIMEOUT_SECONDS + "s)...");

                String result = future.get(MISSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                spinner.stop(true, "Mision completada");
                System.out.println("[SWARM] [OK] Mision completada: " + correlationId);
                logger.info("[SWARM]Misión completada via bus: " + correlationId);

                return new ToolExecutionResult(true,
                    "=== MISIÓN COMPLETADA ===\n" +
                    "ID: " + correlationId + "\n" +
                    "DEFCON: " + defconLevel + "\n\n" +
                    "Resultado:\n" + result,
                    Optional.empty(), Optional.empty());
            }
        } catch (java.util.concurrent.TimeoutException e) {
            pendingMissions.remove(correlationId);
            spinner.stop(false, "Timeout");
            logger.warning("[SWARM]Timeout esperando misión: " + correlationId);
            return new ToolExecutionResult(false,
                "Error: Timeout esperando respuesta de misión (" + MISSION_TIMEOUT_SECONDS + "s)",
                Optional.empty(), Optional.empty());
        } catch (Exception e) {
            pendingMissions.remove(correlationId);
            spinner.stop(false, "Error");
            logger.severe("[SWARM]Error en misión via bus: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error en misión: " + e.getMessage(),
                Optional.empty(), Optional.empty());
        }
    }
}
