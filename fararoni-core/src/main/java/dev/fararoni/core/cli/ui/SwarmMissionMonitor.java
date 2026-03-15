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
package dev.fararoni.core.cli.ui;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.mission.domain.MissionProgressPayload;
import dev.fararoni.core.core.mission.engine.SovereignMissionEngine;
import dev.fararoni.core.ui.OutputCoordinator;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SwarmMissionMonitor {
    private static final Logger LOG = Logger.getLogger(SwarmMissionMonitor.class.getName());

    private final SovereignEventBus bus;
    private final Map<String, Boolean> activeMissions = new ConcurrentHashMap<>();

    private volatile OutputCoordinator outputCoordinator;

    private static final int MAX_SUB_LINES = 5;
    private final Deque<String> renderQueue = new ConcurrentLinkedDeque<>();
    private volatile boolean isFirstRender = true;
    private volatile boolean isMissionActive = false;
    private volatile int lastRenderedLines = 0;

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String CLEAR_LINE = "\u001B[2K";
    private static final String CURSOR_UP = "\u001B[A";

    public SwarmMissionMonitor(SovereignEventBus bus) {
        this.bus = bus;
    }

    public void setOutputCoordinator(OutputCoordinator coordinator) {
        this.outputCoordinator = coordinator;
    }

    public void startListening() {
        bus.subscribe(
            SovereignMissionEngine.TOPIC_MISSION_PROGRESS,
            MissionProgressPayload.class,
            this::handleProgress
        );

        bus.subscribe(
            SovereignMissionEngine.TOPIC_MISSION_COMPLETED,
            SovereignMissionEngine.MissionCompletedPayload.class,
            this::handleCompletion
        );

        bus.subscribe(
            SovereignMissionEngine.TOPIC_MISSION_FAILED,
            SovereignMissionEngine.MissionFailedPayload.class,
            this::handleFailure
        );

        LOG.info("[ETAPA3] SwarmMissionMonitor started listening");
        System.out.println(DIM + "[SWARM-MONITOR] Escuchando telemetría de misiones..." + RESET);
    }

    public void stopListening() {
        activeMissions.clear();
        LOG.info("[ETAPA3] SwarmMissionMonitor stopped");
    }

    private synchronized void handleProgress(SovereignEnvelope<MissionProgressPayload> envelope) {
        isMissionActive = true;
        MissionProgressPayload payload = envelope.payload();
        activeMissions.put(payload.missionId(), true);

        String role = payload.agentRole().toUpperCase();
        String statusColor = getStatusColor(payload.status());

        String retryBadge = "";
        if (payload.retryCount() > 0) {
            retryBadge = RED + " [Loop:" + payload.retryCount() + "]" + RESET;
        }

        String msg = payload.message();
        if (msg.length() > 55) {
            msg = msg.substring(0, 52) + "...";
        }

        String formattedLine = String.format("%s%s%s [%s]: %s%s",
            statusColor, payload.status(), RESET,
            role,
            msg,
            retryBadge
        );

        renderQueue.addLast(formattedLine);
        if (renderQueue.size() > MAX_SUB_LINES) {
            renderQueue.removeFirst();
        }

        renderUI();
    }

    private synchronized void renderUI() {
        if (PulseTelemetry.hasActiveInstance()) {
            for (String line : renderQueue) {
                PulseTelemetry.sendSubLine(line);
            }
            isFirstRender = false;
            return;
        }

        StringBuilder sb = new StringBuilder();

        if (!isFirstRender && lastRenderedLines > 0) {
            for (int i = 0; i < lastRenderedLines; i++) {
                sb.append(CURSOR_UP);
            }
        }

        int lineCount = 0;
        for (String line : renderQueue) {
            sb.append("\r").append(CLEAR_LINE).append("    └─ ").append(line).append("\n");
            lineCount++;
        }

        lastRenderedLines = lineCount;
        isFirstRender = false;

        if (outputCoordinator != null) {
            outputCoordinator.printAnsiBlock(sb.toString());
        } else {
            System.out.print(sb.toString());
            System.out.flush();
        }
    }

    private synchronized void handleCompletion(SovereignEnvelope<SovereignMissionEngine.MissionCompletedPayload> envelope) {
        if (!isMissionActive) return;

        SovereignMissionEngine.MissionCompletedPayload payload = envelope.payload();
        activeMissions.remove(payload.executionId());

        clearProgressBlock();

        String missionIdShort = shortenMissionId(payload.executionId());
        String completionMsg = String.format("%s%s  └─ [SWARM:%s] MISION COMPLETADA%s\n",
            GREEN, BOLD, missionIdShort, RESET);

        if (outputCoordinator != null) {
            outputCoordinator.printAnsiBlock(completionMsg);
        } else {
            System.out.print(completionMsg);
        }

        resetState();
    }

    private synchronized void handleFailure(SovereignEnvelope<SovereignMissionEngine.MissionFailedPayload> envelope) {
        if (!isMissionActive) return;

        SovereignMissionEngine.MissionFailedPayload payload = envelope.payload();
        activeMissions.remove(payload.executionId());

        clearProgressBlock();

        String missionIdShort = shortenMissionId(payload.executionId());
        String failMsg = String.format("%s%s  └─ [SWARM:%s] MISION FALLIDA: %s%s\n",
            RED, BOLD, missionIdShort, payload.reason(), RESET);

        if (outputCoordinator != null) {
            outputCoordinator.printAnsiBlock(failMsg);
        } else {
            System.out.print(failMsg);
        }

        resetState();
    }

    private void clearProgressBlock() {
        if (lastRenderedLines > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lastRenderedLines; i++) {
                sb.append(CURSOR_UP).append(CLEAR_LINE).append("\r");
            }
            if (outputCoordinator != null) {
                outputCoordinator.printAnsiBlock(sb.toString());
            } else {
                System.out.print(sb.toString());
            }
        }
    }

    private void resetState() {
        renderQueue.clear();
        isFirstRender = true;
        isMissionActive = false;
        lastRenderedLines = 0;
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "SUCCESS" -> GREEN;
            case "FAILED", "ERROR" -> RED;
            case "DISPATCHING" -> CYAN;
            case "COMPENSATING" -> YELLOW;
            default -> YELLOW;
        };
    }

    private String getAgentBadge(String role) {
        return switch (role.toLowerCase()) {
            case "commander"  -> "[CMD]";
            case "intel"      -> "[INT]";
            case "strategist" -> "[STR]";
            case "blueprint"  -> "[BLU]";
            case "builder"    -> "[BLD]";
            case "sentinel"   -> "[SNT]";
            case "operator"   -> "[OPR]";
            case "system"     -> "[SYS]";
            default           -> "[AGT]";
        };
    }

    private String shortenMissionId(String missionId) {
        if (missionId == null) return "???";
        if (missionId.length() <= 8) return missionId;
        return missionId.substring(0, 8);
    }

    public int getActiveMissionCount() {
        return activeMissions.size();
    }

    public boolean hasActiveMissions() {
        return !activeMissions.isEmpty();
    }
}
