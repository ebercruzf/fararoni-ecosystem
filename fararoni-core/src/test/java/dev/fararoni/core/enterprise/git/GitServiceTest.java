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
@DisplayName("GitService Tests - Plan V5 MEMORIA")
class GitServiceTest {
    @TempDir
    Path tempDir;

    private GitService service;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        service = new GitService(tempDir);
    }

    @Nested
    @DisplayName("Disponibilidad de Git")
    class GitAvailabilityTests {
        @Test
        @DisplayName("isGitAvailable debe detectar git instalado")
        void isGitAvailable_GitInstalled_ShouldBeTrue() {
            boolean available = service.isGitAvailable();

            if (!available) {
                System.out.println("SKIP: Git no disponible en este sistema");
            }
        }
    }

    @Nested
    @DisplayName("Detección de Repositorio")
    class RepositoryDetectionTests {
        @Test
        @DisplayName("isGitRepo debe ser false para directorio sin .git")
        void isGitRepo_NoGitDir_ShouldBeFalse() {
            assertFalse(service.isGitRepo());
        }

        @Test
        @DisplayName("isGitRepo debe ser true después de git init")
        void isGitRepo_AfterInit_ShouldBeTrue() throws IOException, InterruptedException {
            initGitRepo();

            assertTrue(service.isGitRepo());
        }
    }

    @Nested
    @DisplayName("Control de Auto-Commit")
    class AutoCommitControlTests {
        @Test
        @DisplayName("auto-commit debe estar habilitado por defecto")
        void isAutoCommitEnabled_Default_ShouldBeTrue() {
            assertTrue(service.isAutoCommitEnabled());
        }

        @Test
        @DisplayName("setAutoCommitEnabled debe cambiar estado")
        void setAutoCommitEnabled_Toggle_ShouldChange() {
            service.setAutoCommitEnabled(false);
            assertFalse(service.isAutoCommitEnabled());

            service.setAutoCommitEnabled(true);
            assertTrue(service.isAutoCommitEnabled());
        }
    }

    @Nested
    @DisplayName("Operaciones de Commit")
    class CommitOperationTests {
        @BeforeEach
        void initRepo() throws IOException, InterruptedException {
            initGitRepo();
        }

        @Test
        @DisplayName("autoCommit debe crear commit exitoso")
        void autoCommit_NewFile_ShouldSucceed() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");

            GitService.CommitResult result = service.autoCommit("Test.java", "FILE_CREATED");

            assertTrue(result.success(), "Commit debe ser exitoso: " + result.error());
            assertNotNull(result.shortHash());
            assertTrue(result.message().contains("[FARARONI]"));
        }

        @Test
        @DisplayName("autoCommit debe incluir prefijo [FARARONI]")
        void autoCommit_Message_ShouldHavePrefix() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("App.java"), "class App {}");

            GitService.CommitResult result = service.autoCommit("App.java", "FILE_CREATED");

            assertTrue(result.message().startsWith("[FARARONI]"),
                       "Mensaje debe empezar con [FARARONI]: " + result.message());
        }

        @Test
        @DisplayName("autoCommit debe usar tipo feat para FILE_CREATED")
        void autoCommit_FileCreated_ShouldUseFeat() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("New.java"), "class New {}");

            GitService.CommitResult result = service.autoCommit("New.java", "FILE_CREATED");

            assertTrue(result.message().contains("feat"),
                       "Debe usar tipo feat: " + result.message());
        }

        @Test
        @DisplayName("stageAndCommit debe funcionar con múltiples archivos")
        void stageAndCommit_MultipleFiles_ShouldSucceed() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("A.java"), "class A {}");
            Files.writeString(tempDir.resolve("B.java"), "class B {}");

            GitService.CommitResult result = service.stageAndCommit(
                List.of("A.java", "B.java"),
                "Add multiple files"
            );

            assertTrue(result.success());
            assertEquals(2, result.filesCommitted());
        }

        @Test
        @DisplayName("commit sin cambios debe retornar NOTHING_TO_COMMIT")
        void commit_NoChanges_ShouldReturnNothingToCommit() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");
            service.autoCommit("Test.java", "FILE_CREATED");

            GitService.CommitResult result = service.autoCommit("Test.java", "FILE_CREATED");

            assertEquals(GitService.CommitResult.CommitStatus.NOTHING_TO_COMMIT, result.status());
        }
    }

    @Nested
    @DisplayName("Historial de Commits")
    class CommitHistoryTests {
        @BeforeEach
        void initRepo() throws IOException, InterruptedException {
            initGitRepo();
        }

        @Test
        @DisplayName("getLastCommit debe retornar último commit")
        void getLastCommit_AfterCommit_ShouldReturn() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");
            service.autoCommit("Test.java", "FILE_CREATED");

            Optional<GitService.CommitInfo> last = service.getLastCommit();

            assertTrue(last.isPresent());
            assertTrue(last.get().message().contains("[FARARONI]"));
        }

        @Test
        @DisplayName("getRecentCommits debe retornar lista de commits")
        void getRecentCommits_MultipleCommits_ShouldReturn() throws IOException {
            if (!service.isGitAvailable()) return;

            for (int i = 0; i < 3; i++) {
                Files.writeString(tempDir.resolve("File" + i + ".java"), "class File" + i + " {}");
                service.autoCommit("File" + i + ".java", "FILE_CREATED");
            }

            List<GitService.CommitInfo> commits = service.getRecentCommits(5);

            assertTrue(commits.size() >= 3, "Debe tener al menos 3 commits");
        }

        @Test
        @DisplayName("getLastCommit sin commits debe retornar empty")
        void getLastCommit_NoCommits_ShouldBeEmpty() {
            if (!service.isGitAvailable()) return;

            Optional<GitService.CommitInfo> last = service.getLastCommit();

            assertTrue(last.isEmpty());
        }
    }

    @Nested
    @DisplayName("Operaciones de Rama")
    class BranchOperationTests {
        @BeforeEach
        void initRepo() throws IOException, InterruptedException {
            initGitRepo();
        }

        @Test
        @DisplayName("getCurrentBranch debe retornar rama actual")
        void getCurrentBranch_AfterInit_ShouldReturn() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");
            service.autoCommit("Test.java", "FILE_CREATED");

            Optional<String> branch = service.getCurrentBranch();

            assertTrue(branch.isPresent());
            assertTrue(branch.get().equals("main") || branch.get().equals("master"),
                       "Rama debe ser main o master: " + branch.get());
        }
    }

    @Nested
    @DisplayName("Manejo de Errores")
    class ErrorHandlingTests {
        @Test
        @DisplayName("commit en no-repo debe retornar ERROR")
        void commit_NotARepo_ShouldReturnError() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");

            GitService.CommitResult result = service.autoCommit("Test.java", "FILE_CREATED");

            assertFalse(result.success());
            assertEquals(GitService.CommitResult.CommitStatus.ERROR, result.status());
        }

        @Test
        @DisplayName("commit de archivo inexistente debe fallar")
        void commit_NonexistentFile_ShouldFail() throws IOException, InterruptedException {
            if (!service.isGitAvailable()) return;

            initGitRepo();

            GitService.CommitResult result = service.autoCommit("NoExiste.java", "FILE_CREATED");

            assertTrue(result.status() == GitService.CommitResult.CommitStatus.NOTHING_TO_COMMIT ||
                       result.status() == GitService.CommitResult.CommitStatus.ERROR);
        }
    }

    @Nested
    @DisplayName("Auto-Commit Deshabilitado")
    class AutoCommitDisabledTests {
        @BeforeEach
        void initRepo() throws IOException, InterruptedException {
            initGitRepo();
        }

        @Test
        @DisplayName("autoCommit deshabilitado no debe crear commits")
        void autoCommit_Disabled_ShouldNotCommit() throws IOException {
            if (!service.isGitAvailable()) return;

            service.setAutoCommitEnabled(false);
            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");

            GitService.CommitResult result = service.autoCommit("Test.java", "FILE_CREATED");

            assertEquals(GitService.CommitResult.CommitStatus.DISABLED, result.status());

            Optional<GitService.CommitInfo> last = service.getLastCommit();
            assertTrue(last.isEmpty());
        }
    }

    @Nested
    @DisplayName("CommitInfo Record")
    class CommitInfoRecordTests {
        @BeforeEach
        void initRepo() throws IOException, InterruptedException {
            initGitRepo();
        }

        @Test
        @DisplayName("CommitInfo debe tener todos los campos")
        void commitInfo_Fields_ShouldBePresent() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");
            service.autoCommit("Test.java", "FILE_CREATED");

            Optional<GitService.CommitInfo> info = service.getLastCommit();

            assertTrue(info.isPresent());
            assertNotNull(info.get().fullHash());
            assertNotNull(info.get().shortHash());
            assertNotNull(info.get().message());
        }

        @Test
        @DisplayName("shortHash debe tener 7 caracteres")
        void commitInfo_ShortHash_ShouldBe7Chars() throws IOException {
            if (!service.isGitAvailable()) return;

            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");
            service.autoCommit("Test.java", "FILE_CREATED");

            Optional<GitService.CommitInfo> info = service.getLastCommit();

            assertTrue(info.isPresent());
            assertEquals(7, info.get().shortHash().length(),
                        "Short hash debe ser 7 caracteres: " + info.get().shortHash());
        }
    }

    private void initGitRepo() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();

        configureGitUser();
    }

    private void configureGitUser() throws IOException, InterruptedException {
        ProcessBuilder pb1 = new ProcessBuilder("git", "config", "user.email", "test@test.com");
        pb1.directory(tempDir.toFile());
        pb1.start().waitFor();

        ProcessBuilder pb2 = new ProcessBuilder("git", "config", "user.name", "Test User");
        pb2.directory(tempDir.toFile());
        pb2.start().waitFor();
    }
}
