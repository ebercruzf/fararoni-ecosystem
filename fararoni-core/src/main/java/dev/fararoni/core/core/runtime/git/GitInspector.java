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
package dev.fararoni.core.core.runtime.git;

import dev.fararoni.core.core.runtime.ShellSession;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class GitInspector {
    private static final Logger LOG = Logger.getLogger(GitInspector.class.getName());

    public static final int DEFAULT_COMMIT_COUNT = 10;

    public static final int MAX_REPORT_LINES = 30;

    public static final int MAX_DIFF_LENGTH = 2000;

    private static final String ICON_CHANGES = "[~]";

    private static final String ICON_CLEAN = "[+]";

    private static final String ICON_WARNING = "[!]";

    private static final String ICON_STATS = "[#]";

    private static final String ICON_BRANCH = "[>]";

    private static final String ICON_COMMIT = "[*]";

    private static final Pattern STATUS_PATTERN = Pattern.compile("^\\s*([A-Z?!]+|\\s[A-Z])\\s+(.+)$");

    private final ShellSession shell;

    private Boolean isRepoCache = null;
    private Boolean hasHeadCache = null;

    public GitInspector(ShellSession shell) {
        if (shell == null) {
            throw new IllegalArgumentException("ShellSession cannot be null");
        }
        this.shell = shell;
    }

    public boolean isGitRepository() {
        if (isRepoCache != null) {
            return isRepoCache;
        }

        ShellSession.CommandResult result = shell.execute("git rev-parse --is-inside-work-tree");
        isRepoCache = result.isSuccess() && result.stdout().trim().equals("true");
        return isRepoCache;
    }

    public boolean hasHead() {
        if (hasHeadCache != null) {
            return hasHeadCache;
        }

        if (!isGitRepository()) {
            hasHeadCache = false;
            return false;
        }

        ShellSession.CommandResult result = shell.execute("git rev-parse --verify HEAD");
        hasHeadCache = result.isSuccess();
        return hasHeadCache;
    }

    public void clearCache() {
        isRepoCache = null;
        hasHeadCache = null;
    }

    public Optional<String> getCurrentBranch() {
        ShellSession.CommandResult result = shell.execute("git branch --show-current");
        if (result.isSuccess() && !result.stdout().isBlank()) {
            return Optional.of(result.stdout().trim());
        }
        return Optional.empty();
    }

    public Optional<Path> getRepositoryRoot() {
        ShellSession.CommandResult result = shell.execute("git rev-parse --show-toplevel");
        if (result.isSuccess() && !result.stdout().isBlank()) {
            return Optional.of(Path.of(result.stdout().trim()));
        }
        return Optional.empty();
    }

    public record FileChange(
            String status,
            String statusDescription,
            String filePath
    ) {
        public boolean isUntracked() {
            return "??".equals(status);
        }

        public boolean isModified() {
            return status.contains("M");
        }

        public boolean isAdded() {
            return status.contains("A");
        }

        public boolean isDeleted() {
            return status.contains("D");
        }
    }

    public String getUncommittedChanges() {
        ShellSession.CommandResult result = shell.execute("git status --porcelain");

        if (!result.isSuccess()) {
            return ICON_WARNING + " No es un repositorio git o hubo un error: " + result.stderr();
        }

        if (result.stdout().isBlank()) {
            return ICON_CLEAN + " El directorio de trabajo esta limpio (No hay cambios pendientes).";
        }

        StringBuilder report = new StringBuilder(ICON_CHANGES + " CAMBIOS NO COMITEADOS:\n");
        String[] lines = result.stdout().split("\n");

        int reportedCount = 0;
        int totalChanges = 0;

        for (String line : lines) {
            if (line.isEmpty()) continue;
            totalChanges++;

            if (reportedCount >= MAX_REPORT_LINES) {
                continue;
            }

            Matcher matcher = STATUS_PATTERN.matcher(line);
            if (!matcher.matches()) {
                LOG.fine("[GIT] Could not parse status line: " + line);
                continue;
            }

            String status = matcher.group(1).trim();
            String file = matcher.group(2).trim();

            if (file.isEmpty()) continue;

            String desc = describeStatus(status);
            report.append(String.format("- [%s] %s\n", desc, file));
            reportedCount++;
        }

        int remaining = totalChanges - reportedCount;
        if (remaining > 0) {
            report.append(String.format("... y %d archivos mas (truncado para brevedad).\n", remaining));
        }

        return report.toString();
    }

    public List<FileChange> getUncommittedChangesAsList() {
        List<FileChange> changes = new ArrayList<>();

        ShellSession.CommandResult result = shell.execute("git status --porcelain");
        if (!result.isSuccess() || result.stdout().isBlank()) {
            return changes;
        }

        String[] lines = result.stdout().split("\n");
        for (String line : lines) {
            if (line.isEmpty()) continue;

            Matcher matcher = STATUS_PATTERN.matcher(line);
            if (!matcher.matches()) {
                LOG.fine("[GIT] Could not parse status line: " + line);
                continue;
            }

            String status = matcher.group(1).trim();
            String file = matcher.group(2).trim();

            if (file.isEmpty()) continue;

            String desc = describeStatus(status);
            changes.add(new FileChange(status, desc, file));
        }

        return changes;
    }

    private String describeStatus(String status) {
        return switch (status) {
            case "M" -> "Modificado";
            case "A" -> "Nuevo (Added)";
            case "D" -> "Borrado";
            case "R" -> "Renombrado";
            case "C" -> "Copiado";
            case "U" -> "Actualizado (Unmerged)";
            case "??" -> "No rastreado (Untracked)";
            case "!!" -> "Ignorado";
            case "MM" -> "Modificado (staged y unstaged)";
            case "AM" -> "Agregado y luego modificado";
            default -> status;
        };
    }

    public String getDiffSummary() {
        if (!isGitRepository()) {
            return "";
        }

        if (!hasHead()) {
            return "(Repositorio nuevo sin commits previos)";
        }

        ShellSession.CommandResult result = shell.execute("git diff HEAD --stat");

        if (!result.isSuccess() || result.stdout().isBlank()) {
            return "";
        }

        String output = result.stdout();

        if (output.length() > MAX_DIFF_LENGTH) {
            output = output.substring(0, MAX_DIFF_LENGTH) + "\n... [DIFF STAT TRUNCADO]";
        }

        return ICON_STATS + " RESUMEN DE CAMBIOS (DIFF STAT):\n" + output;
    }

    public String getFileDiff(String filePath) {
        if (!hasHead()) {
            ShellSession.CommandResult result = shell.execute("git diff --cached -- \"" + filePath + "\"");
            return result.isSuccess() ? result.stdout() : "";
        }

        ShellSession.CommandResult result = shell.execute("git diff HEAD -- \"" + filePath + "\"");

        if (!result.isSuccess() || result.stdout().isBlank()) {
            return "";
        }

        return result.stdout();
    }

    public int[] getDiffStats() {
        if (!hasHead()) {
            return new int[]{0, 0};
        }

        ShellSession.CommandResult result = shell.execute("git diff HEAD --numstat");

        if (!result.isSuccess() || result.stdout().isBlank()) {
            return new int[]{0, 0};
        }

        int added = 0;
        int deleted = 0;

        String[] lines = result.stdout().split("\n");
        for (String line : lines) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                try {
                    if (!"-".equals(parts[0])) added += Integer.parseInt(parts[0]);
                    if (!"-".equals(parts[1])) deleted += Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                }
            }
        }

        return new int[]{added, deleted};
    }

    public record CommitInfo(
            String hash,
            String shortHash,
            String author,
            String date,
            String message
    ) {
        public String toOneLine() {
            return shortHash + " - " + message + " (" + author + ", " + date + ")";
        }
    }

    public String getLastCommitMessage() {
        ShellSession.CommandResult result = shell.execute("git log -1 --pretty=%B");
        return result.isSuccess() ? result.stdout().trim() : "Desconocido";
    }

    public Optional<CommitInfo> getLastCommit() {
        return getRecentCommits(1).stream().findFirst();
    }

    public List<CommitInfo> getRecentCommits(int count) {
        List<CommitInfo> commits = new ArrayList<>();

        String format = "%H|%h|%an|%ar|%s";
        ShellSession.CommandResult result = shell.execute(
                "git log -" + count + " --pretty=format:\"" + format + "\""
        );

        if (!result.isSuccess() || result.stdout().isBlank()) {
            return commits;
        }

        String[] lines = result.stdout().split("\n");
        for (String line : lines) {
            String[] parts = line.split("\\|", 5);
            if (parts.length >= 5) {
                commits.add(new CommitInfo(
                        parts[0].trim(),
                        parts[1].trim(),
                        parts[2].trim(),
                        parts[3].trim(),
                        parts[4].trim()
                ));
            }
        }

        return commits;
    }

    public String getRecentCommitsFormatted(int count) {
        List<CommitInfo> commits = getRecentCommits(count);

        if (commits.isEmpty()) {
            return ICON_WARNING + " No hay commits en este repositorio.";
        }

        StringBuilder sb = new StringBuilder(ICON_COMMIT + " COMMITS RECIENTES:\n");
        for (CommitInfo commit : commits) {
            sb.append("- ").append(commit.toOneLine()).append("\n");
        }

        return sb.toString();
    }

    public String getFullSituationReport() {
        if (!isGitRepository()) {
            return ICON_WARNING + " Este directorio no es un repositorio Git.";
        }

        StringBuilder report = new StringBuilder();

        getCurrentBranch().ifPresent(branch ->
                report.append(ICON_BRANCH + " RAMA ACTUAL: ").append(branch).append("\n\n")
        );

        report.append(getUncommittedChanges()).append("\n");

        String diff = getDiffSummary();
        if (!diff.isEmpty()) {
            report.append(diff).append("\n");
        }

        getLastCommit().ifPresent(commit ->
                report.append(ICON_COMMIT + " ULTIMO COMMIT: ")
                        .append(commit.toOneLine())
                        .append("\n")
        );

        return report.toString();
    }

    public String getCompactContext() {
        if (!isGitRepository()) {
            return "[No Git]";
        }

        StringBuilder ctx = new StringBuilder();

        getCurrentBranch().ifPresent(branch ->
                ctx.append("Branch: ").append(branch)
        );

        List<FileChange> changes = getUncommittedChangesAsList();
        if (!changes.isEmpty()) {
            ctx.append(" | ").append(changes.size()).append(" archivos modificados");
        } else {
            ctx.append(" | Limpio");
        }

        return ctx.toString();
    }

    public ShellSession getShell() {
        return shell;
    }
}
