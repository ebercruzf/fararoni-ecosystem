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
import dev.fararoni.core.core.services.WebScraperService;
import dev.fararoni.core.core.services.WebScraperService.WebContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
@DisplayName("WebCommand")
class WebCommandTest {
    private WebCommand command;
    private MockExecutionContext mockContext;
    private MockWebScraperService mockScraper;

    @BeforeEach
    void setUp() {
        mockScraper = new MockWebScraperService();
        command = new WebCommand(mockScraper);
        mockContext = new MockExecutionContext();
    }

    @Nested
    @DisplayName("Metadatos del Comando")
    class MetadataTests {
        @Test
        @DisplayName("trigger es /web")
        void getTrigger_ReturnsWeb() {
            assertEquals("/web", command.getTrigger());
        }

        @Test
        @DisplayName("categoria es CONTEXT")
        void getCategory_ReturnsContext() {
            assertEquals(CommandCategory.CONTEXT, command.getCategory());
        }

        @Test
        @DisplayName("tiene aliases /fetch y /url")
        void getAliases_ReturnsFetchAndUrl() {
            String[] aliases = command.getAliases();

            assertNotNull(aliases);
            assertEquals(2, aliases.length);
            assertTrue(List.of(aliases).contains("/fetch"));
            assertTrue(List.of(aliases).contains("/url"));
        }

        @Test
        @DisplayName("usage contiene <url>")
        void getUsage_ContainsUrl() {
            assertTrue(command.getUsage().contains("<url>"));
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

            assertTrue(help.contains("Ejemplo"));
            assertTrue(help.contains("/web"));
        }
    }

    @Nested
    @DisplayName("Validacion de Input")
    class ValidationTests {
        @Test
        @DisplayName("args null muestra error de uso")
        void execute_NullArgs_ShowsUsageError() {
            command.execute(null, mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("Uso:"));
        }

        @Test
        @DisplayName("args vacio muestra error de uso")
        void execute_EmptyArgs_ShowsUsageError() {
            command.execute("", mockContext);

            assertTrue(mockContext.hasError());
        }

