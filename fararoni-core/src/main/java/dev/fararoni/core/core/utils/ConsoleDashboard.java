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
package dev.fararoni.core.core.utils;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.bus.SovereignBusFactory;
import dev.fararoni.core.core.skills.bridge.CapabilityManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConsoleDashboard {
    private static final Logger LOG = Logger.getLogger(ConsoleDashboard.class.getName());

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";

    private static final String CLEAR_SCREEN = "\033[H\033[2J";
    private static final String CURSOR_HOME = "\033[H";

    private static final int REFRESH_INTERVAL_SECONDS = 2;

    private final ScheduledExecutorService executor;

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    private CapabilityManager capabilityManager;

    private boolean useFullClear = true;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ConsoleDashboard() {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConsoleDashboard-Refresh");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (enabled.compareAndSet(false, true)) {
            LOG.info("[DASHBOARD] Iniciando ConsoleDashboard (refresco cada " +
                REFRESH_INTERVAL_SECONDS + "s)...");

            executor.scheduleAtFixedRate(
                this::printDashboard,
                0,
                REFRESH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );
        }
    }

    public void stop() {
        if (enabled.compareAndSet(true, false)) {
            executor.shutdown();
            LOG.info("[DASHBOARD] ConsoleDashboard detenido.");
        }
    }

    public void setCapabilityManager(CapabilityManager manager) {
        this.capabilityManager = manager;
    }

    public void setUseFullClear(boolean fullClear) {
        this.useFullClear = fullClear;
    }

    public void printOnce() {
        printDashboard();
    }

    public String generatePlainText() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== FARARONI AGENT CORE | BUS MONITOR ===\n");
        sb.append("Timestamp: ").append(LocalDateTime.now().format(TIME_FORMAT)).append("\n");
        sb.append("-".repeat(60)).append("\n");
        sb.append(String.format("%-25s | %-10s | %-8s | %-10s\n",
            "BUS DETECTADO", "PRIORIDAD", "ESTADO", "MODO"));
        sb.append("-".repeat(60)).append("\n");

        List<SovereignEventBus> buses = SovereignBusFactory.getAllDetectedBuses();
        SovereignEventBus activeBus = SovereignBusFactory.getActiveBus();

        for (SovereignEventBus bus : buses) {
            String name = bus.getClass().getSimpleName();
            int priority = bus.getPriority();
            boolean isAlive = bus.isAvailable();
            boolean isDominant = (bus == activeBus);

            String statusStr = isAlive ? "ONLINE" : "OFFLINE";
            String modeStr = isDominant ? "[DOMINANTE]" : "Standby";

            sb.append(String.format("%-25s | %-10d | %-8s | %-10s\n",
                name, priority, statusStr, modeStr));
        }

        sb.append("-".repeat(60)).append("\n");

        return sb.toString();
    }

    private void printDashboard() {
        if (!enabled.get()) {
            return;
        }

        try {
            if (useFullClear) {
                System.out.print(CLEAR_SCREEN);
            } else {
                System.out.print(CURSOR_HOME);
            }
            System.out.flush();

            System.out.println(BOLD + CYAN + "=== FARARONI AGENT CORE | INFRAESTRUCTURA DE 200M ===" + RESET);
            System.out.println("Status: " + GREEN + "OPERATIONAL" + RESET +
                " | Time: " + DIM + LocalDateTime.now().format(TIME_FORMAT) + RESET);
            System.out.println("-".repeat(60));

            System.out.printf(BOLD + "%-25s | %-10s | %-12s | %-10s\n" + RESET,
                "BUS DETECTADO", "PRIORIDAD", "ESTADO", "MODO");
            System.out.println("-".repeat(60));

            List<SovereignEventBus> buses = SovereignBusFactory.getAllDetectedBuses();
            SovereignEventBus activeBus = SovereignBusFactory.getActiveBus();

            if (buses.isEmpty()) {
                System.out.println(YELLOW + "(No hay buses detectados - ejecute init())" + RESET);
            } else {
                for (SovereignEventBus bus : buses) {
                    printBusRow(bus, activeBus);
                }
            }

            System.out.println("-".repeat(60));

            if (capabilityManager != null) {
                printSidecarSection();
            }

            System.out.println(YELLOW + "Tip: " + RESET + "Usa " + CYAN + "/reconfig" + RESET +
                " para forzar conmutacion en caliente.");
            System.out.println(DIM + "Dashboard actualizado cada " + REFRESH_INTERVAL_SECONDS + "s" + RESET);
        } catch (Exception e) {
            LOG.fine("[DASHBOARD] Error en rendering: " + e.getMessage());
        }
    }

    private void printBusRow(SovereignEventBus bus, SovereignEventBus activeBus) {
        String name = bus.getClass().getSimpleName();
        int priority = bus.getPriority();
        boolean isAlive = bus.isAvailable();
        boolean isDominant = (bus == activeBus);

        String statusStr = isAlive ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
        String modeStr = isDominant ? YELLOW + "[DOMINANTE]" + RESET : DIM + "Standby" + RESET;

        if (priority >= 100) {
            name = CYAN + "★ " + name + RESET;
        }

        System.out.printf("%-34s | %-10d | %-21s | %-19s\n",
            name, priority, statusStr, modeStr);
    }

    private void printSidecarSection() {
        System.out.println();
        System.out.println(BOLD + "SIDECARS CONECTADOS:" + RESET);
        System.out.println("-".repeat(60));

        var sidecars = capabilityManager.getAllSidecars();
        if (sidecars.isEmpty()) {
            System.out.println(DIM + "(Ningun sidecar conectado via handshake)" + RESET);
        } else {
            for (var entry : sidecars.entrySet()) {
                var info = entry.getValue();
                System.out.printf("  %s%-15s%s | %d herramientas | v%s\n",
                    GREEN, info.sidecarId(), RESET,
                    info.capabilities().size(),
                    info.version());
            }
        }
        System.out.println("-".repeat(60));
    }
}
