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
import dev.fararoni.core.core.services.StructureScannerService;
import dev.fararoni.core.core.services.StructureScannerService.DirectoryScanResult;
import dev.fararoni.core.core.services.StructureScannerService.SkeletonResult;
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
@DisplayName("TreeCommand")
class TreeCommandTest {
    private TreeCommand command;
    private MockExecutionContext mockContext;
    private MockStructureScannerService mockScanner;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockScanner = new MockStructureScannerService();
        command = new TreeCommand(mockScanner);
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /tree")
        void getTrigger_ReturnsTree() {
            assertEquals("/tree", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONTEXT")
        void getCategory_ReturnsContext() {
            assertEquals(CommandCategory.CONTEXT, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /skeleton y /map-lite")
        void getAliases_ReturnsSkeletonAndMapLite() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/skeleton"));
            assertTrue(List.of(aliases).contains("/map-lite"));
        }

        @Test
        @DisplayName("usage contiene [directorio]")
        void getUsage_ContainsDirectory() {
            assertTrue(command.getUsage().contains("[directorio]"));
        }

        @Test
        @DisplayName("description no es vacia")
        void getDescription_NotEmpty() {
            assertFalse(command.getDescription().isBlank());
        }

        @Test
        @DisplayName("extendedHelp contiene lenguajes soportados")
        void getExtendedHelp_ContainsLanguages() {
            String help = command.getExtendedHelp();

            assertTrue(help.contains("Java"));
            assertTrue(help.contains("Python"));
            assertTrue(help.contains("JavaScript"));
        }
    }

    @Nested
    @DisplayName("Ejecucion Exitosa")
    class SuccessTests {
        @Test
        @DisplayName("ejecuta sin argumentos usando directorio de trabajo")
        void execute_NoArgs_UsesWorkingDirectory() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "public class Test {}");

            mockScanner.setMockResult(createMockScanResult(tempDir, 1, 5, 100));

            command.execute(null, mockContext);

            assertTrue(mockScanner.wasCalledWithPath(tempDir));
        }

        @Test
        @DisplayName("ejecuta con directorio especifico")
        void execute_WithDir_UsesSpecifiedDirectory() throws IOException {
            Path subDir = tempDir.resolve("src");
            Files.createDirectories(subDir);
            Files.writeString(subDir.resolve("Main.java"), "public class Main {}");

            mockScanner.setMockResult(createMockScanResult(subDir, 1, 3, 50));

            command.execute("src", mockContext);

            assertTrue(mockScanner.wasCalledWithPath(subDir));
        }

        @Test
        @DisplayName("agrega contenido al contexto de sistema")
        void execute_ValidDir_AddsToSystemContext() throws IOException {
            Files.writeString(tempDir.resolve("App.java"), "public class App {}");

            mockScanner.setMockResult(createMockScanResult(tempDir, 1, 2, 80));

            command.execute(null, mockContext);

            assertFalse(mockContext.getSystemContextContent().isEmpty());
            assertTrue(mockContext.getSystemContextContent().contains("SKELETON MAP"));
        }

        @Test
        @DisplayName("muestra mensaje de exito con estadisticas")
        void execute_ValidDir_ShowsSuccessWithStats() throws IOException {
            Files.writeString(tempDir.resolve("Service.java"), "public class Service {}");

            mockScanner.setMockResult(createMockScanResult(tempDir, 3, 9, 500));

            command.execute(null, mockContext);

            assertTrue(mockContext.hasSuccess());
            String successMsg = mockContext.getSuccesses().get(0);
            assertTrue(successMsg.contains("3 archivos"));
            assertTrue(successMsg.contains("9 firmas"));
        }

        @Test
        @DisplayName("muestra indicador de truncado si aplica")
        void execute_Truncated_ShowsTruncatedIndicator() throws IOException {
            Files.writeString(tempDir.resolve("Large.java"), "public class Large {}");

            mockScanner.setMockResult(new DirectoryScanResult(
                tempDir,
                List.of(new SkeletonResult(tempDir.resolve("Large.java"), "skeleton", 5, "Java")),
                200000,
                true
            ));

            command.execute(null, mockContext);

            assertTrue(mockContext.hasSuccess());
            assertTrue(mockContext.getSuccesses().get(0).contains("TRUNCATED"));
        }

        @Test
        @DisplayName("imprime mensaje de escaneo")
        void execute_ValidDir_PrintsScanning() throws IOException {
            Files.writeString(tempDir.resolve("File.java"), "public class File {}");

            mockScanner.setMockResult(createMockScanResult(tempDir, 1, 1, 50));

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Escaneando")));
        }
    }

    @Nested
    @DisplayName("Manejo de Errores")
    class ErrorTests {
        @Test
        @DisplayName("muestra error si directorio no existe")
        void execute_NonExistentDir_ShowsError() {
            command.execute("nonexistent", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("no existe"));
        }

        @Test
        @DisplayName("muestra error si no es directorio")
        void execute_FileNotDir_ShowsError() throws IOException {
            Path file = tempDir.resolve("file.txt");
            Files.writeString(file, "content");

            mockContext = new MockExecutionContext(tempDir);
            command.execute("file.txt", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("No es un directorio"));
        }

