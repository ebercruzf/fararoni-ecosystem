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
@DisplayName("DryRunCommand")
class DryRunCommandTest {
    private DryRunCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new DryRunCommand();
        mockContext = new MockExecutionContext(tempDir);
        DryRunCommand.setDryRunEnabled(false);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /dryrun")
        void getTrigger_ReturnsDryrun() {
            assertEquals("/dryrun", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONFIG")
        void getCategory_ReturnsConfig() {
            assertEquals(CommandCategory.CONFIG, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /dry y /simulate")
        void getAliases_ReturnsDryAndSimulate() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/dry"));
            assertTrue(List.of(aliases).contains("/simulate"));
        }

        @Test
        @DisplayName("usage contiene on/off/status")
        void getUsage_ContainsOnOffStatus() {
            String usage = command.getUsage();
            assertTrue(usage.contains("on") || usage.contains("off") || usage.contains("status"));
        }

        @Test
        @DisplayName("description menciona simulacion")
        void getDescription_MentionsSimulation() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("simulacion") || desc.contains("dry"));
        }

        @Test
        @DisplayName("extendedHelp documenta comportamiento")
        void getExtendedHelp_DocumentsBehavior() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("NO se crean") || help.contains("no se ejecutan") ||
                       help.contains("mostraran"));
        }
    }

    @Nested
    @DisplayName("Funcionalidad Toggle")
    class ToggleTests {
        @Test
        @DisplayName("activa dry-run con 'on'")
        void execute_On_EnablesDryRun() {
            assertFalse(DryRunCommand.isDryRunEnabled());

            command.execute("on", mockContext);

            assertTrue(DryRunCommand.isDryRunEnabled());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("desactiva dry-run con 'off'")
        void execute_Off_DisablesDryRun() {
            DryRunCommand.setDryRunEnabled(true);

            command.execute("off", mockContext);

            assertFalse(DryRunCommand.isDryRunEnabled());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("toggle cambia estado")
        void execute_NoArgs_TogglesState() {
            assertFalse(DryRunCommand.isDryRunEnabled());

            command.execute(null, mockContext);

            assertTrue(DryRunCommand.isDryRunEnabled());

            command.execute("", mockContext);

            assertFalse(DryRunCommand.isDryRunEnabled());
        }

        @Test
        @DisplayName("status muestra estado actual")
        void execute_Status_ShowsCurrentState() {
            DryRunCommand.setDryRunEnabled(true);

            command.execute("status", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("ACTIVADO")));
        }

        @Test
        @DisplayName("acepta 'true' como alias de 'on'")
        void execute_True_EnablesDryRun() {
            command.execute("true", mockContext);
            assertTrue(DryRunCommand.isDryRunEnabled());
        }

        @Test
        @DisplayName("acepta 'false' como alias de 'off'")
        void execute_False_DisablesDryRun() {
            DryRunCommand.setDryRunEnabled(true);
            command.execute("false", mockContext);
            assertFalse(DryRunCommand.isDryRunEnabled());
        }
    }

    @Nested
    @DisplayName("Static API")
    class StaticApiTests {
        @Test
        @DisplayName("isDryRunEnabled retorna estado actual")
        void isDryRunEnabled_ReturnsCurrentState() {
            assertFalse(DryRunCommand.isDryRunEnabled());

            DryRunCommand.setDryRunEnabled(true);

            assertTrue(DryRunCommand.isDryRunEnabled());
        }

        @Test
        @DisplayName("setDryRunEnabled cambia estado")
        void setDryRunEnabled_ChangesState() {
            DryRunCommand.setDryRunEnabled(true);
            assertTrue(DryRunCommand.isDryRunEnabled());

            DryRunCommand.setDryRunEnabled(false);
            assertFalse(DryRunCommand.isDryRunEnabled());
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
