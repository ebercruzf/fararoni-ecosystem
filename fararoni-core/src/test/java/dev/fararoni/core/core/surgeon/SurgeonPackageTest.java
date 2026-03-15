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
package dev.fararoni.core.core.surgeon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Surgeon Package - El Cirujano Tests")
class SurgeonPackageTest {
    @Nested
    @DisplayName("LevenshteinUtils Tests")
    class LevenshteinUtilsTests {
        @Test
        @DisplayName("Debe calcular distancia 0 para strings identicos")
        void shouldReturnZeroForIdentical() {
            assertEquals(0, LevenshteinUtils.calculateDistance("hello", "hello"));
        }

        @Test
        @DisplayName("Debe calcular distancia correcta para strings diferentes")
        void shouldCalculateCorrectDistance() {
            assertEquals(3, LevenshteinUtils.calculateDistance("kitten", "sitting"));
        }

        @Test
        @DisplayName("Debe manejar strings null")
        void shouldHandleNullStrings() {
            assertEquals(0, LevenshteinUtils.calculateDistance(null, null));
            assertEquals(5, LevenshteinUtils.calculateDistance(null, "hello"));
            assertEquals(5, LevenshteinUtils.calculateDistance("hello", null));
        }

        @Test
        @DisplayName("Debe manejar strings vacios")
        void shouldHandleEmptyStrings() {
            assertEquals(0, LevenshteinUtils.calculateDistance("", ""));
            assertEquals(5, LevenshteinUtils.calculateDistance("", "hello"));
            assertEquals(5, LevenshteinUtils.calculateDistance("hello", ""));
        }

        @Test
        @DisplayName("Debe calcular similitud correctamente")
        void shouldCalculateSimilarity() {
            assertEquals(1.0, LevenshteinUtils.calculateSimilarity("hello", "hello"));

            double similarity = LevenshteinUtils.calculateSimilarity("abc", "xyz");
            assertTrue(similarity < 0.5);

            double partialSimilarity = LevenshteinUtils.calculateSimilarity("hello", "hallo");
            assertTrue(partialSimilarity > 0.7);
        }

        @Test
        @DisplayName("Debe normalizar codigo correctamente")
        void shouldNormalizeCode() {
            String code = "  int   x  =   1;  ";
            String normalized = LevenshteinUtils.normalizeCode(code);
            assertEquals("int x = 1;", normalized);
        }

        @Test
        @DisplayName("Debe normalizar indentacion")
        void shouldNormalizeIndentation() {
            String code = "    line1\n\t\tline2\n  line3";
            String normalized = LevenshteinUtils.normalizeIndentation(code);
            assertEquals("line1\nline2\nline3", normalized);
        }

        @Test
        @DisplayName("isSimilar debe respetar umbral")
        void isSimilarShouldRespectThreshold() {
            assertTrue(LevenshteinUtils.isSimilar("hello", "hello", 1.0));
            assertTrue(LevenshteinUtils.isSimilar("hello", "hallo", 0.7));
            assertFalse(LevenshteinUtils.isSimilar("hello", "world", 0.9));
        }
    }

    @Nested
    @DisplayName("SmartPatcher Tests")
    class SmartPatcherTests {
        private SmartPatcher patcher;

        @BeforeEach
        void setUp() {
            patcher = new SmartPatcher();
        }

        @Test
        @DisplayName("Debe aplicar parche exacto")
        void shouldApplyExactPatch() {
            String original = "def hello():\n    print('world')";
            String search = "print('world')";
            String replace = "print('FIXED')";

            String result = patcher.applyPatch(original, search, replace);

            assertTrue(result.contains("print('FIXED')"));
            assertEquals(SmartPatcher.PatchResult.EXACT_MATCH, patcher.getLastResult());
        }

        @Test
        @DisplayName("Debe aplicar parche con diferencia de whitespace")
        void shouldApplyPatchWithWhitespaceDifference() {
            String original = "def hello():\n    print('world')\n    return True";
            String search = "def hello():\n  print('world')\n  return True";
            String replace = "def hello():\n    print('FIXED')\n    return True";

            String result = patcher.applyPatch(original, search, replace);

            assertTrue(patcher.wasLastPatchSuccessful());
        }

        @Test
        @DisplayName("Debe retornar original si no encuentra bloque")
        void shouldReturnOriginalIfNotFound() {
            String original = "def hello():\n    print('world')";
            String search = "NONEXISTENT_BLOCK";
            String replace = "REPLACEMENT";

            String result = patcher.applyPatch(original, search, replace);

            assertEquals(original, result);
            assertEquals(SmartPatcher.PatchResult.NOT_FOUND, patcher.getLastResult());
            assertFalse(patcher.wasLastPatchSuccessful());
        }

