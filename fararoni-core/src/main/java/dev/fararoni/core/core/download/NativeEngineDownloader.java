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

import dev.fararoni.core.core.utils.NativeLoader;
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
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class NativeEngineDownloader {
    private static final Logger LOG = Logger.getLogger(NativeEngineDownloader.class.getName());

    private static final String GITHUB_RELEASES_BASE =
        "https://github.com/ebercruzf/native-libs/releases/download";

    public static final String ENGINE_VERSION = "v1.0.0";

    public static final long EXPECTED_SIZE_BYTES = 5_000_000L;

    private static final long MIN_SIZE_BYTES = 1_000_000L;

    private static final double SPACE_SAFETY_MARGIN = 1.2;

    private static final int MAX_RETRIES = 3;

    private static final int CONNECT_TIMEOUT_MS = 30_000;

    private static final int READ_TIMEOUT_MS = 60_000;

    private static final int BUFFER_SIZE = 8192;

    private static final long PROGRESS_INTERVAL_BYTES = 51200;

    private final Path libDir;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public NativeEngineDownloader() {
        this.libDir = WorkspaceManager.getInstance().getLibDir();
    }

    public NativeEngineDownloader(Path libDir) {
        this.libDir = Objects.requireNonNull(libDir, "libDir cannot be null");
    }

    public String getLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "libjllama.dylib";
        } else if (os.contains("win")) {
            return "jllama.dll";
        } else {
            return "libjllama.so";
        }
    }

    public String getArchitecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "aarch64";
        } else if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "x86_64";
        } else {
            return arch;
        }
    }

    public String getOperatingSystem() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        } else if (os.contains("win")) {
            return "windows";
        } else {
            return "linux";
        }
    }

    public Path getLibraryPath() {
        return libDir.resolve(getLibraryName());
    }

    public Path getLibDir() {
        return libDir;
    }

    public boolean isEngineInstalled() {
        Path libPath = getLibraryPath();
        if (!Files.exists(libPath)) {
            return false;
        }

        try {
            long size = Files.size(libPath);
            return size >= MIN_SIZE_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    public long getInstalledSize() {
        Path libPath = getLibraryPath();
        if (!Files.exists(libPath)) {
            return -1;
        }

        try {
            return Files.size(libPath);
        } catch (IOException e) {
            return -1;
        }
    }

    public boolean hasEnoughDiskSpace() {
        try {
            Files.createDirectories(libDir);
            long freeSpace = libDir.toFile().getFreeSpace();
            return freeSpace >= (long) (EXPECTED_SIZE_BYTES * SPACE_SAFETY_MARGIN);
        } catch (IOException e) {
            return false;
        }
    }

    public String getDownloadUrl() {
        return String.format("%s/%s/%s",
            GITHUB_RELEASES_BASE,
            ENGINE_VERSION,
            getDownloadFileName());
    }

    public String getDownloadFileName() {
        String os = getOperatingSystem();
        String arch = getArchitecture();

        return switch (os) {
            case "macos" -> String.format("libjllama-%s-%s.dylib", os, arch);
            case "linux" -> String.format("libjllama-%s-%s.so", os, arch);
            case "windows" -> String.format("jllama-%s-%s.dll", os, arch);
            default -> throw new IllegalStateException("Unsupported OS: " + os);
        };
    }

    public boolean isPlatformSupported() {
        String os = getOperatingSystem();
        String arch = getArchitecture();

        return switch (os) {
            case "macos" -> arch.equals("aarch64") || arch.equals("x86_64");
            case "linux" -> arch.equals("x86_64") || arch.equals("aarch64");
            case "windows" -> arch.equals("x86_64");
            default -> false;
        };
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean download(Consumer<DownloadProgress> progressCallback) {
        cancelled.set(false);

        if (!isPlatformSupported()) {
            throw new ModelDownloadException(
                "Plataforma no soportada: " + getOperatingSystem() + "-" + getArchitecture() +
                ". Plataformas soportadas: macOS (aarch64, x86_64), Linux (x86_64, aarch64), Windows (x86_64)");
        }

        if (!hasEnoughDiskSpace()) {
            throw new ModelDownloadException(
                "Espacio insuficiente en disco para instalar el motor local.");
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (progressCallback != null) {
                    progressCallback.accept(DownloadProgress.starting(attempt, MAX_RETRIES));
                }

                boolean success = doDownload(progressCallback);
                if (success) {
                    makeExecutable();

                    if (progressCallback != null) {
                        progressCallback.accept(DownloadProgress.completed());
                    }
                    LOG.info("[NativeEngine] Motor local instalado exitosamente: " + getLibraryPath());
                    return true;
                }
            } catch (IOException e) {
                if (attempt == MAX_RETRIES) {
                    throw new ModelDownloadException(
                        "Fallo al instalar motor local despues de " + MAX_RETRIES +
                        " intentos: " + e.getMessage(), e);
                }

                if (progressCallback != null) {
                    progressCallback.accept(DownloadProgress.retrying(attempt, MAX_RETRIES, e.getMessage()));
                }

                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ModelDownloadException("Instalacion interrumpida", ie);
                }
            }
        }

        return false;
    }

    public boolean download() {
        return download(null);
    }

    private boolean doDownload(Consumer<DownloadProgress> progressCallback) throws IOException {
        Path libPath = getLibraryPath();
        Path tempPath = libDir.resolve(getLibraryName() + ".downloading");

        Files.createDirectories(libDir);

        Files.deleteIfExists(tempPath);

        String downloadUrl = getDownloadUrl();
        LOG.info("[NativeEngine] Descargando de: " + downloadUrl);

        URL url = URI.create(downloadUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "FARARONI-Core-NativeEngine/1.0");
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
                connection.setRequestProperty("User-Agent", "FARARONI-Core-NativeEngine/1.0");
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
                        throw new IOException("Descarga cancelada por el usuario");
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
                throw new IOException("Archivo descargado muy pequeno: " + downloadedSize + " bytes");
            }

            Files.move(tempPath, libPath, StandardCopyOption.REPLACE_EXISTING);

            return true;
        } finally {
            connection.disconnect();
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }

    private void makeExecutable() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                getLibraryPath().toFile().setExecutable(true);
            } catch (Exception e) {
                LOG.warning("[NativeEngine] No se pudo hacer ejecutable: " + e.getMessage());
            }
        }
    }

    public static String getEngineVersion() {
        return ENGINE_VERSION;
    }

    public static long getExpectedSize() {
        return EXPECTED_SIZE_BYTES;
    }

    public static String formatSize(long bytes) {
        return DownloadProgress.formatSize(bytes);
    }
}
