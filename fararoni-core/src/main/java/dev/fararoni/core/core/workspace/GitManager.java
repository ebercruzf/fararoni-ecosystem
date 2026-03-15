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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class GitManager {
    private static final Logger LOG = Logger.getLogger(GitManager.class.getName());

    private static final String SNAPSHOT_PREFIX = "Fararoni_Snapshot_";

    private static final String EPHEMERAL_PREFIX = "fararoni/wip-";

    private static final String COMMIT_PREFIX = "refactor(ai): ";

    private static final int GIT_TIMEOUT_SECONDS = 30;

    private final Path workingDirectory;

    private CommandResult lastResult;

    private String originalBranch = null;

    private String currentEphemeralBranch = null;

    public GitManager() {
        this(null);
    }

    public GitManager(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public boolean createSnapshot() {
        String snapshotName = SNAPSHOT_PREFIX + System.currentTimeMillis();
        lastResult = runCommand("git", "stash", "push", "-m", snapshotName);

        if (lastResult.success) {
            LOG.info("[GIT] Snapshot de seguridad creado: " + snapshotName);
        } else {
            if (lastResult.output.contains("No local changes to save")) {
                LOG.fine("[GIT] No hay cambios para guardar en snapshot");
                return true;
            }
            LOG.warning("[GIT] Error creando snapshot: " + lastResult.output);
        }

        return lastResult.success || lastResult.output.contains("No local changes");
    }

    public boolean undoLastChange() {
        lastResult = runCommand("git", "stash", "apply");

        if (lastResult.success) {
            LOG.info("[GIT] Cambios revertidos exitosamente (stash preservado)");
        } else {
            LOG.warning("[GIT] Error revirtiendo cambios: " + lastResult.output);
        }

        return lastResult.success;
    }

    public boolean discardSnapshot() {
        lastResult = runCommand("git", "stash", "drop");

        if (lastResult.success) {
            LOG.info("[GIT] Snapshot descartado");
        }

        return lastResult.success;
    }

    public boolean smartCommit(String llmSummary) {
        return smartCommit(llmSummary, null);
    }

    public boolean smartCommit(String llmSummary, List<Path> specificFiles) {
        if (!hasUncommittedChanges()) {
            LOG.info("[GIT] No hay cambios para commitear");
            return true;
        }

        boolean stageSuccess;
        if (specificFiles != null && !specificFiles.isEmpty()) {
            stageSuccess = stageSpecificFiles(specificFiles);
        } else {
            LOG.fine("[GIT] Usando git add . (considera especificar archivos)");
            CommandResult addResult = runCommand("git", "add", ".");
            stageSuccess = addResult.success;
            if (!stageSuccess) {
                LOG.warning("[GIT] Error en git add: " + addResult.output);
            }
        }

        if (!stageSuccess) {
            return false;
        }

        String commitMsg = COMMIT_PREFIX + sanitizeCommitMessage(llmSummary);

        lastResult = runCommand("git", "commit", "-m", commitMsg);

        if (lastResult.success) {
            LOG.info("[GIT] Smart Commit generado: " + commitMsg);
        } else {
            LOG.warning("[GIT] Error en commit: " + lastResult.output);
        }

        return lastResult.success;
    }

    public boolean stageSpecificFiles(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return true;
        }

        for (Path file : files) {
            Path absolutePath = workingDirectory != null
                    ? workingDirectory.resolve(file)
                    : file;

            if (!Files.exists(absolutePath)) {
                LOG.warning("[GIT] Archivo no existe, omitiendo: " + file);
                continue;
            }

            String filePath = file.toString();
            CommandResult result = runCommand("git", "add", filePath);

            if (!result.success) {
                LOG.warning("[GIT] Error agregando " + file + ": " + result.output);
                return false;
            }
        }

        LOG.info("[GIT] Staged " + files.size() + " archivos especificos");
        return true;
    }

    public boolean stageFiles(Path... files) {
        return stageSpecificFiles(Arrays.asList(files));
    }

    private String sanitizeCommitMessage(String message) {
        if (message == null || message.isBlank()) {
            return "automated changes";
        }

        String sanitized = message
                .replaceAll("[\"'`]", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (sanitized.length() > 72) {
            sanitized = sanitized.substring(0, 69) + "...";
        }

        return sanitized;
    }

    public boolean hasUncommittedChanges() {
        lastResult = runCommand("git", "status", "--porcelain");
        return lastResult.success && !lastResult.output.isBlank();
    }

    public CleanStateResult validateCleanState() {
        if (!isGitRepository()) {
            return new CleanStateResult(false, "Not a git repository", CleanStateIssue.NOT_A_REPO);
        }

        CommandResult mergeHead = runCommand("git", "rev-parse", "--verify", "MERGE_HEAD");
        if (mergeHead.success) {
            return new CleanStateResult(false, "Merge in progress", CleanStateIssue.MERGE_IN_PROGRESS);
        }

        CommandResult rebaseHead = runCommand("git", "rev-parse", "--verify", "REBASE_HEAD");
        if (rebaseHead.success) {
            return new CleanStateResult(false, "Rebase in progress", CleanStateIssue.REBASE_IN_PROGRESS);
        }

        boolean hasChanges = hasUncommittedChanges();
        if (hasChanges) {
            return new CleanStateResult(true, "Has uncommitted changes (will be stashed)",
                    CleanStateIssue.UNCOMMITTED_CHANGES);
        }

        return new CleanStateResult(true, "Clean state", CleanStateIssue.NONE);
    }

    public boolean isSafeToModify() {
        CleanStateResult state = validateCleanState();
        return state.safe;
    }

    public record CleanStateResult(boolean safe, String message, CleanStateIssue issue) {}

    public enum CleanStateIssue {
        NONE,
        NOT_A_REPO,
        MERGE_IN_PROGRESS,
        REBASE_IN_PROGRESS,
        UNCOMMITTED_CHANGES
    }

    public String getDiff() {
        CommandResult result = runCommand("git", "diff");
        return result.success ? result.output : "";
    }

    public String getStagedDiff() {
        CommandResult result = runCommand("git", "diff", "--staged");
        return result.success ? result.output : "";
    }

    public boolean isGitRepository() {
        CommandResult result = runCommand("git", "rev-parse", "--is-inside-work-tree");
        return result.success && result.output.trim().equals("true");
    }

    public String getCurrentBranch() {
        CommandResult result = runCommand("git", "branch", "--show-current");
        return result.success ? result.output.trim() : null;
    }

    public List<String> listFararoniSnapshots() {
        List<String> snapshots = new ArrayList<>();
        CommandResult result = runCommand("git", "stash", "list");

        if (result.success && !result.output.isBlank()) {
            for (String line : result.output.split("\n")) {
                if (line.contains(SNAPSHOT_PREFIX)) {
                    snapshots.add(line);
                }
            }
        }

        return snapshots;
    }

    public String startEphemeralBranch(String taskId) {
        if (currentEphemeralBranch != null) {
            return currentEphemeralBranch;
        }

        CommandResult headCheck = runCommand("git", "rev-parse", "HEAD");
        if (!headCheck.success) {
            LOG.info("[GIT] Repo sin commits (HEAD invalido), omitiendo rama efímera");
            return null;
        }

        originalBranch = getCurrentBranch();
        if (originalBranch == null || originalBranch.isBlank()) {
            return null;
        }
        boolean hadChanges = hasUncommittedChanges();
        if (hadChanges) { createSnapshot(); }

        String branchName = EPHEMERAL_PREFIX + taskId;
        CommandResult r = runCommand("git", "checkout", "-b", branchName);
        if (!r.success) {
            if (hadChanges) { runCommand("git", "stash", "pop"); }
            return null;
        }
        if (hadChanges) { runCommand("git", "stash", "pop"); }
        currentEphemeralBranch = branchName;
        LOG.info("[GIT] Rama efímera creada: " + branchName + " (original: " + originalBranch + ")");
        return branchName;
    }

    public boolean hasActiveEphemeralBranch() { return currentEphemeralBranch != null; }
    public String getEphemeralBranchName() { return currentEphemeralBranch; }
    public String getOriginalBranch() { return originalBranch; }

    public EphemeralResult finalizeEphemeralBranch(String message) {
        if (currentEphemeralBranch == null || originalBranch == null) {
            return new EphemeralResult(false, "No hay rama efímera activa para finalizar");
        }
        String ephemeral = currentEphemeralBranch;

        CommandResult co = runCommand("git", "checkout", originalBranch);
        if (!co.success) return new EphemeralResult(false, "Error volviendo a rama original: " + co.output);

        CommandResult mg = runCommand("git", "merge", "--squash", ephemeral);
        if (!mg.success) return new EphemeralResult(false, "Error en squash merge: " + mg.output);

        String commitMsg = "[FARARONI] " + sanitizeCommitMessage(message);
        CommandResult cm = runCommand("git", "commit", "-m", commitMsg);
        if (!cm.success) {
            if (cm.output.contains("nothing to commit")) {
                runCommand("git", "branch", "-D", ephemeral);
                currentEphemeralBranch = null; originalBranch = null;
                return new EphemeralResult(true, "Sin cambios para consolidar. Rama efímera eliminada.");
            }
            return new EphemeralResult(false, "Error en commit consolidado: " + cm.output);
        }

        runCommand("git", "branch", "-D", ephemeral);
        currentEphemeralBranch = null; originalBranch = null;
        LOG.info("[GIT] Squash merge completado: " + commitMsg);
        return new EphemeralResult(true, "Cambios consolidados en un solo commit: " + commitMsg);
    }

    public boolean discardEphemeralBranch() {
        if (currentEphemeralBranch == null || originalBranch == null) return false;
        String ephemeral = currentEphemeralBranch;
        CommandResult co = runCommand("git", "checkout", originalBranch);
        if (!co.success) return false;
        runCommand("git", "branch", "-D", ephemeral);
        currentEphemeralBranch = null; originalBranch = null;
        LOG.info("[GIT] Rama efímera descartada: " + ephemeral);
        return true;
    }

    public record EphemeralResult(boolean success, String message) {}

    private CommandResult runCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            if (workingDirectory != null) {
                pb.directory(workingDirectory.toFile());
            }

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(false, "Command timed out", -1);
            }

            int exitCode = process.exitValue();
            return new CommandResult(exitCode == 0, output.toString().trim(), exitCode);
        } catch (Exception e) {
            LOG.severe("[GIT] Error ejecutando comando: " + e.getMessage());
            return new CommandResult(false, "Error: " + e.getMessage(), -1);
        }
    }

    public CommandResult getLastResult() {
        return lastResult;
    }

    public boolean wasLastCommandSuccessful() {
        return lastResult != null && lastResult.success;
    }

    public record CommandResult(boolean success, String output, int exitCode) {}
}
