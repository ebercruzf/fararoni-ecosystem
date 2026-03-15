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
@DisplayName("IgnoreCommand")
class IgnoreCommandTest {
    private IgnoreCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new IgnoreCommand();
        mockContext = new MockExecutionContext(tempDir);
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /ign")
        void getTrigger_ReturnsIgn() {
            assertEquals("/ign", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es GIT")
        void getCategory_ReturnsGit() {
            assertEquals(CommandCategory.GIT, command.getCategory());
        }

        @Test
        @DisplayName("tiene alias /ignore y /gitignore")
        void getAliases_ReturnsAliases() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/ignore"));
            assertTrue(List.of(aliases).contains("/gitignore"));
        }

        @Test
        @DisplayName("usage contiene add/remove/list")
        void getUsage_ContainsActions() {
            String usage = command.getUsage();
            assertTrue(usage.contains("add") || usage.contains("remove") || usage.contains("list"));
        }

        @Test
        @DisplayName("description menciona gitignore")
        void getDescription_MentionsGitignore() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("gitignore") || desc.contains("patron"));
        }

        @Test
        @DisplayName("extendedHelp documenta templates")
        void getExtendedHelp_DocumentsTemplates() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("java"));
            assertTrue(help.contains("node"));
            assertTrue(help.contains("python"));
        }
    }

    @Nested
    @DisplayName("Lista Patrones")
    class ListPatternsTests {
        @Test
        @DisplayName("sin .gitignore muestra warning")
        void execute_NoGitignore_ShowsWarning() {
            command.execute(null, mockContext);

            assertTrue(mockContext.hasWarning());
        }

        @Test
        @DisplayName("con .gitignore lista patrones")
        void execute_WithGitignore_ListsPatterns() throws IOException {
            Files.writeString(tempDir.resolve(".gitignore"), "*.log\n*.tmp\n");

            command.execute("list", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("*.log") || m.contains("*.tmp")));
        }

        @Test
        @DisplayName("gitignore vacio muestra warning")
        void execute_EmptyGitignore_ShowsWarning() throws IOException {
            Files.writeString(tempDir.resolve(".gitignore"), "# Solo comentarios\n");

            command.execute("list", mockContext);

            assertTrue(mockContext.hasWarning());
        }
    }

    @Nested
    @DisplayName("Agregar Patron")
    class AddPatternTests {
        @Test
        @DisplayName("agrega patron nuevo")
        void execute_Add_CreatesPattern() throws IOException {
            command.execute("add *.log", mockContext);

            assertTrue(mockContext.hasSuccess());
            Path gitignore = tempDir.resolve(".gitignore");
            assertTrue(Files.exists(gitignore));
            String content = Files.readString(gitignore);
            assertTrue(content.contains("*.log"));
        }

        @Test
        @DisplayName("patron duplicado muestra warning")
        void execute_AddDuplicate_ShowsWarning() throws IOException {
            Files.writeString(tempDir.resolve(".gitignore"), "*.log\n");

            command.execute("add *.log", mockContext);

            assertTrue(mockContext.hasWarning());
        }

        @Test
        @DisplayName("add sin patron muestra error")
        void execute_AddNoPattern_ShowsError() {
            command.execute("add", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("agrega multiples patrones")
        void execute_AddMultiple_AddsAll() throws IOException {
            command.execute("add *.log", mockContext);
            command.execute("add *.tmp", mockContext);

            String content = Files.readString(tempDir.resolve(".gitignore"));
            assertTrue(content.contains("*.log"));
            assertTrue(content.contains("*.tmp"));
        }
    }

    @Nested
    @DisplayName("Remover Patron")
    class RemovePatternTests {
        @Test
        @DisplayName("remueve patron existente")
        void execute_Remove_RemovesPattern() throws IOException {
            Files.writeString(tempDir.resolve(".gitignore"), "*.log\n*.tmp\n");

            command.execute("remove *.log", mockContext);

            assertTrue(mockContext.hasSuccess());
            String content = Files.readString(tempDir.resolve(".gitignore"));
            assertFalse(content.contains("*.log"));
            assertTrue(content.contains("*.tmp"));
        }

        @Test
        @DisplayName("patron no encontrado muestra warning")
        void execute_RemoveNotFound_ShowsWarning() throws IOException {
            Files.writeString(tempDir.resolve(".gitignore"), "*.log\n");

            command.execute("remove *.bak", mockContext);

            assertTrue(mockContext.hasWarning());
        }

        @Test
        @DisplayName("remove sin gitignore muestra error")
        void execute_RemoveNoGitignore_ShowsError() {
            command.execute("remove *.log", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Templates")
    class TemplateTests {
        @Test
        @DisplayName("genera template java")
        void execute_TemplateJava_CreatesJavaPatterns() throws IOException {
            command.execute("template java", mockContext);

            assertTrue(mockContext.hasSuccess());
            String content = Files.readString(tempDir.resolve(".gitignore"));
            assertTrue(content.contains("*.class"));
            assertTrue(content.contains("target/"));
        }

        @Test
        @DisplayName("genera template node")
        void execute_TemplateNode_CreatesNodePatterns() throws IOException {
            command.execute("template node", mockContext);

            assertTrue(mockContext.hasSuccess());
            String content = Files.readString(tempDir.resolve(".gitignore"));
            assertTrue(content.contains("node_modules/"));
        }

        @Test
        @DisplayName("genera template python")
        void execute_TemplatePython_CreatesPythonPatterns() throws IOException {
            command.execute("template python", mockContext);

            assertTrue(mockContext.hasSuccess());
            String content = Files.readString(tempDir.resolve(".gitignore"));
            assertTrue(content.contains("__pycache__"));
        }

        @Test
        @DisplayName("template desconocido muestra error")
        void execute_TemplateUnknown_ShowsError() {
            command.execute("template unknown", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("agrega template a gitignore existente")
        void execute_TemplateExisting_Appends() throws IOException {
            Files.writeString(tempDir.resolve(".gitignore"), "# Existing\n*.bak\n");

            command.execute("template macos", mockContext);

            String content = Files.readString(tempDir.resolve(".gitignore"));
            assertTrue(content.contains("*.bak"));
            assertTrue(content.contains(".DS_Store"));
        }
    }

    @Nested
    @DisplayName("Acciones Implicitas")
    class ImplicitActionTests {
        @Test
        @DisplayName("patron sin accion se agrega")
        void execute_PatternOnly_AddsPattern() throws IOException {
            command.execute("*.cache", mockContext);

            assertTrue(mockContext.hasSuccess());
            String content = Files.readString(tempDir.resolve(".gitignore"));
            assertTrue(content.contains("*.cache"));
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
