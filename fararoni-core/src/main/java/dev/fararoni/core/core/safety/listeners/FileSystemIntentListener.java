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
package dev.fararoni.core.core.safety.listeners;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.mission.engine.AgentTemplateManager;
import dev.fararoni.core.core.mission.events.ArtifactCertifiedEvent;
import dev.fararoni.core.core.mission.events.FileRestoreIntentEvent;
import dev.fararoni.core.core.mission.events.FileWriteErrorEvent;
import dev.fararoni.core.core.mission.events.FileWriteIntentEvent;
import dev.fararoni.core.core.mission.events.FileWriteResultEvent;
import dev.fararoni.core.core.mission.model.AgentTemplate;
import dev.fararoni.core.core.safety.IroncladGuard;
import dev.fararoni.core.core.safety.SafetyException;
import dev.fararoni.core.core.safety.SafetyLayer;
import dev.fararoni.core.core.safety.audit.FileIntegrityService;
import dev.fararoni.core.core.safety.overseer.OverseerValidator;
import dev.fararoni.core.core.skills.validators.AstValidatorService;
import dev.fararoni.core.service.WriteResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class FileSystemIntentListener {
    private static final Logger LOG = Logger.getLogger(FileSystemIntentListener.class.getName());

    private static final String DEFAULT_REAL_PACKAGE = "dev.fararoni.core";

    private final SovereignEventBus bus;
    private final IroncladGuard ironcladGuard;
    private final SafetyLayer safetyLayer;
    private final AstValidatorService astValidator;
    private final Path workingDirectory;

    private final String realBasePackage;

    private final FileIntegrityService integrityService;

    private final boolean auditEnabled;

    private final AgentTemplateManager agentTemplateManager;

    private final OverseerValidator overseerValidator;

    private final Map<String, Integer> lastReadHashes = new ConcurrentHashMap<>();

    private final Map<String, Object> fileLocks = new ConcurrentHashMap<>();

    public FileSystemIntentListener(
            SovereignEventBus bus,
            IroncladGuard ironcladGuard,
            SafetyLayer safetyLayer,
            AstValidatorService astValidator,
            Path workingDirectory,
            String realBasePackage,
            boolean auditEnabled,
            AgentTemplateManager agentTemplateManager) {
        this.bus = bus;
        this.ironcladGuard = ironcladGuard;
        this.safetyLayer = safetyLayer;
        this.astValidator = astValidator;
        this.workingDirectory = workingDirectory != null
            ? workingDirectory
            : Paths.get(System.getProperty("user.dir"));
        this.realBasePackage = realBasePackage != null
            ? realBasePackage
            : DEFAULT_REAL_PACKAGE;
        this.integrityService = new FileIntegrityService();
        this.auditEnabled = auditEnabled;
        this.agentTemplateManager = agentTemplateManager;
        this.overseerValidator = new OverseerValidator();
    }

    public FileSystemIntentListener(
            SovereignEventBus bus,
            IroncladGuard ironcladGuard,
            SafetyLayer safetyLayer,
            AstValidatorService astValidator,
            Path workingDirectory,
            String realBasePackage,
            boolean auditEnabled) {
        this(bus, ironcladGuard, safetyLayer, astValidator, workingDirectory,
             realBasePackage, auditEnabled, null);
    }

    public FileSystemIntentListener(
            SovereignEventBus bus,
            IroncladGuard ironcladGuard,
            SafetyLayer safetyLayer,
            AstValidatorService astValidator,
            Path workingDirectory,
            String realBasePackage) {
        this(bus, ironcladGuard, safetyLayer, astValidator, workingDirectory, realBasePackage, true);
    }

    public FileSystemIntentListener(
            SovereignEventBus bus,
            IroncladGuard ironcladGuard,
            SafetyLayer safetyLayer,
            Path workingDirectory) {
        this(bus, ironcladGuard, safetyLayer, new AstValidatorService(),
             workingDirectory, null, true);
    }

    public void start() {
        bus.subscribe(
            FileWriteIntentEvent.TOPIC,
            FileWriteIntentEvent.class,
            this::onFileWriteIntent
        );

        bus.subscribe(
            FileRestoreIntentEvent.TOPIC,
            FileRestoreIntentEvent.class,
            this::onFileRestoreIntent
        );

        LOG.info("FileSystemIntentListener suscrito a: " +
                 FileWriteIntentEvent.TOPIC + ", " + FileRestoreIntentEvent.TOPIC);
        System.out.println("[SAFETY] FileSystemIntentListener activo (Write + Restore)");
    }

    private void onFileWriteIntent(SovereignEnvelope<FileWriteIntentEvent> envelope) {
        FileWriteIntentEvent event = envelope.payload();

        LOG.info(() -> "[SAFETY] Interceptada intención de escritura: " + event.targetPath());

        String finalPath = interceptExamplePath(event.targetPath());
        String finalContent = interceptExamplePackage(event.content());
        Path absolutePath = workingDirectory.resolve(finalPath).normalize();

        if (!absolutePath.startsWith(workingDirectory)) {
            publishError(event, FileWriteErrorEvent.ERR_PATH_FORBIDDEN,
                "Intento de escritura fuera del workspace: " + finalPath, false);
            return;
        }

        Object fileLock = fileLocks.computeIfAbsent(absolutePath.toString(), k -> new Object());

        synchronized (fileLock) {
            try {
                String existingContent = "";
                if (Files.exists(absolutePath)) {
                    existingContent = Files.readString(absolutePath);
                    String concurrencyError = validateConcurrency(
                        absolutePath.toString(), existingContent);
                    if (concurrencyError != null) {
                        publishError(event, FileWriteErrorEvent.ERR_CONCURRENCY,
                            concurrencyError, true);
                        return;
                    }
                }

                try {
                    ironcladGuard.validate(
                        finalPath,
                        existingContent,
                        finalContent,
                        event.forceReason()
                    );
                } catch (SafetyException e) {
                    String errorCode = e.getMessage().contains("truncado")
                        ? FileWriteErrorEvent.ERR_IRONCLAD_TRUNCATION
                        : FileWriteErrorEvent.ERR_IRONCLAD_LAZY;
                    publishError(event, errorCode, e.getMessage(), true);
                    return;
                }

                if (agentTemplateManager != null && event.agentId() != null) {
                    AgentTemplate template = agentTemplateManager.getTemplate(event.agentId());
                    if (template != null) {
                        AgentTemplate.ValidationPolicy policy = template.validationPolicy();
                        if (policy != null && policy.enabled()) {
                            OverseerValidator.ValidationResult validationResult =
                                overseerValidator.validateFile(finalPath, finalContent, policy);

                            if (!validationResult.isValid()) {
                                String violations = validationResult.violationsSummary();
                                LOG.warning(() -> "[B] Overseer RECHAZÓ archivo: " +
                                    finalPath + " | " + violations);
                                System.out.println("[OVERSEER] [ERROR] Rechazado: " +
                                    absolutePath.getFileName() + " | " + violations);

                                publishError(event, FileWriteErrorEvent.ERR_OVERSEER_POLICY,
                                    "Overseer rechazó: " + violations, true);
                                return;
                            }
                            LOG.fine(() -> "[B] Overseer OK: " + finalPath);
                        }
                    }
                }

                String syntaxError = astValidator.validate(finalPath, finalContent);
                if (syntaxError != null) {
                    publishError(event, FileWriteErrorEvent.ERR_AST_SYNTAX,
                        "Error de sintaxis: " + syntaxError, true);
                    return;
                }

                boolean hadBackup = Files.exists(absolutePath);
                WriteResult result = safetyLayer.safeWrite(absolutePath, finalContent);

                if (!result.isSuccess()) {
                    publishError(event, FileWriteErrorEvent.ERR_IO,
                        "Error de escritura: " + result.errorMessage(), true);
                    return;
                }

                lastReadHashes.put(absolutePath.toString(), finalContent.hashCode());

                LOG.info(() -> "[SAFETY] Archivo escrito: " + absolutePath);

                FileWriteResultEvent resultEvent = FileWriteResultEvent.from(
                    event,
                    absolutePath,
                    finalContent.length(),
                    hadBackup
                );

                bus.publish(
                    FileWriteResultEvent.TOPIC,
                    SovereignEnvelope.create("system", resultEvent)
                );

                if (auditEnabled) {
                    publishArtifactCertification(resultEvent, finalContent);
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[SAFETY] Error fatal procesando: " + event.targetPath(), e);
                publishError(event, FileWriteErrorEvent.ERR_FATAL,
                    "Error interno: " + e.getMessage(), false);
            }
        }
    }

    private void publishError(
            FileWriteIntentEvent intent,
            String errorCode,
            String message,
            boolean recoverable) {
        LOG.warning(() -> "[SAFETY] " + errorCode + ": " + message);
        System.out.println("[SAFETY] [ERROR] " + errorCode + ": " +
            (message.length() > 80 ? message.substring(0, 80) + "..." : message));

        FileWriteErrorEvent errorEvent = FileWriteErrorEvent.from(
            intent, errorCode, message, recoverable);

        bus.publish(
            FileWriteErrorEvent.TOPIC,
            SovereignEnvelope.create("system", errorEvent)
        );
    }

    private String interceptExamplePath(String path) {
        if (path == null) return path;

        if (path.contains("com/example/") || path.contains("com\\example\\")) {
            String realPath = realBasePackage.replace(".", "/");
            String corrected = path
                .replace("com/example/", realPath + "/")
                .replace("com\\example\\", realPath + "/");
            LOG.info(() -> "[ANTI-EXAMPLE] Path corregido: " + path + " → " + corrected);
            return corrected;
        }
        return path;
    }

    private String interceptExamplePackage(String content) {
        if (content == null) return content;

        if (content.contains("package com.example")) {
            String corrected = content.replace(
                "package com.example",
                "package " + realBasePackage
            );
            LOG.info(() -> "[ANTI-EXAMPLE] Package corregido en contenido");
            return corrected;
        }
        return content;
    }

    private String validateConcurrency(String path, String currentDiskContent) {
        Integer expectedHash = lastReadHashes.get(path);
        if (expectedHash != null) {
            int currentHash = currentDiskContent.hashCode();
            if (currentHash != expectedHash) {
                return "El archivo fue modificado externamente desde la última lectura. " +
                       "Re-lee el archivo antes de modificarlo.";
            }
        }
        return null;
    }

    public void registerReadHash(String path, String content) {
        if (path != null && content != null) {
            lastReadHashes.put(path, content.hashCode());
        }
    }

    public FileSystemIntentListener withRealPackage(String packageName) {
        return new FileSystemIntentListener(
            this.bus,
            this.ironcladGuard,
            this.safetyLayer,
            this.astValidator,
            this.workingDirectory,
            packageName,
            this.auditEnabled,
            this.agentTemplateManager
        );
    }

    public FileSystemIntentListener withAudit(boolean enabled) {
        return new FileSystemIntentListener(
            this.bus,
            this.ironcladGuard,
            this.safetyLayer,
            this.astValidator,
            this.workingDirectory,
            this.realBasePackage,
            enabled,
            this.agentTemplateManager
        );
    }

    public FileSystemIntentListener withOverseer(AgentTemplateManager manager) {
        return new FileSystemIntentListener(
            this.bus,
            this.ironcladGuard,
            this.safetyLayer,
            this.astValidator,
            this.workingDirectory,
            this.realBasePackage,
            this.auditEnabled,
            manager
        );
    }

    public boolean isAuditEnabled() {
        return this.auditEnabled;
    }

    public boolean isOverseerEnabled() {
        return this.agentTemplateManager != null;
    }

    private void publishArtifactCertification(FileWriteResultEvent resultEvent, String content) {
        try {
            String sha256 = integrityService.calculateHash(content);

            ArtifactCertifiedEvent certEvent = ArtifactCertifiedEvent.from(resultEvent, sha256);

            bus.publish(
                ArtifactCertifiedEvent.TOPIC,
                SovereignEnvelope.create("integrity-service", certEvent)
            );

            LOG.info(() -> FileIntegrityService.formatAuditEntry(
                resultEvent.writtenPath(), sha256));

            System.out.println("[AUDIT] Certificado: " +
                resultEvent.writtenPath().getFileName() +
                " | SHA-256: " + sha256.substring(0, 8) + "...");
        } catch (Exception e) {
            LOG.warning("[AUDIT] Fallo en certificación (non-fatal): " + e.getMessage());
        }
    }

    private void onFileRestoreIntent(SovereignEnvelope<FileRestoreIntentEvent> envelope) {
        FileRestoreIntentEvent event = envelope.payload();

        LOG.warning(() -> "[SAFETY] Ejecutando SAGA Rollback: " + event.targetPath());

        Path absolutePath = workingDirectory.resolve(event.targetPath()).normalize();

        if (!absolutePath.startsWith(workingDirectory)) {
            LOG.severe("[SAFETY] Intento de restaurar fuera del workspace: " + event.targetPath());
            return;
        }

        Object fileLock = fileLocks.computeIfAbsent(absolutePath.toString(), k -> new Object());

        synchronized (fileLock) {
            try {
                boolean restored = safetyLayer.restore(absolutePath);

                if (restored) {
                    if (Files.exists(absolutePath)) {
                        String newContent = Files.readString(absolutePath);
                        lastReadHashes.put(absolutePath.toString(), newContent.hashCode());
                    } else {
                        lastReadHashes.remove(absolutePath.toString());
                    }

                    LOG.info(() -> "[SAFETY] SAGA Rollback exitoso: " + absolutePath.getFileName());
                } else {
                    if (Files.deleteIfExists(absolutePath)) {
                        lastReadHashes.remove(absolutePath.toString());
                        LOG.info(() -> "[SAFETY] Archivo nuevo eliminado en rollback: " + absolutePath.getFileName());
                    } else {
                        LOG.warning(() -> "[SAFETY] No se encontro backup ni archivo: " + absolutePath);
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[SAFETY] SAGA Rollback falló: " + event.targetPath(), e);
                System.out.println("[SAFETY] [ERROR] Error en rollback: " + e.getMessage());
            }
        }
    }
}
