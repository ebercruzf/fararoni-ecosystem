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
package dev.fararoni.core.core.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fararoni.core.core.clients.AgentClient.ToolCall;
import dev.fararoni.core.service.FilesystemService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("A: Protocolo Cognitivo Qwen")
class QwenProtocolAdapterTest {
    @TempDir
    Path tempDir;

    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private FilesystemService filesystemService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        toolRegistry = new ToolRegistry();
        filesystemService = new FilesystemService(tempDir);

        toolExecutor = new ToolExecutor(tempDir, filesystemService);
    }

    @Nested
    @DisplayName("ToolRegistry: Definiciones de Herramientas Qwen")
    class ToolRegistryTests {
        @Test
        @DisplayName("Debe incluir TaskCreate en herramientas disponibles")
        void shouldIncludeTaskCreateTool() {
            List<ObjectNode> tools = toolRegistry.getAvailableTools(false);

            boolean hasTaskCreate = tools.stream()
                .anyMatch(tool -> "TaskCreate".equals(
                    tool.path("function").path("name").asText()));

            assertThat(hasTaskCreate)
                .as("ToolRegistry debe incluir TaskCreate")
                .isTrue();
        }

        @Test
        @DisplayName("Debe incluir TaskUpdate en herramientas disponibles")
        void shouldIncludeTaskUpdateTool() {
            List<ObjectNode> tools = toolRegistry.getAvailableTools(false);

            boolean hasTaskUpdate = tools.stream()
                .anyMatch(tool -> "TaskUpdate".equals(
                    tool.path("function").path("name").asText()));

            assertThat(hasTaskUpdate)
                .as("ToolRegistry debe incluir TaskUpdate")
                .isTrue();
        }

        @Test
        @DisplayName("Debe incluir WriteFile (alias de fs_write)")
        void shouldIncludeWriteFileTool() {
            List<ObjectNode> tools = toolRegistry.getAvailableTools(false);

            boolean hasWriteFile = tools.stream()
                .anyMatch(tool -> "WriteFile".equals(
                    tool.path("function").path("name").asText()));

            assertThat(hasWriteFile)
                .as("ToolRegistry debe incluir WriteFile")
                .isTrue();
        }

        @Test
        @DisplayName("Debe incluir ReadFile (alias de fs_read)")
        void shouldIncludeReadFileTool() {
            List<ObjectNode> tools = toolRegistry.getAvailableTools(false);

            boolean hasReadFile = tools.stream()
                .anyMatch(tool -> "ReadFile".equals(
                    tool.path("function").path("name").asText()));

            assertThat(hasReadFile)
                .as("ToolRegistry debe incluir ReadFile")
                .isTrue();
        }

        @Test
        @DisplayName("TaskCreate debe tener parámetros subject y description requeridos")
        void taskCreateShouldHaveRequiredParameters() {
            List<ObjectNode> tools = toolRegistry.getAvailableTools(false);

            ObjectNode taskCreate = tools.stream()
                .filter(tool -> "TaskCreate".equals(
                    tool.path("function").path("name").asText()))
                .findFirst()
                .orElseThrow();

            JsonNode required = taskCreate.path("function").path("parameters").path("required");

            assertThat(required.isArray()).isTrue();
            assertThat(required.toString()).contains("subject");
            assertThat(required.toString()).contains("description");
        }

        @Test
        @DisplayName("WriteFile debe usar filePath (no path) como parámetro")
        void writeFileShouldUseFilePathParameter() {
            List<ObjectNode> tools = toolRegistry.getAvailableTools(false);

            ObjectNode writeFile = tools.stream()
                .filter(tool -> "WriteFile".equals(
                    tool.path("function").path("name").asText()))
                .findFirst()
                .orElseThrow();

            JsonNode properties = writeFile.path("function").path("parameters").path("properties");

            assertThat(properties.has("filePath"))
                .as("WriteFile debe usar 'filePath' (nombre nativo Qwen)")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("ToolExecutor: Handlers Qwen")
    class ToolExecutorTests {
        @Test
        @DisplayName("TaskCreate debe generar taskId y retornar JSON válido")
        void taskCreateShouldGenerateValidResponse() throws Exception {
            String args = """
                {
                    "subject": "Implementar Auth",
                    "description": "Agregar autenticación JWT",
                    "activeForm": "Analizando requisitos..."
                }
                """;
            ToolCall toolCall = new ToolCall("TaskCreate", args);

            ToolExecutionResult result = toolExecutor.executeTool(toolCall);

            assertThat(result.success()).isTrue();

            JsonNode response = mapper.readTree(result.message());
            assertThat(response.has("taskId")).isTrue();
            assertThat(response.path("taskId").asText()).startsWith("task-");
            assertThat(response.path("subject").asText()).isEqualTo("Implementar Auth");
            assertThat(response.path("status").asText()).isEqualTo("pending");
            assertThat(response.path("activeForm").asText()).isEqualTo("Analizando requisitos...");
            assertThat(response.has("blocks")).isTrue();
            assertThat(response.has("blockedBy")).isTrue();
        }

        @Test
        @DisplayName("TaskUpdate debe actualizar estado correctamente")
        void taskUpdateShouldUpdateStatus() throws Exception {
            String args = """
                {
                    "taskId": "task-123456",
                    "status": "completed",
                    "comment": "Tarea finalizada exitosamente"
                }
                """;
            ToolCall toolCall = new ToolCall("TaskUpdate", args);

            ToolExecutionResult result = toolExecutor.executeTool(toolCall);

            assertThat(result.success()).isTrue();

            JsonNode response = mapper.readTree(result.message());
            assertThat(response.path("id").asText()).isEqualTo("task-123456");
            assertThat(response.path("status").asText()).isEqualTo("completed");
            assertThat(response.path("updated").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("WriteFile debe crear archivo usando filePath")
        void writeFileShouldCreateFileUsingFilePath() throws Exception {
            Path targetFile = tempDir.resolve("test-qwen.txt");
            String args = String.format("""
                {
                    "filePath": "%s",
                    "content": "Contenido creado por Qwen"
                }
                """, targetFile.toString().replace("\\", "\\\\"));
            ToolCall toolCall = new ToolCall("WriteFile", args);

            ToolExecutionResult result = toolExecutor.executeTool(toolCall);

            assertThat(result.success()).isTrue();
            assertThat(Files.exists(targetFile)).isTrue();
            assertThat(Files.readString(targetFile)).isEqualTo("Contenido creado por Qwen");
        }

        @Test
        @DisplayName("ReadFile debe leer archivo usando filePath")
        void readFileShouldReadFileUsingFilePath() throws Exception {
            Path sourceFile = tempDir.resolve("source-qwen.txt");
            Files.writeString(sourceFile, "Contenido para leer");

            String args = String.format("""
                {
                    "filePath": "%s"
                }
                """, sourceFile.toString().replace("\\", "\\\\"));
            ToolCall toolCall = new ToolCall("ReadFile", args);

            ToolExecutionResult result = toolExecutor.executeTool(toolCall);

            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("Contenido para leer");
        }

        @Test
        @DisplayName("WriteFile debe fallar si falta filePath")
        void writeFileShouldFailWithoutFilePath() throws Exception {
            String args = """
                {
                    "content": "Contenido sin path"
                }
                """;
            ToolCall toolCall = new ToolCall("WriteFile", args);

            ToolExecutionResult result = toolExecutor.executeTool(toolCall);

            assertThat(result.success()).isFalse();
        }

        @Test
        @DisplayName("TaskCreate genera activeForm automático si no se proporciona")
        void taskCreateShouldGenerateActiveFormFallback() throws Exception {
            String args = """
                {
                    "subject": "Tarea Simple",
                    "description": "Sin contexto UI"
                }
                """;
            ToolCall toolCall = new ToolCall("TaskCreate", args);

            ToolExecutionResult result = toolExecutor.executeTool(toolCall);

            assertThat(result.success()).isTrue();

            JsonNode response = mapper.readTree(result.message());
            assertThat(response.path("subject").asText()).isEqualTo("Tarea Simple");
            assertThat(response.path("activeForm").asText()).isEqualTo("Iniciando: Tarea Simple");
        }

        @Test
        @DisplayName("TaskUpdate debe aceptar todos los estados válidos")
        void taskUpdateShouldAcceptAllValidStatuses() throws Exception {
            String[] validStatuses = {"in_progress", "completed", "failed", "blocked"};

            for (String status : validStatuses) {
                String args = String.format("""
                    {
                        "taskId": "task-test",
                        "status": "%s"
                    }
                    """, status);
                ToolCall toolCall = new ToolCall("TaskUpdate", args);

                ToolExecutionResult result = toolExecutor.executeTool(toolCall);

                assertThat(result.success())
                    .as("TaskUpdate debe aceptar status: " + status)
                    .isTrue();

                JsonNode response = mapper.readTree(result.message());
                assertThat(response.path("status").asText()).isEqualTo(status);
            }
        }
    }

    @Nested
    @DisplayName("Integración: Flujo Completo Task-Driven")
    class IntegrationTests {
        @Test
        @DisplayName("Flujo completo: TaskCreate → WriteFile → TaskUpdate")
        void fullTaskDrivenFlow() throws Exception {
            String createArgs = """
                {
                    "subject": "Crear archivo config",
                    "description": "Crear config.json para el proyecto",
                    "activeForm": "Preparando configuración..."
                }
                """;
            ToolCall createCall = new ToolCall("TaskCreate", createArgs);
            ToolExecutionResult createResult = toolExecutor.executeTool(createCall);

            assertThat(createResult.success()).isTrue();
            String taskId = mapper.readTree(createResult.message()).path("taskId").asText();

            Path configFile = tempDir.resolve("config.json");
            String writeArgs = String.format("""
                {
                    "filePath": "%s",
                    "content": "{\\"version\\": \\"1.0\\"}"
                }
                """, configFile.toString().replace("\\", "\\\\"));
            ToolCall writeCall = new ToolCall("WriteFile", writeArgs);
            ToolExecutionResult writeResult = toolExecutor.executeTool(writeCall);

            assertThat(writeResult.success()).isTrue();
            assertThat(Files.exists(configFile)).isTrue();

            String updateArgs = String.format("""
                {
                    "taskId": "%s",
                    "status": "completed",
                    "comment": "Archivo creado exitosamente"
                }
                """, taskId);
            ToolCall updateCall = new ToolCall("TaskUpdate", updateArgs);
            ToolExecutionResult updateResult = toolExecutor.executeTool(updateCall);

            assertThat(updateResult.success()).isTrue();
            JsonNode updateResponse = mapper.readTree(updateResult.message());
            assertThat(updateResponse.path("status").asText()).isEqualTo("completed");
        }
    }
}
