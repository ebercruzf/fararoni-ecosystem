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
package dev.fararoni.core.core.skills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

import dev.fararoni.core.core.clients.AgentClient.ToolCall;

import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.core.index.ProjectKnowledgeBase;
import dev.fararoni.core.core.index.ProjectKnowledgeBase.ContextProfile;
import dev.fararoni.core.core.domain.ArchitecturePatternDetector;
import dev.fararoni.core.core.domain.BusinessDomainDictionary;
import dev.fararoni.core.core.domain.DomainInferenceEngine;
import dev.fararoni.core.core.domain.DomainInferenceResult;
import dev.fararoni.core.core.clients.AgentClient;
import dev.fararoni.core.core.managers.BiblioCognitiveTriadManager;
import dev.fararoni.core.core.memory.Wisdom;
import dev.fararoni.core.core.topology.ProjectTopology;
import dev.fararoni.core.core.topology.ProjectTopologyScanner;
import dev.fararoni.core.core.utils.MultiFileParser;
import dev.fararoni.core.core.safety.IroncladGuard;
import dev.fararoni.core.core.safety.SafetyException;
import dev.fararoni.core.core.safety.SafetyLayer;
import dev.fararoni.core.service.FilesystemService;
import dev.fararoni.core.service.WriteResult;
import dev.fararoni.core.core.services.WebScraperService;
import dev.fararoni.core.core.services.WebScraperService.WebContent;
import dev.fararoni.core.core.skills.impl.SovereignSearchSkill;
import dev.fararoni.core.core.workspace.GitManager;
import dev.fararoni.core.core.telemetry.TelemetryContextHelper;
import dev.fararoni.core.core.telemetry.ToolAwareTelemetry;
import dev.fararoni.core.ui.SwarmSpinner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.mission.engine.MissionTemplateManager;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.MissionCompletedPayload;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.MissionFailedPayload;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.MissionStartPayload;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ToolExecutor {
    private static final Logger logger = Logger.getLogger(ToolExecutor.class.getName());
    private final BiblioCognitiveTriadManager brain;
    private final ObjectMapper mapper;
    private final FilesystemService filesystemService;
    private final Path workingDirectory;

    private final ProjectKnowledgeBase knowledgeBase;
    private final ContextualPathResolver pathResolver;

    private final SafetyLayer safetyLayer;
    private final IroncladGuard ironcladGuard;

    private final AgentClient agentClient;

    private final WebScraperService webScraper;
    private final SovereignSearchSkill searchSkill;

    private final GitManager gitManager;

    private final SovereignEventBus sovereignBus;
    private final MissionTemplateManager missionTemplateManager;
    private final AgentTemplateManager agentTemplateManager;
    private static final Map<String, CompletableFuture<String>> pendingMissions = new ConcurrentHashMap<>();
    private static final long MISSION_TIMEOUT_SECONDS = 300;

    private static final boolean SWARM_ASYNC_MODE = Boolean.parseBoolean(
        System.getenv().getOrDefault("FARARONI_SWARM_ASYNC", "true")
    );

    private String currentUserRequest;

    private final Map<Path, Integer> lastReadHashes = new ConcurrentHashMap<>();

    private final dev.fararoni.core.core.skills.handlers.ToolExecSearchHandlers searchHandlers;
    private final dev.fararoni.core.core.skills.handlers.ToolExecWebHandlers webHandlers;
    private final dev.fararoni.core.core.skills.handlers.ToolExecQwenHandlers qwenHandlers;
    private final dev.fararoni.core.core.skills.handlers.ToolExecSwarmHandlers swarmHandlers;
    private final dev.fararoni.core.core.skills.handlers.ToolExecGitHandlers gitHandlers;
    private final dev.fararoni.core.core.skills.handlers.ToolExecFilesystemHandlers fsHandlers;
    private final dev.fararoni.core.core.skills.handlers.ToolExecEmailHandlers emailHandlers;
    private final dev.fararoni.core.core.skills.handlers.ToolExecConfigHandlers configHandlers;

    public ToolExecutor() {
        this(null, null, null, null);
    }

    @Deprecated
    public ToolExecutor(Path workingDirectory, FilesystemService filesystemService) {
        this(workingDirectory, filesystemService, null, null);
    }

    @Deprecated
    public ToolExecutor(Path workingDirectory, FilesystemService filesystemService, ProjectKnowledgeBase knowledgeBase) {
        this(workingDirectory, filesystemService, knowledgeBase, null);
    }

    @Deprecated
    public ToolExecutor(Path workingDirectory, FilesystemService filesystemService,
                        ProjectKnowledgeBase knowledgeBase, SafetyLayer safetyLayer) {
        this(workingDirectory, filesystemService, knowledgeBase, safetyLayer, null);
    }

    @Deprecated
    public ToolExecutor(Path workingDirectory, FilesystemService filesystemService,
                        ProjectKnowledgeBase knowledgeBase, SafetyLayer safetyLayer,
                        AgentClient agentClient) {
        this(workingDirectory, filesystemService, knowledgeBase, safetyLayer, agentClient, null, null, null, null);
    }

    public ToolExecutor(Path workingDirectory, FilesystemService filesystemService,
                        ProjectKnowledgeBase knowledgeBase, SafetyLayer safetyLayer,
                        AgentClient agentClient,
                        SovereignEventBus sovereignBus,
                        MissionTemplateManager missionTemplateManager,
                        AgentTemplateManager agentTemplateManager,
                        GitManager gitManager) {
        this.brain = BiblioCognitiveTriadManager.getInstance();
        this.mapper = new ObjectMapper();
        this.workingDirectory = workingDirectory;
        this.filesystemService = filesystemService;
        this.knowledgeBase = knowledgeBase;
        this.pathResolver = new ContextualPathResolver();

        this.safetyLayer = safetyLayer;
        this.ironcladGuard = new IroncladGuard();

        this.agentClient = agentClient;

        this.webScraper = new WebScraperService();
        this.searchSkill = new SovereignSearchSkill();

        this.gitManager = gitManager;

        this.sovereignBus = sovereignBus;
        this.missionTemplateManager = missionTemplateManager;
        this.agentTemplateManager = agentTemplateManager;

        this.searchHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecSearchHandlers(
            this.mapper, this.workingDirectory, this.knowledgeBase);
        this.webHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecWebHandlers(
            this.mapper, this.webScraper, this.searchSkill);
        this.qwenHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecQwenHandlers(
            this.mapper, this.workingDirectory, this.knowledgeBase);
        this.swarmHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecSwarmHandlers(
            this.mapper, this.sovereignBus, this.missionTemplateManager, this.agentTemplateManager);
        if (sovereignBus != null) {
            swarmHandlers.initializeMissionListeners();
        }
        this.gitHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecGitHandlers(
            this.mapper, this.gitManager, this.workingDirectory);
        this.fsHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecFilesystemHandlers(
            this.mapper, this.workingDirectory, this.filesystemService,
            this.safetyLayer, this.ironcladGuard, this.knowledgeBase,
            this.pathResolver, this.agentClient, this.lastReadHashes);
        this.emailHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecEmailHandlers(this.mapper);
        this.configHandlers = new dev.fararoni.core.core.skills.handlers.ToolExecConfigHandlers(this.mapper);
    }

    @Deprecated
    private void _old_initializeMissionListeners() {
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

        logger.info("[SWARM]Listeners de misiones inicializados");
    }

    public void setCurrentUserRequest(String userRequest) {
        this.currentUserRequest = userRequest;
        if (fsHandlers != null) {
            fsHandlers.setCurrentUserRequest(userRequest);
        }
    }

    public ToolExecutionResult executeTool(ToolCall toolCall, ToolAwareTelemetry telemetry) {
        String functionName = toolCall.functionName();

        Map<String, Object> argsMap = parseArgumentsToMap(toolCall.argumentsJson());
        String uiHint = TelemetryContextHelper.extractUiHint(functionName, argsMap);

        if (telemetry != null) {
            telemetry.onToolStart(functionName, uiHint);
        }

        try {
            ToolExecutionResult result = executeTool(toolCall);

            if (telemetry != null) {
                telemetry.onToolFinish(functionName, result.success());
            }

            return result;
        } catch (Exception e) {
            if (telemetry != null) {
                telemetry.onToolFinish(functionName, false);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgumentsToMap(String jsonArgs) {
        if (jsonArgs == null || jsonArgs.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(jsonArgs, Map.class);
        } catch (Exception e) {
            logger.fine("[PULSE] Could not parse arguments for UI hint: " + e.getMessage());
            return Map.of();
        }
    }

    public ToolExecutionResult executeTool(ToolCall toolCall) {
        String functionName = toolCall.functionName();
        String arguments = toolCall.argumentsJson();

        logger.info("[AGENT] Ejecutando herramienta: " + functionName);

        try {
            return switch (functionName.toLowerCase()) {
                case "restore_solution" -> fsHandlers.handleRestoreSolution(arguments);
                case "fs_write" -> fsHandlers.handleFsWrite(arguments);
                case "fs_patch" -> fsHandlers.handleFsPatch(arguments);
                case "fs_mkdir" -> fsHandlers.handleFsMkdir(arguments);
                case "fs_read" -> fsHandlers.handleFsRead(arguments);
                case "web_fetch" -> webHandlers.handleWebFetch(arguments);
                case "web_search" -> webHandlers.handleWebSearch(arguments);
                case "start_mission" -> swarmHandlers.handleStartMission(arguments);
                case "taskcreate", "taskupdate",
                     "taskget", "tasklist", "tasksearch",
                     "taskstart", "taskstop", "taskstopall",
                     "commentcreate",
                     "projectlist", "projectget", "projectcreate",
                     "codereviewrequest", "codereviewapprove", "codereviewreject",
                     "enterplanmode", "exitplanmode",
                     "shellcommand", "executecode"
                    -> qwenHandlers.dispatch(functionName, arguments);
                case "writefile" -> fsHandlers.handleQwenWriteFile(arguments);
                case "readfile" -> fsHandlers.handleQwenReadFile(arguments);
                case "listfiles" -> searchHandlers.handleListFiles(arguments);
                case "filesearch" -> searchHandlers.handleFileSearch(arguments);
                case "globget" -> searchHandlers.handleGlobGet(arguments);
                case "deepscan" -> searchHandlers.handleDeepScan(arguments);
                case "gitaction" -> gitHandlers.handleGitAction(arguments);
                case "email_fetch" -> emailHandlers.handleEmailFetch(arguments);
                case "email_send" -> emailHandlers.handleEmailSend(arguments);
                case "email_read" -> emailHandlers.handleEmailRead(arguments);
                case "config_set" -> configHandlers.handleConfigSet(arguments);
                default -> new ToolExecutionResult(false,
                    "Error: Herramienta '" + functionName + "' no implementada.",
                    Optional.empty(), Optional.empty());
            };
        } catch (Exception e) {
            logger.severe("[AGENT] [ERROR] Error en herramienta " + functionName + ": " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error ejecutando " + functionName + ": " + e.getMessage(),
                Optional.empty(), Optional.empty());
        }
    }

    @Deprecated
    private ToolExecutionResult _old_handleFsWrite(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("path") || !args.has("content")) {
            return new ToolExecutionResult(false,
                "Error: fs_write requiere 'path' y 'content'",
                Optional.empty(), Optional.empty());
        }

        String originalPath = args.get("path").asText();
        String originalContent = args.get("content").asText();

        if (originalPath == null || originalPath.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: path no puede estar vacio",
                Optional.empty(), Optional.empty());
        }

        if (MultiFileParser.isMultiFile(originalContent)) {
            logger.info("[TOOL] Detectado contenido Multi-Archivo (>>>FILE:). Iniciando desempaquetado...");

            Map<String, String> files = MultiFileParser.parse(originalContent);
            StringBuilder report = new StringBuilder("Escritura Multi-Archivo Completada:\n");
            boolean hasErrors = false;
            int successCount = 0;
            int failCount = 0;

            for (Map.Entry<String, String> file : files.entrySet()) {
                String subPath = file.getKey();
                String subContent = file.getValue();

                if (subContent == null || subContent.isBlank()) {
                    report.append("[SKIP] ").append(subPath).append(" (contenido vacio)\n");
                    continue;
                }

                try {
                    ObjectNode subArgs = mapper.createObjectNode();
                    subArgs.put("path", subPath);
                    subArgs.put("content", subContent);

                    ToolExecutionResult subResult = _old_writeIndividualFile(subPath, subContent);

                    if (subResult.success()) {
                        successCount++;
                        report.append("[OK] ").append(subPath).append("\n");
                        logger.info("[TOOL] [OK] Sub-archivo creado: " + subPath);
                    } else {
                        failCount++;
                        hasErrors = true;
                        report.append("[ERROR] ").append(subPath).append(": ").append(subResult.message()).append("\n");
                        logger.warning("[TOOL] [ERROR] Error en sub-archivo: " + subPath);
                    }
                } catch (Exception e) {
                    failCount++;
                    hasErrors = true;
                    report.append("[ERROR] ").append(subPath).append(": ").append(e.getMessage()).append("\n");
                    logger.severe("[TOOL] [ERROR] Excepcion en sub-archivo " + subPath + ": " + e.getMessage());
                }
            }

            report.insert(0, String.format("=== RESUMEN: %d OK, %d FAIL ===\n", successCount, failCount));

            return new ToolExecutionResult(!hasErrors, report.toString(),
                Optional.of(originalContent), Optional.of("multi-file"));
        }

        String finalPath = originalPath;
        String finalContent = originalContent;

        if (originalPath.contains("com/example") || originalPath.contains("com\\example")) {
            System.out.println("[DEFENSA] Detectado intento de uso de 'com.example'. Corrigiendo...");

            if (workingDirectory != null) {
                try {
                    ProjectTopologyScanner scanner = new ProjectTopologyScanner();
                    ProjectTopology topology = scanner.scan(workingDirectory);
                    String realRoot = topology.detectRootPackage();

                    if (realRoot != null && !realRoot.isEmpty() && !realRoot.equals("com.app")) {
                        String realPathSegment = realRoot.replace('.', '/');

                        finalPath = originalPath.replace("com/example", realPathSegment)
                                                .replace("com\\example", realPathSegment);

                        finalContent = originalContent.replace("package com.example", "package " + realRoot);

                        System.out.println("[DEFENSA] Redirigido de com/example a: " + realPathSegment);
                        System.out.println("[DEFENSA] Ruta final: " + finalPath);
                    } else {
                        System.out.println("[DEFENSA] No se pudo detectar paquete real. Usando path original.");
                    }
                } catch (Exception e) {
                    System.out.println("[DEFENSA] Fallo correccion automatica: " + e.getMessage());
                }
            } else {
                System.out.println("[DEFENSA] workingDirectory es null. No se puede corregir.");
            }
        }

        if (finalPath.endsWith(".java") && workingDirectory != null) {
            try {
                ProjectTopologyScanner scanner = new ProjectTopologyScanner();
                ProjectTopology topology = scanner.scan(workingDirectory);
                BusinessDomainDictionary.learnFromTopology(topology);

                String rootPackage = topology.detectRootPackage();

                boolean isExample = finalPath.contains("com/example") || finalPath.contains("com\\example");

                String pathAsPackage = finalPath.replace("/", ".").replace("\\", ".");
                boolean isOrphan = rootPackage != null
                    && !rootPackage.equals("com.app")
                    && pathAsPackage.contains(rootPackage)
                    && !pathAsPackage.contains(rootPackage + ".");

                if (isExample || isOrphan) {
                    String className = DomainInferenceEngine.extractClassName(finalPath);
                    DomainInferenceResult result = DomainInferenceEngine.analyze(className);

                    if (result.hasDomain() && result.confidence() >= 0.7) {
                        System.out.println("[DOMINIO] Clase huerfana detectada: " + className);
                        System.out.println("[DOMINIO] Palabras: " + result.parsedWords());
                        System.out.println("[DOMINIO] Dominio inferido: " + result.domain()
                            + " (match: " + result.matchedWord()
                            + ", confianza: " + String.format("%.0f%%", result.confidence() * 100) + ")");

                        Optional<String> layerOpt = ArchitecturePatternDetector.suggestLayer(workingDirectory);

                        String oldPath = finalPath;
                        StringBuilder newPath = new StringBuilder();

                        String rootAsPath = rootPackage.replace(".", "/");
                        int rootIdx = finalPath.indexOf(rootAsPath);

                        if (rootIdx >= 0) {
                            newPath.append(finalPath.substring(0, rootIdx));
                            newPath.append(rootAsPath);

                            if (layerOpt.isPresent()) {
                                newPath.append("/").append(layerOpt.get());
                            }

                            newPath.append("/").append(result.domain());

                            newPath.append("/").append(className).append(".java");

                            finalPath = newPath.toString();
                        }

                        StringBuilder newPackage = new StringBuilder(rootPackage);
                        if (layerOpt.isPresent()) {
                            newPackage.append(".").append(layerOpt.get());
                        }
                        newPackage.append(".").append(result.domain());

                        finalContent = finalContent.replaceFirst(
                            "package\\s+[\\w.]+;",
                            "package " + newPackage + ";"
                        );

                        System.out.println("[DOMINIO] Ruta original: " + oldPath);
                        System.out.println("[DOMINIO] Ruta final: " + finalPath);
                        System.out.println("[DOMINIO] Package: " + newPackage);
                    } else if (result.parsedWords() != null && !result.parsedWords().isEmpty()) {
                        System.out.println("[DOMINIO] No se pudo inferir dominio para: " + className);
                        System.out.println("[DOMINIO] Palabras analizadas: " + result.parsedWords());
                    }
                } else {
                    System.out.println("[DOMINIO] Clase tiene subpaquete explicito. Respetando decision del usuario.");
                }
            } catch (Exception e) {
                System.out.println("[DOMINIO] Error en inferencia: " + e.getMessage());
            }
        }

        String forceReason = args.has("force_destruction")
            ? args.get("force_destruction").asText()
            : null;

        Path targetPath = Path.of(finalPath);

        if (Files.exists(targetPath)) {
            Path absolutePath = targetPath.toAbsolutePath();
            Integer expectedHash = lastReadHashes.get(absolutePath);

            if (expectedHash != null) {
                String currentDiskContent = Files.readString(targetPath);
                int currentDiskHash = currentDiskContent.hashCode();

                if (currentDiskHash != expectedHash) {
                    logger.warning("[CONCURRENCY] Conflicto detectado en: " + finalPath);
                    return new ToolExecutionResult(false,
                        "CONFLICTO DE CONCURRENCIA DETECTADO:\n" +
                        "El archivo '" + targetPath.getFileName() + "' ha sido modificado externamente " +
                        "(probablemente por el usuario) despues de que lo lei.\n" +
                        "Accion abortada para proteger los cambios del usuario.\n\n" +
                        "SOLUCION: Ejecuta fs_read para obtener la version actualizada del archivo " +
                        "y luego aplica los cambios sobre esa version.",
                        Optional.empty(), Optional.of(finalPath));
                }
            }
        }

        if (safetyLayer != null) {
            try {
                String existingFileContent = "";
                if (Files.exists(targetPath)) {
                    existingFileContent = Files.readString(targetPath);
                }

                ironcladGuard.validate(finalPath, existingFileContent, finalContent, forceReason);

                WriteResult result = safetyLayer.safeWrite(targetPath, finalContent);

                if (result.isSuccess()) {
                    logger.info("[AGENT] [IRONCLAD] Archivo creado: " + result.path().toAbsolutePath());

                    if (knowledgeBase != null && knowledgeBase.isAvailable()) {
                        knowledgeBase.registerFile(result.path());
                    }

                    lastReadHashes.put(result.path().toAbsolutePath(), finalContent.hashCode());

                    return new ToolExecutionResult(true,
                        "Archivo creado: " + result.path().toAbsolutePath(),
                        Optional.of(finalContent), Optional.of(finalPath));
                } else {
                    return new ToolExecutionResult(false,
                        "Error escribiendo archivo: " + result.errorMessage(),
                        Optional.empty(), Optional.of(finalPath));
                }
            } catch (SafetyException e) {
                logger.warning("[AGENT] [IRONCLAD] Kill Switch activado: " + e.getMessage());
                return new ToolExecutionResult(false,
                    e.getMessage(),
                    Optional.empty(), Optional.of(finalPath));
            }
        }

        if (filesystemService != null) {
            WriteResult result = filesystemService.writeFile(finalPath, finalContent);
            if (result.isSuccess()) {
                logger.info("[AGENT] Archivo creado: " + result.path().toAbsolutePath());

                if (knowledgeBase != null && knowledgeBase.isAvailable()) {
                    knowledgeBase.registerFile(result.path());
                }

                lastReadHashes.put(result.path().toAbsolutePath(), finalContent.hashCode());

                return new ToolExecutionResult(true,
                    "Archivo creado: " + result.path().toAbsolutePath(),
                    Optional.of(finalContent), Optional.of(finalPath));
            } else {
                return new ToolExecutionResult(false,
                    "Error escribiendo archivo: " + result.errorMessage(),
                    Optional.empty(), Optional.of(finalPath));
            }
        }

        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        Files.writeString(targetPath, finalContent);
        logger.info("[AGENT] Archivo creado (fallback): " + targetPath.toAbsolutePath());

        return new ToolExecutionResult(true,
            "Archivo creado: " + targetPath.toAbsolutePath(),
            Optional.of(finalContent), Optional.of(finalPath));
    }

    @Deprecated
    private ToolExecutionResult _old_handleFsPatch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("path") || !args.has("search") || !args.has("replace")) {
            return new ToolExecutionResult(false,
                "Error: fs_patch requiere 'path', 'search' y 'replace'",
                Optional.empty(), Optional.empty());
        }

        String pathStr = args.get("path").asText();
        String searchBlock = args.get("search").asText();
        String replaceBlock = args.get("replace").asText();

        if (pathStr == null || pathStr.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: path no puede estar vacio",
                Optional.empty(), Optional.empty());
        }
        if (searchBlock == null || searchBlock.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: search no puede estar vacio",
                Optional.empty(), Optional.empty());
        }

        Path targetPath = workingDirectory != null
            ? workingDirectory.resolve(pathStr)
            : Path.of(pathStr);

        if (!Files.exists(targetPath)) {
            return new ToolExecutionResult(false,
                "Error: El archivo no existe. Usa fs_write para crearlo: " + pathStr,
                Optional.empty(), Optional.empty());
        }

        String originalContent = Files.readString(targetPath);

        Path absolutePath = targetPath.toAbsolutePath();
        Integer expectedHash = lastReadHashes.get(absolutePath);

        if (expectedHash != null) {
            int currentDiskHash = originalContent.hashCode();

            if (currentDiskHash != expectedHash) {
                logger.warning("[CONCURRENCY] Conflicto detectado en patch: " + pathStr);
                return new ToolExecutionResult(false,
                    "CONFLICTO DE CONCURRENCIA DETECTADO:\n" +
                    "El archivo '" + targetPath.getFileName() + "' ha sido modificado externamente " +
                    "(probablemente por el usuario) despues de que lo lei.\n" +
                    "Parche abortado para proteger los cambios del usuario.\n\n" +
                    "SOLUCION: Ejecuta fs_read para obtener la version actualizada del archivo " +
                    "y luego aplica el parche sobre esa version.",
                    Optional.empty(), Optional.of(pathStr));
            }
        }

        if (originalContent.contains(searchBlock)) {
            String newContent = originalContent.replace(searchBlock, replaceBlock);

            if (safetyLayer != null) {
                WriteResult result = safetyLayer.safeWrite(pathStr, newContent);
                if (result.isSuccess()) {
                    logger.info("[PATCH] Parche aplicado exitosamente (match exacto): " + pathStr);

                    if (knowledgeBase != null && knowledgeBase.isAvailable()) {
                        knowledgeBase.registerFile(targetPath);
                    }

                    lastReadHashes.put(absolutePath, newContent.hashCode());

                    return new ToolExecutionResult(true,
                        "Parche aplicado exitosamente (Match Exacto). Archivo: " + pathStr,
                        Optional.of(newContent), Optional.of(pathStr));
                } else {
                    String errorMsg = result.errorMessage();
                    logger.warning("[PATCH] Kill Switch bloqueo parche: " + errorMsg);
                    return new ToolExecutionResult(false,
                        "Kill Switch bloqueo el parche: " + errorMsg,
                        Optional.empty(), Optional.empty());
                }
            } else {
                Files.writeString(targetPath, newContent);
                logger.info("[PATCH] Parche aplicado (sin SafetyLayer): " + pathStr);

                if (knowledgeBase != null && knowledgeBase.isAvailable()) {
                    knowledgeBase.registerFile(targetPath);
                }

                lastReadHashes.put(absolutePath, newContent.hashCode());

                return new ToolExecutionResult(true,
                    "Parche aplicado exitosamente. Archivo: " + pathStr,
                    Optional.of(newContent), Optional.of(pathStr));
            }
        }

        String normalizedOriginal = normalizeLineEndings(originalContent);
        String normalizedSearch = normalizeLineEndings(searchBlock);

        if (normalizedOriginal.contains(normalizedSearch)) {
            logger.warning("[PATCH] Match normalizado encontrado pero no exacto");
            return new ToolExecutionResult(false,
                "Error de Precision: El bloque 'search' existe pero con diferencias de espacios/indentacion. " +
                "Usa fs_read para ver el contenido exacto y copia el bloque tal cual aparece.",
                Optional.empty(), Optional.empty());
        }

        logger.warning("[PATCH] Bloque search no encontrado en: " + pathStr);
        return new ToolExecutionResult(false,
            "FALLO DE PARCHE: El bloque 'search' no existe en el archivo. " +
            "Usa fs_read para ver el contenido actual y asegurate de copiar un bloque de codigo unico.",
            Optional.empty(), Optional.empty());
    }

    private String normalizeLineEndings(String content) {
        return content
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\r", "\n")
            .trim();
    }

    @Deprecated
    private ToolExecutionResult _old_handleFsMkdir(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("path")) {
            return new ToolExecutionResult(false,
                "Error: fs_mkdir requiere 'path'",
                Optional.empty(), Optional.empty());
        }

        String path = args.get("path").asText();

        if (filesystemService != null) {
            WriteResult result = filesystemService.createDirectory(path);
            if (result.isSuccess()) {
                logger.info("[AGENT] [OK] Directorio creado: " + path);
                return new ToolExecutionResult(true,
                    "[OK] Directorio creado: " + path,
                    Optional.empty(), Optional.of(path));
            } else {
                return new ToolExecutionResult(false,
                    "Error creando directorio: " + result.errorMessage(),
                    Optional.empty(), Optional.of(path));
            }
        }

        Path dirPath = Path.of(path);
        Files.createDirectories(dirPath);
        logger.info("[AGENT] [OK] Directorio creado (fallback): " + dirPath.toAbsolutePath());

        return new ToolExecutionResult(true,
            "[OK] Directorio creado: " + dirPath.toAbsolutePath(),
            Optional.empty(), Optional.of(path));
    }

    @Deprecated
    private ToolExecutionResult _old_handleFsRead(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("path")) {
            return new ToolExecutionResult(false,
                "Error: fs_read requiere 'path'",
                Optional.empty(), Optional.empty());
        }

        String requestedPath = args.get("path").asText();
        Path filePath = Path.of(requestedPath);

        if (!Files.exists(filePath)) {
            if (knowledgeBase instanceof IndexStore indexStore
                    && indexStore.isAvailable() && workingDirectory != null) {
                System.out.println("[PATH-RESOLVER] Archivo no encontrado: " + requestedPath);
                System.out.println("[PATH-RESOLVER] Iniciando resolución contextual...");

                try {
                    List<Path> allJavaFiles = indexStore.getAllJavaFiles(workingDirectory);

                    if (!allJavaFiles.isEmpty()) {
                        Path resolvedPath = pathResolver.resolve(requestedPath, allJavaFiles);

                        filePath = resolvedPath;
                        System.out.println("[PATH-RESOLVER] [OK] Ruta resuelta: " + filePath);
                    }
                } catch (AmbiguousPathException e) {
                    logger.warning("[PATH-RESOLVER] Ambigüedad detectada: " + e.getMessage());
                    return new ToolExecutionResult(false,
                        e.toUserFriendlyMessage(),
                        Optional.empty(), Optional.of(requestedPath));
                } catch (RuntimeException e) {
                    logger.warning("[PATH-RESOLVER] " + e.getMessage());
                    return new ToolExecutionResult(false,
                        "Error: " + e.getMessage(),
                        Optional.empty(), Optional.of(requestedPath));
                }
            } else {
                return new ToolExecutionResult(false,
                    "Error: Archivo no existe: " + requestedPath,
                    Optional.empty(), Optional.of(requestedPath));
            }
        }

        if (!Files.exists(filePath)) {
            return new ToolExecutionResult(false,
                "Error: Archivo no existe: " + requestedPath,
                Optional.empty(), Optional.of(requestedPath));
        }

        String content = Files.readString(filePath);
        String finalPathStr = filePath.toString();

        lastReadHashes.put(filePath.toAbsolutePath(), content.hashCode());

        logger.info("[AGENT] Archivo leido: " + finalPathStr + " (" + content.length() + " chars)");

        if (agentClient != null && currentUserRequest != null && !currentUserRequest.isBlank()) {
            logger.info("[ZERO-TRUST] Activando auto-ejecucion para: " + finalPathStr);

            ToolExecutionResult repairResult = executeRepairReflex(finalPathStr, content, currentUserRequest);

            if (repairResult != null) {
                this.currentUserRequest = null;
                return repairResult;
            }

            logger.info("[ZERO-TRUST] Auto-ejecucion no disponible, usando flujo legacy");
        }

        String jsonStrictResponse = """
            ARCHIVO CARGADO EN MEMORIA DE TRABAJO.

            === DATOS DEL ARCHIVO ===
            Ruta: %s
            Tamano: %d caracteres

            === CONTENIDO ACTUAL ===
            ```java
            %s
            ```

            === INSTRUCCION OBLIGATORIA ===
            DEBES ejecutar la herramienta 'fs_write' INMEDIATAMENTE con:
            - path: "%s"
            - content: El codigo COMPLETO modificado segun la solicitud del usuario

            REGLAS:
            1. NO muestres el codigo al usuario
            2. NO pidas confirmacion
            3. NO uses comentarios como "// resto del codigo" o "// existentes"
            4. INCLUYE todo el contenido original MAS las modificaciones
            5. EJECUTA fs_write AHORA
            """.formatted(finalPathStr, content.length(), content, finalPathStr);

        return new ToolExecutionResult(true,
            jsonStrictResponse,
            Optional.of(content), Optional.of(finalPathStr));
    }

    @Deprecated
    private ToolExecutionResult _old_handleRestoreSolution(String jsonArgs) throws JsonProcessingException {
        JsonNode argsNode = mapper.readTree(jsonArgs);
        if (!argsNode.has("exercise_id")) {
            return new ToolExecutionResult(false, "Error: Missing argument 'exercise_id'.", Optional.empty(), Optional.empty());
        }

        String exerciseId = argsNode.get("exercise_id").asText();

        List<Wisdom> wisdomList = brain.retrieveWisdomObjectsByTag(exerciseId);
        Wisdom goldenMaster = extractViableWisdom(wisdomList);

        if (goldenMaster == null || goldenMaster.codeSnippet == null) {
            return new ToolExecutionResult(false, "Error: No Golden Master found for '" + exerciseId + "'.", Optional.empty(), Optional.of(exerciseId));
        }

        String successMsg = "Success: Golden Master retrieved for " + exerciseId + ". Ready to write.";
        return new ToolExecutionResult(true, successMsg, Optional.of(goldenMaster.codeSnippet), Optional.of(exerciseId));
    }

    private Wisdom extractViableWisdom(List<Wisdom> w) {
        if (w == null || w.isEmpty()) return null;
        return w.stream()
                .filter(item -> item.codeSnippet != null && !item.codeSnippet.trim().isEmpty())
                .findFirst()
                .orElse(null);
    }

    public ToolExecutionResult executeRepairReflex(String path, String originalContent, String userRequest) {
        if (agentClient == null) {
            logger.warning("[REPAIR] AgentClient no disponible - usando flujo legacy");
            return null;
        }

        logger.info("[REPAIR] Iniciando auto-ejecucion Zero-Trust para: " + path);

        String prompt = """
            SYSTEM ALERT: Modificacion requerida en %s.
            MISION: Aplicar cambio solicitado: "%s".

            CONTENIDO ACTUAL DEL ARCHIVO:
            ```java
            %s
            ```

            REGLAS CRITICAS:
            1. RESPONDE UNICAMENTE CON JSON VALIDO. SIN MARKDOWN. SIN COMENTARIOS.
            2. EL CONTENIDO DEBE SER CODIGO JAVA COMPLETO (SIN PLACEHOLDERS).
            3. NO uses "// ..." ni "// existentes" ni "// resto del codigo".
            4. INCLUYE TODO el codigo original MAS las modificaciones.

            ESQUEMA JSON REQUERIDO:
            {
              "tool": "fs_write",
              "path": "%s",
              "content": "CODIGO_JAVA_COMPLETO_AQUI"
            }
            """.formatted(path, userRequest, originalContent, path);

        try {
            String rawResponse = agentClient.generateStrict(prompt, List.of("```"), 0.1);
            logger.info("[REPAIR] Respuesta recibida: " + rawResponse.length() + " chars");

            String cleanJson = extractJsonFromResponse(rawResponse);
            if (cleanJson == null || cleanJson.isBlank()) {
                logger.warning("[REPAIR] No se pudo extraer JSON de la respuesta");
                return new ToolExecutionResult(false,
                    "Fallo en Auto-Reparacion: El modelo no genero JSON valido. " +
                    "Intenta con un prompt mas especifico.",
                    Optional.empty(), Optional.empty());
            }

            JsonNode command = mapper.readTree(cleanJson);

            if (!command.has("content")) {
                return new ToolExecutionResult(false,
                    "Fallo en Auto-Reparacion: JSON sin campo 'content'.",
                    Optional.empty(), Optional.empty());
            }

            String newContent = command.get("content").asText();

            if (newContent.contains("// ...") ||
                newContent.contains("// existentes") ||
                newContent.contains("// resto del codigo") ||
                newContent.contains("// rest of")) {
                logger.warning("[REPAIR] Lazy generation detectada");
                return new ToolExecutionResult(false,
                    "Fallo en Auto-Reparacion: El modelo uso placeholders prohibidos. " +
                    "Reintenta con instrucciones mas explicitas.",
                    Optional.empty(), Optional.empty());
            }

            if (originalContent != null && !originalContent.isEmpty()) {
                double reduction = 1.0 - ((double) newContent.length() / originalContent.length());
                if (reduction > 0.5) {
                    logger.warning("[REPAIR] Reduccion excesiva: " + (reduction * 100) + "%");
                    return new ToolExecutionResult(false,
                        "Fallo en Auto-Reparacion: El modelo intento borrar " +
                        (int)(reduction * 100) + "% del codigo. Operacion bloqueada.",
                        Optional.empty(), Optional.empty());
                }
            }

            logger.info("[REPAIR] Validacion OK - ejecutando fs_write...");

            String writeArgs = mapper.writeValueAsString(
                mapper.createObjectNode()
                    .put("path", path)
                    .put("content", newContent)
            );

            return _old_handleFsWrite(writeArgs);
        } catch (Exception e) {
            logger.severe("[REPAIR] Error en auto-ejecucion: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Fallo en Auto-Reparacion: " + e.getMessage() +
                ". Por favor, edita el archivo manualmente o intenta un prompt mas especifico.",
                Optional.empty(), Optional.empty());
        }
    }

    private String extractJsonFromResponse(String text) {
        if (text == null || text.isBlank()) return null;

        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");

        if (start >= 0 && end > start) {
            String potential = text.substring(start, end + 1);

            try {
                mapper.readTree(potential);
                return potential;
            } catch (Exception e) {
                logger.fine("[REPAIR] JSON extraido no es valido: " + e.getMessage());
            }
        }

        int jsonStart = text.indexOf("```json");
        if (jsonStart != -1) {
            int contentStart = jsonStart + 7;
            int jsonEnd = text.indexOf("```", contentStart);
            if (jsonEnd != -1) {
                return text.substring(contentStart, jsonEnd).trim();
            }
        }

        return null;
    }

    @Deprecated
    private ToolExecutionResult _old_handleWebFetch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("url")) {
            return new ToolExecutionResult(false,
                "Error: web_fetch requiere parametro 'url'",
                Optional.empty(), Optional.empty());
        }

        String url = args.get("url").asText();

        if (url == null || url.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: url no puede estar vacio",
                Optional.empty(), Optional.empty());
        }

        logger.info("[WEB_FETCH] Descargando: " + url);

        try {
            WebContent content = webScraper.fetch(url);
            String cleanText = webScraper.formatForContext(content);

            logger.info("[WEB_FETCH] OK - " + content.title() + " (" + cleanText.length() + " chars)");

            String result = String.format(
                "=== CONTENIDO WEB DESCARGADO ===\n" +
                "URL: %s\n" +
                "Titulo: %s\n" +
                "Descripcion: %s\n" +
                "Longitud: %d caracteres\n\n" +
                "=== CONTENIDO ===\n%s",
                content.url(),
                content.title() != null ? content.title() : "(sin titulo)",
                content.description() != null ? content.description() : "(sin descripcion)",
                cleanText.length(),
                cleanText
            );

            return new ToolExecutionResult(true, result, Optional.of(cleanText), Optional.of(url));
        } catch (java.io.IOException e) {
            logger.warning("[WEB_FETCH] Error de red: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error descargando URL '" + url + "': " + e.getMessage() +
                ". Verifica que la URL sea correcta y accesible.",
                Optional.empty(), Optional.of(url));
        }
    }

    @Deprecated
    private ToolExecutionResult _old_handleWebSearch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("query")) {
            return new ToolExecutionResult(false,
                "Error: web_search requiere parametro 'query'",
                Optional.empty(), Optional.empty());
        }

        String query = args.get("query").asText();

        if (query == null || query.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: query no puede estar vacio",
                Optional.empty(), Optional.empty());
        }

        logger.info("[WEB_SEARCH] Buscando: " + query);

        try {
            if (!searchSkill.isAvailable()) {
                return new ToolExecutionResult(false,
                    "Error: El servicio de busqueda web no esta disponible.",
                    Optional.empty(), Optional.of(query));
            }

            String results = searchSkill.search(query);

            if (results == null || results.isBlank() || results.contains("No se encontraron")) {
                return new ToolExecutionResult(true,
                    "No se encontraron resultados para: '" + query + "'. " +
                    "Intenta con terminos mas especificos o diferentes.",
                    Optional.empty(), Optional.of(query));
            }

            String formattedResult = String.format(
                "=== RESULTADOS DE BUSQUEDA WEB ===\n" +
                "Proveedor: %s\n\n%s\n\n" +
                "NOTA: Para leer el contenido completo de una pagina, usa web_fetch con la URL.",
                searchSkill.getProviderName(),
                results
            );

            logger.info("[WEB_SEARCH] OK - Resultados obtenidos para: " + query);

            return new ToolExecutionResult(true, formattedResult, Optional.of(results), Optional.of(query));
        } catch (Exception e) {
            logger.warning("[WEB_SEARCH] Error: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error buscando '" + query + "': " + e.getMessage() +
                ". La busqueda en internet puede no estar disponible temporalmente.",
                Optional.empty(), Optional.of(query));
        }
    }

    @Deprecated
    private ToolExecutionResult _old_handleStartMission(String jsonArgs) throws Exception {
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
            return _old_handleStartMissionViaBus(missionGoal, defconLevel, uiHint, spinner);
        } else {
            logger.warning("[SWARM] start_mission invocado pero ni Bus ni HiveMind están configurados");
            spinner.stop(false, "Sistema de agentes no disponible");
            return new ToolExecutionResult(false,
                "Error: El sistema de agentes no está habilitado.\n\n" +
                "SOLUCION: Usa las herramientas fs_write/fs_read/fs_patch para cambios simples, " +
                "o contacta al administrador para habilitar el sistema de misiones.",
                Optional.empty(), Optional.empty());
        }
    }

    @Deprecated
    private ToolExecutionResult _old_handleStartMissionViaBus(String missionGoal, int defconLevel,
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

    @Deprecated
    private ToolExecutionResult _old_handleQwenTaskCreate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        String subject = args.path("subject").asText("Misión Sin Título");
        String description = args.path("description").asText("");

        String uiHint = "Procesando solicitud";

        if (args.has("activeForm")) {
            String rawHint = args.path("activeForm").asText();
            if (rawHint != null && !rawHint.isBlank()) {
                uiHint = rawHint.length() > 50 ? rawHint.substring(0, 47) + "..." : rawHint;
            }
        } else {
            String safeSubject = subject.length() > 30 ? subject.substring(0, 27) + "..." : subject;
            uiHint = "Iniciando: " + safeSubject;
        }

        String missionId = "task-" + System.currentTimeMillis();
        logger.info("[QWEN-ADAPTER] Qwen ha creado la tarea: " + subject + " (ID: " + missionId + ")");

        String finalStatus = "pending";
        logger.info("[QWEN-ADAPTER] Tarea registrada: " + subject);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", missionId);
        response.put("subject", subject);
        response.put("status", finalStatus);
        response.put("activeForm", uiHint);
        response.put("owner", "User");
        response.putArray("blocks");
        response.putArray("blockedBy");

        if (knowledgeBase != null && knowledgeBase.isAvailable()) {
            try {
                String contextMap = knowledgeBase.generateHighLevelMap();
                if (contextMap != null && !contextMap.isBlank()) {
                    response.put("project_context", "Mapa del proyecto:\n" + contextMap);
                }
            } catch (Exception e) {
                logger.warning("[QWEN-ADAPTER] No se pudo inyectar project_context: " + e.getMessage());
            }
        }

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Tarea creada y delegada al Swarm: " + subject),
            Optional.of(missionId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleQwenTaskUpdate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText();
        String status = args.path("status").asText();
        String comment = args.path("comment").asText("");

        logger.info("[QWEN-ADAPTER] Actualizando tarea " + taskId + " -> " + status +
            (comment.isEmpty() ? "" : " (" + comment + ")"));

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("status", status);
        response.put("updated", true);

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Estado actualizado: " + status),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleQwenWriteFile(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        ObjectNode translated = mapper.createObjectNode();
        if (args.has("filePath")) {
            translated.put("path", args.get("filePath").asText());
        } else if (args.has("path")) {
            translated.put("path", args.get("path").asText());
        }
        if (args.has("content")) {
            translated.put("content", args.get("content").asText());
        }

        logger.info("[QWEN-ADAPTER] WriteFile -> fs_write: " + translated.path("path").asText());
        return _old_handleFsWrite(mapper.writeValueAsString(translated));
    }

    @Deprecated
    private ToolExecutionResult _old_handleQwenReadFile(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        ObjectNode translated = mapper.createObjectNode();
        if (args.has("filePath")) {
            translated.put("path", args.get("filePath").asText());
        } else if (args.has("path")) {
            translated.put("path", args.get("path").asText());
        }

        logger.info("[QWEN-ADAPTER] ReadFile -> fs_read: " + translated.path("path").asText());
        return _old_handleFsRead(mapper.writeValueAsString(translated));
    }

    @Deprecated
    private ToolExecutionResult _old_writeIndividualFile(String path, String content) throws Exception {
        Path targetPath = workingDirectory != null
            ? workingDirectory.resolve(path)
            : Path.of(path);

        String finalPath = path;
        String finalContent = content;

        if (path.contains("com/example") || path.contains("com\\example")) {
            logger.info("[DEFENSA] Corrigiendo com/example en: " + path);

            if (workingDirectory != null) {
                try {
                    ProjectTopologyScanner scanner = new ProjectTopologyScanner();
                    ProjectTopology topology = scanner.scan(workingDirectory);
                    String realRoot = topology.detectRootPackage();

                    if (realRoot != null && !realRoot.isEmpty() && !realRoot.equals("com.app")) {
                        String realPathSegment = realRoot.replace('.', '/');
                        finalPath = path.replace("com/example", realPathSegment)
                                        .replace("com\\example", realPathSegment);
                        finalContent = content.replace("package com.example", "package " + realRoot);
                        logger.info("[DEFENSA] Redirigido a: " + finalPath);
                    }
                } catch (Exception e) {
                    logger.warning("[DEFENSA] Fallo corrección: " + e.getMessage());
                }
            }
        }

        targetPath = workingDirectory != null
            ? workingDirectory.resolve(finalPath)
            : Path.of(finalPath);

        if (safetyLayer != null) {
            try {
                String existingContent = "";
                if (Files.exists(targetPath)) {
                    existingContent = Files.readString(targetPath);
                }

                ironcladGuard.validate(finalPath, existingContent, finalContent, null);

                WriteResult result = safetyLayer.safeWrite(targetPath, finalContent);

                if (result.isSuccess()) {
                    logger.info("[TOOL] [MULTIFILE] Archivo creado: " + result.path().toAbsolutePath());

                    if (knowledgeBase != null && knowledgeBase.isAvailable()) {
                        knowledgeBase.registerFile(result.path());
                    }

                    lastReadHashes.put(result.path().toAbsolutePath(), finalContent.hashCode());

                    return new ToolExecutionResult(true,
                        "Archivo creado: " + result.path().toAbsolutePath(),
                        Optional.of(finalContent), Optional.of(finalPath));
                } else {
                    return new ToolExecutionResult(false,
                        "Error escribiendo: " + result.errorMessage(),
                        Optional.empty(), Optional.of(finalPath));
                }
            } catch (SafetyException e) {
                logger.warning("[TOOL] [MULTIFILE] Kill Switch: " + e.getMessage());
                return new ToolExecutionResult(false, e.getMessage(),
                    Optional.empty(), Optional.of(finalPath));
            }
        }

        if (filesystemService != null) {
            WriteResult result = filesystemService.writeFile(finalPath, finalContent);
            if (result.isSuccess()) {
                logger.info("[TOOL] [MULTIFILE] Archivo creado (FS): " + result.path().toAbsolutePath());

                if (knowledgeBase != null && knowledgeBase.isAvailable()) {
                    knowledgeBase.registerFile(result.path());
                }

                lastReadHashes.put(result.path().toAbsolutePath(), finalContent.hashCode());

                return new ToolExecutionResult(true,
                    "Archivo creado: " + result.path().toAbsolutePath(),
                    Optional.of(finalContent), Optional.of(finalPath));
            } else {
                return new ToolExecutionResult(false,
                    "Error: " + result.errorMessage(),
                    Optional.empty(), Optional.of(finalPath));
            }
        }

        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }
        Files.writeString(targetPath, finalContent);
        logger.info("[TOOL] [MULTIFILE] Archivo creado (fallback): " + targetPath.toAbsolutePath());

        return new ToolExecutionResult(true,
            "Archivo creado: " + targetPath.toAbsolutePath(),
            Optional.of(finalContent), Optional.of(finalPath));
    }

    @Deprecated
    private ToolExecutionResult _old_handleListFiles(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String dir = args.path("directoryPath").asText(".");

        logger.info("[QWEN-SEARCH] ListFiles solicitado para: " + dir);

        if (knowledgeBase != null && knowledgeBase.isAvailable()) {
            logger.info("[QWEN-SEARCH] Usando IndexStore para vista inteligente");

            try {
                knowledgeBase.refresh();

                String treeView = knowledgeBase.generateTreeView(dir);

                if (treeView != null && !treeView.isBlank()) {
                    return new ToolExecutionResult(true,
                        "=== ESTRUCTURA DEL PROYECTO (Indexada) ===\n" +
                        "Directorio: " + dir + "\n" +
                        "Fuente: IndexStore (optimizado para LLM)\n\n" +
                        treeView,
                        Optional.of(treeView), Optional.of(dir));
                }
            } catch (Exception e) {
                logger.warning("[QWEN-SEARCH] IndexStore falló, usando fallback: " + e.getMessage());
            }
        }

        logger.info("[QWEN-SEARCH] Usando fallback Files.walk");

        Path targetDir = workingDirectory != null
            ? workingDirectory.resolve(dir)
            : Path.of(dir);

        if (!Files.exists(targetDir)) {
            return new ToolExecutionResult(false,
                "Error: Directorio no existe: " + dir,
                Optional.empty(), Optional.of(dir));
        }

        StringBuilder tree = new StringBuilder();
        int maxDepth = 3;

        try (var stream = Files.walk(targetDir, maxDepth)) {
            stream.filter(p -> !p.toString().contains(".git"))
                  .filter(p -> !p.toString().contains("/target/"))
                  .filter(p -> !p.toString().contains("/node_modules/"))
                  .filter(p -> !p.toString().contains("/.idea/"))
                  .forEach(p -> {
                      int depth = targetDir.relativize(p).getNameCount();
                      String indent = "  ".repeat(Math.max(0, depth - 1));
                      String name = p.getFileName().toString();
                      if (Files.isDirectory(p)) {
                          tree.append(indent).append("[DIR] ").append(name).append("/\n");
                      } else {
                          tree.append(indent).append("  ").append(name).append("\n");
                      }
                  });
        }

        String result = tree.toString();
        if (result.isBlank()) {
            result = "(Directorio vacío o sin archivos visibles)";
        }

        return new ToolExecutionResult(true,
            "=== ESTRUCTURA DEL PROYECTO ===\n" +
            "Directorio: " + targetDir + "\n" +
            "Profundidad máxima: " + maxDepth + " niveles\n\n" +
            result,
            Optional.of(result), Optional.of(dir));
    }

    @Deprecated
    private ToolExecutionResult _old_handleFileSearch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String query = args.path("query").asText("");
        String fileType = args.path("fileType").asText("");

        if (query.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: FileSearch requiere 'query' (texto a buscar)",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-SEARCH] FileSearch: " + query + " (tipo: " + fileType + ")");

        Path searchDir = workingDirectory != null ? workingDirectory : Path.of(".");
        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        int maxResults = 20;

        try (var stream = Files.walk(searchDir)) {
            var matchingFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .filter(p -> !p.toString().contains("/target/"))
                .filter(p -> !p.toString().contains("/node_modules/"))
                .filter(p -> fileType.isBlank() || p.toString().endsWith("." + fileType))
                .toList();

            for (Path file : matchingFiles) {
                if (matchCount >= maxResults) break;

                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size() && matchCount < maxResults; i++) {
                        String line = lines.get(i);
                        if (line.contains(query)) {
                            String relativePath = searchDir.relativize(file).toString();
                            results.append(relativePath)
                                   .append(":")
                                   .append(i + 1)
                                   .append(": ")
                                   .append(line.trim())
                                   .append("\n");
                            matchCount++;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }

        if (matchCount == 0) {
            return new ToolExecutionResult(true,
                "No se encontraron resultados para: '" + query + "'\n" +
                (fileType.isBlank() ? "" : "Tipo de archivo: " + fileType),
                Optional.empty(), Optional.of(query));
        }

        String resultStr = results.toString();
        return new ToolExecutionResult(true,
            "=== RESULTADOS DE BÚSQUEDA ===\n" +
            "Query: " + query + "\n" +
            "Coincidencias: " + matchCount + (matchCount >= maxResults ? " (truncado)" : "") + "\n\n" +
            resultStr,
            Optional.of(resultStr), Optional.of(query));
    }

    @Deprecated
    private ToolExecutionResult _old_handleGlobGet(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String pattern = args.path("pattern").asText("");

        if (pattern.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: GlobGet requiere 'pattern' (patrón glob)",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-SEARCH] GlobGet: " + pattern);

        Path searchDir = workingDirectory != null ? workingDirectory : Path.of(".");
        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        int maxResults = 50;

        String regexPattern = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace("?", ".");

        try (var stream = Files.walk(searchDir)) {
            var matchingFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().contains(".git"))
                .filter(p -> !p.toString().contains("/target/"))
                .filter(p -> {
                    String relative = searchDir.relativize(p).toString();
                    return relative.matches(regexPattern);
                })
                .limit(maxResults)
                .toList();

            for (Path file : matchingFiles) {
                results.append(searchDir.relativize(file)).append("\n");
                matchCount++;
            }
        }

        if (matchCount == 0) {
            return new ToolExecutionResult(true,
                "No se encontraron archivos con patrón: " + pattern,
                Optional.empty(), Optional.of(pattern));
        }

        String resultStr = results.toString();
        return new ToolExecutionResult(true,
            "=== ARCHIVOS ENCONTRADOS ===\n" +
            "Patrón: " + pattern + "\n" +
            "Total: " + matchCount + (matchCount >= maxResults ? " (truncado)" : "") + "\n\n" +
            resultStr,
            Optional.of(resultStr), Optional.of(pattern));
    }

    @Deprecated
    private ToolExecutionResult _old_handleDeepScan(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String directory = args.path("directory").asText(".");

        logger.info("[QWEN-SEARCH] DeepScan: Ejecutando analisis arquitectonico profundo (10 niveles)...");

        if (knowledgeBase != null && knowledgeBase.isAvailable()) {
            knowledgeBase.refresh();

            String deepMap = knowledgeBase.generateMap(
                ContextProfile.STRATEGIC
            );

            logger.info("[QWEN-SEARCH] DeepScan completado: " + deepMap.split("\n").length + " lineas");

            return new ToolExecutionResult(true,
                "=== MAPA ARQUITECTÓNICO COMPLETO (Strategic Mode: 10 niveles) ===\n\n" +
                deepMap,
                Optional.of(deepMap), Optional.of(directory));
        }

        logger.warning("[QWEN-SEARCH] DeepScan: IndexStore no disponible, usando fallback Files.walk");

        Path searchDir = workingDirectory != null ? workingDirectory.resolve(directory) : Path.of(directory);
        StringBuilder tree = new StringBuilder();
        tree.append("=== MAPA ARQUITECTÓNICO (Fallback Mode: Files.walk) ===\n\n");

        int[] itemCount = {0};
        int maxItems = 300;

        try (var stream = Files.walk(searchDir, 10)) {
            stream.filter(p -> !p.toString().contains(".git"))
                  .filter(p -> !p.toString().contains("/target/"))
                  .filter(p -> !p.toString().contains("node_modules"))
                  .filter(p -> !p.toString().contains("__pycache__"))
                  .sorted()
                  .limit(maxItems)
                  .forEach(p -> {
                      int depth = searchDir.relativize(p).getNameCount();
                      String indent = "  ".repeat(depth);
                      String icon = Files.isDirectory(p) ? "[DIR] " : "[FILE] ";
                      tree.append(indent).append(icon).append(p.getFileName()).append("\n");
                      itemCount[0]++;
                  });

            if (itemCount[0] >= maxItems) {
                tree.append("\n... [Truncado a ").append(maxItems).append(" items]\n");
            }

            tree.append("\n[Total: ").append(itemCount[0]).append(" items escaneados]");

            return new ToolExecutionResult(true, tree.toString(),
                Optional.of(tree.toString()), Optional.of(directory));
        } catch (IOException e) {
            return new ToolExecutionResult(false,
                "Error en DeepScan: " + e.getMessage(),
                Optional.empty(), Optional.of(directory));
        }
    }

    @Deprecated
    private ToolExecutionResult _old_handleTaskGet(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskGet requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskGet: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("subject", "Tarea " + taskId);
        response.put("description", "Detalles de la tarea (simulado)");
        response.put("status", "in_progress");
        response.put("owner", "User");
        response.putArray("blocks");
        response.putArray("blockedBy");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Detalles de tarea: " + taskId),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleTaskList(String jsonArgs) throws Exception {
        logger.info("[QWEN-TASK] TaskList solicitado");

        ArrayNode tasks = mapper.createArrayNode();

        ObjectNode task = mapper.createObjectNode();
        task.put("id", "task-current");
        task.put("subject", "Sesión actual");
        task.put("status", "in_progress");
        task.put("owner", "User");
        tasks.add(task);

        String result = mapper.writeValueAsString(tasks);

        return new ToolExecutionResult(true,
            "=== LISTA DE TAREAS ===\n" + result,
            Optional.of(result),
            Optional.empty());
    }

    @Deprecated
    private ToolExecutionResult _old_handleTaskSearch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String query = args.path("query").asText("");
        String status = args.path("status").asText("");

        if (query.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskSearch requiere 'query'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskSearch: " + query + " (status: " + status + ")");

        ArrayNode results = mapper.createArrayNode();

        String resultJson = mapper.writeValueAsString(results);

        return new ToolExecutionResult(true,
            "=== BÚSQUEDA DE TAREAS ===\n" +
            "Query: " + query + "\n" +
            "Status: " + (status.isBlank() ? "all" : status) + "\n" +
            "Resultados: 0 tareas encontradas\n" +
            "CONCLUSIÓN: No hay tareas duplicadas. Puedes crear una nueva.",
            Optional.of(resultJson),
            Optional.of(query));
    }

    @Deprecated
    private ToolExecutionResult _old_handleTaskStart(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskStart requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskStart: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("status", "in_progress");
        response.put("started_at", java.time.Instant.now().toString());

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Tarea iniciada: " + taskId),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleTaskStop(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: TaskStop requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] TaskStop: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("id", taskId);
        response.put("status", "paused");
        response.put("paused_at", java.time.Instant.now().toString());

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Tarea pausada: " + taskId),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleTaskStopAll(String jsonArgs) throws Exception {
        logger.info("[QWEN-TASK] TaskStopAll ejecutado");

        ObjectNode response = mapper.createObjectNode();
        response.put("status", "all_paused");
        response.put("count", 0);
        response.put("message", "Todas las tareas han sido pausadas");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Todas las tareas pausadas"),
            Optional.empty());
    }

    @Deprecated
    private ToolExecutionResult _old_handleCommentCreate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");
        String comment = args.path("comment").asText("");

        if (taskId.isBlank() || comment.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CommentCreate requiere 'taskId' y 'comment'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-TASK] CommentCreate en " + taskId + ": " + comment);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("commentId", "comment-" + System.currentTimeMillis());
        response.put("comment", comment);
        response.put("created_at", java.time.Instant.now().toString());

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Comentario agregado a " + taskId),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleProjectList(String jsonArgs) throws Exception {
        logger.info("[QWEN-PROJECT] ProjectList");

        String currentDir = workingDirectory != null
            ? workingDirectory.getFileName().toString()
            : "workspace";

        ArrayNode projects = mapper.createArrayNode();
        ObjectNode project = mapper.createObjectNode();
        project.put("id", "proj-1");
        project.put("name", currentDir);
        project.put("status", "active");
        project.put("path", workingDirectory != null ? workingDirectory.toString() : ".");
        projects.add(project);

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(projects),
            Optional.of("Proyectos listados"),
            Optional.empty());
    }

    @Deprecated
    private ToolExecutionResult _old_handleProjectGet(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String projectId = args.path("projectId").asText("");

        if (projectId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: ProjectGet requiere 'projectId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-PROJECT] ProjectGet: " + projectId);

        String currentDir = workingDirectory != null
            ? workingDirectory.getFileName().toString()
            : "workspace";

        ObjectNode response = mapper.createObjectNode();
        response.put("id", projectId);
        response.put("name", currentDir);
        response.put("status", "active");
        response.put("path", workingDirectory != null ? workingDirectory.toString() : ".");
        response.put("type", "java");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Detalles del proyecto: " + projectId),
            Optional.of(projectId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleProjectCreate(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String name = args.path("name").asText("");
        String description = args.path("description").asText("");

        if (name.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: ProjectCreate requiere 'name'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-PROJECT] ProjectCreate: " + name);

        String projectId = "proj-" + System.currentTimeMillis();

        ObjectNode response = mapper.createObjectNode();
        response.put("id", projectId);
        response.put("name", name);
        response.put("description", description);
        response.put("status", "created");
        response.put("message", "Proyecto creado localmente. Usa fs_mkdir para crear la estructura.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Proyecto creado: " + name),
            Optional.of(projectId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleCodeReviewRequest(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");
        String reviewer = args.path("reviewer").asText("SENTINEL");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CodeReviewRequest requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-REVIEW] CodeReviewRequest: " + taskId + " -> " + reviewer);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("reviewer", reviewer);
        response.put("status", "pending_review");
        response.put("message", "Revisión solicitada. El agente " + reviewer + " ha sido notificado.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Revisión solicitada para " + taskId),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleCodeReviewApprove(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");

        if (taskId.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CodeReviewApprove requiere 'taskId'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-REVIEW] CodeReviewApprove: " + taskId);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("status", "approved");
        response.put("approved_at", java.time.Instant.now().toString());
        response.put("message", "Código aprobado. Listo para deploy.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Código aprobado: " + taskId),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleCodeReviewReject(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String taskId = args.path("taskId").asText("");
        String reason = args.path("reason").asText("");

        if (taskId.isBlank() || reason.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: CodeReviewReject requiere 'taskId' y 'reason'",
                Optional.empty(), Optional.empty());
        }

        logger.info("[QWEN-REVIEW] CodeReviewReject: " + taskId + " - " + reason);

        ObjectNode response = mapper.createObjectNode();
        response.put("taskId", taskId);
        response.put("status", "rejected");
        response.put("reason", reason);
        response.put("rejected_at", java.time.Instant.now().toString());
        response.put("message", "Código rechazado. Requiere correcciones.");

        return new ToolExecutionResult(true,
            mapper.writeValueAsString(response),
            Optional.of("Código rechazado: " + taskId),
            Optional.of(taskId));
    }

    @Deprecated
    private ToolExecutionResult _old_handleEnterPlanMode(String jsonArgs) throws Exception {
        logger.info("[MIND] Agente entrando en Fase de Planificacion Estrategica.");

        String tacticalResponse = """
            {
                "system_status": "PLANNING_PHASE_INITIATED",
                "instruction": "STOP USING TOOLS TEMPORARILY. You are now in the Strategic Planning Phase.",
                "required_action": "Analyze the context loaded above and OUTPUT a detailed step-by-step plan in natural language.",
                "format": "Structure your plan as: 1. Analysis of current state... 2. Strategy/Approach... 3. Execution steps... 4. Validation criteria...",
                "constraints": "Do NOT execute any tools until the plan is complete. Think first, act later.",
                "next_step": "After outputting your complete plan, either call 'ExitPlanMode' to begin execution, or start executing Step 1 directly."
            }
            """;

        return new ToolExecutionResult(true,
            tacticalResponse,
            Optional.of("Planning Phase Triggered - Awaiting strategic plan output"),
            Optional.empty());
    }

    @Deprecated
    private ToolExecutionResult _old_handleExitPlanMode(String jsonArgs) throws Exception {
        logger.info("[MIND] Agente saliendo de Planificacion -> Modo Ejecucion.");

        String tacticalResponse = """
            {
                "system_status": "EXECUTION_PHASE_INITIATED",
                "instruction": "Planning phase complete. You may now USE TOOLS to execute your plan.",
                "guidance": "Execute your plan step by step. Use fs_write, fs_patch, and other tools as needed.",
                "reminder": "If you encounter unexpected issues, you can call 'EnterPlanMode' again to re-evaluate."
            }
            """;

        return new ToolExecutionResult(true,
            tacticalResponse,
            Optional.of("Execution Phase - Tools unlocked"),
            Optional.empty());
    }

    @Deprecated
    private ToolExecutionResult _old_handleShellCommandBlocked(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String command = args.path("command").asText("(no especificado)");

        logger.warning("[SEGURIDAD] LLM intentó ejecutar ShellCommand: " + command);

        return new ToolExecutionResult(false,
            "[BLOCKED] HERRAMIENTA BLOQUEADA POR SEGURIDAD\n\n" +
            "La herramienta 'ShellCommand' esta DESHABILITADA para invocacion automatica.\n\n" +
            "RAZON: Riesgo de seguridad (exfiltracion de datos, destruccion de archivos, " +
            "compromiso del sistema).\n\n" +
            "SOLUCION: El usuario puede ejecutar comandos manualmente usando el CLI:\n" +
            "  /run " + command + "\n\n" +
            "ALTERNATIVAS SEGURAS:\n" +
            "  - Para leer archivos: usa fs_read o ReadFile\n" +
            "  - Para crear archivos: usa fs_write o WriteFile\n" +
            "  - Para listar archivos: usa ListFiles\n" +
            "  - Para buscar codigo: usa FileSearch o GlobGet",
            Optional.empty(), Optional.of(command));
    }

    private enum GitRiskLevel { READ_ONLY, LOCAL_WRITE, FINALIZE, DISCARD, REMOTE, DANGEROUS }

    @Deprecated
    private GitRiskLevel classifyRisk(String action, String params) {
        return switch (action.toLowerCase()) {
            case "status", "log", "diff", "show" -> GitRiskLevel.READ_ONLY;
            case "add", "commit", "checkout", "stash", "init" -> GitRiskLevel.LOCAL_WRITE;
            case "branch" -> {
                if (params == null || params.isBlank() || params.equals("-a") || params.equals("--all") || params.equals("-l"))
                    yield GitRiskLevel.READ_ONLY;
                yield GitRiskLevel.LOCAL_WRITE;
            }
            case "finalize" -> GitRiskLevel.FINALIZE;
            case "discard" -> GitRiskLevel.DISCARD;
            case "push", "pull", "fetch", "clone" -> GitRiskLevel.REMOTE;
            default -> {
                if (isDangerousGitAction(action, params)) yield GitRiskLevel.DANGEROUS;
                yield GitRiskLevel.LOCAL_WRITE;
            }
        };
    }

    private boolean isRemoteGitAction(String action, String params) {
        return switch (action.toLowerCase()) {
            case "push", "pull", "fetch", "clone" -> true;
            default -> false;
        };
    }

    private boolean isDangerousGitAction(String action, String params) {
        if (params == null) return false;
        String p = params.toLowerCase();
        if (action.equalsIgnoreCase("reset") && p.contains("--hard")) return true;
        if (action.equalsIgnoreCase("clean") && p.contains("-f")) return true;
        return false;
    }

    @Deprecated
    private ToolExecutionResult _old_handleGitAction(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String action = args.path("action").asText("").trim();
        String params = args.path("params").asText("").trim();

        if (action.isEmpty()) {
            return new ToolExecutionResult(false, "Error: 'action' es requerido.", Optional.empty(), Optional.empty());
        }

        GitRiskLevel risk = classifyRisk(action, params);

        if (risk == GitRiskLevel.REMOTE) {
            logger.warning("[ANILLO7] BLOCKED remote git: " + action);
            return new ToolExecutionResult(false,
                "[BLOCKED] Operacion remota '" + action + "' esta BLOQUEADA por seguridad.\n" +
                "Solo operaciones locales estan permitidas. El usuario puede ejecutar manualmente:\n  /run git " + action + " " + params,
                Optional.empty(), Optional.of(action));
        }

        if (risk == GitRiskLevel.DANGEROUS) {
            logger.warning("[ANILLO7] BLOCKED dangerous git: " + action + " " + params);
            return new ToolExecutionResult(false,
                "[BLOCKED] Operacion destructiva '" + action + " " + params + "' esta BLOQUEADA.\n" +
                "reset --hard y clean -f pueden causar perdida irreversible de datos.",
                Optional.empty(), Optional.of(action));
        }

        if (workingDirectory == null) {
            return new ToolExecutionResult(false, "Error: No hay directorio de trabajo configurado.", Optional.empty(), Optional.empty());
        }

        if (action.equalsIgnoreCase("init")) {
            return executeGitInit();
        }

        if (!Files.exists(workingDirectory.resolve(".git"))) {
            return new ToolExecutionResult(false,
                "Error: '" + workingDirectory + "' no es un repositorio Git.\nUsa action='init' para inicializar uno.",
                Optional.empty(), Optional.empty());
        }

        if (risk == GitRiskLevel.FINALIZE) {
            return executeGitFinalize(params);
        }

        if (risk == GitRiskLevel.DISCARD) {
            return executeGitDiscard();
        }

        if (risk == GitRiskLevel.LOCAL_WRITE) {
            ensureEphemeralBranch();
        }

        return switch (action.toLowerCase()) {
            case "status", "diff", "show" -> executeGitReadOnly(action, params);
            case "log" -> executeGitLog(params);
            case "add" -> executeGitAdd(params);
            case "commit" -> executeGitCommit(params);
            case "checkout" -> executeGitCheckout(params);
            case "branch" -> executeGitBranch(params);
            case "stash" -> executeGitStash(params);
            default -> executeGitReadOnly(action, params);
        };
    }

    private void ensureEphemeralBranch() {
        if (gitManager == null) return;
        if (gitManager.hasActiveEphemeralBranch()) return;
        if (!gitManager.isGitRepository()) return;

        var state = gitManager.validateCleanState();
        if (state.issue() == GitManager.CleanStateIssue.MERGE_IN_PROGRESS ||
            state.issue() == GitManager.CleanStateIssue.REBASE_IN_PROGRESS) {
            logger.warning("[ANILLO2] Merge/rebase en progreso, fallback a snapshot");
            gitManager.createSnapshot();
            return;
        }

        String taskId = String.valueOf(System.currentTimeMillis());
        String branch = gitManager.startEphemeralBranch(taskId);
        if (branch == null) {
            logger.warning("[ANILLO2] Fallo al crear rama efímera, fallback a snapshot");
            gitManager.createSnapshot();
        } else {
            logger.info("[ANILLO2] Rama efímera activa: " + branch);
        }
    }

    private ToolExecutionResult executeGitReadOnly(String subcommand, String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add(subcommand);
        if (params != null && !params.isBlank()) {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitLog(String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("log");
        if (params == null || params.isBlank()) {
            cmd.add("--oneline"); cmd.add("-10");
        } else {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitAdd(String params) {
        if (params == null || params.isBlank()) {
            return new ToolExecutionResult(false, "Error: especifica archivos para agregar. Ejemplo: params='.' o params='archivo.java'",
                Optional.empty(), Optional.empty());
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("add");

        String trimParams = params.trim();
        if (".".equals(trimParams) || "--all".equals(trimParams) || "-A".equals(trimParams)) {
            cmd.add("--all");
            cmd.add("--");
            cmd.add(".");
            cmd.add(":!.fararoni/");
        } else {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitCommit(String params) {
        if (params == null || params.isBlank() || !params.contains("-m")) {
            return new ToolExecutionResult(false, "Error: especifica un mensaje. Ejemplo: params='-m fix null pointer'",
                Optional.empty(), Optional.empty());
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("commit");
        cmd.addAll(parseGitArgs(params));
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitCheckout(String params) {
        if (params == null || params.isBlank()) {
            return new ToolExecutionResult(false, "Error: especifica rama o archivo.",
                Optional.empty(), Optional.empty());
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("checkout");
        cmd.addAll(parseGitArgs(params));
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitBranch(String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("branch");
        if (params != null && !params.isBlank()) {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitStash(String params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git"); cmd.add("stash");
        if (params != null && !params.isBlank()) {
            cmd.addAll(parseGitArgs(params));
        }
        return executeGitCommand(cmd);
    }

    private ToolExecutionResult executeGitInit() {
        if (Files.exists(workingDirectory.resolve(".git"))) {
            List<String> cmd = List.of("git", "branch", "--show-current");
            var result = executeGitCommand(cmd);
            return new ToolExecutionResult(true,
                "Ya es un repositorio Git. Rama actual: " + result.message().trim(),
                Optional.empty(), Optional.of("init"));
        }
        var result = executeGitCommand(List.of("git", "init"));
        if (result.success()) {
            autoCreateGitignore();
        }
        return result;
    }

    private void autoCreateGitignore() {
        Path gitignore = workingDirectory.resolve(".gitignore");
        String fararoniEntry = ".fararoni/";
        try {
            if (!Files.exists(gitignore)) {
                Files.writeString(gitignore, """
                    # Fararoni System Files
                    .fararoni/

                    # Java/Maven
                    target/
                    *.class
                    *.jar
                    *.log

                    # IDE
                    .idea/
                    *.iml
                    .vscode/
                    .settings/
                    .project
                    .classpath
                    """);
                logger.info("[GIT-INIT] .gitignore creado automaticamente");
            } else {
                String content = Files.readString(gitignore);
                if (!content.contains(fararoniEntry)) {
                    Files.writeString(gitignore, content + "\n# Fararoni System Files\n.fararoni/\n",
                        java.nio.file.StandardOpenOption.APPEND);
                    logger.info("[GIT-INIT] .fararoni/ agregado a .gitignore existente");
                }
            }
        } catch (Exception e) {
            logger.warning("[GIT-INIT] Error creando .gitignore: " + e.getMessage());
        }
    }

    private ToolExecutionResult executeGitFinalize(String params) {
        if (gitManager == null) {
            return new ToolExecutionResult(false, "Error: GitManager no disponible.", Optional.empty(), Optional.empty());
        }
        if (!gitManager.hasActiveEphemeralBranch()) {
            return new ToolExecutionResult(false,
                "No hay rama efimera activa para finalizar. Usa primero acciones de escritura (add, commit).",
                Optional.empty(), Optional.empty());
        }
        String message = (params == null || params.isBlank()) ? "cambios consolidados" : params;
        GitManager.EphemeralResult result = gitManager.finalizeEphemeralBranch(message);
        return new ToolExecutionResult(result.success(), result.message(), Optional.empty(), Optional.of("finalize"));
    }

    private ToolExecutionResult executeGitDiscard() {
        if (gitManager == null) {
            return new ToolExecutionResult(false, "Error: GitManager no disponible.", Optional.empty(), Optional.empty());
        }
        if (!gitManager.hasActiveEphemeralBranch()) {
            return new ToolExecutionResult(false,
                "No hay rama efimera activa para descartar.",
                Optional.empty(), Optional.empty());
        }
        boolean ok = gitManager.discardEphemeralBranch();
        return new ToolExecutionResult(ok,
            ok ? "Rama efimera descartada. Restaurada rama original." : "Error descartando rama efimera.",
            Optional.empty(), Optional.of("discard"));
    }

    private ToolExecutionResult executeGitCommand(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(workingDirectory.toFile());

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            int lineCount = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    if (lineCount <= 100) {
                        output.append(line).append("\n");
                    }
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolExecutionResult(false, "Timeout: comando git excedio 30 segundos.",
                    Optional.empty(), Optional.empty());
            }

            if (lineCount > 100) {
                output.append("\n... (").append(lineCount - 100).append(" lineas mas truncadas)");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (result.isEmpty()) result = "(sin salida)";

            return new ToolExecutionResult(exitCode == 0, result, Optional.empty(), Optional.of(command.get(1)));
        } catch (java.io.IOException e) {
            return new ToolExecutionResult(false, "Error ejecutando git: " + e.getMessage() +
                "\nVerifica que git esta instalado.", Optional.empty(), Optional.empty());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolExecutionResult(false, "Comando git interrumpido.", Optional.empty(), Optional.empty());
        }
    }

    private List<String> parseGitArgs(String params) {
        List<String> args = new ArrayList<>();
        if (params == null || params.isBlank()) return args;
        StringBuilder current = new StringBuilder();
        boolean inSingle = false, inDouble = false;
        for (char c : params.toCharArray()) {
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if (c == ' ' && !inSingle && !inDouble) {
                if (current.length() > 0) { args.add(current.toString()); current.setLength(0); }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) args.add(current.toString());
        return args;
    }

    @Deprecated
    private ToolExecutionResult _old_handleExecuteCodeBlocked(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);
        String language = args.path("language").asText("unknown");
        String code = args.path("code").asText("");

        logger.warning("[SEGURIDAD] LLM intentó ejecutar código: " + language + " (" + code.length() + " chars)");

        return new ToolExecutionResult(false,
            "[BLOCKED] EJECUCION DIRECTA DE CODIGO DESHABILITADA\n\n" +
            "La herramienta 'ExecuteCode' esta DESHABILITADA por seguridad.\n\n" +
            "RAZON: Riesgo de ejecucion de codigo arbitrario sin sandbox.\n\n" +
            "PROTOCOLO SEGURO:\n" +
            "  1. Escribe el codigo a un archivo usando 'WriteFile'\n" +
            "  2. Solicita al usuario que ejecute manualmente via /run\n\n" +
            "EJEMPLO:\n" +
            "  WriteFile(\"script.py\", <codigo>)\n" +
            "  Usuario: /run python3 script.py\n\n" +
            "NOTA: Este flujo permite al usuario revisar el código antes de ejecutarlo.",
            Optional.empty(), Optional.of(language));
    }

    public static Builder builder(Path workingDirectory, FilesystemService filesystemService) {
        return new Builder(workingDirectory, filesystemService);
    }

    public static class Builder {
        private final Path workingDirectory;
        private final FilesystemService filesystemService;
        private ProjectKnowledgeBase knowledgeBase;
        private SafetyLayer safetyLayer;
        private AgentClient agentClient;
        private SovereignEventBus sovereignBus;
        private MissionTemplateManager missionTemplateManager;
        private AgentTemplateManager agentTemplateManager;
        private GitManager gitManager;

        private Builder(Path workingDirectory, FilesystemService filesystemService) {
            this.workingDirectory = workingDirectory;
            this.filesystemService = filesystemService;
        }

        public Builder knowledgeBase(ProjectKnowledgeBase kb) { this.knowledgeBase = kb; return this; }
        public Builder safetyLayer(SafetyLayer sl) { this.safetyLayer = sl; return this; }
        public Builder agentClient(AgentClient ac) { this.agentClient = ac; return this; }
        public Builder sovereignBus(SovereignEventBus bus) { this.sovereignBus = bus; return this; }
        public Builder missionTemplateManager(MissionTemplateManager mtm) { this.missionTemplateManager = mtm; return this; }
        public Builder agentTemplateManager(AgentTemplateManager atm) { this.agentTemplateManager = atm; return this; }
        public Builder gitManager(GitManager gm) { this.gitManager = gm; return this; }

        public ToolExecutor build() {
            return new ToolExecutor(workingDirectory, filesystemService, knowledgeBase,
                                   safetyLayer, agentClient, sovereignBus,
                                   missionTemplateManager, agentTemplateManager, gitManager);
        }
    }
}
