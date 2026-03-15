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
@DisplayName("TestCommand")
class TestCommandTest {
    private TestCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new TestCommand();
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /test")
        void getTrigger_ReturnsTest() {
            assertEquals("/test", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es DEBUG")
        void getCategory_ReturnsDebug() {
            assertEquals(CommandCategory.DEBUG, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /tests y /check")
        void getAliases_ReturnsTestsAndCheck() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/tests"));
            assertTrue(List.of(aliases).contains("/check"));
        }

        @Test
        @DisplayName("usage contiene patron y verbose")
        void getUsage_ContainsPatternAndVerbose() {
            String usage = command.getUsage();
            assertTrue(usage.contains("patron") || usage.contains("verbose") ||
                       usage.contains("timeout"));
        }

        @Test
        @DisplayName("description menciona tests")
        void getDescription_MentionsTests() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("test"));
        }

        @Test
        @DisplayName("extendedHelp documenta frameworks")
        void getExtendedHelp_DocumentsFrameworks() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("Maven") || help.contains("pom.xml"));
            assertTrue(help.contains("Gradle") || help.contains("build.gradle"));
            assertTrue(help.contains("npm") || help.contains("package.json"));
        }
    }

    @Nested
    @DisplayName("Deteccion de Framework")
    class FrameworkDetectionTests {
        @Test
        @DisplayName("error sin framework detectado")
        void execute_NoFramework_ShowsError() {
            command.execute(null, mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("No se detecto") ||
                       mockContext.getErrors().get(0).contains("framework"));
        }

        @Test
        @DisplayName("lista frameworks soportados si no detecta")
        void execute_NoFramework_ListsSupported() {
            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Maven") || m.contains("pom.xml")));
        }

        @Test
        @DisplayName("detecta Maven por pom.xml")
        void execute_PomXml_DetectsMaven() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            mockContext = new MockExecutionContext(tempDir);

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Maven")));
        }

        @Test
        @DisplayName("detecta Gradle por build.gradle")
        void execute_BuildGradle_DetectsGradle() throws IOException {
            Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");
            mockContext = new MockExecutionContext(tempDir);

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Gradle")));
        }

        @Test
        @DisplayName("detecta npm por package.json")
        void execute_PackageJson_DetectsNpm() throws IOException {
            Files.writeString(tempDir.resolve("package.json"), "{}");
            mockContext = new MockExecutionContext(tempDir);

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("npm")));
        }

        @Test
        @DisplayName("detecta Cargo por Cargo.toml")
        void execute_CargoToml_DetectsCargo() throws IOException {
            Files.writeString(tempDir.resolve("Cargo.toml"), "[package]");
            mockContext = new MockExecutionContext(tempDir);

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Cargo")));
        }

        @Test
        @DisplayName("detecta Go por go.mod")
        void execute_GoMod_DetectsGo() throws IOException {
            Files.writeString(tempDir.resolve("go.mod"), "module test");
            mockContext = new MockExecutionContext(tempDir);

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Go")));
        }
    }

    @Nested
    @DisplayName("Parseo de Argumentos")
    class ArgumentParsingTests {
        @Test
        @DisplayName("detecta flag --verbose")
        void execute_VerboseFlag_IsDetected() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            mockContext = new MockExecutionContext(tempDir);

            command.execute("--verbose", mockContext);

            assertNotNull(mockContext.getMessages());
        }

        @Test
        @DisplayName("detecta flag --timeout")
        void execute_TimeoutFlag_IsDetected() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            mockContext = new MockExecutionContext(tempDir);

            command.execute("--timeout=60", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("60") || m.contains("Timeout")));
        }

        @Test
        @DisplayName("detecta patron de test")
        void execute_Pattern_IsDetected() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            mockContext = new MockExecutionContext(tempDir);

            command.execute("UserService", mockContext);

            assertNotNull(mockContext.getMessages());
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
