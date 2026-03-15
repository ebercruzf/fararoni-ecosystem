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
package dev.fararoni.core.core.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class NativeSilencer {
    private static final Logger LOG = Logger.getLogger(NativeSilencer.class.getName());

    public interface LibC extends Library {
        LibC INSTANCE = Native.load(Platform.isWindows() ? "msvcrt" : "c", LibC.class);

        int dup(int fd);

        int dup2(int oldfd, int newfd);

        int open(String path, int flags);

        int close(int fd);
    }

    private static final int STDERR_FILENO = 2;

    private static final int O_WRONLY = 1;

    private static int originalStderr = -1;

    private static int nullFd = -1;

    private static volatile boolean silenced = false;

    private static final Object LOCK = new Object();

    private NativeSilencer() {
        throw new AssertionError("NativeSilencer is not instantiable");
    }

    public static void silence() {
        if (isDebugMode()) {
            LOG.fine("[NativeSilencer] Debug mode enabled - NOT silencing");
            return;
        }

        synchronized (LOCK) {
            if (silenced) {
                LOG.fine("[NativeSilencer] Already silenced");
                return;
            }

            try {
                originalStderr = LibC.INSTANCE.dup(STDERR_FILENO);
                if (originalStderr == -1) {
                    LOG.warning("[NativeSilencer] Failed to dup stderr");
                    return;
                }

                String nullFile = Platform.isWindows() ? "NUL" : "/dev/null";
                nullFd = LibC.INSTANCE.open(nullFile, O_WRONLY);
                if (nullFd == -1) {
                    LOG.warning("[NativeSilencer] Failed to open " + nullFile);
                    LibC.INSTANCE.close(originalStderr);
                    originalStderr = -1;
                    return;
                }

                int result = LibC.INSTANCE.dup2(nullFd, STDERR_FILENO);
                if (result == -1) {
                    LOG.warning("[NativeSilencer] dup2 failed");
                    LibC.INSTANCE.close(nullFd);
                    LibC.INSTANCE.close(originalStderr);
                    nullFd = -1;
                    originalStderr = -1;
                    return;
                }

                silenced = true;
                LOG.fine("[NativeSilencer] stderr silenced (fd 2 -> /dev/null)");
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "[NativeSilencer] Could not silence stderr", e);
                cleanup();
            }
        }
    }

    public static void restore() {
        synchronized (LOCK) {
            if (!silenced || originalStderr == -1) {
                return;
            }

            try {
                int result = LibC.INSTANCE.dup2(originalStderr, STDERR_FILENO);
                if (result == -1) {
                    LOG.warning("[NativeSilencer] Failed to restore stderr");
                }

                cleanup();
                LOG.fine("[NativeSilencer] stderr restored");
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "[NativeSilencer] Error restoring stderr", e);
                cleanup();
            }
        }
    }

    public static boolean isSilenced() {
        return silenced;
    }

    public static void silencePermanently() {
        if (isDebugMode()) {
            LOG.info("[NativeSilencer] Debug mode - stderr NOT silenced (all native logs visible)");
            return;
        }

        synchronized (LOCK) {
            if (silenced) {
                LOG.fine("[NativeSilencer] Already silenced permanently");
                return;
            }

            try {
                originalStderr = LibC.INSTANCE.dup(STDERR_FILENO);
                if (originalStderr == -1) {
                    LOG.warning("[NativeSilencer] Failed to dup stderr");
                    return;
                }

                String nullFile = Platform.isWindows() ? "NUL" : "/dev/null";
                nullFd = LibC.INSTANCE.open(nullFile, O_WRONLY);
                if (nullFd == -1) {
                    LOG.warning("[NativeSilencer] Failed to open " + nullFile);
                    LibC.INSTANCE.close(originalStderr);
                    originalStderr = -1;
                    return;
                }

                int result = LibC.INSTANCE.dup2(nullFd, STDERR_FILENO);
                if (result == -1) {
                    LOG.warning("[NativeSilencer] dup2 failed");
                    LibC.INSTANCE.close(nullFd);
                    LibC.INSTANCE.close(originalStderr);
                    nullFd = -1;
                    originalStderr = -1;
                    return;
                }

                silenced = true;
                LOG.fine("[NativeSilencer] stderr silenced PERMANENTLY (fd 2 -> /dev/null)");
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "[NativeSilencer] Could not silence stderr permanently", e);
                cleanup();
            }
        }
    }

    private static boolean isDebugMode() {
        if (Boolean.getBoolean("fararoni.debug")) {
            return true;
        }

        String envDebug = System.getenv("FARARONI_DEBUG");
        return "true".equalsIgnoreCase(envDebug) || "1".equals(envDebug);
    }

    private static void cleanup() {
        if (originalStderr != -1) {
            try {
                LibC.INSTANCE.close(originalStderr);
            } catch (Throwable ignored) {}
            originalStderr = -1;
        }

        if (nullFd != -1) {
            try {
                LibC.INSTANCE.close(nullFd);
            } catch (Throwable ignored) {}
            nullFd = -1;
        }

        silenced = false;
    }
}
