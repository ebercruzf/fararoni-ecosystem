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
package dev.fararoni.core.ui.dashboard;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.Display;

import dev.fararoni.core.ui.TerminalCapabilityDetector;
import dev.fararoni.core.ui.OutputCoordinator;
import dev.fararoni.core.ui.statusbar.GlobalStatusBar;
import dev.fararoni.core.ui.input.GlobalKeyboardHandler;
import dev.fararoni.bus.agent.api.ui.model.AgentState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class AgentDashboard implements Runnable {
    private final Terminal terminal;
    private final Display display;
    private final AgentState state;
    private final TerminalCapabilityDetector.DisplayMode displayMode;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private volatile Thread dashboardThread;

    private GlobalStatusBar globalStatusBar;
    private GlobalKeyboardHandler keyboardHandler;

    private final String[] spinner = {"✶", "✸", "✹", "✺", "✹", "✸"};
    private final String[] alternativeSpinner = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private int spinnerIndex = 0;

    private static final int UPDATE_INTERVAL_MS = 150;
    private static final int SPINNER_SPEED = 3;

    public AgentDashboard(Terminal terminal, AgentState state, TerminalCapabilityDetector.DisplayMode displayMode) {
        this.terminal = terminal;
        this.state = state;
        this.displayMode = displayMode;
        this.display = new Display(terminal, true);

        initializeGlobalStatusBar();
    }

    private void initializeGlobalStatusBar() {
        try {
            OutputCoordinator outputCoordinator = new OutputCoordinator(displayMode);

            globalStatusBar = GlobalStatusBar.createOptimal(terminal, displayMode, outputCoordinator);

            keyboardHandler = GlobalKeyboardHandler.create(terminal, globalStatusBar);

            configureAgentStateIntegration();
        } catch (Exception e) {
            globalStatusBar = null;
            keyboardHandler = null;
        }
    }

    private void configureAgentStateIntegration() {
        if (globalStatusBar != null) {
            globalStatusBar.setCustomMessage("Agent: " + state.getMainGoal());
            globalStatusBar.addSessionTokens(state.getTokensUsed());
            globalStatusBar.setContextMemoryUsed(estimateContextMemoryUsage());

            if (keyboardHandler != null) {
                keyboardHandler.setCustomEventHandler(this::handleAgentKeyboardEvent);
            }
        }
    }

    private void handleAgentKeyboardEvent(GlobalKeyboardHandler.KeyboardEvent event) {
        switch (event) {
            case ESC -> {
                interrupt();
            }
            case F5 -> {
                if (globalStatusBar != null) {
                    AgentState.Task currentTask = state.getCurrentTask();
                    String message = currentTask != null ?
                        "Current: " + currentTask.getDescription() :
                        "No task in progress";
                    globalStatusBar.showNotification(
                        GlobalStatusBar.SystemNotification.NotificationType.INFO, message);
                }
            }
            case QUESTION_MARK -> {
                showAgentHelp();
            }
        }
    }

    private long estimateContextMemoryUsage() {
        long baseSize = state.getMainGoal().length() * 2;
        baseSize += state.getTasks().size() * 100;
        baseSize += state.getCurrentAction().length() * 2;
        return baseSize;
    }

    private void showAgentHelp() {
        if (globalStatusBar != null) {
            String help = "Agent Dashboard: ESC=interrupt, F5=task info, Shift+Tab=toggle edits, ?=help";
            globalStatusBar.showNotification(
                GlobalStatusBar.SystemNotification.NotificationType.INFO, help, true, 8000);
        }
    }

    public void start() {
        if (running.get()) {
            if (globalStatusBar != null) {
                globalStatusBar.activate();
            }

            if (keyboardHandler != null) {
                keyboardHandler.start();
            }

            dashboardThread = new Thread(this, "AgentDashboard");
            dashboardThread.setDaemon(true);
            dashboardThread.start();
        }
    }

    public void stop() {
        running.set(false);

        if (globalStatusBar != null) {
            globalStatusBar.deactivate();
        }

        if (keyboardHandler != null) {
            keyboardHandler.stop();
        }

        if (dashboardThread != null) {
            try {
                dashboardThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void interrupt() {
        interrupted.set(true);
        state.interrupt();
    }

    public boolean isInterrupted() {
        return interrupted.get();
    }

    @Override
    public void run() {
        int updateCount = 0;

        try {
            terminal.enterRawMode();

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (terminal.reader().ready()) {
                        int key = terminal.reader().read();
                        if (key == 27) {
                            interrupt();
                            break;
                        }
                    }
                } catch (IOException e) {
                }

                renderDashboard(updateCount);

                updateGlobalStatusBar();

                if (updateCount % SPINNER_SPEED == 0) {
                    updateSpinner();
                }

                updateCount++;

                try {
                    Thread.sleep(UPDATE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            renderFallbackDashboard();
        } finally {
            cleanup();
        }
    }

    private void renderDashboard(int updateCount) {
        try {
            List<AttributedString> lines = new ArrayList<>();

            addMainGoalLine(lines);
            lines.add(new AttributedString(""));

            addCurrentActionLine(lines);
            lines.add(new AttributedString(""));

            addTaskTreeLines(lines);
            lines.add(new AttributedString(""));

            addStatisticsLine(lines);
            addControlsLine(lines);

            addSeparatorLine(lines);

            display.resize(terminal.getHeight(), terminal.getWidth());
            display.update(lines, 0);
        } catch (Exception e) {
            renderFallbackDashboard();
        }
    }

    private void addMainGoalLine(List<AttributedString> lines) {
        AttributedStyle style = displayMode.supportsColors() ?
            AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold() :
            AttributedStyle.DEFAULT.bold();

        lines.add(new AttributedStringBuilder()
            .style(style)
            .append("⏺ " + state.getMainGoal())
            .toAttributedString());
    }

    private void addCurrentActionLine(List<AttributedString> lines) {
        String[] currentSpinner = displayMode.supportsColors() ? spinner : alternativeSpinner;
        String spinChar = currentSpinner[spinnerIndex % currentSpinner.length];

        long elapsed = state.getElapsedSeconds();
        double tokensK = state.getTokensUsed() / 1000.0;
        double progress = state.getProgressPercentage();

        String stats = String.format("(esc to interrupt · %ds · ↓ %.1fk tokens · %.1f%%)",
            elapsed, tokensK, progress);

        AttributedStyle actionStyle = getStyleForExecutionState();
        AttributedStyle statsStyle = displayMode.supportsColors() ?
            AttributedStyle.DEFAULT.faint() :
            AttributedStyle.DEFAULT;

        lines.add(new AttributedStringBuilder()
            .style(actionStyle)
            .append(spinChar + " " + state.getCurrentAction() + " ")
            .style(statsStyle)
            .append(stats)
            .toAttributedString());
    }

    private void addTaskTreeLines(List<AttributedString> lines) {
        List<AgentState.Task> tasks = state.getTasks();

        if (tasks.isEmpty()) {
            lines.add(new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.faint())
                .append("  ⎿ No tasks planned yet...")
                .toAttributedString());
            return;
        }

        for (int i = 0; i < tasks.size(); i++) {
            AgentState.Task task = tasks.get(i);

            String treePrefix = (i == tasks.size() - 1) ? "  ⎿  " : "  │  ";

            AttributedStyle taskStyle = getStyleForTaskStatus(task.getStatus());

            lines.add(new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.faint())
                .append(treePrefix)
                .style(taskStyle)
                .append(task.getFormattedLine())
                .toAttributedString());
        }
    }

    private void addStatisticsLine(List<AttributedString> lines) {
        var stats = state.getStats();

        String progressBar = createProgressBar((int) stats.progressPercentage());
        String statsText = String.format("Progress: %s %d/%d tasks · %ds elapsed",
            progressBar,
            stats.completedTasks(),
            stats.totalTasks(),
            stats.elapsedSeconds());

        AttributedStyle style = displayMode.supportsColors() ?
            AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE) :
            AttributedStyle.DEFAULT;

        lines.add(new AttributedStringBuilder()
            .style(style)
            .append(statsText)
            .toAttributedString());
    }

    private void addControlsLine(List<AttributedString> lines) {
        String controlsText = state.getExecutionState() == AgentState.AgentExecutionState.INTERRUPTED ?
            "⏸ Execution interrupted by user" :
            "⌨️ Press ESC to interrupt execution";

        AttributedStyle style = interrupted.get() ?
            (displayMode.supportsColors() ? AttributedStyle.DEFAULT.foreground(AttributedStyle.RED) : AttributedStyle.DEFAULT.bold()) :
            (displayMode.supportsColors() ? AttributedStyle.DEFAULT.faint() : AttributedStyle.DEFAULT);

        lines.add(new AttributedStringBuilder()
            .style(style)
            .append(controlsText)
            .toAttributedString());
    }

    private void addSeparatorLine(List<AttributedString> lines) {
        int width = Math.min(terminal.getWidth(), 80);
        String separator = "─".repeat(width);

        lines.add(new AttributedStringBuilder()
            .style(displayMode.supportsColors() ? AttributedStyle.DEFAULT.faint() : AttributedStyle.DEFAULT)
            .append(separator)
            .toAttributedString());
    }

    private String createProgressBar(int percentage) {
        int width = 20;
        int filled = (percentage * width) / 100;
        String bar = "█".repeat(filled) + "░".repeat(width - filled);
        return "[" + bar + "]";
    }

    private AttributedStyle getStyleForExecutionState() {
        if (!displayMode.supportsColors()) return AttributedStyle.DEFAULT.bold();

        return switch (state.getExecutionState()) {
            case EXECUTING -> AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold();
            case FAILED, INTERRUPTED -> AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold();
            case COMPLETED -> AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
            case PAUSED -> AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold();
            default -> AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold();
        };
    }

    private AttributedStyle getStyleForTaskStatus(AgentState.TaskStatus status) {
        if (!displayMode.supportsColors()) {
            return status == AgentState.TaskStatus.IN_PROGRESS ?
                AttributedStyle.DEFAULT.bold() : AttributedStyle.DEFAULT;
        }

        return switch (status) {
            case COMPLETED -> AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
            case IN_PROGRESS -> AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold();
            case FAILED -> AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
            case PENDING -> AttributedStyle.DEFAULT.faint();
        };
    }

    private void updateSpinner() {
        spinnerIndex++;
    }

    private void updateGlobalStatusBar() {
        if (globalStatusBar != null && globalStatusBar.isActive()) {
            try {
                globalStatusBar.addSessionTokens(0);

                globalStatusBar.setContextMemoryUsed(estimateContextMemoryUsage());

                String currentAction = state.getCurrentAction();
                if (currentAction != null && !currentAction.isBlank()) {
                    globalStatusBar.setCustomMessage(currentAction);
                }

                if (state.getExecutionState() == AgentState.AgentExecutionState.EXECUTING) {
                    globalStatusBar.setBackgroundTasks(1);
                } else {
                    globalStatusBar.setBackgroundTasks(0);
                }

                checkForStateChangeNotifications();
            } catch (Exception e) {
            }
        }
    }

    private void checkForStateChangeNotifications() {
        if (globalStatusBar == null) return;

        AgentState.AgentExecutionState currentState = state.getExecutionState();

        if (currentState == AgentState.AgentExecutionState.COMPLETED) {
            globalStatusBar.showTaskCompletedNotification("Agent execution");
        }

        if (currentState == AgentState.AgentExecutionState.FAILED) {
            globalStatusBar.showNotification(
                GlobalStatusBar.SystemNotification.NotificationType.ERROR,
                "Agent execution failed");
        }

        long memoryUsage = estimateContextMemoryUsage();
        if (memoryUsage > 50000) {
            globalStatusBar.showHighContextMemoryNotification();
        }
    }

    private void renderFallbackDashboard() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Agent: " + state.getMainGoal());
        System.out.println("=".repeat(60));

        System.out.println("Current: " + state.getCurrentAction());
        System.out.printf("Progress: %.1f%% (%ds elapsed, %d tokens)\n",
            state.getProgressPercentage(), state.getElapsedSeconds(), state.getTokensUsed());

        System.out.println("\nTasks:");
        for (AgentState.Task task : state.getTasks()) {
            System.out.println("  " + task.getFormattedLine());
        }

        System.out.println("\n" + "=".repeat(60));
        if (interrupted.get()) {
            System.out.println("INTERRUPTED - Press any key to continue");
        } else {
            System.out.println("Press ESC to interrupt execution");
        }
    }

    private void cleanup() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException e) {
        }
    }

    public static AgentDashboard createOptimal(Terminal terminal, AgentState state,
                                              TerminalCapabilityDetector.DisplayMode displayMode) {
        return new AgentDashboard(terminal, state, displayMode);
    }

    public static AgentDashboard createDebug(Terminal terminal, AgentState state) {
        return new AgentDashboard(terminal, state, TerminalCapabilityDetector.DisplayMode.RICH);
    }

    public DashboardStats getStats() {
        return new DashboardStats(
            running.get(),
            interrupted.get(),
            displayMode,
            state.getStats(),
            globalStatusBar != null ? globalStatusBar.getSessionStats() : null,
            keyboardHandler != null ? keyboardHandler.isRunning() : false
        );
    }

    public record DashboardStats(
        boolean isRunning,
        boolean isInterrupted,
        TerminalCapabilityDetector.DisplayMode displayMode,
        AgentState.AgentStats agentStats,
        GlobalStatusBar.SessionStats globalStatusStats,
        boolean keyboardHandlerActive
    ) {
        public String getFormattedStats() {
            String globalStats = globalStatusStats != null ?
                String.format("Memory: %s, Tokens: %s, Tasks: %d, Edits: %s",
                    globalStatusStats.getFormattedMemory(),
                    globalStatusStats.getFormattedTokens(),
                    globalStatusStats.backgroundTasks(),
                    globalStatusStats.editsAccepted() ? "ON" : "OFF") :
                "Not available";

            return String.format("""
                Dashboard Statistics v1.0.0:
                   - Running: %s
                   - Interrupted: %s
                   - Display Mode: %s
                   - Keyboard Handler: %s
                   - Global Status: %s
                   - Agent Stats: %s
                """,
                isRunning ? "Yes" : "No",
                isInterrupted ? "Yes" : "No",
                displayMode.name(),
                keyboardHandlerActive ? "Active" : "Inactive",
                globalStats,
                agentStats.getFormattedStats()
            );
        }
    }
}
