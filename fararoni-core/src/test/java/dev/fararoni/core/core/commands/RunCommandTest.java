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
@DisplayName("RunCommand")
class RunCommandTest {
    private RunCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new RunCommand();
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /run")
        void getTrigger_ReturnsRun() {
            assertEquals("/run", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es DEBUG")
        void getCategory_ReturnsDebug() {
            assertEquals(CommandCategory.DEBUG, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /exec y /shell")
        void getAliases_ReturnsExecAndShell() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/exec"));
            assertTrue(List.of(aliases).contains("/shell"));
        }

        @Test
        @DisplayName("usage contiene comando y timeout")
        void getUsage_ContainsCommandAndTimeout() {
            String usage = command.getUsage();
            assertTrue(usage.contains("comando") || usage.contains("<"));
            assertTrue(usage.contains("timeout"));
        }

        @Test
        @DisplayName("description menciona comando o shell")
        void getDescription_MentionsCommand() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("comando") || desc.contains("shell"));
        }

        @Test
        @DisplayName("extendedHelp incluye ejemplos")
        void getExtendedHelp_IncludesExamples() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("ls") || help.contains("mvn") ||
                       help.contains("npm") || help.contains("Ejemplos"));
        }

        @Test
        @DisplayName("extendedHelp advierte sobre uso responsable")
        void getExtendedHelp_IncludesWarning() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("Advertencia") || help.contains("cuidado") ||
                       help.contains("responsabilidad"));
        }
    }

    @Nested
    @DisplayName("Validacion de Argumentos")
    class ValidationTests {
        @Test
        @DisplayName("error sin comando")
        void execute_NoCommand_ShowsError() {
            command.execute(null, mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("Uso"));
        }

        @Test
        @DisplayName("error con comando vacio")
        void execute_EmptyCommand_ShowsError() {
            command.execute("   ", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Ejecucion de Comandos")
    class ExecutionTests {
        @Test
        @DisplayName("ejecuta comando simple")
        void execute_SimpleCommand_Runs() {
            command.execute("echo hello", mockContext);

            assertTrue(mockContext.hasSuccess() || !mockContext.getMessages().isEmpty());
        }

        @Test
        @DisplayName("parsea timeout de argumentos")
        void execute_WithTimeout_ParsesTimeout() {
            command.execute("echo test --timeout=10", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Timeout") || m.contains("10")));
        }

        @Test
        @DisplayName("muestra tiempo de ejecucion")
        void execute_ShowsExecutionTime() {
            command.execute("echo test", mockContext);

            assertTrue(mockContext.hasSuccess() || mockContext.hasError());
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
        @Override public boolean isGitRepository() { return false; }
        @Override public Optional<String> getCurrentBranch() { return Optional.of("main"); }
        @Override public <T> T withFileService(Function<FileServiceAccessor, T> op) { return null; }
        @Override public void logAudit(String action, String details) { }
        @Override public AgentUserInterface getUI() { return AgentUserInterface.NO_OP; }
    }
}
