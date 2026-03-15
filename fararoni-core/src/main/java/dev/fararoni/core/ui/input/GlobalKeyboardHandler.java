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
package dev.fararoni.core.ui.input;

import org.jline.terminal.Terminal;

import dev.fararoni.core.ui.statusbar.GlobalStatusBar;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class GlobalKeyboardHandler implements Runnable {
    public enum KeyboardEvent {
        SHIFT_TAB("Shift+Tab", "Toggle accept edits"),
        QUESTION_MARK("?", "Show help"),
        ESC("ESC", "Dismiss notifications"),
        F5("F5", "Refresh"),
        CTRL_SHIFT_S("Ctrl+Shift+S", "Show statistics"),
        CTRL_SHIFT_T("Ctrl+Shift+T", "Show background tasks"),
        UNKNOWN("", "Unknown key combination");

        private final String keyName;
        private final String description;

        KeyboardEvent(String keyName, String description) {
            this.keyName = keyName;
            this.description = description;
        }

        public String getKeyName() { return keyName; }
        public String getDescription() { return description; }
    }

    public interface KeyboardEventHandler {
        void handleKeyboardEvent(KeyboardEvent event);
    }

    private final Terminal terminal;
    private final GlobalStatusBar statusBar;
    private final BlockingQueue<KeyboardEvent> eventQueue;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    private volatile Thread keyboardThread;

    private KeyboardEventHandler globalEventHandler;
    private Consumer<KeyboardEvent> customEventHandler;

    private static final int KEYBOARD_POLL_INTERVAL_MS = 50;
    private static final int EVENT_QUEUE_CAPACITY = 100;

    public GlobalKeyboardHandler(Terminal terminal, GlobalStatusBar statusBar) {
        this.terminal = terminal;
        this.statusBar = statusBar;
        this.eventQueue = new LinkedBlockingQueue<>(EVENT_QUEUE_CAPACITY);

        this.globalEventHandler = this::handleDefaultKeyboardEvent;
    }

    public void setCustomEventHandler(Consumer<KeyboardEvent> handler) {
        this.customEventHandler = handler;
    }

    public void setGlobalEventHandler(KeyboardEventHandler handler) {
        this.globalEventHandler = handler;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            keyboardThread = Thread.ofVirtual()
                .name("GlobalKeyboardHandler")
                .start(this);
        }
    }

    public void stop() {
        running.set(false);
        interrupted.set(true);

        if (keyboardThread != null) {
            keyboardThread.interrupt();
            try {
                keyboardThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        try {
            terminal.enterRawMode();

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (terminal.reader().ready()) {
                        KeyboardEvent event = detectKeyboardEvent();
                        if (event != KeyboardEvent.UNKNOWN) {
                            eventQueue.offer(event);
                            processEvent(event);
                        }
                    }

                    Thread.sleep(KEYBOARD_POLL_INTERVAL_MS);
                } catch (IOException e) {
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.ERROR,
                "Keyboard handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private KeyboardEvent detectKeyboardEvent() throws IOException {
        int firstChar = terminal.reader().read();

        if (firstChar == 27) {
            if (terminal.reader().ready()) {
                return detectEscapeSequence();
            } else {
                return KeyboardEvent.ESC;
            }
        }

        if (firstChar == 63) {
            return KeyboardEvent.QUESTION_MARK;
        }

        if (firstChar == 27 && terminal.reader().ready()) {
            return detectFunctionKey();
        }

        if (firstChar >= 1 && firstChar <= 26) {
            return detectControlCombination(firstChar);
        }

        if (firstChar == 9) {
            return detectTabVariation();
        }

        if (firstChar == 27) {
            return detectShiftTab();
        }

        return KeyboardEvent.UNKNOWN;
    }

    private KeyboardEvent detectEscapeSequence() throws IOException {
        if (terminal.reader().ready()) {
            int next = terminal.reader().read();

            if (next == 91) {
                return detectAnsiSequence();
            }

            if (next == 79) {
                return detectApplicationSequence();
            }
        }

        return KeyboardEvent.ESC;
    }

    private KeyboardEvent detectAnsiSequence() throws IOException {
        if (terminal.reader().ready()) {
            int code = terminal.reader().read();

            if (code == 90) {
                return KeyboardEvent.SHIFT_TAB;
            }

            if (code == 49) {
                return detectF5Sequence();
            }
        }

        return KeyboardEvent.UNKNOWN;
    }

    private KeyboardEvent detectApplicationSequence() throws IOException {
        if (terminal.reader().ready()) {
            int code = terminal.reader().read();
        }

        return KeyboardEvent.UNKNOWN;
    }

    private KeyboardEvent detectFunctionKey() throws IOException {
        return KeyboardEvent.UNKNOWN;
    }

    private KeyboardEvent detectF5Sequence() throws IOException {
        StringBuilder sequence = new StringBuilder();
        while (terminal.reader().ready()) {
            char c = (char) terminal.reader().read();
            sequence.append(c);
            if (c == '~') break;
        }

        if ("5~".equals(sequence.toString())) {
            return KeyboardEvent.F5;
        }

        return KeyboardEvent.UNKNOWN;
    }

    private KeyboardEvent detectControlCombination(int ctrlChar) {
        return KeyboardEvent.UNKNOWN;
    }

    private KeyboardEvent detectTabVariation() {
        return KeyboardEvent.UNKNOWN;
    }

    private KeyboardEvent detectShiftTab() throws IOException {
        if (terminal.reader().ready()) {
            int bracket = terminal.reader().read();
            if (bracket == 91 && terminal.reader().ready()) {
                int z = terminal.reader().read();
                if (z == 90) {
                    return KeyboardEvent.SHIFT_TAB;
                }
            }
        }

        return KeyboardEvent.ESC;
    }

    private void processEvent(KeyboardEvent event) {
        if (customEventHandler != null) {
            try {
                customEventHandler.accept(event);
            } catch (Exception e) {
                statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.ERROR,
                    "Custom handler error: " + e.getMessage());
            }
        }

        if (globalEventHandler != null) {
            try {
                globalEventHandler.handleKeyboardEvent(event);
            } catch (Exception e) {
                statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.ERROR,
                    "Global handler error: " + e.getMessage());
            }
        }
    }

    private void handleDefaultKeyboardEvent(KeyboardEvent event) {
        switch (event) {
            case SHIFT_TAB -> {
                boolean newState = statusBar.toggleEditsAccepted();
                statusBar.setCustomMessage(
                    "Edit acceptance " + (newState ? "enabled" : "disabled") + " (Shift+Tab)");

                Thread.ofVirtual().start(() -> {
                    try {
                        Thread.sleep(2000);
                        statusBar.clearCustomMessage();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            case ESC -> {
                if (statusBar.hasActiveNotification()) {
                    statusBar.dismissNotification();
                }
            }

            case QUESTION_MARK -> {
                showHelpNotification();
            }

            case F5 -> {
                statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.INFO,
                    "Refresh triggered");
            }

            case CTRL_SHIFT_S -> {
                showStatisticsNotification();
            }

            case CTRL_SHIFT_T -> {
                showBackgroundTasksNotification();
            }

            default -> {
            }
        }
    }

    private void showHelpNotification() {
        StringBuilder help = new StringBuilder();
        help.append("Keyboard Shortcuts: ");
        help.append("Shift+Tab=toggle edits, ");
        help.append("ESC=dismiss, ");
        help.append("F5=refresh, ");
        help.append("?=help");

        statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.INFO,
            help.toString(), true, 8000);
    }

    private void showStatisticsNotification() {
        GlobalStatusBar.SessionStats stats = statusBar.getSessionStats();
        String message = String.format("Session: %s memory, %s tokens, %d tasks",
            stats.getFormattedMemory(), stats.getFormattedTokens(), stats.backgroundTasks());

        statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.INFO,
            message, true, 5000);
    }

    private void showBackgroundTasksNotification() {
        int taskCount = statusBar.getBackgroundTaskCount();
        String message = taskCount > 0 ?
            taskCount + " background tasks running" :
            "No background tasks";

        statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.INFO,
            message, true, 3000);
    }

    private void cleanup() {
        try {
            terminal.close();
        } catch (IOException e) {
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isInterrupted() {
        return interrupted.get();
    }

    public BlockingQueue<KeyboardEvent> getEventQueue() {
        return eventQueue;
    }

    public static GlobalKeyboardHandler create(Terminal terminal, GlobalStatusBar statusBar) {
        return new GlobalKeyboardHandler(terminal, statusBar);
    }

    public static GlobalKeyboardHandler createDebug(Terminal terminal, GlobalStatusBar statusBar) {
        GlobalKeyboardHandler handler = new GlobalKeyboardHandler(terminal, statusBar);

        handler.setCustomEventHandler(event -> {
            statusBar.showNotification(GlobalStatusBar.SystemNotification.NotificationType.INFO,
                "Debug: " + event.getKeyName() + " detected");
        });

        return handler;
    }
}
