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
package dev.fararoni.core.core.prompt;

import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.core.persona.Personas;
import dev.fararoni.core.core.memory.GraphRAGService;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Prompt Builder Tests")
class PromptBuilderTest {
    @Nested
    @DisplayName("PromptBuilder Basic Tests")
    class PromptBuilderBasicTests {
        @Test
        @DisplayName("create() crea builder vacio")
        void create_createsEmptyBuilder() {
            PromptBuilder builder = PromptBuilder.create();
            assertNotNull(builder);
        }

        @Test
        @DisplayName("build() con prompt vacio retorna string vacio")
        void build_withEmptyPrompt_returnsEmpty() {
            String prompt = PromptBuilder.create().build();
            assertTrue(prompt.isEmpty() || prompt.isBlank());
        }

        @Test
        @DisplayName("systemPrompt() agrega system section")
        void systemPrompt_addsSystemSection() {
            String prompt = PromptBuilder.create()
                .systemPrompt("You are a helpful assistant.")
                .build();

            assertTrue(prompt.contains("<system>"));
            assertTrue(prompt.contains("You are a helpful assistant."));
            assertTrue(prompt.contains("</system>"));
        }

        @Test
        @DisplayName("userQuery() agrega user_query section")
        void userQuery_addsUserQuerySection() {
            String prompt = PromptBuilder.create()
                .userQuery("What is Java?")
                .build();

            assertTrue(prompt.contains("<user_query>"));
            assertTrue(prompt.contains("What is Java?"));
            assertTrue(prompt.contains("</user_query>"));
        }

        @Test
        @DisplayName("prompt completo tiene todas las secciones en orden")
        void build_fullPrompt_hasSectionsInOrder() {
            String prompt = PromptBuilder.create()
                .systemPrompt("System instructions")
                .userQuery("User question")
                .build();

            int systemIndex = prompt.indexOf("<system>");
            int userIndex = prompt.indexOf("<user_query>");

            assertTrue(systemIndex < userIndex, "system should come before user_query");
        }
    }

    @Nested
    @DisplayName("Persona Integration Tests")
    class PersonaTests {
        @Test
        @DisplayName("withPersona() agrega seccion persona")
        void withPersona_addsPersonaSection() {
            Persona dev = Personas.DEVELOPER;

            String prompt = PromptBuilder.create()
                .systemPrompt("Base prompt")
                .withPersona(dev)
                .userQuery("Help me code")
                .build();

            assertTrue(prompt.contains("<persona>"));
            assertTrue(prompt.contains("Developer"));
            assertTrue(prompt.contains("</persona>"));
        }

        @Test
        @DisplayName("persona incluye expertise")
        void withPersona_includesExpertise() {
            Persona dev = Personas.DEVELOPER;

            String prompt = PromptBuilder.create()
                .withPersona(dev)
                .build();

            assertTrue(prompt.contains("Expertise:"));
        }

        @Test
        @DisplayName("persona incluye communication style")
        void withPersona_includesCommunicationStyle() {
            Persona analyst = Personas.ANALYST;

            String prompt = PromptBuilder.create()
                .withPersona(analyst)
                .build();

            assertTrue(prompt.contains("Communication Style:"));
        }
    }

    @Nested
    @DisplayName("RAG Context Tests")
    class RAGContextTests {
        @Test
        @DisplayName("withRAGContext() agrega seccion context")
        void withRAGContext_addsContextSection() {
            String prompt = PromptBuilder.create()
                .withRAGContext("Some relevant context from RAG")
                .build();

            assertTrue(prompt.contains("<context>"));
            assertTrue(prompt.contains("Some relevant context from RAG"));
            assertTrue(prompt.contains("</context>"));
        }

        @Test
        @DisplayName("RAG context vacio no agrega seccion")
        void withRAGContext_emptyContext_noSection() {
            String prompt = PromptBuilder.create()
                .withRAGContext("")
                .userQuery("Question")
                .build();

            assertFalse(prompt.contains("<context>"));
        }

        @Test
        @DisplayName("withRAGContext() con GraphRAGService")
        void withRAGContext_withService() {
            GraphRAGService rag = GraphRAGService.create();
            rag.addFact("file:App.java", "contains", "class:App");

            String prompt = PromptBuilder.create()
                .withRAGContext(rag, "Tell me about App", 10)
                .build();

            assertNotNull(prompt);
        }
    }

