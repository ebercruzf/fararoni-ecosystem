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
import dev.fararoni.core.core.telemetry.ToolAwareTelemetry;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class PulseTelemetry implements ToolAwareTelemetry {
    private static final String[] SPINNER = {
        "\u280B", "\u2819", "\u2839", "\u2838", "\u283C", "\u2834", "\u2826", "\u2827", "\u2807", "\u280F"
    };

    public enum Mode {
        THINKING(AttributedStyle.YELLOW, "[THINK]"),
        WRITING(AttributedStyle.RED, "[WRITE]"),
        READING(AttributedStyle.BLUE, "[READ]"),
        SYSTEM(AttributedStyle.MAGENTA, "[SYS]"),
        NETWORK(AttributedStyle.CYAN, "[NET]"),
        SUCCESS(AttributedStyle.GREEN, "[OK]"),
        ERROR(AttributedStyle.RED, "[ERR]");

        final int color;
        final String label;

        Mode(int color, String label) {
            this.color = color;
            this.label = label;
        }
    }

    private static final int REFRESH_RATE_MS = 80;
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";
    private static final String CLEAR_LINE = "\r\033[2K";

    private final Terminal terminal;
    private final Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final long startTime;

    private volatile String currentMessage = "Initializing...";
    private volatile String modelName = "System";
    private volatile boolean isRemote = false;
    private volatile Mode currentMode = Mode.THINKING;

    private java.util.function.Consumer<Boolean> processingStateCallback;
    private java.util.function.Consumer<RoutingPlan.TargetModel> modelSwitchCallback;

    private volatile String realLocalModelName = null;
    private volatile String realRemoteModelName = null;

    private static final int MAX_SUB_LINES = 5;
    private final java.util.Deque<String> subLines = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private volatile int lastRenderedLineCount = 1;
    private static final String CURSOR_UP = "\033[A";
    private static final String CLEAR_TO_END = "\033[J";

    private static volatile PulseTelemetry activeInstance = null;

    public PulseTelemetry(Terminal terminal, String initialModel) {
        this.terminal = terminal;
        this.modelName = initialModel != null ? initialModel : "System";
        this.startTime = System.currentTimeMillis();

        activeInstance = this;

        if (terminal != null) {
            terminal.writer().print(HIDE_CURSOR);
            terminal.writer().flush();
        }

        this.worker = Thread.ofVirtual()
            .name("pulse-telemetry")
            .start(this::animationLoop);
    }

    public PulseTelemetry(Terminal terminal) {
        this(terminal, "System");
    }

    public PulseTelemetry withProcessingStateCallback(java.util.function.Consumer<Boolean> callback) {
        this.processingStateCallback = callback;
        return this;
    }

    public PulseTelemetry withModelSwitchCallback(java.util.function.Consumer<RoutingPlan.TargetModel> callback) {
        this.modelSwitchCallback = callback;
        return this;
    }

    public PulseTelemetry withLocalModelName(String modelName) {
        this.realLocalModelName = modelName;
        return this;
    }

    public PulseTelemetry withRemoteModelName(String modelName) {
        this.realRemoteModelName = modelName;
        return this;
    }

    public void addSubLine(String line) {
        if (line == null || line.isEmpty()) return;

        subLines.addLast(line);
        while (subLines.size() > MAX_SUB_LINES) {
            subLines.removeFirst();
        }
    }

    public void clearSubLines() {
        subLines.clear();
    }

    public int getSubLineCount() {
        return subLines.size();
    }

    public static void sendSubLine(String line) {
        PulseTelemetry active = activeInstance;
        if (active != null) {
            active.addSubLine(line);
        }
    }

    public static boolean hasActiveInstance() {
        return activeInstance != null;
    }

    @Override
    public void onPhaseChange(String phaseName) {
        if (phaseName == null) {
            updateState(Mode.THINKING, "Processing...");
            return;
        }

        Mode mode = switch (phaseName.toUpperCase()) {
            case "TX" -> Mode.NETWORK;
            case "RX" -> Mode.NETWORK;
            case "CPU" -> Mode.THINKING;
            default -> Mode.THINKING;
        };

        String message = switch (phaseName.toUpperCase()) {
            case "TX" -> "Transmitting to LLM...";
            case "RX" -> "Receiving response...";
            case "CPU" -> "Processing inference...";
            default -> phaseName + "...";
        };

        updateState(mode, message);
    }

    @Override
    public void onModelSwitch(RoutingPlan.TargetModel targetModel) {
        if (targetModel == null) {
            this.modelName = "Unknown";
            this.isRemote = false;
            return;
        }

        switch (targetModel) {
            case LOCAL -> {
                this.modelName = (realLocalModelName != null) ? realLocalModelName : "Rabbit (1.5B)";
                this.isRemote = false;
            }
            case EXPERT -> {
                this.modelName = (realRemoteModelName != null) ? realRemoteModelName : "Turtle (32B)";
                this.isRemote = true;
            }
            default -> {
                this.modelName = targetModel.name();
                this.isRemote = false;
            }
        }

        updateState(Mode.THINKING, "Switching to " + this.modelName + "...");

        if (modelSwitchCallback != null) {
            modelSwitchCallback.accept(targetModel);
        }
    }

    @Override
    public void onProcessingState(boolean isProcessing) {
        if (isProcessing) {
            updateState(Mode.THINKING, "Thinking...");
        } else {
            updateState(Mode.SUCCESS, "Ready.");
        }

        if (processingStateCallback != null) {
            processingStateCallback.accept(isProcessing);
        }
    }

    @Override
    public void onToolStart(String toolName, String uiHint) {
        Mode mode = detectMode(toolName);
        String message = uiHint != null && !uiHint.isBlank() ? uiHint : "Processing...";
        updateState(mode, message);
    }

    @Override
    public void onToolFinish(String toolName, boolean success) {
        if (success) {
            updateState(Mode.SUCCESS, "Done: " + formatToolName(toolName));
        } else {
            updateState(Mode.ERROR, "Failed: " + formatToolName(toolName));
        }
        updateState(Mode.THINKING, "Processing result...");
    }

    public void showCancelled(String message) {
        updateState(Mode.SYSTEM, message != null ? message : "CANCELLED");
    }

    public void showError(String message) {
        updateState(Mode.ERROR, message != null ? message : "ERROR");
    }

    @Override
    public void close() {
        running.set(false);
        try {
            worker.join(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (terminal != null) {
            StringBuilder cleanup = new StringBuilder();
            for (int i = 1; i < lastRenderedLineCount; i++) {
                cleanup.append(CURSOR_UP);
            }
            for (int i = 0; i < lastRenderedLineCount; i++) {
                cleanup.append(CLEAR_LINE);
                if (i < lastRenderedLineCount - 1) {
                    cleanup.append("\n");
                }
            }
            cleanup.append("\r");
            terminal.writer().print(cleanup.toString());
            terminal.writer().print(SHOW_CURSOR);
            terminal.writer().flush();
        }

        if (activeInstance == this) {
            activeInstance = null;
        }
        subLines.clear();
    }

    private void updateState(Mode mode, String message) {
        this.currentMode = mode;
        this.currentMessage = message;
    }

    private Mode detectMode(String toolName) {
        if (toolName == null) return Mode.SYSTEM;

        String lower = toolName.toLowerCase();

        if (lower.contains("write") || lower.contains("create") ||
            lower.contains("update") || lower.contains("delete") ||
            lower.contains("put") || lower.contains("patch")) {
            return Mode.WRITING;
        }

        if (lower.contains("read") || lower.contains("list") ||
            lower.contains("search") || lower.contains("get") ||
            lower.contains("scan") || lower.contains("glob")) {
            return Mode.READING;
        }

        if (lower.contains("web") || lower.contains("http") ||
            lower.contains("fetch") || lower.contains("net")) {
            return Mode.NETWORK;
        }

        if (lower.contains("shell") || lower.contains("git") ||
            lower.contains("exec") || lower.contains("command")) {
            return Mode.SYSTEM;
        }

        return Mode.THINKING;
    }

    private String formatToolName(String toolName) {
        if (toolName == null) return "operation";
        return toolName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private void animationLoop() {
        int frame = 0;
        while (running.get()) {
            try {
                renderFrame(frame++);
                Thread.sleep(REFRESH_RATE_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void renderFrame(int frame) {
        if (terminal == null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        StringBuilder output = new StringBuilder();

        if (lastRenderedLineCount > 1) {
            for (int i = 0; i < lastRenderedLineCount - 1; i++) {
                output.append(CURSOR_UP);
            }
        }

        output.append(CLEAR_LINE);

        AttributedStringBuilder sb = new AttributedStringBuilder();

        sb.style(AttributedStyle.DEFAULT.foreground(currentMode.color).bold());
        sb.append(SPINNER[frame % SPINNER.length]).append(" ");

        sb.append(currentMode.label).append(" ");

        sb.style(AttributedStyle.DEFAULT.foreground(currentMode.color));
        sb.append(currentMessage);

        sb.style(AttributedStyle.DEFAULT.faint());
        sb.append(" · ");
        sb.append(modelName);
        if (isRemote) {
            sb.append(" (Remote)");
        }
        sb.append(String.format(" %.1fs", elapsed / 1000.0));

        sb.style(AttributedStyle.DEFAULT);
        output.append(sb.toAnsi());

        int lineCount = 1;
        if (!subLines.isEmpty()) {
            for (String subLine : subLines) {
                output.append("\n").append(CLEAR_LINE);
                output.append("    └─ ").append(subLine);
                lineCount++;
            }
        }

        int extraLines = lastRenderedLineCount - lineCount;
        for (int i = 0; i < extraLines; i++) {
            output.append("\n").append(CLEAR_LINE);
        }
        for (int i = 0; i < extraLines; i++) {
            output.append(CURSOR_UP);
        }

        lastRenderedLineCount = lineCount;

        terminal.writer().print(output.toString());
        terminal.writer().flush();
    }
}
