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
package dev.fararoni.core.core.skills;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Logger;

public class HardwareTelemetryScanner {
    private static final Logger LOG = Logger.getLogger(HardwareTelemetryScanner.class.getName());
    private static final long CACHE_TTL_MS = 10_000;

    private volatile double cachedLoadFactor = 1.0;
    private volatile long lastScanTimestamp = 0;
    private final int availableProcessors = Runtime.getRuntime().availableProcessors();

    public double getLoadFactor() {
        long now = System.currentTimeMillis();
        if (now - lastScanTimestamp < CACHE_TTL_MS) {
            return cachedLoadFactor;
        }

        try {
            double loadAvg1m = readLoadAverage();
            double loadRatio = loadAvg1m / availableProcessors;

            double factor;
            if (loadRatio > 2.0) {
                factor = 2.0;
            } else if (loadRatio > 1.0) {
                factor = 1.5;
            } else if (loadRatio > 0.7) {
                factor = 1.3;
            } else {
                factor = 1.0;
            }

            cachedLoadFactor = factor;
            lastScanTimestamp = now;

            if (factor > 1.0) {
                LOG.info("[BACKPRESSURE] loadAvg1m=" + String.format("%.2f", loadAvg1m)
                    + " cores=" + availableProcessors
                    + " ratio=" + String.format("%.2f", loadRatio)
                    + " factor=" + factor);
            }

            return factor;
        } catch (Exception e) {
            LOG.fine("[BACKPRESSURE] No se pudo leer carga del sistema: " + e.getMessage());
            cachedLoadFactor = 1.0;
            lastScanTimestamp = now;
            return 1.0;
        }
    }

    private double readLoadAverage() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("linux")) {
            ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "vm.loadavg");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                if (line != null) {
                    String cleaned = line.replace("{", "").replace("}", "").trim();
                    String firstValue = cleaned.split("\\s+")[0];
                    return Double.parseDouble(firstValue);
                }
            }
        }
        return 0.0;
    }
}
