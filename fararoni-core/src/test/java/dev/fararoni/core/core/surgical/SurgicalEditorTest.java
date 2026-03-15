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

import dev.fararoni.core.core.config.AgentConfig;
import dev.fararoni.core.core.indexing.SentinelJavaParser;
import dev.fararoni.core.core.indexing.model.LineRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SurgicalEditor Tests")
class SurgicalEditorTest {
    private SurgicalEditor editor;

    private static final String SIMPLE_CLASS = """
        public class Example {
            private int value = 10;

            public int getValue() {
                return value;
            }

            public void setValue(int value) {
                this.value = value;
            }
        }
        """;

    @BeforeEach
    void setUp() {
        SentinelJavaParser parser = new SentinelJavaParser();
        editor = new SurgicalEditor(parser);
    }

    @Nested
    @DisplayName("Basic Surgery Tests")
    class BasicSurgeryTests {
        @Test
        @DisplayName("Cirugia simple con un bloque")
        void testBasicSurgery() {
            EditBlock edit = new EditBlock(
                "edit-1",
                "private int value = 10;",
                "private int value = 20;",
                2, 0, 0
            );

            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, List.of(edit));

            assertTrue(report.isSuccessful());
            assertTrue(report.hasChanges());
            assertEquals(1, report.changeCount());
            assertTrue(report.content().contains("private int value = 20;"));
            assertFalse(report.content().contains("private int value = 10;"));
        }

        @Test
        @DisplayName("Cirugia sin cambios retorna original")
        void testNoChangesReturnsOriginal() {
            EditBlock edit = new EditBlock(
                "edit-1",
                "texto que no existe",
                "reemplazo",
                1, 0, 0
            );

            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, List.of(edit));

