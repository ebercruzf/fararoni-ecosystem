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
package dev.fararoni.core.agent;

import dev.fararoni.bus.agent.api.saga.CompensationInstruction;
import dev.fararoni.core.core.hooks.PostWriteHook;
import dev.fararoni.core.core.hooks.PostWriteHook.HookResult;
import dev.fararoni.core.core.saga.SagaOrchestrator;
import dev.fararoni.core.core.hooks.TestOnWriteHook;
import dev.fararoni.core.enterprise.git.GitService;
import dev.fararoni.core.service.FilesystemService;
import dev.fararoni.core.service.WriteResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class ActionParser {
    private static final Logger LOG = Logger.getLogger(ActionParser.class.getName());

    private static final String FILE_START = ">>>FILE:";
    private static final String FILE_END = "<<<END_FILE";
    private static final String DIR_START = ">>>MKDIR:";
    private static final String DIR_END = "<<<END_MKDIR";

    private static final String PATCH_START = ">>>PATCH:";
    private static final String PATCH_END = "<<<END_PATCH";
    private static final String SEARCH_DELIM = "<<<SEARCH>>>";
    private static final String REPLACE_DELIM = "<<<REPLACE>>>";

    private final FilesystemService filesystemService;
    private final Consumer<String> outputCallback;
    private final GitService gitService;

    private final SagaOrchestrator sagaOrchestrator;
    private final List<PostWriteHook> postWriteHooks;

    private boolean isCapturingFile = false;
    private String currentFilename = null;
    private final StringBuilder fileBuffer = new StringBuilder();

    private boolean isCapturingPatch = false;
    private String patchFilename = null;
    private final StringBuilder patchBuffer = new StringBuilder();
    private PatchSection currentPatchSection = PatchSection.NONE;

    private enum PatchSection { NONE, SEARCH, REPLACE }

    private final List<ActionResult> results = new ArrayList<>();

    public ActionParser(FilesystemService filesystemService, Consumer<String> outputCallback) {
        this(filesystemService, outputCallback, null, null, null);
    }

    public ActionParser(FilesystemService filesystemService, Consumer<String> outputCallback, GitService gitService) {
        this(filesystemService, outputCallback, gitService, null, null);
    }

    public ActionParser(FilesystemService filesystemService,
                        Consumer<String> outputCallback,
                        GitService gitService,
                        SagaOrchestrator sagaOrchestrator,
                        List<PostWriteHook> postWriteHooks) {
        this.filesystemService = filesystemService;
        this.outputCallback = outputCallback;
        this.gitService = gitService;
        this.sagaOrchestrator = sagaOrchestrator;
        this.postWriteHooks = postWriteHooks != null ? postWriteHooks : List.of();
    }

    public void processLine(String line) {
        String trimmed = line.trim();

        if (isFileStart(trimmed)) {
            String potentialFilename = extractPath(trimmed);

            if (!isValidFilename(potentialFilename)) {
                String extractedFilename = extractFilenameFromDescription(potentialFilename);
                if (extractedFilename != null) {
                    potentialFilename = extractedFilename;
                } else {
                    outputCallback.accept(line);
                    return;
                }
            }

            isCapturingFile = true;
            currentFilename = potentialFilename;
            fileBuffer.setLength(0);
            return;
        }

        if (trimmed.equals(FILE_END) && isCapturingFile) {
            String content = fileBuffer.toString();
            if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }

            String sagaId = (sagaOrchestrator != null) ? sagaOrchestrator.beginSaga() : null;
            LOG.fine(() -> "[ActionParser] Started saga: " + sagaId);

            boolean isNewFile = !filesystemService.fileExists(currentFilename);

            WriteResult result = filesystemService.writeFile(currentFilename, content);

            if (result.isSuccess()) {
                java.nio.file.Path writtenPath = result.path();

                if (sagaOrchestrator != null && sagaId != null) {
                    CompensationInstruction undo = CompensationInstruction.of(
                        "FileSystemSkill",
                        "delete",
                        Map.of("path", writtenPath.toAbsolutePath().toString())
                    );
                    sagaOrchestrator.registerCompensation(sagaId, undo, "Undo write: " + currentFilename);
                    LOG.fine(() -> "[ActionParser] Registered compensation for: " + currentFilename);
                }

                boolean rollbackTriggered = false;
                for (PostWriteHook hook : postWriteHooks) {
                    HookResult hookResult = hook.onFileWritten(writtenPath, sagaId);

                    if (hookResult.shouldRollback()) {
                        LOG.warning(() -> "[ActionParser] Rollback triggered by " + hook.getName() + ": " + hookResult.message());
                        outputCallback.accept("  [" + hook.getName() + "] " + hookResult.message());

                        if (isNewFile) {
                            LOG.info(() -> "[SAFE-CREATION] Archivo nuevo preservado para iteracion: " + currentFilename);
                            outputCallback.accept("  [SAFE-CREATION] Archivo nuevo preservado para correccion: " + currentFilename);
                            outputCallback.accept("  [~] El archivo se mantiene para permitir ajustes del modelo...");

                            results.add(new ActionResult(ActionResult.Type.FILE_CREATED,
                                currentFilename, false, "Safe-Creation: " + hookResult.message()));
                            rollbackTriggered = true;
                            break;
                        }

                        outputCallback.accept("  [~] Ejecutando rollback automatico...");

                        try {
                            if (java.nio.file.Files.deleteIfExists(writtenPath)) {
                                outputCallback.accept("  [OK] Archivo eliminado (rollback exitoso): " + currentFilename);
                                LOG.info(() -> "[ActionParser] Rollback successful - file deleted: " + writtenPath);
                            } else {
                                outputCallback.accept("  [WARN] Archivo ya no existia: " + currentFilename);
                            }
                        } catch (java.io.IOException e) {
                            outputCallback.accept("  [ERROR] Error en rollback: " + e.getMessage());
                            LOG.warning(() -> "[ActionParser] Rollback failed: " + e.getMessage());
                        }

                        if (sagaOrchestrator != null && sagaId != null) {
                            sagaOrchestrator.markAsCompensated(sagaId);
                        }

                        results.add(new ActionResult(ActionResult.Type.FILE_CREATED,
                            currentFilename, false, "Rollback: " + hookResult.message()));
                        rollbackTriggered = true;
                        break;
                    } else if (hookResult.message() != null) {
                        outputCallback.accept("  [" + hook.getName() + "] " + hookResult.message());
                    }
                }

                if (!rollbackTriggered) {
                    if (sagaOrchestrator != null && sagaId != null) {
                        sagaOrchestrator.commitSaga(sagaId);
                        LOG.fine(() -> "[ActionParser] Saga committed: " + sagaId);
                    }

                    outputCallback.accept("Archivo guardado: " + writtenPath.toAbsolutePath());
                    results.add(new ActionResult(ActionResult.Type.FILE_CREATED, currentFilename, true, null));

                    autoCommitIfEnabled(currentFilename, "FILE_CREATED");
                }
            } else {
                if (sagaOrchestrator != null && sagaId != null) {
                    sagaOrchestrator.cancelSaga(sagaId);
                }
                outputCallback.accept("Error guardando " + currentFilename + ": " + result.errorMessage());
                results.add(new ActionResult(ActionResult.Type.FILE_CREATED, currentFilename, false, result.errorMessage()));
            }

            isCapturingFile = false;
            currentFilename = null;
            return;
        }

        if (isDirStart(trimmed)) {
            String dirPath = extractDirPath(trimmed);
            WriteResult result = filesystemService.createDirectory(dirPath);
            if (result.isSuccess()) {
                outputCallback.accept("Directorio creado: " + dirPath);
                results.add(new ActionResult(ActionResult.Type.DIR_CREATED, dirPath, true, null));
            } else {
                outputCallback.accept("Error creando directorio: " + result.errorMessage());
                results.add(new ActionResult(ActionResult.Type.DIR_CREATED, dirPath, false, result.errorMessage()));
            }
            return;
        }

        if (trimmed.equals(DIR_END)) {
            return;
        }

        if (isPatchStart(trimmed)) {
            patchFilename = extractPatchPath(trimmed);
            isCapturingPatch = true;
            patchBuffer.setLength(0);
            currentPatchSection = PatchSection.NONE;
            LOG.fine(() -> "[ActionParser] Starting PATCH capture for: " + patchFilename);
            return;
        }

        if (isCapturingPatch) {
            if (trimmed.equals(SEARCH_DELIM)) {
                currentPatchSection = PatchSection.SEARCH;
                patchBuffer.append("<<<SEARCH>>>\n");
                return;
            }
            if (trimmed.equals(REPLACE_DELIM)) {
                currentPatchSection = PatchSection.REPLACE;
                patchBuffer.append("<<<REPLACE>>>\n");
                return;
            }
            if (trimmed.equals(PATCH_END)) {
                executePatch();
                isCapturingPatch = false;
                patchFilename = null;
                patchBuffer.setLength(0);
                currentPatchSection = PatchSection.NONE;
                return;
            }
            patchBuffer.append(line).append("\n");
            return;
        }

        if (isCapturingFile) {
            if (fileBuffer.length() > 0) {
                fileBuffer.append("\n");
            }
            fileBuffer.append(line);
        } else {
            outputCallback.accept(line);
        }
    }

    public void flush() {
        if (isCapturingFile && currentFilename != null && fileBuffer.length() > 0) {
            String content = fileBuffer.toString();
            if (content.endsWith("\n")) {
                content = content.substring(0, content.length() - 1);
            }

            WriteResult result = filesystemService.writeFile(currentFilename, content);
            if (result.isSuccess()) {
                outputCallback.accept("Archivo guardado (auto-cerrado): " + result.path().toAbsolutePath());
                results.add(new ActionResult(ActionResult.Type.FILE_CREATED, currentFilename, true, "auto-closed"));
            } else {
                outputCallback.accept("Error guardando " + currentFilename + ": " + result.errorMessage());
                results.add(new ActionResult(ActionResult.Type.FILE_CREATED, currentFilename, false, result.errorMessage()));
            }

            isCapturingFile = false;
            currentFilename = null;
            fileBuffer.setLength(0);
        }
    }

    public void reset() {
        isCapturingFile = false;
        currentFilename = null;
        fileBuffer.setLength(0);
        isCapturingPatch = false;
        patchFilename = null;
        patchBuffer.setLength(0);
        currentPatchSection = PatchSection.NONE;
        results.clear();
    }

    public List<ActionResult> getResults() {
        return new ArrayList<>(results);
    }

    public boolean hasFileOperations() {
        return results.stream().anyMatch(r -> r.success());
    }

    public boolean isCapturing() {
        return isCapturingFile || isCapturingPatch;
    }

    private boolean isFileStart(String line) {
        if (line.startsWith(FILE_START)) {
            return true;
        }
        return line.startsWith(">>>") && line.toUpperCase().contains("FILE:");
    }

    private boolean isDirStart(String line) {
        if (line.startsWith(DIR_START)) {
            return true;
        }
        return line.startsWith(">>>") && line.toUpperCase().contains("MKDIR:");
    }

    private String extractPath(String line) {
        if (line.startsWith(FILE_START)) {
            return line.substring(FILE_START.length()).trim();
        }
        int idx = line.toUpperCase().indexOf("FILE:");
        if (idx >= 0) {
            return line.substring(idx + 5).trim();
        }
        return line.trim();
    }

    private String extractDirPath(String line) {
        if (line.startsWith(DIR_START)) {
            return line.substring(DIR_START.length()).trim();
        }
        int idx = line.toUpperCase().indexOf("MKDIR:");
        if (idx >= 0) {
            return line.substring(idx + 6).trim();
        }
        return line.trim();
    }

    private boolean isPatchStart(String line) {
        if (line.startsWith(PATCH_START)) {
            return true;
        }
        return line.startsWith(">>>") && line.toUpperCase().contains("PATCH:");
    }

    private String extractPatchPath(String line) {
        if (line.startsWith(PATCH_START)) {
            return line.substring(PATCH_START.length()).trim();
        }
        int idx = line.toUpperCase().indexOf("PATCH:");
        if (idx >= 0) {
            return line.substring(idx + 6).trim();
        }
        return line.trim();
    }

    private void executePatch() {
        if (patchFilename == null || patchBuffer.length() == 0) {
            outputCallback.accept("[ERROR] Patch incompleto - falta nombre de archivo o contenido");
            results.add(new ActionResult(ActionResult.Type.FILE_PATCHED, patchFilename, false, "Incomplete patch"));
            return;
        }

        String patchContent = patchBuffer.toString();
        LOG.fine(() -> "[ActionParser] Executing PATCH on: " + patchFilename);
        LOG.fine(() -> "[ActionParser] Patch content:\n" + patchContent);

        int searchIdx = patchContent.indexOf("<<<SEARCH>>>");
        int replaceIdx = patchContent.indexOf("<<<REPLACE>>>");

        if (searchIdx == -1 || replaceIdx == -1) {
            outputCallback.accept("[ERROR] Patch malformado - falta <<<SEARCH>>> o <<<REPLACE>>>");
            results.add(new ActionResult(ActionResult.Type.FILE_PATCHED, patchFilename, false, "Missing delimiters"));
            return;
        }

        String searchText = patchContent.substring(searchIdx + "<<<SEARCH>>>\n".length(), replaceIdx).trim();
        String replaceText = patchContent.substring(replaceIdx + "<<<REPLACE>>>\n".length()).trim();

        LOG.fine(() -> "[ActionParser] SEARCH: " + searchText);
        LOG.fine(() -> "[ActionParser] REPLACE: " + replaceText);

        java.nio.file.Path filePath;
        java.nio.file.Path inputPath = java.nio.file.Path.of(patchFilename);
        if (inputPath.isAbsolute()) {
            filePath = inputPath.normalize();
        } else {
            filePath = filesystemService.getWorkingDirectory().resolve(inputPath).normalize();
        }

        if (!java.nio.file.Files.exists(filePath)) {
            outputCallback.accept("[ERROR] Archivo no existe: " + patchFilename);
            results.add(new ActionResult(ActionResult.Type.FILE_PATCHED, patchFilename, false, "File not found"));
            return;
        }

        try {
            String originalContent = java.nio.file.Files.readString(filePath);

            if (!originalContent.contains(searchText)) {
                outputCallback.accept("[WARN] Texto SEARCH no encontrado en " + patchFilename);
                outputCallback.accept("[WARN] Búsqueda: \"" + searchText.substring(0, Math.min(50, searchText.length())) + "...\"");
                results.add(new ActionResult(ActionResult.Type.FILE_PATCHED, patchFilename, false, "Search text not found"));
                return;
            }

            String newContent = originalContent.replace(searchText, replaceText);

            WriteResult writeResult = filesystemService.writeFile(patchFilename, newContent);

            if (writeResult.isSuccess()) {
                outputCallback.accept("[OK] Patch aplicado: " + patchFilename);
                results.add(new ActionResult(ActionResult.Type.FILE_PATCHED, patchFilename, true, null));
                autoCommitIfEnabled(patchFilename, "FILE_PATCHED");
            } else {
                outputCallback.accept("[ERROR] Error escribiendo patch: " + writeResult.errorMessage());
                results.add(new ActionResult(ActionResult.Type.FILE_PATCHED, patchFilename, false, writeResult.errorMessage()));
            }
        } catch (java.io.IOException e) {
            outputCallback.accept("[ERROR] Error leyendo archivo: " + e.getMessage());
            results.add(new ActionResult(ActionResult.Type.FILE_PATCHED, patchFilename, false, e.getMessage()));
        }
    }

    private boolean isValidFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }

        if (filename.length() > 100) {
            return false;
        }

        if (filename.split("\\s+").length > 3) {
            return false;
        }

        if (filename.contains("..") || filename.startsWith("/") || filename.contains("\\")) {
            return true;
        }

        String name = filename.contains("/") ?
            filename.substring(filename.lastIndexOf("/") + 1) : filename;

        if (!name.contains(".")) {
            return false;
        }

        String lower = filename.toLowerCase();
        if (lower.startsWith("escribe") || lower.startsWith("crea") ||
            lower.startsWith("genera") || lower.startsWith("una clase") ||
            lower.contains("con getters") || lower.contains("con setters") ||
            lower.contains("llamada") || lower.contains("llamado")) {
            return false;
        }

        return true;
    }

    private String extractFilenameFromDescription(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }

        java.util.regex.Pattern quotedPattern = java.util.regex.Pattern.compile("['\"]([A-Z][a-zA-Z0-9_]+)['\"]");
        java.util.regex.Matcher quotedMatcher = quotedPattern.matcher(description);
        if (quotedMatcher.find()) {
            String name = quotedMatcher.group(1);
            String ext = guessExtension(description);
            return name + ext;
        }

        java.util.regex.Pattern filePattern = java.util.regex.Pattern.compile("\\b([A-Za-z][A-Za-z0-9_]*\\.[a-z]{2,4})\\b");
        java.util.regex.Matcher fileMatcher = filePattern.matcher(description);
        if (fileMatcher.find()) {
            return fileMatcher.group(1);
        }

        java.util.regex.Pattern clasePattern = java.util.regex.Pattern.compile("(?:clase|class)\\s+([A-Z][a-zA-Z0-9_]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher claseMatcher = clasePattern.matcher(description);
        if (claseMatcher.find()) {
            String name = claseMatcher.group(1);
            return name + ".java";
        }

        java.util.regex.Pattern llamadaPattern = java.util.regex.Pattern.compile("(?:llamada|llamado)\\s+([A-Z][a-zA-Z0-9_]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher llamadaMatcher = llamadaPattern.matcher(description);
        if (llamadaMatcher.find()) {
            String name = llamadaMatcher.group(1);
            String ext = guessExtension(description);
            return name + ext;
        }

        return null;
    }

    private String guessExtension(String description) {
        String lower = description.toLowerCase();
        if (lower.contains("java") || lower.contains("clase") || lower.contains("class")) {
            return ".java";
        }
        if (lower.contains("python") || lower.contains(".py")) {
            return ".py";
        }
        if (lower.contains("javascript") || lower.contains(".js")) {
            return ".js";
        }
        if (lower.contains("typescript") || lower.contains(".ts")) {
            return ".ts";
        }
        if (lower.contains("json")) {
            return ".json";
        }
        if (lower.contains("xml")) {
            return ".xml";
        }
        if (lower.contains("html")) {
            return ".html";
        }
        return ".java";
    }

    private void autoCommitIfEnabled(String path, String action) {
        if (gitService == null || !gitService.isAutoCommitEnabled()) {
            return;
        }

        if (!gitService.isGitRepo()) {
            return;
        }

        var commitResult = gitService.autoCommit(path, action);
        if (commitResult.success() &&
            commitResult.status() == GitService.CommitResult.CommitStatus.SUCCESS) {
            outputCallback.accept("Git commit: " + commitResult.shortHash());
        }
    }

    public record ActionResult(
        Type type,
        String path,
        boolean success,
        String message
    ) {
        public enum Type {
            FILE_CREATED,
            FILE_APPENDED,
            FILE_PATCHED,
            DIR_CREATED,
            FILE_DELETED
        }
    }
}
