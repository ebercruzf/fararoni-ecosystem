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
package dev.fararoni.core.core.commands;

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.bus.agent.api.interaction.AgentUserInterface;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("GitSubcommand")
class GitSubcommandTest {
    private GitSubcommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new GitSubcommand();
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /git")
        void getTrigger_ReturnsGit() {
            assertEquals("/git", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es GIT")
        void getCategory_ReturnsGit() {
            assertEquals(CommandCategory.GIT, command.getCategory());
        }

        @Test
        @DisplayName("no tiene aliases")
        void getAliases_ReturnsEmpty() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(0, aliases.length);
        }

        @Test
        @DisplayName("usage contiene subcomando")
        void getUsage_ContainsSubcommand() {
            String usage = command.getUsage();
            assertTrue(usage.contains("subcomando") || usage.contains("argumentos"));
        }

        @Test
        @DisplayName("description menciona git")
        void getDescription_MentionsGit() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("git"));
        }

        @Test
        @DisplayName("extendedHelp documenta subcomandos")
        void getExtendedHelp_DocumentsSubcommands() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("init"));
            assertTrue(help.contains("checkout"));
            assertTrue(help.contains("branch"));
            assertTrue(help.contains("status"));
            assertTrue(help.contains("log"));
        }
    }

    @Nested
    @DisplayName("Sin Argumentos")
    class NoArgsTests {
        @Test
        @DisplayName("sin argumentos muestra subcomandos")
        void execute_NoArgs_ShowsSubcommands() {
            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("init") || m.contains("checkout")));
        }

        @Test
        @DisplayName("string vacio muestra subcomandos")
        void execute_EmptyString_ShowsSubcommands() {
            command.execute("", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Subcomandos") || m.contains("disponibles")));
        }
    }

    @Nested
    @DisplayName("Subcomando Init")
    class InitTests {
        @Test
        @DisplayName("init crea repositorio git")
        void execute_Init_CreatesRepo() {
            command.execute("init", mockContext);

            assertTrue(Files.exists(tempDir.resolve(".git")));
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("init en repo existente muestra warning")
        void execute_InitExisting_ShowsWarning() throws IOException {
            Files.createDirectory(tempDir.resolve(".git"));

            command.execute("init", mockContext);

            assertTrue(mockContext.hasWarning());
        }
    }

    @Nested
    @DisplayName("Subcomando Status")
    class StatusTests {
        @Test
        @DisplayName("status en no-repo muestra error")
        void execute_StatusNoRepo_ShowsError() {
            command.execute("status", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("status en repo muestra estado")
        void execute_StatusInRepo_ShowsStatus() throws IOException {
            initGitRepo();

            command.execute("status", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Estado") || m.contains("branch")));
        }
    }

    @Nested
    @DisplayName("Subcomando Branch")
    class BranchTests {
        @Test
        @DisplayName("branch en no-repo muestra error")
        void execute_BranchNoRepo_ShowsError() {
            command.execute("branch", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("branch lista branches en repo")
        void execute_BranchInRepo_ListsBranches() throws IOException {
            initGitRepo();

            command.execute("branch", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Branches") || m.contains("main") || m.contains("master")));
        }
    }

    @Nested
    @DisplayName("Subcomando Checkout")
    class CheckoutTests {
        @Test
        @DisplayName("checkout sin args muestra error")
        void execute_CheckoutNoArgs_ShowsError() throws IOException {
            initGitRepo();

            command.execute("checkout", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("checkout en no-repo muestra error")
        void execute_CheckoutNoRepo_ShowsError() {
            command.execute("checkout main", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Subcomando Log")
    class LogTests {
        @Test
        @DisplayName("log en no-repo muestra error")
        void execute_LogNoRepo_ShowsError() {
            command.execute("log", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("log en repo muestra commits")
        void execute_LogInRepo_ShowsCommits() throws IOException {
            initGitRepoWithCommit();

            command.execute("log", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("commits") || m.contains("Ultimos")));
        }

        @Test
        @DisplayName("log -n limita commits")
        void execute_LogWithLimit_RespectsLimit() throws IOException {
            initGitRepoWithCommit();

            command.execute("log -n 5", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("5") || m.contains("commits")));
        }
    }

    @Nested
    @DisplayName("Subcomando Stash")
    class StashTests {
        @Test
        @DisplayName("stash en no-repo muestra error")
        void execute_StashNoRepo_ShowsError() {
            command.execute("stash", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("stash list en repo vacio")
        void execute_StashListEmpty_Works() throws IOException {
            initGitRepo();

            command.execute("stash list", mockContext);

            assertFalse(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Subcomando Desconocido")
    class UnknownSubcommandTests {
        @Test
        @DisplayName("subcomando desconocido muestra error")
        void execute_Unknown_ShowsError() {
            command.execute("unknown", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    private void initGitRepo() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempDir.toFile());
        try {
            pb.start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void initGitRepoWithCommit() throws IOException {
        initGitRepo();
        Files.writeString(tempDir.resolve("test.txt"), "test");
        ProcessBuilder add = new ProcessBuilder("git", "add", ".");
        add.directory(tempDir.toFile());
        ProcessBuilder commit = new ProcessBuilder("git", "commit", "-m", "Initial");
        commit.directory(tempDir.toFile());
        try {
            add.start().waitFor();
            commit.start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class MockExecutionContext implements ExecutionContext {
        private final Path workingDirectory;
        private final List<String> messages = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        MockExecutionContext(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        List<String> getMessages() { return messages; }
        List<String> getSuccesses() { return successes; }
        List<String> getWarnings() { return warnings; }
        List<String> getErrors() { return errors; }

        boolean hasError() { return !errors.isEmpty(); }
        boolean hasSuccess() { return !successes.isEmpty(); }
        boolean hasWarning() { return !warnings.isEmpty(); }

        @Override public void print(String message) { messages.add(message); }
        @Override public void printSuccess(String message) { successes.add(message); }
        @Override public void printWarning(String message) { warnings.add(message); }
        @Override public void printError(String message) { errors.add(message); }
        @Override public void printDebug(String message) { }
        @Override public void addToContext(String content) { }
        @Override public void addToSystemContext(String content) { }
        @Override public Path getWorkingDirectory() { return workingDirectory; }
        @Override public Optional<Path> getProjectRoot() { return Optional.of(workingDirectory); }
        @Override public boolean isDebugMode() { return false; }
        @Override public boolean isGitRepository() { return Files.exists(workingDirectory.resolve(".git")); }
        @Override public Optional<String> getCurrentBranch() { return Optional.of("main"); }
        @Override public <T> T withFileService(Function<FileServiceAccessor, T> op) { return null; }
        @Override public void logAudit(String action, String details) { }
        @Override public AgentUserInterface getUI() { return AgentUserInterface.NO_OP; }
    }
}
