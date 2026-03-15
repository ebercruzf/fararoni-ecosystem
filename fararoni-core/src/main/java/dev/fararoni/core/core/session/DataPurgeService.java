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
package dev.fararoni.core.core.session;

import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DataPurgeService {
    private static final Logger log = LoggerFactory.getLogger(DataPurgeService.class);

    private static volatile DataPurgeService instance;
    private static final Object LOCK = new Object();

    private static final int SECURE_DELETE_PASSES = 3;
    private static final int BUFFER_SIZE = 64 * 1024;

    private long filesDeleted = 0;
    private long bytesDeleted = 0;
    private long secureDeletesPerformed = 0;

    private DataPurgeService() {
        log.info("[DataPurgeService] Initialized - Secure delete available");
    }

    public static DataPurgeService getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new DataPurgeService();
                }
            }
        }
        return instance;
    }

    public PurgeResult secureDelete(Path filePath) {
        if (filePath == null) {
            return PurgeResult.failure("Path is null");
        }

        if (!Files.exists(filePath)) {
            return PurgeResult.failure("File does not exist: " + filePath);
        }

        if (Files.isDirectory(filePath)) {
            return PurgeResult.failure("Cannot secure delete directory, use purgeDirectory(): " + filePath);
        }

        try {
            long fileSize = Files.size(filePath);

            overwriteFile(filePath, (byte) 0x00, "zeros");
            overwriteFile(filePath, (byte) 0xFF, "ones");
            overwriteFileRandom(filePath);

            Files.delete(filePath);

            filesDeleted++;
            bytesDeleted += fileSize;
            secureDeletesPerformed++;

            log.info("[DataPurgeService] Secure deleted: {} ({} bytes)", filePath.getFileName(), fileSize);

            return PurgeResult.success(1, fileSize);
        } catch (IOException e) {
            log.error("[DataPurgeService] Failed to secure delete {}: {}", filePath, e.getMessage());
            return PurgeResult.failure("Failed to secure delete: " + e.getMessage());
        }
    }

    public PurgeResult standardDelete(Path filePath) {
        if (filePath == null) {
            return PurgeResult.failure("Path is null");
        }

        if (!Files.exists(filePath)) {
            return PurgeResult.success(0, 0);
        }

        try {
            long fileSize = Files.isRegularFile(filePath) ? Files.size(filePath) : 0;

            if (Files.isDirectory(filePath)) {
                return purgeDirectory(filePath, false);
            }

            Files.delete(filePath);

            filesDeleted++;
            bytesDeleted += fileSize;

            return PurgeResult.success(1, fileSize);
        } catch (IOException e) {
            return PurgeResult.failure("Failed to delete: " + e.getMessage());
        }
    }

    public PurgeResult purgeDirectory(Path dirPath, boolean secureDelete) {
        if (dirPath == null) {
            return PurgeResult.failure("Path is null");
        }

        if (!Files.exists(dirPath)) {
            return PurgeResult.success(0, 0);
        }

        if (!Files.isDirectory(dirPath)) {
            return secureDelete ? secureDelete(dirPath) : standardDelete(dirPath);
        }

        List<Path> filesToDelete = new ArrayList<>();
        List<Path> dirsToDelete = new ArrayList<>();
        long totalSize = 0;

        try {
            Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    filesToDelete.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    dirsToDelete.add(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            for (Path file : filesToDelete) {
                try {
                    totalSize += Files.size(file);
                } catch (IOException ignored) {
                }
            }

            int deletedCount = 0;
            for (Path file : filesToDelete) {
                try {
                    if (secureDelete) {
                        overwriteFile(file, (byte) 0x00, "zeros");
                        overwriteFile(file, (byte) 0xFF, "ones");
                        overwriteFileRandom(file);
                        secureDeletesPerformed++;
                    }
                    Files.delete(file);
                    deletedCount++;
                    filesDeleted++;
                } catch (IOException e) {
                    log.warn("[DataPurgeService] Failed to delete file: {}", file);
                }
            }

            for (Path dir : dirsToDelete) {
                try {
                    Files.delete(dir);
                } catch (IOException e) {
                    log.warn("[DataPurgeService] Failed to delete directory: {}", dir);
                }
            }

            bytesDeleted += totalSize;

            log.info("[DataPurgeService] Purged directory: {} ({} files, {} bytes)",
                    dirPath, deletedCount, totalSize);

            return PurgeResult.success(deletedCount, totalSize);
        } catch (IOException e) {
            return PurgeResult.failure("Failed to purge directory: " + e.getMessage());
        }
    }

    public PurgeResult purgeAll() {
        return purgeAll(false);
    }

    public PurgeResult purgeAll(boolean secureDelete) {
        WorkspaceManager workspace = WorkspaceManager.getInstance();
        Path workspaceDir = workspace.getWorkspaceDir();

        log.warn("[DataPurgeService] PURGING ALL DATA from: {}", workspaceDir);

        if (!Files.exists(workspaceDir)) {
            return PurgeResult.success(0, 0);
        }

        return purgeDirectory(workspaceDir, secureDelete);
    }

    public PurgeResult purgeSelective(PurgeConfig purgeConfig) {
        WorkspaceManager workspace = WorkspaceManager.getInstance();
        int totalFiles = 0;
        long totalBytes = 0;

        if (purgeConfig.purgeHistory()) {
            PurgeResult r = secureDelete(workspace.getInteractionsDbPath());
            if (r.success()) {
                totalFiles += r.filesDeleted();
                totalBytes += r.bytesDeleted();
            }
        }

        if (purgeConfig.purgeMemory()) {
            PurgeResult r = secureDelete(workspace.getMemoryDbPath());
            if (r.success()) {
                totalFiles += r.filesDeleted();
                totalBytes += r.bytesDeleted();
            }
        }

        if (purgeConfig.purgeCache()) {
            PurgeResult r = secureDelete(workspace.getCacheDbPath());
            if (r.success()) {
                totalFiles += r.filesDeleted();
                totalBytes += r.bytesDeleted();
            }
        }

        if (purgeConfig.purgeAuditLog()) {
            PurgeResult r = standardDelete(workspace.getAuditLogPath());
            if (r.success()) {
                totalFiles += r.filesDeleted();
                totalBytes += r.bytesDeleted();
            }
        }

        if (purgeConfig.purgeConfig()) {
            PurgeResult r = secureDelete(workspace.getConfigPath());
            if (r.success()) {
                totalFiles += r.filesDeleted();
                totalBytes += r.bytesDeleted();
            }
        }

        return PurgeResult.success(totalFiles, totalBytes);
    }

    private void overwriteFile(Path filePath, byte fillByte, String passName) throws IOException {
        long fileSize = Files.size(filePath);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        for (int i = 0; i < BUFFER_SIZE; i++) {
            buffer.put(fillByte);
        }

        try (FileChannel channel = FileChannel.open(filePath,
                StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
            long written = 0;
            while (written < fileSize) {
                buffer.flip();
                int toWrite = (int) Math.min(BUFFER_SIZE, fileSize - written);
                buffer.limit(toWrite);
                written += channel.write(buffer);
                buffer.clear();
                for (int i = 0; i < BUFFER_SIZE; i++) {
                    buffer.put(fillByte);
                }
            }

            channel.force(true);
        }

        log.debug("[DataPurgeService] Pass {}: {} bytes written with {}", passName, fileSize, fillByte);
    }

    private void overwriteFileRandom(Path filePath) throws IOException {
        long fileSize = Files.size(filePath);
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        SecureRandom random = new SecureRandom();

        try (FileChannel channel = FileChannel.open(filePath,
                StandardOpenOption.WRITE, StandardOpenOption.SYNC)) {
            long written = 0;
            while (written < fileSize) {
                byte[] randomBytes = new byte[BUFFER_SIZE];
                random.nextBytes(randomBytes);
                buffer.clear();
                buffer.put(randomBytes);
                buffer.flip();

                int toWrite = (int) Math.min(BUFFER_SIZE, fileSize - written);
                buffer.limit(toWrite);
                written += channel.write(buffer);
            }

            channel.force(true);
        }

        log.debug("[DataPurgeService] Pass random: {} bytes written", fileSize);
    }

    public long getFilesDeleted() {
        return filesDeleted;
    }

    public long getBytesDeleted() {
        return bytesDeleted;
    }

    public long getSecureDeletesPerformed() {
        return secureDeletesPerformed;
    }

    public String getStatsSummary() {
        return String.format(
                "[DataPurgeService] Stats: files=%d, bytes=%d (%.2f MB), secure_deletes=%d",
                filesDeleted, bytesDeleted, bytesDeleted / (1024.0 * 1024.0), secureDeletesPerformed
        );
    }

    public void resetStats() {
        filesDeleted = 0;
        bytesDeleted = 0;
        secureDeletesPerformed = 0;
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    public record PurgeResult(
            boolean success,
            int filesDeleted,
            long bytesDeleted,
            String errorMessage
    ) {
        public static PurgeResult success(int files, long bytes) {
            return new PurgeResult(true, files, bytes, null);
        }

        public static PurgeResult failure(String error) {
            return new PurgeResult(false, 0, 0, error);
        }

        public String getSummary() {
            if (!success) {
                return "[ERROR] Purge failed: " + errorMessage;
            }
            double mb = bytesDeleted / (1024.0 * 1024.0);
            return String.format("[PURGE] Todos los datos han sido vaporizados: %d archivos eliminados (%.2f MB)",
                    filesDeleted, mb);
        }

        public String getShortSummary() {
            if (!success) {
                return "Failed: " + errorMessage;
            }
            return String.format("%d files, %.2f MB deleted", filesDeleted, bytesDeleted / (1024.0 * 1024.0));
        }
    }

    public record PurgeConfig(
            boolean purgeHistory,
            boolean purgeMemory,
            boolean purgeCache,
            boolean purgeAuditLog,
            boolean purgeConfig
    ) {
        public static PurgeConfig all() {
            return new PurgeConfig(true, true, true, true, true);
        }

        public static PurgeConfig historyOnly() {
            return new PurgeConfig(true, false, false, false, false);
        }

        public static PurgeConfig dataOnly() {
            return new PurgeConfig(true, true, true, true, false);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean history = false;
            private boolean memory = false;
            private boolean cache = false;
            private boolean audit = false;
            private boolean config = false;

            public Builder purgeHistory() { this.history = true; return this; }
            public Builder purgeMemory() { this.memory = true; return this; }
            public Builder purgeCache() { this.cache = true; return this; }
            public Builder purgeAuditLog() { this.audit = true; return this; }
            public Builder purgeConfig() { this.config = true; return this; }

            public PurgeConfig build() {
                return new PurgeConfig(history, memory, cache, audit, config);
            }
        }
    }
}
