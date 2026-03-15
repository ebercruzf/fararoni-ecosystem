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
package dev.fararoni.core.ui.statusbar;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import dev.fararoni.core.ui.TerminalCapabilityDetector;
import dev.fararoni.core.ui.OutputCoordinator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class GlobalStatusBar {
    private final Terminal terminal;
    private final TerminalCapabilityDetector.DisplayMode displayMode;
    private final OutputCoordinator outputCoordinator;

    private final AtomicBoolean editsAccepted = new AtomicBoolean(true);
    private final AtomicInteger backgroundTasks = new AtomicInteger(0);
    private final AtomicLong contextMemoryUsed = new AtomicLong(0);
    private final AtomicInteger sessionTokens = new AtomicInteger(0);
    private final AtomicReference<SystemNotification> currentNotification = new AtomicReference<>();

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicReference<String> customMessage = new AtomicReference<>();

    public static class SystemNotification {
        public enum NotificationType {
            SUCCESS("[OK]", AttributedStyle.GREEN),
            WARNING("[!]", AttributedStyle.YELLOW),
            ERROR("[X]", AttributedStyle.RED),
            INFO("[i]", AttributedStyle.BLUE);

            private final String icon;
            private final int color;

            NotificationType(String icon, int color) {
                this.icon = icon;
                this.color = color;
            }

            public String getIcon() { return icon; }
            public int getColor() { return color; }
        }

        private final NotificationType type;
        private final String message;
        private final long timestamp;
        private final boolean dismissable;

        public SystemNotification(NotificationType type, String message) {
            this(type, message, true);
        }

        public SystemNotification(NotificationType type, String message, boolean dismissable) {
            this.type = type;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.dismissable = dismissable;
        }

        public NotificationType getType() { return type; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        public boolean isDismissable() { return dismissable; }

        public String getFormattedMessage() {
            return type.getIcon() + " " + message;
        }
    }

    public record SessionStats(
        long contextMemoryBytes,
        int sessionTokens,
        int backgroundTasks,
        boolean editsAccepted,
        String customStatus
    ) {
        public String getFormattedMemory() {
            if (contextMemoryBytes < 1024) {
                return contextMemoryBytes + "B";
            } else if (contextMemoryBytes < 1024 * 1024) {
                return String.format("%.1fKB", contextMemoryBytes / 1024.0);
            } else {
                return String.format("%.1fMB", contextMemoryBytes / (1024.0 * 1024.0));
            }
        }

        public String getFormattedTokens() {
            if (sessionTokens < 1000) {
                return sessionTokens + "";
            } else {
                return String.format("%.1fk", sessionTokens / 1000.0);
            }
        }
    }

    public GlobalStatusBar(Terminal terminal, TerminalCapabilityDetector.DisplayMode displayMode,
                          OutputCoordinator outputCoordinator) {
        this.terminal = terminal;
        this.displayMode = displayMode;
        this.outputCoordinator = outputCoordinator;
    }

    public void activate() {
        if (active.compareAndSet(false, true)) {
            if (displayMode.supportsAnimation()) {
                renderStatusBar();
            }
        }
    }

    public void deactivate() {
        if (active.compareAndSet(true, false)) {
            clearStatusBar();
        }
    }

    public boolean toggleEditsAccepted() {
        boolean newState = !editsAccepted.get();
        editsAccepted.set(newState);

        showNotification(SystemNotification.NotificationType.INFO,
            "Edit acceptance " + (newState ? "enabled" : "disabled"));

        renderStatusBar();
        return newState;
    }

    public void setBackgroundTasks(int count) {
        backgroundTasks.set(count);
        renderStatusBar();
    }

    public void incrementBackgroundTasks() {
        backgroundTasks.incrementAndGet();
        renderStatusBar();
    }

    public void decrementBackgroundTasks() {
        int count = backgroundTasks.decrementAndGet();
        if (count < 0) {
            backgroundTasks.set(0);
        }
        renderStatusBar();
    }

    public void setContextMemoryUsed(long bytes) {
        contextMemoryUsed.set(bytes);
        renderStatusBar();
    }

    public void addSessionTokens(int tokens) {
        sessionTokens.addAndGet(tokens);
        renderStatusBar();
    }

    public void setCustomMessage(String message) {
        customMessage.set(message);
        renderStatusBar();
    }

    public void clearCustomMessage() {
        customMessage.set(null);
        renderStatusBar();
    }

    public void showNotification(SystemNotification.NotificationType type, String message) {
        SystemNotification notification = new SystemNotification(type, message);
        currentNotification.set(notification);
        renderStatusBar();

        if (type == SystemNotification.NotificationType.INFO ||
            type == SystemNotification.NotificationType.SUCCESS) {
            scheduleNotificationDismissal(5000);
        }
    }

    public void showNotification(SystemNotification.NotificationType type, String message,
                                boolean dismissable, long autoDismissMs) {
        SystemNotification notification = new SystemNotification(type, message, dismissable);
        currentNotification.set(notification);
        renderStatusBar();

        if (autoDismissMs > 0) {
            scheduleNotificationDismissal(autoDismissMs);
        }
    }

    public void dismissNotification() {
        currentNotification.set(null);
        renderStatusBar();
    }

    private void scheduleNotificationDismissal(long delayMs) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delayMs);
                dismissNotification();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void renderStatusBar() {
        if (!active.get() || !displayMode.supportsAnimation()) {
            return;
        }

        try {
            String statusLine = buildStatusLine();
            outputCoordinator.updateStatusLine(statusLine);
        } catch (Exception e) {
        }
    }

    private String buildStatusLine() {
        AttributedStringBuilder builder = new AttributedStringBuilder();

        addToggleControls(builder);

        builder.append("  ");

        String customMsg = customMessage.get();
        SystemNotification notification = currentNotification.get();

        if (notification != null) {
            addNotificationSection(builder, notification);
        } else if (customMsg != null && !customMsg.isBlank()) {
            builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                   .append(customMsg);
        } else {
            builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE))
                   .append("Ready");
        }

        addSessionStats(builder);

        return builder.toAttributedString().toAnsi();
    }

    private void addToggleControls(AttributedStringBuilder builder) {
        String toggleSymbol = editsAccepted.get() ? "⏵⏵" : "⏸⏸";
        String toggleText = editsAccepted.get() ? "accept edits on" : "accept edits off";

        AttributedStyle toggleStyle = displayMode.supportsColors() ?
            (editsAccepted.get() ?
                AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN) :
                AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)) :
            AttributedStyle.DEFAULT.bold();

        builder.style(toggleStyle)
               .append(toggleSymbol + " " + toggleText);

        builder.style(AttributedStyle.DEFAULT.faint())
               .append(" (Shift+Tab)");
    }

    private void addNotificationSection(AttributedStringBuilder builder, SystemNotification notification) {
        AttributedStyle notificationStyle = displayMode.supportsColors() ?
            AttributedStyle.DEFAULT.foreground(notification.getType().getColor()) :
            AttributedStyle.DEFAULT.bold();

        builder.style(notificationStyle)
               .append(notification.getFormattedMessage());

        if (notification.isDismissable()) {
            builder.style(AttributedStyle.DEFAULT.faint())
                   .append(" (ESC to dismiss)");
        }
    }

    private void addSessionStats(AttributedStringBuilder builder) {
        SessionStats stats = getSessionStats();

        builder.append("  ");

        if (stats.backgroundTasks() > 0) {
            builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                   .append("[~] " + stats.backgroundTasks() + " tasks  ");
        }

        builder.style(AttributedStyle.DEFAULT.faint())
               .append("[MEM] " + stats.getFormattedMemory() + "  ")
               .append("[TKN] " + stats.getFormattedTokens() + " tokens");

        builder.style(AttributedStyle.DEFAULT.faint())
               .append("  ? for help");
    }

    private void clearStatusBar() {
        if (displayMode.supportsAnimation()) {
            outputCoordinator.deactivateStatusLine();
        }
    }

    public SessionStats getSessionStats() {
        return new SessionStats(
            contextMemoryUsed.get(),
            sessionTokens.get(),
            backgroundTasks.get(),
            editsAccepted.get(),
            customMessage.get()
        );
    }

    public boolean areEditsAccepted() {
        return editsAccepted.get();
    }

    public int getBackgroundTaskCount() {
        return backgroundTasks.get();
    }

    public boolean hasActiveNotification() {
        return currentNotification.get() != null;
    }

    public SystemNotification getCurrentNotification() {
        return currentNotification.get();
    }

    public boolean isActive() {
        return active.get();
    }

    public static GlobalStatusBar createOptimal(Terminal terminal,
                                               TerminalCapabilityDetector.DisplayMode displayMode,
                                               OutputCoordinator outputCoordinator) {
        return new GlobalStatusBar(terminal, displayMode, outputCoordinator);
    }

    public static GlobalStatusBar createDebug(Terminal terminal, OutputCoordinator outputCoordinator) {
        return new GlobalStatusBar(terminal, TerminalCapabilityDetector.DisplayMode.RICH, outputCoordinator);
    }

    public void showAutoUpdateFailedNotification() {
        showNotification(SystemNotification.NotificationType.ERROR,
            "Auto-update failed • Try claude doctor...", false, 0);
    }

    public void showConnectionLostNotification() {
        showNotification(SystemNotification.NotificationType.WARNING,
            "Connection lost • Reconnecting...", false, 0);
    }

    public void showHighContextMemoryNotification() {
        showNotification(SystemNotification.NotificationType.WARNING,
            "High context memory usage • Consider summarizing conversation", true, 10000);
    }

    public void showTaskCompletedNotification(String taskName) {
        showNotification(SystemNotification.NotificationType.SUCCESS,
            "Task completed: " + taskName, true, 3000);
    }

    public void showUpdateAvailableNotification(String version) {
        showNotification(SystemNotification.NotificationType.INFO,
            "Update available: v" + version + " • /update to install", true, 0);
    }
}
