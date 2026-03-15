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

import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.saga.CompensationInstruction;
import dev.fararoni.bus.agent.api.skills.GitSkill;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class GitSkillImpl implements GitSkill {
    private static final Logger LOG = Logger.getLogger(GitSkillImpl.class.getName());
    private static final String SKILL_NAME = "git";

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final Pattern COMMIT_PATTERN = Pattern.compile(
        "^([a-f0-9]+)\\|(.*)\\|(.*)\\|(.*)\\|(.*)$"
    );
    private static final Pattern BRANCH_PATTERN = Pattern.compile(
        "^(\\*)?\\s+(.+?)(?:\\s+([a-f0-9]+))?(?:\\s+\\[([^\\]]+)\\])?.*$"
    );

    private final Path repositoryPath;
    private CompensationInstruction lastCompensation;

    public GitSkillImpl(Path repositoryPath) {
        this.repositoryPath = repositoryPath.toAbsolutePath().normalize();
    }

    @Override
    public String getSkillName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return "Git version control with automatic Saga compensation";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public FNLResult<Void> compensate(CompensationInstruction instruction) {
        if (instruction == null) {
            return FNLResult.failure("Compensation instruction is null");
        }

        String method = instruction.method();
        Map<String, Object> params = instruction.params();

        LOG.info(() -> String.format("[GIT_SAGA] Compensating: %s with %s", method, params));

        try {
            return switch (method) {
                case "reset_hard" -> {
                    String sha = (String) params.get("sha");
                    yield executeGit("reset", "--hard", sha).success() ?
                        FNLResult.success(null) :
                        FNLResult.failure("Failed to reset to " + sha);
                }
                case "delete_branch" -> {
                    String branch = (String) params.get("branch");
                    yield executeGit("branch", "-D", branch).success() ?
                        FNLResult.success(null) :
                        FNLResult.failure("Failed to delete branch " + branch);
                }
                case "checkout" -> {
                    String branch = (String) params.get("branch");
                    yield executeGit("checkout", branch).success() ?
                        FNLResult.success(null) :
                        FNLResult.failure("Failed to checkout " + branch);
                }
                case "recreate_branch" -> {
                    String branch = (String) params.get("branch");
                    String sha = (String) params.get("sha");
                    yield executeGit("branch", branch, sha).success() ?
                        FNLResult.success(null) :
                        FNLResult.failure("Failed to recreate branch " + branch);
                }
                default -> FNLResult.failure("Unknown compensation method: " + method);
            };
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[GIT_SAGA] Compensation failed: " + method, e);
            return FNLResult.failure("Compensation failed: " + e.getMessage());
        }
    }

    public CompensationInstruction getLastCompensationInstruction() {
        return lastCompensation;
    }

    @Override
    public FNLResult<GitStatus> status() {
        try {
            GitResult branchResult = executeGit("branch", "--show-current");
            if (!branchResult.success()) {
                return FNLResult.failure("Failed to get current branch: " + branchResult.stderr);
            }
            String branch = branchResult.stdout.trim();

            int ahead = 0, behind = 0;
            GitResult upstreamResult = executeGit("rev-list", "--left-right", "--count", "@{u}...HEAD");
            if (upstreamResult.success()) {
                String[] parts = upstreamResult.stdout.trim().split("\\s+");
                if (parts.length >= 2) {
                    behind = Integer.parseInt(parts[0]);
                    ahead = Integer.parseInt(parts[1]);
                }
            }

            GitResult statusResult = executeGit("status", "--porcelain=v1");
            if (!statusResult.success()) {
                return FNLResult.failure("Failed to get status: " + statusResult.stderr);
            }

            List<String> staged = new ArrayList<>();
            List<String> modified = new ArrayList<>();
            List<String> untracked = new ArrayList<>();
            List<String> conflicted = new ArrayList<>();

            for (String line : statusResult.stdout.split("\n")) {
                if (line.length() < 3) continue;
                char x = line.charAt(0);
                char y = line.charAt(1);
                String file = line.substring(3);

                if (x == 'U' || y == 'U' || (x == 'A' && y == 'A') || (x == 'D' && y == 'D')) {
                    conflicted.add(file);
                } else if (x == '?') {
                    untracked.add(file);
                } else {
                    if (x != ' ' && x != '?') staged.add(file);
                    if (y != ' ' && y != '?') modified.add(file);
                }
            }

            return FNLResult.success(new GitStatus(
                branch, ahead, behind, staged, modified, untracked, conflicted
            ));
        } catch (Exception e) {
            return FNLResult.failure("Status failed: " + e.getMessage());
        }
    }

    @Override
    public FNLResult<String> currentBranch() {
        GitResult result = executeGit("branch", "--show-current");
        if (!result.success()) {
            GitResult fallback = executeGit("rev-parse", "--short", "HEAD");
            if (fallback.success()) {
                return FNLResult.success("(detached at " + fallback.stdout.trim() + ")");
            }
            return FNLResult.failure(result.stderr);
        }
        return FNLResult.success(result.stdout.trim());
    }

    @Override
    public FNLResult<List<BranchInfo>> listBranches(boolean includeRemote) {
        List<String> args = new ArrayList<>(List.of("branch", "-v"));
        if (includeRemote) {
            args.add("-a");
        }

        GitResult result = executeGit(args.toArray(String[]::new));
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        List<BranchInfo> branches = new ArrayList<>();
        for (String line : result.stdout.split("\n")) {
            if (line.isBlank()) continue;

            boolean isCurrent = line.startsWith("*");
            String trimmed = line.replaceFirst("^\\*?\\s+", "");
            String[] parts = trimmed.split("\\s+", 3);

            String name = parts[0];
            boolean isRemote = name.startsWith("remotes/");
            String lastCommit = parts.length > 1 ? parts[1] : "";
            String upstream = null;

            if (name.contains("->")) {
                continue;
            }

            branches.add(new BranchInfo(name, isRemote, isCurrent, lastCommit, upstream));
        }

        return FNLResult.success(branches);
    }

    @Override
    public FNLResult<List<CommitInfo>> log(int maxCount, String branch) {
        List<String> args = new ArrayList<>(List.of(
            "log",
            "--format=%H|%h|%s|%an|%ae|%aI",
            "-n", String.valueOf(maxCount)
        ));
        if (branch != null && !branch.isBlank()) {
            args.add(branch);
        }

        GitResult result = executeGit(args.toArray(String[]::new));
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        List<CommitInfo> commits = new ArrayList<>();
        for (String line : result.stdout.split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|", 6);
            if (parts.length >= 6) {
                commits.add(new CommitInfo(
                    parts[0],
                    parts[1],
                    parts[2],
                    parts[3],
                    parts[4],
                    Instant.parse(parts[5])
                ));
            }
        }

        return FNLResult.success(commits);
    }

    @Override
    public FNLResult<String> diff(boolean staged) {
        GitResult result = staged ?
            executeGit("diff", "--staged") :
            executeGit("diff");
        return result.success() ?
            FNLResult.success(result.stdout) :
            FNLResult.failure(result.stderr);
    }

    @Override
    public FNLResult<Void> stage(String path) {
        GitResult result = executeGit("add", path);
        return result.success() ?
            FNLResult.success(null) :
            FNLResult.failure(result.stderr);
    }

    @Override
    public FNLResult<Void> stageAll() {
        GitResult result = executeGit("add", "-A");
        return result.success() ?
            FNLResult.success(null) :
            FNLResult.failure(result.stderr);
    }

    @Override
    public FNLResult<Void> unstage(String path) {
        GitResult result = executeGit("reset", "HEAD", "--", path);
        return result.success() ?
            FNLResult.success(null) :
            FNLResult.failure(result.stderr);
    }

    @Override
    public FNLResult<CommitInfo> commit(String message) {
        GitResult headResult = executeGit("rev-parse", "HEAD");
        String previousHead = headResult.success() ? headResult.stdout.trim() : null;

        GitResult result = executeGit("commit", "-m", message);
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        FNLResult<List<CommitInfo>> logResult = log(1, null);
        if (!logResult.success() || logResult.data().isEmpty()) {
            return FNLResult.failure("Commit succeeded but failed to get info");
        }

        CommitInfo commitInfo = logResult.data().get(0);

        if (previousHead != null) {
            lastCompensation = CompensationInstruction.of(
                SKILL_NAME,
                "reset_hard",
                Map.<String, Object>of("sha", previousHead)
            );
        }

        LOG.info(() -> String.format("[GIT] Committed: %s - %s", commitInfo.shortSha(), message));
        return FNLResult.success(commitInfo);
    }

    @Override
    public FNLResult<CommitInfo> amend(String message) {
        GitResult headResult = executeGit("rev-parse", "HEAD");
        String previousHead = headResult.success() ? headResult.stdout.trim() : null;

        List<String> args = new ArrayList<>(List.of("commit", "--amend"));
        if (message != null && !message.isBlank()) {
            args.add("-m");
            args.add(message);
        } else {
            args.add("--no-edit");
        }

        GitResult result = executeGit(args.toArray(String[]::new));
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        FNLResult<List<CommitInfo>> logResult = log(1, null);
        if (!logResult.success() || logResult.data().isEmpty()) {
            return FNLResult.failure("Amend succeeded but failed to get info");
        }

        if (previousHead != null) {
            lastCompensation = CompensationInstruction.of(
                SKILL_NAME,
                "reset_hard",
                Map.<String, Object>of("sha", previousHead)
            );
        }

        return FNLResult.success(logResult.data().get(0));
    }

    @Override
    public FNLResult<Void> createBranch(String name, boolean checkout) {
        GitResult result;
        if (checkout) {
            result = executeGit("checkout", "-b", name);
        } else {
            result = executeGit("branch", name);
        }

        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        lastCompensation = CompensationInstruction.of(
            SKILL_NAME,
            "delete_branch",
            Map.<String, Object>of("branch", name)
        );

        return FNLResult.success(null);
    }

    @Override
    public FNLResult<Void> checkout(String name) {
        FNLResult<String> currentResult = currentBranch();
        String previousBranch = currentResult.success() ? currentResult.data() : null;

        GitResult result = executeGit("checkout", name);
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        if (previousBranch != null && !previousBranch.startsWith("(detached")) {
            lastCompensation = CompensationInstruction.of(
                SKILL_NAME,
                "checkout",
                Map.<String, Object>of("branch", previousBranch)
            );
        }

        return FNLResult.success(null);
    }

    @Override
    public FNLResult<Void> deleteBranch(String name, boolean force) {
        GitResult shaResult = executeGit("rev-parse", name);
        String branchSha = shaResult.success() ? shaResult.stdout.trim() : null;

        GitResult result = force ?
            executeGit("branch", "-D", name) :
            executeGit("branch", "-d", name);

        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        if (branchSha != null) {
            lastCompensation = CompensationInstruction.of(
                SKILL_NAME,
                "recreate_branch",
                Map.<String, Object>of("branch", name, "sha", branchSha)
            );
        }

        return FNLResult.success(null);
    }

    @Override
    public FNLResult<MergeResult> merge(String branch, boolean noFastForward) {
        GitResult headResult = executeGit("rev-parse", "HEAD");
        String previousHead = headResult.success() ? headResult.stdout.trim() : null;

        List<String> args = new ArrayList<>(List.of("merge"));
        if (noFastForward) {
            args.add("--no-ff");
        }
        args.add(branch);

        GitResult result = executeGit(args.toArray(String[]::new));

        if (!result.success()) {
            GitResult statusResult = executeGit("status", "--porcelain");
            List<String> conflicts = new ArrayList<>();
            for (String line : statusResult.stdout.split("\n")) {
                if (line.startsWith("UU") || line.startsWith("AA") || line.startsWith("DD")) {
                    conflicts.add(line.substring(3).trim());
                }
            }
            if (!conflicts.isEmpty()) {
                return FNLResult.success(new MergeResult(false, null, false, conflicts));
            }
            return FNLResult.failure(result.stderr);
        }

        GitResult newHeadResult = executeGit("rev-parse", "HEAD");
        String newHead = newHeadResult.success() ? newHeadResult.stdout.trim() : null;
        boolean fastForward = previousHead != null && newHead != null &&
            !previousHead.equals(newHead) && !noFastForward;

        if (previousHead != null) {
            lastCompensation = CompensationInstruction.of(
                SKILL_NAME,
                "reset_hard",
                Map.<String, Object>of("sha", previousHead)
            );
        }

        return FNLResult.success(new MergeResult(true, newHead, fastForward, List.of()));
    }

    @Override
    public FNLResult<Void> fetch(String remote) {
        GitResult result = executeGit("fetch", remote);
        return result.success() ?
            FNLResult.success(null) :
            FNLResult.failure(result.stderr);
    }

    @Override
    public FNLResult<Void> pull(String remote, String branch) {
        GitResult headResult = executeGit("rev-parse", "HEAD");
        String previousHead = headResult.success() ? headResult.stdout.trim() : null;

        GitResult result = executeGit("pull", remote, branch);
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        if (previousHead != null) {
            lastCompensation = CompensationInstruction.of(
                SKILL_NAME,
                "reset_hard",
                Map.<String, Object>of("sha", previousHead)
            );
        }

        return FNLResult.success(null);
    }

    @Override
    public FNLResult<Void> push(String remote, String branch, boolean force) {
        List<String> args = new ArrayList<>(List.of("push", remote, branch));
        if (force) {
            args.add(1, "--force");
            LOG.warning(() -> String.format("[GIT] Force pushing to %s/%s", remote, branch));
        }

        GitResult result = executeGit(args.toArray(String[]::new));
        return result.success() ?
            FNLResult.success(null) :
            FNLResult.failure(result.stderr);
    }

    @Override
    public FNLResult<String> stash(String message) {
        List<String> args = new ArrayList<>(List.of("stash", "push"));
        if (message != null && !message.isBlank()) {
            args.add("-m");
            args.add(message);
        }

        GitResult result = executeGit(args.toArray(String[]::new));
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        GitResult refResult = executeGit("stash", "list", "-1");
        String stashRef = refResult.success() ? refResult.stdout.trim().split(":")[0] : "stash@{0}";

        return FNLResult.success(stashRef);
    }

    @Override
    public FNLResult<Void> stashPop() {
        GitResult result = executeGit("stash", "pop");
        return result.success() ?
            FNLResult.success(null) :
            FNLResult.failure(result.stderr);
    }

    @Override
    public FNLResult<Void> reset(String ref, ResetMode mode) {
        GitResult headResult = executeGit("rev-parse", "HEAD");
        String previousHead = headResult.success() ? headResult.stdout.trim() : null;

        String modeArg = switch (mode) {
            case SOFT -> "--soft";
            case MIXED -> "--mixed";
            case HARD -> "--hard";
        };

        GitResult result = executeGit("reset", modeArg, ref);
        if (!result.success()) {
            return FNLResult.failure(result.stderr);
        }

        if (previousHead != null) {
            lastCompensation = CompensationInstruction.of(
                SKILL_NAME,
                "reset_hard",
                Map.<String, Object>of("sha", previousHead)
            );
        }

        return FNLResult.success(null);
    }

    private record GitResult(boolean success, String stdout, String stderr, int exitCode) {}

    private GitResult executeGit(String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(repositoryPath.toFile());

            Process process = pb.start();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            Thread outThread = new Thread(() -> {
                try (InputStream is = process.getInputStream()) {
                    is.transferTo(stdout);
                } catch (IOException e) {  }
            });

            Thread errThread = new Thread(() -> {
                try (InputStream is = process.getErrorStream()) {
                    is.transferTo(stderr);
                } catch (IOException e) {  }
            });

            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitResult(false, "", "Git command timed out", -1);
            }

            outThread.join(1000);
            errThread.join(1000);

            int exitCode = process.exitValue();
            return new GitResult(
                exitCode == 0,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                exitCode
            );
        } catch (Exception e) {
            return new GitResult(false, "", "Git execution failed: " + e.getMessage(), -1);
        }
    }
}
