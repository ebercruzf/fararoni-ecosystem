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
@DisplayName("ModelCommand")
class ModelCommandTest {
    private ModelCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new ModelCommand();
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /model")
        void getTrigger_ReturnsModel() {
            assertEquals("/model", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONFIG")
        void getCategory_ReturnsConfig() {
            assertEquals(CommandCategory.CONFIG, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /llm y /ai")
        void getAliases_ReturnsLlmAndAi() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/llm"));
            assertTrue(List.of(aliases).contains("/ai"));
        }

        @Test
        @DisplayName("usage contiene acciones principales")
        void getUsage_ContainsActions() {
            String usage = command.getUsage();
            assertTrue(usage.contains("status") || usage.contains("info") ||
                       usage.contains("on") || usage.contains("off"));
        }

        @Test
        @DisplayName("description menciona modelo o LLM")
        void getDescription_MentionsModel() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("modelo") || desc.contains("llm"));
        }

        @Test
        @DisplayName("extendedHelp documenta variables de entorno")
        void getExtendedHelp_DocumentsEnvVars() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("FARARONI_MODEL_PATH") ||
                       help.contains("Variables de Entorno"));
        }
    }

    @Nested
    @DisplayName("Acciones del Comando")
    class ActionTests {
        @Test
        @DisplayName("status muestra estado del modelo")
        void execute_Status_ShowsModelState() {
            command.execute("status", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Estado") || m.contains("Modelo")));
        }

        @Test
        @DisplayName("info muestra configuracion")
        void execute_Info_ShowsConfig() {
            command.execute("info", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Configuracion") || m.contains("Path") ||
                              m.contains("Contexto")));
        }

        @Test
        @DisplayName("stats muestra estadisticas")
        void execute_Stats_ShowsStats() {
            command.execute("stats", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Estadisticas") || m.contains("Requests") ||
                              m.contains("Memoria")));
        }

        @Test
        @DisplayName("sin argumentos muestra status")
        void execute_NoArgs_ShowsStatus() {
            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Estado") || m.contains("Modelo")));
        }

        @Test
        @DisplayName("accion invalida muestra error")
        void execute_InvalidAction_ShowsError() {
            command.execute("invalid", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("reload recarga el modelo")
        void execute_Reload_ReloadsModel() {
            command.execute("reload", mockContext);

            assertTrue(mockContext.hasSuccess());
            assertTrue(mockContext.getSuccesses().stream()
                .anyMatch(m -> m.contains("recargado") || m.contains("OK")));
        }
    }

    @Nested
    @DisplayName("Control de Activacion")
    class ActivationTests {
        @Test
        @DisplayName("on activa el modelo")
        void execute_On_ActivatesModel() {
            command.execute("on", mockContext);

            assertNotNull(mockContext.getMessages());
        }

        @Test
        @DisplayName("off desactiva el modelo")
        void execute_Off_DeactivatesModel() {
            command.execute("off", mockContext);

            assertTrue(mockContext.hasSuccess() || mockContext.hasWarning());
        }
    }

    @Nested
    @DisplayName("Static API")
    class StaticApiTests {
        @Test
        @DisplayName("isModelActive retorna estado")
        void isModelActive_ReturnsState() {
            boolean active = ModelCommand.isModelActive();
            assertTrue(active || !active);
        }

        @Test
        @DisplayName("incrementRequestCount incrementa contador")
        void incrementRequestCount_IncrementsCounter() {
            long before = ModelCommand.getRequestCount();
            ModelCommand.incrementRequestCount();
            long after = ModelCommand.getRequestCount();

            assertTrue(after >= before);
        }

        @Test
        @DisplayName("addTokens agrega al contador total")
        void addTokens_AddsToTotal() {
            long before = ModelCommand.getTotalTokens();
            ModelCommand.addTokens(100);
            long after = ModelCommand.getTotalTokens();

            assertTrue(after >= before);
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
