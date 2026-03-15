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
package dev.fararoni.bus.module;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ModuleConfigSentinel {
    private static final Logger LOG = Logger.getLogger(ModuleConfigSentinel.class.getName());

    private static final String CONFIG_FILE = "modules.yml";

    private static final long DEBOUNCE_MS = 500;

    private final Path configDir;
    private final Path configFile;
    private final Consumer<Path> onConfigChange;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread watcherThread;
    private WatchService watchService;
    private ScheduledExecutorService debounceExecutor;
    private ScheduledFuture<?> pendingReload;

    public ModuleConfigSentinel(Path configDir, Consumer<Path> onConfigChange) {
        this.configDir = configDir;
        this.configFile = configDir.resolve(CONFIG_FILE);
        this.onConfigChange = onConfigChange;
    }

    public void start() {
        if (running.get()) {
            LOG.warning("[CONFIG-SENTINEL] Already running");
            return;
        }

        if (!Files.exists(configDir)) {
            LOG.warning("[CONFIG-SENTINEL] Config directory does not exist: " + configDir);
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "config-sentinel-debounce");
                t.setDaemon(true);
                return t;
            });

            watcherThread = Thread.ofVirtual()
                .name("config-sentinel-watcher")
                .start(this::watchLoop);

            running.set(true);
            LOG.info("[CONFIG-SENTINEL] Started watching: " + configFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start config sentinel", e);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        LOG.info("[CONFIG-SENTINEL] Stopping...");

        if (pendingReload != null) {
            pendingReload.cancel(false);
        }

        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
        }

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
        }

        if (watcherThread != null) {
            watcherThread.interrupt();
        }

        LOG.info("[CONFIG-SENTINEL] Stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public void forceReload() {
        if (Files.exists(configFile)) {
            LOG.info("[CONFIG-SENTINEL] Forcing reload");
            triggerReload();
        }
    }

    private void watchLoop() {
        LOG.fine("[CONFIG-SENTINEL] Watch loop started");

        while (running.get()) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (CONFIG_FILE.equals(filename.toString())) {
                        LOG.fine("[CONFIG-SENTINEL] Detected change: " + filename);
                        scheduleReload();
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    LOG.warning("[CONFIG-SENTINEL] Watch key no longer valid");
                    break;
                }
            } catch (InterruptedException e) {
                LOG.fine("[CONFIG-SENTINEL] Watch loop interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    LOG.log(Level.WARNING, "[CONFIG-SENTINEL] Error in watch loop", e);
                }
            }
        }

        LOG.fine("[CONFIG-SENTINEL] Watch loop ended");
    }

    private void scheduleReload() {
        if (pendingReload != null) {
            pendingReload.cancel(false);
        }

        pendingReload = debounceExecutor.schedule(
            this::triggerReload,
            DEBOUNCE_MS,
            TimeUnit.MILLISECONDS
        );
    }

    private void triggerReload() {
        if (!running.get()) {
            return;
        }

        LOG.info("[CONFIG-SENTINEL] Configuration changed, triggering reload");

        try {
            if (!Files.exists(configFile)) {
                LOG.warning("[CONFIG-SENTINEL] Config file not found: " + configFile);
                return;
            }

            if (!Files.isReadable(configFile)) {
                LOG.warning("[CONFIG-SENTINEL] Config file not readable: " + configFile);
                return;
            }

            if (onConfigChange != null) {
                onConfigChange.accept(configFile);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[CONFIG-SENTINEL] Error during reload", e);
        }
    }
}
