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

import dev.fararoni.core.core.forensic.InvestigationState;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.skills.forensic.NetworkProbeSkill;
import dev.fararoni.core.core.skills.forensic.SandboxRunnerSkill;
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.context.SwarmContext;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class OperatorAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(OperatorAgent.class.getName());

    private static final Persona OPERATOR_PERSONA = Persona.builder("OPERATOR")
        .name("Operator")
        .description("""
            Eres el Operador de Campo de la Colmena, experto en verificación de sistemas.
            TU MISIÓN: Certificar que los entregables EXISTEN y FUNCIONAN en la realidad.

            REGLAS DE ORO (PROTOCOLO FORENSE):
            1. ESCEPTICISMO TOTAL: No confíes en que "está hecho". Verifica el disco.
            2. VERIFICACIÓN EMPÍRICA: Usa ls, cat, curl para confirmar.
            3. AUTOCURACIÓN: Si falta algo y puedes arreglarlo, hazlo.
            4. NUNCA APROBAR SIN EVIDENCIA: Si no ves el archivo, no existe.""")
        .expertise("devops", "linux", "debugging", "monitoring", "infrastructure")
        .allowedTools("fs_read", "fs_write", "fs_patch", "fs_exists", "shell_execute", "network_probe")
        .style(Persona.CommunicationStyle.TECHNICAL)
        .priorityCritics(Critic.CriticCategory.SECURITY, Critic.CriticCategory.QUALITY)
        .build();

    private final SandboxRunnerSkill sandbox = new SandboxRunnerSkill();
    private final NetworkProbeSkill networkProbe = new NetworkProbeSkill();

    private final List<String> evidenceLog = new ArrayList<>();
    private InvestigationState currentState = InvestigationState.IDLE;

    private int verificationsPerformed = 0;
    private int fixesApplied = 0;
    private int failures = 0;

    public OperatorAgent() {
        super("OPERATOR", OPERATOR_PERSONA);
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        switch (msg.type()) {
            case SwarmMessage.TYPE_VERIFY_DEPLOYMENT -> handleVerifyDeployment(msg);
            case SwarmMessage.TYPE_CODE_APPROVED -> handleVerifyDeployment(msg);
            case SwarmMessage.TYPE_ERROR -> handleError(msg);
            default -> LOG.fine(() -> "[OPERATOR] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleVerifyDeployment(SwarmMessage msg) {
        LOG.info(() -> "[OPERATOR] Iniciando verificación de despliegue...");
        verificationsPerformed++;
        currentState = InvestigationState.OBSERVING;
        evidenceLog.clear();

        String code = msg.content();
        String filename = msg.getMetadata("filename", "output.txt");
        Path workspace = SwarmContext.workspaceOrDefault();
        Path targetFile = workspace.resolve(filename);

        evidenceLog.add("TARGET: " + targetFile);
        evidenceLog.add("WORKSPACE: " + workspace);

        LOG.info(() -> "[OPERATOR] OBSERVE: Verificando existencia de " + filename);
        boolean fileExists = Files.exists(targetFile);
        evidenceLog.add("FILE_EXISTS: " + fileExists);

        if (fileExists) {
            try {
                String content = Files.readString(targetFile);
                long size = Files.size(targetFile);
                int lineCount = content.split("\n").length;
                evidenceLog.add("FILE_SIZE: " + size + " bytes");
                evidenceLog.add("FILE_LINES: " + lineCount);

                String integrityHash = calculateSHA256(content);
                evidenceLog.add("INTEGRITY_HASH: " + integrityHash);

                LOG.info(() -> "[OPERATOR] VERIFIED: Archivo existe y tiene contenido");
                currentState = InvestigationState.SOLVED;

                String verification = String.format("""
                    CERTIFICACIÓN DE DESPLIEGUE

                    Archivo: %s
                    Ubicación: %s
                    Tamaño: %d bytes
                    Líneas: %d
                    Hash de Integridad (SHA-256): %s

                    Estado: VERIFICADO Y ARCHIVADO
                    """, filename, targetFile, size, lineCount, integrityHash);

                sendTo("COMMANDER", SwarmMessage.TYPE_FINAL_DELIVERY, verification);
                return;
            } catch (Exception e) {
                evidenceLog.add("READ_ERROR: " + e.getMessage());
            }
        }

        LOG.warning(() -> "[OPERATOR] ORIENT: Archivo NO encontrado. Iniciando diagnóstico...");
        currentState = InvestigationState.ORIENTING;

        String diagnosis = think("""
            SITUACIÓN: El BUILDER dice que creó el archivo pero NO existe en disco.

            EVIDENCIA:
            %s

            CÓDIGO QUE DEBERÍA HABERSE ESCRITO:
            %s

            ¿Qué pudo haber fallado? Genera una hipótesis breve.
            """.formatted(String.join("\n", evidenceLog), truncate(code, 500)));

        evidenceLog.add("DIAGNOSIS: " + diagnosis);

        LOG.info(() -> "[OPERATOR] DECIDE: Intentando autocuración...");
        currentState = InvestigationState.DECIDING;

        LOG.info(() -> "[OPERATOR] ACT: Escribiendo archivo manualmente...");
        currentState = InvestigationState.ACTING;

        String writeResult = executeTool("fs_write", filename, extractCode(code));

        if (writeResult.startsWith("SUCCESS")) {
            fixesApplied++;
            currentState = InvestigationState.SOLVED;

            LOG.info(() -> "[OPERATOR] FIX_APPLIED: Archivo creado por autocuración");

            String fixHash = "N/A";
            try {
                Path fixedFile = SwarmContext.workspaceOrDefault().resolve(filename);
                if (Files.exists(fixedFile)) {
                    fixHash = calculateSHA256(Files.readString(fixedFile));
                }
            } catch (Exception ignored) {  }

            String fixReport = String.format("""
                CERTIFICACIÓN CON AUTOCURACIÓN

                Problema Detectado: Archivo no existía en disco
                Diagnóstico: %s
                Acción: Escritura manual ejecutada
                Resultado: %s

                Archivo: %s
                Hash de Integridad (SHA-256): %s
                Estado: VERIFICADO Y ARCHIVADO (con intervención OPERATOR)
                """, truncate(diagnosis, 200), writeResult, filename, fixHash);

            SwarmMessage fixMsg = SwarmMessage.builder()
                .from(agentId)
                .to("COMMANDER")
                .type("FIX_APPLIED")
                .content(fixReport)
                .metadata("filename", filename)
                .metadata("operator_intervention", true)
                .build();
            getBus().send(fixMsg);
        } else {
            failures++;
            currentState = InvestigationState.FAILED;

            LOG.severe(() -> "[OPERATOR] FAILED: No se pudo crear el archivo");

            String failureReport = String.format("""
                FALLO CRÍTICO DE DESPLIEGUE

                Archivo: %s
                Error de escritura: %s
                Diagnóstico: %s

                ACCIÓN REQUERIDA: Intervención manual necesaria.
                """, filename, writeResult, diagnosis);

            sendTo("BLUEPRINT", "OPERATOR_FAILURE", failureReport);
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
        LOG.severe(() -> "[OPERATOR] Error recibido: " + msg.content());
        evidenceLog.add("ERROR: " + msg.content());
        sendTo("BLUEPRINT", "OPERATOR_FAILURE", "Error en verificación: " + msg.content());
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private String calculateSHA256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            LOG.warning("[OPERATOR] Error calculando SHA-256: " + e.getMessage());
            return "ERROR-HASH";
        }
    }

    @Override
    protected void onStartup() {
        LOG.info(() -> "[OPERATOR] Operator iniciado y listo para verificar");
    }

    @Override
    protected void onShutdown() {
        LOG.info(() -> String.format(
            "[OPERATOR] Shutdown. Verificaciones: %d, Fixes: %d, Fallos: %d",
            verificationsPerformed, fixesApplied, failures));
    }
}
