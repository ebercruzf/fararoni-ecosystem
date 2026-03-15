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

import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.core.surgeon.LevenshteinUtils;
import dev.fararoni.core.core.index.KnowledgeBaseFactory;
import dev.fararoni.core.core.index.ProjectKnowledgeBase;
import dev.fararoni.core.core.index.ProjectKnowledgeBase.ContextProfile;
import dev.fararoni.core.core.index.SafeCrawler;
import dev.fararoni.core.core.index.CrawlResult;
import dev.fararoni.core.core.index.CrawlConfig;
import dev.fararoni.core.core.indexing.GitignoreFilter;
import dev.fararoni.core.core.services.CodeAnalysisService;
import dev.fararoni.core.core.sentinel.ProjectSentinel;
import dev.fararoni.core.core.memory.ContextHealer;
import dev.fararoni.core.core.topology.ProjectTopologyScanner;
import dev.fararoni.core.core.topology.ProjectTopology;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.clients.AgentClient.ToolCall;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.config.ServiceRegistry;
import dev.fararoni.core.model.Message;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class FaraCoreContextVault {
    private static final Logger LOG = Logger.getLogger(FaraCoreContextVault.class.getName());

    private final Path workingDirectory;
    private final WorkspaceManager workspaceManager;

    private final List<Message> conversationHistory = new ArrayList<>();
    static final int MAX_HISTORY_MESSAGES = 6;

    private final ContextHealer contextHealer = new ContextHealer();

    private ProjectKnowledgeBase memoryVault;
    private SafeCrawler crawlerEyes;
    private CompletableFuture<CrawlResult> crawlFuture;
    private ProjectSentinel projectSentinel;

    private String cachedReconData = null;
    private long reconCacheTimestamp = 0;
    private static final long RECON_CACHE_TTL = 30_000;

    private boolean enableCache = true;
    private boolean enablePersistence = true;

    private record ReconTarget(Path path, int score, String extension) {}

    public FaraCoreContextVault(Path workingDirectory, WorkspaceManager workspaceManager) {
        this.workingDirectory = workingDirectory;
        this.workspaceManager = workspaceManager;
    }

    public void initializeMemoryVault() {
        try {
            this.memoryVault = KnowledgeBaseFactory.create();
            if (!memoryVault.isAvailable()) {
                LOG.warning("[FARARONI] Memoria persistente no disponible, continuando sin cache de indexado");
                return;
            }
            String dbInfo = (memoryVault instanceof IndexStore idx) ? idx.getDbPath().toString() : memoryVault.getClass().getSimpleName();
            LOG.info(() -> "[FARARONI] Memoria persistente inicializada: " + dbInfo);

            CodeAnalysisService analysisService;
            try {
                analysisService = new CodeAnalysisService();
                LOG.fine("[FARARONI] CodeAnalysisService inicializado (parsers locales)");
            } catch (Exception e) {
                LOG.warning(() -> "[FARARONI] CodeAnalysisService fallo: " + e.getMessage() +
                    " - Continuando sin análisis semántico");
                analysisService = null;
            }

            GitignoreFilter gitFilter;
            try {
                gitFilter = GitignoreFilter.forProject(workingDirectory);
                LOG.fine("[FARARONI] GitignoreFilter inicializado para: " + workingDirectory);
            } catch (Exception e) {
                LOG.warning(() -> "[FARARONI] GitignoreFilter fallo: " + e.getMessage() +
                    " - Usando filtro por defecto");
                gitFilter = GitignoreFilter.withDefaults();
            }

            IndexStore idxForCrawler = (memoryVault instanceof IndexStore idx) ? idx : new IndexStore();
            this.crawlerEyes = new SafeCrawler(
                idxForCrawler,
                analysisService,
                gitFilter,
                CrawlConfig.DEFAULT
            );
            LOG.fine("[FARARONI] SafeCrawler ensamblado con dependencias verificadas");

            LOG.info("[FARARONI] Iniciando escaneo incremental del workspace...");
            this.crawlFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return crawlerEyes.crawl(workingDirectory);
                } catch (Exception e) {
                    LOG.warning(() -> "[FARARONI] Error en escaneo: " + e.getMessage());
                    return null;
                }
            });

            crawlFuture.thenAccept(result -> {
                if (result != null) {
                    LOG.info(() -> String.format(
                        "[FARARONI] Escaneo completado: %d archivos, %d skipped, %d fallidos en %dms",
                        result.filesProcessed(),
                        result.filesSkipped(),
                        result.filesFailed(),
                        result.duration().toMillis()));
                }
            });

            try {
                IndexStore idxForSentinel = (memoryVault instanceof IndexStore idx) ? idx : null;
                if (idxForSentinel == null) {
                    LOG.warning("[FARARONI] ProjectSentinel requiere IndexStore - vigilancia deshabilitada");
                } else {
                    this.projectSentinel = new ProjectSentinel(workingDirectory, idxForSentinel);
                    this.projectSentinel.start();
                    LOG.info("[FARARONI] Centinela de proyecto iniciado (Fog of War protection)");
                }
            } catch (Exception e) {
                LOG.warning(() -> "[FARARONI] ProjectSentinel fallo: " + e.getMessage() +
                    " - Continuando sin vigilancia en tiempo real");
            }
        } catch (Exception e) {
            LOG.warning(() -> "[FARARONI] [ERROR] Error crítico en inicialización de memoria: " + e.getMessage());
        }
    }

    public void addToHistory(Message message) {
        conversationHistory.add(message);
        while (conversationHistory.size() > MAX_HISTORY_MESSAGES) {
            conversationHistory.remove(0);
        }
    }

    public void removeLastUserMessage() {
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            if ("user".equals(conversationHistory.get(i).role())) {
                conversationHistory.remove(i);
                LOG.fine("[PHOENIX] Rollback: Eliminado mensaje de usuario del historial");
                return;
            }
        }
    }

    public List<Message> getConversationHistory() {
        return List.copyOf(conversationHistory);
    }

    List<Message> getConversationHistoryMutable() {
        return conversationHistory;
    }

    private static final String[] STRATEGIC_KEYWORDS = {
        "analiza", "contexto", "trata", "resumen", "explica", "describe", "overview",
        "arquitectura", "estructura", "investiga", "revisa", "diagnostica", "examina"
    };
    private static final String[] STRATEGIC_PHRASES = {
        "qué hace", "que hace", "de qué", "de que", "cómo funciona", "como funciona"
    };

    public ContextProfile detectContextProfile(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return getDefaultContextProfile();
        }
        String query = userQuery.toLowerCase().trim();

        boolean isStrategicAnalysis = false;
        for (String phrase : STRATEGIC_PHRASES) {
            if (query.contains(phrase)) {
                isStrategicAnalysis = true;
                break;
            }
        }
        if (!isStrategicAnalysis) {
            for (String word : query.split("\\s+")) {
                for (String keyword : STRATEGIC_KEYWORDS) {
                    if (word.length() >= 4 && LevenshteinUtils.calculateDistance(word, keyword) <= 2) {
                        LOG.info("Fuzzy match: '" + word + "' ≈ '" + keyword + "' (Levenshtein ≤ 2)");
                        isStrategicAnalysis = true;
                        break;
                    }
                }
                if (isStrategicAnalysis) break;
            }
        }

        if (isStrategicAnalysis) {
            LOG.info("Intencion detectada: STRATEGIC (analisis profundo)");
            return ContextProfile.STRATEGIC;
        }
        boolean isSimpleChat = query.matches(
            "(?i)^(hola|gracias|ok|bien|perfecto|listo|entendido|si|no|" +
            "buenos días|buenas tardes|buenas noches|bye|adios|chao)" +
            "(\\s+(como estas|qué tal|que tal|cómo vas|como vas|buenas|buen dia))?\\s*[.!?]*$"
        ) || query.length() < 15;
        if (isSimpleChat) {
            LOG.info("Intencion detectada: SKELETAL (chat simple)");
            return ContextProfile.SKELETAL;
        }
        LOG.info("Intencion detectada: TACTICAL (desarrollo estandar)");
        return ContextProfile.TACTICAL;
    }

    public ContextProfile getDefaultContextProfile() {
        String contextMode = System.getProperty("fararoni.context.mode", "TACTICAL");
        return switch (contextMode.toUpperCase()) {
            case "DEEP", "STRATEGIC" -> ContextProfile.STRATEGIC;
            case "SKELETAL" -> ContextProfile.SKELETAL;
            default -> ContextProfile.TACTICAL;
        };
    }

    public boolean detectExercismContext() {
        if (workingDirectory == null) {
            return false;
        }
        try {
            if (java.nio.file.Files.exists(workingDirectory.resolve(".exercism"))) {
                return true;
            }
            if (java.nio.file.Files.exists(workingDirectory.resolve("exercises"))) {
                return true;
            }
            if (java.nio.file.Files.exists(workingDirectory.resolve(".meta/config.json"))) {
                return true;
            }
        } catch (Exception e) {
            LOG.fine(() -> "[CONTEXT] Error detectando Exercism: " + e.getMessage());
        }
        return false;
    }

    public String getProjectContext() {
        return getProjectContext(getDefaultContextProfile());
    }

    public String getProjectContext(ContextProfile profile) {
        if (crawlFuture != null && !crawlFuture.isDone()) {
            System.out.println("[DEBUG] Sincronizando con escaneo de disco (max 5s)...");
            try {
                crawlFuture.get(5, TimeUnit.SECONDS);
                System.out.println("[DEBUG] Sincronizacion completada.");
            } catch (TimeoutException e) {
                System.out.println("[DEBUG] Timeout en escaneo. Usando datos parciales.");
            } catch (InterruptedException e) {
                System.out.println("[DEBUG] Escaneo interrumpido: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                System.out.println("[DEBUG] Error en escaneo: " + e.getMessage());
            }
        } else {
            System.out.println("[DEBUG] Escaneo ya finalizado o inactivo.");
        }
        StringBuilder ctx = new StringBuilder();
        System.out.println("[DEBUG] memoryVault=" + (memoryVault != null) + ", available=" + (memoryVault != null && memoryVault.isAvailable()));
        String reconData = performAgnosticRecon(workingDirectory);
        if (!reconData.isEmpty()) {
            ctx.append("\n===  REALITY ASSERTION: ESTE ES EL PROYECTO ACTUAL ===\n");
            ctx.append("El siguiente contenido es el 'README.md' y 'pom.xml' REALES del directorio donde estás.\n");
            ctx.append("NO NECESITAS BUSCARLOS. YA LOS TIENES AQUÍ. LÉELOS Y RESPONDE DIRECTAMENTE.\n");
            ctx.append("Si te preguntan 'de qué trata el proyecto', la respuesta está ABAJO.\n\n");
            ctx.append(reconData);
            System.out.println("[DEBUG-FASE36] reconData inyectado PRIMERO (" + reconData.length() + " chars)");
        } else {
            ctx.append("\n[WARN] Sin archivos de configuración detectados. Visión limitada.\n");
            System.out.println("[DEBUG-FASE36] reconData VACIO - alto riesgo de alucinación");
        }
        final int STRUCTURE_CAP = 4000;
        if (memoryVault != null && memoryVault.isAvailable()) {
            if (memoryVault instanceof IndexStore idx) {
                var stats = idx.getStats();
                System.out.println("[DEBUG] totalFiles=" + stats.totalFiles() + ", successFiles=" + stats.successFiles());
                if (stats.totalFiles() > 0) {
                    ctx.append("\n[MEMORIA] Archivos indexados: ").append(stats.totalFiles())
                       .append(" | Parseados: ").append(stats.successFiles()).append("\n");
                }
            }
            String highLevelMap = memoryVault.generateMap(profile);
            int originalLength = (highLevelMap != null) ? highLevelMap.length() : 0;
            System.out.println("[DEBUG] Context Profile=" + profile.name() + ", map length=" + originalLength);
            if (highLevelMap != null && !highLevelMap.isEmpty() && !highLevelMap.startsWith("(")) {
                if (highLevelMap.length() > STRUCTURE_CAP) {
                    highLevelMap = highLevelMap.substring(0, STRUCTURE_CAP) +
                        "\n... [ESTRUCTURA TRUNCADA - " + (originalLength - STRUCTURE_CAP) +
                        " chars omitidos. Usa 'ListFiles' para más detalle] ...\n";
                    System.out.println("[DEBUG-FASE36] Mapa truncado de " + originalLength + " a " + STRUCTURE_CAP + " chars");
                }
                ctx.append("\n=== REFERENCIA: ESTRUCTURA DEL PROYECTO (").append(profile.name()).append(" MODE) ===\n");
                ctx.append("Ruta Raíz: ").append(workingDirectory).append("\n");
                ctx.append(highLevelMap);
            }
            if (memoryVault instanceof IndexStore idxStore) {
                String javaFilesMap = idxStore.getJavaFilesMap(workingDirectory);
                System.out.println("[DEBUG] javaFilesMap length=" + (javaFilesMap != null ? javaFilesMap.length() : "null"));
                if (javaFilesMap != null && !javaFilesMap.isEmpty()) {
                    System.out.println("[DEBUG] javaFilesMap (primeros 200 chars): " + javaFilesMap.substring(0, Math.min(200, javaFilesMap.length())));
                    if (javaFilesMap.length() > STRUCTURE_CAP) {
                        javaFilesMap = javaFilesMap.substring(0, STRUCTURE_CAP) +
                            "\n... [LISTA TRUNCADA - Usa 'ListFiles' para más] ...\n";
                    }
                    ctx.append("\n[ARCHIVOS JAVA EXISTENTES - USAR ESTOS PAQUETES]\n");
                    ctx.append(javaFilesMap);
                    ctx.append("\n");
                }
            }
        } else {
            System.out.println("[DEBUG] memoryVault NO disponible");
            ctx.append("\n[WARN] Memoria persistente NO disponible. Contexto limitado.\n");
        }
        try {
            ProjectTopologyScanner scanner = new ProjectTopologyScanner();
            ProjectTopology topology = scanner.scan(workingDirectory);
            ctx.append("\n[TECNOLOGÍA] ").append(topology.buildSystem().getDisplayName());
            if (topology.isEmpty()) {
                ctx.append(" (Modo Explorador / Sin Proyecto)");
            }
            ctx.append("\n");
            if (!topology.isEmpty()) {
                ctx.append(topology.toContextString());
            }
            ctx.append("\n════════════════════════════════════════════════════════════════\n");
            ctx.append("[REGLAS DE ARQUITECTURA - OBLIGATORIAS]\n");
            ctx.append(topology.getArchitecturalRules());
            ctx.append("════════════════════════════════════════════════════════════════\n");
        } catch (Exception e) {
            LOG.warning("[CONTEXT] Fallo al escanear topología: " + e.getMessage());
            ctx.append("\n[WARN] Topología no disponible. Modo STANDALONE activo.\n");
        }
        ctx.append("\n════════ [FIN DEL CONTEXTO DE SISTEMA] ════════\n");
        ctx.append("INSTRUCCIÓN: Responde a la pregunta del usuario. NO repitas el contexto anterior.\n");
        return ctx.toString();
    }

    public String buildAgenticSystemPrompt(String userQuery) {
        ContextProfile profile = detectContextProfile(userQuery);
        String directResponseDirective = buildDirectResponseDirective(profile, userQuery);

        String q = (userQuery != null) ? userQuery.toLowerCase() : "";
        boolean isPassiveAnalysis = profile == ContextProfile.STRATEGIC &&
            q.matches("(?i).*(contexto|resumen|de que trata|que es|explic|analis|overview|describe|dime|muestra).*") &&
            !q.matches("(?i).*(refactor|migra|crea|implementa|diseña|planifica|genera|escribe|modifica|corrige).*");

        if (isPassiveAnalysis) {
            return """
            Eres Fararoni, el Oficial Técnico de Guardia (Staff Engineer).
            Tus capacidades: Análisis arquitectónico, diagnóstico profundo, síntesis ejecutiva.
            DIRECTORIO DE TRABAJO ACTUAL: """ + this.workingDirectory.toAbsolutePath() + """

            ═══════════════════════════════════════════════════════════════════════
            CONTEXTO DEL PROYECTO (YA CARGADO - NO BUSQUES MÁS)
            ═══════════════════════════════════════════════════════════════════════
            """
            + getProjectContext(profile)
            + """

            ═══════════════════════════════════════════════════════════════════════
            REGLAS DE ANÁLISIS (OBLIGATORIAS)
            ═══════════════════════════════════════════════════════════════════════
            1. TIENES ACCESO AL CÓDIGO REAL arriba. ¡ÚSALO! No pidas más archivos.
            2. Analiza el contenido REAL (README, pom.xml), no solo los nombres.
            3. Si hay pom.xml, EXTRAE: groupId, artifactId, version, dependencias.
            4. Si hay README.md, RESUME su contenido para explicar el proyecto.
            5. NO INVENTES entidades que no veas en el código proporcionado.

            REGLAS DE RESPUESTA (ANTI-DUMPING):
            1. [X] NO repitas el código/XML/estructura que ya ves arriba.
            2. [X] NO uses herramientas (ListFiles, FileSearch). El contexto ES SUFICIENTE.
            3. [X] NO digas "necesito ver más archivos". YA TIENES TODO.
            4. [>] SINTETIZA la información en un resumen ejecutivo.
            5. [>] Responde en PROSA, como un Staff Engineer explicando a un CTO.
            6. [>] Empieza con: "Tras analizar la arquitectura del proyecto..."

            Directorio de trabajo: """ + this.workingDirectory.toString();
        }

        return """
            Eres Fararoni, un Oficial Técnico de IA experto en Java y Arquitectura de Software.
            DIRECTORIO DE TRABAJO ACTUAL: """ + this.workingDirectory.toAbsolutePath() + """

            IMPORTANTE: Cuando el usuario pregunte por pwd, ruta actual o directorio de trabajo,
            responde con la ruta REAL de arriba. NO inventes rutas como /home/user/.

            ═══════════════════════════════════════════════════════════════════════
            CONTEXTO OPERATIVO
            ═══════════════════════════════════════════════════════════════════════
            """
            + getProjectContext(profile)
            + directResponseDirective +
            """

            ═══════════════════════════════════════════════════════════════════════
            [NIVEL 1] HERRAMIENTAS NATIVAS (PRIORIDAD ALTA - ACCIÓN)
            ═══════════════════════════════════════════════════════════════════════
            Tienes acceso a funciones del sistema de archivos para MODIFICAR:
            - fs_write: Para crear o sobrescribir archivos.
            - fs_patch: Para aplicar parches quirúrgicos (SEARCH/REPLACE).
            - fs_mkdir: Para crear directorios.
            - fs_read:  Para leer contenido completo de archivos.

            INTENTA USAR ESTAS HERRAMIENTAS SIEMPRE a través del protocolo nativo (Function Calling).

            ═══════════════════════════════════════════════════════════════════════
            [NIVEL 2] EXPLORACIÓN Y CONTEXTO (USAR SI NECESITAS MÁS DETALLES)
            ═══════════════════════════════════════════════════════════════════════
            Si el contexto inicial no es suficiente para tu tarea:
            - ListFiles: Ver contenido detallado de una carpeta.
            - FileSearch: Encontrar código específico por palabras clave (grep).
            - GlobGet: Encontrar archivos por patrón (ej: "*.xml", "User*.java").
            - DeepScan: Análisis arquitectónico profundo (10 niveles).
            - TaskSearch: Buscar tareas previas para no duplicar trabajo.

            ═══════════════════════════════════════════════════════════════════════
            [NIVEL 3] PROTOCOLO DE CONTINGENCIA (FALLBACK)
            ═══════════════════════════════════════════════════════════════════════
            Si por limitaciones técnicas NO puedes invocar la función nativa:

            Para archivos:
            >>>FILE: {ruta_relativa_del_archivo}
            {contenido_completo_del_código}
            <<<END_FILE

            Para directorios:
            >>>MKDIR: {ruta_del_directorio}
            <<<END_MKDIR

            ═══════════════════════════════════════════════════════════════════════
            [NIVEL 4] NORMALIZACIÓN DE PROTOCOLO - CRÍTICO
            ═══════════════════════════════════════════════════════════════════════
            IMPORTANTE: Para obtener información del sistema (fecha, hora, usuario, etc.):
            - [X] NO generes JSON como {"tool":"system","action":"exec",...}
            - [>] USA la herramienta nativa 'datetime' a través de Function Calling
            - [>] O simplemente RESPONDE directamente si conoces la información

            Si el usuario pregunta "qué día es hoy", "qué hora es", etc.:
            - Usa la función 'datetime' si está disponible
            - O responde directamente: "Hoy es [fecha actual]"

            ═══════════════════════════════════════════════════════════════════════
            [REGLAS DE SEGURIDAD - INVIOLABLES]
            ═══════════════════════════════════════════════════════════════════════
            1. JAMÁS escribas comandos tipo 'fs_write path content' sueltos en texto.
            2. Si vas a generar código ejecutable, usa el Bloque >>>FILE: si la tool falla.
            3. No uses bloques markdown simples (```java) para archivos que deben persistirse.
            4. NUNCA generes JSON de herramienta en texto plano - usa Function Calling nativo.

            Directorio de trabajo: """ + this.workingDirectory.toString();
    }

    public String buildAgenticSystemPrompt() {
        return buildAgenticSystemPrompt(null);
    }

    public String buildDirectResponseDirective(ContextProfile profile, String userQuery) {
        if (profile == ContextProfile.STRATEGIC) {
            String q = (userQuery != null) ? userQuery.toLowerCase() : "";
            boolean isPassiveAnalysis = q.matches("(?i).*(contexto|resumen|de que trata|que es|explic|analis|overview|describe|dime|muestra).*")
                                     && !q.matches("(?i).*(refactor|migra|crea|implementa|diseña|planifica|genera|escribe|modifica|corrige).*");

            if (isPassiveAnalysis) {
                return """

            ════════════════════════════════════════════════════════════════════════
            [PROTOCOL: EXECUTIVE SUMMARY] - IMMEDIATE SYNTHESIS REQUIRED
            ════════════════════════════════════════════════════════════════════════
            CONTEXT STATUS: Deep Context loaded (~6-10k chars). README, pom.xml, architecture visible.

             ANTI-DUMPING RULES (STRICT - INVIOLABLE):
            1. [X] DO NOT repeat file structures or lists provided in context.
            2. [X] DO NOT output headers like "=== MAPA ===" or "[FILE] README.md:".
            3. [X] DO NOT copy-paste code blocks from the context.
            4. [X] DO NOT use 'EnterPlanMode', 'TaskCreate', 'ListFiles'. Context is SUFFICIENT.

            [>] ACTION REQUIRED:
            Act as a STAFF ENGINEER briefing a CTO.
            SYNTHESIZE the README, pom.xml, and code into a PROSE summary.

            Structure your response in PROSE (not lists):
            1.  **Purpose**: What does this project do? (1-2 sentences)
            2.  **Architecture**: Tech stack, key patterns (Maven, Java, Agent, Swarm).
            3.  **Key Components**: Critical modules from the analysis (1-2 sentences each).

            Start strictly with: "Tras analizar la arquitectura del proyecto..."
            Write in PARAGRAPHS, not bullet dumps. The user wants INSIGHT, not raw data.
            ════════════════════════════════════════════════════════════════════════
            """;
            } else {
                return """

            ════════════════════════════════════════════════════════════════════════
            [MODE: ACTIVE ARCHITECTURE] - PLANNING AUTHORIZED
            ════════════════════════════════════════════════════════════════════════
            SITUATIONAL AWARENESS: Deep Context is LOADED. You see all files/classes.

            RULES OF ENGAGEMENT (ROE):
            1. [>] If the task is COMPLEX or RISKY, USE 'EnterPlanMode' first to plan.
            2. [>] If the task is SIMPLE, execute directly using Tools.
            3. [>] Use 'TaskCreate' if tracking is needed for multi-step work.

            You have full authority to plan, analyze, and execute.
            ════════════════════════════════════════════════════════════════════════
            """;
            }
        }
        return "";
    }

    public String buildCognitivePrompt(String userPrompt, ToolCall toolCall, ToolExecutionResult toolResult) {
        return """
            [CONTEXTO PREVIO]
            El usuario solicitó: "%s"

            [ACCIÓN TOMADA]
            Ejecutaste la herramienta '%s' para prepararte.
            Resultado del sistema: %s

            [INSTRUCCIÓN CRÍTICA]
            Ahora DEBES generar la respuesta final para el usuario.
            NO uses más herramientas de planificación.
            Sintetiza el contexto que ya tienes y RESPONDE DIRECTAMENTE.

            Comienza tu respuesta con: "Basado en el análisis..."
            """.formatted(userPrompt, toolCall.functionName(), toolResult.message());
    }

    public String buildRabbitPrompt(String userPrompt) {
        String systemBlock = """
            <|im_start|>system
            You are Fararoni, an intelligent CLI assistant that can CREATE FILES.

            Available Tools:
            - system: exec (for date, time, whoami, pwd, uname, hostname)
            - git: push, pull, status, diff, commit, branch, log
            - file: load, unload, search, list
            - config: set, get, show, delete

            FILE CREATION (IMPORTANT):
            When asked to create a file or write code, use this EXACT format:

            >>>FILE: {ruta_relativa_del_archivo}
            {contenido_completo_del_código}
            <<<END_FILE

            RULES:

            1. DATE/TIME/USER QUESTIONS:
               Use system tool. Example: {"tool":"system","action":"exec","params":{"command":"date"}}

            2. GIT/FILE/CONFIG TASKS:
               Use JSON. Example: {"tool":"git","action":"push"}

            3. FILE CREATION:
               Use >>>FILE: format. The file will be created on disk.

            4. GREETINGS/CHAT:
               Reply in plain text. NO JSON.

            EXAMPLES:

            User: hola
            Assistant: Hola! En que te ayudo?

            User: que fecha es hoy
            Assistant: {"tool":"system","action":"exec","params":{"command":"date"}}

            User: crea Alumno.java
            Assistant: >>>FILE: Alumno.java
            public class Alumno {
                private String nombre;
                public Alumno(String nombre) { this.nombre = nombre; }
                public String getNombre() { return nombre; }
            }
            <<<END_FILE

            User: sube cambios
            Assistant: {"tool":"git","action":"push"}
            <|im_end|>
            """;

        String sessionBlock = "";
        try {
            String sessionCtx = ServiceRegistry.getSessionContextForPrompt();
            if (sessionCtx != null && !sessionCtx.isBlank()) {
                sessionBlock = "<|im_start|>system\n" + sessionCtx + "\n<|im_end|>\n";
            }
        } catch (Exception e) {
            LOG.fine(() -> "[CHAT] No session context available: " + e.getMessage());
        }

        StringBuilder historyBlock = new StringBuilder();
        int historySize = conversationHistory.size();
        int messagesToInclude = Math.max(0, historySize - 1);
        for (int i = 0; i < messagesToInclude; i++) {
            Message msg = conversationHistory.get(i);
            historyBlock.append("<|im_start|>").append(msg.role()).append("\n");
            String content = msg.content();
            if (content.length() > 500) {
                content = content.substring(0, 497) + "...";
            }
            historyBlock.append(content).append("\n");
            historyBlock.append("<|im_end|>\n");
        }

        String userBlock = "<|im_start|>user\n" + userPrompt + "\n<|im_end|>\n";
        String assistantBlock = "<|im_start|>assistant\n";

        return systemBlock + sessionBlock + historyBlock.toString() + userBlock + assistantBlock;
    }

    public String buildExpertPrompt(String userPrompt, int maxContextTokens) {
        final int RESERVED_TOKENS = Math.min(maxContextTokens / 3, 10_000);
        final int AVAILABLE_TOKENS = Math.max(maxContextTokens - RESERVED_TOKENS, 2048);
        final int MAX_CONTEXT_CHARS = AVAILABLE_TOKENS * 4;

        StringBuilder sb = new StringBuilder();

        sb.append("<|im_start|>system\n")
          .append("Eres Fararoni, el Oficial Técnico de Guardia (Staff Engineer).\n")
          .append("Tus capacidades: Análisis arquitectónico, diagnóstico profundo, generación de soluciones.\n\n")
          .append("REGLAS DE ANÁLISIS:\n")
          .append("1. TIENES ACCESO AL CÓDIGO REAL en la sección 'AUTOMATIC INTELLIGENCE'. ¡ÚSALO!\n")
          .append("2. Identifica el stack tecnológico basándote en los archivos leídos:\n")
          .append("   - pom.xml → Java/Maven (lee las dependencias)\n")
          .append("   - package.json → Node.js (lee las dependencias)\n")
          .append("   - requirements.txt → Python\n")
          .append("   - Cargo.toml → Rust\n")
          .append("   - go.mod → Go\n")
          .append("3. Si ves credenciales o configs (.env), asume que tienes permiso para mencionarlas.\n")
          .append("4. Analiza el contenido REAL de los archivos, no solo sus nombres.\n")
          .append("5. Si hay un pom.xml, EXTRAE: groupId, artifactId, version, dependencias principales.\n")
          .append("6. Si hay un README.md, RESUME su contenido para explicar el proyecto.\n\n")
          .append("REGLAS DE RESPUESTA:\n")
          .append("1. Basa tu análisis en el CONTENIDO de 'AUTOMATIC INTELLIGENCE'.\n")
          .append("2. Si la sección 'AUTOMATIC INTELLIGENCE' está vacía, usa 'FILE SYSTEM MAP'.\n")
          .append("3. Sé técnico, preciso y directo. Usa Markdown.\n")
          .append("4. NO INVENTES entidades que no veas en el código proporcionado.\n\n")
          .append("### CORE KNOWLEDGE BANK ###\n")
          .append("FACT: El creador de este software es: ").append(AppDefaults.CREATOR_BIO).append("\n")
          .append("INSTRUCTION: Si te preguntan por Eber Cruz, usa estrictamente esta biografía. ")
          .append("No inventes experiencia en empresas no mencionadas (Google, Microsoft, etc).\n")
          .append("<|im_end|>\n");

        try {
            int identityChars = sb.length();
            int userQueryChars = userPrompt.length() + 100;
            int usedChars = identityChars + userQueryChars;
            int availableBudget = Math.max(MAX_CONTEXT_CHARS - usedChars, 1024);

            final int CONTENT_BUDGET = Math.max((int)(availableBudget * 0.60), 256);
            final int SESSION_BUDGET = Math.max((int)(availableBudget * 0.15), 128);
            final int STRUCTURE_BUDGET = Math.max((int)(availableBudget * 0.25), 128);

            LOG.fine(() -> "[TOKEN-BUDGET] Disponible: " + availableBudget + " chars " +
                          "(Contenido: " + CONTENT_BUDGET + ", Sesión: " + SESSION_BUDGET +
                          ", Estructura: " + STRUCTURE_BUDGET + ")");

            String reconData = performAgnosticRecon(workingDirectory);
            if (!reconData.isEmpty()) {
                if (reconData.length() > CONTENT_BUDGET) {
                    reconData = reconData.substring(0, CONTENT_BUDGET - 50) +
                               "\n... [CONTENIDO TRUNCADO - " +
                               (reconData.length() - CONTENT_BUDGET) + " chars omitidos] ...\n";
                    LOG.info("[TOKEN-BUDGET] Contenido truncado a " + CONTENT_BUDGET + " chars");
                }
                sb.append("<|im_start|>system\n")
                  .append("=== [ID] IDENTIDAD DEL PROYECTO (LEER PRIMERO) ===\n")
                  .append("El siguiente contenido define QUE ES este proyecto. ")
                  .append("Basa tu análisis en estos archivos, NO en los nombres de carpetas.\n\n")
                  .append(reconData)
                  .append("<|im_end|>\n");
                LOG.info("[TOKEN-BUDGET] Contenido inyectado PRIMERO (" + reconData.length() + " chars)");
            } else {
                sb.append("<|im_start|>system\n")
                  .append("=== [!] ADVERTENCIA: VISIÓN LIMITADA ===\n")
                  .append("[SYSTEM WARNING: No se encontraron archivos de configuración.\n")
                  .append("El análisis de contenido no es posible. Solo puedo ver estructura.\n")
                  .append("INSTRUCCIÓN: NO INVENTES el propósito del proyecto. ")
                  .append("Si no puedes determinarlo, pregunta al usuario.]\n")
                  .append("<|im_end|>\n");
                LOG.warning("[TOKEN-BUDGET] Reconocimiento vacío. Alto riesgo de alucinación.");
            }

            String sessionCtx = ServiceRegistry.getSessionContextForPrompt();
            if (sessionCtx != null && !sessionCtx.isBlank()) {
                String safeCtx = sessionCtx.replace("<|im_start|>", "[BLOCK_START]")
                                           .replace("<|im_end|>", "[BLOCK_END]");
                if (safeCtx.length() > SESSION_BUDGET) {
                    safeCtx = safeCtx.substring(0, SESSION_BUDGET - 30) +
                             "\n... [SESIÓN TRUNCADA] ...\n";
                }
                sb.append("<|im_start|>system\n")
                  .append("=== CONTEXTO DE SESIÓN ACTIVA ===\n")
                  .append(safeCtx)
                  .append("<|im_end|>\n");
            }

            boolean mapLoaded = false;
            StringBuilder structureBuffer = new StringBuilder();

            if (memoryVault instanceof IndexStore idxStore && idxStore.isAvailable()) {
                var stats = idxStore.getStats();
                if (stats.totalFiles() > 0) {
                    structureBuffer.append("=== REFERENCIA: ESTRUCTURA DE ARCHIVOS ===\n")
                        .append("(").append(stats.totalFiles()).append(" archivos indexados, ")
                        .append(stats.successFiles()).append(" parseados)\n\n");
                    String projectMap = idxStore.getProjectStructureMap(workingDirectory);
                    if (projectMap != null && !projectMap.startsWith("(") && !projectMap.contains("0 files")) {
                        if (projectMap.length() > STRUCTURE_BUDGET - 200) {
                            int cutPoint = STRUCTURE_BUDGET - 250;
                            if (cutPoint > 0 && cutPoint < projectMap.length()) {
                                projectMap = projectMap.substring(0, cutPoint) +
                                            "\n... [ESTRUCTURA TRUNCADA - proyecto grande, " +
                                            stats.totalFiles() + " archivos total] ...\n";
                            }
                            LOG.info("[TOKEN-BUDGET] Mapa estructural truncado (proyecto grande)");
                        }
                        structureBuffer.append(projectMap);
                        mapLoaded = true;
                    }
                }
            }

            if (!mapLoaded) {
                LOG.info("[TOKEN-BUDGET] IndexStore no disponible. Usando escaneo live.");
                String liveMap = generateFallbackMap();
                if (liveMap.length() > STRUCTURE_BUDGET) {
                    liveMap = liveMap.substring(0, STRUCTURE_BUDGET - 50) +
                             "\n... [ESCANEO TRUNCADO] ...\n";
                }
                structureBuffer.append("=== ESTRUCTURA (Escaneo Live) ===\n")
                               .append(liveMap)
                               .append("[Database indexing may be in progress]\n");
            }

            if (structureBuffer.length() > 0) {
                sb.append("<|im_start|>system\n")
                  .append(structureBuffer)
                  .append("<|im_end|>\n");
            }
        } catch (Exception e) {
            LOG.warning(() -> "[TOKEN-BUDGET] Fallo en construcción de contexto: " + e.getMessage());
            sb.append("<|im_start|>system\n")
              .append("[SYSTEM WARN: Context retrieval degraded - " + e.getMessage() + "]\n")
              .append("<|im_end|>\n");
        }

        if (sb.length() < MAX_CONTEXT_CHARS) {
            int historySize = conversationHistory.size();
            int startIdx = Math.max(0, historySize - 8);
            for (int i = startIdx; i < historySize; i++) {
                Message msg = conversationHistory.get(i);
                String content = msg.content();
                if (content.length() > 2000) {
                    content = content.substring(0, 1997) + "...";
                }
                sb.append("<|im_start|>").append(msg.role()).append("\n")
                  .append(content).append("\n<|im_end|>\n");
            }
        }

        sb.append("<|im_start|>user\n").append(userPrompt).append("<|im_end|>\n");
        sb.append("<|im_start|>assistant\n");

        return sb.toString();
    }

    public String performAgnosticRecon(Path rootDir) {
        long now = System.currentTimeMillis();
        if (cachedReconData != null && (now - reconCacheTimestamp) < RECON_CACHE_TTL) {
            return cachedReconData;
        }

        String rootStr = rootDir.toAbsolutePath().toString();
        String homeDir = System.getProperty("user.home");
        if (rootStr.equals(homeDir) || rootStr.equals("/") || rootStr.matches("^[A-Z]:\\\\$")) {
            LOG.warning("[RECON] SAFETY: Directorio HOME o raiz detectado. Recon limitado.");
            return "\n=== AUTOMATIC INTELLIGENCE ===\n" +
                    "[SYSTEM WARNING: Ejecutando desde directorio HOME o raiz.\n" +
                    "El reconocimiento automatico esta deshabilitado para evitar escanear miles de archivos.\n" +
                    "Sugerencia: Navega a la carpeta del proyecto antes de ejecutar Fararoni.]\n";
        }

        StringBuilder recon = new StringBuilder();

        java.util.Set<String> ignoredDirs = java.util.Set.of(
                "node_modules", ".git", ".svn", ".hg",
                "target", "build", "dist", "out", "bin",
                ".idea", ".vscode", ".settings", ".classpath",
                "__pycache__", ".gradle", ".m2", ".checkstyle",
                "coverage", "test-output", "archived"
        );

        try {
            java.util.PriorityQueue<ReconTarget> candidates = new java.util.PriorityQueue<>(
                    java.util.Comparator.comparingInt(ReconTarget::score).reversed()
            );

            java.nio.file.Files.walkFileTree(rootDir, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 12, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (ignoredDirs.contains(dirName) || dirName.startsWith(".")) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    int score = calculateRelevanceScore(rootDir, file);
                    if (score > 0) {
                        String ext = getFileExtension(file);
                        candidates.add(new ReconTarget(file, score, ext));
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, java.io.IOException exc) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });

            if (candidates.isEmpty()) return "";

            recon.append("\n=== AUTOMATIC INTELLIGENCE (Contexto Detectado) ===\n");

            int maxFiles = 6;
            int maxCharsPerFile = 2000;
            int totalBudget = 8000;
            int currentTotal = 0;
            int selectedCount = 0;
            java.util.Set<String> seenExtensions = new java.util.HashSet<>();

            while (!candidates.isEmpty() && selectedCount < maxFiles && currentTotal < totalBudget) {
                ReconTarget target = candidates.poll();
                boolean isImportant = target.score >= 600;
                boolean isDuplicate = seenExtensions.contains(target.extension);
                if (isDuplicate && !isImportant && candidates.size() > 2) {
                    continue;
                }
                String content = readSafeContentWithTimeout(target.path);
                if (!content.isEmpty()) {
                    if (content.length() > maxCharsPerFile) {
                        content = content.substring(0, maxCharsPerFile) +
                                "\n... [ARCHIVO TRUNCADO - Espacio para otros archivos] ...\n";
                    }
                    recon.append(content);
                    seenExtensions.add(target.extension);
                    currentTotal += content.length();
                    selectedCount++;
                }
            }

            String result = (selectedCount > 0) ? recon.toString() : "";
            cachedReconData = result;
            reconCacheTimestamp = now;
            return result;
        } catch (Exception e) {
            LOG.warning("Fallo en reconocimiento táctico: " + e.getMessage());
            return "";
        }
    }

    public int calculateRelevanceScore(Path root, Path file) {
        String filename = file.getFileName().toString();
        String lower = filename.toLowerCase();
        String absolutePath = file.toAbsolutePath().toString();

        if (lower.startsWith("license") || lower.startsWith("notice") ||
            lower.startsWith("copying") || lower.endsWith(".lock") ||
            lower.contains("lock.json") || lower.equals("contributing.md") ||
            lower.equals("code_of_conduct.md") || lower.equals("security.md")) {
            return 1;
        }

        if (absolutePath.contains("node_modules") || absolutePath.contains(".git") ||
            absolutePath.contains("target") || absolutePath.contains("build") ||
            absolutePath.contains("dist") || absolutePath.contains("__pycache__") ||
            absolutePath.contains(".benchmarks") || absolutePath.contains("/tmp/") ||
            absolutePath.contains("/tmp.") || absolutePath.contains("coverage") ||
            absolutePath.contains("test-output") || absolutePath.contains(".cache")) {
            return 0;
        }

        if (lower.endsWith(".class") || lower.endsWith(".jar") || lower.endsWith(".exe") ||
            lower.endsWith(".dll") || lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".gif") || lower.endsWith(".zip") || lower.endsWith(".tar") ||
            lower.endsWith(".gz") || lower.endsWith(".ico") || lower.endsWith(".woff") ||
            lower.endsWith(".ttf") || lower.endsWith(".pdf") || lower.endsWith(".mp4") ||
            lower.endsWith(".mp3") || lower.endsWith(".wav")) {
            return 0;
        }

        int depth = root.relativize(file).getNameCount();
        if (depth > 12) {
            return 0;
        }

        try {
            if (java.nio.file.Files.size(file) > 80_000) return 0;
        } catch (Exception e) { return 0; }

        int score = 0;
        boolean isRoot = file.getParent() != null && file.getParent().equals(root);

        if (lower.contains("readme")) {
            score += 900;
        }

        boolean isIdentityFile = lower.equals("pom.xml") || lower.equals("package.json") ||
                                 lower.equals("requirements.txt") || lower.equals("go.mod") ||
                                 lower.equals("cargo.toml") || lower.equals("build.gradle") ||
                                 lower.equals("build.gradle.kts") || lower.equals("dockerfile") ||
                                 lower.contains("docker-compose") || lower.equals("makefile") ||
                                 lower.equals("cmakelists.txt");

        if (isIdentityFile) {
            score += 800;
        }

        boolean isEntryPoint = lower.startsWith("main.") || lower.startsWith("app.") ||
                               lower.startsWith("index.") || lower.startsWith("server.") ||
                               lower.startsWith("program.") || lower.contains("application") ||
                               lower.contains("core.") || lower.contains("bootstrap");

        if (isEntryPoint) {
            score += 600;
        }

        boolean isSourceCode = lower.endsWith(".java") || lower.endsWith(".py") ||
                               lower.endsWith(".js") || lower.endsWith(".ts") ||
                               lower.endsWith(".go") || lower.endsWith(".rs") ||
                               lower.endsWith(".cs") || lower.endsWith(".kt");

        if (isSourceCode) {
            score += 100;
            if (isRoot) score += 200;
            try {
                if (java.nio.file.Files.size(file) < 2048) score += 50;
            } catch (Exception e) {  }
        }

        if (isRoot) score += 50;
        if (lower.contains("architecture") || lower.contains("design")) score += 80;
        if (absolutePath.contains("/docs/") && !lower.contains("readme") && !isRoot) {
            score -= 50;
        }

        if (score > 0) {
            if (lower.contains("core") || lower.contains("kernel") || lower.contains("engine")) {
                score += 500;
            }
            if (lower.equals("fararonicore.java")) {
                score += 1000;
            }
        }

        return Math.max(0, score);
    }

    public String readSafeContentWithTimeout(Path p) {
        try {
            java.util.concurrent.CompletableFuture<String> future =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        try (java.io.InputStream is = java.nio.file.Files.newInputStream(p)) {
                            byte[] buffer = new byte[2048];
                            int read = is.read(buffer);
                            for (int i = 0; i < read; i++) {
                                if (buffer[i] == 0) return "";
                            }
                        }
                        return java.nio.file.Files.readString(p);
                    } catch (Exception e) {
                        return "";
                    }
                });
            String content = future.get(250, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (content == null || content.isEmpty()) return "";
            if (content.length() > 6000) {
                content = content.substring(0, 6000) + "\n... [TRUNCADO POR LONGITUD] ...";
            }
            return "\n--- ARCHIVO: " + p.getFileName() + " ---\n" + content + "\n";
        } catch (Exception e) {
            return "";
        }
    }

    public String getFileExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot == -1 ? "NO_EXT" : name.substring(lastDot).toLowerCase();
    }

    public String generateFallbackMap() {
        StringBuilder sb = new StringBuilder();
        sb.append("Directory Structure (Live Scan - Depth 2):\n");
        try {
            Path root = workingDirectory != null
                ? workingDirectory
                : java.nio.file.Paths.get(System.getProperty("user.dir"));
            try (var stream = java.nio.file.Files.list(root)) {
                stream.sorted().forEach(path -> {
                    try {
                        String name = path.getFileName().toString();
                        if (name.startsWith(".") ||
                            name.equals("target") ||
                            name.equals("build") ||
                            name.equals("node_modules") ||
                            name.equals("__pycache__") ||
                            name.equals("dist") ||
                            name.equals("out")) {
                            return;
                        }
                        boolean isDir = java.nio.file.Files.isDirectory(path);
                        sb.append(isDir ? "  [D] " : "  [F] ")
                          .append(name)
                          .append(isDir ? "/" : "")
                          .append("\n");
                        if (isDir) {
                            try (var subStream = java.nio.file.Files.list(path)) {
                                long count = subStream
                                    .filter(p -> !p.getFileName().toString().startsWith("."))
                                    .count();
                                if (count > 0) {
                                    sb.append("     └── (contains ").append(count).append(" items)\n");
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                });
            }
        } catch (Exception e) {
            sb.append("(Fallback scan failed: ").append(e.getMessage()).append(")");
        }
        return sb.toString();
    }

    public String generateFallbackAnalysis(String userPrompt) {
        if (memoryVault != null && memoryVault.isAvailable()) {
            try {
                String map = memoryVault.generateMap(ContextProfile.STRATEGIC);
                if (map != null && !map.isEmpty()) {
                    return "Basado en el análisis del proyecto:\n\n" +
                           "**Estructura del Proyecto:**\n" + map +
                           "\n\nPara más detalles, puedes preguntar sobre archivos específicos.";
                }
            } catch (Exception e) { LOG.fine("Fallback analysis failed: " + e.getMessage()); }
        }
        return "El proyecto contiene código fuente que puedo analizar. " +
               "¿Sobre qué aspecto específico te gustaría saber más?";
    }

    public void setEnableCache(boolean enable) {
        this.enableCache = enable;
    }

    public void setEnablePersistence(boolean enable) {
        this.enablePersistence = enable;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public boolean isEnablePersistence() {
        return enablePersistence;
    }

    public ProjectKnowledgeBase getMemoryVault() {
        return memoryVault;
    }

    public ContextHealer getContextHealer() {
        return contextHealer;
    }

    public IndexStore.IndexStats getIndexStats() {
        if (memoryVault instanceof IndexStore idx && idx.isAvailable()) {
            return idx.getStats();
        }
        return IndexStore.IndexStats.EMPTY;
    }

    public long getIndexedFileCount() {
        var stats = getIndexStats();
        return stats != null ? stats.totalFiles() : 0;
    }

    public boolean isIndexingComplete() {
        return crawlFuture != null && crawlFuture.isDone();
    }

    public ProjectSentinel getProjectSentinel() {
        return projectSentinel;
    }

    public CompletableFuture<CrawlResult> getCrawlFuture() {
        return crawlFuture;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public void shutdownResources() {
        if (projectSentinel != null) {
            try {
                projectSentinel.close();
                LOG.info("[FARARONI] Centinela de proyecto cerrado");
            } catch (Exception e) {
                LOG.warning(() -> "[FARARONI] Error cerrando centinela: " + e.getMessage());
            }
        }
        if (memoryVault != null) {
            try {
                memoryVault.close();
                LOG.info("[FARARONI] Memoria persistente cerrada");
            } catch (Exception e) {
                LOG.warning(() -> "[FARARONI] Error cerrando memoria: " + e.getMessage());
            }
        }
    }
}
