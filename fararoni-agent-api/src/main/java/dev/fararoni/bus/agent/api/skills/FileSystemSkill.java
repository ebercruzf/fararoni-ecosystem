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
package dev.fararoni.bus.agent.api.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.io.StreamableSkill;
import dev.fararoni.bus.agent.api.saga.SagaCapableSkill;
import dev.fararoni.bus.agent.api.security.AuditLog;
import dev.fararoni.bus.agent.api.security.RequiresRole;

import java.util.List;

/**
 * Contract for file system operations with Saga compensation and streaming.
 *
 * <p>This interface defines the contract for file operations that the AI agent
 * can invoke. Implementations must support automatic rollback (Saga) and
 * efficient binary streaming.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Saga Support:</strong> All write operations return compensation
 *       instructions for automatic rollback</li>
 *   <li><strong>Binary Streaming:</strong> Large files handled via streams,
 *       not Base64 encoding</li>
 *   <li><strong>Path Sanitization:</strong> All paths are validated and
 *       sandboxed to prevent traversal attacks</li>
 *   <li><strong>Audit Logging:</strong> All operations are logged for compliance</li>
 * </ul>
 *
 * <h2>Saga Compensation</h2>
 * <pre>
 * Agent: writeFile("/app/config.json", newConfig)
 *        ↓
 * FNL: 1. Backup existing file to /tmp/backup_xxx
 *      2. Write new content
 *      3. Return success + CompensationInstruction("restore", backup, target)
 *        ↓
 * Later step fails...
 *        ↓
 * FNL: Execute compensation → restore backup → original state recovered
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Simple file write with automatic Saga
 * FNLResult<String> result = fileSkill.writeFile("/app/config.json", newConfig);
 *
 * if (result.success()) {
 *     // Operation succeeded, compensation stored
 *     CompensationInstruction undo = result.undoInstruction();
 *     // If later step fails, call: fileSkill.compensate(undo);
 * }
 *
 * // Large file streaming
 * FNLResult<BinaryStreamHandle> stream = fileSkill.openReadStream("/logs/huge.log");
 * try (BinaryStreamHandle handle = stream.data()) {
 *     // Process in 8KB chunks - never loads entire file
 *     byte[] buffer = new byte[8192];
 *     int read;
 *     while ((read = handle.stream().read(buffer)) != -1) {
 *         process(buffer, 0, read);
 *     }
 * }
 * }</pre>
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>All paths sanitized against traversal (../, etc.)</li>
 *   <li>Operations confined to workspace sandbox</li>
 *   <li>Sensitive operations require elevated roles</li>
 *   <li>All mutations logged for audit</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see SagaCapableSkill
 * @see StreamableSkill
 */
public interface FileSystemSkill extends SagaCapableSkill, StreamableSkill {

    // ==================== Read Operations ====================

    /**
     * Reads the content of a file.
     *
     * <p>For small files (&lt; 1MB), returns content directly. For large files,
     * use {@link #openReadStream(String)} instead for memory efficiency.</p>
     *
     * @param path the file path (will be sanitized)
     * @return result containing file content as string
     */
    @AgentAction(
        name = "read_file",
        description = "Reads text content from a file. Use open_read_stream for binary or large files."
    )
    FNLResult<String> readFile(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path
    );

    /**
     * Lists files and directories in a path.
     *
     * @param path the directory path
     * @param recursive whether to list recursively
     * @return result containing list of file entries
     */
    @AgentAction(
        name = "list_files",
        description = "Lists files and directories. Set recursive=true for subdirectories."
    )
    FNLResult<List<FileEntry>> listFiles(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path,
        boolean recursive
    );

    /**
     * Checks if a file or directory exists.
     *
     * @param path the path to check
     * @return result containing true if exists
     */
    @AgentAction(
        name = "exists",
        description = "Checks if a file or directory exists at the given path"
    )
    FNLResult<Boolean> exists(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path
    );

    /**
     * Gets file metadata without reading content.
     *
     * @param path the file path
     * @return result containing file metadata
     */
    @AgentAction(
        name = "file_info",
        description = "Gets file metadata: size, type, permissions, timestamps"
    )
    FNLResult<FileInfo> getFileInfo(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path
    );

    // ==================== Write Operations (Saga-Enabled) ====================

    /**
     * Writes content to a file.
     *
     * <p>If the file exists, creates a backup for Saga compensation.
     * If new, compensation will delete the file on rollback.</p>
     *
     * @param path the file path
     * @param content the content to write
     * @return result with compensation instruction
     */
    @AgentAction(
        name = "write_file",
        description = "Writes text content to a file. Supports automatic rollback via Saga."
    )
    @AuditLog(severity = "INFO", category = "FILE_WRITE")
    FNLResult<String> writeFile(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path,
        String content
    );

