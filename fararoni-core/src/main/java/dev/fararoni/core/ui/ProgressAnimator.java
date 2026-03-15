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
package dev.fararoni.core.ui;

import dev.fararoni.bus.agent.api.ui.model.TaskTreeModel;
import dev.fararoni.core.ui.renderers.LiveProgressRenderer;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ProgressAnimator {
    private static final Logger LOG = Logger.getLogger(ProgressAnimator.class.getName());

    private static final long REFRESH_INTERVAL_MS = 80;

    private final LiveProgressRenderer renderer;

    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> currentTask;

    private final AtomicReference<TaskTreeModel> currentModel;

    private volatile boolean running;

    public ProgressAnimator(LiveProgressRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer no puede ser null");
        this.currentModel = new AtomicReference<>();
        this.running = false;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProgressAnimator");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(TaskTreeModel model) {
        Objects.requireNonNull(model, "model no puede ser null");
        currentModel.set(model);

        if (!running) {
            running = true;
            currentTask = scheduler.scheduleAtFixedRate(
                this::tick,
                0,
                REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
            LOG.fine("[ProgressAnimator] Animacion iniciada");
        }
    }

    public void updateModel(TaskTreeModel model) {
        Objects.requireNonNull(model, "model no puede ser null");
        currentModel.set(model);

        if (!running) {
            tick();
        }
    }

    public void stop() {
        running = false;

        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }

        try {
            renderer.clear();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Error limpiando renderer", e);
        }

        LOG.fine("[ProgressAnimator] Animacion detenida");
    }

    public boolean isRunning() {
        return running;
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void tick() {
        TaskTreeModel model = currentModel.get();
        if (model != null && running) {
            try {
                renderer.render(model);
            } catch (Exception e) {
                LOG.log(Level.FINE, "Error en render tick", e);
            }
        }
    }
}
