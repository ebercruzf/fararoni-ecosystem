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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 3.0.0
 * @since 1.0.0
 */
public class NeuralStatus {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private final String modelName;
    private long startTime;

    private static final String RESET = "\033[0m";
    private static final String CYAN = "\033[0;36m";
    private static final String YELLOW = "\033[0;33m";
    private static final String GREEN = "\033[0;32m";
    private static final String BOLD = "\033[1m";

    private static final String CLEAR_LINE = "\r\033[2K";

    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private static final String[] PROGRESS_BARS = {" ", "▂", "▃", "▄", "▅", "▆", "▇", "█"};

    public NeuralStatus(String modelName) {
        this.modelName = modelName != null ? modelName : "Unknown";
    }

    public void start() {
        if (running.getAndSet(true)) {
            return;
        }

        startTime = System.currentTimeMillis();

        this.worker = Thread.ofVirtual()
                .name("neural-hud")
                .start(this::animateHybrid);
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        if (worker != null) {
            try {
                worker.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.print(CLEAR_LINE);
        System.out.flush();
    }

    private void animateHybrid() {
        int tick = 0;

        while (running.get()) {
            long elapsed = System.currentTimeMillis() - startTime;

            if (elapsed < 50) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            long seconds = elapsed / 1000;
            StringBuilder line = new StringBuilder(CLEAR_LINE);

            String spinner = SPINNER_FRAMES[tick % SPINNER_FRAMES.length];

            if (elapsed < 3000) {
                long kbSent = (elapsed / 10) + 120;
                String bar = PROGRESS_BARS[tick % PROGRESS_BARS.length];

                line.append(CYAN).append(spinner)
                    .append(BOLD).append(" [⇡ TX] ")
                    .append(RESET).append(CYAN)
                    .append("Subiendo Contexto... ").append(bar)
                    .append(" (").append(kbSent).append("KB) ")
                    .append(RESET).append("→ ").append(modelName);
            }
            else if (elapsed < 15000) {
                String bar = PROGRESS_BARS[(tick / 2) % PROGRESS_BARS.length];

                line.append(YELLOW).append(spinner)
                    .append(BOLD).append(" [◴ CPU] ")
                    .append(RESET).append(YELLOW)
                    .append("Inferencia Profunda (Cargando Tensores)... ")
                    .append(bar).append(bar)
                    .append(RESET).append(" ")
                    .append(seconds).append("s");
            }
            else {
                line.append(GREEN).append(spinner)
                    .append(BOLD).append(" [⇣ RX] ")
                    .append(RESET).append(GREEN)
                    .append("Enlace Establecido. Esperando flujo de tokens... ")
                    .append(RESET)
                    .append("⏱ ").append(seconds).append("s");
            }

            System.out.print(line.toString());
            System.out.flush();

            tick++;

            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getElapsedMillis() {
        return System.currentTimeMillis() - startTime;
    }
}
