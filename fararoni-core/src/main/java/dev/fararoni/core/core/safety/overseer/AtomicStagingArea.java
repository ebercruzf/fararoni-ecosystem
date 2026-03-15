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
package dev.fararoni.core.core.safety.overseer;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class AtomicStagingArea implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(AtomicStagingArea.class.getName());

    private static final String STAGING_DIR_NAME = ".fararoni-staging";

    private final Path baseDir;

    private final Path stagingDir;

    private final String transactionId;

    private final Map<Path, Path> stagedFiles = new ConcurrentHashMap<>();

    private volatile boolean committed = false;
    private volatile boolean rolledBack = false;

    public AtomicStagingArea(Path baseDir) {
        this(baseDir, "txn-" + System.currentTimeMillis() + "-" +
                      Long.toHexString(Double.doubleToLongBits(Math.random())));
    }

    public AtomicStagingArea(Path baseDir, String transactionId) {
        this.baseDir = baseDir;
        this.transactionId = transactionId;
        this.stagingDir = baseDir.resolve(STAGING_DIR_NAME).resolve(transactionId);

        try {
            Files.createDirectories(stagingDir);
            LOG.info("StagingArea created: " + stagingDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create staging directory", e);
        }
    }

    public Path stage(Path targetPath, String content) {
        checkNotFinalized();

        Path normalizedTarget = targetPath.isAbsolute()
            ? targetPath
            : baseDir.resolve(targetPath);

        Path relativePath = baseDir.relativize(normalizedTarget);
        Path stagedPath = stagingDir.resolve(relativePath);

        try {
            Files.createDirectories(stagedPath.getParent());

            Files.writeString(stagedPath, content, StandardOpenOption.CREATE,
                             StandardOpenOption.TRUNCATE_EXISTING);

            stagedFiles.put(normalizedTarget, stagedPath);

            LOG.fine("Staged: " + relativePath);

            return stagedPath;
        } catch (IOException e) {
            throw new StagingException("Failed to stage file: " + targetPath, e);
        }
    }

    public Path stage(String targetPath, String content) {
        return stage(Path.of(targetPath), content);
    }

    public List<Path> commit() {
        checkNotFinalized();

        List<Path> committed = new ArrayList<>();
        List<Path> failed = new ArrayList<>();

        LOG.info("Committing " + stagedFiles.size() + " files...");

        for (Map.Entry<Path, Path> entry : stagedFiles.entrySet()) {
            Path target = entry.getKey();
            Path staged = entry.getValue();

            try {
                Files.createDirectories(target.getParent());

                Files.move(staged, target,
                          StandardCopyOption.REPLACE_EXISTING,
                          StandardCopyOption.ATOMIC_MOVE);

                committed.add(target);
                LOG.fine("Committed: " + target);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to commit: " + target, e);
                failed.add(target);
            }
        }

        cleanupStagingDir();

        this.committed = true;

        if (!failed.isEmpty()) {
            LOG.warning("Commit partial: " + committed.size() +
                       " OK, " + failed.size() + " failed");
        } else {
            LOG.info("Commit complete: " + committed.size() + " files");
        }

        return committed;
    }

    public void rollback() {
        if (committed) {
            LOG.warning("Cannot rollback: already committed");
            return;
        }

        LOG.info("Rolling back " + stagedFiles.size() + " files...");

        cleanupStagingDir();

        this.rolledBack = true;
        stagedFiles.clear();

        LOG.info("Rollback complete");
    }

    public List<Path> getStagedFiles() {
        return List.copyOf(stagedFiles.keySet());
    }

    public int getStagedCount() {
        return stagedFiles.size();
    }

    public boolean isEmpty() {
        return stagedFiles.isEmpty();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public boolean isCommitted() {
        return committed;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    private void checkNotFinalized() {
        if (committed) {
            throw new IllegalStateException("Transaction already committed");
        }
        if (rolledBack) {
            throw new IllegalStateException("Transaction already rolled back");
        }
    }

    private void cleanupStagingDir() {
        try {
            if (Files.exists(stagingDir)) {
                try (var walk = Files.walk(stagingDir)) {
                    walk.sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                LOG.warning("Could not delete: " + path);
                            }
                        });
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error cleaning staging dir", e);
        }
    }

    public static void cleanupOrphanedStaging(Path baseDir) {
        Path stagingRoot = baseDir.resolve(STAGING_DIR_NAME);
        if (!Files.exists(stagingRoot)) {
            return;
        }

        try (var dirs = Files.list(stagingRoot)) {
            dirs.filter(Files::isDirectory)
                .forEach(txnDir -> {
                    LOG.info("Cleaning orphaned staging: " + txnDir.getFileName());
                    try (var walk = Files.walk(txnDir)) {
                        walk.sorted((a, b) -> b.compareTo(a))
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    LOG.warning("Could not delete: " + path);
                                }
                            });
                    } catch (IOException e) {
                        LOG.warning("Error walking: " + txnDir);
                    }
                });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error listing staging dirs", e);
        }
    }

    @Override
    public void close() {
        if (!committed && !rolledBack) {
            LOG.warning("StagingArea closed without commit/rollback - rolling back");
            rollback();
        }
    }

    public static class StagingException extends RuntimeException {
        public StagingException(String message) {
            super(message);
        }

        public StagingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