        @Test
        @DisplayName("Debe lanzar excepcion para parametros null")
        void shouldThrowForNullParams() {
            assertThrows(NullPointerException.class, () ->
                patcher.applyPatch(null, "search", "replace"));
            assertThrows(NullPointerException.class, () ->
                patcher.applyPatch("original", null, "replace"));
            assertThrows(NullPointerException.class, () ->
                patcher.applyPatch("original", "search", null));
        }

        @Test
        @DisplayName("Debe aplicar parche fuzzy con lineas similares")
        void shouldApplyFuzzyPatchWithSimilarLines() {
            String original = "function test() {\n    console.log('hello');\n}";
            String search = "function test() {\n    console.log('hello');\n}";
            String replace = "function test() {\n    console.log('FIXED');\n}";

            String result = patcher.applyPatch(original, search, replace);

            assertTrue(result.contains("FIXED"));
        }
    }

    @Nested
    @DisplayName("SelfHealer Tests")
    class SelfHealerTests {
        @Test
        @DisplayName("Debe retornar codigo valido sin cambios")
        void shouldReturnValidCodeUnchanged() {
            SelfHealer.SyntaxValidator alwaysValid = code -> 0;
            SelfHealer healer = new SelfHealer(alwaysValid);

            String code = "valid code";
            String result = healer.heal(code);

            assertEquals(code, result);
            assertTrue(healer.wasLastHealSuccessful());
            assertEquals(0, healer.getLastAttempts());
        }

        @Test
        @DisplayName("Debe agregar llave de cierre faltante")
        void shouldAddMissingClosingBrace() {
            SelfHealer.SyntaxValidator braceValidator = code -> {
                int open = countChar(code, '{');
                int close = countChar(code, '}');
                return Math.abs(open - close);
            };

            SelfHealer healer = new SelfHealer(braceValidator);

            String brokenCode = "class X { void foo() {";
            String result = healer.heal(brokenCode);

            int openBraces = countChar(result, '{');
            int closeBraces = countChar(result, '}');
            assertEquals(openBraces, closeBraces);
        }

        @Test
        @DisplayName("Debe respetar limite de intentos")
        void shouldRespectMaxAttempts() {
            SelfHealer.SyntaxValidator neverValid = code -> 999;
            SelfHealer healer = new SelfHealer(neverValid);

            String code = "broken";
            healer.heal(code);

            assertTrue(healer.getLastAttempts() <= 50);
            assertFalse(healer.wasLastHealSuccessful());
        }

        @Test
        @DisplayName("Debe lanzar excepcion para validator null")
        void shouldThrowForNullValidator() {
            assertThrows(NullPointerException.class, () -> new SelfHealer(null));
        }

        @Test
        @DisplayName("Debe lanzar excepcion para codigo null")
        void shouldThrowForNullCode() {
            SelfHealer healer = new SelfHealer(code -> 0);
            assertThrows(NullPointerException.class, () -> healer.heal(null));
        }

        private int countChar(String s, char c) {
            return (int) s.chars().filter(ch -> ch == c).count();
        }
    }

    @Nested
    @DisplayName("SurgeonManager Tests")
    class SurgeonManagerTests {
        @Test
        @DisplayName("Debe operar exitosamente con parche exacto")
        void shouldOperateSuccessfullyWithExactPatch() {
            SurgeonManager surgeon = new SurgeonManager();

            String original = "print('hello')";
            String search = "print('hello')";
            String replace = "print('FIXED')";

            String result = surgeon.operate(original, search, replace);

            assertEquals("print('FIXED')", result);
            assertTrue(surgeon.wasLastOperationSuccessful());
            assertEquals(SurgeonManager.OperationResult.SUCCESS_DIRECT, surgeon.getLastResult());
        }

        @Test
        @DisplayName("Debe abortar si no encuentra el bloque")
        void shouldAbortIfBlockNotFound() {
            SurgeonManager surgeon = new SurgeonManager();

            String original = "print('hello')";
            String search = "NONEXISTENT";
            String replace = "REPLACEMENT";

            String result = surgeon.operate(original, search, replace);

            assertEquals(original, result);
            assertFalse(surgeon.wasLastOperationSuccessful());
            assertEquals(SurgeonManager.OperationResult.PATCH_FAILED, surgeon.getLastResult());
        }

        @Test
        @DisplayName("Debe intentar auto-curacion si hay errores")
        void shouldAttemptHealingIfErrors() {
            final int[] calls = {0};
            SelfHealer.SyntaxValidator validator = code -> {
                calls[0]++;
                return code.contains("}") ? 0 : 1;
            };

            SurgeonManager surgeon = new SurgeonManager(validator);

            String original = "function test() { return 1;";
            String search = "return 1;";
            String replace = "return 42;";

            String result = surgeon.operate(original, search, replace);

            assertTrue(calls[0] > 1);
        }

