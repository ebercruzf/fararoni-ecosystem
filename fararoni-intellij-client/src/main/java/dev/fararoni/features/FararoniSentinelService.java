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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class FararoniSentinelService implements Disposable {

    private static final Logger LOG = Logger.getLogger(FararoniSentinelService.class.getName());

    /** Intervalo del heartbeat en segundos */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /** Numero de conexiones en el pool */
    private static final int CONNECTION_POOL_SIZE = 5;

    /** Tiempo de vida de conexiones en minutos */
    private static final int CONNECTION_KEEP_ALIVE_MINUTES = 5;

    private final Project project;
    private final OkHttpClient httpClient;
    private final FararoniCallbackServer callbackServer;
    private final ScheduledExecutorService heartbeatScheduler;
    private volatile boolean running = false;

    /**
     * Crea el servicio Sentinel para el proyecto dado.
     *
     * <p>Inicializa el cliente HTTP con dispatcher dedicado,
     * inicia el servidor de callback y programa el heartbeat.</p>
     *
     * @param project el proyecto de IntelliJ
     */
    public FararoniSentinelService(Project project) {
        this.project = project;

        LOG.info("[Sentinel] Inicializando Fararoni Sentinel Service...");

        // 1. Dispatcher dedicado para que OkHttp NUNCA compita con IntelliJ
        ExecutorService networkExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Fararoni-Sentinel-Network");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });

        Dispatcher dispatcher = new Dispatcher(networkExecutor);
        dispatcher.setMaxRequests(64);
        dispatcher.setMaxRequestsPerHost(10);

        // 2. Connection Pool para reutilizar conexiones
        ConnectionPool connectionPool = new ConnectionPool(
            CONNECTION_POOL_SIZE,
            CONNECTION_KEEP_ALIVE_MINUTES,
            TimeUnit.MINUTES
        );

        // 3. Cliente HTTP robusto
        this.httpClient = new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

        // 4. Scheduler para heartbeat
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Fararoni-Sentinel-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        // 5. Obtener CallbackServer (ya registrado como servicio)
        this.callbackServer = FararoniCallbackServer.getInstance(project);

        // 6. Iniciar todo
        start();

        LOG.info("[Sentinel] Servicio inicializado para proyecto: " + project.getName());
    }

    /**
     * Inicia el servicio: CallbackServer y Heartbeat.
     */
    private void start() {
        if (running) return;
        running = true;

        // Iniciar CallbackServer si no esta corriendo
        if (callbackServer != null && !callbackServer.isRunning()) {
            int port = FararoniSettingsState.getInstance().callbackPort;
            boolean started = callbackServer.start(port);
            if (started) {
                LOG.info("[Sentinel] CallbackServer activo en puerto: " + port);
            } else {
                LOG.warning("[Sentinel] Error iniciando CallbackServer en puerto: " + port);
            }
        }

        // Iniciar Heartbeat
        startHeartbeat();

        LOG.info("[Sentinel] Heartbeat iniciado: Manteniendo conexion con Gateway");
    }

    /**
     * Inicia el heartbeat que mantiene los sockets calientes.
     *
     * <p>Cada 30 segundos hace un GET al /health del Gateway.</p>
     */
    private void startHeartbeat() {
        String gatewayHealth = FararoniSettingsState.getInstance().gatewayUrl
            .replace("/inbound", "/health");

        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!running) return;

            try {
                Request ping = new Request.Builder()
                    .url(gatewayHealth)
                    .header("X-Sentinel-Heartbeat", "true")
                    .header("X-Fararoni-Client", "IntelliJ-Plugin")
                    .get()
                    .build();

                try (Response response = httpClient.newCall(ping).execute()) {
                    if (response.isSuccessful()) {
                        LOG.fine("[Sentinel] Heartbeat OK - Gateway healthy");
                    } else {
                        LOG.warning("[Sentinel] Heartbeat WARN - Gateway: HTTP " + response.code());
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.FINE, "[Sentinel] Heartbeat MISS - Gateway no disponible", e);
            }
        }, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Detiene el servicio de forma ordenada.
     */
    public void stop() {
        if (!running) return;
        running = false;

        LOG.info("[Sentinel] Deteniendo servicio...");

        // Detener heartbeat
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Cerrar cliente HTTP
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();

        LOG.info("[Sentinel] Servicio detenido");
    }

    /**
     * Reinicia el CallbackServer con un nuevo puerto.
     *
     * <p>Usado cuando el usuario cambia el puerto en Settings.</p>
     */
    public void restartCallbackServer() {
        if (callbackServer == null) return;

        int newPort = FararoniSettingsState.getInstance().callbackPort;
        int currentPort = callbackServer.getPort();

        if (currentPort != newPort) {
            LOG.info("[Sentinel] Reiniciando CallbackServer: " + currentPort + " -> " + newPort);
            callbackServer.stop();
            boolean started = callbackServer.start(newPort);
            if (started) {
                LOG.info("[Sentinel] CallbackServer reiniciado en puerto: " + newPort);
            }
        }
    }

    /**
     * Retorna el cliente HTTP robusto con dispatcher dedicado.
     *
     * <p>Este cliente debe usarse para TODAS las comunicaciones
     * con el Gateway para garantizar baja latencia.</p>
     *
     * @return cliente HTTP optimizado
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Retorna el servidor de callback.
     *
     * @return el CallbackServer
     */
    public FararoniCallbackServer getCallbackServer() {
        return callbackServer;
    }

    /**
     * Verifica si el servicio esta corriendo.
     *
     * @return true si esta activo
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Obtiene instancia del servicio para un proyecto.
     *
     * @param project el proyecto
     * @return instancia del SentinelService
     */
    public static FararoniSentinelService getInstance(Project project) {
        return project.getService(FararoniSentinelService.class);
    }

    @Override
    public void dispose() {
        stop();
    }
}
