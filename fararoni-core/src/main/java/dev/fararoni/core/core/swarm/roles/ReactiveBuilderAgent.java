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
package dev.fararoni.core.core.swarm.roles;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.agents.ReactiveSwarmAgent;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine.TaskPayload;
import dev.fararoni.core.core.skills.FileSystemSkillImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ReactiveBuilderAgent extends ReactiveSwarmAgent {
    private static final Logger LOG = Logger.getLogger(ReactiveBuilderAgent.class.getName());

    private final Map<String, List<Path>> createdFilesByExecution = new ConcurrentHashMap<>();

    private final BiFunction<String, String, String> llmFunction;

    private final FileSystemSkillImpl fileSkill;

    private final Path workspace;

    public ReactiveBuilderAgent(
            SovereignEventBus bus,
            BiFunction<String, String, String> llmFunction,
            Path workspace) {
        super("builder", bus);
        this.llmFunction = llmFunction;
        this.workspace = workspace;
        this.fileSkill = new FileSystemSkillImpl(workspace);

        LOG.info("ReactiveBuilderAgent initialized with workspace: " + workspace);
    }

    @Override
    protected AgentResult processTask(SovereignEnvelope<?> envelope) {
        logThinking("Analizando tarea de construcción...");

        TaskPayload task = extractTaskPayload(envelope);
        if (task == null) {
            return AgentResult.failure("No se pudo extraer TaskPayload del envelope");
        }

        String executionId = task.executionId();
        String context = task.contextJson();
        String systemPromptOverride = task.systemPromptOverride();

        createdFilesByExecution.computeIfAbsent(executionId, k ->
            Collections.synchronizedList(new ArrayList<>())
        );

        if (isWeakModel()) {
            LOG.severe("[BUILDER-BLOCK] Modelo 1.5B no puede generar archivos");
            return AgentResult.failure(
                "Misión pausada: El modelo local (1.5B) no tiene capacidad cognitiva " +
                "para generar archivos. Esperando a que Qwen/Tortuga (7B+) esté disponible."
            );
        }

        logThinking("Generando código con LLM...");

        String systemPrompt = systemPromptOverride != null ? systemPromptOverride : getDefaultSystemPrompt();
        String userPrompt = buildUserPrompt(context);

        String llmResponse;
        try {
            llmResponse = llmFunction.apply(systemPrompt, userPrompt);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error calling LLM", e);
            return AgentResult.failure("Error en LLM: " + e.getMessage());
        }

        logAction("Procesando respuesta del LLM...");

        Map<String, String> filesToCreate = extractMultipleFiles(llmResponse);

        if (filesToCreate.isEmpty()) {
            String filename = inferFilename(context);
            String code = extractCode(llmResponse);
            if (!code.isBlank()) {
                filesToCreate.put(filename, code);
            }
        }

        if (filesToCreate.isEmpty()) {
            return AgentResult.failure("No se pudo extraer código del LLM response");
        }

        StringBuilder report = new StringBuilder();
        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<String, String> entry : filesToCreate.entrySet()) {
            String filename = entry.getKey();
            String content = entry.getValue();

            logAction("Escribiendo: " + filename);

            try {
                var result = fileSkill.writeFile(filename, content);
                if (result.success()) {
                    Path createdPath = workspace.resolve(filename).normalize();
                    registerCreatedFile(executionId, createdPath);

                    successCount++;
                    report.append("[OK] ").append(filename).append("\n");
                    LOG.info("Archivo creado y registrado: " + filename);
                } else {
                    failCount++;
                    report.append("[ERROR] ").append(filename).append(": ").append(result.error()).append("\n");
                }
            } catch (Exception e) {
                failCount++;
                report.append("[ERROR] ").append(filename).append(": ").append(e.getMessage()).append("\n");
                LOG.log(Level.WARNING, "Error writing file: " + filename, e);
            }
        }

        String summary = String.format("Builder completado: %d archivos OK, %d fallidos\n%s",
            successCount, failCount, report);

        if (failCount > 0) {
            return AgentResult.failure(summary);
        }

        return AgentResult.success(summary, filesToCreate.keySet());
    }

    @Override
    protected AgentResult compensateTask(SovereignEnvelope<?> envelope) {
        logThinking("Iniciando compensación (rollback de archivos)...");

        TaskPayload task = extractTaskPayload(envelope);
        if (task == null) {
            String executionId = extractExecutionIdFromCompensation(envelope);
            if (executionId == null) {
                return AgentResult.failure("No se pudo determinar executionId para compensación");
            }
            return performCompensation(executionId);
        }

        return performCompensation(task.executionId());
    }

    private AgentResult performCompensation(String executionId) {
        List<Path> filesToDelete = createdFilesByExecution.remove(executionId);

        if (filesToDelete == null || filesToDelete.isEmpty()) {
            LOG.info("No files to compensate for execution: " + executionId);
            return AgentResult.success("Sin archivos para compensar");
        }

        List<Path> reversed = new ArrayList<>(filesToDelete);
        Collections.reverse(reversed);

        StringBuilder report = new StringBuilder();
        int deletedCount = 0;
        int failedCount = 0;

        for (Path file : reversed) {
            logAction("Eliminando: " + file.getFileName());

            try {
                if (Files.deleteIfExists(file)) {
                    deletedCount++;
                    report.append("[DELETE] ").append(file.getFileName()).append("\n");
                    LOG.info("Compensacion: eliminado " + file);
                } else {
                    report.append("[WARN] ").append(file.getFileName()).append(" (no existia)\n");
                }
            } catch (IOException e) {
                failedCount++;
                report.append("[ERROR] ").append(file.getFileName()).append(": ").append(e.getMessage()).append("\n");
                LOG.log(Level.WARNING, "Error deleting file during compensation: " + file, e);
            }
        }

        String summary = String.format("Compensación completada: %d archivos eliminados, %d errores\n%s",
            deletedCount, failedCount, report);

        if (failedCount > 0) {
            return AgentResult.failure(summary);
        }

        return AgentResult.success(summary);
    }

    private void registerCreatedFile(String executionId, Path file) {
        List<Path> files = createdFilesByExecution.computeIfAbsent(executionId, k ->
            Collections.synchronizedList(new ArrayList<>())
        );
        files.add(file);
        LOG.fine("Registered file for compensation: " + file);
    }

    public List<Path> getCreatedFiles(String executionId) {
        List<Path> files = createdFilesByExecution.get(executionId);
        return files != null ? new ArrayList<>(files) : List.of();
    }

    private TaskPayload extractTaskPayload(SovereignEnvelope<?> envelope) {
        Object payload = envelope.payload();
        if (payload instanceof TaskPayload tp) {
            return tp;
        }
        if (payload instanceof Map<?, ?> map) {
            try {
                return new TaskPayload(
                    (String) map.get("executionId"),
                    (String) map.get("missionId"),
                    (String) map.get("stepId"),
                    (String) map.get("systemPromptOverride"),
                    (String) map.get("contextJson")
                );
            } catch (Exception e) {
                LOG.warning("Could not extract TaskPayload from Map: " + e.getMessage());
            }
        }
        return null;
    }

    private String extractExecutionIdFromCompensation(SovereignEnvelope<?> envelope) {
        Object payload = envelope.payload();
        if (payload instanceof Map<?, ?> map) {
            Object execId = map.get("executionId");
            if (execId instanceof String s) {
                return s;
            }
        }
        return envelope.correlationId();
    }

    private String getDefaultSystemPrompt() {
        return """
            Eres el Constructor (Builder) de un equipo de agentes de IA.
            Tu trabajo es escribir código limpio, funcional y mantenible.

            REGLAS:
            1. Sigue las especificaciones del blueprint exactamente
            2. Incluye manejo de errores apropiado
            3. Escribe código completo (no fragmentos)
            4. Usa el formato >>>FILE: para múltiples archivos

            FORMATO DE SALIDA (MULTI-ARCHIVO):
            >>>FILE: ruta/del/archivo.java
            <contenido del código>

            >>>FILE: otra/ruta/archivo.java
            <contenido del código>
            """;
    }

    private String buildUserPrompt(String context) {
        return """
            CONTEXTO DE LA TAREA:
            %s

            Genera el código necesario siguiendo el formato especificado.
            """.formatted(context != null ? context : "Sin contexto adicional");
    }

    private Map<String, String> extractMultipleFiles(String llmResponse) {
        Map<String, String> files = new java.util.LinkedHashMap<>();

        if (llmResponse == null || llmResponse.isBlank()) {
            return files;
        }

        Pattern pattern = Pattern.compile(
            ">>>FILE:\\s*([^\\n]+)\\n([\\s\\S]*?)(?=>>>FILE:|$)",
            Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(llmResponse);
        while (matcher.find()) {
            String filename = matcher.group(1).trim();
            String content = matcher.group(2).trim();

            content = extractCode(content);

            if (!filename.isBlank() && !content.isBlank()) {
                files.put(filename, content);
            }
        }

        return files;
    }

    private String extractCode(String content) {
        if (content == null) return "";

        int codeBlockStart = content.indexOf("```");
        if (codeBlockStart >= 0) {
            int lineEnd = content.indexOf('\n', codeBlockStart);
            int codeStart = lineEnd >= 0 ? lineEnd + 1 : codeBlockStart + 3;
            int codeEnd = content.indexOf("```", codeStart);
            if (codeEnd > codeStart) {
                return content.substring(codeStart, codeEnd).trim();
            }
        }

        return content.trim();
    }

    private String inferFilename(String context) {
        if (context == null || context.isBlank()) {
            return "output.txt";
        }

        String lower = context.toLowerCase();

        if (lower.contains("public class ") || lower.contains("package ")) {
            return "Main.java";
        }
        if (lower.contains("def ") && lower.contains(":")) {
            return "script.py";
        }
        if (lower.contains("function") || lower.contains("const ")) {
            return "script.js";
        }
        if (lower.contains("<html>")) {
            return "index.html";
        }
        if (lower.contains("create table")) {
            return "schema.sql";
        }

        return "output.txt";
    }

    public int getPendingCompensationCount() {
        return createdFilesByExecution.size();
    }

    public int getTotalRegisteredFiles() {
        return createdFilesByExecution.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    private boolean isWeakModel() {
        String model = System.getenv("FARARONI_RABBIT_MODEL");
        if (model != null) {
            String lower = model.toLowerCase();
            if (lower.contains("1.5b") || lower.contains("0.5b") || lower.contains("1b")) {
                LOG.info("Modelo debil detectado: " + model);
                return true;
            }
            if (lower.contains("7b") || lower.contains("8b") || lower.contains("14b") ||
                lower.contains("30b") || lower.contains("32b") || lower.contains("70b") ||
                lower.contains("480b")) {
                return false;
            }
        }

        String sysProp = System.getProperty("fararoni.rabbit.model");
        if (sysProp != null && sysProp.toLowerCase().contains("1.5b")) {
            return true;
        }

        return false;
    }
}
