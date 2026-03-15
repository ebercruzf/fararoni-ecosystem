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

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.ToolParameter;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.core.enterprise.git.GitService;
import dev.fararoni.core.enterprise.git.GitService.CommitInfo;
import dev.fararoni.core.enterprise.git.GitService.CommitResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class GitSkill implements ToolSkill {
    private static final Logger LOG = Logger.getLogger(GitSkill.class.getName());
    private static final String SKILL_NAME = "GIT";
    private static final int GIT_TIMEOUT_SECONDS = 30;

    static final String CONVENTIONAL_COMMIT_GUIDE = """
        Genera un mensaje de commit siguiendo el estandar Conventional Commits (Angular Style).

        FORMATO: <type>(<scope>): <descripcion corta>

        TIPOS: feat, fix, docs, style, refactor, perf, test, chore
        SCOPES: auth, api, ui, db, config, cli, git, test, deps

        REGLAS:
        1. Descripcion corta: maximo 50 caracteres, imperativo presente
        2. Menciona las clases principales modificadas
        3. NO uses frases genericas como 'Actualiza archivos' o 'Cambios varios'
        4. Responde SOLO con el mensaje de commit, sin explicacion
        """;

    private final Path workspaceRoot;
    private final GitService gitService;

    public GitSkill(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.gitService = new GitService(this.workspaceRoot);
    }

    @Override
    public String getSkillName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return "Git version control operations for the workspace repository";
    }

    @Override
    public boolean isAvailable() {
        return gitService.isGitAvailable() && gitService.isGitRepo();
    }

    @AgentAction(
        name = "status",
        description = "Get the current git repository status showing modified, staged, and untracked files"
    )
    public String status() {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        Optional<String> status = gitService.getStatus();
        if (status.isEmpty() || status.get().isBlank()) {
            return "Working tree clean - no changes";
        }
        return status.get();
    }

    @AgentAction(
        name = "branch",
        description = "Get the current git branch name"
    )
    public String branch() {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        return gitService.getCurrentBranch()
            .orElse("Error: Could not determine current branch");
    }

    @AgentAction(
        name = "log",
        description = "Get recent commit history with hash, message, and author"
    )
    public String log(
        @ToolParameter(
            name = "count",
            description = "Number of commits to show (default: 5, max: 20)",
            required = false,
            defaultValue = "5"
        ) int count
    ) {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        int safeCount = Math.min(Math.max(count, 1), 20);

        List<CommitInfo> commits = gitService.getRecentCommits(safeCount);
        if (commits.isEmpty()) {
            return "No commits found";
        }

        StringBuilder sb = new StringBuilder();
        for (CommitInfo commit : commits) {
            sb.append(commit.shortHash())
              .append(" ")
              .append(commit.message())
              .append(" (")
              .append(commit.authorName())
              .append(")\n");
        }
        return sb.toString().trim();
    }

    @AgentAction(
        name = "diff",
        description = "Show changes in the working directory. Use staged=true for staged changes only"
    )
    public String diff(
        @ToolParameter(
            name = "staged",
            description = "If true, show only staged changes (git diff --cached)",
            required = false,
            defaultValue = "false"
        ) boolean staged
    ) {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        try {
            List<String> args = staged
                ? List.of("diff", "--cached")
                : List.of("diff");

            ProcessResult result = executeGit(args);
            if (!result.success()) {
                return "Error: " + result.error();
            }

            String output = result.output().trim();
            if (output.isEmpty()) {
                return staged ? "No staged changes" : "No unstaged changes";
            }

            if (output.length() > 4000) {
                return output.substring(0, 4000) + "\n... (output truncated)";
            }
            return output;
        } catch (Exception e) {
            return "Error executing diff: " + e.getMessage();
        }
    }

    private void ensureGitIdentity() {
        try {
            ProcessBuilder check = new ProcessBuilder("git", "config", "--local", "user.name");
            check.directory(workspaceRoot.toFile());
            Process p = check.start();
            boolean hasIdentity = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;

            if (hasIdentity) {
                return;
            }

            String userName = System.getProperty("user.name", "developer");
            String safeName = userName.replaceAll("[^a-zA-Z0-9 ._-]", "") + " (Fararoni Core)";
            String safeEmail = userName.toLowerCase().replaceAll("[^a-z0-9]", ".") + "@fararoni.local";

            new ProcessBuilder("git", "config", "--local", "user.name", safeName)
                .directory(workspaceRoot.toFile()).start().waitFor(5, TimeUnit.SECONDS);
            new ProcessBuilder("git", "config", "--local", "user.email", safeEmail)
                .directory(workspaceRoot.toFile()).start().waitFor(5, TimeUnit.SECONDS);

            LOG.info("[GitSkill] Identidad tactica configurada: " + safeName + " <" + safeEmail + ">");
        } catch (IOException | InterruptedException e) {
            LOG.warning("[GitSkill] No se pudo configurar identidad Git: " + e.getMessage());
        }
    }

    @AgentAction(
        name = "add",
        description = "Stage files for commit. Use '.' to stage all changes"
    )
    public String add(
        @ToolParameter(
            name = "files",
            description = "Space-separated list of files to stage, or '.' for all changes"
        ) String files
    ) {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        if (files == null || files.isBlank()) {
            return "Error: No files specified";
        }

        try {
            List<String> args = new ArrayList<>();
            args.add("add");

            for (String file : files.trim().split("\\s+")) {
                if (!file.isBlank()) {
                    args.add(file);
                }
            }

            ProcessResult result = executeGit(args);
            if (!result.success()) {
                return "Error staging files: " + result.error();
            }

            return "Files staged successfully: " + files;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @AgentAction(
        name = "commit",
        description = "Commit staged changes. Use Conventional Commits format: "
            + "feat(scope): description, fix(scope): description, refactor, docs, chore. "
            + "Message will be prefixed with [FARARONI]"
    )
    public String commit(
        @ToolParameter(
            name = "message",
            description = "Commit message in Conventional Commits format. "
                + "Example: 'feat(auth): add JwtUtil and security filters'"
        ) String message
    ) {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        if (message == null || message.isBlank()) {
            return "Error: Commit message is required";
        }

        ensureGitIdentity();

        try {
            executeGit(List.of("add", "."));

            String fullMessage = "[FARARONI] " + message;
            ProcessResult result = executeGit(List.of("commit", "-m", fullMessage));

            if (!result.success()) {
                String error = result.error().trim();
                if (error.contains("nothing to commit") || result.output().contains("nothing to commit")) {
                    return "Nothing to commit - stage files first with 'add' action";
                }
                return "Error: " + error;
            }

            ProcessResult hashResult = executeGit(List.of("rev-parse", "--short", "HEAD"));
            String hash = hashResult.success() ? hashResult.output().trim() : "unknown";

            return "Commit successful: " + hash + " - " + fullMessage;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @AgentAction(
        name = "commit_files",
        description = "Stage and commit specific files in one step"
    )
    public String commitFiles(
        @ToolParameter(
            name = "files",
            description = "Space-separated list of files to commit"
        ) String files,
        @ToolParameter(
            name = "message",
            description = "Commit message describing the changes"
        ) String message
    ) {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        if (files == null || files.isBlank()) {
            return "Error: No files specified";
        }

        if (message == null || message.isBlank()) {
            return "Error: Commit message is required";
        }

        ensureGitIdentity();

        List<String> fileList = new ArrayList<>();
        for (String file : files.trim().split("\\s+")) {
            if (!file.isBlank()) {
                fileList.add(file);
            }
        }

        CommitResult result = gitService.stageAndCommit(fileList, message);

        if (!result.success()) {
            if (result.status() == CommitResult.CommitStatus.NOTHING_TO_COMMIT) {
                return "Nothing to commit - files may not have changes";
            }
            return "Error: " + result.error();
        }

        return "Commit successful: " + result.shortHash() + " - " + result.message() +
               " (" + result.filesCommitted() + " file(s))";
    }

    @AgentAction(
        name = "push",
        description = "Push committed changes to the remote repository"
    )
    public String push() {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        try {
            ProcessResult result = executeGit(List.of("push"));

            if (!result.success()) {
                String error = result.error().trim();
                if (error.contains("no upstream") || error.contains("has no upstream")) {
                    String branch = gitService.getCurrentBranch().orElse("main");
                    return "Error: No upstream branch configured. Use 'git push -u origin " + branch + "' manually.";
                }
                return "Error: " + error;
            }

            String output = result.output().trim();
            if (output.isEmpty()) {
                output = result.error().trim();
            }
            if (output.contains("Everything up-to-date")) {
                return "Already up to date - nothing to push";
            }

            return "Push successful\n" + output;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @AgentAction(
        name = "pull",
        description = "Pull changes from the remote repository"
    )
    public String pull() {
        if (!gitService.isGitRepo()) {
            return "Error: Not a git repository";
        }

        try {
            ProcessResult result = executeGit(List.of("pull"));

            if (!result.success()) {
                return "Error: " + result.error().trim();
            }

            String output = result.output().trim();
            if (output.isEmpty()) {
                output = "Pull completed";
            }

            return output;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private ProcessResult executeGit(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workspaceRoot.toFile());
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
            return new ProcessResult(false, "", "Timeout executing git command");
        }

        int exitCode = process.exitValue();
        return new ProcessResult(
            exitCode == 0,
            output.toString(),
            error.toString()
        );
    }

    private record ProcessResult(boolean success, String output, String error) {}
}
