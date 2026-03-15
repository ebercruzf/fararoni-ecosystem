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
package dev.fararoni.core.core.infra;

import dev.fararoni.core.FararoniCore;

import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ConfigSentinel {
    private static final Logger LOG = Logger.getLogger(ConfigSentinel.class.getName());

    private static final long DEBOUNCE_MS = 500;

    private final FararoniCore core;
    private final Path watchDir;
    private final ExecutorService watchExecutor;
    private volatile boolean running = false;

    public ConfigSentinel(FararoniCore core, Path configBase) {
        this.core = core;
        this.watchDir = configBase.resolve("instances");
        this.watchExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("config-sentinel").factory()
        );
    }

    public void start() {
        if (running) return;
        running = true;

        watchExecutor.submit(this::watchLoop);
        LOG.info("[SENTINEL] Vigilancia activa en: " + watchDir);
    }

    public void stop() {
        running = false;
        watchExecutor.shutdownNow();
        LOG.info("[SENTINEL] Detenido");
    }

    public boolean isRunning() {
        return running;
    }

    public Path getWatchDir() {
        return watchDir;
    }

    private void watchLoop() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            if (!Files.exists(watchDir)) {
                Files.createDirectories(watchDir);
                LOG.info("[SENTINEL] Directorio creado: " + watchDir);
            }

            watchDir.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );

            LOG.info("[SENTINEL] Escuchando cambios...");

            while (running) {
                WatchKey key = watcher.take();

                Thread.sleep(DEBOUNCE_MS);

                boolean reloadNeeded = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changedFile = (Path) event.context();
                    String fileName = changedFile.toString();

                    if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
                        WatchEvent.Kind<?> kind = event.kind();
                        String action = kind == StandardWatchEventKinds.ENTRY_CREATE ? "Creado" :
                                        kind == StandardWatchEventKinds.ENTRY_MODIFY ? "Modificado" :
                                        kind == StandardWatchEventKinds.ENTRY_DELETE ? "Eliminado" : "Cambio";

                        LOG.info("[SENTINEL] " + action + ": " + fileName);
                        reloadNeeded = true;
                    }
                }

                if (reloadNeeded) {
                    LOG.info("[SENTINEL] Disparando redespliegue...");
                    try {
                        core.deployDynamicAgents();
                    } catch (Exception e) {
                        LOG.severe("[SENTINEL] Error en redespliegue: " + e.getMessage());
                    }
                }

                if (!key.reset()) {
                    LOG.warning("[SENTINEL] WatchKey invalido, deteniendo...");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("[SENTINEL] Interrumpido");
        } catch (Exception e) {
            if (running) {
                LOG.severe("[SENTINEL] Error fatal: " + e.getMessage());
            }
        }
    }
}
