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
package dev.fararoni.core.core.orchestrator;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.orchestrator.domain.AgentProfile;
import dev.fararoni.core.core.orchestrator.domain.MissionRequirement;
import dev.fararoni.core.core.orchestrator.registry.AgentRegistry;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SovereignOrchestrator {
    private static final Logger LOG = Logger.getLogger(SovereignOrchestrator.class.getName());

    public static final String MISSION_REQUEST_TOPIC = "agency.mission.request";

    public static final String TASK_ASSIGN_TOPIC = "agency.task.assign";

    public static final String OUTPUT_TOPIC = "agency.output.main";

    public static final String HEARTBEAT_TOPIC = "sys.agent.heartbeat";

    private final SovereignEventBus bus;
    private final AgentRegistry registry;
    private final LlmFallbackProvider llmFallback;

    private volatile boolean running = false;

    @FunctionalInterface
    public interface LlmFallbackProvider {
        String infer(String systemPrompt, String userPrompt);
    }

    public SovereignOrchestrator(SovereignEventBus bus, AgentRegistry registry, LlmFallbackProvider llmFallback) {
        this.bus = bus;
        this.registry = registry;
        this.llmFallback = llmFallback;
    }

    public SovereignOrchestrator(SovereignEventBus bus, AgentRegistry registry) {
        this(bus, registry, null);
    }

    public void start() {
        if (running) {
            LOG.warning("[ORCHESTRATOR] Ya esta corriendo");
            return;
        }

        running = true;

        bus.subscribe(MISSION_REQUEST_TOPIC, String.class, this::handleMissionRequest);

        LOG.info("[ORCHESTRATOR] Mission Control Activo - Escuchando: " + MISSION_REQUEST_TOPIC);
    }

    public void stop() {
        running = false;
        LOG.info("[ORCHESTRATOR] Detenido");
    }

    public boolean isRunning() {
        return running;
    }

    private void handleMissionRequest(SovereignEnvelope<String> envelope) {
        String missionDescription = envelope.payload();
        String sender = envelope.headers().get("X-Sender-Phone");

        LOG.info(() -> String.format(
            "[ORCHESTRATOR] Mision recibida de %s: %s",
            sender, truncate(missionDescription, 50)
        ));

        MissionRequirement requirement = buildMissionRequirement(envelope);

        LOG.info(() -> String.format(
            "[ORCHESTRATOR] Requisitos: Skills=%s, Tools=%s, Location=%s, GPU=%s",
            requirement.requiredSkills(),
            requirement.requiredTools(),
            requirement.targetLocation(),
            requirement.requiresGpu()
        ));

        var bestAgentOpt = registry.findBestAgentFor(requirement);

        if (bestAgentOpt.isPresent()) {
            AgentProfile agent = bestAgentOpt.get();
            assignMission(agent, envelope, requirement);
        } else {
            handleNoAgentAvailable(envelope, requirement);
        }
    }

    private MissionRequirement buildMissionRequirement(SovereignEnvelope<String> envelope) {
        MissionRequirement.Builder builder = MissionRequirement.builder();

        String skill = determineRequiredCapability(envelope);
        builder.addSkill(skill);

        String tool = detectRequiredTool(envelope);
        if (tool != null) {
            builder.addTool(tool);
        }

        String location = envelope.headers().get("X-Required-Location");
        if (location != null && !location.isBlank()) {
            builder.atLocation(location);
        }

        String gpuHeader = envelope.headers().get("X-Requires-GPU");
        if ("true".equalsIgnoreCase(gpuHeader) || requiresGpuByContent(envelope.payload())) {
            builder.requiresGpu();
        }

        return builder.build();
    }

    private boolean requiresGpuByContent(String content) {
        String lower = content.toLowerCase();
        return lower.contains("entrenar modelo") || lower.contains("train model")
            || lower.contains("gpu") || lower.contains("cuda")
            || lower.contains("machine learning") || lower.contains("deep learning");
    }

    private void assignMission(
            AgentProfile agent,
            SovereignEnvelope<String> originalEnvelope,
            MissionRequirement requirement) {
        LOG.info(() -> String.format(
            "[ORCHESTRATOR] Asignando mision a: %s (%s) [Tipo: %s]",
            agent.name(), agent.id(), agent.type()
        ));

        var taskEnvelope = SovereignEnvelope.create(
            "Orchestrator",
            UUID.randomUUID().toString(),
            originalEnvelope.payload()
        )
        .withHeader("X-Original-Sender", originalEnvelope.headers().get("X-Sender-Phone"))
        .withHeader("X-Reply-Channel-Id", originalEnvelope.headers().get("X-Reply-Channel-Id"))
        .withHeader("X-Conversation-Id", originalEnvelope.headers().get("X-Conversation-Id"))
        .withHeader("X-Origin-Protocol", originalEnvelope.headers().get("X-Origin-Protocol"))
        .withHeader("X-Callback-Url", originalEnvelope.headers().get("X-Callback-Url"))
        .withHeader("X-Intent", originalEnvelope.headers().get("X-Intent"))
        .withHeader("X-Required-Skills", String.join(",", requirement.requiredSkills()))
        .withHeader("X-Required-Tools", String.join(",", requirement.requiredTools()))
        .withHeader("X-Target-Agent-Id", agent.id())
        .withHeader("X-Target-Agent-Type", agent.type().name());

        if (requirement.targetLocation() != null) {
            taskEnvelope = taskEnvelope.withHeader("X-Required-Location", requirement.targetLocation());
        }

        if (requirement.requiresGpu()) {
            taskEnvelope = taskEnvelope.withHeader("X-Requires-GPU", "true");
        }

        bus.publish(TASK_ASSIGN_TOPIC, taskEnvelope);

        LOG.fine(() -> "[ORCHESTRATOR] Tarea publicada en " + TASK_ASSIGN_TOPIC);
    }

    private void handleNoAgentAvailable(SovereignEnvelope<String> envelope, MissionRequirement requirement) {
        LOG.info(() -> String.format(
            "[ORCHESTRATOR] No hay agentes para: Skills=%s, Tools=%s -> Usando LLM fallback",
            requirement.requiredSkills(), requirement.requiredTools()
        ));

        String replyChannelId = envelope.headers().get("X-Reply-Channel-Id");
        String originProtocol = envelope.headers().get("X-Origin-Protocol");

        String response;

        if (llmFallback != null) {
            String systemPrompt = """
                Eres un asistente experto. El usuario ha solicitado ayuda con una tarea.
                Responde de forma clara, util y profesional.
                Si la tarea requiere acceso a archivos o ejecucion de codigo, explica
                que actualmente no tienes esas capacidades pero puedes ayudar con consultas.
                """;

            try {
                response = llmFallback.infer(systemPrompt, envelope.payload());
                LOG.info(() -> "[ORCHESTRATOR] LLM fallback respondio exitosamente");
            } catch (Exception e) {
                LOG.warning(() -> "[ORCHESTRATOR] Error en LLM fallback: " + e.getMessage());
                response = "Lo siento, ocurrio un error procesando tu solicitud.";
            }
        } else {
            response = String.format(
                "Lo siento, no tengo agentes con: Skills=%s, Tools=%s. " +
                "Usa /wizard para crear agentes especializados.",
                requirement.requiredSkills(), requirement.requiredTools()
            );
        }

        var responseEnvelope = SovereignEnvelope.create(
            "Orchestrator",
            UUID.randomUUID().toString(),
            response
        )
        .withHeader("X-Reply-Channel-Id", replyChannelId)
        .withHeader("X-Origin-Protocol", originProtocol)
        .withHeader("X-Conversation-Id", envelope.headers().get("X-Conversation-Id"))
        .withHeader("X-Route-Used", llmFallback != null ? "Orchestrator-LLM-Fallback" : "Orchestrator-NoAgent")
        .withHeader("X-Callback-Url", envelope.headers().get("X-Callback-Url"))
        .withHeader("X-Intent", envelope.headers().get("X-Intent"));

        bus.publish(OUTPUT_TOPIC, responseEnvelope);
    }

    private String determineRequiredCapability(SovereignEnvelope<String> envelope) {
        String explicitReq = envelope.headers().get("X-Requirement");
        if (explicitReq != null && !explicitReq.isBlank()) {
            return explicitReq;
        }

        String content = envelope.payload().toLowerCase();

        if (content.contains("codigo") || content.contains("code")
            || content.contains(".java") || content.contains("clase")) {
            return "CODE_ANALYSIS";
        }

        if (content.contains("mercado") || content.contains("market")
            || content.contains("financiero") || content.contains("inversion")) {
            return "MARKET_ANALYSIS";
        }

        if (content.contains("whatsapp") || content.contains("mensaje")) {
            return "WHATSAPP_ROUTING";
        }

        return "GENERAL_TASK";
    }

    private String detectRequiredTool(SovereignEnvelope<String> envelope) {
        String explicitTool = envelope.headers().get("X-Required-Tool");
        if (explicitTool != null && !explicitTool.isBlank()) {
            return explicitTool;
        }

        String content = envelope.payload().toLowerCase();

        if (content.contains("navegar") || content.contains("browser")
            || content.contains("screenshot") || content.contains("pagina web")
            || content.contains("abrir url") || content.contains("scraping")) {
            return AgentProfile.TOOL_BROWSER_PLAYWRIGHT;
        }

        if (content.contains("ejecutar codigo") || content.contains("sandbox")
            || content.contains("docker") || content.contains("container")
            || content.contains("compilar") || content.contains("runtime")) {
            return AgentProfile.TOOL_DOCKER_CLIENT;
        }

        if (content.contains("transcribir") || content.contains("audio")
            || content.contains("whisper") || content.contains("voz")
            || content.contains("grabar") || content.contains("tts")) {
            return AgentProfile.TOOL_WHISPER;
        }

        if (content.contains("commit") || content.contains("push")
            || content.contains("branch") || content.contains("merge")) {
            return AgentProfile.TOOL_GIT;
        }

        return null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    public AgentRegistry getRegistry() {
        return registry;
    }
}
