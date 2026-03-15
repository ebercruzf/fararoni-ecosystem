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
package dev.fararoni.core.enterprise.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class GitService {
    private final Path workingDirectory;
    private static final String COMMIT_PREFIX = "[FARARONI]";
    private static final int GIT_TIMEOUT_SECONDS = 30;
    private boolean autoCommitEnabled = true;

    public GitService(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public boolean isGitRepo() {
        Path gitDir = workingDirectory.resolve(".git");
        return Files.exists(gitDir) && Files.isDirectory(gitDir);
    }

    public boolean isGitAvailable() {
        try {
            ProcessResult result = executeGit("--version");
            return result.success() && result.output().contains("git version");
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<String> getStatus() {
        if (!isGitRepo()) {
            return Optional.empty();
        }
        try {
            ProcessResult result = executeGit("status", "--short");
            return result.success() ? Optional.of(result.output()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public CommitResult stageAndCommit(List<String> files, String message) {
        if (!isGitRepo()) {
            return CommitResult.error("No es un repositorio git");
        }

        if (files == null || files.isEmpty()) {
            return CommitResult.error("No hay archivos para commitear");
        }

        if (message == null || message.isBlank()) {
            message = "Agent changes";
        }

        try {
            for (String file : files) {
                ProcessResult addResult = executeGit("add", file);
                if (!addResult.success()) {
                    return CommitResult.error("Error al agregar " + file + ": " + addResult.error());
                }
            }

            String fullMessage = COMMIT_PREFIX + " " + message;
            ProcessResult commitResult = executeGit("commit", "-m", fullMessage);

            if (!commitResult.success()) {
                if (commitResult.output().contains("nothing to commit") ||
                    commitResult.error().contains("nothing to commit")) {
                    return CommitResult.nothingToCommit();
                }
                return CommitResult.error("Error en commit: " + commitResult.error());
            }

            ProcessResult hashResult = executeGit("rev-parse", "--short", "HEAD");
            String shortHash = hashResult.success() ? hashResult.output().trim() : "unknown";

            return CommitResult.success(shortHash, fullMessage, files.size());
        } catch (Exception e) {
            return CommitResult.error("Excepción: " + e.getMessage());
        }
    }

    public CommitResult commitFile(String file, String message) {
        return stageAndCommit(List.of(file), message);
    }

    public CommitResult autoCommit(String file, String action) {
        if (!autoCommitEnabled) {
            return CommitResult.disabled();
        }

        String message = switch (action.toLowerCase()) {
            case "create", "file_created" -> "feat(agent): create " + file;
            case "modify", "file_modified" -> "fix(agent): modify " + file;
            case "delete", "file_deleted" -> "chore(agent): delete " + file;
            default -> "chore(agent): " + action + " " + file;
        };

        return stageAndCommit(List.of(file), message);
    }

    public Optional<CommitInfo> getLastCommit() {
        if (!isGitRepo()) {
            return Optional.empty();
        }

        try {
            ProcessResult result = executeGit("log", "-1", "--format=%H|%h|%s|%an|%ae");
            if (!result.success() || result.output().isBlank()) {
                return Optional.empty();
            }

            String[] parts = result.output().trim().split("\\|");
            if (parts.length >= 5) {
                return Optional.of(new CommitInfo(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4]
                ));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<CommitInfo> getRecentCommits(int count) {
        List<CommitInfo> commits = new ArrayList<>();
        if (!isGitRepo()) {
            return commits;
        }

        try {
            ProcessResult result = executeGit("log", "-" + count, "--format=%H|%h|%s|%an|%ae");
            if (!result.success()) {
                return commits;
            }

            for (String line : result.output().trim().split("\n")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 5) {
                    commits.add(new CommitInfo(
                        parts[0], parts[1], parts[2], parts[3], parts[4]
                    ));
                }
            }
        } catch (Exception e) {
        }

        return commits;
    }

    public boolean resetToLastCommit() {
        if (!isGitRepo()) {
            return false;
        }

        try {
            ProcessResult result = executeGit("reset", "--hard", "HEAD");
            return result.success();
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<String> getCurrentBranch() {
        if (!isGitRepo()) {
            return Optional.empty();
        }

        try {
            ProcessResult result = executeGit("rev-parse", "--abbrev-ref", "HEAD");
            return result.success() ? Optional.of(result.output().trim()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void setAutoCommitEnabled(boolean enabled) {
        this.autoCommitEnabled = enabled;
    }

    public boolean isAutoCommitEnabled() {
        return autoCommitEnabled;
    }

    private ProcessResult executeGit(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(false, "", "Timeout ejecutando git");
        }

        int exitCode = process.exitValue();
        return new ProcessResult(
            exitCode == 0,
            output.toString(),
            error.toString()
        );
    }

    private record ProcessResult(boolean success, String output, String error) {}

    public record CommitResult(
        boolean success,
        String shortHash,
        String message,
        int filesCommitted,
        String error,
        CommitStatus status
    ) {
        public enum CommitStatus {
            SUCCESS,
            NOTHING_TO_COMMIT,
            DISABLED,
            ERROR
        }

        public static CommitResult success(String shortHash, String message, int files) {
            return new CommitResult(true, shortHash, message, files, null, CommitStatus.SUCCESS);
        }

        public static CommitResult error(String error) {
            return new CommitResult(false, null, null, 0, error, CommitStatus.ERROR);
        }

        public static CommitResult nothingToCommit() {
            return new CommitResult(true, null, null, 0, null, CommitStatus.NOTHING_TO_COMMIT);
        }

        public static CommitResult disabled() {
            return new CommitResult(false, null, null, 0, "Auto-commit disabled", CommitStatus.DISABLED);
        }
    }

    public record CommitInfo(
        String fullHash,
        String shortHash,
        String message,
        String authorName,
        String authorEmail
    ) {
        public boolean isFararoniCommit() {
            return message != null && message.startsWith(COMMIT_PREFIX);
        }
    }
}
