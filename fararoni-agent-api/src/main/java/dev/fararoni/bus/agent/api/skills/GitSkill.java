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
package dev.fararoni.bus.agent.api.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.saga.SagaCapableSkill;
import dev.fararoni.bus.agent.api.security.AuditLog;
import dev.fararoni.bus.agent.api.security.RequiresRole;

import java.time.Instant;
import java.util.List;

/**
 * Contract for Git version control operations with Saga compensation.
 *
 * <p>This interface defines Git operations that the AI agent can perform.
 * All mutating operations support automatic rollback via the Saga pattern.</p>
 *
 * <h2>Saga Compensation</h2>
 * <pre>
 * Agent: commit("Fix bug in parser")
 *        ↓
 * FNL: 1. Record current HEAD: abc123
 *      2. Create commit: def456
 *      3. Return success + CompensationInstruction("reset", "abc123")
 *        ↓
 * Later step fails (e.g., push rejected)
 *        ↓
 * FNL: Execute compensation → git reset --hard abc123 → back to original
 * </pre>
 *
 * <h2>Safety Features</h2>
 * <ul>
 *   <li>Never force-pushes without explicit permission</li>
 *   <li>Refuses to operate on main/master without elevated role</li>
 *   <li>Stashes uncommitted changes before risky operations</li>
 *   <li>All operations logged for audit trail</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Stage and commit with automatic Saga
 * gitSkill.stageAll();
 * FNLResult<CommitInfo> result = gitSkill.commit("Implement feature X");
 *
 * if (result.success()) {
 *     // Compensation stored: can rollback this commit if needed
 *     String sha = result.data().sha();
 *
 *     // Try to push
 *     FNLResult<Void> pushResult = gitSkill.push("origin", "feature-x", false);
 *     if (!pushResult.success()) {
 *         // Push failed - rollback the commit
 *         gitSkill.compensate(result.undoInstruction());
 *     }
 * }
 * }</pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see SagaCapableSkill
 */
public interface GitSkill extends SagaCapableSkill {

    // ==================== Status Operations ====================

    /**
     * Gets the current repository status.
     *
     * @return result containing repository status
     */
    @AgentAction(
        name = "status",
        description = "Shows working tree status: modified, staged, untracked files"
    )
    FNLResult<GitStatus> status();

    /**
     * Gets the current branch name.
     *
     * @return result containing branch name
     */
    @AgentAction(
        name = "current_branch",
        description = "Returns the name of the current branch"
    )
    FNLResult<String> currentBranch();

    /**
     * Lists all branches.
     *
     * @param includeRemote whether to include remote branches
     * @return result containing branch list
     */
    @AgentAction(
        name = "list_branches",
        description = "Lists all local and optionally remote branches"
    )
    FNLResult<List<BranchInfo>> listBranches(boolean includeRemote);

    /**
     * Gets the commit log.
     *
     * @param maxCount maximum number of commits to return
     * @param branch optional branch name (null for current)
     * @return result containing commit history
     */
    @AgentAction(
        name = "log",
        description = "Shows commit history for the current or specified branch"
    )
    FNLResult<List<CommitInfo>> log(int maxCount, String branch);

    /**
     * Shows the diff of uncommitted changes.
     *
     * @param staged whether to show staged changes (true) or unstaged (false)
     * @return result containing diff output
     */
    @AgentAction(
        name = "diff",
        description = "Shows changes not yet staged (staged=false) or staged changes (staged=true)"
    )
    FNLResult<String> diff(boolean staged);

    // ==================== Staging Operations ====================

    /**
     * Stages a file for commit.
     *
     * @param path the file path to stage
     * @return result indicating success
     */
    @AgentAction(
        name = "stage",
        description = "Stages a file for the next commit (git add)"
    )
    @AuditLog(severity = "INFO", category = "GIT_STAGE")
    FNLResult<Void> stage(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path
    );

