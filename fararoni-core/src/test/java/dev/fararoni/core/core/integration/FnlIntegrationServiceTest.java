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
package dev.fararoni.core.core.integration;

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.ToolParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("FnlIntegrationService Tests")
class FnlIntegrationServiceTest {
    private FnlIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new FnlIntegrationService();
    }

    @Nested
    @DisplayName("Skill Registration")
    class SkillRegistrationTests {
        @Test
        @DisplayName("Should start with no skills")
        void shouldStartWithNoSkills() {
            assertThat(service.getSkillCount()).isZero();
            assertThat(service.getSkillNames()).isEmpty();
        }

        @Test
        @DisplayName("Should register skill")
        void shouldRegisterSkill() {
            service.registerSkill(new SimpleTestSkill());

            assertThat(service.getSkillCount()).isEqualTo(1);
            assertThat(service.hasSkill("SIMPLE")).isTrue();
            assertThat(service.getSkillNames()).contains("SIMPLE");
        }

        @Test
        @DisplayName("Should register multiple skills")
        void shouldRegisterMultipleSkills() {
            service.registerSkill(new SimpleTestSkill());
            service.registerSkill(new MathTestSkill());

            assertThat(service.getSkillCount()).isEqualTo(2);
            assertThat(service.hasSkill("SIMPLE")).isTrue();
            assertThat(service.hasSkill("MATH")).isTrue();
        }

        @Test
        @DisplayName("Should throw on duplicate skill registration")
        void shouldThrowOnDuplicateRegistration() {
            service.registerSkill(new SimpleTestSkill());

            assertThatThrownBy(() -> service.registerSkill(new SimpleTestSkill()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
        }

        @Test
        @DisplayName("Should unregister skill")
        void shouldUnregisterSkill() {
            service.registerSkill(new SimpleTestSkill());

            boolean removed = service.unregisterSkill("SIMPLE");

            assertThat(removed).isTrue();
            assertThat(service.hasSkill("SIMPLE")).isFalse();
            assertThat(service.getSkillCount()).isZero();
        }

        @Test
        @DisplayName("Should return false for unknown skill")
        void shouldReturnFalseForUnknownSkill() {
            assertThat(service.hasSkill("UNKNOWN")).isFalse();
            assertThat(service.unregisterSkill("UNKNOWN")).isFalse();
        }
    }

    @Nested
    @DisplayName("Prompt Generation")
    class PromptGenerationTests {
        @Test
        @DisplayName("Should generate empty tools JSON when no skills")
        void shouldGenerateEmptyToolsJson() {
            String json = service.generateSystemPrompt();

            assertThat(json).contains("\"tools\"");
            assertThat(json).contains("[]");
        }

        @Test
        @DisplayName("Should generate tools JSON with registered skills")
        void shouldGenerateToolsJsonWithSkills() {
            service.registerSkill(new SimpleTestSkill());

            String json = service.generateSystemPrompt();

            assertThat(json).contains("\"tools\"");
            assertThat(json).contains("\"SIMPLE\"");
            assertThat(json).contains("\"echo\"");
        }

        @Test
        @DisplayName("Should generate summary")
        void shouldGenerateSummary() {
            service.registerSkill(new SimpleTestSkill());

            String summary = service.generateToolsSummary();

            assertThat(summary).contains("SIMPLE");
            assertThat(summary).contains("echo");
        }

        @Test
        @DisplayName("Should generate instructions")
        void shouldGenerateInstructions() {
            service.registerSkill(new SimpleTestSkill());

            String instructions = service.generateInstructions();

            assertThat(instructions).contains("Available Tools");
            assertThat(instructions).contains("tool");
            assertThat(instructions).contains("action");
            assertThat(instructions).contains("params");
        }
    }

    @Nested
    @DisplayName("Request Parsing")
    class RequestParsingTests {
        @Test
        @DisplayName("Should parse valid JSON")
        void shouldParseValidJson() {
            String json = """
                {"tool":"SIMPLE","action":"echo","params":{"message":"Hello"}}
                """;

            Optional<ToolRequest> result = service.parseToolRequest(json);

            assertThat(result).isPresent();
            assertThat(result.get().toolName()).isEqualTo("SIMPLE");
            assertThat(result.get().action()).isEqualTo("echo");
            assertThat(result.get().params()).containsEntry("message", "Hello");
        }

        @Test
        @DisplayName("Should detect tool request in response")
        void shouldDetectToolRequest() {
            String response = """
                Here is the result: {"tool":"SIMPLE","action":"echo"}
                """;

            assertThat(service.containsToolRequest(response)).isTrue();
            assertThat(service.containsToolRequest("No JSON here")).isFalse();
        }

        @Test
        @DisplayName("Should extract JSON from mixed content")
        void shouldExtractJson() {
            String response = """
                Some text before
                {"tool":"SIMPLE","action":"echo"}
                Some text after
                """;

            String json = service.extractJson(response);

            assertThat(json).isNotNull();
            assertThat(json).startsWith("{");
            assertThat(json).endsWith("}");
        }

        @Test
        @DisplayName("Should return empty for invalid JSON")
        void shouldReturnEmptyForInvalidJson() {
            assertThat(service.parseToolRequest("not json")).isEmpty();
            assertThat(service.parseToolRequest(null)).isEmpty();
            assertThat(service.parseToolRequest("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Execution")
    class ExecutionTests {
        @BeforeEach
        void setUpSkills() {
            service.registerSkill(new SimpleTestSkill());
            service.registerSkill(new MathTestSkill());
        }

        @Test
        @DisplayName("Should execute simple action")
        void shouldExecuteSimpleAction() {
            ToolRequest request = ToolRequest.of("SIMPLE", "echo", Map.of("message", "Hello"));

            ToolResponse response = service.execute(request);

            assertThat(response.success()).isTrue();
            assertThat(response.result()).contains("Echo: Hello");
        }

        @Test
        @DisplayName("Should execute action with numeric params")
        void shouldExecuteActionWithNumericParams() {
            ToolRequest request = ToolRequest.of("MATH", "add", Map.of("a", 5, "b", 3));

            ToolResponse response = service.execute(request);

            assertThat(response.success()).isTrue();
            assertThat(response.result()).isEqualTo("8");
        }

        @Test
        @DisplayName("Should handle unknown skill")
        void shouldHandleUnknownSkill() {
            ToolRequest request = ToolRequest.of("UNKNOWN", "action", Map.of());

            ToolResponse response = service.execute(request);

            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).containsIgnoringCase("not found");
        }

        @Test
        @DisplayName("Should handle unknown action")
        void shouldHandleUnknownAction() {
            ToolRequest request = ToolRequest.of("SIMPLE", "unknown_action", Map.of());

            ToolResponse response = service.execute(request);

            assertThat(response.success()).isFalse();
            assertThat(response.errorMessage()).containsIgnoringCase("action");
        }

        @Test
        @DisplayName("Should parse and execute in one operation")
        void shouldParseAndExecute() {
            String json = """
                {"tool":"SIMPLE","action":"echo","params":{"message":"Test"}}
                """;

            Optional<ToolResponse> result = service.parseAndExecute(json);

            assertThat(result).isPresent();
            assertThat(result.get().success()).isTrue();
            assertThat(result.get().result()).contains("Echo: Test");
        }

        @Test
        @DisplayName("Should return empty when parse fails")
        void shouldReturnEmptyWhenParseFails() {
            Optional<ToolResponse> result = service.parseAndExecute("not json");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should check skill availability")
        void shouldCheckSkillAvailability() {
            assertThat(service.isSkillAvailable("SIMPLE")).isTrue();
            assertThat(service.isSkillAvailable("UNKNOWN")).isFalse();
        }
    }

    @Nested
    @DisplayName("Batch Execution")
    class BatchExecutionTests {
        @BeforeEach
        void setUpSkills() {
            service.registerSkill(new SimpleTestSkill());
            service.registerSkill(new MathTestSkill());
        }

        @Test
        @DisplayName("Should execute batch in parallel")
        void shouldExecuteBatchInParallel() {
            List<ToolRequest> requests = List.of(
                ToolRequest.of("SIMPLE", "echo", Map.of("message", "One")),
                ToolRequest.of("SIMPLE", "echo", Map.of("message", "Two")),
                ToolRequest.of("MATH", "add", Map.of("a", 1, "b", 2))
            );

            List<ToolResponse> responses = service.executeBatch(requests);

            assertThat(responses).hasSize(3);
            assertThat(responses.get(0).success()).isTrue();
            assertThat(responses.get(1).success()).isTrue();
            assertThat(responses.get(2).success()).isTrue();
        }

        @Test
        @DisplayName("Should handle empty batch")
        void shouldHandleEmptyBatch() {
            List<ToolResponse> responses = service.executeBatch(List.of());

            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("Should handle single item batch")
        void shouldHandleSingleItemBatch() {
            List<ToolRequest> requests = List.of(
                ToolRequest.of("SIMPLE", "echo", Map.of("message", "Solo"))
            );

            List<ToolResponse> responses = service.executeBatch(requests);

            assertThat(responses).hasSize(1);
            assertThat(responses.getFirst().success()).isTrue();
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {
        @Test
        @DisplayName("Should track active executions")
        void shouldTrackActiveExecutions() {
            assertThat(service.getActiveExecutions()).isZero();
        }

        @Test
        @DisplayName("Should provide registry access")
        void shouldProvideRegistryAccess() {
            assertThat(service.getRegistry()).isNotNull();
        }

        @Test
        @DisplayName("Should provide dispatcher access")
        void shouldProvideDispatcherAccess() {
            assertThat(service.getDispatcher()).isNotNull();
        }

        @Test
        @DisplayName("Should shutdown cleanly")
        void shouldShutdownCleanly() {
            service.registerSkill(new SimpleTestSkill());

            assertThatCode(() -> service.shutdown()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Real LLM Scenarios")
    class RealLlmScenarioTests {
        @BeforeEach
        void setUpSkills() {
            service.registerSkill(new SimpleTestSkill());
        }

        @Test
        @DisplayName("Should handle LLM response with text before JSON")
        void shouldHandleLlmResponseWithTextBeforeJson() {
            String llmResponse = """
                Let me execute that command for you:
                {"tool":"SIMPLE","action":"echo","params":{"message":"Hello from LLM"}}
                """;

            Optional<ToolResponse> result = service.parseAndExecute(llmResponse);

            assertThat(result).isPresent();
            assertThat(result.get().success()).isTrue();
        }

        @Test
        @DisplayName("Should handle LLM response with markdown code block")
        void shouldHandleLlmResponseWithMarkdownCodeBlock() {
            String llmResponse = """
                Here is the JSON:
                ```json
                {"tool":"SIMPLE","action":"echo","params":{"message":"Markdown"}}
                ```
                """;

            Optional<ToolResponse> result = service.parseAndExecute(llmResponse);

            assertThat(result).isPresent();
            assertThat(result.get().success()).isTrue();
        }

        @Test
        @DisplayName("Should handle lowercase tool name")
        void shouldHandleLowercaseToolName() {
            String json = """
                {"tool":"simple","action":"echo","params":{"message":"Lowercase"}}
                """;

            Optional<ToolResponse> result = service.parseAndExecute(json);

            assertThat(result).isPresent();
            assertThat(result.get().success()).isTrue();
        }
    }

    static class SimpleTestSkill implements ToolSkill {
        @Override
        public String getSkillName() {
            return "SIMPLE";
        }

        @Override
        public String getDescription() {
            return "Simple test skill";
        }

        @AgentAction(name = "echo", description = "Echoes back the message")
        public String echo(
            @ToolParameter(name = "message", description = "Message to echo") String message
        ) {
            return "Echo: " + message;
        }
    }

    static class MathTestSkill implements ToolSkill {
        @Override
        public String getSkillName() {
            return "MATH";
        }

        @Override
        public String getDescription() {
            return "Math operations";
        }

        @AgentAction(name = "add", description = "Adds two numbers")
        public int add(
            @ToolParameter(name = "a", description = "First number") int a,
            @ToolParameter(name = "b", description = "Second number") int b
        ) {
            return a + b;
        }

        @AgentAction(name = "multiply", description = "Multiplies two numbers")
        public int multiply(
            @ToolParameter(name = "a", description = "First number") int a,
            @ToolParameter(name = "b", description = "Second number") int b
        ) {
            return a * b;
        }
    }
}
