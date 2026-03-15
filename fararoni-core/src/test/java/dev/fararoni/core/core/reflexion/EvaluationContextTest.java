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
package dev.fararoni.core.core.reflexion;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("EvaluationContext - Contexto de Evaluacion")
class EvaluationContextTest {
    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {
        @Test
        @DisplayName("empty() crea contexto vacio")
        void empty_createsEmptyContext() {
            EvaluationContext context = EvaluationContext.empty();

            assertNotNull(context);
            assertEquals("", context.getUserPrompt());
            assertEquals(EvaluationContext.ResponseType.TEXT, context.getResponseType());
            assertTrue(context.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("ofPrompt() crea contexto con prompt")
        void ofPrompt_createsContextWithPrompt() {
            EvaluationContext context = EvaluationContext.ofPrompt("Test prompt");

            assertEquals("Test prompt", context.getUserPrompt());
        }

        @Test
        @DisplayName("forCode() crea contexto para codigo")
        void forCode_createsCodeContext() {
            EvaluationContext context = EvaluationContext.forCode("Implementa add", "python");

            assertEquals("Implementa add", context.getUserPrompt());
            assertEquals(EvaluationContext.ResponseType.CODE, context.getResponseType());
            assertEquals("python", context.getMetadata("language").orElse(null));
        }
    }

    @Nested
    @DisplayName("forTestRetry() - Factory para Self-Correction")
    class ForTestRetryTests {
        @Test
        @DisplayName("forTestRetry() crea contexto con testOutput")
        void forTestRetry_createsContextWithTestOutput() {
            String testOutput = "FAILED test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.forTestRetry(
                "Implementa add",
                testOutput,
                1
            );

            assertEquals("Implementa add", context.getUserPrompt());
            assertEquals(EvaluationContext.ResponseType.CODE, context.getResponseType());

            Optional<String> output = context.getMetadata("testOutput");
            assertTrue(output.isPresent());
            assertEquals(testOutput, output.get());
        }

        @Test
        @DisplayName("forTestRetry() incluye attemptNumber")
        void forTestRetry_includesAttemptNumber() {
            EvaluationContext context = EvaluationContext.forTestRetry(
                "prompt",
                "test output",
                3
            );

            Optional<Integer> attempt = context.getMetadata("attemptNumber");
            assertTrue(attempt.isPresent());
            assertEquals(3, attempt.get());
        }

        @Test
        @DisplayName("forTestRetry() con historial incluye history")
        void forTestRetry_withHistory_includesHistory() {
            List<String> history = List.of(
                "Intento 1: def add(a, b): return a",
                "Feedback: Falta sumar b"
            );

            EvaluationContext context = EvaluationContext.forTestRetry(
                "prompt",
                "test output",
                2,
                history
            );

            assertTrue(context.hasHistory());
            assertEquals(2, context.getConversationHistory().size());
            assertEquals("Intento 1: def add(a, b): return a", context.getConversationHistory().get(0));
        }

        @Test
        @DisplayName("forTestRetry() es compatible con TestOutputCritic")
        void forTestRetry_compatibleWithTestOutputCritic() {
            String testOutput = "FAILED test_add - AssertionError: assert 5 == 4";

            EvaluationContext context = EvaluationContext.forTestRetry(
                "Implementa add",
                testOutput,
                2
            );

            assertTrue(context.hasMetadata("testOutput"));
            assertTrue(context.hasMetadata("attemptNumber"));
            assertEquals(testOutput, context.getMetadataOrDefault("testOutput", ""));
            assertEquals(2, (int) context.getMetadataOrDefault("attemptNumber", 0));
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {
        @Test
        @DisplayName("Builder construye contexto completo")
        void builder_createsCompleteContext() {
            EvaluationContext context = EvaluationContext.builder()
                .userPrompt("Test prompt")
                .responseType(EvaluationContext.ResponseType.JSON)
                .metadata("key1", "value1")
                .metadata("key2", 42)
                .sessionId("session-123")
                .turnNumber(5)
                .build();

            assertEquals("Test prompt", context.getUserPrompt());
            assertEquals(EvaluationContext.ResponseType.JSON, context.getResponseType());
            assertEquals("value1", context.getMetadata("key1").orElse(null));
            assertEquals(42, context.getMetadata("key2").orElse(null));
            assertEquals("session-123", context.getSessionId());
            assertEquals(5, context.getTurnNumber());
        }

        @Test
        @DisplayName("Builder con metadata map agrega todas las keys")
        void builder_withMetadataMap_addsAllKeys() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata(java.util.Map.of("a", 1, "b", 2, "c", 3))
                .build();

            assertEquals(1, context.getMetadata("a").orElse(null));
            assertEquals(2, context.getMetadata("b").orElse(null));
            assertEquals(3, context.getMetadata("c").orElse(null));
        }
    }

    @Nested
    @DisplayName("Metadata Access")
    class MetadataTests {
        @Test
        @DisplayName("getMetadata() retorna Optional.empty para key inexistente")
        void getMetadata_returnsEmptyForMissingKey() {
            EvaluationContext context = EvaluationContext.empty();

            assertTrue(context.getMetadata("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("getMetadataOrDefault() retorna default para key inexistente")
        void getMetadataOrDefault_returnsDefaultForMissingKey() {
            EvaluationContext context = EvaluationContext.empty();

            assertEquals("default", context.getMetadataOrDefault("missing", "default"));
            assertEquals(42, (int) context.getMetadataOrDefault("missing", 42));
        }

        @Test
        @DisplayName("hasMetadata() verifica existencia de key")
        void hasMetadata_checksKeyExistence() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata("exists", "value")
                .build();

            assertTrue(context.hasMetadata("exists"));
            assertFalse(context.hasMetadata("notexists"));
        }

        @Test
        @DisplayName("withMetadata() crea nuevo contexto con metadata adicional")
        void withMetadata_createsNewContextWithAdditionalMetadata() {
            EvaluationContext original = EvaluationContext.builder()
                .userPrompt("prompt")
                .metadata("key1", "value1")
                .build();

            EvaluationContext modified = original.withMetadata("key2", "value2");

            assertFalse(original.hasMetadata("key2"));

            assertTrue(modified.hasMetadata("key1"));
            assertTrue(modified.hasMetadata("key2"));
            assertEquals("prompt", modified.getUserPrompt());
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityTests {
        @Test
        @DisplayName("expectsCode() retorna true para CODE")
        void expectsCode_trueForCode() {
            EvaluationContext context = EvaluationContext.builder()
                .responseType(EvaluationContext.ResponseType.CODE)
                .build();

            assertTrue(context.expectsCode());
            assertFalse(context.expectsJson());
        }

        @Test
        @DisplayName("expectsJson() retorna true para JSON")
        void expectsJson_trueForJson() {
            EvaluationContext context = EvaluationContext.builder()
                .responseType(EvaluationContext.ResponseType.JSON)
                .build();

            assertTrue(context.expectsJson());
            assertFalse(context.expectsCode());
        }

        @Test
        @DisplayName("hasHistory() verifica historial no vacio")
        void hasHistory_checksNonEmptyHistory() {
            EvaluationContext withHistory = EvaluationContext.builder()
                .conversationHistory(List.of("msg1", "msg2"))
                .build();

            EvaluationContext noHistory = EvaluationContext.empty();

            assertTrue(withHistory.hasHistory());
            assertFalse(noHistory.hasHistory());
        }

        @Test
        @DisplayName("getLastHistoryMessage() retorna ultimo mensaje")
        void getLastHistoryMessage_returnsLastMessage() {
            EvaluationContext context = EvaluationContext.builder()
                .conversationHistory(List.of("first", "second", "last"))
                .build();

            Optional<String> last = context.getLastHistoryMessage();

            assertTrue(last.isPresent());
            assertEquals("last", last.get());
        }

        @Test
        @DisplayName("getLastHistoryMessage() retorna empty sin historial")
        void getLastHistoryMessage_emptyWithoutHistory() {
            EvaluationContext context = EvaluationContext.empty();

            assertTrue(context.getLastHistoryMessage().isEmpty());
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {
        @Test
        @DisplayName("getMetadata() retorna mapa inmutable")
        void getMetadata_returnsImmutableMap() {
            EvaluationContext context = EvaluationContext.builder()
                .metadata("key", "value")
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                context.getMetadata().put("new", "value"));
        }

        @Test
        @DisplayName("getConversationHistory() retorna lista inmutable")
        void getConversationHistory_returnsImmutableList() {
            EvaluationContext context = EvaluationContext.builder()
                .conversationHistory(List.of("msg"))
                .build();

            assertThrows(UnsupportedOperationException.class, () ->
                context.getConversationHistory().add("new"));
        }
    }
}
