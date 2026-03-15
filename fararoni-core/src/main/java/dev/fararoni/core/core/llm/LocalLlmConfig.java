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
package dev.fararoni.core.core.llm;

import dev.fararoni.core.core.download.ModelDownloader;
import dev.fararoni.core.core.workspace.WorkspaceManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record LocalLlmConfig(
    Path modelPath,

    int contextLength,

    int maxTokens,

    double temperature,

    int threads,

    int gpuLayers,

    int minFreeRamMb,

    boolean debugLogging
) {
    public static final int DEFAULT_CONTEXT_LENGTH = 2048;

    public static final int DEFAULT_MAX_TOKENS = 1024;

    public static final double DEFAULT_TEMPERATURE = 0.3;

    public static final int DEFAULT_MIN_FREE_RAM_MB = 2048;

    public static final int APPLE_SILICON_GPU_LAYERS = 99;

    public static final int CPU_ONLY_GPU_LAYERS = 0;

    public static final int HARD_LIMIT_CONTEXT = 4096;

    public static final int HARD_LIMIT_MAX_TOKENS = 2048;

    public static final double HARD_LIMIT_TEMPERATURE = 2.0;

    public static final double HARD_LIMIT_TEMPERATURE_MIN = 0.0;

    public static LocalLlmConfig defaults() {
        Path modelPath = WorkspaceManager.getInstance().getModelsDir()
            .resolve(ModelDownloader.MODEL_NAME);

        boolean debug = isDebugMode();

        return new LocalLlmConfig(
            modelPath,
            DEFAULT_CONTEXT_LENGTH,
            DEFAULT_MAX_TOKENS,
            DEFAULT_TEMPERATURE,
            0,
            detectOptimalGpuLayers(),
            DEFAULT_MIN_FREE_RAM_MB,
            debug
        );
    }

    public static LocalLlmConfig fromEnvironment() {
        Path modelPath = WorkspaceManager.getInstance().getModelsDir()
            .resolve(ModelDownloader.MODEL_NAME);

        String envModelPath = System.getenv("FARARONI_MODEL_PATH");
        if (envModelPath != null && !envModelPath.isBlank()) {
            modelPath = Path.of(envModelPath);
        }

        int userContextLength = getEnvInt("FARARONI_CONTEXT_LENGTH", DEFAULT_CONTEXT_LENGTH);
        int userMaxTokens = getEnvInt("FARARONI_MAX_TOKENS", DEFAULT_MAX_TOKENS);
        double userTemperature = getEnvDouble("FARARONI_TEMPERATURE", DEFAULT_TEMPERATURE);
        int threads = getEnvInt("FARARONI_THREADS", 0);
        int gpuLayers = getGpuLayersWithAutoDetect();
        int minRam = getEnvInt("FARARONI_MIN_RAM_MB", DEFAULT_MIN_FREE_RAM_MB);
        boolean debug = isDebugMode();

        int safeContextLength = applySafetyCap(
            userContextLength, HARD_LIMIT_CONTEXT, "FARARONI_CONTEXT_LENGTH");
        int safeMaxTokens = applySafetyCap(
            userMaxTokens, HARD_LIMIT_MAX_TOKENS, "FARARONI_MAX_TOKENS");
        double safeTemperature = applySafetyCapDouble(
            userTemperature, HARD_LIMIT_TEMPERATURE_MIN, HARD_LIMIT_TEMPERATURE, "FARARONI_TEMPERATURE");

        if (safeMaxTokens > safeContextLength) {
            System.err.println("[SAFETY] maxTokens (" + safeMaxTokens +
                ") excede contextLength (" + safeContextLength +
                "). Ajustado a " + (safeContextLength / 2));
            safeMaxTokens = safeContextLength / 2;
        }

        return new LocalLlmConfig(
            modelPath,
            safeContextLength,
            safeMaxTokens,
            safeTemperature,
            threads,
            gpuLayers,
            minRam,
            debug
        );
    }

    public boolean isModelDownloaded() {
        if (!Files.exists(modelPath)) {
            return false;
        }
        try {
            long size = Files.size(modelPath);
            return size >= 1_000_000_000L;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasEnoughRam() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long availableMemoryMb = (maxMemory - totalMemory + freeMemory) / (1024 * 1024);
        return availableMemoryMb >= minFreeRamMb;
    }

    public long getAvailableRamMb() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return (maxMemory - totalMemory + freeMemory) / (1024 * 1024);
    }

    private static boolean isDebugMode() {
        if (Boolean.getBoolean("fararoni.debug")) {
            return true;
        }
        String envDebug = System.getenv("FARARONI_DEBUG");
        return "true".equalsIgnoreCase(envDebug) || "1".equals(envDebug);
    }

    private static int getEnvInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double getEnvDouble(String name, double defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int applySafetyCap(int userValue, int hardLimit, String envVarName) {
        if (userValue <= 0) {
            System.err.println("[SAFETY] " + envVarName + " tiene valor invalido (" +
                userValue + "). Usando limite seguro: " + hardLimit);
            return hardLimit;
        }
        if (userValue > hardLimit) {
            System.err.println("[SAFETY] " + envVarName + " (" + userValue +
                ") excede el limite seguro para este hardware (" + hardLimit +
                "). Ajustado automaticamente para evitar alucinaciones/congelamiento.");
            return hardLimit;
        }
        return userValue;
    }

    private static double applySafetyCapDouble(double userValue, double minLimit,
                                                double maxLimit, String envVarName) {
        if (userValue < minLimit) {
            System.err.println("[SAFETY] " + envVarName + " (" + userValue +
                ") es menor que el minimo permitido (" + minLimit +
                "). Ajustado automaticamente.");
            return minLimit;
        }
        if (userValue > maxLimit) {
            System.err.println("[SAFETY] " + envVarName + " (" + userValue +
                ") excede el maximo seguro (" + maxLimit +
                "). Ajustado para evitar respuestas incoherentes.");
            return maxLimit;
        }
        return userValue;
    }

    private static int getGpuLayersWithAutoDetect() {
        String envValue = System.getenv("FARARONI_GPU_LAYERS");
        if (envValue != null && !envValue.isBlank()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
            }
        }

        return detectOptimalGpuLayers();
    }

    private static int detectOptimalGpuLayers() {
        if (isAppleSilicon()) {
            return APPLE_SILICON_GPU_LAYERS;
        }

        return CPU_ONLY_GPU_LAYERS;
    }

    private static Boolean appleSiliconCache = null;

    public static boolean isAppleSilicon() {
        if (appleSiliconCache != null) {
            return appleSiliconCache;
        }

        String osName = System.getProperty("os.name", "").toLowerCase();

        if (!osName.contains("mac")) {
            appleSiliconCache = false;
            return false;
        }

        String osArch = System.getProperty("os.arch", "");
        if ("aarch64".equals(osArch)) {
            appleSiliconCache = true;
            return true;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "hw.optional.arm64");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();

            if ("1".equals(output)) {
                appleSiliconCache = true;
                return true;
            }
        } catch (Exception e) {
        }

        appleSiliconCache = false;
        return false;
    }

    public static String getHardwareDescription() {
        String osName = System.getProperty("os.name", "Unknown OS");
        String osArch = System.getProperty("os.arch", "Unknown Arch");

        if (isAppleSilicon()) {
            String rosetta = "aarch64".equals(osArch) ? "" : " via Rosetta";
            return "Apple Silicon" + rosetta + " - Metal Acceleration";
        } else if (osName.toLowerCase().contains("mac")) {
            return "Intel Mac (" + osArch + ") - CPU Only";
        } else {
            return osName + " (" + osArch + ") - CPU Only";
        }
    }
}
