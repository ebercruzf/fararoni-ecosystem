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
package dev.fararoni.core.core.swarm;

import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.context.SwarmContext;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;
import dev.fararoni.core.core.swarm.infra.MessageBus;
import dev.fararoni.core.core.swarm.infra.SwarmTransport;
import dev.fararoni.core.core.swarm.infra.SovereignBridgeBus;
import dev.fararoni.core.core.swarm.roles.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class HiveMind {
    private static final Logger LOG = Logger.getLogger(HiveMind.class.getName());

    private final HyperNativeKernel kernel;
    private final SwarmTransport bus;
    private final Path workspace;
    private final IndexStore indexStore;

    private Duration missionTimeout = Duration.ofMinutes(30);
    private boolean failFastEnabled = true;

    private MissionConfig currentMissionConfig = MissionConfig.fullSquad();

    private long missionsExecuted = 0;
    private long missionsFailed = 0;
    private long missionsSucceeded = 0;
    private long missionsTimedOut = 0;

    public HiveMind(HyperNativeKernel kernel, SwarmTransport bus, Path workspace) {
        this(kernel, bus, workspace, null);
    }

    public HiveMind(HyperNativeKernel kernel, SwarmTransport bus, Path workspace, IndexStore indexStore) {
        this.kernel = kernel;
        this.bus = bus;
        this.workspace = workspace != null ? workspace : Path.of(System.getProperty("user.dir"));
        this.indexStore = indexStore;

        this.bus.setPersistenceHook(msg -> {
            LOG.fine(() -> String.format("[PERSIST] %s → %s: %s",
                msg.senderId(), msg.receiverId(), msg.type()));
        });
    }

    public HiveMind(HyperNativeKernel kernel, SwarmTransport bus) {
        this(kernel, bus, Path.of(System.getProperty("user.dir")), null);
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    public HiveMind(HyperNativeKernel kernel) {
        this(kernel, MessageBus.getInstance(), Path.of(System.getProperty("user.dir")), null);
    }

    public HyperNativeKernel getKernel() {
        return kernel;
    }

    public SwarmTransport getBus() {
        return bus;
    }

    public MissionResult executeMission(String userRequest) {
        preEvaluateDefcon(userRequest);
        return executeMissionWithConfig(userRequest, currentMissionConfig);
    }

    public MissionResult executeMissionWithConfig(String userRequest, MissionConfig config) {
        this.currentMissionConfig = config;
        String missionId = "M-" + Instant.now().getEpochSecond();
        Instant startTime = Instant.now();

        LOG.info(() -> "========================================");
        LOG.info(() -> "INICIANDO MISIÓN: " + missionId);
        LOG.info(() -> config.describe());
        LOG.info(() -> "Request: " + truncate(userRequest, 100));
        LOG.info(() -> "========================================");

        missionsExecuted++;

        bus.setCurrentMissionId(missionId);

        try {
            MissionResult result = SwarmContext.callWith(kernel, bus, workspace, missionId, config, indexStore, () -> {
                return executeWithinContext(missionId, userRequest, startTime);
            });

            if (result.isSuccess()) {
                bus.clearMissionJournal();
            }

            return result;
        } catch (Exception e) {
            missionsFailed++;
            LOG.severe(() -> String.format(
                "MISIÓN %s FALLÓ: %s", missionId, e.getMessage()));

            return new MissionResult(
                missionId,
                MissionStatus.FAILED,
                null,
                e.getMessage(),
                Duration.between(startTime, Instant.now())
            );
        }
    }

    private MissionResult executeWithinContext(String missionId, String userRequest, Instant startTime) {
        System.out.println("[DEBUG-HIVE] Iniciando executeWithinContext...");

        List<SwarmAgent> agents = createAgents();
        System.out.println("[DEBUG-HIVE] Agentes creados: " + agents.size());

        for (SwarmAgent agent : agents) {
            bus.register(agent.getAgentId());
        }
        System.out.println("[DEBUG-HIVE] Pre-registro completado: " + bus.getRegisteredAgents());
        LOG.info(() -> "[HIVE] Pre-registro completado: " + bus.getRegisteredAgents());

        try (var scope = StructuredTaskScope.open(
                Joiner.awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withTimeout(missionTimeout))) {
            for (SwarmAgent agent : agents) {
                scope.fork(agent);
            }

            System.out.println("[DEBUG-HIVE] LA COLMENA HA DESPERTADO (" + agents.size() + " agentes)");
            LOG.info(() -> "LA COLMENA HA DESPERTADO (" + agents.size() + " agentes)");

            System.out.println("[DEBUG-HIVE] Enviando USER_REQUEST al COMMANDER...");
            bus.send(SwarmMessage.userRequest(userRequest));
            System.out.println("[DEBUG-HIVE] USER_REQUEST enviado. Esperando join()...");

            scope.join();
            System.out.println("[DEBUG-HIVE] scope.join() completado!");

            missionsSucceeded++;
            String result = collectFinalResult();

            LOG.info(() -> "========================================");
            LOG.info(() -> "MISIÓN " + missionId + " COMPLETADA");
            LOG.info(() -> "========================================");

            return new MissionResult(
                missionId,
                MissionStatus.SUCCESS,
                result,
                null,
                Duration.between(startTime, Instant.now())
            );
        } catch (StructuredTaskScope.TimeoutException e) {
            LOG.warning(() -> String.format(
                "MISIÓN %s ABORTADA POR TIMEOUT (%d min limit)",
                missionId, missionTimeout.toMinutes()));
            stopAllAgents(agents);
            missionsTimedOut++;

            return new MissionResult(
                missionId,
                MissionStatus.TIMEOUT,
                collectPartialResult(),
                "Misión abortada por Timeout (" + missionTimeout.toMinutes() + " min limit)",
                Duration.between(startTime, Instant.now())
            );
        } catch (StructuredTaskScope.FailedException e) {
            handleSwarmCollapse(missionId, e.getCause());
            missionsFailed++;

            return new MissionResult(
                missionId,
                MissionStatus.FAILED,
                null,
                e.getCause() != null ? e.getCause().getMessage() : e.getMessage(),
                Duration.between(startTime, Instant.now())
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new MissionResult(
                missionId,
                MissionStatus.INTERRUPTED,
                null,
                "Mission interrupted",
                Duration.between(startTime, Instant.now())
            );
        }
    }

    public java.util.concurrent.CompletableFuture<MissionResult> executeMissionAsync(String userRequest) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
            () -> executeMission(userRequest));
    }

    public MissionResult resumeMission(String missionId) {
        LOG.info(() -> "========================================");
        LOG.info(() -> "REANUDANDO MISIÓN: " + missionId);
        LOG.info(() -> "========================================");

        bus.replayHistory(missionId);

        Instant startTime = Instant.now();
        missionsExecuted++;

        try {
            return SwarmContext.callWith(kernel, bus, workspace, missionId, currentMissionConfig, indexStore, () -> {
                return executeWithinContext(missionId, "[RESUMED]", startTime);
            });
        } catch (Exception e) {
            missionsFailed++;
            return new MissionResult(
                missionId,
                MissionStatus.FAILED,
                null,
                "Resume failed: " + e.getMessage(),
                Duration.between(startTime, Instant.now())
            );
        }
    }

    protected List<SwarmAgent> createAgents() {
        return createAgents(currentMissionConfig);
    }

    protected List<SwarmAgent> createAgents(MissionConfig config) {
        List<SwarmAgent> agents = new ArrayList<>();

        agents.add(new CommanderAgent());
        agents.add(new BlueprintAgent());
        agents.add(new BuilderAgent());
        agents.add(new OperatorAgent());

        if (config.enableAnalyst()) {
            agents.add(new IntelAgent());
            LOG.info(() -> "[HIVE] INTEL activado");
        }
        if (config.enableArchitect()) {
            agents.add(new StrategistAgent());
            LOG.info(() -> "[HIVE] STRATEGIST activado");
        }
        if (config.enableQA()) {
            agents.add(new SentinelAgent());
            LOG.info(() -> "[HIVE] SENTINEL activado");
        }

        LOG.info(() -> String.format("[HIVE] %s - %d agentes desplegados",
            config.level().getDisplay(), agents.size()));

        return agents;
    }

    public void setMissionConfig(MissionConfig config) {
        this.currentMissionConfig = config != null ? config : MissionConfig.fullSquad();
        LOG.info(() -> "[HIVE] Configuración actualizada: " + this.currentMissionConfig.describe());
    }

    public MissionConfig getMissionConfig() {
        return currentMissionConfig;
    }

    protected void addAgent(SwarmAgent agent) {
        bus.register(agent.getAgentId());
    }

    private void handleSwarmCollapse(String missionId, Throwable t) {
        LOG.severe(() -> "FALLO CRÍTICO EN COLMENA: " + (t != null ? t.getMessage() : "causa desconocida"));
        LOG.info(() -> "Recuperando caja negra...");

        bus.dumpLogs(missionId);
    }

    private void stopAllAgents(List<SwarmAgent> agents) {
        for (SwarmAgent agent : agents) {
            agent.stop();
        }
    }

    private String collectFinalResult() {
        return "Mission completed successfully";
    }

    private String collectPartialResult() {
        return "Partial result (mission incomplete)";
    }

    public void setMissionTimeout(Duration timeout) {
        this.missionTimeout = timeout;
    }

    public void setFailFastEnabled(boolean enabled) {
        this.failFastEnabled = enabled;
    }

    public HiveMetrics getMetrics() {
        return new HiveMetrics(
            missionsExecuted,
            missionsSucceeded,
            missionsFailed,
            missionsTimedOut,
            bus.getMetrics()
        );
    }

    private void preEvaluateDefcon(String request) {
        if (request == null || request.isBlank()) {
            this.currentMissionConfig = MissionConfig.fastMode();
            return;
        }

        String lower = request.toLowerCase();

        if (containsAny(lower,
                "arquitectura", "architecture", "sistema completo", "full system",
                "base de datos", "database", "sql", "mongodb", "postgresql",
                "autenticación", "authentication", "auth", "login", "jwt", "oauth",
                "pagos", "payment", "stripe", "paypal", "transacción",
                "seguridad", "security", "encrypt", "cifrado", "ssl", "https",
                "despliegue", "deploy", "kubernetes", "docker", "aws", "gcp", "azure",
                "microservicio", "microservice", "api rest", "graphql")) {
            LOG.info(() -> "[HIVE] Detectada solicitud CRITICA (DEFCON 1)");
            this.currentMissionConfig = MissionConfig.fullSquad();
            return;
        }

        if (containsAny(lower,
                "funcionalidad", "feature", "funcional",
                "refactor", "refactorizar", "reorganizar",
                "componente", "component", "módulo", "module",
                "clase", "class", "servicio", "service",
                "test", "prueba", "testing",
                "validación", "validation", "validar",
                "integración", "integration",
                "múltiples", "varios archivos", "multiple files")) {
            LOG.info(() -> "[HIVE] Detectada solicitud ESTANDAR (DEFCON 3)");
            this.currentMissionConfig = MissionConfig.standardMode();
            return;
        }

        LOG.info(() -> "[HIVE] Solicitud RUTINARIA detectada (DEFCON 5)");
        this.currentMissionConfig = MissionConfig.fastMode();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    public record MissionResult(
        String missionId,
        MissionStatus status,
        String result,
        String errorMessage,
        Duration duration
    ) {
        public boolean isSuccess() {
            return status == MissionStatus.SUCCESS;
        }
    }

    public enum MissionStatus {
        SUCCESS,
        FAILED,
        TIMEOUT,
        INTERRUPTED
    }

    public record HiveMetrics(
        long totalMissions,
        long successfulMissions,
        long failedMissions,
        long timedOutMissions,
        MessageBus.BusMetrics busMetrics
    ) {
        public double successRate() {
            return totalMissions > 0 ? (double) successfulMissions / totalMissions : 0.0;
        }

        public double timeoutRate() {
            return totalMissions > 0 ? (double) timedOutMissions / totalMissions : 0.0;
        }
    }
}