    @Nested
    @DisplayName("Tools Tests")
    class ToolsTests {
        @Test
        @DisplayName("addTool() agrega seccion available_tools")
        void addTool_addsToolsSection() {
            String prompt = PromptBuilder.create()
                .addTool("file_read", "Read a file", "path: string")
                .build();

            assertTrue(prompt.contains("<available_tools>"));
            assertTrue(prompt.contains("file_read"));
            assertTrue(prompt.contains("Read a file"));
            assertTrue(prompt.contains("</available_tools>"));
        }

        @Test
        @DisplayName("multiples tools se agregan correctamente")
        void addTool_multipleTools() {
            String prompt = PromptBuilder.create()
                .addTool("file_read", "Read file", "path")
                .addTool("file_write", "Write file", "path, content")
                .addTool("bash", "Execute command", "command")
                .build();

            assertTrue(prompt.contains("file_read"));
            assertTrue(prompt.contains("file_write"));
            assertTrue(prompt.contains("bash"));
        }

        @Test
        @DisplayName("addTools() con lista")
        void addTools_withList() {
            List<PromptBuilder.ToolDescription> tools = List.of(
                new PromptBuilder.ToolDescription("tool1", "desc1", "params1"),
                new PromptBuilder.ToolDescription("tool2", "desc2", "params2")
            );

            String prompt = PromptBuilder.create()
                .addTools(tools)
                .build();

            assertTrue(prompt.contains("tool1"));
            assertTrue(prompt.contains("tool2"));
        }
    }

    @Nested
    @DisplayName("Constraints Tests")
    class ConstraintsTests {
        @Test
        @DisplayName("addConstraint() agrega seccion constraints")
        void addConstraint_addsSection() {
            String prompt = PromptBuilder.create()
                .addConstraint("Always use best practices")
                .build();

            assertTrue(prompt.contains("<constraints>"));
            assertTrue(prompt.contains("Always use best practices"));
            assertTrue(prompt.contains("</constraints>"));
        }

        @Test
        @DisplayName("multiples constraints como bullet points")
        void addConstraint_multipleBullets() {
            String prompt = PromptBuilder.create()
                .addConstraint("Be concise")
                .addConstraint("Use examples")
                .addConstraint("Validate inputs")
                .build();

            int dashCount = prompt.split("- ").length - 1;
            assertTrue(dashCount >= 3);
        }

        @Test
        @DisplayName("constraint blank se ignora")
        void addConstraint_blankIgnored() {
            String prompt = PromptBuilder.create()
                .addConstraint("Valid constraint")
                .addConstraint("")
                .addConstraint("  ")
                .build();

            int dashCount = prompt.split("- ").length - 1;
            assertEquals(1, dashCount);
        }
    }

    @Nested
    @DisplayName("Examples Tests")
    class ExamplesTests {
        @Test
        @DisplayName("addExample() agrega seccion examples")
        void addExample_addsSection() {
            String prompt = PromptBuilder.create()
                .addExample("Input text", "Output text")
                .build();

            assertTrue(prompt.contains("<examples>"));
            assertTrue(prompt.contains("Input: Input text"));
            assertTrue(prompt.contains("Output: Output text"));
            assertTrue(prompt.contains("</examples>"));
        }

        @Test
        @DisplayName("example con explicacion")
        void addExample_withExplanation() {
            String prompt = PromptBuilder.create()
                .addExample("2 + 2", "4", "Basic addition")
                .build();

            assertTrue(prompt.contains("Explanation: Basic addition"));
        }

        @Test
        @DisplayName("multiples ejemplos numerados")
        void addExample_multipleNumbered() {
            String prompt = PromptBuilder.create()
                .addExample("A", "B")
                .addExample("C", "D")
                .build();

            assertTrue(prompt.contains("Example 1:"));
            assertTrue(prompt.contains("Example 2:"));
        }
    }

