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
import dev.fararoni.core.core.commands.ModeCommand.AgentMode;
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
@DisplayName("ModeCommand")
class ModeCommandTest {
    private ModeCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new ModeCommand();
        mockContext = new MockExecutionContext(tempDir);
        ModeCommand.setCurrentMode(AgentMode.ASK);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /mode")
        void getTrigger_ReturnsMode() {
            assertEquals("/mode", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONFIG")
        void getCategory_ReturnsConfig() {
            assertEquals(CommandCategory.CONFIG, command.getCategory());
        }

        @Test
        @DisplayName("tiene alias /behavior")
        void getAliases_ReturnsBehavior() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(1, aliases.length);
            assertEquals("/behavior", aliases[0]);
        }

        @Test
        @DisplayName("usage contiene auto/ask/safe")
        void getUsage_ContainsModes() {
            String usage = command.getUsage();
            assertTrue(usage.contains("auto"));
            assertTrue(usage.contains("ask"));
            assertTrue(usage.contains("safe"));
        }

        @Test
        @DisplayName("description menciona modo")
        void getDescription_MentionsMode() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("modo"));
        }

        @Test
        @DisplayName("extendedHelp documenta modos")
        void getExtendedHelp_DocumentsModes() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("AUTO") || help.contains("auto"));
            assertTrue(help.contains("ASK") || help.contains("ask"));
            assertTrue(help.contains("SAFE") || help.contains("safe"));
        }
    }

    @Nested
    @DisplayName("Cambio de Modos")
    class ModeChangeTests {
        @Test
        @DisplayName("cambia a modo AUTO")
        void execute_Auto_ChangesToAuto() {
            command.execute("auto", mockContext);

            assertEquals(AgentMode.AUTO, ModeCommand.getCurrentMode());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("cambia a modo ASK")
        void execute_Ask_ChangesToAsk() {
            ModeCommand.setCurrentMode(AgentMode.AUTO);

            command.execute("ask", mockContext);

            assertEquals(AgentMode.ASK, ModeCommand.getCurrentMode());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("cambia a modo SAFE")
        void execute_Safe_ChangesToSafe() {
            command.execute("safe", mockContext);

            assertEquals(AgentMode.SAFE, ModeCommand.getCurrentMode());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("muestra advertencia al cambiar a AUTO")
        void execute_ToAuto_ShowsWarning() {
            command.execute("auto", mockContext);

            assertTrue(mockContext.hasWarning());
            assertTrue(mockContext.getWarnings().stream()
                .anyMatch(w -> w.contains("ADVERTENCIA") || w.contains("AUTO")));
        }

        @Test
        @DisplayName("muestra estado actual sin argumentos")
        void execute_NoArgs_ShowsCurrentMode() {
            ModeCommand.setCurrentMode(AgentMode.SAFE);

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("SAFE") || m.contains("actual")));
        }

        @Test
        @DisplayName("error con modo invalido")
        void execute_InvalidMode_ShowsError() {
            command.execute("invalid", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Confirmacion de Acciones")
    class ConfirmationTests {
        @Test
        @DisplayName("AUTO no requiere confirmacion")
        void requiresConfirmation_Auto_ReturnsFalse() {
            ModeCommand.setCurrentMode(AgentMode.AUTO);

            assertFalse(ModeCommand.requiresConfirmation("create"));
            assertFalse(ModeCommand.requiresConfirmation("modify"));
            assertFalse(ModeCommand.requiresConfirmation("delete"));
        }

        @Test
        @DisplayName("ASK requiere confirmacion excepto shell")
        void requiresConfirmation_Ask_TrueExceptShell() {
            ModeCommand.setCurrentMode(AgentMode.ASK);

            assertTrue(ModeCommand.requiresConfirmation("create"));
            assertTrue(ModeCommand.requiresConfirmation("modify"));
            assertFalse(ModeCommand.requiresConfirmation("shell"));
        }

        @Test
        @DisplayName("SAFE siempre requiere confirmacion")
        void requiresConfirmation_Safe_AlwaysTrue() {
            ModeCommand.setCurrentMode(AgentMode.SAFE);

            assertTrue(ModeCommand.requiresConfirmation("create"));
            assertTrue(ModeCommand.requiresConfirmation("shell"));
            assertTrue(ModeCommand.requiresConfirmation("anything"));
        }
    }

    @Nested
    @DisplayName("AgentMode Enum")
    class AgentModeTests {
        @Test
        @DisplayName("fromId encuentra modo por id")
        void fromId_ValidId_ReturnsMode() {
            assertEquals(AgentMode.AUTO, AgentMode.fromId("auto"));
            assertEquals(AgentMode.ASK, AgentMode.fromId("ask"));
            assertEquals(AgentMode.SAFE, AgentMode.fromId("safe"));
        }

        @Test
        @DisplayName("fromId es case-insensitive")
        void fromId_CaseInsensitive_ReturnsMode() {
            assertEquals(AgentMode.AUTO, AgentMode.fromId("AUTO"));
            assertEquals(AgentMode.ASK, AgentMode.fromId("Ask"));
            assertEquals(AgentMode.SAFE, AgentMode.fromId("SAFE"));
        }

        @Test
        @DisplayName("fromId retorna null para id invalido")
        void fromId_InvalidId_ReturnsNull() {
            assertNull(AgentMode.fromId("invalid"));
            assertNull(AgentMode.fromId(""));
            assertNull(AgentMode.fromId(null));
        }

        @Test
        @DisplayName("cada modo tiene id y descripcion")
        void modes_HaveIdAndDescription() {
            for (AgentMode mode : AgentMode.values()) {
                assertNotNull(mode.getId());
                assertNotNull(mode.getDescription());
                assertFalse(mode.getId().isEmpty());
                assertFalse(mode.getDescription().isEmpty());
            }
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
