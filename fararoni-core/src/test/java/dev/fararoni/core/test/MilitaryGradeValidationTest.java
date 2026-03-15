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
package dev.fararoni.core.test;

import dev.fararoni.core.core.search.BM25Engine;
import dev.fararoni.core.core.search.VectorUtils;
import dev.fararoni.core.core.surgeon.SmartPatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Military Grade Validation Suite")
public class MilitaryGradeValidationTest {
    @Nested
    @DisplayName("V1 - VectorMath Safety Tests")
    class VectorMathSafetyTests {
        @Test
        @DisplayName("Vector cero debe retornar similitud 0.0 (evitar NaN)")
        void testVectorMathSafe_ZeroVector() {
            float[] v1 = {0.0f, 0.0f};
            float[] v2 = {1.0f, 2.0f};

            double similarity = VectorUtils.cosineSimilarity(v1, v2);

            assertEquals(0.0, similarity, 0.001,
                "FALLO CRITICO: El vector cero debe resultar en similitud 0.0 para evitar NaN");
        }

        @Test
        @DisplayName("Ambos vectores cero deben retornar similitud 0.0")
        void testVectorMathSafe_BothZeroVectors() {
            float[] v1 = {0.0f, 0.0f};
            float[] v2 = {0.0f, 0.0f};

            double similarity = VectorUtils.cosineSimilarity(v1, v2);

            assertEquals(0.0, similarity, 0.001,
                "FALLO CRITICO: Dos vectores cero deben resultar en similitud 0.0");
        }
    }

    @Nested
    @DisplayName("V2 - BM25 Code-Aware Tokenization Tests")
    class BM25TokenizationTests {
        @Test
        @DisplayName("BM25 debe encontrar identificadores con snake_case")
        void testBM25Tokenization_SnakeCase() {
            BM25Engine engine = new BM25Engine();

            String codeSnippet = "public void calculate_total(String user_id) { return; }";
            engine.index("doc1", codeSnippet);

            Map<String, Double> results = engine.search("user_id");

            assertTrue(results.containsKey("doc1"),
                "FALLO LEXICO: BM25 no encontro 'user_id'. Revisa el regex de tokenizacion (debe incluir '_').");
        }

        @Test
        @DisplayName("BM25 debe encontrar identificadores con dot notation")
        void testBM25Tokenization_DotNotation() {
            BM25Engine engine = new BM25Engine();

            String codeSnippet = "private PrintStream stream = System.out; void log() {}";
            engine.index("doc1", codeSnippet);

            Map<String, Double> results = engine.search("system.out");

            assertTrue(results.containsKey("doc1"),
                "FALLO LEXICO: BM25 no encontro 'system.out'. Revisa el regex de tokenizacion (debe incluir '.').");
        }

        @Test
        @DisplayName("BM25 debe encontrar metodos con dot notation completa")
        void testBM25Tokenization_DotNotationMethod() {
            BM25Engine engine = new BM25Engine();

            String codeSnippet = "public void log() { System.out.println(); }";
            engine.index("doc1", codeSnippet);

            Map<String, Double> results = engine.search("system.out.println");

            assertTrue(results.containsKey("doc1"),
                "FALLO LEXICO: BM25 no encontro 'system.out.println'.");
        }

        @Test
        @DisplayName("BM25 debe encontrar anotaciones con @")
        void testBM25Tokenization_Annotations() {
            BM25Engine engine = new BM25Engine();

            String codeSnippet = "@Controller public class UserController {}";
            engine.index("doc1", codeSnippet);

            Map<String, Double> results = engine.search("@controller");

            assertTrue(results.containsKey("doc1"),
                "FALLO LEXICO: BM25 no encontro '@controller'. Revisa el regex de tokenizacion (debe incluir '@').");
        }

        @Test
        @DisplayName("BM25 debe encontrar inner classes con $")
        void testBM25Tokenization_InnerClasses() {
            BM25Engine engine = new BM25Engine();

            String codeSnippet = "class Outer$Inner { void test() {} }";
            engine.index("doc1", codeSnippet);

            Map<String, Double> results = engine.search("outer$inner");

            assertTrue(results.containsKey("doc1"),
                "FALLO LEXICO: BM25 no encontro 'outer$inner'. Revisa el regex de tokenizacion (debe incluir '$').");
        }
    }

    @Nested
    @DisplayName("V3 - SmartPatcher Context Anchor Tests")
    class SmartPatcherAnchorTests {
        @Test
        @DisplayName("Debe aplicar parche sin danar codigo vecino")
        void testSmartPatcherContextAnchors_Specific() {
            SmartPatcher surgeon = new SmartPatcher();

            String original = """
                public class Calculadora {
                    public int sumar(int a, int b) {
                        return a + b;
                    }

                    public int restar(int a, int b) {
                        return a - b;
                    }
                }
                """;

            String searchBlock = """
                    public int sumar(int a, int b) {
                        return a + b;
                    }
                """;

            String replaceBlock = """
                    public int sumar(int a, int b) {
                        return Math.addExact(a, b);
                    }
                """;

            String result = surgeon.applyPatch(original, searchBlock, replaceBlock);

            assertTrue(result.contains("Math.addExact"),
                "FALLO QUIRURGICO: El parche no se aplico.");

            assertTrue(result.contains("public int restar"),
                "DANO COLATERAL: El metodo vecino 'restar' fue eliminado o corrompido.");

            assertFalse(result.contains("return a + b;"),
                "RESIDUOS: El codigo antiguo no fue eliminado completamente.");
        }

        @Test
        @DisplayName("Debe rechazar parche para bloques pequenos sin coincidencia exacta")
        void testSmartPatcher_RejectSmallBlockFuzzy() {
            SmartPatcher surgeon = new SmartPatcher();

            String original = "int x = 1;\nint y = 2;\nint z = 3;";

            String searchBlock = "int x=1;\nint y=2;";
            String replaceBlock = "int x = 99;\nint y = 99;";

            String result = surgeon.applyPatch(original, searchBlock, replaceBlock);

            assertEquals(original, result,
                "SEGURIDAD: Bloques pequenos sin coincidencia exacta no deben aplicar Fuzzy Match");
        }

        @Test
        @DisplayName("Debe aplicar parche exacto incluso para bloques pequenos")
        void testSmartPatcher_AcceptSmallBlockExact() {
            SmartPatcher surgeon = new SmartPatcher();

            String original = "int x = 1;\nint y = 2;";
            String searchBlock = "int x = 1;\nint y = 2;";
            String replaceBlock = "int x = 99;\nint y = 99;";

            String result = surgeon.applyPatch(original, searchBlock, replaceBlock);

            assertTrue(result.contains("x = 99"),
                "Coincidencia exacta debe funcionar para cualquier tamano de bloque");
        }

        @Test
        @DisplayName("Debe aplicar Fuzzy Match para bloques grandes con diferencias de whitespace")
        void testSmartPatcher_LargeBlockFuzzy() {
            SmartPatcher surgeon = new SmartPatcher();

            String original = """
                void method() {
                    line1();
                    line2();
                    line3();
                }
                """;

            String search = """
                void method() {
                  line1();
                  line2();
                  line3();
                }
                """;

            String replace = """
                void methodFixed() {
                    lineA();
                    lineB();
                    lineC();
                }
                """;

            String result = surgeon.applyPatch(original, search, replace);

            assertTrue(result.contains("methodFixed") || result.contains("lineA"),
                "FALLO FUNCIONAL: El patcher debio aceptar el fuzzy match en un bloque grande (>3 lineas).");
        }
    }
}
