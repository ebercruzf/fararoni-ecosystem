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

import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;
import dev.fararoni.core.core.swarm.context.SwarmContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class BuilderAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(BuilderAgent.class.getName());

    private static final Persona BUILDER_PERSONA = Persona.builder("BUILDER")
        .name("Builder")
        .description("""
            Eres el Constructor de la Colmena. Tu trabajo es:
            1. Escribir código limpio y mantenible
            2. Seguir las especificaciones del blueprint
            3. Incluir manejo de errores apropiado
            4. Escribir tests cuando sea necesario""")
        .expertise("coding", "java", "testing", "debugging")
        .allowedTools("fs_read", "fs_write", "fs_patch", "shell_execute", "git", "test_run")
        .style(Persona.CommunicationStyle.BALANCED)
        .priorityCritics(Critic.CriticCategory.CODE, Critic.CriticCategory.QUALITY)
        .build();

    private int codeVersions = 0;
    private int bugsFixes = 0;

    public BuilderAgent() {
        super("BUILDER", BUILDER_PERSONA);
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        System.out.println("[DEBUG-BUILDER] ===== MENSAJE RECIBIDO: " + msg.type() + " =====");
        switch (msg.type()) {
            case SwarmMessage.TYPE_BLUEPRINT -> handleBlueprint(msg);
            case SwarmMessage.TYPE_BUG_REPORT -> handleBugReport(msg);
            case SwarmMessage.TYPE_CODE_APPROVED -> handleCodeApproved(msg);
            case SwarmMessage.TYPE_ERROR -> handleError(msg);
            default -> LOG.fine(() -> "[BUILDER] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleBlueprint(SwarmMessage msg) {
        System.out.println("[DEBUG-BUILDER] handleBlueprint iniciado");
        LOG.info(() -> "[BUILDER] Recibido blueprint. Analizando entregables...");

        String blueprint = msg.content();

        Map<String, String> detectedFiles = extractMultipleFiles(blueprint);

        detectedFiles.entrySet().removeIf(entry -> isBlacklistedFilename(entry.getKey()));

        if (detectedFiles.size() >= 2) {
            System.out.println("[DEBUG-BUILDER] BATCH MODE activado. Archivos: " + detectedFiles.keySet());
            handleBatchMode(detectedFiles);
        } else {
            System.out.println("[DEBUG-BUILDER] ATOMIC MODE (flujo seguro)");
            handleAtomicMode(msg, blueprint);
        }
    }

    private void handleAtomicMode(SwarmMessage msg, String blueprint) {
        String contextHints = msg.getMetadata("context_hints");
        if (contextHints == null) {
            contextHints = msg.getMetadata("filename");
        }

        System.out.println("[DEBUG-BUILDER] Context hints recibido: " + contextHints);

        String prompt = """
            TIENES UN BLUEPRINT TÉCNICO:
            %s

            PISTAS DE CONTEXTO DEL USUARIO: %s

            TU TAREA:
            Escribir el código completo y funcional para CADA archivo definido en el Blueprint.

            PROTOCOLO DE SALIDA (MULTI-ARCHIVO):
            Debes entregar el contenido de los archivos usando este formato separador EXACTO:

            >>>FILE: <ruta_del_archivo>
            <contenido_del_codigo>

            REGLAS:
            1. Si el Blueprint pide múltiples archivos, genera un bloque `>>>FILE:` para cada uno.
            2. El código debe estar completo (imports, clases, métodos).
            3. No incluyas markdown (```java) dentro del bloque, solo el código puro.
            4. RESPETA las rutas exactas que indica el Blueprint.
            5. Si solo hay un archivo, aún así usa el formato >>>FILE:.

            EJEMPLO:
            >>>FILE: src/models/User.java
            package com.example.models;

            public class User {
                private String name;
            }

            >>>FILE: src/services/UserService.java
            package com.example.services;

            public class UserService {
            }
            """.formatted(blueprint, (contextHints != null ? contextHints : "Ninguno"));

        String code = think(prompt);
        codeVersions++;

        boolean sentinelAvailable = getBus().isRegistered("SENTINEL");

        if (sentinelAvailable) {
            LOG.info(() -> String.format("[BUILDER] Código v%d generado (protocolo >>>FILE:). Enviando a SENTINEL...", codeVersions));

            SwarmMessage draftMsg = SwarmMessage.builder()
                .from(agentId)
                .to("SENTINEL")
                .type(SwarmMessage.TYPE_CODE_DRAFT)
                .content(code)
                .metadata("context_hints", contextHints)
                .metadata("version", codeVersions)
                .metadata("multi_file_protocol", true)
                .build();
            getBus().send(draftMsg);
        } else {
            LOG.info(() -> String.format("[BUILDER] Código v%d generado. FastMode: Escribiendo archivos...", codeVersions));

            String cleanCode = extractCode(code);
            System.out.println("[DEBUG-BUILDER] WORKSPACE: " + SwarmContext.workspaceOrDefault());
            System.out.println("[DEBUG-BUILDER] cleanCode length: " + cleanCode.length());

            String result = executeTool("fs_write", "multi-file-output", cleanCode);
            System.out.println("[DEBUG-BUILDER] fs_write result: " + result);

            SwarmMessage verifyMsg = SwarmMessage.builder()
                .from(agentId)
                .to("OPERATOR")
                .type(SwarmMessage.TYPE_VERIFY_DEPLOYMENT)
                .content(cleanCode)
                .metadata("context_hints", contextHints)
                .metadata("builder_write_result", result)
                .metadata("fast_mode", true)
                .metadata("multi_file_protocol", true)
                .build();
            getBus().send(verifyMsg);
        }
    }

    private void handleBatchMode(Map<String, String> filesToCreate) {
        LOG.info(() -> "[BUILDER] Procesando " + filesToCreate.size() + " archivos en modo batch...");

        StringBuilder batchReport = new StringBuilder();
        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<String, String> entry : filesToCreate.entrySet()) {
            String targetFile = entry.getKey();
            String content = extractCode(entry.getValue());

            if (content.isBlank()) {
                System.out.println("[DEBUG-BUILDER] Saltando archivo vacío: " + targetFile);
                batchReport.append("SKIP: ").append(targetFile).append(" (contenido vacío)\n");
                continue;
            }

            System.out.println("[DEBUG-BUILDER] Escribiendo: " + targetFile + " (" + content.length() + " chars)");
            String result = executeTool("fs_write", targetFile, content);

            if (result.startsWith("SUCCESS") || result.contains("creado")) {
                successCount++;
                batchReport.append("OK: ").append(targetFile).append("\n");
                LOG.info(() -> "[BUILDER] Archivo creado: " + targetFile);
            } else {
                failCount++;
                batchReport.append("FAIL: ").append(targetFile).append(" - ").append(result).append("\n");
                LOG.warning(() -> "[BUILDER] Error creando: " + targetFile);
            }

            codeVersions++;
        }

        String finalReport = String.format("BATCH REPORT: %d OK, %d FAIL\n%s",
            successCount, failCount, batchReport);

        SwarmMessage verifyMsg = SwarmMessage.builder()
            .from(agentId)
            .to("OPERATOR")
            .type(SwarmMessage.TYPE_VERIFY_DEPLOYMENT)
            .content(finalReport)
            .metadata("batch_mode", true)
            .metadata("files_created", successCount)
            .metadata("files_failed", failCount)
            .build();
        getBus().send(verifyMsg);
    }

    private Map<String, String> extractMultipleFiles(String blueprint) {
        Map<String, String> files = new LinkedHashMap<>();

        if (blueprint == null || blueprint.isBlank()) {
            return files;
        }

        String extPattern = "py|java|js|ts|html|css|sql|sh|xml|json|yaml|yml|txt|md|go|rs|rb|php";

        Pattern patternA = Pattern.compile(
            "(?:File|Archivo|Nombre)[:\\s]+[`'\"]?([a-zA-Z_][a-zA-Z0-9_-]*\\.(?:" + extPattern + "))[`'\"]?" +
            ".*?```(?:\\w+)?\\n([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
        );

        Pattern patternB = Pattern.compile(
            "###+\\s*[`'\"]?([a-zA-Z_][a-zA-Z0-9_-]*\\.(?:" + extPattern + "))[`'\"]?" +
            ".*?```(?:\\w+)?\\n([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
        );

        Pattern patternC = Pattern.compile(
            "\\*\\*([a-zA-Z_][a-zA-Z0-9_-]*\\.(?:" + extPattern + "))\\*\\*" +
            ".*?```(?:\\w+)?\\n([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
        );

        for (Pattern p : new Pattern[]{patternA, patternB, patternC}) {
            Matcher m = p.matcher(blueprint);
            while (m.find()) {
                String filename = m.group(1);
                String content = m.group(2);
                if (filename != null && content != null && !content.isBlank()) {
                    files.putIfAbsent(filename, content);
                }
            }
        }

        System.out.println("[DEBUG-BUILDER] extractMultipleFiles encontró: " + files.size() + " archivos");
        return files;
    }

    private String inferFilename(String blueprint) {
        if (blueprint == null || blueprint.isBlank()) {
            return "output.txt";
        }

        String extensionGroup = "(py|java|js|ts|go|rs|rb|php|c|cpp|h|hpp|cs|swift|kt|" +
            "html|css|scss|less|sass|sql|json|xml|yaml|yml|toml|ini|conf|properties|" +
            "sh|bash|zsh|bat|ps1|cmd|md|txt|rst|adoc)";

        java.util.regex.Pattern contextPattern = java.util.regex.Pattern.compile(
            "(?:llamado|named|archivo|file|crear|create)\\s+['\"]?([a-zA-Z_][a-zA-Z0-9_-]*\\." + extensionGroup + ")['\"]?",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher contextMatcher = contextPattern.matcher(blueprint);
        if (contextMatcher.find()) {
            String found = contextMatcher.group(1);
            if (!isBlacklistedFilename(found)) {
                System.out.println("[DEBUG-BUILDER] Filename por Contexto: " + found);
                return found;
            }
        }

        java.util.regex.Pattern filePattern = java.util.regex.Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_-]*\\." + extensionGroup + ")",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = filePattern.matcher(blueprint);

        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!isBlacklistedFilename(candidate)) {
                System.out.println("[DEBUG-BUILDER] Filename por Inferencia Genérica: " + candidate);
                return candidate;
            }
        }

        String lower = blueprint.toLowerCase();

        if (lower.contains("public class ") || lower.contains("package ")) {
            return "Main.java";
        }

        if (lower.contains("def ") && lower.contains(":") || lower.contains("import ") && !lower.contains("import {")) {
            return "script.py";
        }

        if (lower.contains("<html>") || lower.contains("<!doctype")) {
            return "index.html";
        }

        if (lower.contains("function") || lower.contains("const ") || lower.contains("=>")) {
            return "script.js";
        }

        if (lower.contains("create table") || lower.contains("select ") && lower.contains(" from ")) {
            return "schema.sql";
        }

        if (lower.contains("#!/bin/")) {
            return "script.sh";
        }

        if (lower.contains("background-color:") || lower.contains("{") && lower.contains("margin:")) {
            return "styles.css";
        }

        return "output.txt";
    }

    private boolean isBlacklistedFilename(String name) {
        if (name == null) return true;
        String lower = name.toLowerCase();

        if (lower.equals("node.js") ||
            lower.equals("vue.js") ||
            lower.equals("react.js") ||
            lower.equals("angular.js") ||
            lower.equals("chart.js") ||
            lower.equals("three.js") ||
            lower.equals("next.js") ||
            lower.equals("nuxt.js") ||
            lower.equals("express.js") ||
            lower.equals("electron.js") ||
            lower.equals("ember.js") ||
            lower.equals("backbone.js") ||
            lower.equals("d3.js") ||
            lower.equals("lodash.js") ||
            lower.equals("moment.js") ||
            lower.equals("axios.js") ||
            lower.startsWith("jquery")) {
            return true;
        }

        if (lower.equals("system.out") ||
            lower.equals("system.err") ||
            lower.equals("object.java")) {
            return true;
        }

        if (lower.equals("__init__.py") ||
            lower.equals("__main__.py") ||
            lower.equals("setup.py") ||
            lower.equals("conftest.py") ||
            lower.equals("requirements.txt")) {
            return true;
        }

        if (lower.equals("package.json") ||
            lower.equals("tsconfig.json") ||
            lower.equals("webpack.config.js") ||
            lower.equals("babel.config.js") ||
            lower.equals("pom.xml") ||
            lower.equals("build.gradle") ||
            lower.equals("makefile") ||
            lower.equals("dockerfile") ||
            lower.equals(".gitignore") ||
            lower.equals(".env")) {
            return true;
        }

        return false;
    }

    private void handleBugReport(SwarmMessage msg) {
        LOG.warning(() -> "[BUILDER] Recibido reporte de bugs. Corrigiendo...");

        String filename = msg.getMetadata("filename", "output.txt");

        String prompt = """
            El código anterior tiene errores. Corrígelos:

            Errores reportados:
            %s

            IMPORTANTE: El archivo de salida será: %s

            Proporciona SOLO el código corregido:
            1. Sin explicaciones adicionales
            2. Sin bloques markdown
            3. Código listo para ejecutar/compilar
            """.formatted(msg.content(), filename);

        String fixedCode = think(prompt);
        codeVersions++;
        bugsFixes++;

        boolean sentinelAvailable = getBus().isRegistered("SENTINEL");

        if (sentinelAvailable) {
            LOG.info(() -> String.format("[BUILDER] Código v%d corregido. Reenviando a SENTINEL...", codeVersions));

            SwarmMessage draftMsg = SwarmMessage.builder()
                .from(agentId)
                .to("SENTINEL")
                .type(SwarmMessage.TYPE_CODE_DRAFT)
                .content(fixedCode)
                .metadata("filename", filename)
                .metadata("version", codeVersions)
                .build();
            getBus().send(draftMsg);
        } else {
            LOG.info(() -> String.format("[BUILDER] Código v%d corregido. FastMode: Auto-aprobando y enviando a OPERATOR...", codeVersions));

            String cleanCode = extractCode(fixedCode);
            String result = executeTool("fs_write", filename, cleanCode);

            SwarmMessage verifyMsg = SwarmMessage.builder()
                .from(agentId)
                .to("OPERATOR")
                .type(SwarmMessage.TYPE_VERIFY_DEPLOYMENT)
                .content(cleanCode)
                .metadata("filename", filename)
                .metadata("builder_write_result", result)
                .metadata("fast_mode", true)
                .build();
            getBus().send(verifyMsg);
        }
    }

    private void handleCodeApproved(SwarmMessage msg) {
        LOG.info(() -> "[BUILDER] Código APROBADO por SENTINEL. Procediendo al despliegue...");

        String code = msg.content();

        String contextHints = msg.getMetadata("context_hints");
        if (contextHints == null) {
            contextHints = msg.getMetadata("filename");
        }

        String cleanCode = extractCode(code);

        String pathArg = (contextHints != null && !contextHints.isBlank())
            ? contextHints
            : "output.txt";

        String result = executeTool("fs_write", pathArg, cleanCode);

        final String finalHints = contextHints;

        if (result.startsWith("SUCCESS") || result.contains("creado") || result.contains("Completada")) {
            LOG.info(() -> "[BUILDER] Archivo(s) escrito(s). Solicitando verificación a OPERATOR...");

            SwarmMessage verifyMsg = SwarmMessage.builder()
                .from(agentId)
                .to("OPERATOR")
                .type(SwarmMessage.TYPE_VERIFY_DEPLOYMENT)
                .content(cleanCode)
                .metadata("context_hints", finalHints)
                .metadata("builder_write_result", result)
                .build();
            getBus().send(verifyMsg);
        } else {
            LOG.severe(() -> "[BUILDER] Error escribiendo archivo(s): " + result);

            SwarmMessage failMsg = SwarmMessage.builder()
                .from(agentId)
                .to("OPERATOR")
                .type(SwarmMessage.TYPE_VERIFY_DEPLOYMENT)
                .content(cleanCode)
                .metadata("context_hints", finalHints)
                .metadata("builder_write_result", result)
                .metadata("builder_write_failed", true)
                .build();
            getBus().send(failMsg);
        }
    }

    private String extractCode(String content) {
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

    private void handleError(SwarmMessage msg) {
        LOG.severe(() -> "[BUILDER] Error recibido: " + msg.content());
        sendTo("BLUEPRINT", SwarmMessage.TYPE_ERROR, "Error en construcción: " + msg.content());
    }

    @Override
    protected void onStartup() {
        LOG.info(() -> "[BUILDER] Builder iniciado y listo para codificar");
    }

    @Override
    protected void onShutdown() {
        LOG.info(() -> String.format("[BUILDER] Shutdown. Versiones: %d, Bugs corregidos: %d",
            codeVersions, bugsFixes));
    }
}