        @Test
        @DisplayName("Debe generar reporte de operacion")
        void shouldGenerateOperationReport() {
            SurgeonManager surgeon = new SurgeonManager();

            surgeon.operate("hello", "hello", "world");

            String report = surgeon.getOperationReport();
            assertTrue(report.contains("SurgeonManager Operation Report"));
            assertTrue(report.contains("Resultado"));
        }

        @Test
        @DisplayName("operateWithoutValidation debe saltarse la validacion")
        void operateWithoutValidationShouldSkipValidation() {
            SelfHealer.SyntaxValidator neverValid = code -> {
                throw new RuntimeException("No deberia llamarse");
            };

            SurgeonManager surgeon = new SurgeonManager(neverValid);

            String original = "hello";
            String search = "hello";
            String replace = "world";

            String result = surgeon.operateWithoutValidation(original, search, replace);
            assertEquals("world", result);
        }

        @Test
        @DisplayName("Debe exponer patcher y healer")
        void shouldExposePatcherAndHealer() {
            SurgeonManager surgeon = new SurgeonManager();

            assertNotNull(surgeon.getPatcher());
            assertNotNull(surgeon.getHealer());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @Test
        @DisplayName("Flujo completo: Parche + Validacion + Curacion")
        void fullSurgicalFlow() {
            SelfHealer.SyntaxValidator braceValidator = code -> {
                int open = (int) code.chars().filter(c -> c == '{').count();
                int close = (int) code.chars().filter(c -> c == '}').count();
                return Math.abs(open - close);
            };

            SurgeonManager surgeon = new SurgeonManager(braceValidator);

            String original = "class X {\n    void foo() {\n        return;\n    }\n}";

            String search = "void foo() {\n        return;\n    }";

            String replace = "void bar() {\n        return 42;";

            String result = surgeon.operate(original, search, replace);

            int openBraces = (int) result.chars().filter(c -> c == '{').count();
            int closeBraces = (int) result.chars().filter(c -> c == '}').count();
            assertEquals(openBraces, closeBraces, "Las llaves deben estar balanceadas");
        }

        @Test
        @DisplayName("Levenshtein + SmartPatcher integrados")
        void levenshteinAndPatcherIntegration() {
            SmartPatcher patcher = new SmartPatcher();

            String original = "def process():\n    data = load()\n    return data";

            String search = "def process():\n  data = load()\n  return data";

            String replace = "def process_v2():\n    data = load()\n    return transform(data)";

            String result = patcher.applyPatch(original, search, replace);

            assertTrue(patcher.wasLastPatchSuccessful(),
                "Deberia encontrar el bloque con diferente indentacion");
        }
    }

    @Nested
    @DisplayName("V1 - Anchor-Based Matching Tests")
    class AnchorBasedMatchingTests {
        @Test
        @DisplayName("Debe detectar ambiguedad con bloques similares")
        void shouldDetectAmbiguityWithSimilarBlocks() {
            SmartPatcher patcher = new SmartPatcher();

            String original = "def foo():\n    return 1\n\ndef bar():\n    return 1";
            String search = "def foo():\n    return 1";
            String replace = "def foo():\n    return 42";

            String result = patcher.applyPatchWithAmbiguityCheck(original, search, replace);

            assertTrue(result.contains("return 42"));
        }

        @Test
        @DisplayName("Debe rechazar cuando hay multiples candidatos muy similares")
        void shouldRejectWhenMultipleCandidatesAreTooSimilar() {
            SmartPatcher patcher = new SmartPatcher();

            String original = "x = 1\nx = 1";
            String search = "x = 1";
            String replace = "x = 99";

            String result = patcher.applyPatchWithAmbiguityCheck(original, search, replace);

            assertTrue(result.startsWith("x = 99"));
        }

        @Test
        @DisplayName("Debe calcular score de bloque correctamente")
        void shouldCalculateBlockScoreCorrectly() {
            SmartPatcher patcher = new SmartPatcher();

            String original = "line1\nline2\nline3";
            String search = "line1\nline2\nline3";
            String replace = "new1\nnew2\nnew3";

            String result = patcher.applyPatchWithAmbiguityCheck(original, search, replace);
            assertEquals("new1\nnew2\nnew3", result.trim());
        }
    }