        @Test
        @DisplayName("args solo espacios muestra error de uso")
        void execute_BlankArgs_ShowsUsageError() {
            command.execute("   ", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Ejecucion Exitosa")
    class SuccessTests {
        @Test
        @DisplayName("ejecuta con URL valida")
        void execute_ValidUrl_CallsScraper() {
            mockScraper.setMockContent(new WebContent(
                "https://example.com",
                "Example",
                "Desc",
                "Content body"
            ));

            command.execute("example.com", mockContext);

            assertTrue(mockScraper.wasCalledWith("example.com"));
        }

        @Test
        @DisplayName("agrega contenido al contexto")
        void execute_ValidUrl_AddsToContext() {
            mockScraper.setMockContent(new WebContent(
                "https://example.com",
                "Example",
                "Desc",
                "Content body"
            ));

            command.execute("example.com", mockContext);

            assertFalse(mockContext.getContextContent().isEmpty());
            assertTrue(mockContext.getContextContent().contains("WEB SOURCE"));
        }

        @Test
        @DisplayName("muestra mensaje de exito con titulo y chars")
        void execute_ValidUrl_ShowsSuccess() {
            mockScraper.setMockContent(new WebContent(
                "https://example.com",
                "My Page Title",
                null,
                "Some content"
            ));

            command.execute("example.com", mockContext);

            assertTrue(mockContext.hasSuccess());
            String successMsg = mockContext.getSuccesses().get(0);
            assertTrue(successMsg.contains("My Page Title"));
            assertTrue(successMsg.contains("chars"));
        }

        @Test
        @DisplayName("imprime mensaje de conexion")
        void execute_ValidUrl_PrintsConnecting() {
            mockScraper.setMockContent(new WebContent(
                "https://example.com",
                "Title",
                null,
                "Body"
            ));

            command.execute("example.com", mockContext);

            assertTrue(mockContext.getMessages().stream()
                .anyMatch(m -> m.contains("Conectando")));
        }
    }

    @Nested
    @DisplayName("Manejo de Errores")
    class ErrorTests {
        @Test
        @DisplayName("IOException muestra error descriptivo")
        void execute_IOException_ShowsError() {
            mockScraper.setException(new IOException("Connection refused"));

            command.execute("bad-url.com", mockContext);

            assertTrue(mockContext.hasError());
            assertTrue(mockContext.getErrors().get(0).contains("Connection refused"));
        }

        @Test
        @DisplayName("Exception generica muestra error")
        void execute_GenericException_ShowsError() {
            mockScraper.setException(new RuntimeException("Unexpected error"));

            command.execute("url.com", mockContext);

            assertTrue(mockContext.hasError());
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("constructor por defecto crea su propio servicio")
        void defaultConstructor_CreatesService() {
            WebCommand cmd = new WebCommand();
            assertNotNull(cmd);
        }

        @Test
        @DisplayName("constructor con servicio inyectado usa ese servicio")
        void injectedConstructor_UsesProvidedService() {
            MockWebScraperService injected = new MockWebScraperService();
            injected.setMockContent(new WebContent("url", "title", "desc", "body"));

            WebCommand cmd = new WebCommand(injected);
            cmd.execute("test.com", mockContext);

            assertTrue(injected.wasCalledWith("test.com"));
        }
    }

    private static class MockWebScraperService extends WebScraperService {
        private WebContent mockContent;
        private Exception exception;
        private final List<String> fetchedUrls = new ArrayList<>();

        void setMockContent(WebContent content) {
            this.mockContent = content;
            this.exception = null;
        }

        void setException(Exception e) {
            this.exception = e;
            this.mockContent = null;
        }

        boolean wasCalledWith(String url) {
            return fetchedUrls.stream().anyMatch(u -> u.contains(url));
        }

        @Override
        public WebContent fetch(String url) throws IOException {
            fetchedUrls.add(url);
            if (exception != null) {
                if (exception instanceof IOException) {
                    throw (IOException) exception;
                }
                throw new RuntimeException(exception);
            }
            return mockContent;
        }
    }

    private static class MockExecutionContext implements ExecutionContext {
        private final List<String> messages = new ArrayList<>();
        private final List<String> successes = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> debugs = new ArrayList<>();
        private final List<String> contextContent = new ArrayList<>();
        private boolean debugMode = false;

        List<String> getMessages() { return messages; }
        List<String> getSuccesses() { return successes; }
        List<String> getWarnings() { return warnings; }
        List<String> getErrors() { return errors; }
        List<String> getDebugs() { return debugs; }
        String getContextContent() { return String.join("\n", contextContent); }

        boolean hasError() { return !errors.isEmpty(); }
        boolean hasSuccess() { return !successes.isEmpty(); }

        void setDebugMode(boolean mode) { this.debugMode = mode; }

        @Override public void print(String message) { messages.add(message); }
        @Override public void printSuccess(String message) { successes.add(message); }
        @Override public void printWarning(String message) { warnings.add(message); }
        @Override public void printError(String message) { errors.add(message); }
        @Override public void printDebug(String message) { if (debugMode) debugs.add(message); }
        @Override public void addToContext(String content) { contextContent.add(content); }
        @Override public void addToSystemContext(String content) { contextContent.add(content); }
        @Override public Path getWorkingDirectory() { return Path.of("."); }
        @Override public Optional<Path> getProjectRoot() { return Optional.empty(); }
        @Override public boolean isDebugMode() { return debugMode; }
        @Override public boolean isGitRepository() { return false; }
        @Override public Optional<String> getCurrentBranch() { return Optional.empty(); }
        @Override public <T> T withFileService(Function<FileServiceAccessor, T> op) { return null; }
        @Override public void logAudit(String action, String details) { }
        @Override public AgentUserInterface getUI() { return AgentUserInterface.NO_OP; }
    }
}
