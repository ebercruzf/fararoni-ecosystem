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
package dev.fararoni.core.core.download;

import dev.fararoni.core.core.workspace.WorkspaceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ModelDownloader {
    public static final String MODEL_NAME = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf";

    private static final String HF_BASE_URL = "https://huggingface.co";

    private static final String MODEL_REPO = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF";

    public static final String MODEL_URL = HF_BASE_URL + "/" + MODEL_REPO + "/resolve/main/" + MODEL_NAME;

    public static final long EXPECTED_SIZE_BYTES = 1_200_000_000L;

    private static final long MIN_SIZE_BYTES = 1_000_000_000L;

    private static final double SPACE_SAFETY_MARGIN = 1.1;

    private static final int MAX_RETRIES = 3;

    private static final int CONNECT_TIMEOUT_MS = 30_000;

    private static final int READ_TIMEOUT_MS = 60_000;

    private static final int BUFFER_SIZE = 8192;

    private static final long PROGRESS_INTERVAL_BYTES = 102400;

    private final Path modelsDir;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public ModelDownloader() {
        this.modelsDir = WorkspaceManager.getInstance().getModelsDir();
    }

    public ModelDownloader(Path modelsDir) {
        this.modelsDir = Objects.requireNonNull(modelsDir, "modelsDir cannot be null");
    }

    public Path getModelPath() {
        return modelsDir.resolve(MODEL_NAME);
    }

    public Path getModelsDir() {
        return modelsDir;
    }

    public boolean isModelDownloaded() {
        Path modelPath = getModelPath();
        if (!Files.exists(modelPath)) {
            return false;
        }

        try {
            long size = Files.size(modelPath);
            return size >= MIN_SIZE_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    public long getDownloadedSize() {
        Path modelPath = getModelPath();
        if (!Files.exists(modelPath)) {
            return -1;
        }

        try {
            return Files.size(modelPath);
        } catch (IOException e) {
            return -1;
        }
    }

    public boolean hasEnoughDiskSpace() {
        try {
            Files.createDirectories(modelsDir);
            long freeSpace = modelsDir.toFile().getFreeSpace();
            return freeSpace >= (long) (EXPECTED_SIZE_BYTES * SPACE_SAFETY_MARGIN);
        } catch (IOException e) {
            return false;
        }
    }

    public long getFreeDiskSpace() {
        try {
            Files.createDirectories(modelsDir);
            return modelsDir.toFile().getFreeSpace();
        } catch (IOException e) {
            return 0;
        }
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean download(Consumer<DownloadProgress> progressCallback) {
        cancelled.set(false);

        if (!hasEnoughDiskSpace()) {
            long free = getFreeDiskSpace();
            throw new ModelDownloadException(
                String.format("Insufficient disk space. Need %.1f GB, have %.1f GB",
                    EXPECTED_SIZE_BYTES / (1024.0 * 1024 * 1024),
                    free / (1024.0 * 1024 * 1024)));
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (progressCallback != null) {
                    progressCallback.accept(DownloadProgress.starting(attempt, MAX_RETRIES));
                }

                boolean success = doDownload(progressCallback);
                if (success) {
                    if (progressCallback != null) {
                        progressCallback.accept(DownloadProgress.completed());
                    }
                    return true;
                }
            } catch (IOException e) {
                if (attempt == MAX_RETRIES) {
                    throw new ModelDownloadException(
                        "Failed to download model after " + MAX_RETRIES + " attempts: " + e.getMessage(), e);
                }

                if (progressCallback != null) {
                    progressCallback.accept(DownloadProgress.retrying(attempt, MAX_RETRIES, e.getMessage()));
                }

                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ModelDownloadException("Download interrupted", ie);
                }
            }
        }

        return false;
    }

    public boolean download() {
        return download(null);
    }

    private boolean doDownload(Consumer<DownloadProgress> progressCallback) throws IOException {
        Path modelPath = getModelPath();
        Path tempPath = modelsDir.resolve(MODEL_NAME + ".downloading");

        Files.createDirectories(modelsDir);

        Files.deleteIfExists(tempPath);

        URL url = URI.create(MODEL_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "FARARONI-Core-ModelDownloader/1.0");

            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308) {
                String newUrl = connection.getHeaderField("Location");
                connection.disconnect();
                connection = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", "FARARONI-Core-ModelDownloader/1.0");
                responseCode = connection.getResponseCode();
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error: " + responseCode + " " + connection.getResponseMessage());
            }

            long totalSize = connection.getContentLengthLong();
            if (totalSize <= 0) {
                totalSize = EXPECTED_SIZE_BYTES;
            }

            try (InputStream in = connection.getInputStream();
                 OutputStream out = Files.newOutputStream(tempPath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long downloaded = 0;
                int bytesRead;
                long lastProgressUpdate = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (cancelled.get()) {
                        throw new IOException("Download cancelled by user");
                    }

                    out.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    if (progressCallback != null && downloaded - lastProgressUpdate > PROGRESS_INTERVAL_BYTES) {
                        double percentage = (double) downloaded / totalSize * 100;
                        progressCallback.accept(DownloadProgress.downloading(downloaded, totalSize, percentage));
                        lastProgressUpdate = downloaded;
                    }
                }
            }

            long downloadedSize = Files.size(tempPath);
            if (downloadedSize < MIN_SIZE_BYTES) {
                Files.deleteIfExists(tempPath);
                throw new IOException("Downloaded file too small: " + downloadedSize + " bytes");
            }

            Files.move(tempPath, modelPath, StandardCopyOption.REPLACE_EXISTING);

            return true;
        } finally {
            connection.disconnect();
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }

    public static String getModelUrl() {
        return MODEL_URL;
    }

    public static long getExpectedSize() {
        return EXPECTED_SIZE_BYTES;
    }

    public static String formatSize(long bytes) {
        return DownloadProgress.formatSize(bytes);
    }
}
