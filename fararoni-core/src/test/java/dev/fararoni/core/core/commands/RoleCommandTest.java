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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("RoleCommand")
class RoleCommandTest {
    private RoleCommand command;
    private MockExecutionContext mockContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new RoleCommand();
        mockContext = new MockExecutionContext(tempDir);
        command.execute("reset", mockContext);
        mockContext.clear();
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /role")
        void getTrigger_ReturnsRole() {
            assertEquals("/role", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONFIG")
        void getCategory_ReturnsConfig() {
            assertEquals(CommandCategory.CONFIG, command.getCategory());
        }

        @Test
        @DisplayName("tiene alias /persona")
        void getAliases_ReturnsPersona() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(1, aliases.length);
            assertEquals("/persona", aliases[0]);
        }

        @Test
        @DisplayName("usage contiene roles")
        void getUsage_ContainsRoles() {
            String usage = command.getUsage();
            assertTrue(usage.contains("dev") || usage.contains("architect"));
        }

        @Test
        @DisplayName("description menciona rol")
        void getDescription_MentionsRole() {
            String desc = command.getDescription().toLowerCase();
            assertTrue(desc.contains("rol") || desc.contains("persona"));
        }

        @Test
        @DisplayName("extendedHelp documenta todos los roles")
        void getExtendedHelp_DocumentsAllRoles() {
            String help = command.getExtendedHelp();
            assertTrue(help.contains("dev"));
            assertTrue(help.contains("architect"));
            assertTrue(help.contains("security"));
            assertTrue(help.contains("qa"));
            assertTrue(help.contains("ux"));
        }
    }

    @Nested
    @DisplayName("Mostrar Rol Actual")
    class ShowCurrentRoleTests {
        @Test
        @DisplayName("sin argumentos muestra rol actual")
        void execute_NoArgs_ShowsCurrentRole() {
            command.execute(null, mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("actual") || m.contains("Developer")));
        }

