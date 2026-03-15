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
package dev.fararoni.core.core.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DataMigration {
    private static final Logger log = LoggerFactory.getLogger(DataMigration.class);

    private static final String OLD_HISTORY_DB = "llm_history.db";

    private static final String OLD_MEMORY_DB = "memory.db";

    private static final String OLD_CACHE_DB = "cache.db";

    private static final String[] WAL_EXTENSIONS = {"-wal", "-shm"};

    private DataMigration() {
    }

    public static MigrationResult migrateIfNeeded() {
        WorkspaceManager workspace = WorkspaceManager.getInstance();
        Path currentDir = Paths.get(".").toAbsolutePath().normalize();

        List<MigrationEntry> migrations = new ArrayList<>();
        List<MigrationEntry> skipped = new ArrayList<>();
        List<MigrationEntry> failed = new ArrayList<>();

        log.info("[DataMigration] Checking for legacy data files in: {}", currentDir);

        migrateFile(
                currentDir.resolve(OLD_HISTORY_DB),
                workspace.getInteractionsDbPath(),
                migrations, skipped, failed
        );

        migrateFile(
                currentDir.resolve(OLD_MEMORY_DB),
                workspace.getMemoryDbPath(),
                migrations, skipped, failed
        );

        migrateFile(
                currentDir.resolve(OLD_CACHE_DB),
                workspace.getCacheDbPath(),
                migrations, skipped, failed
        );

        MigrationResult result = new MigrationResult(migrations, skipped, failed);

        if (result.hasMigrations()) {
            log.info("[DataMigration] {}", result.getSummary());
        } else if (result.hasSkipped()) {
            log.debug("[DataMigration] No migrations needed (files already exist in workspace)");
        }

        return result;
    }

    public static boolean hasPendingMigrations() {
        WorkspaceManager workspace = WorkspaceManager.getInstance();
        Path currentDir = Paths.get(".").toAbsolutePath().normalize();

        return wouldMigrate(currentDir.resolve(OLD_HISTORY_DB), workspace.getInteractionsDbPath())
                || wouldMigrate(currentDir.resolve(OLD_MEMORY_DB), workspace.getMemoryDbPath())
                || wouldMigrate(currentDir.resolve(OLD_CACHE_DB), workspace.getCacheDbPath());
    }

    public static MigrationEntry migrateFile(Path source, Path target) {
        if (source == null || target == null) {
            return MigrationEntry.failed(source, target, "Null path provided");
        }

        if (!Files.exists(source)) {
            return MigrationEntry.skipped(source, target, "Source does not exist");
        }

        if (Files.exists(target)) {
            return MigrationEntry.skipped(source, target, "Target already exists");
        }

        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);

            for (String ext : WAL_EXTENSIONS) {
                Path walSource = Paths.get(source.toString() + ext);
                Path walTarget = Paths.get(target.toString() + ext);
                if (Files.exists(walSource)) {
                    try {
                        Files.move(walSource, walTarget, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        log.warn("[DataMigration] Failed to move WAL file {}: {}",
                                walSource.getFileName(), e.getMessage());
                    }
                }
            }

            log.info("[DataMigration] Migrated: {} -> {}", source.getFileName(), target);
            return MigrationEntry.success(source, target);
        } catch (IOException e) {
            log.error("[DataMigration] Failed to migrate {}: {}", source, e.getMessage());
            return MigrationEntry.failed(source, target, e.getMessage());
        }
    }

    private static void migrateFile(
            Path source,
            Path target,
            List<MigrationEntry> migrations,
            List<MigrationEntry> skipped,
            List<MigrationEntry> failed
    ) {
        MigrationEntry result = migrateFile(source, target);

        switch (result.status()) {
            case SUCCESS -> migrations.add(result);
            case SKIPPED -> skipped.add(result);
            case FAILED -> failed.add(result);
        }
    }

    private static boolean wouldMigrate(Path source, Path target) {
        return Files.exists(source) && !Files.exists(target);
    }

    public record MigrationEntry(
            Path source,
            Path target,
            MigrationStatus status,
            String message
    ) {
        public static MigrationEntry success(Path source, Path target) {
            return new MigrationEntry(source, target, MigrationStatus.SUCCESS, "Migrated successfully");
        }

        public static MigrationEntry skipped(Path source, Path target, String reason) {
            return new MigrationEntry(source, target, MigrationStatus.SKIPPED, reason);
        }

        public static MigrationEntry failed(Path source, Path target, String error) {
            return new MigrationEntry(source, target, MigrationStatus.FAILED, error);
        }

        public boolean isSuccess() {
            return status == MigrationStatus.SUCCESS;
        }
    }

    public enum MigrationStatus {
        SUCCESS,
        SKIPPED,
        FAILED
    }

    public record MigrationResult(
            List<MigrationEntry> migrations,
            List<MigrationEntry> skipped,
            List<MigrationEntry> failed
    ) {
        public boolean hasMigrations() {
            return !migrations.isEmpty();
        }

        public boolean hasSkipped() {
            return !skipped.isEmpty();
        }

        public boolean hasErrors() {
            return !failed.isEmpty();
        }

        public boolean isSuccess() {
            return failed.isEmpty();
        }

        public int migratedCount() {
            return migrations.size();
        }

        public String getSummary() {
            if (migrations.isEmpty() && failed.isEmpty()) {
                return "No files to migrate";
            }

            StringBuilder sb = new StringBuilder();

            if (!migrations.isEmpty()) {
                sb.append(String.format("Migrated %d file(s)", migrations.size()));
                for (MigrationEntry entry : migrations) {
                    sb.append(String.format("%n  - %s -> %s",
                            entry.source().getFileName(),
                            entry.target()));
                }
            }

            if (!failed.isEmpty()) {
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(String.format("Failed %d file(s)", failed.size()));
                for (MigrationEntry entry : failed) {
                    sb.append(String.format("%n  - %s: %s",
                            entry.source().getFileName(),
                            entry.message()));
                }
            }

            return sb.toString();
        }

        public String getShortSummary() {
            if (migrations.isEmpty() && failed.isEmpty()) {
                return "No migration needed";
            }
            if (failed.isEmpty()) {
                return String.format("Migrated %d file(s) to workspace", migrations.size());
            }
            return String.format("Migrated %d, failed %d", migrations.size(), failed.size());
        }
    }
}
