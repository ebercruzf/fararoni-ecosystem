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
import dev.fararoni.bus.agent.api.ToolSkill;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("GitSkill Tests")
class GitSkillTest {
    @TempDir
    Path tempDir;

    private GitSkill skill;

    @BeforeEach
    void setUp() {
        skill = new GitSkill(tempDir);
    }

    @Nested
    @DisplayName("ToolSkill Interface")
    class ToolSkillInterfaceTests {
        @Test
        @DisplayName("Should implement ToolSkill interface")
        void shouldImplementToolSkill() {
            assertThat(skill).isInstanceOf(ToolSkill.class);
        }

        @Test
        @DisplayName("Should return GIT as skill name")
        void shouldReturnGitAsSkillName() {
            assertThat(skill.getSkillName()).isEqualTo("GIT");
        }

        @Test
        @DisplayName("Should have description")
        void shouldHaveDescription() {
            assertThat(skill.getDescription()).isNotBlank();
            assertThat(skill.getDescription()).containsIgnoringCase("git");
        }

        @Test
        @DisplayName("Should report unavailable when not a git repo")
        void shouldReportUnavailableWhenNotGitRepo() {
            assertThat(skill.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should report available after git init")
        void shouldReportAvailableAfterGitInit() throws Exception {
            initGitRepo();

            assertThat(skill.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("AgentAction Annotations")
    class AgentActionAnnotationTests {
        @Test
        @DisplayName("Should have status action")
        void shouldHaveStatusAction() throws Exception {
            Method method = GitSkill.class.getMethod("status");
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("status");
            assertThat(action.description()).isNotBlank();
        }

        @Test
        @DisplayName("Should have branch action")
        void shouldHaveBranchAction() throws Exception {
            Method method = GitSkill.class.getMethod("branch");
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("branch");
        }

        @Test
        @DisplayName("Should have log action")
        void shouldHaveLogAction() throws Exception {
            Method method = GitSkill.class.getMethod("log", int.class);
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("log");
        }

        @Test
        @DisplayName("Should have diff action")
        void shouldHaveDiffAction() throws Exception {
            Method method = GitSkill.class.getMethod("diff", boolean.class);
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("diff");
        }

        @Test
        @DisplayName("Should have add action")
        void shouldHaveAddAction() throws Exception {
            Method method = GitSkill.class.getMethod("add", String.class);
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("add");
        }

        @Test
        @DisplayName("Should have commit action")
        void shouldHaveCommitAction() throws Exception {
            Method method = GitSkill.class.getMethod("commit", String.class);
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("commit");
        }

        @Test
        @DisplayName("Should have commit_files action")
        void shouldHaveCommitFilesAction() throws Exception {
            Method method = GitSkill.class.getMethod("commitFiles", String.class, String.class);
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("commit_files");
        }

        @Test
        @DisplayName("Should have push action")
        void shouldHavePushAction() throws Exception {
            Method method = GitSkill.class.getMethod("push");
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("push");
        }

        @Test
        @DisplayName("Should have pull action")
        void shouldHavePullAction() throws Exception {
            Method method = GitSkill.class.getMethod("pull");
            AgentAction action = method.getAnnotation(AgentAction.class);

            assertThat(action).isNotNull();
            assertThat(action.name()).isEqualTo("pull");
        }

        @Test
        @DisplayName("Should have at least 8 actions")
        void shouldHaveAtLeast8Actions() {
            long actionCount = Arrays.stream(GitSkill.class.getMethods())
                .filter(m -> m.isAnnotationPresent(AgentAction.class))
                .count();

            assertThat(actionCount).isGreaterThanOrEqualTo(8);
        }
    }

    @Nested
    @DisplayName("status() Action")
    class StatusActionTests {
        @Test
        @DisplayName("Should return error when not a git repo")
        void shouldReturnErrorWhenNotGitRepo() {
            String result = skill.status();

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("not a git repository");
        }

        @Test
        @DisplayName("Should return clean status for new repo")
        void shouldReturnCleanStatusForNewRepo() throws Exception {
            initGitRepo();

            String result = skill.status();

            assertThat(result).containsIgnoringCase("clean");
        }

        @Test
        @DisplayName("Should show modified files")
        void shouldShowModifiedFiles() throws Exception {
            initGitRepo();
            createAndCommitFile("test.txt", "initial");

            Files.writeString(tempDir.resolve("test.txt"), "modified");

            String result = skill.status();

            assertThat(result).contains("test.txt");
        }
    }

    @Nested
    @DisplayName("branch() Action")
    class BranchActionTests {
        @Test
        @DisplayName("Should return error when not a git repo")
        void shouldReturnErrorWhenNotGitRepo() {
            String result = skill.branch();

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should return branch name after commit")
        void shouldReturnBranchNameAfterCommit() throws Exception {
            initGitRepo();
            createAndCommitFile("test.txt", "content");

            String result = skill.branch();

            assertThat(result).isIn("main", "master");
        }
    }

    @Nested
    @DisplayName("log() Action")
    class LogActionTests {
        @Test
        @DisplayName("Should return error when not a git repo")
        void shouldReturnErrorWhenNotGitRepo() {
            String result = skill.log(5);

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should return no commits for empty repo")
        void shouldReturnNoCommitsForEmptyRepo() throws Exception {
            initGitRepo();

            String result = skill.log(5);

            assertThat(result).containsIgnoringCase("no commits");
        }

        @Test
        @DisplayName("Should return commit history")
        void shouldReturnCommitHistory() throws Exception {
            initGitRepo();
            createAndCommitFile("a.txt", "a");
            createAndCommitFile("b.txt", "b");

            String result = skill.log(5);

            assertThat(result).contains("[FARARONI]");
        }

        @Test
        @DisplayName("Should limit count to 20")
        void shouldLimitCountTo20() throws Exception {
            initGitRepo();
            createAndCommitFile("test.txt", "content");

            String result = skill.log(100);

            assertThat(result).doesNotContainIgnoringCase("error");
        }
    }

    @Nested
    @DisplayName("diff() Action")
    class DiffActionTests {
        @Test
        @DisplayName("Should return error when not a git repo")
        void shouldReturnErrorWhenNotGitRepo() {
            String result = skill.diff(false);

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should return no changes for clean repo")
        void shouldReturnNoChangesForCleanRepo() throws Exception {
            initGitRepo();
            createAndCommitFile("test.txt", "content");

            String result = skill.diff(false);

            assertThat(result).containsIgnoringCase("no");
        }

        @Test
        @DisplayName("Should show unstaged changes")
        void shouldShowUnstagedChanges() throws Exception {
            initGitRepo();
            createAndCommitFile("test.txt", "initial");

            Files.writeString(tempDir.resolve("test.txt"), "modified");

            String result = skill.diff(false);

            assertThat(result).contains("modified");
        }

        @Test
        @DisplayName("Should show staged changes")
        void shouldShowStagedChanges() throws Exception {
            initGitRepo();
            createAndCommitFile("test.txt", "initial");

            Files.writeString(tempDir.resolve("test.txt"), "staged change");
            executeGit("add", "test.txt");

            String result = skill.diff(true);

            assertThat(result).contains("staged change");
        }
    }

    @Nested
    @DisplayName("add() Action")
    class AddActionTests {
        @Test
        @DisplayName("Should return error when not a git repo")
        void shouldReturnErrorWhenNotGitRepo() {
            String result = skill.add("file.txt");

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should return error for empty files")
        void shouldReturnErrorForEmptyFiles() throws Exception {
            initGitRepo();

            String result = skill.add("");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("no files");
        }

        @Test
        @DisplayName("Should stage single file")
        void shouldStageSingleFile() throws Exception {
            initGitRepo();
            Files.writeString(tempDir.resolve("new.txt"), "content");

            String result = skill.add("new.txt");

            assertThat(result).containsIgnoringCase("success");
        }

        @Test
        @DisplayName("Should stage multiple files")
        void shouldStageMultipleFiles() throws Exception {
            initGitRepo();
            Files.writeString(tempDir.resolve("a.txt"), "a");
            Files.writeString(tempDir.resolve("b.txt"), "b");

            String result = skill.add("a.txt b.txt");

            assertThat(result).containsIgnoringCase("success");
        }

        @Test
        @DisplayName("Should stage all with dot")
        void shouldStageAllWithDot() throws Exception {
            initGitRepo();
            Files.writeString(tempDir.resolve("x.txt"), "x");
            Files.writeString(tempDir.resolve("y.txt"), "y");

            String result = skill.add(".");

            assertThat(result).containsIgnoringCase("success");
        }
    }

    @Nested
    @DisplayName("commit() Action")
    class CommitActionTests {
        @Test
        @DisplayName("Should return error when not a git repo")
        void shouldReturnErrorWhenNotGitRepo() {
            String result = skill.commit("test message");

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should return error for empty message")
        void shouldReturnErrorForEmptyMessage() throws Exception {
            initGitRepo();

            String result = skill.commit("");

            assertThat(result).containsIgnoringCase("error");
            assertThat(result).containsIgnoringCase("message");
        }

        @Test
        @DisplayName("Should return nothing to commit when no staged files")
        void shouldReturnNothingToCommitWhenNoStagedFiles() throws Exception {
            initGitRepo();
            createAndCommitFile("initial.txt", "content");

            String result = skill.commit("attempt commit");

            assertThat(result).containsIgnoringCase("nothing to commit");
        }

        @Test
        @DisplayName("Should commit staged files with FARARONI prefix")
        void shouldCommitStagedFilesWithPrefix() throws Exception {
            initGitRepo();
            Files.writeString(tempDir.resolve("new.txt"), "content");
            executeGit("add", "new.txt");

            String result = skill.commit("add new file");

            assertThat(result).containsIgnoringCase("success");
            assertThat(result).contains("[FARARONI]");
        }
    }

    @Nested
    @DisplayName("commitFiles() Action")
    class CommitFilesActionTests {
        @Test
        @DisplayName("Should return error when not a git repo")
        void shouldReturnErrorWhenNotGitRepo() {
            String result = skill.commitFiles("file.txt", "message");

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("Should stage and commit files")
        void shouldStageAndCommitFiles() throws Exception {
            initGitRepo();
            Files.writeString(tempDir.resolve("a.txt"), "content a");
            Files.writeString(tempDir.resolve("b.txt"), "content b");

            String result = skill.commitFiles("a.txt b.txt", "add two files");

            assertThat(result).containsIgnoringCase("success");
            assertThat(result).contains("[FARARONI]");
            assertThat(result).contains("2 file");
        }
    }

    @Nested
    @DisplayName("push() and pull() Actions")
    class PushPullActionTests {
        @Test
        @DisplayName("push should return error when not a git repo")
        void pushShouldReturnErrorWhenNotGitRepo() {
            String result = skill.push();

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("pull should return error when not a git repo")
        void pullShouldReturnErrorWhenNotGitRepo() {
            String result = skill.pull();

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("push should handle no remote gracefully")
        void pushShouldHandleNoRemoteGracefully() throws Exception {
            initGitRepo();
            createAndCommitFile("test.txt", "content");

            String result = skill.push();

            assertThat(result).containsIgnoringCase("error");
        }
    }

    private void initGitRepo() throws IOException, InterruptedException {
        executeGit("init");
        executeGit("config", "user.email", "test@test.com");
        executeGit("config", "user.name", "Test User");
    }

    private void createAndCommitFile(String filename, String content) throws IOException, InterruptedException {
        Files.writeString(tempDir.resolve(filename), content);
        executeGit("add", filename);
        executeGit("commit", "-m", "[FARARONI] Add " + filename);
    }

    private void executeGit(String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();
    }
}
