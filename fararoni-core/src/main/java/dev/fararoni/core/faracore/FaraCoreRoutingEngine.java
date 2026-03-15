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
import dev.fararoni.core.core.clients.AgentClient.AgentResponse;
import dev.fararoni.core.core.clients.AgentClient.ToolCall;
import dev.fararoni.core.core.skills.ModelFamily;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.core.routing.EnterpriseRouter;
import dev.fararoni.core.core.routing.RequestSanitizer;
import dev.fararoni.core.core.routing.RoutingPlan;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.telemetry.OperationTelemetry;
import dev.fararoni.core.core.telemetry.ToolAwareTelemetry;
import dev.fararoni.core.context.ResponseHallucinationGuard;
import dev.fararoni.core.core.context.ExecutionContext;
import dev.fararoni.core.core.index.ProjectKnowledgeBase.ContextProfile;
import dev.fararoni.core.cli.ui.NeuralStatus;
import dev.fararoni.core.model.Message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import java.util.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FaraCoreRoutingEngine {
    private static final Logger LOG = Logger.getLogger(FaraCoreRoutingEngine.class.getName());

    private static final Path TRACE_FILE = Path.of("/tmp/fararoni-trace.log");
    private static void trace(String msg) {
        try {
            Files.writeString(TRACE_FILE,
                "[" + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + "] " + msg + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    private static final ObjectMapper DEPLOY_MAPPER = new ObjectMapper();

    private static final Pattern DEPLOY_INTENT = Pattern.compile(
        "(?i)(despliega|deploy|levanta|arranca|ejecuta|run|start|serve|lanza|sube|up)" +
        ".*\\s*(local|servidor|servicio|server|service|app|aplicacion|endpoint|puerto|port|backend|frontend)"
    );

    private static final Pattern COMPILE_ONLY = Pattern.compile(
        "(?i)^\\s*(" +
        "mvn\\s+(.*\\s+)?(compile|package|install|checkstyle)|" +
        "gradle\\s+(.*\\s+)?(build|assemble|classes)|" +
        "npm\\s+run\\s+(build|check|lint)|" +
        "ng\\s+build|" +
        "cargo\\s+build|" +
        "go\\s+build|" +
        "python3?\\s+-m\\s+compileall|" +
        "javac|tsc|gcc|clang" +
        ")(\\s+.*)?$"
    );

    private static String extractCommandFromToolCall(ToolCall toolCall) {
        if (toolCall == null || !"ShellCommand".equalsIgnoreCase(toolCall.functionName())) return "";
        try {
            JsonNode args = DEPLOY_MAPPER.readTree(toolCall.argumentsJson());
            return args.path("command").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private final FaraCoreLlmDispatcher dispatcher;
    private final FaraCoreContextVault contextVault;

    private RoutingPlan.TargetModel lastRoutingTarget = RoutingPlan.TargetModel.LOCAL;

    public FaraCoreRoutingEngine(FaraCoreLlmDispatcher dispatcher, FaraCoreContextVault contextVault) {
        this.dispatcher = dispatcher;
        this.contextVault = contextVault;
    }

    public String chat(String prompt) {
        return chat(prompt, OperationTelemetry.noOp());
    }

    public String chat(String prompt, OperationTelemetry telemetry) {
        OperationTelemetry monitor = (telemetry != null) ? telemetry : OperationTelemetry.noOp();
        monitor.onPhaseChange("Analizando Intención...");

        try {
            if (prompt.matches(AppDefaults.IDENTITY_TRIGGER_REGEX)) {
                contextVault.addToHistory(Message.user(prompt));
                contextVault.addToHistory(Message.assistant(AppDefaults.IDENTITY_RESPONSE));
                return AppDefaults.IDENTITY_RESPONSE;
            }
            if (prompt.matches(AppDefaults.CREATOR_TRIGGER_REGEX)) {
                String bio = AppDefaults.CREATOR_RESPONSE_HEADER + AppDefaults.CREATOR_BIO;
                contextVault.addToHistory(Message.user(prompt));
                contextVault.addToHistory(Message.assistant(bio));
                return bio;
            }

            monitor.onPhaseChange("Routing...");
            RoutingPlan plan = dispatcher.getRouter().route(prompt);

            RoutingPlan.TargetModel finalTarget = contextVault.getContextHealer().resolveTargetModel(plan.target(), prompt);

            if (finalTarget != plan.target()) {
                LOG.info("[PHOENIX] Router corregido por contexto: " + plan.target() + " → " + finalTarget);
            }

            String effectivePrompt = prompt;

            if (contextVault.getContextHealer().hasPendingContext()) {
                LOG.info("[PHOENIX] Fusionando contexto interrumpido...");
                monitor.onPhaseChange("Fusionando contexto...");

                effectivePrompt = contextVault.getContextHealer().healContext(prompt);
                contextVault.getContextHealer().consumeLimbo();
            }

            boolean expertIsAlive = dispatcher.isRemoteModelAvailable();

            if (finalTarget == RoutingPlan.TargetModel.EXPERT && !expertIsAlive) {
                LOG.warning("[PHOENIX] Experto NO disponible para continuidad. Activando Handover Seguro.");

                finalTarget = RoutingPlan.TargetModel.LOCAL;

                effectivePrompt = """
                [SYSTEM_WARNING: EXPERT MODEL OFFLINE]
                You are a smaller model taking over a conversation from an Expert Model that crashed.
                CRITICAL: You likely LACK the full file context the Expert read previously.

                INSTRUCTION:
                1. If the user asks about specific code/files you cannot see, DO NOT GUESS.
                2. State clearly: "Al no estar disponible el modelo experto, no tengo acceso al análisis completo anterior."
                3. Try to answer based on general principles or ask the user to run 'ListFiles' again.

                USER QUERY:
                """ + effectivePrompt;
            }

            contextVault.addToHistory(Message.user(prompt));

            this.lastRoutingTarget = finalTarget;
            monitor.onModelSwitch(finalTarget);
            monitor.onProcessingState(true);

            final var logTarget = finalTarget;
            final var logPrompt = effectivePrompt;
            LOG.fine(() -> "[CHAT] Final Target: " + logTarget + " | Prompt Length: " + logPrompt.length());

            String response;

            switch (finalTarget) {
                case EXPERT -> {
                    if (dispatcher.isClaudePreferred()) {
                        monitor.onPhaseChange("Claude (" + dispatcher.getClaudeModelName() + ")");
                        response = dispatcher.executeWithClaude(effectivePrompt);
                    } else {
                        monitor.onPhaseChange("Turtle (" + dispatcher.getTurtleModelName() + ")");
                        response = dispatcher.executeExpertWithFallbackSilent(effectivePrompt);
                    }
                }
                case LOCAL -> {
                    monitor.onPhaseChange("Rabbit (" + dispatcher.getRabbitModelName() + ")");
                    try {
                        String result = dispatcher.tryLocalExecution(effectivePrompt, dispatcher.getFastClient());
                        if (result == null) {
                            throw new RuntimeException("Rabbit no disponible");
                        }
                        response = result;
                    } catch (Exception e) {
                        LOG.warning("[ESCALADA] Rabbit falló: " + e.getMessage());

                        if (dispatcher.isRemoteModelAvailable()) {
                            monitor.onPhaseChange("Escalando → Turtle");
                            response = dispatcher.tryRemoteExecutionSilent(effectivePrompt);
                            this.lastRoutingTarget = RoutingPlan.TargetModel.EXPERT;
                        } else {
                            response = "Error crítico: El modelo local falló y el experto no está disponible. (" + e.getMessage() + ")";
                        }
                    }
                }
                default -> {
                    if (dispatcher.isClaudePreferred()) {
                        monitor.onPhaseChange("Claude (" + dispatcher.getClaudeModelName() + ")");
                        response = dispatcher.executeWithClaude(effectivePrompt);
                    } else {
                        monitor.onPhaseChange("Turtle (" + dispatcher.getTurtleModelName() + ")");
                        response = dispatcher.executeExpertWithFallbackSilent(effectivePrompt);
                    }
                }
            }

            if (response != null && dispatcher.containsEmbeddedToolCall(response)) {
                LOG.info("[REACT-LOOP] Interceptando JSON embebido en chat()");
                response = dispatcher.executeEmbeddedToolCall(response, prompt);
            }

            String finalResponse = response != null ? response : "Error generando respuesta.";
            finalResponse = ResponseHallucinationGuard.filter(prompt, finalResponse);
            contextVault.addToHistory(Message.assistant(finalResponse));

            return finalResponse;
        } finally {
            monitor.onProcessingState(false);
            monitor.close();
        }
    }

    public String chatWithSystemPrompt(String systemPrompt, String userMessage, String traceId) {
        RoutingPlan plan = dispatcher.getRouter().route(userMessage);

        LOG.info("[%s] VECTOR DE DECISION: %s para input '%s' (len=%d)".formatted(
            traceId,
            plan.target(),
            userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage,
            userMessage.length()
        ));

        String fullPrompt = systemPrompt + "\n\n" + userMessage;

        LOG.fine("[%s]  VECTOR DE CONTEXTO: systemPrompt=%d chars, userMsg=%d chars, total=%d chars".formatted(
            traceId,
            systemPrompt.length(),
            userMessage.length(),
            fullPrompt.length()
        ));

        this.lastRoutingTarget = plan.target();

        try {
            return switch (plan.target()) {
                case LOCAL -> {
                    LOG.info("[%s] Ejecutando con RABBIT (modelo rapido)".formatted(traceId));
                    String result = dispatcher.tryLocalExecution(fullPrompt, dispatcher.getFastClient());
                    if (result == null) {
                        LOG.warning("[%s] Rabbit fallo, escalando a Turtle".formatted(traceId));
                        this.lastRoutingTarget = RoutingPlan.TargetModel.EXPERT;
                        yield dispatcher.executeExpertWithFallbackSilent(fullPrompt);
                    }
                    yield result;
                }
                case EXPERT -> {
                    if (dispatcher.isClaudePreferred()) {
                        LOG.info("[%s] Ejecutando con CLAUDE (modelo experto)".formatted(traceId));
                        yield dispatcher.executeWithClaude(fullPrompt);
                    } else {
                        LOG.info("[%s] Ejecutando con TURTLE (modelo experto)".formatted(traceId));
                        yield dispatcher.executeExpertWithFallbackSilent(fullPrompt);
                    }
                }
            };
        } catch (Exception e) {
            LOG.severe("[%s]  Error en ejecución: %s".formatted(traceId, e.getMessage()));
            return "[ERROR] No se pudo procesar la solicitud: " + e.getMessage();
        }
    }

    public String chatWithSystemPrompt(String systemPrompt, String userMessage) {
        return chatWithSystemPrompt(systemPrompt, userMessage, "AUTO-" + System.nanoTime());
    }

    @Deprecated
    public String chatLegacyFlow(String prompt) {
        return chat(prompt);
    }

    public String executeWithTelemetry(String modelName, Supplier<String> operation) {
        NeuralStatus status = new NeuralStatus(modelName);
        try {
            status.start();

            return operation.get();
        } catch (Exception e) {
            status.stop();

            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOG.severe("Fallo en ejecución (" + modelName + "): " + cause.getMessage());

            return "[SISTEMA: Error en enlace neural con " + modelName + "]\n" + cause.getMessage();
        } finally {
            status.stop();
        }
    }

    public String chatAgentic(String prompt) {
        try {
            return chatAgentic(prompt, ToolAwareTelemetry.noOp(), ExecutionContext.immortal());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[ERROR] Operacion interrumpida inesperadamente.";
        }
    }

    public String chatAgentic(String prompt, ToolAwareTelemetry telemetry) {
        try {
            return chatAgentic(prompt, telemetry, ExecutionContext.immortal());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[ERROR] Operacion interrumpida inesperadamente.";
        }
    }

    public String chatAgenticWithSystemPrompt(String agentSystemPrompt, String userMessage) {
        try {
            return chatAgenticInternal(userMessage, agentSystemPrompt,
                ToolAwareTelemetry.noOp(), ExecutionContext.immortal());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[ERROR] Operacion interrumpida.";
        }
    }

    public String chatAgentic(String prompt, ToolAwareTelemetry telemetry, ExecutionContext ctx)
            throws InterruptedException {
        return chatAgenticInternal(prompt, null, telemetry, ctx);
    }

    private String chatAgenticInternal(String prompt, String systemPromptOverride,
            ToolAwareTelemetry telemetry, ExecutionContext ctx)
            throws InterruptedException {
        System.err.flush();

        String entryLabel = systemPromptOverride != null ? "AGENT-AGENTIC" : "AGENTIC-ENTRY";
        System.out.println("[" + entryLabel + "] chatAgentic() invocado con prompt: " +
            (prompt != null && prompt.length() > 60 ? prompt.substring(0, 60) + "..." : prompt));
        trace("═══ INICIO chatAgentic() prompt=" + (prompt != null ? prompt.substring(0, Math.min(80, prompt.length())) : "null"));

        ToolAwareTelemetry monitor = (telemetry != null) ? telemetry : ToolAwareTelemetry.noOp();

        String bareResult = dispatcher.executeBareCommand(prompt.trim().toLowerCase());
        if (bareResult != null) {
            trace("FIX-H bareCommand INTERCEPTED → " + bareResult.substring(0, Math.min(100, bareResult.length())));
            LOG.info("[FIX-H] Bare command resuelto sin LLM: " + prompt);
            contextVault.addToHistory(Message.user(prompt));
            contextVault.addToHistory(Message.assistant(bareResult));
            return bareResult;
        }

        if (dispatcher.getAgenticClient() == null || dispatcher.getToolRegistry() == null) {
            boolean clientNull = dispatcher.getAgenticClient() == null;
            boolean registryNull = dispatcher.getToolRegistry() == null;
            LOG.severe("[CRITICAL-AGENTIC] Motor agéntico NO disponible — " +
                "agenticClient=" + (clientNull ? "NULL" : "OK") +
                ", toolRegistry=" + (registryNull ? "NULL" : "OK"));
            System.out.println("[CRITICAL-AGENTIC] ⚠ chatAgentic() BYPASSED → chat() — " +
                "agenticClient=" + (clientNull ? "NULL" : "OK") +
                ", toolRegistry=" + (registryNull ? "NULL" : "OK") +
                " — El push técnico NO funcionará en este estado.");
            return "ERROR: El motor de ejecucion (Agentic) no esta inicializado. " +
                   "agenticClient=" + (clientNull ? "NULL" : "OK") +
                   ", toolRegistry=" + (registryNull ? "NULL" : "OK") +
                   ". Verifica la conexion con el modelo experto.";
        }

        String finalLlmPrompt = contextVault.getContextHealer().hasPendingContext()
            ? contextVault.getContextHealer().healContext(prompt)
            : prompt;

        if (contextVault.getContextHealer().hasPendingContext()) {
            LOG.info("[PHOENIX] Contexto pendiente detectado, aplicando curación...");
            contextVault.getContextHealer().consumeLimbo();
        }

        try {
            contextVault.addToHistory(Message.user(prompt));

            ctx.checkCancelled();

            String solicitud = RequestSanitizer.extractSolicitud(prompt);
            String pathExtraido = RequestSanitizer.extractPath(prompt);

            String q = solicitud;
            ContextProfile profile = contextVault.detectContextProfile(solicitud);

            if (RequestSanitizer.containsPath(prompt)) {
                LOG.info("[SANITIZER] Solicitud: \"" + solicitud + "\"");
                LOG.info("[SANITIZER] Path: \"" + pathExtraido + "\"");
            }

            boolean forceExpert = "true".equalsIgnoreCase(
                System.getenv("FARARONI_FORCE_EXPERT"));

            RoutingPlan.TargetModel selectedTarget;
            String routingSource;

            if (forceExpert) {
                selectedTarget = RoutingPlan.TargetModel.EXPERT;
                routingSource = "BETSMART_FORCE";
                LOG.info("[ROUTING-AGENTIC] BETSMART MODE: Forzando EXPERT");
            } else {
                RoutingPlan agenticPlan = dispatcher.getRouter().route(solicitud, profile);

                boolean isComplexMission = q.contains("start_mission") ||
                                           q.contains("arquitectura completa") ||
                                           q.contains("refactoriza todo") ||
                                           q.contains("analiza todo el proyecto");

                boolean rabbitIsCapable = dispatcher.getCurrentRabbitMode() == FararoniCore.RabbitMode.REMOTE_LARGE;

                boolean claudeIsExpert = dispatcher.isClaudePreferred();

                if (isComplexMission && !rabbitIsCapable) {
                    selectedTarget = RoutingPlan.TargetModel.EXPERT;
                    routingSource = "COMPLEX_MISSION_OVERRIDE";
                } else if (claudeIsExpert && profile != ContextProfile.SKELETAL) {
                    selectedTarget = RoutingPlan.TargetModel.EXPERT;
                    routingSource = "CLAUDE_PREFERRED";
                } else {
                    selectedTarget = agenticPlan.target();
                    routingSource = agenticPlan.decisionSource().toString();
                }

                LOG.info("[ROUTING-AGENTIC] Target: " + selectedTarget +
                         " | Profile: " + profile +
                         " | Source: " + routingSource +
                         " | Override: " + isComplexMission +
                         " | Solicitud: \"" + solicitud + "\"");
            }

            this.lastRoutingTarget = selectedTarget;
            monitor.onModelSwitch(selectedTarget);
            monitor.onProcessingState(true);
            trace("T1-ROUTING target=" + selectedTarget + " profile=" + profile + " source=" + routingSource + " rabbitMode=" + dispatcher.getCurrentRabbitMode());

            monitor.onPhaseChange("TX");

            String systemPrompt = systemPromptOverride != null
                ? systemPromptOverride
                : contextVault.buildAgenticSystemPrompt(prompt);
            boolean isExercismContext = contextVault.detectExercismContext();
            String effectiveModelName = dispatcher.resolveEffectiveModelName(selectedTarget);
            ModelFamily family = ModelFamily.fromModelName(effectiveModelName);
            List<ObjectNode> allTools = dispatcher.getToolRegistry().getAvailableTools(family, isExercismContext);

            boolean isGeneralSummary = profile == ContextProfile.STRATEGIC &&
                q.matches("(?i).*(contexto|resumen|de que trata|que es este proyecto|explica.*proyecto|para.*entender|para.*comprender).*") &&
                !q.matches("(?i).*(busca|encuentra|archivo|clase|metodo|funcion|donde|lee|cat|grep|específico|especifico).*");

            boolean isSimpleGreeting = profile == ContextProfile.SKELETAL;
            if (isSimpleGreeting) {
                LOG.info("Saludo simple detectado. Bypass de herramientas activado.");
            }

            List<ObjectNode> tools;
            String finalUserPrompt = prompt;

            if (isSimpleGreeting) {
                tools = java.util.Collections.emptyList();
            } else if (isGeneralSummary) {
                LOG.info("Activando PROTOCOLO DE ANALISIS DIRECTO (User Injection + Zero Tools)");

                tools = java.util.Collections.emptyList();

                String reconData = contextVault.performAgnosticRecon(contextVault.getWorkingDirectory());
                if (!reconData.isEmpty()) {
                    finalUserPrompt = """
                    ══════════════════════════════════════════════════════════════════
                    [CONTEXTO DE ARCHIVOS DEL PROYECTO - LECTURA OBLIGATORIA]
                    ══════════════════════════════════════════════════════════════════
                    %s
                    ══════════════════════════════════════════════════════════════════

                    INSTRUCCIÓN: Basado EXCLUSIVAMENTE en el contexto de arriba, responde a esto:
                    "%s"
                    """.formatted(reconData, prompt);
                }
            } else {
                tools = allTools;
            }

            monitor.onPhaseChange("CPU");
            LOG.info(() -> "[CHAT-AGENTIC] Enviando prompt (" + tools.size() + " tools active)");
            trace("T2-TOOLS count=" + tools.size() + " isGreeting=" + isSimpleGreeting + " isGeneralSummary=" + isGeneralSummary);

            ctx.checkCancelled();

            var effectiveClient = dispatcher.resolveAgenticClient(selectedTarget);
            trace("T2.5-LLM-CALL client=" + effectiveClient.getClass().getSimpleName() + " promptLen=" + finalUserPrompt.length() + " sysPromptLen=" + systemPrompt.length() + " tools=" + tools.size());
            long llmStart = System.currentTimeMillis();
            AgentResponse response = effectiveClient.generateWithTools(systemPrompt, finalUserPrompt, tools, ctx);
            long llmDuration = System.currentTimeMillis() - llmStart;
            trace(String.format("T2.9-LLM-DONE duration=%dms tok/s=%.1f", llmDuration, response.tokensPerSecond()));

            ctx.checkCancelled();

            monitor.onPhaseChange("RX");
            trace("T3-LLM-RESPONSE isToolCall=" + response.isToolCall()
                + " client=" + effectiveClient.getClass().getSimpleName()
                + (response.isToolCall() ? " tool=" + response.toolCall().functionName() + " args=" + response.toolCall().argumentsJson() : " textLen=" + (response.textContent() != null ? response.textContent().length() : 0)));

            if (response.isToolCall()) {
                ToolCall toolCall = response.toolCall();
                LOG.info(() -> "[CHAT-AGENTIC] Tool Call detectado: " + toolCall.functionName());

                if (!dispatcher.isClaudePreferred()) {
                    dispatcher.getToolExecutor().setCurrentUserRequest(prompt);
                }

                ToolExecutionResult result = dispatcher.getToolExecutor().executeTool(toolCall, monitor);
                String exitCodeLine = "";
                if (result.message() != null && result.message().contains("[exit_code:")) {
                    int idx = result.message().indexOf("[exit_code:");
                    exitCodeLine = result.message().substring(idx, Math.min(idx + 80, result.message().length()));
                }
                trace("T4-TOOL-RESULT success=" + result.success()
                    + " exitCodeLine=" + exitCodeLine
                    + " msgTail=" + (result.message() != null ? result.message().substring(Math.max(0, result.message().length() - 300)).replace("\n", "\\n") : "null"));

                if (dispatcher.isCognitiveToolCall(toolCall.functionName())) {
                    LOG.info("[REACT-LOOP] Herramienta cognitiva detectada. Iniciando recursion de pensamiento...");
                    return dispatcher.executeCognitiveToolContinuation(systemPrompt, prompt, toolCall, result, tools, monitor);
                }

                if (result.success()) {
                    boolean hasExitCode = result.message() != null && result.message().contains("[exit_code:");
                    boolean exitCodeZero = result.message() != null && result.message().contains("[exit_code: 0");

                    boolean needsContinuation = (hasExitCode && !exitCodeZero)
                        || dispatcher.isClaudePreferred();

                    trace("T5-DECISION success=true hasExitCode=" + hasExitCode + " exitCodeZero=" + exitCodeZero
                        + " claudePreferred=" + dispatcher.isClaudePreferred()
                        + " → " + (needsContinuation ? "REACT_CONTINUATION" : "SUCCESS_DIRECT"));

                    if (needsContinuation) {
                        LOG.info("[REACT-LOOP] Continuacion ReAct: " +
                            (dispatcher.isClaudePreferred() ? "Claude analisis" : "build con errores"));
                        String reactResult = dispatcher.executeToolResultContinuation(
                            systemPrompt, prompt, toolCall, result, tools, monitor);
                        trace("T6-REACT-RETURNED len=" + (reactResult != null ? reactResult.length() : 0) + " preview=" + (reactResult != null ? reactResult.substring(0, Math.min(150, reactResult.length())).replace("\n", "\\n") : "null"));
                        contextVault.addToHistory(Message.assistant(reactResult));
                        return reactResult;
                    }

                    if (exitCodeZero) {
                        String lastCommand = extractCommandFromToolCall(toolCall);
                        if (!lastCommand.isEmpty()
                            && !lastCommand.contains("&&") && !lastCommand.contains("||")
                            && DEPLOY_INTENT.matcher(prompt).find()
                            && COMPILE_ONLY.matcher(lastCommand).find()) {
                            trace("T5-DEPLOY-SHORTCUT detected: user asked deploy, LLM only compiled cmd=" + lastCommand);
                            LOG.info("[DEPLOY-SHORTCUT] Atajo detectado: prompt pide deploy, LLM ejecuto: " + lastCommand);

                            ToolExecutionResult enrichedResult = new ToolExecutionResult(
                                true,
                                result.message() + "\n[DEPLOY-SHORTCUT] User requested deployment but only compilation was executed.",
                                result.payload(),
                                result.targetId()
                            );

                            String deployReact = dispatcher.executeToolResultContinuation(
                                systemPrompt, prompt, toolCall, enrichedResult, tools, monitor);
                            trace("T6-DEPLOY-REACT len=" + (deployReact != null ? deployReact.length() : 0));
                            deployReact = ResponseHallucinationGuard.filter(prompt, deployReact);
                            contextVault.addToHistory(Message.assistant(deployReact));
                            return deployReact;
                        }
                    }

                    trace("T5-SUCCESS-DIRECT returning result.message len=" + (result.message() != null ? result.message().length() : 0));
                    String successMsg = ResponseHallucinationGuard.filter(prompt, result.message());
                    contextVault.addToHistory(Message.assistant(successMsg));
                    return successMsg;
                } else {
                    trace("T5-FAILURE success=false → returning error message");
                    String errorMsg = "Error ejecutando " + toolCall.functionName() + ": " + result.message();
                    errorMsg = ResponseHallucinationGuard.filter(prompt, errorMsg);
                    contextVault.addToHistory(Message.assistant(errorMsg));
                    return errorMsg;
                }
            } else {
                String textResponse = response.textContent() != null ? response.textContent() : "";
                trace("T7-TEXT-PATH textLen=" + textResponse.length() + " preview=" + textResponse.substring(0, Math.min(150, textResponse.length())).replace("\n", "\\n"));

                if (dispatcher.containsEmbeddedToolCall(textResponse)) {
                    LOG.info("[REACT-LOOP] Interceptando JSON embebido en respuesta de chatAgentic()");
                    String processed = dispatcher.executeEmbeddedToolCall(textResponse, prompt);
                    processed = ResponseHallucinationGuard.filter(prompt, processed);
                    contextVault.addToHistory(Message.assistant(processed));
                    return processed;
                }

                if (isTechnicalRequest(prompt) && !tools.isEmpty()) {
                    trace("T8-ACT-OR-DIE isTechnicalRequest=true toolsCount=" + tools.size());
                    System.out.println("[ACT-OR-DIE] ══════════════════════════════════════════");
                    System.out.println("[ACT-OR-DIE] Solicitud tecnica detectada: " + prompt);
                    System.out.println("[ACT-OR-DIE] LLM respondio con texto. Iniciando empuje forzado...");
                    System.out.println("[ACT-OR-DIE] effectiveClient: " + effectiveClient.getClass().getSimpleName());
                    LOG.info("[ACT-OR-DIE] LLM explico en vez de actuar. Empujando...");

                    try {
                        java.nio.file.Files.writeString(
                            java.nio.file.Path.of("/tmp/fararoni-act-or-die.log"),
                            "[" + java.time.Instant.now() + "] ACT-OR-DIE fired for: " + prompt + "\n",
                            java.nio.file.StandardOpenOption.CREATE,
                            java.nio.file.StandardOpenOption.APPEND);
                    } catch (Exception logEx) {  }

                    List<com.fasterxml.jackson.databind.node.ObjectNode> filteredTools = new java.util.ArrayList<>();
                    for (var tool : tools) {
                        String toolName = tool.path("function").path("name").asText("");
                        if ("ShellCommand".equalsIgnoreCase(toolName) ||
                            "fs_write".equalsIgnoreCase(toolName) ||
                            "fs_patch".equalsIgnoreCase(toolName) ||
                            "fs_read".equalsIgnoreCase(toolName) ||
                            "gitaction".equalsIgnoreCase(toolName)) {
                            filteredTools.add(tool);
                        }
                    }
                    if (filteredTools.isEmpty()) {
                        filteredTools = new java.util.ArrayList<>(tools);
                    }
                    System.out.println("[ACT-OR-DIE] Tools filtradas: " + filteredTools.size() + " (de " + tools.size() + " originales)");

                    final int MAX_PUSH_RETRIES = 3;
                    var mapper = FaraCoreLlmDispatcher.JSON_MAPPER;
                    String lastPushText = textResponse;

                    for (int pushAttempt = 1; pushAttempt <= MAX_PUSH_RETRIES; pushAttempt++) {
                        System.out.println("[ACT-OR-DIE] Intento " + pushAttempt + "/" + MAX_PUSH_RETRIES);

                        ArrayNode messages = mapper.createArrayNode();
                        messages.addObject().put("role", "system").put("content", systemPrompt);
                        messages.addObject().put("role", "user").put("content", finalUserPrompt);
                        messages.addObject().put("role", "assistant").put("content", lastPushText);

                        String reinforcement = pushAttempt == 1
                            ? "[SISTEMA] Tu explicacion fue recibida pero el usuario necesita que EJECUTES la accion. " +
                              "Usa ShellCommand para ejecutar el comando correspondiente ahora."
                            : "[SISTEMA] RESPUESTA INVALIDA. Se requiere Tool Call. NO acepto texto. " +
                              "Invoca ShellCommand con el comando apropiado. Intento " + pushAttempt + "/" + MAX_PUSH_RETRIES + ".";
                        messages.addObject().put("role", "user").put("content", reinforcement);

                        AgentResponse pushResponse = effectiveClient.generateWithFullHistory(
                            messages, filteredTools, "required");

                        if (pushResponse.isToolCall()) {
                            ToolCall pushCall = pushResponse.toolCall();
                            System.out.println("[ACT-OR-DIE] EXITO en intento " + pushAttempt + " → " + pushCall.functionName());
                            LOG.info("[ACT-OR-DIE] Empuje exitoso en intento " + pushAttempt + ": " + pushCall.functionName());

                            if (!dispatcher.isClaudePreferred()) {
                                dispatcher.getToolExecutor().setCurrentUserRequest(prompt);
                            }
                            ToolExecutionResult pushResult = dispatcher.getToolExecutor().executeTool(pushCall, monitor);

                            if (pushResult.success() && pushResult.message() != null
                                && pushResult.message().contains("[exit_code:")
                                && !pushResult.message().contains("[exit_code: 0")) {
                                LOG.info("[ACT-OR-DIE] Build con errores. Delegando a ReAct continuation...");
                                String reactResult = dispatcher.executeToolResultContinuation(
                                    systemPrompt, prompt, pushCall, pushResult, tools, monitor);
                                contextVault.addToHistory(Message.assistant(reactResult));
                                return reactResult;
                            }

                            contextVault.addToHistory(Message.assistant(pushResult.message()));
                            return pushResult.message();
                        } else {
                            lastPushText = pushResponse.textContent() != null
                                ? pushResponse.textContent() : lastPushText;
                            System.out.println("[ACT-OR-DIE] Intento " + pushAttempt + " FALLIDO. LLM devolvio texto.");
                            LOG.warning("[ACT-OR-DIE] Intento " + pushAttempt + " fallido.");
                        }
                    }
                    System.out.println("[ACT-OR-DIE] TODOS los intentos agotados. Devolviendo texto original.");
                    LOG.warning("[ACT-OR-DIE] 3/3 intentos fallidos. tool_choice=required no funciona en este backend.");
                }

                if (dispatcher.isCognitiveToolTextResponse(textResponse)) {
                    LOG.info("[REACT-LOOP]  Texto cognitivo plano detectado. Forzando bucle...");
                    ToolCall synthCall = new ToolCall("EnterPlanMode", "{}");
                    ToolExecutionResult synthResult = new ToolExecutionResult(true, "Plan Mode Active via Text Fallback", java.util.Optional.empty(), java.util.Optional.empty());
                    return dispatcher.executeCognitiveToolContinuation(systemPrompt, prompt, synthCall, synthResult, tools, monitor);
                }

                trace("T9-FINAL-TEXT-RETURN textLen=" + textResponse.length());
                textResponse = ResponseHallucinationGuard.filter(prompt, textResponse);
                contextVault.addToHistory(Message.assistant(textResponse));
                return textResponse;
            }
        } catch (InterruptedException e) {
            LOG.info("[PHOENIX] Operación interrumpida. Ejecutando rollback de memoria...");

            contextVault.removeLastUserMessage();

            contextVault.getContextHealer().moveToLimbo(prompt, RoutingPlan.TargetModel.EXPERT);

            throw e;
        } catch (Exception e) {
            LOG.severe("[CHAT-AGENTIC] Error: " + e.getMessage());
            return chat(prompt, OperationTelemetry.noOp());
        } finally {
            monitor.onProcessingState(false);
        }
    }

    public RoutingPlan.TargetModel getLastRoutingTarget() {
        return lastRoutingTarget;
    }

    public void setLastRoutingTarget(RoutingPlan.TargetModel target) {
        this.lastRoutingTarget = target;
    }

    private boolean isTechnicalRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String lower = prompt.toLowerCase().trim();

        return lower.matches("(?s).*(compila|compilar|compile|build|construye|construir|" +
            "ejecuta|ejecutar|run|corre|correr|" +
            "crea|crear|create|genera|generar|generate|" +
            "corrige|corregir|fix|arregla|arreglar|repara|reparar|" +
            "escribe|escribir|write|" +
            "modifica|modificar|modify|cambia|cambiar|change|" +
            "elimina|eliminar|delete|borra|borrar|remove|" +
            "lee|leer|read|muestra|mostrar|show|" +
            "instala|instalar|install|" +
            "despliega|desplegar|deploy|" +
            "testea|test|prueba|probar).*");
    }

    private static final java.util.Set<String> INFORMATIONAL_TOOLS = java.util.Set.of(
        "fs_read", "ListFiles", "SearchCode", "GetFileInfo",
        "ReadFile", "ListDirectory", "SearchFiles"
    );

    private static boolean isInformationalTool(String toolName) {
        return toolName != null && INFORMATIONAL_TOOLS.contains(toolName);
    }
}
