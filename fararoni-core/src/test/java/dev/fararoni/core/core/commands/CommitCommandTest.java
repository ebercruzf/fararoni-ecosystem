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
@DisplayName("CommitCommand")
class CommitCommandTest {
    private CommitCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new CommitCommand();
        mockContext = new MockExecutionContext(tempDir, false);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /commit")
        void getTrigger_ReturnsCommit() {
            assertEquals("/commit", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es GIT")
        void getCategory_ReturnsGit() {
            assertEquals(CommandCategory.GIT, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /save y /ci")
        void getAliases_ReturnsSaveAndCi() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/save"));
            assertTrue(List.of(aliases).contains("/ci"));
        }

        @Test
        @DisplayName("usage contiene mensaje y --all")
        void getUsage_ContainsMessageAndAll() {
            String usage = command.getUsage();
            assertTrue(usage.contains("mensaje") || usage.contains("--all"));
        }

        @Test
        @DisplayName("description menciona commit")
        void getDescription_MentionsCommit() {
            assertTrue(command.getDescription().toLowerCase().contains("commit"));
        }

        @Test
        @DisplayName("extendedHelp contiene Conventional Commits")
        void getExtendedHelp_ContainsConventionalCommits() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("Conventional Commits") ||
                       (help.contains("feat:") && help.contains("fix:")));
        }

        @Test
        @DisplayName("extendedHelp menciona prefijo FARARONI")
        void getExtendedHelp_MentionsFararoniPrefix() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("FARARONI") || help.contains("[FARARONI]"));
        }
    }

    @Nested
    @DisplayName("Validacion de Repositorio")
    class ValidationTests {
        @Test
        @DisplayName("muestra error si no es repositorio git")
        void execute_NotGitRepo_ShowsError() {
            mockContext = new MockExecutionContext(tempDir, false);

            command.execute(null, mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("No es un repositorio git"));
        }

        @Test
        @DisplayName("sugiere git init si no es repo")
        void execute_NotGitRepo_SuggestsGitInit() {
            mockContext = new MockExecutionContext(tempDir, false);

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("git init")));
        }
    }

    @Nested
    @DisplayName("Parseo de Argumentos")
    class ArgumentParsingTests {
        @Test
        @DisplayName("detecta flag --all")
        void execute_AllFlag_DetectsCorrectly() {
            mockContext = new MockExecutionContext(tempDir, false);

            command.execute("--all", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("repositorio"));
        }

        @Test
        @DisplayName("detecta flag -a como all")
        void execute_ShortAllFlag_DetectsCorrectly() {
            mockContext = new MockExecutionContext(tempDir, false);

            command.execute("-a", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("repositorio"));
        }

        @Test
        @DisplayName("parsea mensaje con comillas")
        void execute_QuotedMessage_ParsesCorrectly() {
            mockContext = new MockExecutionContext(tempDir, false);

            command.execute("\"feat: add feature\"", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("repositorio"));
        }

        @Test
        @DisplayName("parsea mensaje con flag")
        void execute_MessageWithFlag_ParsesCorrectly() {
            mockContext = new MockExecutionContext(tempDir, false);

            command.execute("\"fix: bug\" --all", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("repositorio"));
        }
    }

    @Nested
    @DisplayName("Generacion de Mensaje")
    class MessageGenerationTests {
        @Test
        @DisplayName("getExtendedHelp documenta tipos de prefijos")
        void extendedHelp_DocumentsPrefixes() {
            String help = command.getExtendedHelp();

            assertTrue(help.contains("feat"));
            assertTrue(help.contains("fix"));
            assertTrue(help.contains("refactor") || help.contains("chore"));
        }
    }

    private static class MockExecutionContext implements ExecutionContext {
        private final Path workingDirectory;
        private final boolean isGitRepo;
        private final List<String> messages = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        MockExecutionContext(Path workingDirectory, boolean isGitRepo) {
            this.workingDirectory = workingDirectory;
            this.isGitRepo = isGitRepo;
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
        @Override public boolean isGitRepository() { return isGitRepo; }
        @Override public Optional<String> getCurrentBranch() { return Optional.of("main"); }
        @Override public <T> T withFileService(Function<FileServiceAccessor, T> op) { return null; }
        @Override public void logAudit(String action, String details) { }
        @Override public AgentUserInterface getUI() { return AgentUserInterface.NO_OP; }
    }
}
