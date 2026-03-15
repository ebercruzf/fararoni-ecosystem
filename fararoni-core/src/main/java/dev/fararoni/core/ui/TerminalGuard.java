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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TerminalGuard {
    private static final Logger LOG = Logger.getLogger(TerminalGuard.class.getName());

    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

    private static volatile OutputCoordinator coordinator;
    private static final AtomicBoolean footerActive = new AtomicBoolean(false);
    private static final AtomicBoolean installed = new AtomicBoolean(false);

    private static final ThreadLocal<Boolean> bypassGuard = ThreadLocal.withInitial(() -> false);

    private static final BlockingQueue<PendingOutput> pendingMessages = new LinkedBlockingQueue<>(1000);

    public static void install(OutputCoordinator coord) {
        if (installed.compareAndSet(false, true)) {
            coordinator = coord;
            System.setOut(new GuardedPrintStream(ORIGINAL_OUT, false));
            System.setErr(new GuardedPrintStream(ORIGINAL_ERR, true));
            LOG.fine("[TerminalGuard] Instalado - System.out/err interceptados");
        }
    }

    public static void uninstall() {
        if (installed.compareAndSet(true, false)) {
            flushPendingMessages();

            System.setOut(ORIGINAL_OUT);
            System.setErr(ORIGINAL_ERR);
            coordinator = null;
            LOG.fine("[TerminalGuard] Desinstalado - Streams restaurados");
        }
    }

    public static void setFooterActive(boolean active) {
        boolean wasActive = footerActive.getAndSet(active);

        if (wasActive && !active) {
            flushPendingMessages();
        }
    }

    public static boolean isFooterActive() {
        return footerActive.get();
    }

    public static void enableBypassForCurrentThread() {
        bypassGuard.set(true);
    }

    public static void disableBypassForCurrentThread() {
        bypassGuard.set(false);
    }

    public static PrintStream getOriginalOut() {
        return ORIGINAL_OUT;
    }

    public static PrintStream getOriginalErr() {
        return ORIGINAL_ERR;
    }

    private static void flushPendingMessages() {
        PendingOutput msg;
        while ((msg = pendingMessages.poll()) != null) {
            PrintStream target = msg.isError ? ORIGINAL_ERR : ORIGINAL_OUT;
            target.println(msg.message);
        }
    }

    private static class GuardedPrintStream extends PrintStream {
        private final PrintStream original;
        private final boolean isError;

        GuardedPrintStream(PrintStream original, boolean isError) {
            super(original, true);
            this.original = original;
            this.isError = isError;
        }

        @Override
        public void println(String x) {
            routeMessage(x);
        }

        @Override
        public void println(Object x) {
            routeMessage(String.valueOf(x));
        }

        @Override
        public void println() {
            routeMessage("");
        }

        @Override
        public void print(String s) {
            if (shouldBypass()) {
                original.print(s);
            } else if (footerActive.get() && coordinator != null) {
                if (s != null && !s.isEmpty()) {
                    routeMessage(s);
                }
            } else {
                original.print(s);
            }
        }

        @Override
        public PrintStream printf(String format, Object... args) {
            String formatted = String.format(format, args);
            if (shouldBypass()) {
                original.print(formatted);
            } else if (footerActive.get() && coordinator != null) {
                routeMessage(formatted.replace("\n", ""));
            } else {
                original.print(formatted);
            }
            return this;
        }

        private void routeMessage(String message) {
            if (shouldBypass()) {
                original.println(message);
                return;
            }

            if (footerActive.get() && coordinator != null) {
                try {
                    if (isError) {
                        coordinator.printError(message);
                    } else {
                        coordinator.printSafe(message);
                    }
                } catch (Exception e) {
                    original.println(message);
                }
                return;
            }

            original.println(message);
        }

        private boolean shouldBypass() {
            return Boolean.TRUE.equals(bypassGuard.get());
        }
    }

    private record PendingOutput(String message, boolean isError) {}

    private TerminalGuard() {
        throw new UnsupportedOperationException("Utility class");
    }
}
