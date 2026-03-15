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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SwarmSpinner {
    private static final String[] PULSE_FRAMES = {"•", "○", "●", "·", "◉", "○"};

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String MAGENTA = "\u001B[35m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String DIM = "\u001B[2m";

    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";

    private final String message;
    private final Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final long startTime;
    private volatile String agentName = "SWARM";

    private static volatile Boolean cachedHeadlessMode = null;

    public SwarmSpinner(String message) {
        this.message = message;
        this.startTime = System.currentTimeMillis();

        this.worker = Thread.ofVirtual().name("swarm-spinner").unstarted(() -> {
            if (!isHeadless()) {
                String line = String.format("\n%s%s[%s]%s %s●%s %s%s%s\n",
                    MAGENTA, BOLD, agentName, RESET,
                    CYAN, RESET,
                    BOLD, message, RESET
                );
                System.err.print(line);
                System.err.flush();
            }

            while (running.get()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public SwarmSpinner(String agentName, String message) {
        this(message);
        this.agentName = agentName;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            worker.start();
        }
    }

    public void stop(boolean success, String finalMessage) {
        if (running.compareAndSet(true, false)) {
            try {
                worker.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!isHeadless()) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                String icon = success ? "✔" : "✖";
                String color = success ? GREEN : RED;
                String msg = finalMessage != null ? finalMessage : message;

                if (msg.length() > 60) {
                    msg = msg.substring(0, 57) + "...";
                }

                String line = String.format("\n%s%s[%s]%s %s%s%s %s %s(%ds)%s\n",
                    MAGENTA, BOLD, agentName, RESET,
                    color, icon, RESET,
                    msg,
                    DIM, elapsed, RESET
                );

                System.err.print(line);
                System.err.flush();
            }
        }
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public static SwarmSpinner forTaskCreate(String activeForm) {
        String msg = (activeForm != null && !activeForm.isBlank())
            ? activeForm
            : "Inicializando misión";
        SwarmSpinner spinner = new SwarmSpinner("SWARM", msg);
        spinner.start();
        return spinner;
    }

    public static SwarmSpinner forMission(String missionGoal) {
        String msg = missionGoal.length() > 40
            ? missionGoal.substring(0, 37) + "..."
            : missionGoal;
        SwarmSpinner spinner = new SwarmSpinner("HIVE", msg);
        spinner.start();
        return spinner;
    }

    public static SwarmSpinner forAgent(String agentId, String action) {
        SwarmSpinner spinner = new SwarmSpinner(agentId, action);
        spinner.start();
        return spinner;
    }

    private static boolean isHeadless() {
        if (cachedHeadlessMode != null) {
            return cachedHeadlessMode;
        }

        synchronized (SwarmSpinner.class) {
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

    public static boolean isHeadlessMode() {
        return isHeadless();
    }
}
