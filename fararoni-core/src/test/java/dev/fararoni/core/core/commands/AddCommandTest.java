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
import dev.fararoni.core.core.services.FileContextService;
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
@DisplayName("AddCommand")
class AddCommandTest {
    private AddCommand command;
    private MockExecutionContext mockContext;
    private FileContextService fileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileService = new FileContextService(tempDir);
        command = new AddCommand(fileService);
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /add")
        void getTrigger_ReturnsAdd() {
            assertEquals("/add", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONTEXT")
        void getCategory_ReturnsContext() {
            assertEquals(CommandCategory.CONTEXT, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /load y /include")
        void getAliases_ReturnsLoadAndInclude() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/load"));
            assertTrue(List.of(aliases).contains("/include"));
        }

        @Test
        @DisplayName("usage contiene archivo/directorio/glob")
        void getUsage_ContainsPatterns() {
            String usage = command.getUsage();
            assertTrue(usage.contains("archivo") || usage.contains("directorio") || usage.contains("glob"));
        }

        @Test
        @DisplayName("description no es vacia")
        void getDescription_NotEmpty() {
            assertFalse(command.getDescription().isBlank());
        }

        @Test
        @DisplayName("extendedHelp contiene ejemplos")
        void getExtendedHelp_ContainsExamples() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("Ejemplos"));
            assertTrue(help.contains(".java"));
        }
    }

    @Nested
    @DisplayName("Ejecucion Exitosa")
    class SuccessTests {
        @Test
        @DisplayName("agrega archivo individual")
        void execute_SingleFile_AddsSuccessfully() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "public class Test {}");

            command.execute("Test.java", mockContext);

            assertTrue(mockContext.hasSuccess());
            assertTrue(mockContext.getSuccesses().get(0).contains("1 archivo"));
        }

        @Test
        @DisplayName("agrega multiples archivos con glob")
        void execute_GlobPattern_AddsMultiple() throws IOException {
            Files.writeString(tempDir.resolve("A.java"), "class A {}");
            Files.writeString(tempDir.resolve("B.java"), "class B {}");

            command.execute("*.java", mockContext);

            assertTrue(mockContext.hasSuccess());
            assertTrue(mockContext.getSuccesses().get(0).contains("2 archivo"));
        }

        @Test
        @DisplayName("agrega contenido al contexto")
        void execute_AddsToContext() throws IOException {
            Files.writeString(tempDir.resolve("App.java"), "public class App {}");

            command.execute("App.java", mockContext);

            assertFalse(mockContext.getContextContent().isEmpty());
        }

        @Test
        @DisplayName("detecta archivos ya cargados")
        void execute_AlreadyLoaded_DetectsCorrectly() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");

            command.execute("Test.java", mockContext);
            assertTrue(mockContext.hasSuccess());

            Files.writeString(tempDir.resolve("Other.java"), "class Other {}");
            MockExecutionContext freshContext = new MockExecutionContext(tempDir);

            command.execute("*.java", freshContext);

            assertTrue(freshContext.hasSuccess());
            assertTrue(freshContext.hasWarning());
            assertTrue(freshContext.getWarnings().get(0).contains("omitido"));
        }

        @Test
        @DisplayName("muestra mensaje de busqueda")
        void execute_ShowsSearchingMessage() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");

            command.execute("*.java", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Buscando")));
        }
    }

    @Nested
    @DisplayName("Manejo de Errores")
    class ErrorTests {
        @Test
        @DisplayName("muestra error si no hay argumentos")
        void execute_NoArgs_ShowsError() {
            command.execute(null, mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("Uso:"));
        }

        @Test
        @DisplayName("muestra error si argumentos vacios")
        void execute_EmptyArgs_ShowsError() {
            command.execute("   ", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("muestra error si archivo no existe")
        void execute_NonExistent_ShowsError() {
            command.execute("nonexistent.java", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("No se agregaron"));
        }

        @Test
        @DisplayName("muestra error si patron no coincide")
        void execute_NoMatch_ShowsError() {
            command.execute("*.xyz", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("constructor por defecto es valido")
        void defaultConstructor_CreatesCommand() {
            AddCommand cmd = new AddCommand();
            assertNotNull(cmd);
        }

        @Test
        @DisplayName("constructor con servicio inyectado usa ese servicio")
        void injectedConstructor_UsesProvidedService() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");

            FileContextService injected = new FileContextService(tempDir);
            AddCommand cmd = new AddCommand(injected);
            cmd.execute("Test.java", mockContext);

            assertEquals(1, injected.getLoadedCount());
        }
    }

    private static class MockExecutionContext implements ExecutionContext {
        private final Path workingDirectory;
        private final List<String> messages = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> debugs = new ArrayList<>();
        private final List<String> contextContent = new ArrayList<>();
        private boolean debugMode = false;

        MockExecutionContext(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        List<String> getMessages() { return messages; }
        List<String> getSuccesses() { return successes; }
        List<String> getWarnings() { return warnings; }
        List<String> getErrors() { return errors; }
        String getContextContent() { return String.join("\n", contextContent); }

        boolean hasError() { return !errors.isEmpty(); }
        boolean hasSuccess() { return !successes.isEmpty(); }
        boolean hasWarning() { return !warnings.isEmpty(); }

        @Override public void print(String message) { messages.add(message); }
        @Override public void printSuccess(String message) { successes.add(message); }
        @Override public void printWarning(String message) { warnings.add(message); }
        @Override public void printError(String message) { errors.add(message); }
        @Override public void printDebug(String message) { if (debugMode) debugs.add(message); }
        @Override public void addToContext(String content) { contextContent.add(content); }
        @Override public void addToSystemContext(String content) { }
        @Override public Path getWorkingDirectory() { return workingDirectory; }
        @Override public Optional<Path> getProjectRoot() { return Optional.of(workingDirectory); }
        @Override public boolean isDebugMode() { return debugMode; }
        @Override public boolean isGitRepository() { return false; }
        @Override public Optional<String> getCurrentBranch() { return Optional.empty(); }
        @Override public <T> T withFileService(Function<FileServiceAccessor, T> op) { return null; }
        @Override public void logAudit(String action, String details) { }
        @Override public AgentUserInterface getUI() { return AgentUserInterface.NO_OP; }
    }
}
