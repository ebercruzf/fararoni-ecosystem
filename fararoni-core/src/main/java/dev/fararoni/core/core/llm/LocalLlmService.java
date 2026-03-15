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

import dev.fararoni.core.core.download.DownloadProgress;
import dev.fararoni.core.core.download.ModelDownloadException;
import dev.fararoni.core.core.download.ModelDownloader;
import dev.fararoni.core.core.download.NativeEngineDownloader;
import dev.fararoni.core.core.utils.NativeLoader;
import dev.fararoni.core.core.utils.NativeSilencer;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.LlamaOutput;
import de.kherud.llama.LogLevel;
import de.kherud.llama.ModelParameters;
import de.kherud.llama.args.LogFormat;
import dev.fararoni.core.core.routing.RouterGrammars;
import dev.fararoni.core.core.spi.UserNotifier;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LocalLlmService implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(LocalLlmService.class.getName());

    public enum EngineState {
        READY("Motor local listo", true),

        MISSING_ENGINE("Motor local no instalado", false),

        MISSING_MODEL("Modelo no descargado", false),

        DISABLED("Motor local deshabilitado", false);

        private final String displayName;
        private final boolean operational;

        EngineState(String displayName, boolean operational) {
            this.displayName = displayName;
            this.operational = operational;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isOperational() {
            return operational;
        }
    }

    private final LocalLlmConfig config;
    private final ModelDownloader downloader;
    private final NativeEngineDownloader engineDownloader;

    private volatile LlamaModel model;
    private final ReentrantLock modelLock = new ReentrantLock();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private volatile long modelLoadTimeMs = 0;

    private volatile boolean nativeInitialized = false;
    private volatile boolean nativeAvailable = false;

    private final UserNotifier userNotifier;

    public LocalLlmService() {
        this(LocalLlmConfig.defaults(), null);
    }

    public LocalLlmService(LocalLlmConfig config) {
        this(config, null);
    }

    public LocalLlmService(LocalLlmConfig config, UserNotifier userNotifier) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.downloader = new ModelDownloader();
        this.engineDownloader = new NativeEngineDownloader();
        this.userNotifier = userNotifier != null ? userNotifier : UserNotifier.stderrFallback();
    }

    public EngineState getEngineState() {
        if (!config.hasEnoughRam()) {
            return EngineState.DISABLED;
        }

        if (!NativeLoader.isNativeLibraryAvailable()) {
            if (engineDownloader.isPlatformSupported() && engineDownloader.hasEnoughDiskSpace()) {
                return EngineState.MISSING_ENGINE;
            }
            return EngineState.DISABLED;
        }

        if (!config.isModelDownloaded()) {
            if (downloader.hasEnoughDiskSpace()) {
                return EngineState.MISSING_MODEL;
            }
            return EngineState.DISABLED;
        }

        return EngineState.READY;
    }

    public boolean isModelAvailable() {
        return config.isModelDownloaded() && config.hasEnoughRam();
    }

    public boolean isModelDownloaded() {
        return config.isModelDownloaded();
    }

    public boolean hasEnoughResources() {
        return config.hasEnoughRam() && downloader.hasEnoughDiskSpace();
    }

    public boolean isModelLoaded() {
        return model != null;
    }

    public LocalLlmConfig getConfig() {
        return config;
    }

    public LlmStats getStats() {
        return new LlmStats(
            isModelDownloaded(),
            isModelLoaded(),
            modelLoadTimeMs,
            config.getAvailableRamMb(),
            config.minFreeRamMb(),
            downloader.getDownloadedSize()
        );
    }

    public boolean shouldOfferDownload() {
        return !isModelDownloaded() && hasEnoughResources();
    }

    public boolean downloadModel(Consumer<DownloadProgress> progressCallback) {
        return downloader.download(progressCallback);
    }

    public boolean downloadModel() {
        return downloader.download();
    }

    public void cancelDownload() {
        downloader.cancel();
    }

    public ModelDownloader getDownloader() {
        return downloader;
    }

    public boolean shouldOfferEngineInstall() {
        return !NativeLoader.isNativeLibraryAvailable()
            && engineDownloader.isPlatformSupported()
            && engineDownloader.hasEnoughDiskSpace();
    }

    public boolean installEngine(Consumer<DownloadProgress> progressCallback) {
        boolean success = engineDownloader.download(progressCallback);
        if (success) {
            LOG.info("[LocalLlm] Motor nativo instalado exitosamente");
        }
        return success;
    }

    public boolean installEngine() {
        return installEngine(null);
    }

    public void cancelEngineInstall() {
        engineDownloader.cancel();
    }

    public NativeEngineDownloader getEngineDownloader() {
        return engineDownloader;
    }

    public String getPlatformInfo() {
        return engineDownloader.getOperatingSystem() + "-" + engineDownloader.getArchitecture();
    }

    public String generate(String prompt) {
        return generate(prompt, config.maxTokens());
    }

    public String generate(String prompt, int maxTokens) {
        ensureInitialized();

        if (!ensureModelLoaded()) {
            throw new LocalLlmException("Failed to load model");
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                StringBuilder response = new StringBuilder();

                InferenceParameters params = new InferenceParameters(prompt)
                    .setTemperature((float) config.temperature())
                    .setNPredict(maxTokens)
                    .setRepeatPenalty(1.3f)
                    .setRepeatLastN(64)
                    .setPenalizeNl(false);

                for (LlamaOutput output : model.generate(params)) {
                    response.append(output.text);

                    if (response.toString().contains("<|im_end|>")) {
                        break;
                    }
                }

                return cleanResponse(response.toString());
            })
            .orTimeout(180, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof TimeoutException) {
                    LOG.severe("[LocalLlm] ABORTANDO: El modelo excedio el limite de 180s (Zombie Kill)");
                    return "ERROR_TIMEOUT_EXCEEDED";
                }
                LOG.severe("[LocalLlm] Error en generacion LLM: " + cause.getMessage());
                return "ERROR_GENERATION_FAILED";
            })
            .join();
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                LOG.warning("[LocalLlm] Hilo interrumpido durante generacion");
                return "ERROR_THREAD_INTERRUPTED";
            }
            LOG.log(Level.WARNING, "[LocalLlm] Inference error", e);
            throw new LocalLlmException("Inference failed: " + e.getMessage(), e);
        }
    }

    public void generateStream(String prompt, Consumer<String> tokenCallback) {
        generateStream(prompt, tokenCallback, config.maxTokens());
    }

    public void generateStream(String prompt, Consumer<String> tokenCallback, int maxTokens) {
        ensureInitialized();

        if (!ensureModelLoaded()) {
            throw new LocalLlmException("Failed to load model");
        }

        Objects.requireNonNull(tokenCallback, "tokenCallback cannot be null");

        try {
            InferenceParameters params = new InferenceParameters(prompt)
                .setTemperature((float) config.temperature())
                .setNPredict(maxTokens)
                .setRepeatPenalty(1.3f)
                .setRepeatLastN(64)
                .setPenalizeNl(false);

            StringBuilder fullResponse = new StringBuilder();
            LoopDetector loopDetector = new LoopDetector();

            for (LlamaOutput output : model.generate(params)) {
                String token = output.text;
                fullResponse.append(token);

                if (loopDetector.shouldStop(fullResponse.toString())) {
                    tokenCallback.accept("\n\n[Respuesta truncada - texto repetitivo detectado]");
                    break;
                }

                if (!token.contains("<|im_end|>") && !token.contains("<|im_start|>")) {
                    tokenCallback.accept(token);
                }

                if (fullResponse.toString().contains("<|im_end|>")) {
                    break;
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[LocalLlm] Stream error", e);
            throw new LocalLlmException("Stream generation failed: " + e.getMessage(), e);
        }
    }

    public String generateWithGrammar(String prompt, String grammar, int maxTokens) {
        if (grammar == null || grammar.isBlank()) {
            throw new IllegalArgumentException("Grammar cannot be null or empty");
        }

        ensureInitialized();

        if (!ensureModelLoaded()) {
            throw new LocalLlmException("Failed to load model for grammar-constrained generation");
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                StringBuilder response = new StringBuilder();

                InferenceParameters params = new InferenceParameters(prompt)
                    .setTemperature(0.1f)
                    .setNPredict(maxTokens)
                    .setGrammar(grammar);

                for (LlamaOutput output : model.generate(params)) {
                    response.append(output.text);

                    if (response.toString().contains("<|im_end|>")) {
                        break;
                    }
                }

                String result = cleanResponse(response.toString());

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[LocalLlm] Grammar generation complete: " + result);
                }

                return result;
            })
            .orTimeout(180, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                if (cause instanceof TimeoutException) {
                    LOG.severe("[LocalLlm] ABORTANDO Grammar: Timeout 180s (Zombie Kill)");
                    return "ERROR_TIMEOUT_EXCEEDED";
                }
                LOG.severe("[LocalLlm] Error en grammar generation: " + cause.getMessage());
                return "ERROR_GENERATION_FAILED";
            })
            .join();
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                return "ERROR_THREAD_INTERRUPTED";
            }
            LOG.log(Level.WARNING, "[LocalLlm] Grammar-constrained generation error", e);
            throw new LocalLlmException("Grammar generation failed: " + e.getMessage(), e);
        }
    }

    public String generateWithGrammar(String prompt, String grammar) {
        return generateWithGrammar(prompt, grammar, 50);
    }

    public boolean isGrammarGenerationAvailable() {
        return isModelLoaded();
    }

    public void warmup() {
        if (!isModelAvailable()) {
            throw new LocalLlmException("Model not available for warmup");
        }

        LOG.info("[LocalLlm] Warming up...");
        long startTime = System.currentTimeMillis();

        ensureInitialized();

        if (!ensureModelLoaded()) {
            throw new LocalLlmException("Failed to load model during warmup");
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOG.info("[LocalLlm] Warmup completed in " + elapsed + "ms");
    }

    public void unloadModel() {
        modelLock.lock();
        try {
            if (model != null) {
                LOG.info("[LocalLlm] Unloading model...");
                try {
                    model.close();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[LocalLlm] Error closing model", e);
                }
                model = null;
                LOG.info("[LocalLlm] Model unloaded");
            }
        } finally {
            modelLock.unlock();
        }
    }

    @Override
    public void close() {
        LOG.info("[LocalLlm] Shutting down...");
        shutdownRequested.set(true);
        unloadModel();
        NativeSilencer.restore();
        LOG.info("[LocalLlm] Shutdown complete");
    }

    private void ensureInitialized() {
        if (nativeInitialized) {
            return;
        }

        synchronized (this) {
            if (nativeInitialized) {
                return;
            }

            try {
                NativeSilencer.silencePermanently();

                NativeLoader.loadEmbeddedLibrary();

                configureNativeLogger();

                nativeAvailable = true;

                if (config.debugLogging()) {
                    LOG.info("[LocalLlm] Motor Local (JLLama) inicializado correctamente");
                }
            } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
                nativeAvailable = false;
                LOG.fine("[LocalLlm] Modo Local no disponible: " + e.getClass().getSimpleName());
            } catch (Exception e) {
                nativeAvailable = false;
                LOG.warning("[LocalLlm] Error cargando libreria nativa: " + e.getMessage());
            } finally {
                nativeInitialized = true;
            }
        }
    }

    public boolean isNativeAvailable() {
        ensureInitialized();
        return nativeAvailable;
    }

    private void configureNativeLogger() {
        if (config.debugLogging()) {
            LlamaModel.setLogger(LogFormat.TEXT, (level, message) -> {
                String prefix = switch (level) {
                    case ERROR -> "ERROR";
                    case WARN  -> "WARN ";
                    case INFO  -> "INFO ";
                    case DEBUG -> "DEBUG";
                };
                System.err.println("[Native/" + prefix + "] " + message.trim());
            });
        } else {
            LlamaModel.setLogger(LogFormat.TEXT, (level, message) -> {
                if (level == LogLevel.ERROR) {
                    System.err.println("[Native/ERROR] " + message.trim());
                }
            });
        }
    }

    private boolean ensureModelLoaded() {
        if (model != null) {
            return true;
        }

        modelLock.lock();
        try {
            if (model != null) {
                return true;
            }
            return loadModel();
        } finally {
            modelLock.unlock();
        }
    }

    private boolean loadModel() {
        if (shutdownRequested.get()) {
            return false;
        }

        if (!config.isModelDownloaded()) {
            LOG.warning("[LocalLlm] Model not found: " + config.modelPath());
            return false;
        }

        System.err.println("[NATIVE] Despertando al Conejo (cargando GGUF en memoria)...");

        LOG.info("[LocalLlm] Loading model: " + config.modelPath());
        LOG.info("[LocalLlm] Hardware: " + LocalLlmConfig.getHardwareDescription());
        LOG.info("[LocalLlm] GPU Layers: " + config.gpuLayers() +
            (config.gpuLayers() > 0 ? " (Metal acceleration)" : " (CPU only)"));
        long startTime = System.currentTimeMillis();

        try {
            String nullDevice = System.getProperty("os.name").toLowerCase().contains("win")
                ? "NUL" : "/dev/null";

            int threads = config.threads() > 0
                ? config.threads()
                : Runtime.getRuntime().availableProcessors();

            ModelParameters params = new ModelParameters()
                .setModel(config.modelPath().toString())
                .setCtxSize(config.contextLength())
                .setThreads(threads)
                .setGpuLayers(config.gpuLayers())
                .setLogFile(nullDevice)
                .setLogVerbosity(0);

            model = new LlamaModel(params);

            modelLoadTimeMs = System.currentTimeMillis() - startTime;
            String accelInfo = config.gpuLayers() > 0 ? " with Metal" : " on CPU";
            LOG.info("[LocalLlm] Model loaded in " + modelLoadTimeMs + "ms" + accelInfo);

            System.err.println("[NATIVE] Conejo listo en " + modelLoadTimeMs + "ms" + accelInfo);

            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[LocalLlm] Failed to load model", e);
            model = null;
            return false;
        } catch (Error e) {
            LOG.log(Level.SEVERE, "[LocalLlm] JNI/Native error loading model: " + e.getMessage());
            model = null;
            return false;
        }
    }

    private String cleanResponse(String response) {
        return response
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .trim();
    }

    public record LlmStats(
        boolean modelDownloaded,
        boolean modelLoaded,
        long modelLoadTimeMs,
        long availableRamMb,
        int requiredRamMb,
        long modelSizeBytes
    ) {
        public String formatModelSize() {
            return DownloadProgress.formatSize(modelSizeBytes);
        }
    }

    public static class LocalLlmException extends RuntimeException {
        public LocalLlmException(String message) {
            super(message);
        }

        public LocalLlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
