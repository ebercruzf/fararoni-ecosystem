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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fararoni.core.core.clients.AgentClient;
import dev.fararoni.core.core.domain.ArchitecturePatternDetector;
import dev.fararoni.core.core.domain.BusinessDomainDictionary;
import dev.fararoni.core.core.domain.DomainInferenceEngine;
import dev.fararoni.core.core.domain.DomainInferenceResult;
import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.core.index.ProjectKnowledgeBase;
import dev.fararoni.core.core.managers.BiblioCognitiveTriadManager;
import dev.fararoni.core.core.memory.Wisdom;
import dev.fararoni.core.core.safety.IroncladGuard;
import dev.fararoni.core.core.safety.SafetyException;
import dev.fararoni.core.core.safety.SafetyLayer;
import dev.fararoni.core.core.skills.AmbiguousPathException;
import dev.fararoni.core.core.skills.ContextualPathResolver;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.core.topology.ProjectTopology;
import dev.fararoni.core.core.topology.ProjectTopologyScanner;
import dev.fararoni.core.core.utils.MultiFileParser;
import dev.fararoni.core.service.FilesystemService;
import dev.fararoni.core.service.WriteResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ToolExecFilesystemHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecFilesystemHandlers.class.getName());

    private final ObjectMapper mapper;
    private final Path workingDirectory;
    private final FilesystemService filesystemService;
    private final SafetyLayer safetyLayer;
    private final IroncladGuard ironcladGuard;
    private final ProjectKnowledgeBase knowledgeBase;
    private final ContextualPathResolver pathResolver;
    private final AgentClient agentClient;
    private final Map<Path, Integer> lastReadHashes;

    private String currentUserRequest;

    public ToolExecFilesystemHandlers(
            ObjectMapper mapper,
            Path workingDirectory,
            FilesystemService filesystemService,
            SafetyLayer safetyLayer,
            IroncladGuard ironcladGuard,
            ProjectKnowledgeBase knowledgeBase,
            ContextualPathResolver pathResolver,
            AgentClient agentClient,
            Map<Path, Integer> lastReadHashes) {
        this.mapper = mapper;
        this.workingDirectory = workingDirectory;
        this.filesystemService = filesystemService;
        this.safetyLayer = safetyLayer;
        this.ironcladGuard = (ironcladGuard != null) ? ironcladGuard : new IroncladGuard();
        this.knowledgeBase = knowledgeBase;
        this.pathResolver = (pathResolver != null) ? pathResolver : new ContextualPathResolver();
        this.agentClient = agentClient;
        this.lastReadHashes = lastReadHashes;
    }

    public void setCurrentUserRequest(String req) {
        this.currentUserRequest = req;
    }

    public ToolExecutionResult handleFsWrite(String jsonArgs) throws Exception {
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

                    ToolExecutionResult subResult = writeIndividualFile(subPath, subContent);

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

    public ToolExecutionResult handleFsPatch(String jsonArgs) throws Exception {
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

        String truncatedContent = truncateForErrorContext(originalContent, pathStr);

        String normalizedOriginal = normalizeLineEndings(originalContent);
        String normalizedSearch = normalizeLineEndings(searchBlock);

        if (normalizedOriginal.contains(normalizedSearch)) {
            logger.warning("[PATCH] Match normalizado encontrado pero no exacto en: " + pathStr);
            return new ToolExecutionResult(false,
                "Error de Precision: El bloque 'search' existe pero con diferencias de espacios/indentacion.\n" +
                "Copia el bloque EXACTO del contenido actual que se muestra a continuacion.\n\n" +
                truncatedContent,
                Optional.empty(), Optional.empty());
        }

        logger.warning("[PATCH] Bloque search no encontrado en: " + pathStr);
        return new ToolExecutionResult(false,
            "FALLO DE PARCHE: El bloque 'search' no existe en el archivo.\n" +
            "Revisa el contenido REAL del archivo y usa un bloque de codigo unico que SI exista.\n\n" +
            truncatedContent,
            Optional.empty(), Optional.empty());
    }

    private String normalizeLineEndings(String content) {
        return content
            .replaceAll("\\r\\n", "\n")
            .replaceAll("\\r", "\n")
            .trim();
    }

    private String truncateForErrorContext(String content, String pathStr) {
        final int MAX_LINES = 300;
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        sb.append("═══ CONTENIDO ACTUAL DE: ").append(pathStr).append(" ═══\n");

        int lineCount = Math.min(lines.length, MAX_LINES);
        for (int i = 0; i < lineCount; i++) {
            sb.append(String.format("%4d | %s\n", i + 1, lines[i]));
        }

        if (lines.length > MAX_LINES) {
            sb.append("... (").append(lines.length - MAX_LINES)
              .append(" lineas adicionales truncadas, total: ").append(lines.length).append(")\n");
        }

        sb.append("═══ FIN DEL ARCHIVO ═══");
        return sb.toString();
    }

    public ToolExecutionResult handleFsMkdir(String jsonArgs) throws Exception {
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

    public ToolExecutionResult handleFsRead(String jsonArgs) throws Exception {
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

        logger.info("[SECURE-READ] Retornando contenido sin instrucciones internas: " + finalPathStr);

        String safeResponse = """
            Archivo cargado: %s (%d caracteres).
            Contenido disponible en memoria de trabajo para modificacion via fs_write.
            """.formatted(finalPathStr, content.length());

        return new ToolExecutionResult(true,
            safeResponse,
            Optional.of(content), Optional.of(finalPathStr));
    }

    public ToolExecutionResult handleRestoreSolution(String jsonArgs) throws JsonProcessingException {
        JsonNode argsNode = mapper.readTree(jsonArgs);
        if (!argsNode.has("exercise_id")) {
            return new ToolExecutionResult(false, "Error: Missing argument 'exercise_id'.", Optional.empty(), Optional.empty());
        }

        String exerciseId = argsNode.get("exercise_id").asText();

        BiblioCognitiveTriadManager brain = BiblioCognitiveTriadManager.getInstance();
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

            return handleFsWrite(writeArgs);
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

    public ToolExecutionResult handleQwenWriteFile(String jsonArgs) throws Exception {
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
        return handleFsWrite(mapper.writeValueAsString(translated));
    }

    public ToolExecutionResult handleQwenReadFile(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        ObjectNode translated = mapper.createObjectNode();
        if (args.has("filePath")) {
            translated.put("path", args.get("filePath").asText());
        } else if (args.has("path")) {
            translated.put("path", args.get("path").asText());
        }

        logger.info("[QWEN-ADAPTER] ReadFile -> fs_read: " + translated.path("path").asText());
        return handleFsRead(mapper.writeValueAsString(translated));
    }

    private ToolExecutionResult writeIndividualFile(String path, String content) throws Exception {
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
}