    /**
     * Stages all modified and new files.
     *
     * @return result indicating success
     */
    @AgentAction(
        name = "stage_all",
        description = "Stages all modified and new files (git add -A)"
    )
    @AuditLog(severity = "INFO", category = "GIT_STAGE")
    FNLResult<Void> stageAll();

    /**
     * Unstages a file.
     *
     * @param path the file path to unstage
     * @return result indicating success
     */
    @AgentAction(
        name = "unstage",
        description = "Removes a file from staging area (git reset HEAD)"
    )
    @AuditLog(severity = "INFO", category = "GIT_UNSTAGE")
    FNLResult<Void> unstage(
        @Sanitized(strategy = Sanitized.SanitizationStrategy.PATH) String path
    );

    // ==================== Commit Operations (Saga-Enabled) ====================

    /**
     * Creates a commit with staged changes.
     *
     * <p>Returns compensation instruction to reset to previous HEAD.</p>
     *
     * @param message the commit message
     * @return result containing commit info with compensation
     */
    @AgentAction(
        name = "commit",
        description = "Creates a commit with staged changes. Supports automatic rollback."
    )
    @AuditLog(severity = "INFO", category = "GIT_COMMIT")
    FNLResult<CommitInfo> commit(String message);

    /**
     * Amends the last commit.
     *
     * <p>Compensation restores original commit.</p>
     *
     * @param message new commit message (null to keep original)
     * @return result containing amended commit info
     */
    @AgentAction(
        name = "amend",
        description = "Amends the last commit. Use with caution on pushed commits."
    )
    @RequiresRole("git:amend")
    @AuditLog(severity = "WARN", category = "GIT_AMEND")
    FNLResult<CommitInfo> amend(String message);

    // ==================== Branch Operations (Saga-Enabled) ====================

    /**
     * Creates a new branch.
     *
     * @param name the branch name
     * @param checkout whether to checkout the new branch
     * @return result indicating success with compensation to delete branch
     */
    @AgentAction(
        name = "create_branch",
        description = "Creates a new branch, optionally checking it out"
    )
    @AuditLog(severity = "INFO", category = "GIT_BRANCH")
    FNLResult<Void> createBranch(String name, boolean checkout);

    /**
     * Switches to a branch.
     *
     * @param name the branch name to checkout
     * @return result with compensation to return to previous branch
     */
    @AgentAction(
        name = "checkout",
        description = "Switches to the specified branch"
    )
    @AuditLog(severity = "INFO", category = "GIT_CHECKOUT")
    FNLResult<Void> checkout(String name);

    /**
     * Deletes a branch.
     *
     * @param name the branch name to delete
     * @param force whether to force delete (even if not merged)
     * @return result with compensation to recreate branch
     */
    @AgentAction(
        name = "delete_branch",
        description = "Deletes a branch. Use force=true for unmerged branches."
    )
    @RequiresRole("git:delete")
    @AuditLog(severity = "WARN", category = "GIT_DELETE_BRANCH")
    FNLResult<Void> deleteBranch(String name, boolean force);

    /**
     * Merges a branch into current branch.
     *
     * @param branch the branch to merge
     * @param noFastForward whether to force a merge commit
     * @return result containing merge info with compensation
     */
    @AgentAction(
        name = "merge",
        description = "Merges specified branch into current branch"
    )
    @AuditLog(severity = "INFO", category = "GIT_MERGE")
    FNLResult<MergeResult> merge(String branch, boolean noFastForward);

    // ==================== Remote Operations ====================

    /**
     * Fetches updates from remote.
     *
     * @param remote the remote name (e.g., "origin")
     * @return result indicating success
     */
    @AgentAction(
        name = "fetch",
        description = "Downloads objects and refs from remote repository"
    )
    FNLResult<Void> fetch(String remote);

    /**
     * Pulls changes from remote.
     *
     * @param remote the remote name
     * @param branch the branch name
     * @return result with compensation to reset to previous state
     */
    @AgentAction(
        name = "pull",
        description = "Fetches and integrates changes from remote"
    )
    @AuditLog(severity = "INFO", category = "GIT_PULL")
    FNLResult<Void> pull(String remote, String branch);

