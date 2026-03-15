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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("IndentationNormalizer Tests")
class IndentationNormalizerTest {
    private IndentationNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new IndentationNormalizer();
    }

    @Nested
    @DisplayName("Paso 1: Zero-Index del LLM")
    class ZeroIndexTests {
        @Test
        @DisplayName("LLM devuelve codigo sin indentacion - debe alinearlo")
        void testZeroIndexAlignment() {
            String source = "class A {\n        hacerAlgo();\n}";
            int matchIndex = 10;

            String rawReplacement = "nuevo();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertEquals("        nuevo();", result);
        }

        @Test
        @DisplayName("LLM devuelve bloque multilinea sin indentacion")
        void testZeroIndexMultiline() {
            String source = "class A {\n        if (true) {\n        }\n}";
            int matchIndex = 10;

            String rawReplacement = "if (cond) {\n    x = 1;\n}";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            String expected = "        if (cond) {\n            x = 1;\n        }";
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Paso 2: Doble Indentacion")
    class DoubleIndentationTests {
        @Test
        @DisplayName("Evitar doble indentacion cuando LLM ya indenta")
        void testPreventDoubleIndentation() {
            String source = "class A {\n    hacerAlgo();\n}";
            int matchIndex = 10;

            String rawReplacement = "    nuevo();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertEquals("    nuevo();", result);
        }

        @Test
        @DisplayName("Dedent y Shift con bloque multilinea")
        void testDedentAndShiftMultiline() {
            String source = "class A {\n    metodo() {\n    }\n}";
            int matchIndex = 10;

            String rawReplacement = """
                        if (x) {
                            return y;
                        }""";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertTrue(result.startsWith("    if (x) {"));
            assertTrue(result.contains("        return y;"));
        }
    }

    @Nested
    @DisplayName("Paso 3: Tabs vs Espacios")
    class TabsVsSpacesTests {
        @Test
        @DisplayName("Source usa tabs - resultado usa tabs")
        void testTabPreservation() {
            String source = "class A {\n\thacerAlgo();\n}";
            int matchIndex = 10;

            String rawReplacement = "nuevo();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertEquals("\tnuevo();", result);
        }

        @Test
        @DisplayName("Source mezcla tabs y espacios - preservar patron")
        void testMixedIndentation() {
            String source = "class A {\n\t    hacerAlgo();\n}";
            int matchIndex = 10;

            String rawReplacement = "nuevo();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertEquals("\t    nuevo();", result);
        }
    }

    @Nested
    @DisplayName("Paso 4: Lineas Vacias")
    class EmptyLinesTests {
        @Test
        @DisplayName("No indentar lineas vacias - evitar trailing whitespace")
        void testEmptyLinesNotIndented() {
            String source = "class A {\n    metodo();\n}";
            int matchIndex = 10;

            String rawReplacement = "linea1();\n\nlinea2();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            String[] lines = result.split("\n", -1);
            assertEquals("    linea1();", lines[0]);
            assertEquals("", lines[1]);
            assertEquals("    linea2();", lines[2]);
        }
    }

    @Nested
    @DisplayName("Paso 5: Match a Mitad de Linea")
    class MidLineMatchTests {
        @Test
        @DisplayName("Match despues de caracteres no-blancos")
        void testMidLineMatch() {
            String source = "int x = hacerAlgo();";
            int matchIndex = 8;

            String rawReplacement = "nuevo()";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertEquals("nuevo()", result);
        }
    }

    @Nested
    @DisplayName("Paso 6: Preservacion de Indentacion Relativa")
    class RelativeIndentationTests {
        @Test
        @DisplayName("Preservar estructura interna de if anidado")
        void testRelativeIndentationPreserved() {
            String source = "class A {\n    codigo;\n}";
            int matchIndex = 10;

            String rawReplacement = """
                if (x) {
                    if (y) {
                        return z;
                    }
                }""";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertTrue(result.contains("    if (x) {"));
            assertTrue(result.contains("        if (y) {"));
            assertTrue(result.contains("            return z;"));
        }
    }

    @Nested
    @DisplayName("Paso 8: Fallback Defensivo")
    class FallbackTests {
        @Test
        @DisplayName("Null replacement retorna string vacio")
        void testNullReplacementReturnsEmpty() {
            String result = normalizer.alignIndentation("source", 0, null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("Empty replacement retorna string vacio")
        void testEmptyReplacementReturnsEmpty() {
            String result = normalizer.alignIndentation("source", 0, "");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Null source retorna replacement crudo")
        void testNullSourceReturnRaw() {
            String raw = "codigo();";
            String result = normalizer.alignIndentation(null, 0, raw);
            assertEquals(raw, result);
        }

        @Test
        @DisplayName("Index negativo retorna replacement crudo")
        void testNegativeIndexReturnsRaw() {
            String raw = "codigo();";
            String result = normalizer.alignIndentation("source", -1, raw);
            assertEquals(raw, result);
        }

        @Test
        @DisplayName("Index fuera de rango retorna replacement crudo")
        void testIndexOutOfBoundsReturnsRaw() {
            String raw = "codigo();";
            String result = normalizer.alignIndentation("abc", 100, raw);
            assertEquals(raw, result);
        }
    }

    @Nested
    @DisplayName("Paso 10: Multi-plataforma")
    class CrossPlatformTests {
        @Test
        @DisplayName("Manejo de saltos CRLF")
        void testCRLFHandling() {
            String source = "class A {\r\n    codigo;\r\n}";
            int matchIndex = 12;

            String rawReplacement = "nuevo();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertEquals("    nuevo();", result);
        }

        @Test
        @DisplayName("Replacement con CRLF preserva saltos")
        void testReplacementWithCRLFPreservesNewlines() {
            String source = "class A {\n    codigo;\n}";
            int matchIndex = 10;

            String rawReplacement = "linea1();\nlinea2();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertTrue(result.contains("    linea1();"));
            assertTrue(result.contains("    linea2();"));
        }
    }

    @Nested
    @DisplayName("Integracion con Flujo Real")
    class IntegrationTests {
        @Test
        @DisplayName("Ejemplo completo del documento Fase 0.8.1")
        void testDocumentExample() {
            String source = "    public void metodo() {\n        hacerAlgo();\n    }";
            int matchIndex = 31;

            String rawReplacement = "nuevo();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertEquals("        nuevo();", result);
        }

        @Test
        @DisplayName("Preservar newline final si el original lo tenia")
        void testPreserveTrailingNewline() {
            String source = "class A {\n    codigo;\n}";
            int matchIndex = 10;

            String rawReplacement = "nuevo();\n";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertTrue(result.endsWith("nuevo();\n"));
        }

        @Test
        @DisplayName("No agregar newline final si el original no lo tenia")
        void testNoTrailingNewlineIfMissing() {
            String source = "class A {\n    codigo;\n}";
            int matchIndex = 10;

            String rawReplacement = "nuevo();";

            String result = normalizer.alignIndentation(source, matchIndex, rawReplacement);

            assertFalse(result.endsWith("\n"));
        }
    }
}
