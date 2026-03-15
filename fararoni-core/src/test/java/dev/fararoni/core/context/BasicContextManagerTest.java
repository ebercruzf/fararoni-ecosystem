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
package dev.fararoni.core.context;

import dev.fararoni.core.model.Message;
import dev.fararoni.core.tokenizer.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@DisplayName("BasicContextManager - Token Budgeting Tests")
class BasicContextManagerTest {
    private BasicContextManager contextManager;
    private MockTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        tokenizer = new MockTokenizer();
        contextManager = new BasicContextManager(tokenizer, 10000);
    }

    @Test
    @DisplayName("Debe retornar nombre de estrategia correcto")
    void shouldReturnCorrectStrategyName() {
        String strategyName = contextManager.getStrategyName();

        assertEquals("CORE (Manual Loading + Token Budgeting)", strategyName);
    }

    @Test
    @DisplayName("Debe retornar prioridad 0 para Core")
    void shouldReturnPriorityZeroForCore() {
        int priority = contextManager.getPriority();

        assertEquals(0, priority, "BasicContextManager debe tener prioridad 0");
    }

    @Test
    @DisplayName("Debe incluir system prompt en el contexto")
    void shouldIncludeSystemPromptInContext() {
        String systemPrompt = "You are a helpful assistant";

        String prompt = contextManager.assemblePrompt(
            systemPrompt,
            List.of(),
            List.of(),
            "Hello"
        );

        assertTrue(prompt.contains(systemPrompt),
            "El prompt debe incluir el system prompt");
    }

    @Test
    @DisplayName("Debe incluir query del usuario en el contexto")
    void shouldIncludeUserQueryInContext() {
        String query = "What is Java?";

        String prompt = contextManager.assemblePrompt(
            "System prompt",
            List.of(),
            List.of(),
            query
        );

        assertTrue(prompt.contains(query),
            "El prompt debe incluir la query del usuario");
    }

    @Test
    @DisplayName("Debe incluir archivos cargados en el contexto")
    void shouldIncludeLoadedFilesInContext() {
        List<String> files = List.of("File1.java content", "File2.java content");

        String prompt = contextManager.assemblePrompt(
            "System prompt",
            files,
            List.of(),
            "Analyze the files"
        );

        assertTrue(prompt.contains("File1.java content"),
            "El prompt debe incluir el contenido del archivo 1");
        assertTrue(prompt.contains("File2.java content"),
            "El prompt debe incluir el contenido del archivo 2");
    }

    @Test
    @DisplayName("Debe incluir historial de mensajes en el contexto")
    void shouldIncludeMessageHistoryInContext() {
        List<Message> history = List.of(
            Message.user("Previous question"),
            Message.assistant("Previous answer")
        );

        String prompt = contextManager.assemblePrompt(
            "System prompt",
            List.of(),
            history,
            "New question"
        );

        assertTrue(prompt.contains("Previous question"),
            "El prompt debe incluir preguntas anteriores");
        assertTrue(prompt.contains("Previous answer"),
            "El prompt debe incluir respuestas anteriores");
    }

    @Test
    @DisplayName("No debe lanzar excepción con listas vacías")
    void shouldNotThrowWithEmptyLists() {
        assertDoesNotThrow(() ->
            contextManager.assemblePrompt(
                "System prompt",
                List.of(),
                List.of(),
                "Query"
            )
        );
    }

    private static class MockTokenizer implements Tokenizer {
        @Override
        public List<Integer> encode(String text) {
            int tokenCount = Math.max(1, text.length() / 4);
            return java.util.stream.IntStream.range(0, tokenCount)
                .boxed()
                .toList();
        }

        @Override
        public String decode(List<Integer> tokens) {
            return "decoded_" + tokens.size();
        }

        @Override
        public List<String> tokenize(String text) {
            return List.of(text.split("\\s+"));
        }

        @Override
        public int countTokens(String text) {
            return Math.max(1, text.length() / 4);
        }
    }
}
