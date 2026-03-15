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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record DownloadProgress(
    DownloadState state,
    long bytesDownloaded,
    long totalBytes,
    double percentage,
    int attempt,
    int maxAttempts,
    String message
) {
    public DownloadProgress(DownloadState state, long bytesDownloaded, long totalBytes,
                           double percentage, String message) {
        this(state, bytesDownloaded, totalBytes, percentage, 0, 0, message);
    }

    public long downloadedBytes() {
        return bytesDownloaded;
    }

    public static DownloadProgress starting(int attempt, int maxAttempts) {
        return new DownloadProgress(
            DownloadState.STARTING,
            0, 0, 0,
            attempt, maxAttempts,
            "Starting download (attempt " + attempt + "/" + maxAttempts + ")"
        );
    }

    public static DownloadProgress downloading(long downloaded, long total, double percentage) {
        return new DownloadProgress(
            DownloadState.DOWNLOADING,
            downloaded, total, percentage,
            0, 0,
            String.format("Downloading: %.1f%% (%s / %s)",
                percentage, formatSize(downloaded), formatSize(total))
        );
    }

    public static DownloadProgress retrying(int attempt, int maxAttempts, String error) {
        return new DownloadProgress(
            DownloadState.RETRYING,
            0, 0, 0,
            attempt, maxAttempts,
            "Retry " + (attempt + 1) + "/" + maxAttempts + ": " + error
        );
    }

    public static DownloadProgress completed() {
        return new DownloadProgress(
            DownloadState.COMPLETED,
            0, 0, 100,
            0, 0,
            "Download completed successfully"
        );
    }

    public static DownloadProgress failed(String error) {
        return new DownloadProgress(
            DownloadState.FAILED,
            0, 0, 0,
            0, 0,
            "Download failed: " + error
        );
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
