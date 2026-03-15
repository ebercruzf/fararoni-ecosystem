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

import java.io.Console;
import java.util.Locale;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TerminalCapabilityDetector {
    private final String osName;
    private final String terminalType;
    private final boolean hasConsole;
    private final boolean isRedirected;

    public TerminalCapabilityDetector() {
        this.osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        this.terminalType = System.getenv("TERM");
        this.hasConsole = System.console() != null;
        this.isRedirected = detectRedirection();
    }

    public DisplayMode detectBestMode() {
        if (isRedirected || !hasConsole) {
            return DisplayMode.COMPATIBLE;
        }

        if (supportsAnsiColors() && supportsAdvancedFeatures()) {
            return DisplayMode.RICH;
        }

        if (supportsBasicTerminal()) {
            return DisplayMode.SIMPLE;
        }

        return DisplayMode.COMPATIBLE;
    }

    private boolean detectRedirection() {
        try {
            if (System.console() == null) {
                return true;
            }

            String javaIoTmpdir = System.getProperty("java.io.tmpdir");
            if (javaIoTmpdir != null && javaIoTmpdir.contains("pipe")) {
                return true;
            }

            String ci = System.getenv("CI");
            if ("true".equalsIgnoreCase(ci)) {
                return true;
            }

            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private boolean supportsAnsiColors() {
        if (osName.contains("windows")) {
            return checkWindowsAnsiSupport();
        }

        if (osName.contains("mac") || osName.contains("linux") || osName.contains("unix")) {
            return checkUnixAnsiSupport();
        }

        return false;
    }

    private boolean checkWindowsAnsiSupport() {
        String windowsVersion = System.getProperty("os.version", "");

        String term = terminalType;
        if (term != null && (
            term.contains("xterm") ||
            term.contains("screen") ||
            term.contains("tmux") ||
            term.contains("alacritty") ||
            term.contains("wezterm")
        )) {
            return true;
        }

        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null && (
            termProgram.equals("vscode") ||
            termProgram.equals("Windows Terminal")
        )) {
            return true;
        }

        return false;
    }

    private boolean checkUnixAnsiSupport() {
        if (terminalType == null) {
            return false;
        }

        return terminalType.contains("xterm") ||
               terminalType.contains("screen") ||
               terminalType.contains("tmux") ||
               terminalType.contains("color") ||
               terminalType.contains("256") ||
               terminalType.equals("alacritty") ||
               terminalType.equals("kitty") ||
               terminalType.equals("wezterm");
    }

    private boolean supportsAdvancedFeatures() {
        if (!supportsAnsiColors()) {
            return false;
        }

        if (terminalType != null && (
            terminalType.equals("dumb") ||
            terminalType.equals("unknown") ||
            terminalType.contains("basic")
        )) {
            return false;
        }

        try {
            String columns = System.getenv("COLUMNS");
            if (columns != null) {
                int width = Integer.parseInt(columns);
                if (width < 80) {
                    return false;
                }
            }
        } catch (NumberFormatException ignored) {}

        return true;
    }

    private boolean supportsBasicTerminal() {
        return hasConsole && terminalType != null && !terminalType.equals("dumb");
    }

    public EnvironmentInfo getEnvironmentInfo() {
        return new EnvironmentInfo(
            osName,
            terminalType,
            hasConsole,
            isRedirected,
            supportsAnsiColors(),
            supportsAdvancedFeatures(),
            detectBestMode()
        );
    }

    public enum DisplayMode {
        RICH("Rich Interactive Mode"),

        SIMPLE("Simple Progress Mode"),

        COMPATIBLE("Compatible Log Mode");

        private final String description;

        DisplayMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean supportsAnimation() {
            return this == RICH;
        }

        public boolean supportsColors() {
            return this == RICH || this == SIMPLE;
        }

        public boolean supportsProgressBars() {
            return this != COMPATIBLE;
        }
    }

    public record EnvironmentInfo(
        String osName,
        String terminalType,
        boolean hasConsole,
        boolean isRedirected,
        boolean supportsAnsi,
        boolean supportsAdvanced,
        DisplayMode recommendedMode
    ) {
        public String getFormattedInfo() {
            return String.format("""
                🖥️  Environment Detection:
                   • OS: %s
                   • Terminal: %s
                   • Console: %s
                   • Redirected: %s
                   • ANSI Support: %s
                   • Advanced Features: %s
                   • Recommended Mode: %s
                """,
                osName,
                terminalType != null ? terminalType : "unknown",
                hasConsole ? "Available" : "Not available",
                isRedirected ? "Yes" : "No",
                supportsAnsi ? "Yes" : "No",
                supportsAdvanced ? "Yes" : "No",
                recommendedMode.getDescription()
            );
        }
    }
}
