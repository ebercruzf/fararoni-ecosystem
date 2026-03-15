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

import dev.fararoni.bus.agent.api.interaction.AgentUserInterface;
import dev.fararoni.bus.agent.api.ui.model.DashboardModel;
import dev.fararoni.bus.agent.api.ui.model.LogEntry;
import dev.fararoni.bus.agent.api.ui.model.MenuModel;
import dev.fararoni.bus.agent.api.ui.model.MenuItem;
import dev.fararoni.bus.agent.api.ui.model.StatusBarModel;
import dev.fararoni.bus.agent.api.ui.model.TaskState;
import dev.fararoni.bus.agent.api.ui.model.TaskTreeModel;
import dev.fararoni.core.cli.JLineConfig;
import dev.fararoni.core.ui.renderers.CompactHeaderRenderer;
import dev.fararoni.core.ui.renderers.LiveProgressRenderer;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;

import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class JLineAgentUI implements AgentUserInterface {
    private static final Logger LOG = Logger.getLogger(JLineAgentUI.class.getName());

    private static final AttributedStyle STYLE_INFO =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);

    private static final AttributedStyle STYLE_SUCCESS =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);

    private static final AttributedStyle STYLE_WARN =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);

    private static final AttributedStyle STYLE_ERROR =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);

    private static final AttributedStyle STYLE_HEADER =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold();

    private static final AttributedStyle STYLE_SECONDARY =
        AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT);

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Terminal terminal;
    private final LineReader lineReader;
    private final PrintWriter writer;
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private volatile String loadingMessage = "";

    private final LiveProgressRenderer progressRenderer;
    private final CompactHeaderRenderer headerRenderer;
    private final ProgressAnimator progressAnimator;

    public JLineAgentUI(Terminal terminal, LineReader lineReader) {
        this.terminal = Objects.requireNonNull(terminal, "terminal no puede ser null");
        this.lineReader = Objects.requireNonNull(lineReader, "lineReader no puede ser null");
        this.writer = terminal.writer();

        this.progressRenderer = new LiveProgressRenderer(terminal);
        this.headerRenderer = new CompactHeaderRenderer(terminal);
        this.progressAnimator = new ProgressAnimator(progressRenderer);

        LOG.fine("[JLineAgentUI] Inicializado con terminal: " + terminal.getName());
    }

    @Override
    public String prompt(String message) {
        try {
            return lineReader.readLine(message + ": ");
        } catch (UserInterruptException e) {
            throw new UserCancelledException("Prompt cancelado con Ctrl+C");
        } catch (EndOfFileException e) {
            throw new UserCancelledException("Prompt cancelado con Ctrl+D");
        }
    }

    @Override
    public String promptSecret(String message) {
        try {
            return lineReader.readLine(message + ": ", '*');
        } catch (UserInterruptException e) {
            throw new UserCancelledException("Prompt secreto cancelado con Ctrl+C");
        } catch (EndOfFileException e) {
            throw new UserCancelledException("Prompt secreto cancelado con Ctrl+D");
        }
    }

    @Override
    public boolean confirm(String message) {
        try {
            String response = lineReader.readLine(message + " [y/N]: ");
            if (response == null) return false;

            String lower = response.trim().toLowerCase();
            return lower.equals("y") ||
                   lower.equals("yes") ||
                   lower.equals("si") ||
                   lower.equals("s");
        } catch (UserInterruptException | EndOfFileException e) {
            return false;
        }
    }

    @Override
    public String selectFromMenu(MenuModel menu) {
        Objects.requireNonNull(menu, "menu no puede ser null");

        writer.println();
        printStyled("  " + menu.title(), STYLE_HEADER);
        writer.println();

        for (int i = 0; i < menu.items().size(); i++) {
            MenuItem item = menu.items().get(i);
            String prefix = i == menu.selectedIndex() ? " > " : "   ";
            String shortcut = item.hasShortcut() ? "[" + item.shortcut() + "] " : "    ";

            if (item.enabled()) {
                writer.println(prefix + shortcut + item.label());
            } else {
                printStyled(prefix + shortcut + item.label() + " (deshabilitado)", STYLE_SECONDARY);
            }
        }
        writer.println();
        writer.flush();

        try {
            String input = lineReader.readLine("Seleccion: ");
            if (input == null || input.isBlank()) {
                MenuItem selected = menu.getSelectedItem();
                return selected.enabled() ? selected.id() : null;
            }

            int byShortcut = menu.findByShortcut(input);
            if (byShortcut >= 0) {
                return menu.items().get(byShortcut).id();
            }

            try {
                int index = Integer.parseInt(input) - 1;
                if (index >= 0 && index < menu.size()) {
                    MenuItem item = menu.items().get(index);
                    return item.enabled() ? item.id() : null;
                }
            } catch (NumberFormatException ignored) {
            }

            return null;
        } catch (UserInterruptException | EndOfFileException e) {
            throw new UserCancelledException("Menu cancelado");
        }
    }

    @Override
    public void info(String message) {
        printStyled(message, STYLE_INFO);
    }

    @Override
    public void success(String message) {
        printStyled("[OK] " + message, STYLE_SUCCESS);
    }

    @Override
    public void warn(String message) {
        printStyled("[WARN] " + message, STYLE_WARN);
    }

    @Override
    public void error(String message) {
        printStyled("[ERROR] " + message, STYLE_ERROR);
    }

    @Override
    public void printMarkdown(String markdown) {
        writer.println(markdown);
        writer.flush();
    }

    @Override
    public void renderDashboard(DashboardModel model) {
        Objects.requireNonNull(model, "model no puede ser null");

        int width = Math.min(getTerminalWidth(), 70);
        String line = "═".repeat(width);
        String thinLine = "─".repeat(width);

        writer.println();

        printStyled("╔" + line + "╗", STYLE_HEADER);
        printCentered("FARARONI AGENT DASHBOARD", width, STYLE_HEADER);
        printStyled("╠" + line + "╣", STYLE_HEADER);

        AttributedStyle statusStyle = getStatusStyle(model.agentStatus());
        printRow("Status", model.agentStatus(), width, statusStyle);

        String tokensText = String.format("%,d / %,d (%d%%)",
            model.tokensUsed(), model.tokensLimit(), model.getTokenUsagePercent());
        printRow("Tokens", tokensText, width, STYLE_INFO);

        if (!model.activeSkills().isEmpty()) {
            String skillsText = "[" + String.join("] [", model.activeSkills()) + "]";
            printRow("Skills", skillsText, width, STYLE_SECONDARY);
        }

        if (model.hasCurrentTask()) {
            printRow("Task", model.currentTask(), width, STYLE_INFO);
        }

        if (!model.recentLogs().isEmpty()) {
            printStyled("╠" + thinLine + "╣", STYLE_SECONDARY);
            printStyled("║  LOGS RECIENTES" + " ".repeat(width - 16) + "║", STYLE_SECONDARY);

            for (LogEntry log : model.recentLogs()) {
                String time = log.timestamp().atZone(java.time.ZoneId.systemDefault())
                    .format(TIME_FORMATTER);
                String logLine = String.format("[%s] %-5s %s", time, log.level(), log.message());

                if (logLine.length() > width - 4) {
                    logLine = logLine.substring(0, width - 7) + "...";
                }

                AttributedStyle logStyle = getLogStyle(log.level());
                printStyled("║  " + logLine + " ".repeat(Math.max(0, width - logLine.length() - 2)) + "║", logStyle);
            }
        }

        printStyled("╚" + line + "╝", STYLE_HEADER);
        writer.println();
        writer.flush();
    }

    @Override
    public void updateStatusBar(StatusBarModel model) {
        Objects.requireNonNull(model, "model no puede ser null");

        if (model.isEmpty()) {
            return;
        }

        int width = getTerminalWidth();
        StringBuilder sb = new StringBuilder();

        if (model.leftText() != null) {
            if (model.isLoading()) {
                sb.append("⠋ ");
            }
            sb.append(model.leftText());
        }

        if (model.hasProgress()) {
            int barWidth = 20;
            int filled = (model.progressPercent() * barWidth) / 100;
            sb.append(" [");
            sb.append("█".repeat(filled));
            sb.append("░".repeat(barWidth - filled));
            sb.append("] ");
            sb.append(model.progressPercent()).append("%");
        }

        if (model.rightText() != null) {
            int currentLen = sb.length();
            int rightLen = model.rightText().length();
            int padding = width - currentLen - rightLen - 2;
            if (padding > 0) {
                sb.append(" ".repeat(padding));
            }
            sb.append(model.rightText());
        }

        AttributedStyle style = model.isLoading() ? STYLE_INFO : STYLE_SECONDARY;
        printStyled(sb.toString(), style);
    }

    @Override
    public void setLoading(boolean loading, String message) {
        this.isLoading.set(loading);
        this.loadingMessage = message != null ? message : "";

        if (loading && message != null) {
            printStyled("⠋ " + message, STYLE_INFO);
        }
    }

    @Override
    public void updateProgress(TaskTreeModel model) {
        Objects.requireNonNull(model, "model no puede ser null");

        if (model.rootState() == TaskState.RUNNING) {
            if (!progressAnimator.isRunning()) {
                progressAnimator.start(model);
            } else {
                progressAnimator.updateModel(model);
            }
        } else {
            progressAnimator.updateModel(model);
            if (model.isComplete() && progressAnimator.isRunning()) {
                progressAnimator.stop();
            }
        }
    }

    @Override
    public void onProcessComplete(String summary) {
        if (progressAnimator.isRunning()) {
            progressAnimator.stop();
        }

        headerRenderer.printCompactHeader("Conversation compacted");

        if (summary != null && !summary.isEmpty()) {
            success(summary);
        }
    }

    @Override
    public void clearScreen() {
        try {
            terminal.puts(InfoCmp.Capability.clear_screen);
            terminal.flush();
        } catch (Exception e) {
            for (int i = 0; i < 50; i++) {
                writer.println();
            }
            writer.flush();
        }
    }

    @Override
    public int getTerminalWidth() {
        int width = terminal.getWidth();
        return width > 0 ? width : 80;
    }

    @Override
    public int getTerminalHeight() {
        int height = terminal.getHeight();
        return height > 0 ? height : 24;
    }

    private void printStyled(String text, AttributedStyle style) {
        String styled = new AttributedStringBuilder()
            .style(style)
            .append(text)
            .toAnsi(terminal);
        writer.println(styled);
        writer.flush();
    }

    private void printRow(String label, String value, int width, AttributedStyle valueStyle) {
        String row = String.format("║  %-12s: %s", label, value);
        int padding = width - row.length() + 1;
        if (padding > 0) {
            row = row + " ".repeat(padding) + "║";
        }

        String labelPart = "║  " + label + ": ";
        String styledValue = new AttributedStringBuilder()
            .style(valueStyle)
            .append(value)
            .toAnsi(terminal);

        int valuePadding = width - labelPart.length() - value.length() + 1;
        writer.println(labelPart + styledValue + " ".repeat(Math.max(0, valuePadding)) + "║");
        writer.flush();
    }

    private void printCentered(String text, int width, AttributedStyle style) {
        int padding = (width - text.length()) / 2;
        String centered = "║" + " ".repeat(padding) + text +
                         " ".repeat(width - padding - text.length()) + "║";
        printStyled(centered, style);
    }

    private AttributedStyle getStatusStyle(String status) {
        return switch (status.toUpperCase()) {
            case "IDLE" -> STYLE_SECONDARY;
            case "THINKING", "ANALYZING" -> STYLE_INFO;
            case "EXECUTING" -> STYLE_SUCCESS;
            case "WAITING" -> STYLE_WARN;
            case "ERROR" -> STYLE_ERROR;
            default -> STYLE_INFO;
        };
    }

    private AttributedStyle getLogStyle(String level) {
        return switch (level.toUpperCase()) {
            case "ERROR" -> STYLE_ERROR;
            case "WARN" -> STYLE_WARN;
            case "SUCCESS" -> STYLE_SUCCESS;
            case "DEBUG" -> STYLE_SECONDARY;
            default -> STYLE_INFO;
        };
    }
}
