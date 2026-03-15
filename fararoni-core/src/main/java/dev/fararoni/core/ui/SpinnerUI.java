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

import static org.fusesource.jansi.Ansi.ansi;

/**
 * @author Eber Cruz
 * @version 1.0.1
 * @since 1.0.0
 */
public class SpinnerUI implements Runnable {
    private static volatile Boolean cachedHeadlessMode = null;

    private static boolean isHeadless() {
        if (cachedHeadlessMode != null) {
            return cachedHeadlessMode;
        }

        synchronized (SpinnerUI.class) {
            if (cachedHeadlessMode != null) {
                return cachedHeadlessMode;
            }

            boolean headless = System.console() == null;

            String term = System.getenv("TERM");
            if ("dumb".equalsIgnoreCase(term) || term == null || term.isEmpty()) {
                headless = true;
            }

            String ci = System.getenv("CI");
            if ("true".equalsIgnoreCase(ci) || "1".equals(ci)) {
                headless = true;
            }

            String forceHeadless = System.getenv("FARARONI_HEADLESS");
            if ("true".equalsIgnoreCase(forceHeadless) || "1".equals(forceHeadless)) {
                headless = true;
            }

            cachedHeadlessMode = headless;
            return headless;
        }
    }

    private volatile boolean running = true;
    private volatile String statusText = "Tinkering...";
    private final long startTime;
    private String lastStatusUpdate = "";

    private static final String[] THINKING_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] PROCESSING_FRAMES = {"◐", "◓", "◑", "◒"};
    private static final String[] DOTS_FRAMES = {"⠁", "⠂", "⠄", "⡀", "⢀", "⠠", "⠐", "⠈"};

    private String[] currentFrames = THINKING_FRAMES;

    public SpinnerUI() {
        this.startTime = System.currentTimeMillis();
    }

    public SpinnerUI(String initialStatus) {
        this();
        this.statusText = initialStatus;
    }

    private Thread spinnerThread;

    public void stop() {
        this.running = false;
        if (spinnerThread != null && spinnerThread.isAlive()) {
            try {
                spinnerThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void updateStatus(String text) {
        this.statusText = text;

        if (text.toLowerCase().contains("thinking") || text.toLowerCase().contains("pensando")) {
            currentFrames = THINKING_FRAMES;
        } else if (text.toLowerCase().contains("processing") || text.toLowerCase().contains("procesando")) {
            currentFrames = PROCESSING_FRAMES;
        } else if (text.toLowerCase().contains("generating") || text.toLowerCase().contains("generando")) {
            currentFrames = DOTS_FRAMES;
        }

        lastStatusUpdate = text;
    }

    public static SpinnerUI startWith(String status) {
        var spinner = new SpinnerUI(status);

        if (!isHeadless()) {
            String firstFrame = ansi().fgYellow().a(THINKING_FRAMES[0]).reset()
                .a(" ")
                .fgBrightYellow().a(status).reset()
                .toString();
            System.err.print(firstFrame);
            System.err.flush();
        }

        spinner.spinnerThread = new Thread(spinner);
        spinner.spinnerThread.setDaemon(true);
        spinner.spinnerThread.start();
        return spinner;
    }

    @Override
    public void run() {
        if (isHeadless()) {
            runHeadless();
            return;
        }

        int frameIndex = 0;

        while (running) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            String frame = currentFrames[frameIndex % currentFrames.length];

            String message = buildSpinnerMessage(frame, elapsed);

            System.err.print(ansi().cursorLeft(200).eraseLine());
            System.err.print(message);
            System.err.flush();

            frameIndex++;

            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        clearSpinner();
    }

    private void runHeadless() {
        System.err.println("[" + statusText + "]");
        System.err.flush();

        while (running) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String buildSpinnerMessage(String frame, long elapsed) {
        var coloredFrame = getColoredFrame(frame);
        var coloredStatus = getColoredStatus();
        var timeInfo = getTimeInfo(elapsed);
        var interruptInfo = getInterruptInfo();

        return String.format("%s %s %s%s",
            coloredFrame,
            coloredStatus,
            timeInfo,
            interruptInfo
        );
    }

    private String getColoredFrame(String frame) {
        if (statusText.toLowerCase().contains("thinking")) {
            return ansi().fgYellow().a(frame).reset().toString();
        } else if (statusText.toLowerCase().contains("error")) {
            return ansi().fgRed().a(frame).reset().toString();
        } else {
            return ansi().fgCyan().a(frame).reset().toString();
        }
    }

    private String getColoredStatus() {
        if (statusText.toLowerCase().contains("thinking")) {
            return ansi().fgBrightYellow().a(statusText).reset().toString();
        } else if (statusText.toLowerCase().contains("error")) {
            return ansi().fgBrightRed().a(statusText).reset().toString();
        } else if (statusText.toLowerCase().contains("generating")) {
            return ansi().fgBrightGreen().a(statusText).reset().toString();
        } else {
            return ansi().fgBrightCyan().a(statusText).reset().toString();
        }
    }

    private String getTimeInfo(long elapsed) {
        if (elapsed < 5) {
            return ansi().fgBrightBlack().a("(" + elapsed + "s)").reset().toString();
        } else if (elapsed < 15) {
            return ansi().fgYellow().a("(" + elapsed + "s)").reset().toString();
        } else {
            return ansi().fgRed().a("(" + elapsed + "s)").reset().toString();
        }
    }

    private String getInterruptInfo() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsed > 3) {
            return ansi().fgBrightBlack().a(" · [Ctrl+C para interrumpir]").reset().toString();
        }
        return "";
    }

    private void clearSpinner() {
        System.err.print(ansi().cursorLeft(200).eraseLine());
        System.err.flush();
    }

    public static SpinnerUI thinking() {
        return startWith("Thinking deeply...");
    }

    public static SpinnerUI processing(String fileName) {
        return startWith("Processing " + fileName + "...");
    }

    public static SpinnerUI generating() {
        return startWith("Generating response...");
    }

    public static SpinnerUI working() {
        return startWith("Working...");
    }

    public static SpinnerUI connecting() {
        return startWith("Connecting to server...");
    }

    public enum SpinnerState {
        TINKERING("Tinkering..."),
        THINKING("Thinking deeply..."),
        PROCESSING("Processing data..."),
        GENERATING("Generating response..."),
        CONNECTING("Connecting..."),
        WORKING("Working..."),
        ANALYZING("Analyzing context..."),
        OPTIMIZING("Optimizing memory..."),
        LOADING("Loading files...");

        public final String text;

        SpinnerState(String text) {
            this.text = text;
        }
    }

    public static SpinnerUI withState(SpinnerState state) {
        return startWith(state.text);
    }

    public static boolean isHeadlessMode() {
        return isHeadless();
    }
}
