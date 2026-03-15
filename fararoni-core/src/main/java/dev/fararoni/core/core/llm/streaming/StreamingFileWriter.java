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
package dev.fararoni.core.core.llm.streaming;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class StreamingFileWriter implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(StreamingFileWriter.class.getName());

    private static final int AUTO_FLUSH_CHUNKS = 20;

    private static final long POLL_TIMEOUT_MS = 100;

    private static final int QUEUE_CAPACITY = 10000;

    private final Path basePath;

    private final BlockingQueue<WriteOperation> writeQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private final Map<String, BufferedWriter> activeWriters = new ConcurrentHashMap<>();

    private final Map<String, Integer> chunkCounts = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread writerThread;

    private final FileCompletionCallback completionCallback;

    private sealed interface WriteOperation permits StartFile, WriteChunk, EndFile, Shutdown {}

    private record StartFile(String path) implements WriteOperation {}
    private record WriteChunk(String path, String content) implements WriteOperation {}
    private record EndFile(String path) implements WriteOperation {}
    private record Shutdown() implements WriteOperation {}

    @FunctionalInterface
    public interface FileCompletionCallback {
        void onFileCompleted(Path absolutePath, long bytesWritten);
    }

    public StreamingFileWriter(Path basePath) {
        this(basePath, null);
    }

    public StreamingFileWriter(Path basePath, FileCompletionCallback completionCallback) {
        this.basePath = basePath.toAbsolutePath().normalize();
        this.completionCallback = completionCallback;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            writerThread = Thread.ofVirtual()
                .name("streaming-file-writer-milspec")
                .start(this::writerLoop);
            LOG.info("[WRITER-MILSPEC] StreamingFileWriter iniciado en: " + basePath);
        }
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            writeQueue.offer(new Shutdown());
            try {
                if (writerThread != null) {
                    writerThread.join(5000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeAllWriters();
            LOG.info("[WRITER-MILSPEC] StreamingFileWriter detenido");
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public void startFile(String relativePath) {
        writeQueue.offer(new StartFile(relativePath));
    }

    public void writeChunk(String relativePath, String content) {
        writeQueue.offer(new WriteChunk(relativePath, content));
    }

    public void endFile(String relativePath) {
        writeQueue.offer(new EndFile(relativePath));
    }

    public boolean isHealthy() {
        return running.get() && writerThread != null && writerThread.isAlive();
    }

    public int getActiveFileCount() {
        return activeWriters.size();
    }

    public int getQueueSize() {
        return writeQueue.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    private void writerLoop() {
        LOG.fine("[WRITER-MILSPEC] Thread de escritura iniciado");

        while (running.get() || !writeQueue.isEmpty()) {
            try {
                WriteOperation op = writeQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (op == null) continue;

                switch (op) {
                    case StartFile(String path) -> handleStartFile(path);
                    case WriteChunk(String path, String content) -> handleWriteChunk(path, content);
                    case EndFile(String path) -> handleEndFile(path);
                    case Shutdown() -> {
                        LOG.fine("[WRITER-MILSPEC] Recibido shutdown");
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (SecurityException e) {
                LOG.severe("[WRITER-MILSPEC] SECURITY: " + e.getMessage());
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[WRITER-MILSPEC] Error en loop de escritura", e);
            }
        }

        LOG.fine("[WRITER-MILSPEC] Thread de escritura terminado");
    }

    private void handleStartFile(String relativePath) throws IOException {
        Path fullPath = basePath.resolve(relativePath).normalize();

        if (!fullPath.startsWith(basePath)) {
            throw new SecurityException(
                "Intento de escribir fuera del sandbox: " + relativePath +
                " (resuelve a: " + fullPath + ")");
        }

        Files.createDirectories(fullPath.getParent());

        Path tempPath = fullPath.resolveSibling(fullPath.getFileName() + ".tmp");
        BufferedWriter writer = Files.newBufferedWriter(tempPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);

        activeWriters.put(relativePath, writer);
        chunkCounts.put(relativePath, 0);

        LOG.info("[WRITER-MILSPEC] Archivo iniciado (shadow): " + relativePath);
    }

    private void handleWriteChunk(String relativePath, String content) throws IOException {
        BufferedWriter writer = activeWriters.get(relativePath);
        if (writer == null) {
            LOG.warning("[WRITER-MILSPEC] No hay writer activo para: " + relativePath);
            return;
        }

        writer.write(content);

        int count = chunkCounts.merge(relativePath, 1, Integer::sum);
        if (count % AUTO_FLUSH_CHUNKS == 0) {
            writer.flush();
            LOG.fine("[WRITER-MILSPEC] Auto-flush en chunk " + count + " para: " + relativePath);
        }
    }

    private void handleEndFile(String relativePath) throws IOException {
        BufferedWriter writer = activeWriters.remove(relativePath);
        chunkCounts.remove(relativePath);

        if (writer == null) {
            LOG.warning("[WRITER-MILSPEC] No hay writer para cerrar: " + relativePath);
            return;
        }

        writer.flush();
        writer.close();

        Path fullPath = basePath.resolve(relativePath).normalize();
        Path tempPath = fullPath.resolveSibling(fullPath.getFileName() + ".tmp");

        Files.move(tempPath, fullPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);

        long size = Files.size(fullPath);

        LOG.info(String.format("[WRITER-MILSPEC] Archivo verificado: %s (%d bytes)",
            relativePath, size));

        if (completionCallback != null) {
            completionCallback.onFileCompleted(fullPath, size);
        }
    }

    private void closeAllWriters() {
        for (Map.Entry<String, BufferedWriter> entry : activeWriters.entrySet()) {
            try {
                entry.getValue().close();

                Path fullPath = basePath.resolve(entry.getKey()).normalize();
                Path tempPath = fullPath.resolveSibling(fullPath.getFileName() + ".tmp");
                Files.deleteIfExists(tempPath);

                LOG.warning("[WRITER-MILSPEC] Cerrado forzado (limpiado .tmp): " + entry.getKey());
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "[WRITER-MILSPEC] Error en cierre forzado: " + entry.getKey(), e);
            }
        }
        activeWriters.clear();
        chunkCounts.clear();
    }
}
