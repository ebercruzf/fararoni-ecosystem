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
package dev.fararoni.core.core.safety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ShadowBackupManager {
    private static final String SHADOW_DIR = ".fararoni/shadow";

    private static final int MAX_VERSIONS_PER_FILE = 10;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final Path projectRoot;
    private final Path shadowDir;

    public ShadowBackupManager(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot no puede ser null")
            .toAbsolutePath().normalize();
        this.shadowDir = this.projectRoot.resolve(SHADOW_DIR);
    }

    public BackupEntry createBackup(Path file) {
        Objects.requireNonNull(file, "file no puede ser null");

        if (!Files.exists(file)) {
            throw new SafetyException(
                "Archivo no existe para backup: " + file,
                SafetyException.SafetyErrorCode.FILE_NOT_FOUND
            );
        }

        try {
            Files.createDirectories(shadowDir);

            String backupName = generateBackupName(file);
            Path backupPath = shadowDir.resolve(backupName);

            Files.copy(file, backupPath, StandardCopyOption.REPLACE_EXISTING);

            pruneOldVersions(file);

            return new BackupEntry(
                file.toAbsolutePath().normalize(),
                backupPath,
                Instant.now(),
                Files.size(backupPath)
            );
        } catch (IOException e) {
            throw new SafetyException(
                "Error creando backup: " + file,
                SafetyException.SafetyErrorCode.ATOMIC_WRITE_FAILED,
                e
            );
        }
    }

    public boolean restore(Path file) {
        Optional<BackupEntry> latest = getLatestBackup(file);

        if (latest.isEmpty()) {
            return false;
        }

        return restoreFrom(file, latest.get());
    }

    public boolean restoreFrom(Path file, BackupEntry backup) {
        Objects.requireNonNull(file, "file no puede ser null");
        Objects.requireNonNull(backup, "backup no puede ser null");

        if (!Files.exists(backup.backupPath())) {
            throw new SafetyException(
                "Backup no encontrado: " + backup.backupPath(),
                SafetyException.SafetyErrorCode.BACKUP_CORRUPTED
            );
        }

        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.copy(backup.backupPath(), file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            throw new SafetyException(
                "Error restaurando backup: " + file,
                SafetyException.SafetyErrorCode.ROLLBACK_FAILED,
                e
            );
        }
    }

    public Optional<BackupEntry> getLatestBackup(Path file) {
        List<BackupEntry> backups = listBackups(file);
        return backups.isEmpty() ? Optional.empty() : Optional.of(backups.get(0));
    }

    public List<BackupEntry> listBackups(Path file) {
        Objects.requireNonNull(file, "file no puede ser null");

        if (!Files.exists(shadowDir)) {
            return List.of();
        }

        String prefix = pathToBackupPrefix(file);
        List<BackupEntry> backups = new ArrayList<>();

        try (Stream<Path> stream = Files.list(shadowDir)) {
            stream
                .filter(p -> p.getFileName().toString().startsWith(prefix))
                .forEach(backupPath -> {
                    try {
                        backups.add(new BackupEntry(
                            file.toAbsolutePath().normalize(),
                            backupPath,
                            Files.getLastModifiedTime(backupPath).toInstant(),
                            Files.size(backupPath)
                        ));
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException e) {
            return List.of();
        }

        backups.sort(Comparator.comparing(BackupEntry::timestamp).reversed());
        return backups;
    }

    public List<BackupEntry> listAllBackups() {
        if (!Files.exists(shadowDir)) {
            return List.of();
        }

        List<BackupEntry> backups = new ArrayList<>();

        try (Stream<Path> stream = Files.list(shadowDir)) {
            stream
                .filter(Files::isRegularFile)
                .forEach(backupPath -> {
                    try {
                        String name = backupPath.getFileName().toString();
                        int versionIndex = name.lastIndexOf(".v");
                        if (versionIndex > 0) {
                            String originalName = name.substring(0, versionIndex)
                                .replace("_", "/");
                            Path originalPath = projectRoot.resolve(originalName);

                            backups.add(new BackupEntry(
                                originalPath,
                                backupPath,
                                Files.getLastModifiedTime(backupPath).toInstant(),
                                Files.size(backupPath)
                            ));
                        }
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException e) {
            return List.of();
        }

        backups.sort(Comparator.comparing(BackupEntry::timestamp).reversed());
        return backups;
    }

    public int clearBackups(Path file) {
        List<BackupEntry> backups = listBackups(file);
        int deleted = 0;

        for (BackupEntry backup : backups) {
            try {
                Files.deleteIfExists(backup.backupPath());
                deleted++;
            } catch (IOException ignored) {
            }
        }

        return deleted;
    }

    private String generateBackupName(Path file) {
        String prefix = pathToBackupPrefix(file);
        int version = listBackups(file).size() + 1;
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        return String.format("%s.v%d.%s", prefix, version, timestamp);
    }

    private String pathToBackupPrefix(Path file) {
        Path relativePath;
        try {
            relativePath = projectRoot.relativize(file.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            relativePath = file.getFileName();
        }
        return relativePath.toString().replace("/", "_").replace("\\", "_");
    }

    private void pruneOldVersions(Path file) {
        List<BackupEntry> backups = listBackups(file);

        if (backups.size() > MAX_VERSIONS_PER_FILE) {
            for (int i = MAX_VERSIONS_PER_FILE; i < backups.size(); i++) {
                try {
                    Files.deleteIfExists(backups.get(i).backupPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    public Path getShadowDir() {
        return shadowDir;
    }

    public record BackupEntry(
        Path originalPath,
        Path backupPath,
        Instant timestamp,
        long sizeBytes
    ) {
        public long ageSeconds() {
            return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
        }
    }
}