    @Nested
    @DisplayName("V2 - Error-Driven Healing Tests")
    class ErrorDrivenHealingTests {
        @Test
        @DisplayName("DetailedValidator debe extraer numero de linea")
        void detailedValidatorShouldExtractLineNumber() {
            SelfHealer.DetailedSyntaxValidator validator = new SelfHealer.DetailedSyntaxValidator() {
                @Override
                public int countErrors(String code) {
                    return code.contains("error") ? 1 : 0;
                }

                @Override
                public String getErrorMessage(String code) {
                    return "SyntaxError at line 10: unexpected token";
                }
            };

            int line = validator.extractErrorLine("Error on line 42");
            assertEquals(42, line);

            line = validator.extractErrorLine("file.py:15: error");
            assertEquals(15, line);

            line = validator.extractErrorLine("no line number here");
            assertEquals(-1, line);
        }

        @Test
        @DisplayName("Debe usar limite reducido para validaciones costosas")
        void shouldUseLowerLimitForExpensiveValidation() {
            final int[] callCount = {0};

            SelfHealer.SyntaxValidator neverValid = code -> {
                callCount[0]++;
                return 999;
            };

            SelfHealer healer = new SelfHealer(neverValid, true);
            healer.heal("broken code");

            assertTrue(healer.getLastAttempts() <= 10,
                "Deberia usar limite reducido, pero uso: " + healer.getLastAttempts());
        }

        @Test
        @DisplayName("healErrorDriven debe funcionar con DetailedValidator")
        void healErrorDrivenShouldWorkWithDetailedValidator() {
            SelfHealer.DetailedSyntaxValidator validator = new SelfHealer.DetailedSyntaxValidator() {
                @Override
                public int countErrors(String code) {
                    int open = (int) code.chars().filter(c -> c == '{').count();
                    int close = (int) code.chars().filter(c -> c == '}').count();
                    return Math.abs(open - close);
                }

                @Override
                public String getErrorMessage(String code) {
                    return "Unexpected EOF, missing '}'";
                }
            };

            SelfHealer healer = new SelfHealer(validator);
            String result = healer.healErrorDriven("class X {");

            assertTrue(result.contains("}"));
        }
    }

    @Nested
    @DisplayName("V3 - Levenshtein Optimization Tests")
    class LevenshteinOptimizationTests {
        @Test
        @DisplayName("Similitud de trigramas debe ser rapida")
        void trigramSimilarityShouldBeFast() {
            String x = "hello world this is a test string";
            String y = "hello world this is a test string!";

            double similarity = LevenshteinUtils.calculateTrigramSimilarity(x, y);
            assertTrue(similarity > 0.9, "Strings muy similares deberian tener alta similitud de trigramas");
        }

        @Test
        @DisplayName("Similitud de trigramas debe detectar diferencias")
        void trigramSimilarityShouldDetectDifferences() {
            String x = "completely different text";
            String y = "nothing similar at all here";

            double similarity = LevenshteinUtils.calculateTrigramSimilarity(x, y);
            assertTrue(similarity < 0.5, "Strings diferentes deberian tener baja similitud");
        }

        @Test
        @DisplayName("Similitud por ventanas debe funcionar para strings largos")
        void windowSimilarityShouldWorkForLongStrings() {
            StringBuilder sb1 = new StringBuilder();
            StringBuilder sb2 = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb1.append("line ").append(i).append("\n");
                sb2.append("line ").append(i).append("\n");
            }
            sb2.append("extra line");

            double similarity = LevenshteinUtils.calculateWindowSimilarity(sb1.toString(), sb2.toString());
            assertTrue(similarity > 0.8, "Strings mayormente iguales deberian ser similares");
        }

        @Test
        @DisplayName("calculateSimilarityOptimized debe manejar cadenas largas")
        void optimizedSimilarityShouldHandleLongStrings() {
            String x = "a".repeat(1000);
            String y = "a".repeat(1000);

            double similarity = LevenshteinUtils.calculateSimilarityOptimized(x, y);
            assertEquals(1.0, similarity, 0.01);
        }

        @Test
        @DisplayName("couldBeSimilar debe filtrar rapidamente")
        void couldBeSimilarShouldFilterQuickly() {
            assertFalse(LevenshteinUtils.couldBeSimilar("short", "a".repeat(100), 0.8));

            assertTrue(LevenshteinUtils.couldBeSimilar("hello", "hallo", 0.8));

            assertTrue(LevenshteinUtils.couldBeSimilar("same", "same", 1.0));
        }

        @Test
        @DisplayName("findBestMatchPosition debe encontrar bloque en texto largo")
        void findBestMatchPositionShouldFindBlock() {
            String largeText = "prefix\n" + "target block here\n" + "suffix\n".repeat(50);
            String searchBlock = "target block here";

            int position = LevenshteinUtils.findBestMatchPosition(largeText, searchBlock, 0.8);
            assertTrue(position >= 0, "Deberia encontrar el bloque");
            assertTrue(position < 20, "Deberia encontrarlo cerca del inicio");
        }
    }
}
