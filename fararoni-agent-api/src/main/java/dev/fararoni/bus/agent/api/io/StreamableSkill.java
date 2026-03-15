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

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.ToolSkill;

import java.io.OutputStream;

/**
 * Interface for skills that support binary streaming.
 *
 * <p>Implement this interface when your skill needs to handle large binary
 * data (logs, PDFs, images, database exports) efficiently. FNL streams data
 * directly without Base64 encoding, unlike MCP.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Reading a large log file
 * FNLResult<BinaryStreamHandle> result = logSkill.openReadStream("/var/log/app.log");
 *
 * if (result.success()) {
 *     try (BinaryStreamHandle handle = result.data()) {
 *         // Stream processing - only 8KB in memory at a time
 *         byte[] buffer = new byte[8192];
 *         int read;
 *         while ((read = handle.stream().read(buffer)) != -1) {
 *             analyzer.process(buffer, 0, read);
 *         }
 *     }
 * }
 *
 * // Writing a large export
 * FNLResult<OutputStream> writer = exportSkill.openWriteStream("/exports/data.csv");
 * if (writer.success()) {
 *     try (OutputStream out = writer.data()) {
 *         for (Row row : millionRows) {
 *             out.write(row.toCsv().getBytes());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Performance vs MCP</h2>
 * <table border="1">
 *   <caption>Memory usage comparison by file size</caption>
 *   <tr><th>File Size</th><th>MCP Memory</th><th>FNL Memory</th></tr>
 *   <tr><td>100MB</td><td>~200MB (Base64)</td><td>8KB (stream)</td></tr>
 *   <tr><td>1GB</td><td>~2GB (Base64)</td><td>8KB (stream)</td></tr>
 *   <tr><td>10GB</td><td>OOM Error</td><td>8KB (stream)</td></tr>
 * </table>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see BinaryStreamHandle
 */
public interface StreamableSkill extends ToolSkill {

    /**
     * Opens a stream for reading binary data.
     *
     * <p>The returned handle contains the input stream and metadata.
     * The caller is responsible for closing the handle when done.</p>
     *
     * @param resourceId identifier for the resource (path, URL, ID, etc.)
     * @return result containing the stream handle
     */
    @AgentAction(
        name = "open_read_stream",
        description = "Opens a binary stream for efficient reading of large data"
    )
    FNLResult<BinaryStreamHandle> openReadStream(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String resourceId
    );

    /**
     * Opens a stream for writing binary data.
     *
     * <p>The caller is responsible for closing the stream when done.</p>
     *
     * @param resourceId identifier for the target resource
     * @return result containing the output stream
     */
    @AgentAction(
        name = "open_write_stream",
        description = "Opens a binary stream for efficient writing of large data"
    )
    FNLResult<OutputStream> openWriteStream(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String resourceId
    );

    /**
     * Gets the size of a resource without opening a stream.
     *
     * <p>Useful for progress indicators and validation.</p>
     *
     * @param resourceId identifier for the resource
     * @return size in bytes, or -1 if unknown
     */
    default FNLResult<Long> getResourceSize(String resourceId) {
        return FNLResult.success(-1L);
    }

    /**
     * Checks if this skill supports seeking within streams.
     *
     * @return true if random access is supported
     */
    default boolean supportsSeek() {
        return false;
    }
}
