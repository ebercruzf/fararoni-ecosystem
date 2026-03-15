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
package dev.fararoni.core.core.runtime.pty;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class InteractiveShell {
    private static final Logger LOG = Logger.getLogger(InteractiveShell.class.getName());

    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public static final int DEFAULT_COLUMNS = 120;

    public static final int DEFAULT_ROWS = 40;

    private static final int SIGINT_GRACE_MS = 500;

    private static final int SIGTERM_GRACE_MS = 1000;

    private static final Pattern PROMPT_PATTERN = Pattern.compile(
            ".*(\\[y/N\\]|\\[Y/n\\]|\\(yes/no\\)|password:|Password:|continue\\?|Are you sure\\?).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SELECTION_PATTERN = Pattern.compile(
            ".*\\[\\d+\\].*|.*\\(\\d+\\).*|.*Enter.*number.*",
            Pattern.CASE_INSENSITIVE
    );

    private PtyProcess ptyProcess;
    private BufferedWriter inputWriter;
    private final Path workingDir;
    private final Consumer<String> outputListener;
    private final StringBuilder outputBuffer;
    private Thread readerThread;
    private volatile boolean running;

    private int columns = DEFAULT_COLUMNS;
    private int rows = DEFAULT_ROWS;

    public InteractiveShell(Path workingDir, Consumer<String> outputListener) {
        this.workingDir = workingDir != null ? workingDir : Path.of(".");
        this.outputListener = outputListener != null ? outputListener : line -> {};
        this.outputBuffer = new StringBuilder();
        this.running = false;
    }

    public void start(String... command) throws IOException {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        LOG.info("[PTY] Starting: " + String.join(" ", command));

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        env.put("COLUMNS", String.valueOf(columns));
        env.put("LINES", String.valueOf(rows));
        env.put("LC_ALL", "en_US.UTF-8");

        this.ptyProcess = new PtyProcessBuilder()
                .setCommand(command)
                .setEnvironment(env)
                .setDirectory(workingDir.toString())
                .start();

        this.inputWriter = new BufferedWriter(
                new OutputStreamWriter(ptyProcess.getOutputStream(), StandardCharsets.UTF_8));

        this.running = true;
        this.readerThread = new Thread(this::readOutputLoop, "pty-reader-" + command[0]);
        this.readerThread.setDaemon(true);
        this.readerThread.start();

        LOG.info("[PTY] Process started with PID: " + ptyProcess.pid());
    }

    public void writeInput(String text) {
        if (inputWriter == null || !running) {
            LOG.warning("[PTY] Cannot write: process not running");
            return;
        }

        try {
            inputWriter.write(text);
            inputWriter.flush();
            LOG.fine("[PTY] Wrote: " + text.replace("\n", "\\n"));
        } catch (IOException e) {
            LOG.severe("[PTY] Write error: " + e.getMessage());
        }
    }

    public void sendEnter() {
        writeInput("\n");
    }

    public void sendInterrupt() {
        writeInput("\u0003");
    }

    public void sendEOF() {
        writeInput("\u0004");
    }

    public void destroy() {
        if (ptyProcess != null && ptyProcess.isAlive()) {
            ptyProcess.destroy();
            LOG.info("[PTY] Process destroyed");
        }
        running = false;
    }

    public void destroyForcibly() {
        if (ptyProcess != null && ptyProcess.isAlive()) {
            ptyProcess.destroyForcibly();
            LOG.info("[PTY] Process destroyed forcibly");
        }
        running = false;
    }

    public InteractiveShell withWindowSize(int columns, int rows) {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        this.columns = columns;
        this.rows = rows;
        return this;
    }

    public void setWindowSize(int columns, int rows) {
        if (ptyProcess == null || !running) {
            LOG.warning("[PTY] Cannot resize: process not running");
            return;
        }

        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Window size must be positive");
        }

        try {
            WinSize newSize = new WinSize(columns, rows);
            ptyProcess.setWinSize(newSize);
            this.columns = columns;
            this.rows = rows;
            LOG.fine("[PTY] Window resized to " + columns + "x" + rows);
        } catch (Exception e) {
            LOG.warning("[PTY] Failed to resize window: " + e.getMessage());
        }
    }

    public boolean terminate() {
        if (ptyProcess == null || !ptyProcess.isAlive()) {
            running = false;
            return true;
        }

        LOG.info("[PTY] Terminating process (PID: " + ptyProcess.pid() + ")...");

        try {
            sendInterrupt();
            if (ptyProcess.waitFor(SIGINT_GRACE_MS, TimeUnit.MILLISECONDS)) {
                LOG.info("[PTY] Process terminated by SIGINT");
                running = false;
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            ptyProcess.destroy();
            if (ptyProcess.waitFor(SIGTERM_GRACE_MS, TimeUnit.MILLISECONDS)) {
                LOG.info("[PTY] Process terminated by SIGTERM");
                running = false;
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ptyProcess.destroyForcibly();
        LOG.warning("[PTY] Process killed forcibly (SIGKILL)");
        running = false;
        return true;
    }

    public int[] getWindowSize() {
        return new int[] { columns, rows };
    }

    public boolean isAlive() {
        return ptyProcess != null && ptyProcess.isAlive();
    }

    public boolean isRunning() {
        return running;
    }

    public int waitFor() throws InterruptedException {
        if (ptyProcess == null) return -1;
        int exitCode = ptyProcess.waitFor();
        running = false;
        return exitCode;
    }

    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        if (ptyProcess == null) return true;
        boolean finished = ptyProcess.waitFor(timeout, unit);
        if (finished) running = false;
        return finished;
    }

    public int exitValue() {
        if (ptyProcess == null || ptyProcess.isAlive()) return -1;
        return ptyProcess.exitValue();
    }

    public String getOutputBuffer() {
        synchronized (outputBuffer) {
            return outputBuffer.toString();
        }
    }

    public void clearOutputBuffer() {
        synchronized (outputBuffer) {
            outputBuffer.setLength(0);
        }
    }

    public static boolean isInteractivePrompt(String line) {
        if (line == null || line.isBlank()) return false;
        return PROMPT_PATTERN.matcher(line).matches();
    }

    public static boolean isSelectionPrompt(String line) {
        if (line == null || line.isBlank()) return false;
        return SELECTION_PATTERN.matcher(line).matches();
    }

    private void readOutputLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ptyProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                synchronized (outputBuffer) {
                    outputBuffer.append(line).append("\n");
                }

                outputListener.accept(line);

                if (isInteractivePrompt(line)) {
                    LOG.info("[PTY] Detected interactive prompt: " + line);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOG.fine("[PTY] Read error (process may have ended): " + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    public long getPid() {
        return ptyProcess != null ? ptyProcess.pid() : -1;
    }
}
