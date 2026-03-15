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
@DisplayName("LintCommand")
class LintCommandTest {
    private LintCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new LintCommand();
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /lint")
        void getTrigger_ReturnsLint() {
            assertEquals("/lint", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es DEBUG")
        void getCategory_ReturnsDebug() {
            assertEquals(CommandCategory.DEBUG, command.getCategory());
        }

        @Test
        @DisplayName("tiene alias /format y /style")
        void getAliases_ReturnsAliases() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/format"));
            assertTrue(List.of(aliases).contains("/style"));
        }

        @Test
        @DisplayName("usage contiene --fix")
        void getUsage_ContainsFix() {
            String usage = command.getUsage();
            assertTrue(usage.contains("--fix") || usage.contains("archivo"));
        }

        @Test
        @DisplayName("description menciona linter")
        void getDescription_MentionsLinter() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("linter") || desc.contains("formatter"));
        }

        @Test
        @DisplayName("extendedHelp documenta linters")
        void getExtendedHelp_DocumentsLinters() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("ESLint"));
            assertTrue(help.contains("Prettier"));
            assertTrue(help.contains("Checkstyle"));
            assertTrue(help.contains("Ruff") || help.contains("Python"));
        }
    }

    @Nested
    @DisplayName("Deteccion de Linter")
    class LinterDetectionTests {
        @Test
        @DisplayName("sin linter muestra error")
        void execute_NoLinter_ShowsError() {
            command.execute(null, mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("ESLint") || m.contains("soportados")));
        }

        @Test
        @DisplayName("detecta ESLint con .eslintrc.json")
        void execute_WithEslintrc_DetectsEslint() throws IOException {
            Files.writeString(tempDir.resolve(".eslintrc.json"), "{}");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("ESLint")));
        }

        @Test
        @DisplayName("detecta Prettier con .prettierrc")
        void execute_WithPrettierrc_DetectsPrettier() throws IOException {
            Files.writeString(tempDir.resolve(".prettierrc"), "{}");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Prettier")));
        }

        @Test
        @DisplayName("detecta Maven con pom.xml")
        void execute_WithPomXml_DetectsMaven() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Maven") || m.contains("Checkstyle")));
        }

        @Test
        @DisplayName("detecta Gradle con build.gradle")
        void execute_WithBuildGradle_DetectsGradle() throws IOException {
            Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Spotless") || m.contains("Gradle")));
        }

        @Test
        @DisplayName("detecta Ruff con pyproject.toml")
        void execute_WithPyprojectToml_DetectsRuff() throws IOException {
            Files.writeString(tempDir.resolve("pyproject.toml"), "[tool.ruff]");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Ruff")));
        }

        @Test
        @DisplayName("detecta Clippy con Cargo.toml")
        void execute_WithCargoToml_DetectsClippy() throws IOException {
            Files.writeString(tempDir.resolve("Cargo.toml"), "[package]");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Clippy")));
        }

        @Test
        @DisplayName("detecta golangci-lint con .golangci.yml")
        void execute_WithGolangciYml_DetectsGolangci() throws IOException {
            Files.writeString(tempDir.resolve(".golangci.yml"), "linters:");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("golangci")));
        }
    }

    @Nested
    @DisplayName("Opcion --fix")
    class FixOptionTests {
        @Test
        @DisplayName("parsea --fix correctamente")
        void execute_WithFix_ParsesFix() throws IOException {
            Files.writeString(tempDir.resolve(".eslintrc.json"), "{}");

            command.execute("--fix", mockContext);

            assertTrue(mockContext.hasWarning());
            assertTrue(mockContext.getWarnings().stream()
                .anyMatch(w -> w.contains("--fix") || w.contains("modificar")));
        }

        @Test
        @DisplayName("parsea -f como alias de --fix")
        void execute_WithShortFix_ParsesFix() throws IOException {
            Files.writeString(tempDir.resolve(".eslintrc.json"), "{}");

            command.execute("-f", mockContext);

            assertTrue(mockContext.hasWarning());
        }
    }

    @Nested
    @DisplayName("Archivo Especifico")
    class TargetFileTests {
        @Test
        @DisplayName("acepta archivo como argumento")
        void execute_WithFile_ParsesFile() throws IOException {
            Files.writeString(tempDir.resolve(".eslintrc.json"), "{}");

            command.execute("src/main.js", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("main.js") || m.contains("src")));
        }

        @Test
        @DisplayName("combina --fix con archivo")
        void execute_WithFixAndFile_ParsesBoth() throws IOException {
            Files.writeString(tempDir.resolve(".eslintrc.json"), "{}");

            command.execute("--fix src/main.js", mockContext);

            assertTrue(mockContext.hasWarning());
            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("main.js")));
        }
    }

    @Nested
    @DisplayName("Prioridad de Deteccion")
    class DetectionPriorityTests {
        @Test
        @DisplayName("ESLint tiene prioridad sobre Prettier")
        void execute_BothEslintAndPrettier_PrefersEslint() throws IOException {
            Files.writeString(tempDir.resolve(".eslintrc.json"), "{}");
            Files.writeString(tempDir.resolve(".prettierrc"), "{}");

            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("ESLint")));
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
