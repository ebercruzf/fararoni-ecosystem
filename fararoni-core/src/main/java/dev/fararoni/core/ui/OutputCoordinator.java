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

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.Status;

import dev.fararoni.core.core.spi.UserNotifier;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class OutputCoordinator implements UserNotifier {
    private final TerminalCapabilityDetector.DisplayMode displayMode;
    private final PrintStream stdout;
    private final PrintStream stderr;

    private final Terminal terminal;

    private final AtomicBoolean statusLineActive = new AtomicBoolean(false);
    private final AtomicReference<String> currentStatusLine = new AtomicReference<>("");
    private final BlockingQueue<PendingMessage> pendingMessages = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedQueue<String> logBuffer = new ConcurrentLinkedQueue<>();

    private final CopyOnWriteArrayList<AttributedString> currentStatusLines = new CopyOnWriteArrayList<>();

    private final AtomicBoolean silenceMode = new AtomicBoolean(false);

    private final boolean enableBuffering;
    private final boolean enableTimestamps;

    public OutputCoordinator(TerminalCapabilityDetector.DisplayMode displayMode) {
        this(displayMode, null);
    }

    public OutputCoordinator(TerminalCapabilityDetector.DisplayMode displayMode, Terminal terminal) {
        this.displayMode = displayMode;
        this.terminal = terminal;
        this.stdout = System.out;
        this.stderr = System.err;
        this.enableBuffering = displayMode.supportsAnimation();
        this.enableTimestamps = displayMode == TerminalCapabilityDetector.DisplayMode.COMPATIBLE;
    }

    public void print(String message) {
        print(message, MessageType.NORMAL);
    }

    public void print(String message, MessageType type) {
        var pendingMessage = new PendingMessage(message, type, LocalDateTime.now());

        if (statusLineActive.get() && enableBuffering && type != MessageType.URGENT) {
            pendingMessages.offer(pendingMessage);
        } else {
            printMessage(pendingMessage);
        }
    }

    public void printUrgent(String message) {
        if (statusLineActive.get()) {
            clearStatusLine();
        }
        print(message, MessageType.URGENT);
        if (statusLineActive.get()) {
            redrawStatusLine();
        }
    }

    public void printError(String message) {
        if (statusLineActive.get()) {
            clearStatusLine();
        }

        String formatted = formatErrorMessage(message);
        stderr.println(formatted);
        stderr.flush();

        if (statusLineActive.get()) {
            redrawStatusLine();
        }
    }

    public void printDebug(String message) {
        if (Boolean.getBoolean("llm.debug")) {
            print("[DEBUG] " + message, MessageType.DEBUG);
        }
    }

    public void setSilenceMode(boolean active) {
        silenceMode.set(active);
    }

    public boolean isSilenceMode() {
        return silenceMode.get();
    }

    public synchronized void printSafe(String message) {
        printSafe(message, false);
    }

    public synchronized void printSafe(String message, boolean force) {
        if (silenceMode.get() && !force) {
            return;
        }

        if (terminal == null) {
            printUrgent(message);
            return;
        }

        Status status = Status.getStatus(terminal);

        if (status != null) {
            status.update(null);
        }

        terminal.writer().println(message);
        terminal.flush();

        if (status != null && !currentStatusLines.isEmpty()) {
            status.update(currentStatusLines);
        }
    }

    public void setCurrentStatusLines(List<AttributedString> lines) {
        currentStatusLines.clear();
        if (lines != null) {
            currentStatusLines.addAll(lines);
        }
    }

    public synchronized void printAnsiBlock(String ansiBlock) {
        if (silenceMode.get()) {
            return;
        }

        if (terminal == null) {
            stdout.print(ansiBlock);
            stdout.flush();
            return;
        }

        Status status = Status.getStatus(terminal);

        if (status != null) {
            status.update(null);
        }

        terminal.writer().print(ansiBlock);
        terminal.flush();

        if (status != null && !currentStatusLines.isEmpty()) {
            status.update(currentStatusLines);
        }
    }

    public boolean isTerminalAware() {
        return terminal != null;
    }

    @Override
    public void notify(String message) {
        printUrgent(message);
    }

    @Override
    public void notify(String message, boolean urgent) {
        if (urgent) {
            printUrgent(message);
        } else {
            print(message, MessageType.NORMAL);
        }
    }

    public void activateStatusLine(String initialMessage) {
        if (!displayMode.supportsAnimation()) {
            print(initialMessage);
            return;
        }

        statusLineActive.set(true);
        currentStatusLine.set(initialMessage);
        drawStatusLine(initialMessage);
    }

    public void updateStatusLine(String message) {
        if (!statusLineActive.get()) {
            return;
        }

        currentStatusLine.set(message);
        if (displayMode.supportsAnimation()) {
            redrawStatusLine();
        }
    }

    public void deactivateStatusLine() {
        if (!statusLineActive.get()) {
            return;
        }

        statusLineActive.set(false);

        if (displayMode.supportsAnimation()) {
            clearStatusLine();
        }

        flushPendingMessages();
    }

    private void flushPendingMessages() {
        PendingMessage message;
        while ((message = pendingMessages.poll()) != null) {
            printMessage(message);
        }
    }

    private void drawStatusLine(String message) {
        if (displayMode == TerminalCapabilityDetector.DisplayMode.RICH) {
            stdout.print(ansi().cursorLeft(1000).eraseLine());
            stdout.print(ansi().render(message));
            stdout.flush();
        } else if (displayMode == TerminalCapabilityDetector.DisplayMode.SIMPLE) {
            stdout.print("\r" + message);
            stdout.flush();
        }
    }

    private void redrawStatusLine() {
        String current = currentStatusLine.get();
        if (current != null && !current.isEmpty()) {
            drawStatusLine(current);
        }
    }

    private void clearStatusLine() {
        if (displayMode.supportsColors()) {
            stdout.print(ansi().cursorLeft(1000).eraseLine());
        } else {
            stdout.print("\r" + " ".repeat(80) + "\r");
        }
        stdout.flush();
    }

    private void printMessage(PendingMessage pendingMessage) {
        String message = formatMessage(pendingMessage);

        PrintStream target = pendingMessage.type() == MessageType.ERROR ? stderr : stdout;
        target.println(message);
        target.flush();

        if (enableTimestamps) {
            logBuffer.offer(String.format("[%s] %s",
                pendingMessage.timestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                pendingMessage.message()));
        }
    }

    private String formatMessage(PendingMessage pendingMessage) {
        String message = pendingMessage.message();
        MessageType type = pendingMessage.type();

        if (!displayMode.supportsColors()) {
            if (enableTimestamps) {
                return String.format("[%s] %s",
                    pendingMessage.timestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    message);
            }
            return message;
        }

        return switch (type) {
            case NORMAL -> message;
            case DEBUG -> ansi().fgBrightBlack().a(message).reset().toString();
            case WARNING -> ansi().fgYellow().a("[WARN] " + message).reset().toString();
            case ERROR -> ansi().fgRed().a("[ERROR] " + message).reset().toString();
            case SUCCESS -> ansi().fgGreen().a("[OK] " + message).reset().toString();
            case URGENT -> ansi().fgBrightRed().bold().a("[ALERT] " + message).reset().toString();
        };
    }

    private String formatErrorMessage(String message) {
        if (enableTimestamps) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            message = String.format("[%s] ERROR: %s", timestamp, message);
        }

        if (displayMode.supportsColors()) {
            return ansi().fgRed().bold().a("[ERROR] ").reset().fgRed().a(message).reset().toString();
        }

        return "ERROR: " + message;
    }

    public CoordinatorStatus getStatus() {
        return new CoordinatorStatus(
            displayMode,
            statusLineActive.get(),
            currentStatusLine.get(),
            pendingMessages.size(),
            logBuffer.size()
        );
    }

    public java.util.List<String> getLogBuffer() {
        return new java.util.ArrayList<>(logBuffer);
    }

    public enum MessageType {
        NORMAL,
        DEBUG,
        WARNING,
        ERROR,
        SUCCESS,
        URGENT
    }

    private record PendingMessage(
        String message,
        MessageType type,
        LocalDateTime timestamp
    ) {}

    public record CoordinatorStatus(
        TerminalCapabilityDetector.DisplayMode displayMode,
        boolean statusLineActive,
        String currentStatusLine,
        int pendingMessages,
        int logBufferSize
    ) {
        public String getFormattedStatus() {
            return String.format("""
                [MEM] Output Coordinator Status:
                   • Display Mode: %s
                   • Status Line Active: %s
                   • Current Status: %s
                   • Pending Messages: %d
                   • Log Buffer Size: %d
                """,
                displayMode.getDescription(),
                statusLineActive ? "Yes" : "No",
                statusLineActive ? "'" + currentStatusLine + "'" : "None",
                pendingMessages,
                logBufferSize
            );
        }
    }
}
