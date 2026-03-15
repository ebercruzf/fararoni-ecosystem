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
package dev.fararoni.core.core.diagnostics;

import dev.fararoni.core.core.sati.SATIRouter;
import dev.fararoni.core.core.sati.SidecarNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * @since 1.0.0
 */
public class SatiHealthMonitor {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SatiHealthMonitor.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SATIRouter router;
    private ScheduledExecutorService executor;

    private int reportIntervalSeconds = 30;
    private int pruneIntervalSeconds = 60;
    private boolean printToConsole = true;
    private boolean alertOnNoHealthy = true;

    public SatiHealthMonitor(SATIRouter router) {
        this.router = router;
    }

    public SatiHealthMonitor withReportInterval(int seconds) {
        this.reportIntervalSeconds = seconds;
        return this;
    }

    public SatiHealthMonitor withPruneInterval(int seconds) {
        this.pruneIntervalSeconds = seconds;
        return this;
    }

    public SatiHealthMonitor withConsoleOutput(boolean enabled) {
        this.printToConsole = enabled;
        return this;
    }

    public void startReporting() {
        if (executor != null && !executor.isShutdown()) {
            LOG.warn("[SATI-MONITOR] Ya esta corriendo");
            return;
        }

        executor = Executors.newScheduledThreadPool(2, Thread.ofVirtual().factory());

        executor.scheduleAtFixedRate(
            this::printHealthReport,
            5,
            reportIntervalSeconds,
            TimeUnit.SECONDS
        );

        executor.scheduleAtFixedRate(
            router::pruneStaleNodes,
            pruneIntervalSeconds,
            pruneIntervalSeconds,
            TimeUnit.SECONDS
        );

        LOG.info("[SATI-MONITOR] Iniciado. Reporte cada {}s, limpieza cada {}s",
            reportIntervalSeconds, pruneIntervalSeconds);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LOG.info("[SATI-MONITOR] Detenido");
        }
    }

    private void printHealthReport() {
        try {
            String timestamp = LocalDateTime.now().format(TIME_FMT);
            int total = router.getTotalSidecars();
            int healthy = router.getHealthySidecars();
            Optional<String> best = router.getBestSidecar();

            StringBuilder report = new StringBuilder();
            report.append("\n");
            report.append("==================================================\n");
            report.append("  MONITOR DE SOBERANIA FARARONI S.A.T.I.\n");
            report.append("  ").append(timestamp).append("\n");
            report.append("==================================================\n");
            report.append(String.format("  Total: %d | Saludables: %d | Mejor: %s%n",
                total, healthy, best.orElse("NINGUNO")));
            report.append("--------------------------------------------------\n");

            router.getSwarmStatusMap().get("nodes");
            router.printSwarmStatus();

            if (healthy == 0 && total > 0 && alertOnNoHealthy) {
                report.append("\n");
                report.append("  [ALERTA] NO HAY SIDECARS SALUDABLES!\n");
                report.append("  El sistema operara en modo degradado.\n");
                LOG.warn("[SATI-MONITOR] ALERTA: No hay Sidecars saludables disponibles");
            }

            if (total == 0) {
                report.append("\n");
                report.append("  [INFO] No hay Sidecars registrados.\n");
                report.append("  Ejecuta: ./spawn-swarm.sh --sidecars 3\n");
            }

            report.append("==================================================\n");

            if (printToConsole) {
                System.out.print(report);
            }

            LOG.debug("[SATI-MONITOR] Reporte: total={}, healthy={}, best={}",
                total, healthy, best.orElse("none"));
        } catch (Exception e) {
            LOG.error("[SATI-MONITOR] Error generando reporte: {}", e.getMessage());
        }
    }

    public String getHealthReportString() {
        int total = router.getTotalSidecars();
        int healthy = router.getHealthySidecars();
        Optional<String> best = router.getBestSidecar();

        return String.format(
            "SATI Status: total=%d, healthy=%d, best=%s",
            total, healthy, best.orElse("none")
        );
    }

    public boolean isSwarmHealthy() {
        return router.getHealthySidecars() > 0;
    }

    public double getAverageLatency() {
        var status = router.getSwarmStatusMap();
        @SuppressWarnings("unchecked")
        var nodes = (java.util.List<java.util.Map<String, Object>>) status.get("nodes");

        return nodes.stream()
            .filter(n -> Boolean.TRUE.equals(n.get("healthy")))
            .mapToLong(n -> ((Number) n.get("latency_us")).longValue())
            .average()
            .orElse(-1);
    }
}
