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
package dev.fararoni.bus.agent.api.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Wrapper for efficient binary stream handling.
 *
 * <p>FNL uses this to handle large files (logs, PDFs, images) without the
 * overhead of Base64 encoding that MCP requires. This enables zero-copy
 * streaming of gigabytes of data.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Opening a stream
 * FNLResult<BinaryStreamHandle> result = fileSkill.openReadStream("/logs/app.log");
 *
 * if (result.success()) {
 *     BinaryStreamHandle handle = result.data();
 *     try (InputStream stream = handle.stream()) {
 *         // Process in chunks - never loads entire file in memory
 *         byte[] buffer = new byte[8192];
 *         int read;
 *         while ((read = stream.read(buffer)) != -1) {
 *             process(buffer, read);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Enterprise Value vs MCP</h2>
 * <table border="1">
 *   <caption>FNL vs MCP comparison for binary streams</caption>
 *   <tr><th>Feature</th><th>MCP</th><th>FNL</th></tr>
 *   <tr><td>Large Files</td><td>Base64 in JSON (2x memory)</td><td>Direct stream (zero-copy)</td></tr>
 *   <tr><td>1GB Log File</td><td>2GB+ RAM required</td><td>8KB buffer</td></tr>
 *   <tr><td>Binary Data</td><td>Encoding overhead</td><td>Native bytes</td></tr>
 * </table>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @param stream The input stream for reading data
 * @param sizeBytes Size in bytes (-1 if unknown, e.g., infinite stream)
 * @param mimeType MIME type (e.g., "application/pdf", "text/plain")
 * @param isSeekable Whether the stream supports random access (seek)
 * @param resourceCloser Hook to close underlying resources (DB connection, socket)
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see StreamableSkill
 */
public record BinaryStreamHandle(
    InputStream stream,
    long sizeBytes,
    String mimeType,
    boolean isSeekable,
    Closeable resourceCloser
) implements Closeable {

    /**
     * Compact constructor with validation.
     */
    public BinaryStreamHandle {
        Objects.requireNonNull(stream, "stream cannot be null");
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a handle for a simple file stream.
     *
     * @param stream the input stream
     * @param sizeBytes file size in bytes
     * @param mimeType MIME type
     * @return new handle
     */
    public static BinaryStreamHandle of(InputStream stream, long sizeBytes, String mimeType) {
        return new BinaryStreamHandle(stream, sizeBytes, mimeType, false, null);
    }

    /**
     * Creates a handle for a stream with unknown size (e.g., network stream).
     *
     * @param stream the input stream
     * @param mimeType MIME type
     * @return new handle
     */
    public static BinaryStreamHandle ofUnknownSize(InputStream stream, String mimeType) {
        return new BinaryStreamHandle(stream, -1, mimeType, false, null);
    }

    /**
     * Creates a handle with a resource closer (e.g., DB connection).
     *
     * @param stream the input stream
     * @param sizeBytes file size
     * @param mimeType MIME type
     * @param resourceCloser closeable resource to release when done
     * @return new handle
     */
    public static BinaryStreamHandle withCloser(
            InputStream stream, long sizeBytes, String mimeType, Closeable resourceCloser) {
        return new BinaryStreamHandle(stream, sizeBytes, mimeType, false, resourceCloser);
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if the size is known.
     *
     * @return true if size is known (>= 0)
     */
    public boolean hasKnownSize() {
        return sizeBytes >= 0;
    }

    /**
     * Gets the size in a human-readable format.
     *
     * @return formatted size (e.g., "1.5 MB")
     */
    public String getFormattedSize() {
        if (sizeBytes < 0) return "unknown";
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
        if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", sizeBytes / (1024.0 * 1024));
        return String.format("%.1f GB", sizeBytes / (1024.0 * 1024 * 1024));
    }

    /**
     * Closes the stream and any associated resources.
     *
     * @throws IOException if closing fails
     */
    @Override
    public void close() throws IOException {
        try {
            stream.close();
        } finally {
            if (resourceCloser != null) {
                resourceCloser.close();
            }
        }
    }
}
