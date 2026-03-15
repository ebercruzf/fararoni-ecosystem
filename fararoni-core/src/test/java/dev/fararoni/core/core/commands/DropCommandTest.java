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
@DisplayName("DropCommand")
class DropCommandTest {
    private DropCommand command;
    private MockExecutionContext mockContext;
    private FileContextService fileService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        fileService = new FileContextService(tempDir);
        command = new DropCommand(fileService);
        mockContext = new MockExecutionContext(tempDir);

        Files.writeString(tempDir.resolve("A.java"), "class A {}");
        Files.writeString(tempDir.resolve("B.java"), "class B {}");
        Files.writeString(tempDir.resolve("C.py"), "pass");
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /drop")
        void getTrigger_ReturnsDrop() {
            assertEquals("/drop", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONTEXT")
        void getCategory_ReturnsContext() {
            assertEquals(CommandCategory.CONTEXT, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /unload y /remove")
        void getAliases_ReturnsUnloadAndRemove() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/unload"));
            assertTrue(List.of(aliases).contains("/remove"));
        }

        @Test
        @DisplayName("description no es vacia")
        void getDescription_NotEmpty() {
            assertFalse(command.getDescription().isBlank());
        }

        @Test
        @DisplayName("extendedHelp contiene 'all'")
        void getExtendedHelp_ContainsAll() {
            assertTrue(command.getExtendedHelp().contains("all"));
        }
    }

    @Nested
    @DisplayName("Ejecucion Exitosa")
    class SuccessTests {
        @Test
        @DisplayName("remueve archivo especifico")
        void execute_SingleFile_RemovesSuccessfully() throws IOException {
            fileService.addFiles(List.of("*.java"));

            command.execute("A.java", mockContext);

            assertTrue(mockContext.hasSuccess());
            assertEquals(1, fileService.getLoadedCount());
        }

        @Test
        @DisplayName("remueve por patron de extension")
        void execute_ExtensionPattern_RemovesMatching() {
            fileService.addFiles(List.of("*.java", "*.py"));

            command.execute("*.java", mockContext);

            assertTrue(mockContext.hasSuccess());
            assertEquals(1, fileService.getLoadedCount());
        }

        @Test
        @DisplayName("drop all remueve todo")
        void execute_DropAll_RemovesEverything() {
            fileService.addFiles(List.of("*.java", "*.py"));

            command.execute("all", mockContext);

            assertTrue(mockContext.hasSuccess());
            assertEquals(0, fileService.getLoadedCount());
            assertTrue(mockContext.getSuccesses().get(0).contains("3 archivo"));
        }

        @Test
        @DisplayName("drop ALL (mayusculas) funciona igual")
        void execute_DropAllUppercase_Works() {
            fileService.addFiles(List.of("*.java"));

            command.execute("ALL", mockContext);

            assertTrue(mockContext.hasSuccess());
            assertEquals(0, fileService.getLoadedCount());
        }

        @Test
        @DisplayName("muestra archivos removidos")
        void execute_ShowsRemovedFiles() {
            fileService.addFile("A.java");

            command.execute("A.java", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("A.java")));
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
        @DisplayName("muestra warning si no hay archivos cargados")
        void execute_NoLoadedFiles_ShowsWarning() {
            command.execute("Test.java", mockContext);

            assertTrue(mockContext.hasWarning());
            assertTrue(mockContext.getWarnings().get(0).contains("No hay archivos"));
        }

        @Test
        @DisplayName("muestra error si patron no coincide")
        void execute_NoMatch_ShowsError() {
            fileService.addFile("A.java");

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
            DropCommand cmd = new DropCommand();
            assertNotNull(cmd);
        }
    }

    private static class MockExecutionContext implements ExecutionContext {
        private final Path workingDirectory;
        private final List<String> messages = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private boolean debugMode = false;

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
        @Override public boolean isDebugMode() { return debugMode; }
        @Override public boolean isGitRepository() { return false; }
        @Override public Optional<String> getCurrentBranch() { return Optional.empty(); }
        @Override public <T> T withFileService(Function<FileServiceAccessor, T> op) { return null; }
        @Override public void logAudit(String action, String details) { }
        @Override public AgentUserInterface getUI() { return AgentUserInterface.NO_OP; }
    }
}