    @Nested
    @DisplayName("Variables Tests")
    class VariablesTests {
        @Test
        @DisplayName("variable() reemplaza {{name}}")
        void variable_replacesDoubleBraces() {
            String prompt = PromptBuilder.create()
                .systemPrompt("Hello {{name}}, welcome!")
                .variable("name", "Alice")
                .build();

            assertTrue(prompt.contains("Hello Alice, welcome!"));
            assertFalse(prompt.contains("{{name}}"));
        }

        @Test
        @DisplayName("variable() reemplaza {name}")
        void variable_replacesSingleBraces() {
            String prompt = PromptBuilder.create()
                .systemPrompt("Hello {name}, welcome!")
                .variable("name", "Bob")
                .build();

            assertTrue(prompt.contains("Hello Bob, welcome!"));
            assertFalse(prompt.contains("{name}"));
        }

        @Test
        @DisplayName("variables() con mapa")
        void variables_withMap() {
            Map<String, String> vars = Map.of(
                "user", "Charlie",
                "role", "admin"
            );

            String prompt = PromptBuilder.create()
                .systemPrompt("User: {{user}}, Role: {{role}}")
                .variables(vars)
                .build();

            assertTrue(prompt.contains("User: Charlie"));
            assertTrue(prompt.contains("Role: admin"));
        }
    }

    @Nested
    @DisplayName("Build Messages Tests")
    class BuildMessagesTests {
        @Test
        @DisplayName("buildMessages() retorna lista de mensajes")
        void buildMessages_returnsList() {
            List<String[]> messages = PromptBuilder.create()
                .systemPrompt("System instructions")
                .userQuery("User question")
                .buildMessages();

            assertFalse(messages.isEmpty());
        }

        @Test
        @DisplayName("messages incluye system y user")
        void buildMessages_includesSystemAndUser() {
            List<String[]> messages = PromptBuilder.create()
                .systemPrompt("System instructions")
                .userQuery("User question")
                .buildMessages();

            boolean hasSystem = messages.stream()
                .anyMatch(m -> m[0].equals("system"));
            boolean hasUser = messages.stream()
                .anyMatch(m -> m[0].equals("user"));

            assertTrue(hasSystem);
            assertTrue(hasUser);
        }

        @Test
        @DisplayName("examples como user/assistant pairs")
        void buildMessages_examplesAsPairs() {
            List<String[]> messages = PromptBuilder.create()
                .systemPrompt("System")
                .addExample("Question?", "Answer!")
                .userQuery("Real question")
                .buildMessages();

            assertTrue(messages.size() >= 4);
        }
    }

    @Nested
    @DisplayName("Complete Prompt Tests")
    class CompletePromptTests {
        @Test
        @DisplayName("prompt completo con todas las secciones")
        void build_completePrompt() {
            String prompt = PromptBuilder.create()
                .systemPrompt("You are a {{role}}.")
                .withPersona(Personas.DEVELOPER)
                .addConstraint("Be helpful")
                .addConstraint("Be concise")
                .withRAGContext("Context: App.java contains User class")
                .addTool("file_read", "Read file", "path")
                .addExample("How to?", "Like this.")
                .variable("role", "coding assistant")
                .userQuery("Help me code")
                .build();

            assertTrue(prompt.contains("<system>"));
            assertTrue(prompt.contains("<persona>"));
            assertTrue(prompt.contains("<constraints>"));
            assertTrue(prompt.contains("<context>"));
            assertTrue(prompt.contains("<available_tools>"));
            assertTrue(prompt.contains("<examples>"));
            assertTrue(prompt.contains("<user_query>"));

            assertTrue(prompt.contains("coding assistant"));

            int systemIndex = prompt.indexOf("<system>");
            int personaIndex = prompt.indexOf("<persona>");
            int constraintsIndex = prompt.indexOf("<constraints>");
            int contextIndex = prompt.indexOf("<context>");
            int toolsIndex = prompt.indexOf("<available_tools>");
            int examplesIndex = prompt.indexOf("<examples>");
            int userIndex = prompt.indexOf("<user_query>");

            assertTrue(systemIndex < personaIndex);
            assertTrue(personaIndex < constraintsIndex);
            assertTrue(constraintsIndex < contextIndex);
            assertTrue(contextIndex < toolsIndex);
            assertTrue(toolsIndex < examplesIndex);
            assertTrue(examplesIndex < userIndex);
        }
    }
}
