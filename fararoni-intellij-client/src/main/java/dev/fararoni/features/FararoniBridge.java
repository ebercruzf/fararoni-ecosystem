/*
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---------------------------------------------------------------------------
 *
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licenciado bajo la Licencia Apache, Version 2.0 (la "Licencia");
 * no puede usar este archivo excepto en cumplimiento con la Licencia.
 * Puede obtener una copia de la Licencia en
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * A menos que lo exija la ley aplicable o se acuerde por escrito, el software
 * distribuido bajo la Licencia se distribuye "TAL CUAL", SIN GARANTIAS NI
 * CONDICIONES DE NINGUN TIPO, ya sean expresas o implicitas.
 * Consulte la Licencia para conocer el lenguaje especifico que rige los
 * permisos y las limitaciones de la misma.
 */
package dev.fararoni.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import okhttp3.*;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class FararoniBridge {

    private static final Logger LOG = Logger.getLogger(FararoniBridge.class.getName());

    /** URL del Gateway Fararoni (ahora dinamico via FararoniSettingsState) */
    // GATEWAY_URL ahora se obtiene de FararoniSettingsState.getInstance().gatewayUrl

    /** Canal identificador para IntelliJ */
    private static final String CHANNEL_ID = "intellij";

    /** MediaType para JSON */
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Project project;
    private OkHttpClient httpClient;  // Ahora se obtiene del Sentinel
    private final Gson gson;

    // Métricas
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalAccepted = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    /**
     * Crea un nuevo Bridge para el proyecto dado.
     *
     * @param project el proyecto de IntelliJ
     */
    public FararoniBridge(Project project) {
        this.project = project;
        this.gson = new Gson();

        // Usar el cliente HTTP del SentinelService para aprovechar su heartbeat
        // Esto evita que la conexión se "muera" por inactividad
        this.httpClient = null; // Se obtiene lazily en getHttpClient()

        LOG.info("[FararoniBridge] Initialized for project: " + project.getName());
    }

    /**
     * Obtiene el cliente HTTP, preferiblemente del SentinelService.
     *
     * <p>El cliente del Sentinel tiene heartbeat activo que mantiene
     * las conexiones "calientes" y evita hibernación.</p>
     */
    private OkHttpClient getHttpClient() {
        if (httpClient != null) {
            return httpClient;
        }

        // Intentar obtener del Sentinel (tiene heartbeat)
        try {
            FararoniSentinelService sentinel = FararoniSentinelService.getInstance(project);
            if (sentinel != null && sentinel.isRunning()) {
                httpClient = sentinel.getHttpClient();
                LOG.info("[FararoniBridge] Using Sentinel's HTTP client (with heartbeat)");
                return httpClient;
            }
        } catch (Exception e) {
            LOG.fine("[FararoniBridge] Sentinel not available, creating standalone client");
        }

        // Fallback: crear cliente propio si Sentinel no está disponible
        ExecutorService networkExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "FararoniBridge-Network");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });

        Dispatcher dispatcher = new Dispatcher(networkExecutor);
        dispatcher.setMaxRequests(64);
        dispatcher.setMaxRequestsPerHost(10);

        httpClient = new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        LOG.info("[FararoniBridge] Created standalone HTTP client (no heartbeat)");
        return httpClient;
    }

    /**
     * Envía una consulta al Core con contexto de proyecto.
     *
     * <p>GRADO MILITAR: El contexto se inyecta DIRECTAMENTE en el textContent
     * para garantizar que el LLM lo vea, independientemente de si el Core
     * procesa correctamente los metadatos.</p>
     *
     * @param prompt texto de la consulta
     * @param intent tipo de respuesta esperada
     */
    public void sendQuery(String prompt, String intent) {
        // Variables para contexto
        String contextualPrompt = prompt;
        String activeFile = "N/A";
        String openFiles = "[]";
        String projectName = project.getName();

        // Obtener contexto de proyecto y construir prompt enriquecido
        try {
            ProjectAwarenessManager awareness = ProjectAwarenessManager.getInstance(project);
            if (awareness != null) {
                JsonObject projectContext = awareness.getProjectContextSnapshot();

                // Extraer datos del contexto
                if (projectContext.has("activeFile")) {
                    activeFile = projectContext.get("activeFile").getAsString();
                }
                if (projectContext.has("openFiles")) {
                    openFiles = projectContext.get("openFiles").toString();
                }
                if (projectContext.has("projectName")) {
                    projectName = projectContext.get("projectName").getAsString();
                }

                // [GRADO MILITAR] Inyectar contexto DIRECTO en el prompt
                // Esto garantiza que el LLM siempre vea los archivos abiertos
                contextualPrompt = buildContextualPrompt(prompt, projectName, activeFile, openFiles);

                // --- LOG DE GRADO MILITAR: INSPECCION DE METADATOS SALIENTES ---
                LOG.info("================ [FARARONI DEBUG: OUTBOUND METADATA] ================");
                LOG.info("SENDER ID: " + getSenderId());
                LOG.info("INTENT: " + intent);
                LOG.info("ORIGINAL PROMPT: " + truncate(prompt, 100));
                LOG.info("PROJECT: " + projectName);
                LOG.info("FILE IN FOCUS: " + activeFile);
                LOG.info("OPEN FILES: " + openFiles);
                LOG.info("CONTEXTUAL PROMPT SIZE: " + contextualPrompt.length() + " chars");
                LOG.info("====================================================================");

                // También incluir en metadata para compatibilidad
                String serializedContext = gson.toJson(projectContext);
                // Se agregará después de buildBasePayload
            } else {
                LOG.warning("[FararoniBridge] ProjectAwarenessManager is NULL - no context available!");
            }
        } catch (Exception e) {
            LOG.warning("[FararoniBridge] Could not get project context: " + e.getMessage());
        }

        // Construir payload con el prompt enriquecido
        JsonObject payload = buildBasePayload(contextualPrompt, intent);

        // Agregar metadata de contexto (para compatibilidad con Core si lo procesa)
        try {
            ProjectAwarenessManager awareness = ProjectAwarenessManager.getInstance(project);
            if (awareness != null) {
                JsonObject metadata = payload.getAsJsonObject("metadata");
                JsonObject projectContext = awareness.getProjectContextSnapshot();
                String serializedContext = gson.toJson(projectContext);
                metadata.addProperty("projectContext", serializedContext);
            }
        } catch (Exception e) {
            // Ya logueado arriba
        }

        sendToGateway(payload);
    }

    /**
     * Construye un prompt enriquecido con contexto de proyecto.
     *
     * <p>Esto fuerza al LLM a "ver" los archivos antes de procesar la pregunta,
     * resolviendo el problema de "sesion hurfana" donde el Core ignora el contexto.</p>
     *
     * @param originalPrompt pregunta original del usuario
     * @param projectName    nombre del proyecto
     * @param activeFile     archivo actualmente en foco
     * @param openFiles      lista de archivos abiertos
     * @return prompt enriquecido con contexto
     */
    private String buildContextualPrompt(String originalPrompt, String projectName,
                                          String activeFile, String openFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CONTEXTO DEL PROYECTO ===\n");
        sb.append("Proyecto: ").append(projectName).append("\n");
        sb.append("Archivo activo: ").append(activeFile).append("\n");
        sb.append("Archivos abiertos: ").append(openFiles).append("\n");

        // Incluir rama de Git
        String gitBranch = getCurrentGitBranch();
        if (!"no-vcs".equals(gitBranch)) {
            sb.append("Rama Git: ").append(gitBranch).append("\n");
        }

        sb.append("=============================\n\n");
        sb.append("PREGUNTA DEL USUARIO: ").append(originalPrompt);
        return sb.toString();
    }

    /**
     * Envía código para análisis con contexto de archivo.
     *
     * @param code       código a analizar
     * @param filePath   ruta del archivo
     * @param lineNumber línea actual
     * @param intent     tipo de análisis
     */
    public void analyzeCode(String code, String filePath, int lineNumber, String intent) {
        JsonObject payload = buildBasePayload(code, intent);

        // Agregar metadata de archivo
        JsonObject metadata = payload.getAsJsonObject("metadata");
        metadata.addProperty("file", filePath);
        metadata.addProperty("line", lineNumber);

        sendToGateway(payload);
    }

    /**
     * Envía análisis con contexto completo de proyecto.
     *
     * <p>Incluye información de todos los archivos abiertos.</p>
     *
     * @param code     código a analizar
     * @param filePath ruta del archivo activo
     * @param intent   tipo de análisis
     */
    public void analyzeWithFullContext(String code, String filePath, String intent) {
        JsonObject payload = buildBasePayload(code, intent);

        // Agregar contexto de proyecto
        JsonObject metadata = payload.getAsJsonObject("metadata");
        metadata.addProperty("activeFile", filePath);
        metadata.addProperty("intent", "PROJECT_WIDE_REFACTOR");

        // Incluir ProjectAwareness (serializado porque metadata es Map<String, String>)
        try {
            ProjectAwarenessManager awareness = ProjectAwarenessManager.getInstance(project);
            JsonObject projectContext = awareness.getProjectContextSnapshot();
            metadata.addProperty("projectContext", gson.toJson(projectContext));
        } catch (Exception e) {
            LOG.warning("[FararoniBridge] Could not get project context: " + e.getMessage());
        }

        sendToGateway(payload);
    }

    /**
     * Solicita corrección táctica para un error.
     *
     * @param errorDescription descripción del error
     * @param codeContext      código alrededor del error
     * @param filePath         archivo con el error
     */
    public void requestTacticalFix(String errorDescription, String codeContext, String filePath) {
        String prompt = "Error: " + errorDescription + "\n\nCódigo:\n" + codeContext;

        JsonObject payload = buildBasePayload(prompt, "QUICK_FIX");
        JsonObject metadata = payload.getAsJsonObject("metadata");
        metadata.addProperty("file", filePath);
        metadata.addProperty("errorDescription", errorDescription);

        sendToGateway(payload);
    }

    /**
     * Solicita análisis proactivo de SOLID/Clean Code.
     *
     * @param code     código a analizar
     * @param fileName nombre del archivo
     */
    public void analyzeProactively(String code, String fileName) {
        String prompt = "Analiza este código para mejoras de SOLID y Clean Code:\n\n" + code;

        JsonObject payload = buildBasePayload(prompt, "SMART_SUGGESTION");
        JsonObject metadata = payload.getAsJsonObject("metadata");
        metadata.addProperty("file", fileName);

        sendToGateway(payload);
    }

    /**
     * Construye el payload base para enviar al Gateway.
     *
     * <p>Incluye el Trace ID persistente para mantener la sesion
     * entre reinicios del plugin/IDE.</p>
     */
    private JsonObject buildBasePayload(String textContent, String intent) {
        JsonObject payload = new JsonObject();

        // Campos requeridos por UniversalMessage
        payload.addProperty("channelId", CHANNEL_ID);
        payload.addProperty("senderId", getSenderId());
        payload.addProperty("textContent", textContent);

        // Trace ID por proyecto (evita contaminación entre ventanas)
        String traceId = FararoniProjectSession.getInstance(project).getTraceId();
        payload.addProperty("traceId", traceId);

        // Metadata
        JsonObject metadata = new JsonObject();
        metadata.addProperty("callback_url", getCallbackUrl());
        metadata.addProperty("intent", intent);
        metadata.addProperty("traceParent", traceId);
        payload.add("metadata", metadata);

        LOG.fine("[FararoniBridge] Using project-scoped TraceParent: " + traceId);

        return payload;
    }

    /**
     * Genera un ID unico para el sender incluyendo identidad de hardware.
     *
     * <p>Resuelve el problema de colision de nombres entre diferentes maquinas.
     * Formato: usuario@proyecto#machineId</p>
     *
     * <p>El triple anclaje garantiza:</p>
     * <ul>
     *   <li><b>Quien</b>: user.name del sistema operativo</li>
     *   <li><b>Donde</b>: Nombre del proyecto de IntelliJ</li>
     *   <li><b>En que dispositivo</b>: Hostname de la maquina</li>
     * </ul>
     */
    private String getSenderId() {
        String user = System.getProperty("user.name", "unknown");
        String projectName = project.getName().replaceAll("[^a-zA-Z0-9]", "_");

        // Obtenemos un ID unico de la maquina (Hostname)
        String machineId = "local";
        try {
            java.net.InetAddress localMachine = java.net.InetAddress.getLocalHost();
            machineId = localMachine.getHostName().replaceAll("[^a-zA-Z0-9]", "");
        } catch (java.net.UnknownHostException e) {
            // Fallback: usar hash del OS
            machineId = Integer.toHexString(System.getProperty("os.arch").hashCode());
        }

        // Formato: usuario@proyecto#machineId
        String finalId = String.format("%s@%s#%s", user, projectName, machineId);
        LOG.fine("[FararoniBridge] Identity anchored to: " + finalId);
        return finalId;
    }

    /**
     * Obtiene la URL del CallbackServer desde configuracion.
     */
    private String getCallbackUrl() {
        // Usar configuracion dinamica
        return FararoniSettingsState.getInstance().getCallbackUrl();
    }

    /**
     * Extrae el nombre de la rama actual de Git de forma segura.
     *
     * <p>Usa la API de VcsRepositoryManager de IntelliJ para obtener
     * la rama actual del repositorio.</p>
     *
     * @return nombre de la rama (ej: "feature/auth-system") o "no-vcs" si no hay Git
     */
    public String getCurrentGitBranch() {
        try {
            // Intentar obtener via Git4Idea si está disponible
            var repositoryManager = com.intellij.dvcs.repo.VcsRepositoryManager.getInstance(project);
            var repositories = repositoryManager.getRepositories();

            if (!repositories.isEmpty()) {
                // Tomamos la rama del primer repositorio (usualmente el root)
                var repo = repositories.iterator().next();
                String branchName = repo.getCurrentBranchName();
                if (branchName != null && !branchName.isEmpty()) {
                    LOG.fine("[FararoniBridge] Git branch detected: " + branchName);
                    return branchName;
                }
                return "detached-or-unknown";
            }
        } catch (Exception e) {
            LOG.fine("[FararoniBridge] No se pudo obtener la rama de Git: " + e.getMessage());
        }
        return "no-vcs";
    }

    /**
     * Envía el payload al Gateway de forma asíncrona.
     *
     * <p>Fire-and-Forget: Solo espera el 202 Accepted, no la respuesta.</p>
     */
    private void sendToGateway(JsonObject payload) {
        totalSent.incrementAndGet();

        String jsonBody = gson.toJson(payload);

        // Usar URL dinamica desde configuracion
        String gatewayUrl = FararoniSettingsState.getInstance().gatewayUrl;

        Request request = new Request.Builder()
            .url(gatewayUrl)
            .post(RequestBody.create(jsonBody, JSON))
            .addHeader("X-Fararoni-Client", "IntelliJ-Plugin")
            .build();

        LOG.fine("[FararoniBridge] Sending to Gateway: " + truncate(jsonBody, 100));

        getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    if (response.code() == 202) {
                        totalAccepted.incrementAndGet();
                        LOG.info("[FararoniBridge] Request accepted by Gateway");
                    } else {
                        totalRejected.incrementAndGet();
                        LOG.warning("[FararoniBridge] Gateway rejected: HTTP " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                totalRejected.incrementAndGet();
                LOG.log(Level.WARNING, "[FararoniBridge] Failed to reach Gateway", e);
            }
        });
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    public long getTotalSent() { return totalSent.get(); }
    public long getTotalAccepted() { return totalAccepted.get(); }
    public long getTotalRejected() { return totalRejected.get(); }

    /**
     * Obtiene instancia del servicio para un proyecto.
     *
     * @param project el proyecto
     * @return instancia del FararoniBridge
     */
    public static FararoniBridge getInstance(Project project) {
        return project.getService(FararoniBridge.class);
    }
}
