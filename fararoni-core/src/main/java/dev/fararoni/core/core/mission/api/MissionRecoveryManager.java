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
package dev.fararoni.core.core.mission.api;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine;
import dev.fararoni.core.core.mission.model.MissionState;
import dev.fararoni.core.core.mission.persistence.JdbcMissionStateRepository;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class MissionRecoveryManager {
    private static final Logger LOG = Logger.getLogger(MissionRecoveryManager.class.getName());

    private static final java.time.Duration STALE_THRESHOLD = java.time.Duration.ofMinutes(10);

    private static final long PATROL_INTERVAL_MINUTES = 5;

    private final JdbcMissionStateRepository stateRepo;
    private final SovereignEventBus bus;
    private SovereignMissionEngine engine;
    private java.util.concurrent.ScheduledExecutorService scheduler;

    public MissionRecoveryManager(JdbcMissionStateRepository stateRepo, SovereignEventBus bus) {
        this.stateRepo = stateRepo;
        this.bus = bus;
        this.engine = null;
        this.scheduler = null;

        LOG.info("MissionRecoveryManager initialized");
    }

    public void setEngine(SovereignMissionEngine engine) {
        this.engine = engine;
    }

    public List<MissionState> inspectOrphanedMissions() {
        LOG.info("Inspecting orphaned missions...");

        List<MissionState> orphans = stateRepo.findOrphanedMissions();

        if (orphans.isEmpty()) {
            LOG.info("No orphaned missions found");
        } else {
            LOG.info("Found " + orphans.size() + " orphaned missions:");
            for (MissionState state : orphans) {
                LOG.info("  - " + state.executionId() +
                         " (mission=" + state.missionId() +
                         ", step=" + state.currentStepId() +
                         ", iterations=" + state.iterations() + ")");
            }
        }

        return orphans;
    }

    public String getStats() {
        List<MissionState> orphans = stateRepo.findOrphanedMissions();
        String poolStats = stateRepo.getPoolStats();

        return String.format(
            "MissionRecovery[orphans=%d, %s, healthy=%s]",
            orphans.size(),
            poolStats,
            stateRepo.isHealthy()
        );
    }

    public boolean resumeMissionManually(String executionId) {
        LOG.info("Manual resume request: " + executionId);

        var state = stateRepo.findById(executionId);
        if (state.isEmpty()) {
            LOG.warning("Mission not found: " + executionId);
            return false;
        }

        MissionState mission = state.get();
        if (mission.isTerminated()) {
            LOG.warning("Cannot resume terminated mission: " + executionId);
            return false;
        }

        publishResumeRequest(executionId);
        return true;
    }

    public int autoResumeAll() {
        LOG.info("Starting auto-resume of orphaned missions...");

        List<MissionState> orphans = inspectOrphanedMissions();
        int resumed = 0;

        for (MissionState state : orphans) {
            try {
                publishResumeRequest(state.executionId());
                resumed++;
                LOG.info("Published resume for: " + state.executionId());
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                    "Failed to resume mission: " + state.executionId(), e);
            }
        }

        LOG.info("Auto-resume completed: " + resumed + "/" + orphans.size() + " missions");
        return resumed;
    }

    public boolean markAsCrashed(String executionId, String reason) {
        LOG.info("Marking mission as CRASHED: " + executionId);

        var state = stateRepo.findById(executionId);
        if (state.isEmpty()) {
            LOG.warning("Mission not found: " + executionId);
            return false;
        }

        MissionState mission = state.get();
        if (mission.isTerminated()) {
            LOG.warning("Mission already terminated: " + executionId);
            return false;
        }

        MissionState crashed = mission.terminate(MissionState.ExecutionStatus.CRASHED);
        stateRepo.save(crashed);

        LOG.info("Mission marked as CRASHED: " + executionId +
                 " reason=" + reason);
        return true;
    }

    private void publishResumeRequest(String executionId) {
        SovereignMissionEngine.MissionResumePayload payload =
            new SovereignMissionEngine.MissionResumePayload(executionId);

        SovereignEnvelope<SovereignMissionEngine.MissionResumePayload> envelope =
            SovereignEnvelope.createSecure(
                "system",
                "recovery-manager",
                null,
                SovereignMissionEngine.TOPIC_MISSION_RESUME,
                payload
            );

        bus.publish(SovereignMissionEngine.TOPIC_MISSION_RESUME, envelope);
    }

    public void startPatrol() {
        LOG.info("Iniciando patrulla de recuperacion de desastres...");
        System.out.println("[RECOVERY] Servicio de Recuperacion de Desastres iniciado");

        this.scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "swarm-reaper-thread");
            t.setDaemon(true);
            return t;
        });

        performRecoverySweep();

        scheduler.scheduleAtFixedRate(
            this::performRecoverySweep,
            PATROL_INTERVAL_MINUTES,
            PATROL_INTERVAL_MINUTES,
            java.util.concurrent.TimeUnit.MINUTES
        );

        LOG.info("Patrol scheduled every " + PATROL_INTERVAL_MINUTES + " minutes");
    }

    private void performRecoverySweep() {
        try {
            java.time.Instant threshold = java.time.Instant.now().minus(STALE_THRESHOLD);
            List<MissionState> zombies = stateRepo.findStaleMissions(threshold);

            if (zombies.isEmpty()) {
                return;
            }

            LOG.warning(() -> String.format(
                "Detectadas %d misiones ZOMBIES. Iniciando protocolo Lazaro...",
                zombies.size()
            ));
            System.out.println("[RECOVERY] [ALERT] Detectadas " + zombies.size() +
                " misiones zombies (>10min sin actividad)");

            for (MissionState zombie : zombies) {
                rescueMission(zombie);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error critico durante patrulla: " + e.getMessage(), e);
        }
    }

    private void rescueMission(MissionState state) {
        LOG.info(() -> "Evaluando mision: " + state.executionId() +
            " atascada en nodo: " + state.currentStepId());

        try {
            if (engine != null) {
                MissionState recoveringState = state.recordStepAndAdvance(
                    state.currentStepId(),
                    state.payloadJson() != null ? state.payloadJson() : "capability",
                    "RECOVERY",
                    state.currentStepId(),
                    true
                );
                stateRepo.save(recoveringState);

                engine.resumeRecoveredMission(recoveringState);
                LOG.info("Mision " + state.executionId() + " reinyectada exitosamente");
            } else {
                publishResumeRequest(state.executionId());
                LOG.info("Mision " + state.executionId() + " publicada para reanudacion");
            }
        } catch (Exception e) {
            LOG.severe("Fallo al rescatar mision " + state.executionId() +
                ": " + e.getMessage());

            if (engine != null) {
                engine.forceFailMission(state,
                    "Fallo catastrófico en recuperación de desastres: " + e.getMessage());
            } else {
                markAsCrashed(state.executionId(),
                    "Irrecuperable: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            LOG.info("Patrol scheduler stopped");
        }
    }
}