            assertTrue(report.isSuccessful());
            assertFalse(report.hasChanges());
            assertEquals(0, report.changeCount());
            assertEquals(SIMPLE_CLASS, report.content());
        }

        @Test
        @DisplayName("Lista vacia de ediciones retorna original")
        void testEmptyEditsReturnsOriginal() {
            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, List.of());

            assertTrue(report.isSuccessful());
            assertFalse(report.hasChanges());
            assertEquals(SIMPLE_CLASS, report.content());
        }
    }

    @Nested
    @DisplayName("Multiple Surgery Tests")
    class MultipleSurgeryTests {
        @Test
        @DisplayName("Cirugia con multiples bloques")
        void testMultipleSurgery() {
            List<EditBlock> edits = List.of(
                new EditBlock("edit-1", "private int value = 10;", "private int value = 100;", 2, 0, 0),
                new EditBlock("edit-2", "return value;", "return this.value;", 5, 0, 0)
            );

            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, edits);

            assertTrue(report.isSuccessful());
            assertEquals(2, report.changeCount());
            assertTrue(report.content().contains("private int value = 100;"));
            assertTrue(report.content().contains("return this.value;"));
        }

        @Test
        @DisplayName("Orden Bottom-Up minimiza drift")
        void testBottomUpOrder() {
            List<EditBlock> edits = List.of(
                new EditBlock("edit-top", "private int value = 10;", "private int counter = 10;", 2, 0, 0),
                new EditBlock("edit-bottom", "this.value = value;", "this.counter = value;", 8, 0, 0)
            );

            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, edits);

            assertTrue(report.isSuccessful());
            assertEquals(2, report.changeCount());
            assertTrue(report.content().contains("private int counter = 10;"));
            assertTrue(report.content().contains("this.counter = value;"));
        }
    }

    @Nested
    @DisplayName("AST Validation Tests")
    class ASTValidationTests {
        @Test
        @DisplayName("Cirugia que corrompe AST lanza excepcion")
        void testASTValidationFailure() {
            EditBlock corruptingEdit = new EditBlock(
                "corrupt-1",
                "public int getValue() {",
                "public int getValue( {",
                4, 0, 0
            );

            assertThrows(SurgicalException.class, () ->
                editor.executeSurgery(SIMPLE_CLASS, List.of(corruptingEdit))
            );
        }

        @Test
        @DisplayName("Cirugia valida pasa validacion AST")
        void testASTValidationSuccess() {
            EditBlock validEdit = new EditBlock(
                "valid-1",
                "public int getValue()",
                "public int obtenerValor()",
                4, 0, 0
            );

            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, List.of(validEdit));

            assertTrue(report.isSuccessful());
            assertTrue(report.content().contains("public int obtenerValor()"));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        @Test
        @DisplayName("Source null lanza excepcion")
        void testNullSourceThrowsException() {
            EditBlock edit = new EditBlock("edit-1", "a", "b", 1, 0, 0);

            assertThrows(SurgicalException.class, () ->
                editor.executeSurgery(null, List.of(edit))
            );
        }

        @Test
        @DisplayName("Source vacio lanza excepcion")
        void testEmptySourceThrowsException() {
            EditBlock edit = new EditBlock("edit-1", "a", "b", 1, 0, 0);

            assertThrows(SurgicalException.class, () ->
                editor.executeSurgery("   ", List.of(edit))
            );
        }

        @Test
        @DisplayName("Scope no encontrado lanza excepcion")
        void testScopeNotFoundThrowsException() {
            EditBlock edit = new EditBlock("edit-1", "value", "val", 2, 0, 0);

            assertThrows(SurgicalException.class, () ->
                editor.executeSurgery(SIMPLE_CLASS, List.of(edit), "metodoInexistente()")
            );
        }
    }

    @Nested
    @DisplayName("Delta Tracking Tests")
    class DeltaTrackingTests {
        @Test
        @DisplayName("Delta positivo cuando crece")
        void testPositiveDelta() {
            EditBlock edit = new EditBlock(
                "edit-1",
                "int value",
                "int valorNumerico",
                2, 0, 0
            );

            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, List.of(edit));

            assertTrue(report.didGrow());
            assertTrue(report.totalDelta() > 0);
        }

        @Test
        @DisplayName("Delta negativo cuando encoge")
        void testNegativeDelta() {
            EditBlock edit = new EditBlock(
                "edit-1",
                "    private int value = 10;",
                "    private int v = 10;",
                2, 0, 0
            );

            SurgeryReport report = editor.executeSurgery(SIMPLE_CLASS, List.of(edit));

            assertTrue(report.didShrink());
            assertTrue(report.totalDelta() < 0);
        }
    }

    @Nested
    @DisplayName("Fase 0.2: applySurgery con Scope AST")
    class ApplySurgeryWithScopeTests {
        private SentinelJavaParser parser;

        @BeforeEach
        void setUp() {
            parser = new SentinelJavaParser();
        }

        @Test
        @DisplayName("applySurgery edita solo dentro del scope del metodo")
        void testApplySurgeryWithScope() {
            Map<String, LineRange> ranges = parser.extractMethodRanges(SIMPLE_CLASS);
            LineRange getValueScope = ranges.get("getValue()");

            assertNotNull(getValueScope, "Debe encontrar el metodo getValue()");

            String result = editor.applySurgery(
                SIMPLE_CLASS,
                "return value;",
                "return this.value;",
                getValueScope
            );

            assertTrue(result.contains("return this.value;"));
            assertTrue(result.contains("private int value = 10;"));
        }

        @Test
        @DisplayName("applySurgeryToMethod encuentra metodo automaticamente")
        void testApplySurgeryToMethod() {
            String result = editor.applySurgeryToMethod(
                SIMPLE_CLASS,
                "return value;",
                "return cachedValue;",
                "getValue()"
            );

            assertTrue(result.contains("return cachedValue;"));
        }

        @Test
        @DisplayName("applySurgery lanza excepcion si bloque no esta en scope")
        void testApplySurgeryBlockNotInScope() {
            String testSource = "xxxabcxxxreturn value;xxx";
            LineRange tinyScope = new LineRange(1, 1, 1, 10, 0, 10);

            assertThrows(SurgicalException.class, () ->
                editor.applySurgery(
                    testSource,
                    "return value;",
                    "return x;",
                    tinyScope
                )
            );
        }

        @Test
        @DisplayName("applySurgeryToMethod lanza excepcion si metodo no existe")
        void testApplySurgeryMethodNotFound() {
            assertThrows(SurgicalException.class, () ->
                editor.applySurgeryToMethod(
                    SIMPLE_CLASS,
                    "algo",
                    "otro",
                    "metodoInexistente()"
                )
            );
        }

        @Test
        @DisplayName("Constructor con AgentConfig funciona")
        void testConstructorWithAgentConfig() {
            AgentConfig config = AgentConfig.defaults();
            SurgicalEditor editorWithConfig = new SurgicalEditor(config);

            Map<String, LineRange> ranges = parser.extractMethodRanges(SIMPLE_CLASS);
            LineRange scope = ranges.get("getValue()");

            String result = editorWithConfig.applySurgery(
                SIMPLE_CLASS,
                "return value;",
                "return 42;",
                scope
            );

            assertTrue(result.contains("return 42;"));
        }

        @Test
        @DisplayName("Fuzzy matching tolera diferencias menores")
        void testFuzzyMatchingTolerance() {
            AgentConfig config = new AgentConfig(3, 0.1f, 0.3f, true, 12000, 0.20f);
            SurgicalEditor fuzzyEditor = new SurgicalEditor(parser, config);

            Map<String, LineRange> ranges = parser.extractMethodRanges(SIMPLE_CLASS);
            LineRange scope = ranges.get("getValue()");

            String result = fuzzyEditor.applySurgery(
                SIMPLE_CLASS,
                "return  value;",
                "return fixed;",
                scope
            );

            assertTrue(result.contains("return fixed;"));
        }
    }
}