        @Test
        @DisplayName("rol default es dev")
        void execute_Default_IsDev() {
            assertEquals("dev", RoleCommand.getCurrentRole());
        }
    }

    @Nested
    @DisplayName("Cambio de Roles")
    class RoleChangeTests {
        @Test
        @DisplayName("cambia a rol developer")
        void execute_Dev_ChangesToDev() {
            command.execute("dev", mockContext);

            assertEquals("dev", RoleCommand.getCurrentRole());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("cambia a rol architect")
        void execute_Architect_ChangesToArchitect() {
            command.execute("architect", mockContext);

            assertEquals("architect", RoleCommand.getCurrentRole());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("cambia a rol security")
        void execute_Security_ChangesToSecurity() {
            command.execute("security", mockContext);

            assertEquals("security", RoleCommand.getCurrentRole());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("cambia a rol qa")
        void execute_Qa_ChangesToQa() {
            command.execute("qa", mockContext);

            assertEquals("qa", RoleCommand.getCurrentRole());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("cambia a rol ux")
        void execute_Ux_ChangesToUx() {
            command.execute("ux", mockContext);

            assertEquals("ux", RoleCommand.getCurrentRole());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("rol invalido muestra error")
        void execute_Invalid_ShowsError() {
            command.execute("invalid", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("muestra tip al cambiar rol")
        void execute_ChangeRole_ShowsTip() {
            command.execute("security", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Tip") || m.contains("OWASP")));
        }

        @Test
        @DisplayName("inyecta system prompt al cambiar")
        void execute_ChangeRole_InjectsSystemPrompt() {
            command.execute("architect", mockContext);

            assertTrue(mockContext.getSystemContexts().stream()
                .anyMatch(s -> s.contains("arquitecto") || s.contains("ROLE")));
        }
    }

    @Nested
    @DisplayName("Lista de Roles")
    class ListRolesTests {
        @Test
        @DisplayName("list muestra todos los roles")
        void execute_List_ShowsAllRoles() {
            command.execute("list", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("dev")));
            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("architect")));
            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("security")));
        }

        @Test
        @DisplayName("ls es alias de list")
        void execute_Ls_SameAsList() {
            command.execute("ls", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("disponibles") || m.contains("Roles")));
        }

        @Test
        @DisplayName("list marca rol activo")
        void execute_List_MarksActiveRole() {
            command.execute("qa", mockContext);
            mockContext.clear();

            command.execute("list", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("[activo]") || m.contains("qa")));
        }
    }

    @Nested
    @DisplayName("Reset de Rol")
    class ResetRoleTests {
        @Test
        @DisplayName("reset vuelve a dev")
        void execute_Reset_ReturnsTodev() {
            command.execute("security", mockContext);
            mockContext.clear();

            command.execute("reset", mockContext);

            assertEquals("dev", RoleCommand.getCurrentRole());
            assertTrue(mockContext.hasSuccess());
        }

        @Test
        @DisplayName("default es alias de reset")
        void execute_Default_SameAsReset() {
            command.execute("architect", mockContext);
            mockContext.clear();

            command.execute("default", mockContext);

            assertEquals("dev", RoleCommand.getCurrentRole());
        }

        @Test
        @DisplayName("clear es alias de reset")
        void execute_Clear_SameAsReset() {
            command.execute("ux", mockContext);
            mockContext.clear();

            command.execute("clear", mockContext);

            assertEquals("dev", RoleCommand.getCurrentRole());
        }
    }

    @Nested
    @DisplayName("Metodos Estaticos")
    class StaticMethodsTests {
        @Test
        @DisplayName("getCurrentRole retorna rol actual")
        void getCurrentRole_ReturnsCurrentRole() {
            command.execute("architect", mockContext);

            assertEquals("architect", RoleCommand.getCurrentRole());
        }

        @Test
        @DisplayName("getCurrentSystemPrompt retorna prompt del rol actual")
        void getCurrentSystemPrompt_ReturnsPrompt() {
            command.execute("security", mockContext);

            String prompt = RoleCommand.getCurrentSystemPrompt();

            assertNotNull(prompt);
            assertTrue(prompt.contains("seguridad") || prompt.contains("vulnerabilidad"));
        }

        @Test
        @DisplayName("getSystemPromptForRole retorna prompt de rol especifico")
        void getSystemPromptForRole_ReturnsPromptForRole() {
            String prompt = RoleCommand.getSystemPromptForRole("qa");

            assertNotNull(prompt);
            assertTrue(prompt.contains("QA") || prompt.contains("calidad") || prompt.contains("test"));
        }

        @Test
        @DisplayName("getSystemPromptForRole con null retorna dev")
        void getSystemPromptForRole_Null_ReturnsDev() {
            String prompt = RoleCommand.getSystemPromptForRole(null);

            assertNotNull(prompt);
            assertTrue(prompt.contains("desarrollador") || prompt.contains("codigo"));
        }

        @Test
        @DisplayName("getAvailableRoles retorna 5 roles")
        void getAvailableRoles_ReturnsFiveRoles() {
            Map<String, RoleCommand.RoleDefinition> roles = RoleCommand.getAvailableRoles();

            assertEquals(5, roles.size());
            assertTrue(roles.containsKey("dev"));
            assertTrue(roles.containsKey("architect"));
            assertTrue(roles.containsKey("security"));
            assertTrue(roles.containsKey("qa"));
            assertTrue(roles.containsKey("ux"));
        }
    }

    @Nested
    @DisplayName("RoleDefinition Record")
    class RoleDefinitionTests {
        @Test
        @DisplayName("cada rol tiene name, description y systemPrompt")
        void roles_HaveAllFields() {
            Map<String, RoleCommand.RoleDefinition> roles = RoleCommand.getAvailableRoles();

            for (RoleCommand.RoleDefinition def : roles.values()) {
                assertNotNull(def.name());
                assertNotNull(def.description());
                assertNotNull(def.systemPrompt());
                assertFalse(def.name().isEmpty());
                assertFalse(def.description().isEmpty());
                assertFalse(def.systemPrompt().isEmpty());
            }
        }
    }

    private static class MockExecutionContext implements ExecutionContext {
        private final Path workingDirectory;
        private final List<String> messages = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> systemContexts = new ArrayList<>();

        MockExecutionContext(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        void clear() {
            messages.clear();
            successes.clear();
            warnings.clear();
            errors.clear();
            systemContexts.clear();
        }

        List<String> getMessages() { return messages; }
        List<String> getSuccesses() { return successes; }
        List<String> getWarnings() { return warnings; }
        List<String> getErrors() { return errors; }
        List<String> getSystemContexts() { return systemContexts; }

        boolean hasError() { return !errors.isEmpty(); }
        boolean hasSuccess() { return !successes.isEmpty(); }
        boolean hasWarning() { return !warnings.isEmpty(); }

        @Override public void print(String message) { messages.add(message); }
        @Override public void printSuccess(String message) { successes.add(message); }
        @Override public void printWarning(String message) { warnings.add(message); }
        @Override public void printError(String message) { errors.add(message); }
        @Override public void printDebug(String message) { }
        @Override public void addToContext(String content) { }
        @Override public void addToSystemContext(String content) { systemContexts.add(content); }
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
