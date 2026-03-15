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

import dev.fararoni.core.core.routing.RoutingPlan;
import dev.fararoni.core.core.telemetry.OperationTelemetry;
import org.jline.terminal.Terminal;
import org.jline.utils.Status;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 14.5.0
 * @since 1.0.0
 */
public class ConsoleTelemetry implements OperationTelemetry {
    private final Terminal terminal;
    private final Status status;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread animationThread;

    private volatile String currentPhaseText = "Iniciando...";
    private volatile boolean isExpertMode = false;
    private final long startTime;

    private Consumer<Boolean> processingStateCallback;
    private Consumer<RoutingPlan.TargetModel> modelSwitchCallback;

    private static final String RESET = "\033[0m";
    private static final String CYAN = "\033[0;36m";
    private static final String YELLOW = "\033[0;33m";
    private static final String GREEN = "\033[0;32m";
    private static final String BOLD = "\033[1m";

    private static final String CLEAR_LINE = "\r\033[2K";
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";

    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] BARS = {" ", "▂", "▃", "▄", "▅", "▆", "▇", "█"};

    private enum SpinnerState {
        CONNECTING("Connecting..."),
        ANALYZING("Analyzing context..."),
        THINKING("Thinking deeply..."),
        PROCESSING("Processing data..."),
        OPTIMIZING("Optimizing memory..."),
        GENERATING("Generating response..."),

        TINKERING("Tinkering..."),
        WORKING("Working..."),
        LOADING("Loading files...");

        public final String text;

        SpinnerState(String text) {
            this.text = text;
        }
    }

    public ConsoleTelemetry(Terminal terminal, Status status) {
        this.terminal = terminal;
        this.status = status;
        this.startTime = System.currentTimeMillis();

        if (terminal != null) {
            terminal.writer().print(HIDE_CURSOR);
            terminal.writer().print(buildFrame(0));
            terminal.writer().flush();
            if (status != null) status.redraw();
        }

        this.animationThread = new Thread(this::animateLoop);
        this.animationThread.setName("fararoni-hud-render");
        this.animationThread.setDaemon(true);
        this.animationThread.setPriority(Thread.MAX_PRIORITY);
        this.animationThread.start();
    }

    public ConsoleTelemetry(Terminal terminal) {
        this(terminal, null);
    }

    @Override
    public void onPhaseChange(String phaseInfo) {
        this.currentPhaseText = phaseInfo;
        String lower = phaseInfo.toLowerCase();
        if (lower.contains("turtle") || lower.contains("32b") ||
            lower.contains("expert") || lower.contains("fallback")) {
            this.isExpertMode = true;
        }
    }

    @Override
    public void onModelSwitch(RoutingPlan.TargetModel target) {
        this.isExpertMode = (target == RoutingPlan.TargetModel.EXPERT);
        if (modelSwitchCallback != null) {
            modelSwitchCallback.accept(target);
        }
    }

    @Override
    public void onProcessingState(boolean isProcessing) {
        if (processingStateCallback != null) {
            processingStateCallback.accept(isProcessing);
        }
    }

    public ConsoleTelemetry withProcessingStateCallback(Consumer<Boolean> callback) {
        this.processingStateCallback = callback;
        return this;
    }

    public ConsoleTelemetry withModelSwitchCallback(Consumer<RoutingPlan.TargetModel> callback) {
        this.modelSwitchCallback = callback;
        return this;
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) return;

        try {
            if (animationThread != null) animationThread.join(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (terminal != null) {
            terminal.writer().print(CLEAR_LINE + "\r" + SHOW_CURSOR);
            terminal.writer().flush();
            if (status != null) status.redraw();
        }
    }

    private void animateLoop() {
        int tick = 1;
        while (running.get()) {
            try {
                if (terminal != null) {
                    terminal.writer().print(buildFrame(tick));
                    terminal.writer().flush();
                    if (status != null) status.redraw();
                }
                tick++;
                Thread.sleep(60);
            } catch (Exception e) {
                break;
            }
        }
        if (terminal != null) {
            terminal.writer().print(SHOW_CURSOR);
            terminal.writer().flush();
        }
    }

    private String getStateText(long elapsed) {
        if (elapsed < 1000) return SpinnerState.CONNECTING.text;
        if (elapsed < 3000) return SpinnerState.ANALYZING.text;
        if (elapsed < 7000) return SpinnerState.THINKING.text;
        if (elapsed < 11000) return SpinnerState.PROCESSING.text;
        if (elapsed < 15000) return SpinnerState.OPTIMIZING.text;
        return SpinnerState.GENERATING.text;
    }

    private String buildFrame(int tick) {
        long elapsed = System.currentTimeMillis() - startTime;
        long seconds = elapsed / 1000;

        String spinner = SPINNER[tick % SPINNER.length];

        String modelName = currentPhaseText.length() > 20
            ? currentPhaseText.substring(0, 17) + "..."
            : currentPhaseText;

        String dynamicState = getStateText(elapsed);

        StringBuilder line = new StringBuilder(CLEAR_LINE);

        if (elapsed < 2500 && !isExpertMode) {
            String bar = BARS[tick % BARS.length];
            line.append(CYAN).append(spinner)
                .append(BOLD).append(" [⇡ TX] ")
                .append(RESET).append(CYAN)
                .append("Enlace Neural Activo... ")
                .append(RESET).append(CYAN).append(dynamicState).append(" ")
                .append(bar)
                .append(RESET).append(" ").append(modelName);
        } else if (elapsed < 15000 || isExpertMode) {
            String bar = BARS[(tick / 2) % BARS.length];
            line.append(YELLOW).append(spinner)
                .append(BOLD).append(" [◴ CPU] ")
                .append(RESET).append(YELLOW)
                .append("Procesando Inferencia... ")
                .append(RESET).append(YELLOW).append(dynamicState).append(" ")
                .append(bar).append(bar)
                .append(RESET).append(" ").append(seconds).append("s");
        } else {
            line.append(GREEN).append(spinner)
                .append(BOLD).append(" [⇣ RX] ")
                .append(RESET).append(GREEN)
                .append("Recibiendo Respuesta... ")
                .append(RESET).append(GREEN).append(dynamicState).append(" ")
                .append(RESET).append(seconds).append("s");
        }

        line.append(RESET);
        return line.toString();
    }
}
