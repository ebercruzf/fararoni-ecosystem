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
package dev.fararoni.core.core.mission.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.agents.ReactiveSwarmAgent;
import dev.fararoni.core.core.mission.domain.MissionProgressPayload;
import dev.fararoni.core.core.mission.model.MissionState;
import dev.fararoni.core.core.mission.model.MissionState.ExecutedStep;
import dev.fararoni.core.core.mission.model.MissionState.ExecutionStatus;
import dev.fararoni.core.core.mission.model.MissionStep;
import dev.fararoni.core.core.mission.model.MissionTemplate;
import dev.fararoni.core.core.mission.persistence.JdbcMissionStateRepository;
import dev.fararoni.core.core.safety.mission.MissionPostChecker;
import dev.fararoni.core.core.safety.mission.MissionReport;
import dev.fararoni.core.core.mission.events.FileWriteIntentEvent;
import dev.fararoni.core.core.mission.events.FileWriteResultEvent;
import dev.fararoni.core.core.mission.events.FileWriteErrorEvent;
import dev.fararoni.core.core.utils.MultiFileParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SovereignMissionEngine {
    private static final Logger LOG = Logger.getLogger(SovereignMissionEngine.class.getName());

    public static final String TOPIC_MISSION_START = "agency.mission.start";
    public static final String TOPIC_MISSION_RESUME = "agency.mission.resume";
    public static final String TOPIC_MISSION_COMPLETED = "agency.mission.completed";
    public static final String TOPIC_MISSION_FAILED = "agency.mission.failed";
    public static final String TOPIC_MISSION_PROGRESS = "agency.mission.progress";
    public static final String TOPIC_SWARM_RESULT_PREFIX = "swarm.result.";
    public static final String TOPIC_SWARM_TASK_PREFIX = "swarm.task.";
    public static final String TOPIC_SWARM_COMPENSATION_PREFIX = "swarm.compensation.";

    private final SovereignEventBus bus;
    private final JdbcMissionStateRepository stateRepo;
    private final MissionTemplateManager templateManager;
    private final AgentTemplateManager agentTemplateManager;
    private final ObjectMapper objectMapper;

    private final MissionPostChecker postChecker;
    private final Path projectPath;

    private volatile boolean running;

    private final java.util.Set<String> subscribedAgentIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SovereignMissionEngine(
            SovereignEventBus bus,
            JdbcMissionStateRepository stateRepo,
            MissionTemplateManager templateManager,
            AgentTemplateManager agentTemplateManager) {
        this(bus, stateRepo, templateManager, agentTemplateManager, Path.of(System.getProperty("user.dir")));
    }

    public SovereignMissionEngine(
            SovereignEventBus bus,
            JdbcMissionStateRepository stateRepo,
            MissionTemplateManager templateManager,
            AgentTemplateManager agentTemplateManager,
            Path projectPath) {
        this.bus = bus;
        this.stateRepo = stateRepo;
        this.templateManager = templateManager;
        this.agentTemplateManager = agentTemplateManager;
        this.objectMapper = new ObjectMapper();
        this.projectPath = projectPath;
        this.postChecker = new MissionPostChecker();
        this.running = false;

        LOG.info("SovereignMissionEngine created");
        LOG.info("Post-write validation enabled. Project: " + projectPath);
        LOG.info("Validation mode: " + MissionPostChecker.getValidationMode());
    }

    public void start() {
        if (running) {
            LOG.warning("Engine already running");
            return;
        }

        running = true;

        bus.subscribe(TOPIC_MISSION_START, MissionStartPayload.class, this::handleNewMission);

        bus.subscribe(TOPIC_MISSION_RESUME, MissionResumePayload.class, this::handleResumeMission);

        subscribeToAgentResults();

        bus.subscribe("agency.agent.loaded", String.class, this::handleAgentLoaded);

        bus.subscribe("agency.agent.removed", String.class, this::handleAgentRemoved);

        initializeFileSystemListeners();

        LOG.info("SovereignMissionEngine started");
        LOG.info("Loaded " + templateManager.templateCount() + " mission templates");
    }

    private void subscribeToAgentResults() {
        List<String> dynamicRoles = agentTemplateManager.listAgentIds();

        if (dynamicRoles.isEmpty()) {
            LOG.warning("No se detectaron agentes cargados. El DAG no podra avanzar.");
            System.out.println("[MISSION-ENGINE] [WARN] No hay agentes cargados en ~/.fararoni/config/agentes/");
            return;
        }

        for (String role : dynamicRoles) {
            bus.subscribe(TOPIC_SWARM_RESULT_PREFIX + role, Object.class, this::handleAgentResult);
            subscribedAgentIds.add(role);
        }

        LOG.info("Subscribed to " + dynamicRoles.size() + " dynamic agent result topics: " +
                 String.join(", ", dynamicRoles));
        System.out.println("[MISSION-ENGINE] Escuchando resultados de " + dynamicRoles.size() +
                          " agentes: " + dynamicRoles);
    }

    private void handleAgentLoaded(SovereignEnvelope<String> envelope) {
        String agentId = envelope.payload();

        if (!agentTemplateManager.listAgentIds().contains(agentId)) {
            LOG.warning("[HOT-RELOAD] Agente '" + agentId + "' no encontrado en AgentTemplateManager. Ignorado.");
            return;
        }

        if (subscribedAgentIds.contains(agentId)) {
            LOG.info("[HOT-RELOAD] Agente '" + agentId + "' ya suscrito. Skip.");
            return;
        }

        bus.subscribe(TOPIC_SWARM_RESULT_PREFIX + agentId, Object.class, this::handleAgentResult);
        subscribedAgentIds.add(agentId);

        LOG.info("[HOT-RELOAD] Agente '" + agentId + "' suscrito dinámicamente. " +
                 "Total agentes: " + subscribedAgentIds.size());
        System.out.println("[MISSION-ENGINE] [HOT-RELOAD] Nuevo agente detectado: " + agentId +
                           " (total: " + subscribedAgentIds.size() + ")");
        System.out.flush();
    }

    private void handleAgentRemoved(SovereignEnvelope<String> envelope) {
        String agentId = envelope.payload();

        if (subscribedAgentIds.remove(agentId)) {
            LOG.info("[HOT-RELOAD] Agente '" + agentId + "' removido del tracking. " +
                     "Total agentes: " + subscribedAgentIds.size());
            System.out.println("[MISSION-ENGINE] [HOT-RELOAD] Agente eliminado: " + agentId +
                               " (total: " + subscribedAgentIds.size() + ")");
        System.out.flush();
        } else {
            LOG.fine("[HOT-RELOAD] Agente '" + agentId + "' no estaba en tracking.");
        }
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        LOG.info("SovereignMissionEngine stopped");
    }

    private void handleNewMission(SovereignEnvelope<MissionStartPayload> envelope) {
        MissionStartPayload payload = envelope.payload();
        System.out.println("[MISSION-ENGINE] Recibida solicitud de mision: " + payload.missionId());
        System.out.println("[MISSION-ENGINE] CorrelationId: " + envelope.correlationId());
        LOG.info("New mission request: " + payload.missionId());

        try {
            MissionTemplate template = templateManager.getTemplateOrThrow(payload.missionId());
            System.out.println("[MISSION-ENGINE] [OK] Template cargado: " + template.missionId());

            String executionId = UUID.randomUUID().toString();
            String originalCorrelationId = envelope.correlationId();
            String payloadJson = serializePayload(payload.context());

            MissionState state = MissionState.start(
                executionId,
                originalCorrelationId,
                payload.missionId(),
                template.initialStepId(),
                payloadJson
            );

            stateRepo.save(state);
            String dualId = String.format("[%s (ref:%s)]", executionId,
                originalCorrelationId != null ? originalCorrelationId : "N/A");
            System.out.println("[MISSION-ENGINE] Estado persistido: " + dualId);
            System.out.println("[MISSION-ENGINE] [START] Iniciando paso: " + template.initialStepId());
            LOG.info("Mission state persisted: " + dualId);

            advanceMission(state, template, envelope);
        } catch (IllegalArgumentException e) {
            System.out.println("[MISSION-ENGINE] [ERROR] Template no encontrado: " + payload.missionId());
            LOG.warning("Template not found: " + payload.missionId());
            publishMissionFailed(envelope, "Template not found: " + payload.missionId());
        } catch (Exception e) {
            System.out.println("[MISSION-ENGINE] [ERROR] " + e.getMessage());
            LOG.log(Level.SEVERE, "Failed to start mission", e);
            publishMissionFailed(envelope, e.getMessage());
        }
    }

    private void handleResumeMission(SovereignEnvelope<MissionResumePayload> envelope) {
        String executionId = envelope.payload().executionId();
        LOG.info("Resume mission request: " + executionId);

        try {
            MissionState state = stateRepo.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Mission not found: " + executionId));

            if (state.isTerminated()) {
                LOG.warning("Cannot resume terminated mission: " + executionId);
                return;
            }

            MissionTemplate template = templateManager.getTemplateOrThrow(state.missionId());

            if (state.status() == ExecutionStatus.PAUSED) {
                state = state.resume();
                stateRepo.save(state);
            }

            advanceMission(state, template, envelope);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to resume mission: " + executionId, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleAgentResult(SovereignEnvelope<Object> envelope) {
        try {
            Object payload = envelope.payload();
            String originalEnvelopeId;
            String status;
            String message;
            String agentRole;

            if (payload instanceof ReactiveSwarmAgent.AgentResultPayload record) {
                originalEnvelopeId = record.originalEnvelopeId();
                status = record.status();
                message = record.message();
                agentRole = record.agentRole();
            } else if (payload instanceof Map<?, ?> map) {
                originalEnvelopeId = (String) map.get("originalEnvelopeId");
                status = (String) map.get("status");
                message = (String) map.get("message");
                agentRole = (String) map.get("agentRole");
            } else {
                LOG.warning("Unknown payload type: " + payload.getClass().getName());
                System.out.println("[MISSION-ENGINE] [WARN] Payload desconocido: " + payload.getClass().getSimpleName());
                return;
            }

            System.out.println("[MISSION-ENGINE] Resultado de agente recibido:");
            System.out.println("[MISSION-ENGINE]    Agente: " + agentRole);
            System.out.println("[MISSION-ENGINE]    Status: " + status);
            System.out.println("[MISSION-ENGINE]    CorrelationId: " + envelope.correlationId());

            LOG.info("Agent result: role=" + agentRole +
                     " status=" + status + " msg=" + message);

            String correlationId = envelope.correlationId();
            if (correlationId == null) {
                LOG.warning("Agent result without correlationId, ignoring");
                return;
            }

            MissionState state = stateRepo.findById(correlationId).orElse(null);
            if (state == null) {
                LOG.warning("Mission not found for correlationId: " + correlationId);
                return;
            }

            if (state.isTerminated()) {
                LOG.warning("Ignoring result for terminated mission: " + correlationId);
                return;
            }

            MissionTemplate template = templateManager.getTemplate(state.missionId()).orElse(null);
            if (template == null) {
                LOG.severe("Template not found for running mission: " + state.missionId());
                return;
            }

            processAgentResult(state, template, status, message, envelope);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to process agent result", e);
        }
    }

    private void advanceMission(MissionState state, MissionTemplate template,
                                SovereignEnvelope<?> originalEnvelope) {
        if (state.hasExceededMaxIterations(template.maxIterations())) {
            LOG.warning("Max iterations exceeded: " + state.executionId());
            triggerCompensation(state, template, "Max iterations exceeded", originalEnvelope);
            return;
        }

        MissionStep currentStep = template.getStep(state.currentStepId());
        if (currentStep == null) {
            LOG.severe("Step not found: " + state.currentStepId());
            handleMissionEnd(state, MissionStep.END_FAILURE, "Step not found", originalEnvelope);
            return;
        }

        publishTaskToAgent(state, currentStep, originalEnvelope);
    }

    private void processAgentResult(MissionState state, MissionTemplate template,
                                    String status, String resultPayload,
                                    SovereignEnvelope<?> envelope) {
        MissionStep currentStep = template.getStep(state.currentStepId());
        if (currentStep == null) {
            LOG.severe("Current step not found: " + state.currentStepId());
            return;
        }

        boolean isFailure = "failure".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status);
        String capability = currentStep.requiredCapability();

        if (!isFailure && "code_review".equals(capability) && resultPayload != null) {
            String upper = resultPayload.toUpperCase();
            boolean hasRejection = upper.contains("RECHAZAR") || upper.contains("REJECT");
            if (hasRejection) {
                isFailure = true;
                LOG.info("[QA-LOOP] Sentinel RECHAZÓ el código → transición failure → fix_bugs");
                System.out.println("[QA-LOOP] Sentinel RECHAZÓ el código → Enviando a Builder para corrección");
            } else {
                LOG.info("[QA-LOOP] Sentinel APROBÓ el código → transición success → siguiente paso");
                System.out.println("[QA-LOOP] Sentinel APROBÓ el código → Continuando flujo");
            }
        }

        String nextStepId = currentStep.getNextStep(isFailure ? "failure" : status);

        if (isFailure) {
            broadcastProgress(state, capability.toUpperCase(), capability,
                MissionProgressPayload.STATUS_FAILED,
                "Problema detectado. Evaluando contramedidas...", envelope);
        } else {
            broadcastProgress(state, capability.toUpperCase(), capability,
                MissionProgressPayload.STATUS_SUCCESS,
                "Tarea completada con exito.", envelope);
        }

        if ("technical_design".equals(capability) && !isFailure) {
            System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
            System.out.println("[BLUEPRINT] BLUEPRINT COMPLETADO");
            System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
            System.out.println("[BLUEPRINT]    Step: " + currentStep.stepId());
            System.out.println("[BLUEPRINT]    Capability: " + capability);
            System.out.println("[BLUEPRINT]    Resultado: " + resultPayload.length() + " chars");

            boolean hasFileMarkers = MultiFileParser.isMultiFile(resultPayload);
            System.out.println("[BLUEPRINT]    Tiene >>>FILE:: " + (hasFileMarkers ? "[OK] SI" : "[ERROR] NO"));

            boolean isComplete = validateBlueprintCompleteness(resultPayload, state, template);
            if (!isComplete) {
                LOG.warning("Blueprint incompleto, pero continuando con archivos disponibles...");
            }

            if (hasFileMarkers) {
                LOG.info("PASO A COMPLETADO: Blueprint genero codigo con >>>FILE:");

                FileIntentCollector collector = new FileIntentCollector();
                List<FileIntent> intents = collector.collect(resultPayload);

                if (!intents.isEmpty()) {
                    System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
                    System.out.println("[BLUEPRINT] [START] BYPASS BUILDER (Escritura Directa)");
                    System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
                    System.out.println("[BLUEPRINT]    Archivos a escribir: " + intents.size());
                    System.out.println("[BLUEPRINT]    Modo: Event-Driven (FileSystemIntentListener)");

                    int published = 0;
                    for (FileIntent intent : intents) {
                        FileWriteIntentEvent writeEvent = FileWriteIntentEvent.create(
                            "blueprint",
                            state.executionId(),
                            intent.path(),
                            intent.content()
                        );

                        bus.publish(FileWriteIntentEvent.TOPIC,
                            SovereignEnvelope.create("mission-engine", writeEvent));
                        published++;

                        System.out.printf("[BLUEPRINT]    [%d/%d] %s (%d bytes)%n",
                            published, intents.size(), intent.path(), intent.content().length());
                    }

                    LOG.info("BYPASS COMPLETADO: " + published + " archivos publicados");
                    System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
                    System.out.println("[BLUEPRINT] [OK] BYPASS COMPLETADO - " + published + " eventos publicados");
                    System.out.println("[BLUEPRINT]    FileSystemIntentListener procesará la escritura");
                    System.out.println("[BLUEPRINT] ════════════════════════════════════════════════════════");
                }
            }
        }

        if (isFailure && currentStep.hasRetryLimit()) {
            int currentRetries = state.getRetriesFor(currentStep.stepId());

            if (currentRetries >= currentStep.maxRetries()) {
                LOG.warning(() -> String.format(
                    "[ENGINE] [WARN] Max retries (%d) alcanzado en nodo '%s'. Activando fallback...",
                    currentStep.maxRetries(), currentStep.stepId()
                ));
                System.out.println("[MISSION-ENGINE] [WARN] Max retries (" + currentStep.maxRetries() +
                    ") alcanzado en '" + currentStep.stepId() + "'");

                nextStepId = currentStep.getFallbackStep().orElse(null);

                if (nextStepId == null) {
                    LOG.severe(() -> "[ENGINE] [ERROR] Fallback no configurado. Abortando mision.");
                    System.out.println("[MISSION-ENGINE] [ERROR] Sin fallback configurado. Abortando mision.");
                    handleMissionEnd(state, MissionStep.END_FAILURE,
                        "Max retries reached without fallback for step: " + currentStep.stepId(),
                        envelope);
                    return;
                }

                System.out.println("[MISSION-ENGINE] Fallback activado: " + nextStepId);
            }
        }

        LOG.info("Transition: " + state.currentStepId() +
                 " --[" + status + "]--> " + nextStepId);

        if (nextStepId == null || nextStepId.startsWith("end_")) {
            handleMissionEnd(state, nextStepId, resultPayload, envelope);
            return;
        }

        state = state.recordStepAndAdvance(
            currentStep.stepId(),
            currentStep.requiredCapability(),
            resultPayload,
            nextStepId,
            isFailure
        );

        stateRepo.save(state);

        if (!isFailure && isCodeGenerationStep(currentStep)) {
            MissionReport buildReport = executePostWriteValidation(state, envelope);

            if (!buildReport.isSuccess() && !buildReport.isSkipped()) {
                LOG.warning("Post-write validation FAILED. Triggering correction loop.");
                System.out.println("[MISSION-ENGINE] [ERROR] Compilacion fallo post-escritura");
                System.out.println("[MISSION-ENGINE]    Error: " + buildReport.getFirstLines(3));

                triggerAgentCorrection(state, buildReport, envelope);

                return;
            }

            if (buildReport.isSuccess()) {
                LOG.info("Post-write validation PASSED: " + buildReport.techStack());
                System.out.println("[MISSION-ENGINE] [OK] Compilacion exitosa (" + buildReport.durationMs() + "ms)");
            }
        }

        advanceMission(state, template, envelope);
    }

    private void handleMissionEnd(MissionState state, String endMarker,
                                  String result, SovereignEnvelope<?> envelope) {
        ExecutionStatus finalStatus;
        if (MissionStep.END_SUCCESS.equals(endMarker)) {
            finalStatus = ExecutionStatus.COMPLETED;
            LOG.info("Mission COMPLETED: " + state.executionId());
            publishMissionCompleted(state, result, envelope);
        } else if (MissionStep.END_ROLLBACK.equals(endMarker)) {
            finalStatus = ExecutionStatus.ROLLED_BACK;
            LOG.info("Mission ROLLED_BACK: " + state.executionId());
        } else {
            finalStatus = ExecutionStatus.FAILED;
            LOG.warning("Mission FAILED: " + state.executionId());

            if (state.hasStepsToCompensate()) {
                MissionTemplate template = templateManager.getTemplate(state.missionId()).orElse(null);
                if (template != null) {
                    triggerCompensation(state, template, result, envelope);
                    return;
                }
            }
            publishMissionFailed(envelope, result);
        }

        state = state.terminate(finalStatus);
        stateRepo.save(state);
    }

    private void triggerCompensation(MissionState state, MissionTemplate template,
                                     String reason, SovereignEnvelope<?> envelope) {
        List<ExecutedStep> stepsToCompensate = state.getStepsToCompensate();
        LOG.info("Triggering compensation for " + stepsToCompensate.size() + " steps");

        for (ExecutedStep step : stepsToCompensate) {
            LOG.info("Compensating step: " + step.stepId() +
                     " (capability=" + step.capability() + ")");

            publishCompensationToAgent(step, envelope);
        }
    }

    private void broadcastProgress(MissionState state, String agentRole, String capability,
                                   String status, String message,
                                   SovereignEnvelope<?> originalEnvelope) {
        int retries = state.getRetriesFor(state.currentStepId());

        MissionProgressPayload payload = MissionProgressPayload.create(
            state.executionId(),
            agentRole,
            capability,
            status,
            message,
            retries
        );

        SovereignEnvelope<MissionProgressPayload> progressEnvelope = SovereignEnvelope.createSecure(
            originalEnvelope.userId(),
            "mission-engine",
            originalEnvelope.traceId(),
            TOPIC_MISSION_PROGRESS,
            payload
        ).withCorrelation(state.executionId());

        bus.publish(TOPIC_MISSION_PROGRESS, progressEnvelope);

        LOG.fine(() -> String.format("[TELEMETRY] %s | %s | %s | %s",
            state.executionId(), agentRole, status, message));
    }

    private void initializeFileSystemListeners() {
        bus.subscribe(FileWriteResultEvent.TOPIC, FileWriteResultEvent.class, this::onFileWriteResult);
        bus.subscribe(FileWriteErrorEvent.TOPIC, FileWriteErrorEvent.class, this::onFileWriteError);
        LOG.info("Subscribed to file write events: " +
                 FileWriteResultEvent.TOPIC + ", " + FileWriteErrorEvent.TOPIC);
    }

    private void onFileWriteResult(SovereignEnvelope<FileWriteResultEvent> envelope) {
        FileWriteResultEvent event = envelope.payload();

        LOG.info(() -> "[IO-CONFIRM] Archivo persistido: " + event.writtenPath());
        System.out.println("[MISSION-ENGINE] [OK] Archivo persistido: " + event.writtenPath().getFileName());

        if (event.missionId() != null) {
            LOG.fine(() -> "Archivo asociado a mision: " + event.missionId());
        }
    }

    private void onFileWriteError(SovereignEnvelope<FileWriteErrorEvent> envelope) {
        FileWriteErrorEvent event = envelope.payload();

        LOG.severe(() -> String.format("[IO-ERROR] Fallo en escritura: %s | Code=%s | Msg=%s",
            event.targetPath(), event.errorCode(), event.errorMessage()));

        System.out.println("[MISSION-ENGINE] [X] Error I/O: " + event.targetPath());
        System.out.println("[MISSION-ENGINE]    Código: " + event.errorCode());
        System.out.println("[MISSION-ENGINE]    Detalle: " + event.errorMessage());

        if (isCorruptPathError(event)) {
            LOG.severe("[CRITICAL] El modelo generó basura técnica. Abortando misión para evitar corrupción.");
            System.out.println("[MISSION-ENGINE] [!] ABORT: Ruta corrupta detectada - mision cancelada");

            if (event.missionId() != null) {
                cancelMissionDueToCorruption(event.missionId(), "Falla de integridad en rutas: " + event.targetPath());
            }
            return;
        }

        if (event.recoverable() && event.missionId() != null) {
            LOG.info("Error recuperable, considerando reintento...");
        }
    }

    private boolean isCorruptPathError(FileWriteErrorEvent event) {
        String code = event.errorCode();
        String path = event.targetPath();

        if (FileWriteErrorEvent.ERR_PATH_FORBIDDEN.equals(code)) {
            return true;
        }

        if (path != null && (path.contains("<") || path.contains(">") ||
                            path.endsWith("/java") || path.endsWith("/src"))) {
            return true;
        }

        return false;
    }

    private void cancelMissionDueToCorruption(String missionId, String reason) {
        LOG.warning("Cancelando mision " + missionId + ": " + reason);

        stateRepo.findById(missionId).ifPresent(state -> {
            MissionState failedState = state.terminate(MissionState.ExecutionStatus.FAILED);
            stateRepo.save(failedState);
            LOG.info("Mision marcada como FAILED: " + missionId);
        });
    }

    private static final List<String> CODE_GENERATION_CAPABILITIES = List.of(
        "code_generation",
        "java_refactoring",
        "implementation"
    );

    private boolean isCodeGenerationStep(MissionStep step) {
        if (step == null) return false;
        String capability = step.requiredCapability();
        return CODE_GENERATION_CAPABILITIES.contains(capability);
    }

    private MissionReport executePostWriteValidation(MissionState state, SovereignEnvelope<?> envelope) {
        LOG.info("Executing post-write validation for project: " + projectPath);
        System.out.println("[MISSION-ENGINE] Validando compilacion del proyecto...");

        broadcastProgress(state, "COMPILER", "validation",
            MissionProgressPayload.STATUS_DISPATCHING,
            "Ejecutando compilación real del proyecto...", envelope);

        MissionReport report = postChecker.verifyFullProject(projectPath, null);

        String telemetryStatus = report.isSuccess()
            ? MissionProgressPayload.STATUS_SUCCESS
            : MissionProgressPayload.STATUS_FAILED;

        broadcastProgress(state, "COMPILER", "validation",
            telemetryStatus, report.message(), envelope);

        return report;
    }

    private void triggerAgentCorrection(MissionState state, MissionReport buildReport,
                                        SovereignEnvelope<?> envelope) {
        LOG.info("Triggering agent correction for build errors");
        System.out.println("[MISSION-ENGINE] Enviando errores a agente para correccion...");

        String errorContext = String.format("""
            ## ERROR DE COMPILACIÓN DETECTADO

            **Tecnología**: %s
            **Duración**: %dms

            ### Salida del Compilador:
            ```
            %s
            ```

            ### INSTRUCCIONES:
            1. Analiza los errores de compilación arriba
            2. Identifica la causa raíz (import faltante, tipo incorrecto, etc.)
            3. Genera el código corregido usando la herramienta fs_write
            4. IMPORTANTE: NO inventes dependencias - verifica que existan en pom.xml/build.gradle
            """,
            buildReport.techStack(),
            buildReport.durationMs(),
            buildReport.buildOutput()
        );

        String correctionTopic = TOPIC_SWARM_TASK_PREFIX + "code_review";

        TaskPayload correctionPayload = new TaskPayload(
            state.executionId(),
            state.missionId(),
            "build_error_correction",
            errorContext,
            state.payloadJson()
        );

        SovereignEnvelope<TaskPayload> correctionEnvelope = SovereignEnvelope.createSecure(
            envelope.userId(),
            "mission-engine",
            envelope.traceId(),
            correctionTopic,
            correctionPayload
        ).withCorrelation(state.executionId());

        broadcastProgress(state, "SENTINEL", "code_review",
            MissionProgressPayload.STATUS_DISPATCHING,
            "Analizando errores de compilación para corrección...", envelope);

        bus.publish(correctionTopic, correctionEnvelope);
        LOG.info("Published correction task to: " + correctionTopic);
        System.out.println("[MISSION-ENGINE] Tarea de correccion enviada a Sentinel");
    }

    private void publishTaskToAgent(MissionState state, MissionStep step,
                                    SovereignEnvelope<?> originalEnvelope) {
        String topic = TOPIC_SWARM_TASK_PREFIX + step.requiredCapability();
        String capability = step.requiredCapability();

        String progressMsg = String.format("Asignando tarea [%s] al escuadrón...",
            capability.toUpperCase());
        broadcastProgress(state, "SYSTEM", capability,
            MissionProgressPayload.STATUS_DISPATCHING, progressMsg, originalEnvelope);

        String contextForNextAgent;
        List<MissionState.ExecutedStep> executedSteps = state.executedSteps();

        if ("code_generation".equals(capability) && !executedSteps.isEmpty()) {
            MissionState.ExecutedStep lastStep = executedSteps.get(executedSteps.size() - 1);
            String previousResult = lastStep.resultPayload();

            if (previousResult != null && previousResult.contains(">>>FILE:")) {
                contextForNextAgent = previousResult;
                LOG.info("BYPASS COGNITIVO: Builder recibe codigo directo");
                LOG.info("Desde: " + lastStep.stepId() + " -> " + step.stepId());
                LOG.info("Tamano del codigo: " + previousResult.length() + " chars");
                System.out.println("[MISSION-ENGINE] Bypass Cognitivo activo para Builder");
                System.out.println("[MISSION-ENGINE]    Tamaño: " + previousResult.length() + " chars");
            } else {
                contextForNextAgent = buildContextWithHistory(state, executedSteps, capability);
                LOG.info("Builder sin >>>FILE:, usando historial comprimido");
            }
        } else if (!executedSteps.isEmpty()) {
            contextForNextAgent = buildContextWithHistory(state, executedSteps, capability);
            LOG.info("Contexto COMPRIMIDO para " + capability + " (" + executedSteps.size() + " pasos)");
            System.out.println("[MISSION-ENGINE] Contexto comprimido: " + executedSteps.size() + " pasos -> " + capability);
        } else {
            contextForNextAgent = state.payloadJson();
            LOG.info("Primer paso, usando goal original");
        }

        TaskPayload payload = new TaskPayload(
            state.executionId(),
            state.missionId(),
            step.stepId(),
            step.systemPromptOverride(),
            contextForNextAgent
        );

        SovereignEnvelope<TaskPayload> taskEnvelope = SovereignEnvelope.createSecure(
            originalEnvelope.userId(),
            "mission-engine",
            originalEnvelope.traceId(),
            topic,
            payload
        ).withCorrelation(state.executionId());

        System.out.println("[MISSION-ENGINE] Despachando tarea al agente:");
        System.out.println("[MISSION-ENGINE]    Topic: " + topic);
        System.out.println("[MISSION-ENGINE]    Step: " + step.stepId());
        System.out.println("[MISSION-ENGINE]    Capability: " + step.requiredCapability());

        bus.publish(topic, taskEnvelope);
        LOG.info("Published task to: " + topic);
    }

    private void publishCompensationToAgent(ExecutedStep step, SovereignEnvelope<?> envelope) {
        String topic = TOPIC_SWARM_COMPENSATION_PREFIX + step.capability();

        CompensationPayload payload = new CompensationPayload(
            step.stepId(),
            step.capability(),
            step.resultPayload()
        );

        SovereignEnvelope<CompensationPayload> compEnvelope = SovereignEnvelope.createSecure(
            envelope.userId(),
            "mission-engine",
            envelope.traceId(),
            topic,
            payload
        );

        bus.publish(topic, compEnvelope);
        LOG.info("Published compensation to: " + topic);
    }

    private void publishMissionCompleted(MissionState state, String result,
                                         SovereignEnvelope<?> envelope) {
        MissionCompletedPayload payload = new MissionCompletedPayload(
            state.executionId(),
            state.missionId(),
            result
        );

        String replyCorrelation = state.originalCorrelationId() != null
            ? state.originalCorrelationId()
            : state.executionId();

        SovereignEnvelope<MissionCompletedPayload> completedEnvelope = SovereignEnvelope.createSecure(
            envelope.userId(),
            "mission-engine",
            envelope.traceId(),
            TOPIC_MISSION_COMPLETED,
            payload
        ).withCorrelation(replyCorrelation);

        LOG.info(String.format("Mission COMPLETED [%s (ref:%s)]",
            state.executionId(), replyCorrelation));

        bus.publish(TOPIC_MISSION_COMPLETED, completedEnvelope);
    }

    private void publishMissionFailed(SovereignEnvelope<?> envelope, String reason) {
        MissionFailedPayload payload = new MissionFailedPayload(
            envelope.correlationId(),
            reason
        );

        SovereignEnvelope<MissionFailedPayload> failedEnvelope = SovereignEnvelope.createSecure(
            envelope.userId(),
            "mission-engine",
            envelope.traceId(),
            TOPIC_MISSION_FAILED,
            payload
        ).withCorrelation(envelope.correlationId());

        bus.publish(TOPIC_MISSION_FAILED, failedEnvelope);
    }

    private String serializePayload(Object payload) {
        if (payload == null) return "null";
        if (payload instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return payload.toString();
        }
    }

    private String buildContextWithHistory(MissionState state,
                                           List<MissionState.ExecutedStep> executedSteps,
                                           String targetCapability) {
        int maxCharsPerStep = switch (targetCapability) {
            case "code_review" -> 500;
            case "deployment_verification" -> 300;
            case "audit_logging" -> 150;
            default -> 2000;
        };

        LOG.info("Compresion selectiva: " + targetCapability + " -> max " + maxCharsPerStep + " chars/paso");

        StringBuilder ctx = new StringBuilder();
        ctx.append("## CONTEXTO PARA: ").append(targetCapability.toUpperCase()).append("\n\n");

        ctx.append("**ExecutionId:** ").append(state.executionId()).append("\n");
        if (state.originalCorrelationId() != null) {
            ctx.append("**CorrelationId:** ").append(state.originalCorrelationId()).append("\n");
        }
        ctx.append("**MissionId:** ").append(state.missionId()).append("\n\n");

        for (MissionState.ExecutedStep step : executedSteps) {
            ctx.append("### ").append(step.stepId()).append("\n");

            String result = step.resultPayload();
            if (result != null) {
                if (result.contains(">>>FILE:")) {
                    ctx.append(summarizeFileList(result));
                } else if (result.length() > maxCharsPerStep) {
                    ctx.append(result.substring(0, maxCharsPerStep));
                    ctx.append("\n[...información persistida en DB para auditoría completa...]");
                } else {
                    ctx.append(result);
                }
                ctx.append("\n\n");
            }
        }

        ctx.append("---\n## OBJETIVO ORIGINAL\n");
        ctx.append(state.payloadJson());

        return ctx.toString();
    }

    private String summarizeFileList(String codeWithFileMarkers) {
        StringBuilder summary = new StringBuilder();
        summary.append("**Archivos generados:**\n");

        String[] lines = codeWithFileMarkers.split("\n");
        int fileCount = 0;
        for (String line : lines) {
            if (line.startsWith(">>>FILE:")) {
                fileCount++;
                String path = line.substring(8).trim();
                summary.append("- ").append(path).append("\n");
            }
        }
        summary.append("\n*Total: ").append(fileCount).append(" archivos escritos por Builder*\n");

        return summary.toString();
    }

    public void resumeRecoveredMission(MissionState state) {
        LOG.info("Resuming recovered mission: " + state.executionId());

        MissionTemplate template = templateManager.getTemplateOrThrow(state.missionId());
        MissionStep currentStep = template.getStep(state.currentStepId());

        if (currentStep == null) {
            LOG.severe("Cannot resume - step not found: " + state.currentStepId());
            return;
        }

        var recoveryEnvelope = SovereignEnvelope.createSecure(
            "SYSTEM_RECOVERY",
            "mission-reaper",
            "RECOVERY-" + state.executionId(),
            "recovery.resume",
            (Object) null
        ).withCorrelation(state.executionId());

        broadcastProgress(state, "SYSTEM", "RECOVERY",
            MissionProgressPayload.STATUS_DISPATCHING,
            "Restaurando misión tras interrupción del sistema...",
            recoveryEnvelope);

        System.out.println("[MISSION-ENGINE] Recuperando mision: " + state.executionId());
        System.out.println("[MISSION-ENGINE]    Step: " + state.currentStepId());
        System.out.println("[MISSION-ENGINE]    Iteraciones previas: " + state.iterations());

        publishTaskToAgent(state, currentStep, recoveryEnvelope);
    }

    public void forceFailMission(MissionState state, String reason) {
        LOG.warning("Force-failing mission: " + state.executionId() +
            " reason=" + reason);

        var failEnvelope = SovereignEnvelope.createSecure(
            "SYSTEM_RECOVERY",
            "mission-reaper",
            "RECOVERY-" + state.executionId(),
            "recovery.fail",
            (Object) null
        ).withCorrelation(state.executionId());

        System.out.println("[MISSION-ENGINE] [ERROR] Abortando mision irrecuperable: " + state.executionId());
        System.out.println("[MISSION-ENGINE]    Razón: " + reason);

        handleMissionEnd(state, MissionStep.END_FAILURE, reason, failEnvelope);
    }

    public boolean isRunning() {
        return running;
    }

    public int getTemplateCount() {
        return templateManager.templateCount();
    }

    public record MissionStartPayload(String missionId, Object context) {}

    public record MissionResumePayload(String executionId) {}

    public record TaskPayload(
        String executionId,
        String missionId,
        String stepId,
        String systemPromptOverride,
        String contextJson
    ) {}

    public record CompensationPayload(
        String stepId,
        String capability,
        String originalResultPayload
    ) {}

    public record MissionCompletedPayload(
        String executionId,
        String missionId,
        String result
    ) {}

    public record MissionFailedPayload(
        String executionId,
        String reason
    ) {}

    public record FileRequest(
        String path,
        String type,
        String description
    ) {
        @SuppressWarnings("unchecked")
        public static FileRequest fromMap(Map<String, Object> map) {
            return new FileRequest(
                (String) map.getOrDefault("path", ""),
                (String) map.getOrDefault("type", "unknown"),
                (String) map.getOrDefault("description", "")
            );
        }
    }

    @SuppressWarnings("unchecked")
    public List<FileRequest> parseManifest(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()) {
            LOG.warning("Manifiesto vacio recibido");
            return List.of();
        }

        try {
            String cleanJson = manifestJson.trim();
            if (cleanJson.startsWith("```")) {
                int start = cleanJson.indexOf('\n') + 1;
                int end = cleanJson.lastIndexOf("```");
                if (end > start) {
                    cleanJson = cleanJson.substring(start, end).trim();
                }
            }

            Map<String, Object> manifest = objectMapper.readValue(cleanJson, Map.class);

            String projectName = (String) manifest.getOrDefault("projectName", "unknown");
            String language = (String) manifest.getOrDefault("language", "java");
            String basePackage = (String) manifest.getOrDefault("basePackage", "");

            LOG.info("Manifiesto parseado: proyecto=" + projectName +
                     " lenguaje=" + language + " paquete=" + basePackage);
            System.out.println("[STREAMING] Manifiesto: " + projectName + " (" + language + ")");

            Object filesObj = manifest.get("files");
            if (!(filesObj instanceof List<?> filesList)) {
                LOG.warning("Campo 'files' no es una lista");
                return List.of();
            }

            List<FileRequest> requests = new ArrayList<>();
            for (Object item : filesList) {
                if (item instanceof Map<?, ?> fileMap) {
                    FileRequest req = FileRequest.fromMap((Map<String, Object>) fileMap);
                    if (!req.path().isBlank()) {
                        requests.add(req);
                    }
                }
            }

            LOG.info("Archivos en manifiesto: " + requests.size());
            System.out.println("[STREAMING]    Archivos: " + requests.size());

            return requests;
        } catch (Exception e) {
            LOG.warning("Error parseando manifiesto: " + e.getMessage());
            System.out.println("[STREAMING] [WARN] Error en manifiesto: " + e.getMessage());
            return List.of();
        }
    }

    public void executeIncrementalPipeline(MissionState state, List<FileRequest> manifest,
                                           SovereignEnvelope<?> envelope) {
        if (manifest == null || manifest.isEmpty()) {
            LOG.warning("Manifiesto vacio, abortando pipeline incremental");
            return;
        }

        LOG.info("INICIANDO PIPELINE INCREMENTAL (Streaming Mode)");
        LOG.info("Mision: " + state.missionId());
        LOG.info("Archivos: " + manifest.size());

        System.out.println("[STREAMING] ════════════════════════════════════════════════════════");
        System.out.println("[STREAMING] [START] PIPELINE INCREMENTAL (Streaming Mode)");
        System.out.println("[STREAMING] ════════════════════════════════════════════════════════");
        System.out.println("[STREAMING]    Misión: " + state.missionId());
        System.out.println("[STREAMING]    Archivos a generar: " + manifest.size());

        broadcastProgress(state, "BLUEPRINT", "incremental_generation",
            MissionProgressPayload.STATUS_DISPATCHING,
            "Iniciando generación archivo-por-archivo...", envelope);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < manifest.size(); i++) {
            FileRequest fileReq = manifest.get(i);
            int fileIndex = i + 1;

            LOG.info(String.format("[%d/%d] Procesando: %s",
                fileIndex, manifest.size(), fileReq.path()));
            System.out.printf("[STREAMING]    [%d/%d] %s (%s)%n",
                fileIndex, manifest.size(), fileReq.path(), fileReq.type());

            try {
                String singleFilePrompt = String.format(
                    "ARCHIVO A GENERAR: %s\nTIPO: %s\nDESCRIPCIÓN: %s",
                    fileReq.path(), fileReq.type(), fileReq.description()
                );

                TaskPayload singleFileTask = new TaskPayload(
                    state.executionId(),
                    state.missionId(),
                    "incremental_file_" + fileIndex,
                    singleFilePrompt,
                    state.payloadJson()
                );

                String topic = TOPIC_SWARM_TASK_PREFIX + "technical_design";
                SovereignEnvelope<TaskPayload> taskEnvelope = SovereignEnvelope.createSecure(
                    envelope.userId(),
                    "mission-engine",
                    envelope.traceId(),
                    topic,
                    singleFileTask
                ).withCorrelation(state.executionId());

                broadcastProgress(state, "BLUEPRINT", "file_generation",
                    MissionProgressPayload.STATUS_DISPATCHING,
                    String.format("Generando %s (%d/%d)...", fileReq.path(), fileIndex, manifest.size()),
                    envelope);

                bus.publish(topic, taskEnvelope);
                successCount.incrementAndGet();

                LOG.info("Tarea publicada para: " + fileReq.path());
            } catch (Exception e) {
                errorCount.incrementAndGet();
                LOG.warning("Error procesando " + fileReq.path() + ": " + e.getMessage());
                System.out.println("[STREAMING]    [ERROR] " + fileReq.path() + " - " + e.getMessage());
            }
        }

        LOG.info(String.format("PIPELINE COMPLETADO: %d exitos, %d errores",
            successCount.get(), errorCount.get()));

        System.out.println("[STREAMING] ════════════════════════════════════════════════════════");
        System.out.printf("[STREAMING] [OK] COMPLETADO: %d tareas publicadas, %d errores%n",
            successCount.get(), errorCount.get());
        System.out.println("[STREAMING] ════════════════════════════════════════════════════════");

        String finalStatus = errorCount.get() == 0
            ? MissionProgressPayload.STATUS_SUCCESS
            : MissionProgressPayload.STATUS_FAILED;
        broadcastProgress(state, "BLUEPRINT", "incremental_generation",
            finalStatus,
            String.format("Pipeline incremental: %d archivos procesados", successCount.get()),
            envelope);
    }

    public void executeIncrementalPipeline(MissionState state, List<FileRequest> manifest,
                                           SovereignEnvelope<?> envelope, boolean parallelWrites) {
        if (!parallelWrites) {
            executeIncrementalPipeline(state, manifest, envelope);
            return;
        }

        LOG.info("Pipeline con PARALLEL WRITES habilitado");
        System.out.println("[STREAMING] Modo: Escritura Paralela (inferir B mientras escribe A)");

        executeIncrementalPipeline(state, manifest, envelope);
    }

    private boolean validateBlueprintCompleteness(String resultPayload, MissionState state,
                                                   MissionTemplate template) {
        int minRequired = template.minBlueprintFiles();

        if (minRequired <= 0) {
            return true;
        }

        int fileCount = countFilesInResult(resultPayload);

        if (fileCount < minRequired) {
            LOG.warning(String.format(
                "[BLUEPRINT-VALIDATE] Blueprint: %d archivos < %d minimo (configurable en YAML: minBlueprintFiles)",
                fileCount, minRequired));

            System.out.println("[BLUEPRINT-VALIDATE] ════════════════════════════════════════════════════════");
            System.out.println("[BLUEPRINT-VALIDATE] [WARN] Blueprint genero menos archivos de lo esperado");
            System.out.println("[BLUEPRINT-VALIDATE]    Archivos generados: " + fileCount);
            System.out.println("[BLUEPRINT-VALIDATE]    Mínimo requerido: " + minRequired);
            System.out.println("[BLUEPRINT-VALIDATE] ════════════════════════════════════════════════════════");

            return false;
        }

        LOG.info("Blueprint COMPLETO: " + fileCount + " archivos");
        return true;
    }

    private boolean isCrudMission(MissionState state) {
        String payload = state.payloadJson();
        if (payload == null) return false;

        String lower = payload.toLowerCase();
        return lower.contains("crud") ||
               lower.contains("microservicio") ||
               lower.contains("api rest") ||
               lower.contains("spring boot") ||
               (lower.contains("crear") && lower.contains("servicio"));
    }

    private int countFilesInResult(String resultPayload) {
        if (resultPayload == null) return 0;

        int count = 0;
        int index = 0;
        while ((index = resultPayload.indexOf(">>>FILE:", index)) != -1) {
            count++;
            index += 8;
        }
        return count;
    }
}