    /**
     * Appends content to a file.
     *
     * <p>Compensation truncates file to original length.</p>
     *
     * @param path the file path
     * @param content the content to append
     * @return result with compensation instruction
     */
    @AgentAction(
        name = "append_file",
        description = "Appends content to end of file. Creates file if not exists."
    )
    @AuditLog(severity = "INFO", category = "FILE_WRITE")
    FNLResult<String> appendFile(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path,
        String content
    );

    /**
     * Deletes a file or empty directory.
     *
     * <p>Creates backup before deletion for Saga compensation.</p>
     *
     * @param path the path to delete
     * @return result with compensation instruction to restore
     */
    @AgentAction(
        name = "delete",
        description = "Deletes a file or empty directory. Backup created for rollback."
    )
    @AuditLog(severity = "WARN", category = "FILE_DELETE")
    @RequiresRole("file:delete")
    FNLResult<Boolean> delete(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path
    );

    /**
     * Creates a directory.
     *
     * @param path the directory path
     * @param createParents whether to create parent directories
     * @return result with compensation instruction to delete
     */
    @AgentAction(
        name = "mkdir",
        description = "Creates a directory. Set createParents=true for nested directories."
    )
    @AuditLog(severity = "INFO", category = "FILE_CREATE")
    FNLResult<Boolean> mkdir(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path,
        boolean createParents
    );

    /**
     * Moves or renames a file/directory.
     *
     * @param source the source path
     * @param destination the destination path
     * @return result with compensation instruction to reverse move
     */
    @AgentAction(
        name = "move",
        description = "Moves or renames a file/directory."
    )
    @AuditLog(severity = "INFO", category = "FILE_MOVE")
    FNLResult<Boolean> move(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String source,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String destination
    );

    /**
     * Copies a file or directory.
     *
     * @param source the source path
     * @param destination the destination path
     * @param recursive whether to copy directories recursively
     * @return result with compensation instruction to delete copy
     */
    @AgentAction(
        name = "copy",
        description = "Copies a file or directory to new location."
    )
    @AuditLog(severity = "INFO", category = "FILE_COPY")
    FNLResult<Boolean> copy(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String source,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String destination,
        boolean recursive
    );

    // ==================== Search Operations ====================

    /**
     * Searches for files matching a glob pattern.
     *
     * @param baseDir the base directory to search from
     * @param pattern glob pattern (e.g., "*.java", "**\/*.txt")
     * @param maxResults maximum number of results
     * @return result containing matching file paths
     */
    @AgentAction(
        name = "find_files",
        description = "Finds files matching a glob pattern (e.g., *.java, **/*.txt)"
    )
    FNLResult<List<String>> findFiles(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String baseDir,
        String pattern,
        int maxResults
    );

    /**
     * Searches for content within files.
     *
     * @param baseDir the base directory to search
     * @param pattern regex pattern to search for
     * @param filePattern optional file glob filter
     * @return result containing matches with line numbers
     */
    @AgentAction(
        name = "grep",
        description = "Searches file contents for a regex pattern"
    )
    FNLResult<List<SearchMatch>> grep(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String baseDir,
        String pattern,
        String filePattern
    );

    // ==================== Nested Types ====================

    /**
     * Represents a file/directory entry in a listing.
     *
     * @param name the file name
     * @param path the full path
     * @param isDirectory whether this is a directory
     * @param size size in bytes (-1 for directories)
     */
    record FileEntry(String name, String path, boolean isDirectory, long size) {}

    /**
     * Detailed file information.
     *
     * @param path the file path
     * @param size size in bytes
     * @param isDirectory whether this is a directory
     * @param isReadable whether file is readable
     * @param isWritable whether file is writable
     * @param isExecutable whether file is executable
     * @param createdAt creation timestamp (epoch millis)
     * @param modifiedAt last modified timestamp (epoch millis)
     * @param mimeType detected MIME type
     */
    record FileInfo(
        String path,
        long size,
        boolean isDirectory,
        boolean isReadable,
        boolean isWritable,
        boolean isExecutable,
        long createdAt,
        long modifiedAt,
        String mimeType
    ) {}

    /**
     * Represents a search match within a file.
     *
     * @param file the file path
     * @param line the line number (1-based)
     * @param content the matching line content
     * @param matchStart start index of match within line
     * @param matchEnd end index of match within line
     */
    record SearchMatch(String file, int line, String content, int matchStart, int matchEnd) {}
}