    /**
     * Pushes commits to remote.
     *
     * @param remote the remote name
     * @param branch the branch name
     * @param force whether to force push (dangerous!)
     * @return result indicating success
     */
    @AgentAction(
        name = "push",
        description = "Uploads local commits to remote. force=true is dangerous!"
    )
    @RequiresRole("git:push")
    @AuditLog(severity = "WARN", category = "GIT_PUSH")
    FNLResult<Void> push(String remote, String branch, boolean force);

    // ==================== Stash Operations ====================

    /**
     * Stashes uncommitted changes.
     *
     * @param message optional stash message
     * @return result with stash reference
     */
    @AgentAction(
        name = "stash",
        description = "Saves uncommitted changes to stash"
    )
    @AuditLog(severity = "INFO", category = "GIT_STASH")
    FNLResult<String> stash(String message);

    /**
     * Applies and removes the latest stash.
     *
     * @return result indicating success
     */
    @AgentAction(
        name = "stash_pop",
        description = "Applies and removes the latest stash"
    )
    @AuditLog(severity = "INFO", category = "GIT_STASH")
    FNLResult<Void> stashPop();

    // ==================== Reset Operations ====================

    /**
     * Resets to a specific commit.
     *
     * @param ref the commit reference (SHA, branch, HEAD~n)
     * @param mode reset mode: soft, mixed, or hard
     * @return result with compensation to return to original state
     */
    @AgentAction(
        name = "reset",
        description = "Resets current HEAD to specified state. 'hard' discards changes!"
    )
    @RequiresRole("git:reset")
    @AuditLog(severity = "WARN", category = "GIT_RESET")
    FNLResult<Void> reset(String ref, ResetMode mode);

    // ==================== Nested Types ====================

    /**
     * Repository status information.
     *
     * @param branch current branch name
     * @param ahead commits ahead of upstream
     * @param behind commits behind upstream
     * @param staged files staged for commit
     * @param modified files modified but not staged
     * @param untracked untracked files
     * @param conflicted files with merge conflicts
     */
    record GitStatus(
        String branch,
        int ahead,
        int behind,
        List<String> staged,
        List<String> modified,
        List<String> untracked,
        List<String> conflicted
    ) {
        public boolean isClean() {
            return staged.isEmpty() && modified.isEmpty() && untracked.isEmpty() && conflicted.isEmpty();
        }

        public boolean hasConflicts() {
            return !conflicted.isEmpty();
        }
    }

    /**
     * Commit information.
     *
     * @param sha the commit SHA
     * @param shortSha abbreviated SHA (7 chars)
     * @param message commit message
     * @param author author name
     * @param authorEmail author email
     * @param timestamp commit timestamp
     */
    record CommitInfo(
        String sha,
        String shortSha,
        String message,
        String author,
        String authorEmail,
        Instant timestamp
    ) {}

    /**
     * Branch information.
     *
     * @param name branch name
     * @param isRemote whether this is a remote branch
     * @param isCurrent whether this is the current branch
     * @param lastCommit last commit SHA
     * @param upstream upstream tracking branch
     */
    record BranchInfo(
        String name,
        boolean isRemote,
        boolean isCurrent,
        String lastCommit,
        String upstream
    ) {}

    /**
     * Merge operation result.
     *
     * @param success whether merge completed successfully
     * @param mergeCommit the merge commit SHA (if created)
     * @param fastForward whether it was a fast-forward merge
     * @param conflicts list of conflicted files (if any)
     */
    record MergeResult(
        boolean success,
        String mergeCommit,
        boolean fastForward,
        List<String> conflicts
    ) {}

    /**
     * Reset modes.
     */
    enum ResetMode {
        /** Keep changes staged */
        SOFT,
        /** Unstage changes but keep in working tree */
        MIXED,
        /** Discard all changes (dangerous!) */
        HARD
    }
}
