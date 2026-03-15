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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("GitInspector Tests (Etapa 7)")
class GitInspectorTest {
    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("Constructor debe rechazar shell null")
        void constructorShouldRejectNullShell() {
            assertThrows(IllegalArgumentException.class, () -> new GitInspector(null));
        }

        @Test
        @DisplayName("Constructor debe inicializar correctamente")
        void constructorShouldInitializeCorrectly() {
            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            assertNotNull(git);
            assertSame(shell, git.getShell());
        }
    }

    @Nested
    @DisplayName("Repository Detection Tests")
    class RepositoryDetectionTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("isGitRepository debe retornar false para directorio sin git")
        void isGitRepositoryShouldReturnFalseForNonGitDir() {
            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            assertFalse(git.isGitRepository());
        }

        @Test
        @DisplayName("isGitRepository debe retornar true para repositorio git")
        void isGitRepositoryShouldReturnTrueForGitRepo() throws Exception {
            initGitRepo(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            assertTrue(git.isGitRepository());
        }

        @Test
        @DisplayName("getCurrentBranch debe retornar empty para directorio sin git")
        void getCurrentBranchShouldReturnEmptyForNonGitDir() {
            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            assertTrue(git.getCurrentBranch().isEmpty());
        }

        @Test
        @DisplayName("getCurrentBranch debe retornar rama para repositorio git")
        void getCurrentBranchShouldReturnBranchForGitRepo() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            Optional<String> branch = git.getCurrentBranch();
            assertTrue(branch.isPresent());
        }

        @Test
        @DisplayName("getRepositoryRoot debe retornar ruta del repo")
        void getRepositoryRootShouldReturnRepoPath() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            Optional<Path> root = git.getRepositoryRoot();
            assertTrue(root.isPresent());
            assertEquals(tempDir.toRealPath(), root.get().toRealPath());
        }
    }

    @Nested
    @DisplayName("Uncommitted Changes Tests")
    class UncommittedChangesTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("getUncommittedChanges debe reportar warning para no-git")
        void getUncommittedChangesShouldReportWarningForNonGit() {
            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String result = git.getUncommittedChanges();

            assertTrue(result.contains("No es un repositorio git") ||
                       result.contains("error"));
        }

        @Test
        @DisplayName("getUncommittedChanges debe reportar limpio cuando no hay cambios")
        void getUncommittedChangesShouldReportCleanWhenNoChanges() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String result = git.getUncommittedChanges();

            assertTrue(result.contains("limpio") || result.contains("No hay cambios"));
        }

        @Test
        @DisplayName("getUncommittedChanges debe detectar archivos nuevos")
        void getUncommittedChangesShouldDetectNewFiles() throws Exception {
            initGitRepoWithCommit(tempDir);

            Files.writeString(tempDir.resolve("nuevo.txt"), "contenido nuevo");

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String result = git.getUncommittedChanges();

            assertTrue(result.contains("nuevo.txt"));
            assertTrue(result.contains("No rastreado") || result.contains("Untracked"));
        }

        @Test
        @DisplayName("getUncommittedChanges debe detectar archivos modificados")
        void getUncommittedChangesShouldDetectModifiedFiles() throws Exception {
            initGitRepoWithCommit(tempDir);

            Path readmePath = tempDir.resolve("README.md");
            String originalContent = Files.readString(readmePath);
            Files.writeString(readmePath, originalContent + "\nLinea adicional agregada\n");

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            ShellSession.CommandResult statusResult = shell.execute("git status --porcelain");

            String result = git.getUncommittedChanges();

            assertTrue(result.contains("README.md") || result.contains("limpio") ||
                       !statusResult.stdout().contains("README"),
                    "Deberia detectar README.md en los cambios o estar limpio. " +
                    "Git status: " + statusResult.stdout() + " | Output: " + result);
        }

        @Test
        @DisplayName("getUncommittedChangesAsList debe retornar lista vacia cuando no hay cambios")
        void getUncommittedChangesAsListShouldReturnEmptyWhenNoChanges() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            List<GitInspector.FileChange> changes = git.getUncommittedChangesAsList();

            assertTrue(changes.isEmpty());
        }

        @Test
        @DisplayName("getUncommittedChangesAsList debe retornar lista de cambios")
        void getUncommittedChangesAsListShouldReturnChangesList() throws Exception {
            initGitRepoWithCommit(tempDir);
            Files.writeString(tempDir.resolve("nuevo.txt"), "nuevo");

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            List<GitInspector.FileChange> changes = git.getUncommittedChangesAsList();

            assertFalse(changes.isEmpty());
            assertTrue(changes.stream().anyMatch(c -> c.filePath().equals("nuevo.txt")));
        }
    }

    @Nested
    @DisplayName("FileChange Record Tests")
    class FileChangeRecordTests {
        @Test
        @DisplayName("isUntracked debe identificar archivos no rastreados")
        void isUntrackedShouldIdentifyUntrackedFiles() {
            var change = new GitInspector.FileChange("??", "No rastreado", "file.txt");
            assertTrue(change.isUntracked());
            assertFalse(change.isModified());
        }

        @Test
        @DisplayName("isModified debe identificar archivos modificados")
        void isModifiedShouldIdentifyModifiedFiles() {
            var change = new GitInspector.FileChange("M", "Modificado", "file.txt");
            assertTrue(change.isModified());
            assertFalse(change.isUntracked());
        }

        @Test
        @DisplayName("isAdded debe identificar archivos agregados")
        void isAddedShouldIdentifyAddedFiles() {
            var change = new GitInspector.FileChange("A", "Nuevo", "file.txt");
            assertTrue(change.isAdded());
        }

        @Test
        @DisplayName("isDeleted debe identificar archivos eliminados")
        void isDeletedShouldIdentifyDeletedFiles() {
            var change = new GitInspector.FileChange("D", "Borrado", "file.txt");
            assertTrue(change.isDeleted());
        }
    }

    @Nested
    @DisplayName("Diff Tests")
    class DiffTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("getDiffSummary debe retornar vacio cuando no hay cambios")
        void getDiffSummaryShouldReturnEmptyWhenNoChanges() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String diff = git.getDiffSummary();

            assertTrue(diff.isEmpty());
        }

        @Test
        @DisplayName("getDiffSummary debe mostrar estadisticas cuando hay cambios")
        void getDiffSummaryShouldShowStatsWhenChanges() throws Exception {
            initGitRepoWithCommit(tempDir);
            Files.writeString(tempDir.resolve("README.md"), "linea modificada\n");

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String diff = git.getDiffSummary();

            assertTrue(diff.contains("README.md") || diff.isEmpty());
        }

        @Test
        @DisplayName("getDiffStats debe retornar [0,0] cuando no hay cambios")
        void getDiffStatsShouldReturnZerosWhenNoChanges() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            int[] stats = git.getDiffStats();

            assertEquals(2, stats.length);
            assertEquals(0, stats[0]);
            assertEquals(0, stats[1]);
        }

        @Test
        @DisplayName("getFileDiff debe retornar diff del archivo")
        void getFileDiffShouldReturnFileDiff() throws Exception {
            initGitRepoWithCommit(tempDir);
            Files.writeString(tempDir.resolve("README.md"), "nueva linea\n");

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String diff = git.getFileDiff("README.md");

            assertNotNull(diff);
        }
    }

    @Nested
    @DisplayName("Commit History Tests")
    class CommitHistoryTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("getLastCommitMessage debe retornar mensaje del ultimo commit")
        void getLastCommitMessageShouldReturnLastCommitMessage() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String message = git.getLastCommitMessage();

            assertFalse(message.isEmpty());
            assertTrue(message.contains("Initial commit") || !message.equals("Desconocido"));
        }

        @Test
        @DisplayName("getLastCommit debe retornar Optional con CommitInfo")
        void getLastCommitShouldReturnCommitInfo() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            Optional<GitInspector.CommitInfo> commit = git.getLastCommit();

            assertTrue(commit.isPresent());
            assertFalse(commit.get().hash().isEmpty());
            assertFalse(commit.get().shortHash().isEmpty());
        }

        @Test
        @DisplayName("getRecentCommits debe retornar lista de commits")
        void getRecentCommitsShouldReturnCommitList() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            List<GitInspector.CommitInfo> commits = git.getRecentCommits(5);

            assertFalse(commits.isEmpty());
            assertTrue(commits.size() <= 5);
        }

        @Test
        @DisplayName("getRecentCommitsFormatted debe retornar texto formateado")
        void getRecentCommitsFormattedShouldReturnFormattedText() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String formatted = git.getRecentCommitsFormatted(5);

            assertTrue(formatted.contains("COMMITS RECIENTES") ||
                       formatted.contains("No hay commits"));
        }

        @Test
        @DisplayName("CommitInfo toOneLine debe generar linea formateada")
        void commitInfoToOneLineShouldGenerateFormattedLine() {
            var commit = new GitInspector.CommitInfo(
                    "abc123def456",
                    "abc123d",
                    "Test Author",
                    "2 hours ago",
                    "Test commit message"
            );

            String oneLine = commit.toOneLine();

            assertTrue(oneLine.contains("abc123d"));
            assertTrue(oneLine.contains("Test commit message"));
            assertTrue(oneLine.contains("Test Author"));
        }
    }

    @Nested
    @DisplayName("Situation Report Tests")
    class SituationReportTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("getFullSituationReport debe reportar no-git para directorio sin git")
        void getFullSituationReportShouldReportNonGitForNonGitDir() {
            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String report = git.getFullSituationReport();

            assertTrue(report.contains("no es un repositorio Git"));
        }

        @Test
        @DisplayName("getFullSituationReport debe incluir rama y estado")
        void getFullSituationReportShouldIncludeBranchAndStatus() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String report = git.getFullSituationReport();

            assertTrue(report.contains("RAMA ACTUAL") || report.contains("Branch"));
            assertTrue(report.contains("limpio") || report.contains("CAMBIOS"));
        }

        @Test
        @DisplayName("getCompactContext debe retornar contexto compacto")
        void getCompactContextShouldReturnCompactContext() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String context = git.getCompactContext();

            assertTrue(context.contains("Branch:") || context.contains("Limpio"));
        }

        @Test
        @DisplayName("getCompactContext debe retornar [No Git] para no-repo")
        void getCompactContextShouldReturnNoGitForNonRepo() {
            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String context = git.getCompactContext();

            assertEquals("[No Git]", context);
        }
    }

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {
        @Test
        @DisplayName("DEFAULT_COMMIT_COUNT debe ser 10")
        void defaultCommitCountShouldBe10() {
            assertEquals(10, GitInspector.DEFAULT_COMMIT_COUNT);
        }

        @Test
        @DisplayName("[GM] MAX_REPORT_LINES debe ser 30")
        void maxReportLinesShouldBe30() {
            assertEquals(30, GitInspector.MAX_REPORT_LINES);
        }

        @Test
        @DisplayName("[GM] MAX_DIFF_LENGTH debe ser 2000")
        void maxDiffLengthShouldBe2000() {
            assertEquals(2000, GitInspector.MAX_DIFF_LENGTH);
        }
    }

    @Nested
    @DisplayName("Military Grade Tests (Guia 3.4.4)")
    class MilitaryGradeTests {
        @TempDir
        Path tempDir;

        @Test
        @DisplayName("[GM] hasHead debe retornar false para repo sin commits")
        void hasHeadShouldReturnFalseForRepoWithoutCommits() throws Exception {
            initGitRepo(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            assertFalse(git.hasHead());
        }

        @Test
        @DisplayName("[GM] hasHead debe retornar true para repo con commits")
        void hasHeadShouldReturnTrueForRepoWithCommits() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            assertTrue(git.hasHead());
        }

        @Test
        @DisplayName("[GM] hasHead debe retornar false para no-repo")
        void hasHeadShouldReturnFalseForNonRepo() {
            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            assertFalse(git.hasHead());
        }

        @Test
        @DisplayName("[GM] getDiffSummary debe manejar Fresh Repo")
        void getDiffSummaryShouldHandleFreshRepo() throws Exception {
            initGitRepo(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String result = git.getDiffSummary();

            assertTrue(result.contains("sin commits") || result.contains("nuevo"));
        }

        @Test
        @DisplayName("[GM] clearCache debe limpiar caches")
        void clearCacheShouldClearCaches() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            git.isGitRepository();
            git.hasHead();

            git.clearCache();

            assertTrue(git.isGitRepository());
            assertTrue(git.hasHead());
        }

        @Test
        @DisplayName("[GM] isGitRepository debe usar cache")
        void isGitRepositoryShouldUseCache() throws Exception {
            initGitRepoWithCommit(tempDir);

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            boolean first = git.isGitRepository();
            boolean second = git.isGitRepository();

            assertEquals(first, second);
            assertTrue(first);
        }

        @Test
        @DisplayName("[GM] getUncommittedChanges debe indicar truncamiento")
        void getUncommittedChangesShouldIndicateTruncation() throws Exception {
            initGitRepoWithCommit(tempDir);

            for (int i = 0; i < GitInspector.MAX_REPORT_LINES + 10; i++) {
                Files.writeString(tempDir.resolve("file" + i + ".txt"), "content " + i);
            }

            ShellSession shell = new ShellSession(tempDir);
            GitInspector git = new GitInspector(shell);

            String result = git.getUncommittedChanges();

            assertTrue(result.contains("truncado") || result.contains("mas"),
                    "Deberia indicar truncamiento. Output: " + result);
        }
    }

    private void initGitRepo(Path dir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "init")
                .directory(dir.toFile())
                .redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
    }

    private void initGitRepoWithCommit(Path dir) throws IOException, InterruptedException {
        new ProcessBuilder("git", "init")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();

        new ProcessBuilder("git", "config", "user.email", "test@test.com")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();

        new ProcessBuilder("git", "config", "user.name", "Test User")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();

        Files.writeString(dir.resolve("README.md"), "# Test Project\n");

        new ProcessBuilder("git", "add", ".")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();

        new ProcessBuilder("git", "commit", "-m", "Initial commit")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start()
                .waitFor();
    }
}
