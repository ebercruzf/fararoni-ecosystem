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

import dev.fararoni.core.service.FilesystemService;
import dev.fararoni.core.service.WriteResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SafetyLayer {
    private static final int MAX_OPERATION_HISTORY = 50;

    private final Path projectRoot;
    private final FilesystemService filesystemService;
    private final AtomicFileWriter atomicWriter;
    private final ShadowBackupManager backupManager;

    private final Deque<Operation> operationHistory;

    public SafetyLayer(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot no puede ser null")
            .toAbsolutePath().normalize();
        this.filesystemService = new FilesystemService(this.projectRoot);
        this.atomicWriter = new AtomicFileWriter();
        this.backupManager = new ShadowBackupManager(this.projectRoot);
        this.operationHistory = new ArrayDeque<>();
    }

    public SafetyLayer(Path projectRoot,
                       FilesystemService filesystemService,
                       AtomicFileWriter atomicWriter,
                       ShadowBackupManager backupManager) {
        this.projectRoot = Objects.requireNonNull(projectRoot).toAbsolutePath().normalize();
        this.filesystemService = Objects.requireNonNull(filesystemService);
        this.atomicWriter = Objects.requireNonNull(atomicWriter);
        this.backupManager = Objects.requireNonNull(backupManager);
        this.operationHistory = new ArrayDeque<>();
    }

    public WriteResult safeWrite(Path target, String content) {
        Objects.requireNonNull(target, "target no puede ser null");
        Objects.requireNonNull(content, "content no puede ser null");

        Path absoluteTarget = resolvePath(target);

        if (!filesystemService.isPathSafe(absoluteTarget.toString())) {
            return WriteResult.error("Ruta no permitida: " + target);
        }

        try {
            ShadowBackupManager.BackupEntry backup = null;
            boolean existed = Files.exists(absoluteTarget);
            if (existed) {
                backup = backupManager.createBackup(absoluteTarget);
            }

            atomicWriter.writeAtomic(absoluteTarget, content);

            Operation op = new Operation(
                OperationType.WRITE,
                absoluteTarget,
                null,
                backup,
                existed
            );
            pushOperation(op);

            return WriteResult.success(absoluteTarget, content.length());
        } catch (SafetyException e) {
            return WriteResult.error(e.getMessage());
        }
    }

    public WriteResult safeWrite(String relativePath, String content) {
        return safeWrite(Path.of(relativePath), content);
    }

    public void safeMove(Path source, Path destination) {
        Objects.requireNonNull(source, "source no puede ser null");
        Objects.requireNonNull(destination, "destination no puede ser null");

        Path absoluteSource = resolvePath(source);
        Path absoluteDest = resolvePath(destination);

        if (!Files.exists(absoluteSource)) {
            throw new SafetyException(
                "Archivo origen no existe: " + source,
                SafetyException.SafetyErrorCode.FILE_NOT_FOUND
            );
        }

        if (!filesystemService.isPathSafe(absoluteDest.toString())) {
            throw new SafetyException(
                "Destino no permitido: " + destination,
                SafetyException.SafetyErrorCode.PATH_NOT_ALLOWED
            );
        }

        try {
            ShadowBackupManager.BackupEntry sourceBackup = backupManager.createBackup(absoluteSource);

            ShadowBackupManager.BackupEntry destBackup = null;
            boolean destExisted = Files.exists(absoluteDest);
            if (destExisted) {
                destBackup = backupManager.createBackup(absoluteDest);
            }

            Path parent = absoluteDest.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.move(absoluteSource, absoluteDest, StandardCopyOption.REPLACE_EXISTING);

            Operation op = new Operation(
                OperationType.MOVE,
                absoluteSource,
                absoluteDest,
                sourceBackup,
                true
            );
            pushOperation(op);
        } catch (IOException e) {
            throw new SafetyException(
                "Error moviendo archivo: " + e.getMessage(),
                SafetyException.SafetyErrorCode.ATOMIC_WRITE_FAILED,
                e
            );
        }
    }

    public void safeDelete(Path target) {
        Objects.requireNonNull(target, "target no puede ser null");

        Path absoluteTarget = resolvePath(target);

        if (!Files.exists(absoluteTarget)) {
            return;
        }

        try {
            ShadowBackupManager.BackupEntry backup = backupManager.createBackup(absoluteTarget);

            Files.delete(absoluteTarget);

            Operation op = new Operation(
                OperationType.DELETE,
                absoluteTarget,
                null,
                backup,
                true
            );
            pushOperation(op);
        } catch (IOException e) {
            throw new SafetyException(
                "Error eliminando archivo: " + target,
                SafetyException.SafetyErrorCode.ATOMIC_WRITE_FAILED,
                e
            );
        }
    }

    public boolean rollbackLast() {
        if (operationHistory.isEmpty()) {
            return false;
        }

        Operation op = operationHistory.pop();
        return rollbackOperation(op);
    }

    public int rollbackLast(int count) {
        int rolledBack = 0;
        for (int i = 0; i < count && !operationHistory.isEmpty(); i++) {
            if (rollbackLast()) {
                rolledBack++;
            }
        }
        return rolledBack;
    }

    public int rollbackAll() {
        int count = 0;
        while (rollbackLast()) {
            count++;
        }
        return count;
    }

    private boolean rollbackOperation(Operation op) {
        if (op.backup() == null) {
            return false;
        }

        try {
            switch (op.type()) {
                case WRITE -> {
                    if (op.existed()) {
                        backupManager.restoreFrom(op.source(), op.backup());
                    } else {
                        Files.deleteIfExists(op.source());
                    }
                }
                case MOVE -> {
                    backupManager.restoreFrom(op.source(), op.backup());
                }
                case DELETE -> {
                    backupManager.restoreFrom(op.source(), op.backup());
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<ShadowBackupManager.BackupEntry> listBackups() {
        return backupManager.listAllBackups();
    }

    public List<ShadowBackupManager.BackupEntry> listBackups(Path file) {
        return backupManager.listBackups(resolvePath(file));
    }

    public Optional<ShadowBackupManager.BackupEntry> getLatestBackup(Path file) {
        return backupManager.getLatestBackup(resolvePath(file));
    }

    public boolean restore(Path file) {
        return backupManager.restore(resolvePath(file));
    }

    public int getPendingOperationsCount() {
        return operationHistory.size();
    }

    public void clearOperationHistory() {
        operationHistory.clear();
    }

    private Path resolvePath(Path path) {
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return projectRoot.resolve(path).normalize();
    }

    private void pushOperation(Operation op) {
        operationHistory.push(op);

        while (operationHistory.size() > MAX_OPERATION_HISTORY) {
            operationHistory.removeLast();
        }
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public ShadowBackupManager getBackupManager() {
        return backupManager;
    }

    public enum OperationType {
        WRITE,
        MOVE,
        DELETE
    }

    public record Operation(
        OperationType type,
        Path source,
        Path destination,
        ShadowBackupManager.BackupEntry backup,
        boolean existed
    ) {}
}
