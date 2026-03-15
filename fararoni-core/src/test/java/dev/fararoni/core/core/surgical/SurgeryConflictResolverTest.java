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
package dev.fararoni.core.core.surgical;

import dev.fararoni.core.core.agents.RabbitAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SurgeryConflictResolver Tests")
class SurgeryConflictResolverTest {
    @Mock
    private RabbitAgent rabbitAgent;

    private SurgeryConflictResolver resolver;
    private OverlapDetector detector;

    @BeforeEach
    void setUp() {
        resolver = new SurgeryConflictResolver(rabbitAgent);
        detector = new OverlapDetector();
    }

    @Nested
    @DisplayName("Escenario 1: The Overlapping Heart - Colision Semantica")
    class OverlappingHeartTests {
        @Test
        @DisplayName("Detecta colision cuando dos parches modifican la misma linea")
        void testOverlapDetection() {
            String source = "public void addFile(File f) { files.add(f); }";
            List<EditBlock> conflictingEdits = List.of(
                new EditBlock("A", "files.add(f);", "if(f!=null) files.add(f);", 1, 0, 0),
                new EditBlock("B", "files.add(f);", "files.add(clean(f));", 1, 0, 0)
            );

            assertThrows(OverlapConflictException.class, () ->
                detector.validate(conflictingEdits)
            );
        }

        @Test
        @DisplayName("Resolver con RabbitAgent que falla lanza SurgicalException")
        void testResolverWithFailingRabbitThrows() {
            String source = "public void addFile(File f) { files.add(f); }";
            List<EditBlock> conflictingEdits = List.of(
                new EditBlock("A", "files.add(f);", "if(f!=null) files.add(f);", 1, 0, 0),
                new EditBlock("B", "files.add(f);", "files.add(clean(f));", 1, 0, 0)
            );

            when(rabbitAgent.generateRaw(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("LLM error"));

            assertThrows(SurgicalException.class, () ->
                resolver.resolve(conflictingEdits, source)
            );
        }
    }

    @Nested
    @DisplayName("Escenario 2: The Nested Inception - Inclusion Total")
    class NestedInceptionTests {
        @Test
        @DisplayName("Detecta redundancia cuando un bloque es subconjunto de otro")
        void testRedundancyDetection() {
            List<EditBlock> edits = List.of(
                new EditBlock("A", "public void method() { doSomething(); }", "public void method() { doSomethingNew(); }", 1, 0, 0),
                new EditBlock("B", "doSomething()", "doSomethingNew()", 1, 0, 0)
            );

            OverlapConflictException ex = assertThrows(
                OverlapConflictException.class,
                () -> detector.validate(edits)
            );

            assertEquals("REDUNDANT", ex.getType());
        }
    }

    @Nested
    @DisplayName("Escenario 3: The Boundary Collision - Error de 1-Byte")
    class BoundaryCollisionTests {
        @Test
        @DisplayName("Detecta colision en lineas adyacentes con mismo search")
        void testAdjacentLineCollision() {
            List<EditBlock> edits = List.of(
                new EditBlock("A", "line10\n", "modified10\n", 10, 0, 0),
                new EditBlock("B", "line10\n", "modified10B\n", 11, 0, 0)
            );

            assertThrows(OverlapConflictException.class, () ->
                detector.validate(edits)
            );
        }
    }

    @Nested
    @DisplayName("RabbitAgent Integration Tests")
    class RabbitIntegrationTests {
        @Test
        @DisplayName("Resolver llama a RabbitAgent con prompt correcto")
        void testResolverCallsRabbitAgent() {
            String source = "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10";
            List<EditBlock> edits = List.of(
                new EditBlock("A", "line5", "modified5-A", 5, 0, 0),
                new EditBlock("B", "line5", "modified5-B", 5, 0, 0)
            );

            when(rabbitAgent.generateRaw(anyString(), anyString(), any()))
                .thenReturn("<<<<<<< SEARCH\nline5\n=======\nmodified5\n>>>>>>> REPLACE");

            try {
                resolver.resolve(edits, source);
            } catch (SurgicalException e) {
            }

            verify(rabbitAgent).generateRaw(
                argThat(prompt -> prompt.contains("Block A") && prompt.contains("Block B")),
                anyString(),
                any()
            );
        }

        @Test
        @DisplayName("Resolver falla si Rabbit devuelve respuesta vacia")
        void testResolverFailsOnEmptyResponse() {
            String source = "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10";
            List<EditBlock> edits = List.of(
                new EditBlock("A", "line5", "modified5-A", 5, 0, 0),
                new EditBlock("B", "line5", "modified5-B", 5, 0, 0)
            );

            when(rabbitAgent.generateRaw(anyString(), anyString(), any()))
                .thenReturn("");

            assertThrows(SurgicalException.class, () ->
                resolver.resolve(edits, source)
            );
        }

        @Test
        @DisplayName("Resolver falla si Rabbit lanza excepcion")
        void testResolverFailsOnRabbitException() {
            String source = "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10";
            List<EditBlock> edits = List.of(
                new EditBlock("A", "line5", "modified5-A", 5, 0, 0),
                new EditBlock("B", "line5", "modified5-B", 5, 0, 0)
            );

            when(rabbitAgent.generateRaw(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("LLM error"));

            assertThrows(SurgicalException.class, () ->
                resolver.resolve(edits, source)
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {
        @Test
        @DisplayName("Lista null retorna vacia")
        void testNullList() {
            List<EditBlock> result = resolver.resolve(null, "source");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Lista vacia retorna vacia")
        void testEmptyList() {
            List<EditBlock> result = resolver.resolve(List.of(), "source");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Un solo bloque pasa a Rabbit para verificacion")
        void testSingleBlockPassedToRabbit() {
            String source = "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10";
            EditBlock single = new EditBlock("A", "line5", "modified5", 5, 0, 0);

            when(rabbitAgent.generateRaw(anyString(), anyString(), any()))
                .thenReturn("<<<<<<< SEARCH\nline5\n=======\nmodified5\n>>>>>>> REPLACE");

            try {
                resolver.resolve(List.of(single), source);
            } catch (SurgicalException e) {
            }

            verify(rabbitAgent).generateRaw(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("Prompt Construction Tests")
    class PromptConstructionTests {
        @Test
        @DisplayName("Prompt incluye descripcion de conflictos")
        void testPromptIncludesConflictDescription() {
            String source = "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10";
            List<EditBlock> edits = List.of(
                new EditBlock("A", "line5", "modified5-A", 5, 0, 0),
                new EditBlock("B", "line5", "modified5-B", 5, 0, 0)
            );

            when(rabbitAgent.generateRaw(argThat(prompt ->
                prompt.contains("CRITICAL") &&
                prompt.contains("Block A") &&
                prompt.contains("Block B") &&
                prompt.contains("line5")
            ), anyString(), any())).thenReturn("");

            try {
                resolver.resolve(edits, source);
            } catch (SurgicalException e) {
            }

            verify(rabbitAgent).generateRaw(anyString(), anyString(), any());
        }
    }
}
