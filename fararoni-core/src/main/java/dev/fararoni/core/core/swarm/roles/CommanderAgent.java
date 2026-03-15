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

import dev.fararoni.core.core.memory.BlackBoxRecorder;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.swarm.MissionConfig;
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;
import dev.fararoni.core.core.util.CodeSanitizer;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class CommanderAgent extends SwarmAgent {
    private static final Logger LOG = Logger.getLogger(CommanderAgent.class.getName());

    private static final Persona COMMANDER_PERSONA = Persona.builder("COMMANDER")
        .name("Commander")
        .description("""
            Eres el Comandante de la Colmena. Tu trabajo es:
            1. Entender las necesidades del usuario
            2. Traducir requisitos a especificaciones claras
            3. Coordinar el equipo de desarrollo
            4. Asegurar la entrega de valor""")
        .expertise("requirements", "product-management", "stakeholder-communication")
        .allowedTools("fs_read", "fs_write", "doc_write", "search")
        .style(Persona.CommunicationStyle.DETAILED)
        .priorityCritics(Critic.CriticCategory.QUALITY)
        .build();

    private int deliveriesCount = 0;

    private final BlackBoxRecorder recorder;

    public CommanderAgent() {
        super("COMMANDER", COMMANDER_PERSONA);
        this.recorder = new BlackBoxRecorder();
    }

    @Override
    protected void processMessage(SwarmMessage msg) {
        switch (msg.type()) {
            case SwarmMessage.TYPE_USER_REQUEST -> handleUserRequest(msg);
            case SwarmMessage.TYPE_APPROVAL -> handleApproval(msg);
            case SwarmMessage.TYPE_FINAL_DELIVERY -> handleFinalDelivery(msg);
            case SwarmMessage.TYPE_FIX_APPLIED -> handleFixApplied(msg);
            case SwarmMessage.TYPE_PROJECT_REJECTED -> handleProjectRejected(msg);
            case SwarmMessage.TYPE_BUG_REPORT -> handleBugReport(msg);
            case SwarmMessage.TYPE_ERROR -> handleError(msg);
            default -> LOG.fine(() -> "[COMMANDER] Mensaje ignorado: " + msg.type());
        }
    }

    private void handleUserRequest(SwarmMessage msg) {
        System.out.println("[DEBUG-COMMANDER] ===== RECIBIDA SOLICITUD DEL USUARIO =====");
        System.out.println("[DEBUG-COMMANDER] Contenido: " + msg.content().substring(0, Math.min(100, msg.content().length())));
        LOG.info(() -> "[COMMANDER] Recibida solicitud del usuario");

        String request = msg.content();

        TaskType taskType = detectTaskType(request);
        System.out.println("[DEBUG-COMMANDER] TaskType detectado: " + taskType);

        if (taskType == TaskType.QUERY) {
            handleQueryTask(request);
            return;
        } else if (taskType == TaskType.SURGICAL) {
            handleSurgicalTask(request);
            return;
        }

        LOG.info(() -> "[COMMANDER] Evaluando nivel de amenaza y complejidad...");
        System.out.println("[DEBUG-COMMANDER] Llamando think() para riskAssessment...");

        String riskAssessment = think("""
            Analiza la solicitud del usuario: "%s"

            Determina el NIVEL DE COMPLEJIDAD Y RIESGO:
            - LOW: Tareas simples, scripts de un archivo, dudas, correcciones menores, "Hola Mundo".
            - MEDIUM: Nuevas funcionalidades, refactorización, lógica de negocio moderada, múltiples archivos.
            - HIGH: Arquitectura completa, sistemas de pagos, autenticación, bases de datos, despliegues nube.

            Responde estrictamente con una palabra: LOW, MEDIUM o HIGH.
            """.formatted(request));

        System.out.println("[DEBUG-COMMANDER] riskAssessment recibido: " + riskAssessment.substring(0, Math.min(50, riskAssessment.length())));

        MissionConfig missionConfig = MissionConfig.fromAssessment(riskAssessment);
        System.out.println("[DEBUG-COMMANDER] MissionConfig: " + missionConfig.toSimpleFormat());
        LOG.info(() -> "[COMMANDER] " + missionConfig.describe());

        LOG.fine(() -> "[COMMANDER] DEFCON config: " + missionConfig.toSimpleFormat());

        String prompt = """
            Analiza la siguiente solicitud del usuario y extrae:
            1. Objetivo principal
            2. Requisitos funcionales
            3. Criterios de aceptación
            4. Prioridad y urgencia

            Solicitud:
            %s

            Formatea como especificación técnica para el equipo de desarrollo.
            """.formatted(request);

        System.out.println("[DEBUG-COMMANDER] Llamando think() para requirements...");
        String requirements = think(prompt);
        System.out.println("[DEBUG-COMMANDER] requirements recibido, length=" + requirements.length());

        boolean intelAvailable = getBus().isRegistered("INTEL");
        System.out.println("[DEBUG-COMMANDER] enableAnalyst=" + missionConfig.enableAnalyst() + ", intelAvailable=" + intelAvailable);

        String originalFilename = extractFilename(request);
        if (originalFilename != null) {
            System.out.println("[DEBUG-COMMANDER] Filename EXPLÍCITO extraído: " + originalFilename);
        } else {
            System.out.println("[DEBUG-COMMANDER] Sin filename explícito - BUILDER usará inferencia");
        }

        if (missionConfig.enableAnalyst() && intelAvailable) {
            LOG.info(() -> "[COMMANDER] Requisitos analizados. Enviando a INTEL para refinamiento...");
            System.out.println("[DEBUG-COMMANDER] Enviando a INTEL...");

            SwarmMessage reqMsg = SwarmMessage.builder()
                .from(agentId)
                .to("INTEL")
                .type(SwarmMessage.TYPE_REQUIREMENTS)
                .content(requirements)
                .metadata("filename", originalFilename)
                .metadata("original_request", request)
                .build();
            getBus().send(reqMsg);
            System.out.println("[DEBUG-COMMANDER] Mensaje enviado a INTEL con filename=" + originalFilename);
        } else {
            LOG.info(() -> "[COMMANDER] Modo FAST: Saltando INTEL, enviando directo a BLUEPRINT...");
            System.out.println("[DEBUG-COMMANDER] Enviando a BLUEPRINT...");

            SwarmMessage reqMsg = SwarmMessage.builder()
                .from(agentId)
                .to("BLUEPRINT")
                .type(SwarmMessage.TYPE_REQUIREMENTS)
                .content(requirements)
                .metadata("filename", originalFilename)
                .metadata("original_request", request)
                .build();
            getBus().send(reqMsg);
            System.out.println("[DEBUG-COMMANDER] Mensaje enviado a BLUEPRINT con filename=" + originalFilename);
        }
    }

    private void handleApproval(SwarmMessage msg) {
        LOG.info(() -> "[COMMANDER] Recibida aprobación de SENTINEL");

        deliveriesCount++;

        String prompt = """
            El equipo ha completado el trabajo. Prepara un resumen ejecutivo:

            Resultado del equipo:
            %s

            Incluye:
            1. Lo que se completó
            2. Cualquier limitación o nota
            3. Próximos pasos sugeridos
            """.formatted(msg.content());

        String summary = think(prompt);

        LOG.info(() -> "[COMMANDER] Entregando resultado final al usuario");
        sendTo("USER", SwarmMessage.TYPE_FINAL_DELIVERY, summary);

        terminateMission("Misión completada con aprobación SENTINEL");
    }

    private void handleBugReport(SwarmMessage msg) {
        LOG.warning(() -> "[COMMANDER] Recibido reporte de bugs");

        String prompt = """
            El equipo de SENTINEL encontró problemas. Evalúa la situación:

            Reporte:
            %s

            Determina:
            1. Severidad del problema
            2. Impacto en el cronograma
            3. Recomendación de acción
            """.formatted(msg.content());

        String evaluation = think(prompt);

        sendTo("BLUEPRINT", "ISSUE_ESCALATION", evaluation);
    }

    private void handleFinalDelivery(SwarmMessage msg) {
        LOG.info(() -> "[COMMANDER] Recibida entrega final del OPERATOR");

        deliveriesCount++;

        String prompt = """
            El equipo ha completado y verificado el despliegue. Prepara un resumen ejecutivo:

            Resultado de verificación OPERATOR:
            %s

            Incluye:
            1. Lo que se completó y desplegó
            2. Verificación de despliegue exitosa (incluir Hash de Integridad SHA-256 del OPERATOR)
            3. Estado final: ARCHIVADO
            4. Próximos pasos sugeridos
            """.formatted(msg.content());

        String summary = think(prompt);

        LOG.info(() -> "[COMMANDER] Entregando resultado final al usuario");
        sendTo("USER", SwarmMessage.TYPE_FINAL_DELIVERY, summary);

        terminateMission("Misión ARCHIVADA - Verificación OPERATOR exitosa");
    }

    private void handleFixApplied(SwarmMessage msg) {
        LOG.warning(() -> "[COMMANDER] OPERATOR aplicó autocuración");

        deliveriesCount++;

        String prompt = """
            El OPERATOR detectó y corrigió un problema automáticamente. Prepara un informe:

            Reporte de autocuración:
            %s

            Incluye:
            1. Problema detectado y corregido
            2. Estado actual del despliegue
            3. Recomendaciones para evitar el problema
            """.formatted(msg.content());

        String report = think(prompt);

        LOG.info(() -> "[COMMANDER] Entregando resultado con nota de autocuración");
        sendTo("USER", SwarmMessage.TYPE_FINAL_DELIVERY, report);

        terminateMission("Misión completada - Autocuración OPERATOR aplicada");
    }

    private void handleProjectRejected(SwarmMessage msg) {
        LOG.severe(() -> "[COMMANDER] Proyecto RECHAZADO por el STRATEGIST");

        String prompt = """
            El STRATEGIST ha rechazado el proyecto. Explica la situación al usuario:

            Razón del rechazo:
            %s

            Incluye:
            1. Por qué no es viable el proyecto
            2. Qué cambios serían necesarios
            3. Alternativas posibles
            """.formatted(msg.content());

        String explanation = think(prompt);

        sendTo("USER", SwarmMessage.TYPE_ERROR, explanation);

        terminateMission("Misión terminada - Proyecto rechazado por STRATEGIST");
    }

    private void handleError(SwarmMessage msg) {
        LOG.severe(() -> "[COMMANDER] Error recibido: " + msg.content());
        sendTo("USER", SwarmMessage.TYPE_ERROR,
            "El equipo encontró un problema: " + msg.content());
    }

    @Override
    protected void onStartup() {
        LOG.info(() -> "[COMMANDER] Commander iniciado y listo para recibir solicitudes");
    }

    @Override
    protected void onShutdown() {
        LOG.info(() -> String.format("[COMMANDER] Shutdown. Entregas realizadas: %d", deliveriesCount));
    }

    private void terminateMission(String reason) {
        LOG.info(() -> String.format("[COMMANDER] DISPARANDO BENGALA DE TERMINACIÓN: %s", reason));

        getBus().broadcast(agentId, "MISSION_COMPLETE", reason);

        stop();
    }

    private enum TaskType {
        QUERY,
        SURGICAL,
        GENERATION
    }

    private TaskType detectTaskType(String request) {
        String lower = request.toLowerCase();

        if ((lower.contains("lee") || lower.contains("read") || lower.contains("leer")) &&
            (lower.contains("dime") || lower.contains("cual") || lower.contains("tell me") ||
             lower.contains("what") || lower.contains("responde"))) {
            return TaskType.QUERY;
        }

        if ((lower.contains("arregl") || lower.contains("correg") || lower.contains("fix") ||
             lower.contains("repair") || lower.contains("error de sintaxis") || lower.contains("syntax error")) &&
            (lower.contains(".py") || lower.contains(".java") || lower.contains(".js") ||
             lower.contains(".ts") || lower.contains("archivo") || lower.contains("file"))) {
            return TaskType.SURGICAL;
        }

        return TaskType.GENERATION;
    }

    private void handleQueryTask(String request) {
        LOG.info(() -> "[COMMANDER] Modo QUERY: Leyendo archivo para responder pregunta...");

        String filename = extractFilename(request);
        System.out.println("[DEBUG-COMMANDER] Archivo a leer: " + filename);

        if (filename == null) {
            sendTo("USER", SwarmMessage.TYPE_ERROR, "No se pudo identificar qué archivo leer.");
            terminateMission("Query sin archivo identificado");
            return;
        }

        String content = executeTool("fs_read", filename);
        System.out.println("[DEBUG-COMMANDER] Contenido leído: " + content.substring(0, Math.min(100, content.length())));

        if (content.startsWith("ERROR")) {
            sendTo("USER", SwarmMessage.TYPE_ERROR, "No se pudo leer el archivo: " + content);
            terminateMission("Query con error de lectura");
            return;
        }

        String prompt = """
            El usuario preguntó: "%s"

            Contenido del archivo leído:
            ---
            %s
            ---

            Responde SOLAMENTE con la información solicitada.
            No generes código. No expliques. Solo responde la pregunta.
            """.formatted(request, content);

        String answer = think(prompt);
        System.out.println("[DEBUG-COMMANDER] Respuesta: " + answer);

        sendTo("USER", SwarmMessage.TYPE_FINAL_DELIVERY, answer);
        terminateMission("Query completada - Respuesta entregada");
    }

    private void handleSurgicalTask(String request) {
        LOG.info(() -> "[COMMANDER] Modo SURGICAL: Arreglando archivo existente...");

        String filename = extractFilename(request);
        System.out.println("[DEBUG-COMMANDER] Archivo a reparar: " + filename);

        if (filename == null) {
            sendTo("USER", SwarmMessage.TYPE_ERROR, "No se pudo identificar qué archivo arreglar.");
            terminateMission("Surgical sin archivo identificado");
            return;
        }

        String originalContent = executeTool("fs_read", filename);
        System.out.println("[DEBUG-COMMANDER] Contenido original length: " + originalContent.length());

        if (originalContent.startsWith("ERROR")) {
            sendTo("USER", SwarmMessage.TYPE_ERROR, "No se pudo leer el archivo: " + originalContent);
            terminateMission("Surgical con error de lectura");
            return;
        }

        String prompt = """
            El archivo "%s" tiene errores. Código actual:
            ---
            %s
            ---

            IMPORTANTE: Genera SOLO el código corregido.
            - Sin explicaciones
            - Sin bloques markdown (no uses ```)
            - Código listo para guardar directamente en el archivo
            """.formatted(filename, originalContent);

        String fixedCode = think(prompt);
        System.out.println("[DEBUG-COMMANDER] Código corregido length: " + fixedCode.length());

        int maxRetries = 3;
        boolean missionSuccess = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            System.out.println("[COMMANDER] Aplicando parche (Intento " + attempt + "/" + maxRetries + ")...");

            String cleanCode = CodeSanitizer.sanitize(fixedCode, detectLanguageFromFilename(filename));
            System.out.println("[DEBUG-COMMANDER] cleanCode length (post-sanitize): " + cleanCode.length());

            if (cleanCode.isBlank()) {
                System.out.println("[COMMANDER] Código vacío detectado, reintentando...");
                if (attempt < maxRetries) {
                    fixedCode = think("El código anterior fue vacío. Genera el código corregido para " + filename + " basándote en:\n" + originalContent);
                    continue;
                }
                break;
            }

            String result = executeTool("fs_write", filename, cleanCode);
            System.out.println("[DEBUG-COMMANDER] fs_write result: " + result);

            if (result.startsWith("SUCCESS") || result.contains("[OK]")) {
                sendTo("USER", SwarmMessage.TYPE_FINAL_DELIVERY,
                    "Archivo " + filename + " reparado y verificado sintácticamente.");

                String language = detectLanguageFromFilename(filename);
                recorder.recordTrainingSample(
                    "Arregla el archivo " + filename + " que tiene errores: " + originalContent,
                    cleanCode,
                    language,
                    "fix"
                );
                recorder.generateRegressionTest(filename, originalContent, "SUCCESS");
                LOG.info(() -> "[COMMANDER] Datos de entrenamiento y test de regresión grabados.");

                missionSuccess = true;
                break;
            }

            if (result.contains("[ERROR]") || result.contains("RECHAZADO") || result.contains("sintaxis")) {
                if (attempt == maxRetries) break;

                String feedbackPrompt = String.format("""
                    ALERTA CRÍTICA: Tu código fue rechazado por el Sistema de Seguridad.

                    CÓDIGO ORIGINAL CON ERROR:
                    ---
                    %s
                    ---

                    REPORTE DE ERROR DEL VALIDADOR:
                    %s

                    ORDEN: Analiza el error anterior, corrige la sintaxis y genera SOLO el código corregido completo.
                    - Sin explicaciones
                    - Sin bloques markdown
                    - Código listo para guardar directamente
                    """, originalContent, result);

                System.out.println("[COMMANDER] Self-Correction: Reintentando con feedback del error...");

                fixedCode = think(feedbackPrompt);
                System.out.println("[DEBUG-COMMANDER] Código re-corregido length: " + fixedCode.length());
            } else {
                sendTo("USER", SwarmMessage.TYPE_ERROR,
                    "Abortando: Error de infraestructura no recuperable: " + result);
                terminateMission("Surgical abortada - Error de I/O");
                return;
            }
        }

        if (missionSuccess) {
            terminateMission("Surgical completada con éxito.");
        } else {
            sendTo("USER", SwarmMessage.TYPE_ERROR,
                "MISIÓN FALLIDA: El agente no pudo generar código válido tras " + maxRetries + " intentos.");
            terminateMission("Surgical fallida - Código inválido tras " + maxRetries + " intentos");
        }
    }

    private String extractFilename(String request) {
        String[] patterns = {
            "([\\w\\-]+\\.py)",
            "([\\w\\-]+\\.java)",
            "([\\w\\-]+\\.js)",
            "([\\w\\-]+\\.ts)",
            "([\\w\\-]+\\.txt)",
            "([\\w\\-]+\\.json)",
            "([\\w\\-]+\\.xml)",
            "([\\w\\-]+\\.md)"
        };

        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(request);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private String detectLanguageFromFilename(String filename) {
        if (filename == null) return "java";
        String lower = filename.toLowerCase();

        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".rb")) return "ruby";
        if (lower.endsWith(".php")) return "php";

        return "java";
    }
}
