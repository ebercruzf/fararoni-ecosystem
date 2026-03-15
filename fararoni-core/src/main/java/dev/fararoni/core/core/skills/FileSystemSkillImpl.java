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
package dev.fararoni.core.core.skills;

import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.io.BinaryStreamHandle;
import dev.fararoni.bus.agent.api.saga.CompensationInstruction;
import dev.fararoni.bus.agent.api.skills.FileSystemSkill;

import dev.fararoni.core.config.ServiceRegistry;
import dev.fararoni.core.core.session.FileAction;
import dev.fararoni.core.core.swarm.context.SwarmContext;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class FileSystemSkillImpl implements FileSystemSkill {
    private static final Logger LOG = Logger.getLogger(FileSystemSkillImpl.class.getName());
    private static final String SKILL_NAME = "FileSystemSkill";
    private static final long MAX_READ_SIZE = 10 * 1024 * 1024;
    private static final long MAX_STREAM_SIZE = 1024 * 1024 * 1024;

    private final Path workspaceRoot;
    private final Path backupDir;

    public FileSystemSkillImpl(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.backupDir = this.workspaceRoot.resolve(".fararoni/backups");

        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            LOG.warning("Could not create backup directory: " + e.getMessage());
        }
    }

    @Override
    public String getSkillName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return "File system operations with Saga compensation for automatic rollback";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public FNLResult<Void> compensate(CompensationInstruction instruction) {
        String method = instruction.method();
        LOG.info(() -> String.format("[COMPENSATE] %s.%s with %s", SKILL_NAME, method, instruction.params()));

        try {
            return switch (method) {
                case "delete" -> compensateDelete(instruction);
                case "restore" -> compensateRestore(instruction);
                case "truncate" -> compensateTruncate(instruction);
                case "move_back" -> compensateMoveBack(instruction);
                default -> FNLResult.failure("Unknown compensation method: " + method);
            };
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Compensation failed: " + method, e);
            return FNLResult.failure("Compensation failed: " + e.getMessage());
        }
    }

    private FNLResult<Void> compensateDelete(CompensationInstruction instruction) {
        String path = instruction.getParam("path");
        try {
            Path target = resolvePath(path);
            Files.deleteIfExists(target);
            LOG.info(() -> "[COMPENSATE] Deleted: " + path);
            return FNLResult.success(null);
        } catch (IOException e) {
            return FNLResult.failure("Failed to delete: " + e.getMessage());
        }
    }

    private FNLResult<Void> compensateRestore(CompensationInstruction instruction) {
        String backup = instruction.getParam("backup");
        String target = instruction.getParam("target");
        try {
            Path backupPath = Path.of(backup);
            Path targetPath = resolvePath(target);

            if (Files.exists(backupPath)) {
                Files.move(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LOG.info(() -> "[COMPENSATE] Restored: " + target + " from " + backup);
            }
            return FNLResult.success(null);
        } catch (IOException e) {
            return FNLResult.failure("Failed to restore: " + e.getMessage());
        }
    }

    private FNLResult<Void> compensateTruncate(CompensationInstruction instruction) {
        String path = instruction.getParam("path");
        long originalSize = instruction.getParam("originalSize", 0L);
        try {
            Path target = resolvePath(path);
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
                channel.truncate(originalSize);
            }
            LOG.info(() -> "[COMPENSATE] Truncated: " + path + " to " + originalSize);
            return FNLResult.success(null);
        } catch (IOException e) {
            return FNLResult.failure("Failed to truncate: " + e.getMessage());
        }
    }

    private FNLResult<Void> compensateMoveBack(CompensationInstruction instruction) {
        String source = instruction.getParam("source");
        String destination = instruction.getParam("destination");
        try {
            Path sourcePath = resolvePath(source);
            Path destPath = resolvePath(destination);
            Files.move(destPath, sourcePath, StandardCopyOption.REPLACE_EXISTING);
            LOG.info(() -> "[COMPENSATE] Moved back: " + destination + " to " + source);
            return FNLResult.success(null);
        } catch (IOException e) {
            return FNLResult.failure("Failed to move back: " + e.getMessage());
        }
    }

    @Override
    public String[] getSupportedCompensations() {
        return new String[]{"delete", "restore", "truncate", "move_back"};
    }

    @Override
    public FNLResult<BinaryStreamHandle> openReadStream(String path) {
        try {
            Path resolved = resolvePath(path);
            if (!Files.exists(resolved)) {
                return FNLResult.failure("File not found: " + path);
            }

            long size = Files.size(resolved);
            InputStream stream = new BufferedInputStream(Files.newInputStream(resolved));
            String mimeType = Files.probeContentType(resolved);

            BinaryStreamHandle handle = BinaryStreamHandle.of(
                stream, size, mimeType != null ? mimeType : "application/octet-stream"
            );

            return FNLResult.success(handle);
        } catch (IOException e) {
            return FNLResult.failure("Failed to open stream: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<OutputStream> openWriteStream(String path) {
        try {
            Path resolved = resolvePath(path);
            Files.createDirectories(resolved.getParent());

            OutputStream stream = new BufferedOutputStream(Files.newOutputStream(resolved,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));

            return FNLResult.success(stream);
        } catch (IOException e) {
            return FNLResult.failure("Failed to open write stream: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<Long> getResourceSize(String path) {
        try {
            Path resolved = resolvePath(path);
            if (!Files.exists(resolved)) {
                return FNLResult.success(-1L);
            }
            return FNLResult.success(Files.size(resolved));
        } catch (IOException e) {
            return FNLResult.success(-1L);
        }
    }

    @Override
    public FNLResult<String> readFile(String path) {
        try {
            Path resolved = resolvePath(path);
            if (!Files.exists(resolved)) {
                return FNLResult.failure("File not found: " + path);
            }

            long size = Files.size(resolved);
            if (size > MAX_READ_SIZE) {
                return FNLResult.failure("File too large for readFile. Use openReadStream instead. Size: " + size);
            }

            String content = Files.readString(resolved, StandardCharsets.UTF_8);
            return FNLResult.success(content);
        } catch (IOException e) {
            return FNLResult.failure("Failed to read file: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<List<FileEntry>> listFiles(String path, boolean recursive) {
        try {
            Path resolved = resolvePath(path);
            if (!Files.exists(resolved)) {
                return FNLResult.failure("Directory not found: " + path);
            }
            if (!Files.isDirectory(resolved)) {
                return FNLResult.failure("Not a directory: " + path);
            }

            List<FileEntry> entries = new ArrayList<>();

            if (recursive) {
                try (Stream<Path> walk = Files.walk(resolved)) {
                    walk.filter(p -> !p.equals(resolved))
                        .forEach(p -> entries.add(createFileEntry(p)));
                }
            } else {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolved)) {
                    for (Path p : stream) {
                        entries.add(createFileEntry(p));
                    }
                }
            }

            return FNLResult.success(entries);
        } catch (IOException e) {
            return FNLResult.failure("Failed to list files: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<Boolean> exists(String path) {
        try {
            Path resolved = resolvePath(path);
            return FNLResult.success(Files.exists(resolved));
        } catch (Exception e) {
            return FNLResult.failure("Failed to check existence: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<FileInfo> getFileInfo(String path) {
        try {
            Path resolved = resolvePath(path);
            if (!Files.exists(resolved)) {
                return FNLResult.failure("File not found: " + path);
            }

            BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class);
            String mimeType = Files.probeContentType(resolved);

            FileInfo info = new FileInfo(
                resolved.toString(),
                attrs.size(),
                attrs.isDirectory(),
                Files.isReadable(resolved),
                Files.isWritable(resolved),
                Files.isExecutable(resolved),
                attrs.creationTime().toMillis(),
                attrs.lastModifiedTime().toMillis(),
                mimeType != null ? mimeType : "application/octet-stream"
            );

            return FNLResult.success(info);
        } catch (IOException e) {
            return FNLResult.failure("Failed to get file info: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<String> writeFile(String path, String content) {
        try {
            Path resolved = resolvePath(path);
            boolean existed = Files.exists(resolved);
            String backupPath = null;

            if (existed) {
                backupPath = createBackup(resolved);
            }

            Files.createDirectories(resolved.getParent());

            Files.writeString(resolved, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            CompensationInstruction compensation;
            if (backupPath != null) {
                compensation = CompensationInstruction.of(SKILL_NAME, "restore",
                    Map.of("backup", backupPath, "target", path));
            } else {
                compensation = CompensationInstruction.of(SKILL_NAME, "delete",
                    Map.of("path", path));
            }

            ServiceRegistry.trackFile(path, existed ? FileAction.MODIFIED : FileAction.CREATED);

            SwarmContext.registerFileIfAvailable(resolved);

            LOG.info(() -> "[WRITE] " + path + " (" + content.length() + " bytes)");
            return FNLResult.successWithSaga("Written: " + path, compensation);
        } catch (IOException e) {
            return FNLResult.failure("Failed to write file: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<String> appendFile(String path, String content) {
        try {
            Path resolved = resolvePath(path);
            long originalSize = Files.exists(resolved) ? Files.size(resolved) : 0;

            Files.writeString(resolved, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            CompensationInstruction compensation = CompensationInstruction.of(
                SKILL_NAME, "truncate",
                Map.of("path", path, "originalSize", originalSize)
            );

            ServiceRegistry.trackFile(path, FileAction.MODIFIED);

            LOG.info(() -> "[APPEND] " + path + " (+" + content.length() + " bytes)");
            return FNLResult.successWithSaga("Appended: " + path, compensation);
        } catch (IOException e) {
            return FNLResult.failure("Failed to append to file: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<Boolean> delete(String path) {
        try {
            Path resolved = resolvePath(path);
            if (!Files.exists(resolved)) {
                return FNLResult.success(false);
            }

            String backupPath = createBackup(resolved);

            Files.delete(resolved);

            CompensationInstruction compensation = CompensationInstruction.of(
                SKILL_NAME, "restore",
                Map.of("backup", backupPath, "target", path)
            );

            ServiceRegistry.trackFile(path, FileAction.DELETED);

            LOG.info(() -> "[DELETE] " + path);
            return FNLResult.successWithSaga(true, compensation);
        } catch (IOException e) {
            return FNLResult.failure("Failed to delete: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<Boolean> mkdir(String path, boolean createParents) {
        try {
            Path resolved = resolvePath(path);

            if (Files.exists(resolved)) {
                return FNLResult.success(false);
            }

            if (createParents) {
                Files.createDirectories(resolved);
            } else {
                Files.createDirectory(resolved);
            }

            CompensationInstruction compensation = CompensationInstruction.of(
                SKILL_NAME, "delete",
                Map.of("path", path)
            );

            ServiceRegistry.trackFile(path, FileAction.CREATED);

            LOG.info(() -> "[MKDIR] " + path);
            return FNLResult.successWithSaga(true, compensation);
        } catch (IOException e) {
            return FNLResult.failure("Failed to create directory: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<Boolean> move(String source, String destination) {
        try {
            Path sourcePath = resolvePath(source);
            Path destPath = resolvePath(destination);

            if (!Files.exists(sourcePath)) {
                return FNLResult.failure("Source not found: " + source);
            }

            Files.createDirectories(destPath.getParent());
            Files.move(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);

            CompensationInstruction compensation = CompensationInstruction.of(
                SKILL_NAME, "move_back",
                Map.of("source", source, "destination", destination)
            );

            ServiceRegistry.trackFile(source, FileAction.DELETED);
            ServiceRegistry.trackFile(destination, FileAction.CREATED);

            LOG.info(() -> "[MOVE] " + source + " -> " + destination);
            return FNLResult.successWithSaga(true, compensation);
        } catch (IOException e) {
            return FNLResult.failure("Failed to move: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<Boolean> copy(String source, String destination, boolean recursive) {
        try {
            Path sourcePath = resolvePath(source);
            Path destPath = resolvePath(destination);

            if (!Files.exists(sourcePath)) {
                return FNLResult.failure("Source not found: " + source);
            }

            Files.createDirectories(destPath.getParent());

            if (Files.isDirectory(sourcePath) && recursive) {
                copyDirectory(sourcePath, destPath);
            } else {
                Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
            }

            CompensationInstruction compensation = CompensationInstruction.of(
                SKILL_NAME, "delete",
                Map.of("path", destination)
            );

            ServiceRegistry.trackFile(destination, FileAction.CREATED);

            LOG.info(() -> "[COPY] " + source + " -> " + destination);
            return FNLResult.successWithSaga(true, compensation);
        } catch (IOException e) {
            return FNLResult.failure("Failed to copy: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<List<String>> findFiles(String baseDir, String pattern, int maxResults) {
        try {
            Path basePath = resolvePath(baseDir);
            if (!Files.exists(basePath)) {
                return FNLResult.failure("Directory not found: " + baseDir);
            }

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> results = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(basePath)) {
                walk.filter(p -> matcher.matches(p.getFileName()))
                    .limit(maxResults)
                    .forEach(p -> results.add(workspaceRoot.relativize(p).toString()));
            }

            return FNLResult.success(results);
        } catch (IOException e) {
            return FNLResult.failure("Failed to find files: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<List<SearchMatch>> grep(String baseDir, String pattern, String filePattern) {
        try {
            Path basePath = resolvePath(baseDir);
            if (!Files.exists(basePath)) {
                return FNLResult.failure("Directory not found: " + baseDir);
            }

            Pattern regex = Pattern.compile(pattern);
            PathMatcher fileMatcher = filePattern != null ?
                FileSystems.getDefault().getPathMatcher("glob:" + filePattern) : null;

            List<SearchMatch> matches = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(basePath)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> fileMatcher == null || fileMatcher.matches(p.getFileName()))
                    .forEach(p -> searchInFile(p, regex, matches));
            }

            return FNLResult.success(matches);
        } catch (IOException e) {
            return FNLResult.failure("Failed to grep: " + e.getMessage());
        }
    }

    private void searchInFile(Path file, Pattern pattern, List<SearchMatch> matches) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relativePath = workspaceRoot.relativize(file).toString();

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    matches.add(new SearchMatch(
                        relativePath,
                        i + 1,
                        line,
                        matcher.start(),
                        matcher.end()
                    ));
                }
            }
        } catch (IOException e) {
            LOG.fine(() -> "Could not search in " + file + ": " + e.getMessage());
        }
    }

    private Path resolvePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null or blank");
        }

        if (path.contains("..")) {
            throw new SecurityException("Path traversal not allowed: " + path);
        }

        Path resolved = workspaceRoot.resolve(path).normalize();

        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Path escapes workspace: " + path);
        }

        return resolved;
    }

    private String createBackup(Path file) throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = file.getFileName().toString();
        Path backup = backupDir.resolve(fileName + "." + timestamp + ".bak");

        Files.createDirectories(backupDir);
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);

        return backup.toString();
    }

    private FileEntry createFileEntry(Path path) {
        try {
            return new FileEntry(
                path.getFileName().toString(),
                workspaceRoot.relativize(path).toString(),
                Files.isDirectory(path),
                Files.isDirectory(path) ? -1 : Files.size(path)
            );
        } catch (IOException e) {
            return new FileEntry(
                path.getFileName().toString(),
                workspaceRoot.relativize(path).toString(),
                Files.isDirectory(path),
                -1
            );
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(s -> {
                try {
                    Path d = target.resolve(source.relativize(s));
                    if (Files.isDirectory(s)) {
                        Files.createDirectories(d);
                    } else {
                        Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }
}