        @Test
        @DisplayName("muestra warning si no hay archivos de codigo")
        void execute_NoCodeFiles_ShowsWarning() throws IOException {
            Files.writeString(tempDir.resolve("readme.txt"), "readme");

            mockScanner.setMockResult(new DirectoryScanResult(
                tempDir,
                List.of(),
                0,
                false
            ));

            command.execute(null, mockContext);

            assertTrue(mockContext.hasWarning());
            assertTrue(mockContext.getWarnings().get(0).contains("No se encontraron"));
        }

        @Test
        @DisplayName("IOException muestra error descriptivo")
        void execute_IOException_ShowsError() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "public class Test {}");

            mockScanner.setException(new IOException("Permission denied"));

            command.execute(null, mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("Permission denied"));
        }
    }

    @Nested
    @DisplayName("Resolucion de Ruta")
    class PathResolutionTests {
        @Test
        @DisplayName("resuelve ruta relativa correctamente")
        void execute_RelativePath_ResolvesCorrectly() throws IOException {
            Path subDir = tempDir.resolve("src/main");
            Files.createDirectories(subDir);
            Files.writeString(subDir.resolve("App.java"), "public class App {}");

            mockScanner.setMockResult(createMockScanResult(subDir, 1, 1, 30));

            command.execute("src/main", mockContext);

            assertTrue(mockScanner.wasCalledWithPath(subDir));
        }

        @Test
        @DisplayName("maneja espacios en argumentos")
        void execute_ArgsWithSpaces_TrimsCorrectly() throws IOException {
            Path subDir = tempDir.resolve("src");
            Files.createDirectories(subDir);
            Files.writeString(subDir.resolve("Main.java"), "public class Main {}");

            mockScanner.setMockResult(createMockScanResult(subDir, 1, 1, 30));

            command.execute("  src  ", mockContext);

            assertTrue(mockScanner.wasCalledWithPath(subDir));
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("constructor por defecto crea su propio servicio")
        void defaultConstructor_CreatesService() {
            TreeCommand cmd = new TreeCommand();
            assertNotNull(cmd);
        }

        @Test
        @DisplayName("constructor con servicio inyectado usa ese servicio")
        void injectedConstructor_UsesProvidedService() throws IOException {
            MockStructureScannerService injected = new MockStructureScannerService();
            injected.setMockResult(createMockScanResult(tempDir, 1, 1, 50));

            Files.writeString(tempDir.resolve("Test.java"), "public class Test {}");

            TreeCommand cmd = new TreeCommand(injected);
            cmd.execute(null, mockContext);

            assertTrue(injected.wasCalledWithPath(tempDir));
        }
    }

    private DirectoryScanResult createMockScanResult(Path root, int fileCount, int signatures, int chars) {
        List<SkeletonResult> results = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            results.add(new SkeletonResult(
                root.resolve("File" + i + ".java"),
                "// File" + i + ".java\n  public class File" + i,
                signatures / fileCount,
                "Java"
            ));
        }
        return new DirectoryScanResult(root, results, chars, false);
    }

    private static class MockStructureScannerService extends StructureScannerService {
        private DirectoryScanResult mockResult;
        private IOException exception;
        private final List<Path> scannedPaths = new ArrayList<>();

        void setMockResult(DirectoryScanResult result) {
            this.mockResult = result;
            this.exception = null;
        }

        void setException(IOException e) {
            this.exception = e;
            this.mockResult = null;
        }

        boolean wasCalledWithPath(Path path) {
            return scannedPaths.stream()
                .anyMatch(p -> p.toAbsolutePath().normalize()
                    .equals(path.toAbsolutePath().normalize()));
        }

        @Override
        public DirectoryScanResult scanDirectory(Path rootPath) throws IOException {
            scannedPaths.add(rootPath);
            if (exception != null) {
                throw exception;
            }
            return mockResult;
        }

        @Override
        public String formatForContext(DirectoryScanResult result) {
            StringBuilder sb = new StringBuilder();
            sb.append(">>> SKELETON MAP: ").append(result.rootPath()).append("\n");
            sb.append("Files scanned: ").append(result.fileCount()).append("\n");
            for (SkeletonResult r : result.results()) {
                sb.append(r.skeleton()).append("\n");
            }
            return sb.toString();
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
        private final List<String> systemContextContent = new ArrayList<>();
        private boolean debugMode = false;

        MockExecutionContext(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        List<String> getMessages() { return messages; }
        List<String> getSuccesses() { return successes; }
        List<String> getWarnings() { return warnings; }
        List<String> getErrors() { return errors; }
        List<String> getDebugs() { return debugs; }
        String getContextContent() { return String.join("\n", contextContent); }
        String getSystemContextContent() { return String.join("\n", systemContextContent); }

        boolean hasError() { return !errors.isEmpty(); }
        boolean hasSuccess() { return !successes.isEmpty(); }
        boolean hasWarning() { return !warnings.isEmpty(); }

        @Override public void print(String message) { messages.add(message); }
        @Override public void printSuccess(String message) { successes.add(message); }
        @Override public void printWarning(String message) { warnings.add(message); }
        @Override public void printError(String message) { errors.add(message); }
        @Override public void printDebug(String message) { if (debugMode) debugs.add(message); }
        @Override public void addToContext(String content) { contextContent.add(content); }
        @Override public void addToSystemContext(String content) { systemContextContent.add(content); }
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
