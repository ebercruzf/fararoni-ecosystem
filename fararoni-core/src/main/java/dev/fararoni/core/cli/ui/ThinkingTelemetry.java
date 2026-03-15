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

import org.jline.terminal.Terminal;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ThinkingTelemetry implements AutoCloseable {
    private static final String RESET = "\033[0m";
    private static final String GRAY_ITALIC = "\033[3;90m";
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";

    private final Terminal terminal;
    private final AtomicBoolean thinkingStarted = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ThinkingTelemetry(Terminal terminal) {
        this.terminal = terminal;

        if (terminal != null) {
            terminal.writer().print(HIDE_CURSOR);
            terminal.writer().flush();
        }
    }

    public synchronized void onThinkingToken(String token) {
        if (token == null || token.isEmpty() || terminal == null || closed.get()) {
            return;
        }

        if (thinkingStarted.compareAndSet(false, true)) {
            terminal.writer().print("\n" + GRAY_ITALIC + "[Pensando...] " + RESET);
            terminal.writer().flush();
        }

        terminal.writer().print(GRAY_ITALIC + token + RESET);
        terminal.writer().flush();
    }

    public boolean hasThinkingStarted() {
        return thinkingStarted.get();
    }

    public void finishThinking() {
        if (terminal != null && thinkingStarted.get() && !closed.get()) {
            terminal.writer().println(RESET);
            terminal.writer().println();
            terminal.writer().flush();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (terminal != null) {
                terminal.writer().print(SHOW_CURSOR);
                terminal.writer().flush();
            }
            deactivate();
        }
    }

    private static volatile ThinkingTelemetry activeInstance = null;

    public void activate() {
        activeInstance = this;
    }

    public void deactivate() {
        if (activeInstance == this) {
            activeInstance = null;
        }
    }

    public static boolean sendToken(String token) {
        ThinkingTelemetry active = activeInstance;
        if (active != null) {
            active.onThinkingToken(token);
            return true;
        }
        return false;
    }

    public static boolean hasActiveInstance() {
        return activeInstance != null;
    }
}
