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
package dev.fararoni.core.core.reflexion.testoutput;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("PytestOutputParser - Parser de Output de Pytest")
class PytestOutputParserTest {
    private PytestOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new PytestOutputParser();
    }

    @Nested
    @DisplayName("Parsing Basico")
    class BasicParsingTests {
        @Test
        @DisplayName("Parse linea FAILED simple")
        void parse_simpleFailedLine() {
            String output = "FAILED test_add - AssertionError: assert 5 == 4";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            TestFailure failure = failures.get(0);
            assertEquals("test_add", failure.testName());
            assertEquals("AssertionError", failure.errorType());
        }

        @Test
        @DisplayName("Parse extrae expected y actual de assert")
        void parse_extractsExpectedActual() {
            String output = "FAILED test_add - AssertionError: assert 5 == 4";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("5", failures.get(0).expected());
            assertEquals("4", failures.get(0).actual());
        }

        @Test
        @DisplayName("Parse multiples tests fallidos")
        void parse_multipleFailures() {
            String output = """
                FAILED test_add - AssertionError: assert 5 == 4
                FAILED test_sub - AssertionError: assert 3 == 2
                FAILED test_mul - TypeError: invalid type
                """;

            List<TestFailure> failures = parser.parse(output);

            assertEquals(3, failures.size());
            assertEquals("test_add", failures.get(0).testName());
            assertEquals("test_sub", failures.get(1).testName());
            assertEquals("test_mul", failures.get(2).testName());
        }

        @Test
        @DisplayName("Parse output vacio retorna lista vacia")
        void parse_emptyOutput_returnsEmptyList() {
            List<TestFailure> failures = parser.parse("");

            assertTrue(failures.isEmpty());
        }

        @Test
        @DisplayName("Parse output sin failures retorna lista vacia")
        void parse_noFailures_returnsEmptyList() {
            String output = """
                PASSED test_add
                PASSED test_sub
                ===================== 2 passed =====================
                """;

            List<TestFailure> failures = parser.parse(output);

            assertTrue(failures.isEmpty());
        }

        @Test
        @DisplayName("Parse null lanza NullPointerException")
        void parse_null_throwsNPE() {
            assertThrows(NullPointerException.class, () -> parser.parse(null));
        }
    }

    @Nested
    @DisplayName("Formato Verbose")
    class VerboseFormatTests {
        @Test
        @DisplayName("Parse formato test_file.py::TestClass::test_method FAILED")
        void parse_verboseFormat() {
            String output = "test_math.py::TestCalculator::test_add FAILED";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("test_add", failures.get(0).testName());
            assertEquals("test_math.py", failures.get(0).fileName());
        }

        @Test
        @DisplayName("Parse formato con path largo")
        void parse_longPathFormat() {
            String output = "../../../dev::BeerSongTest::test_all_verses FAILED [ 12%]";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("test_all_verses", failures.get(0).testName());
        }
    }

    @Nested
    @DisplayName("Patrones de Assert")
    class AssertionPatternTests {
        @Test
        @DisplayName("Parse E assert X == Y")
        void parse_eAssertEquals() {
            String output = """
                FAILED test_add - AssertionError
                E       assert 5 == 4
                """;

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("5", failures.get(0).expected());
            assertEquals("4", failures.get(0).actual());
        }

        @Test
        @DisplayName("Parse E AssertionError: assert X == Y")
        void parse_eAssertionErrorAssert() {
            String output = """
                FAILED test_compare - AssertionError
                E       AssertionError: assert 'hello' == 'hallo'
                """;

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("hello", failures.get(0).expected());
            assertEquals("hallo", failures.get(0).actual());
        }

        @Test
        @DisplayName("Parse strings con comillas")
        void parse_quotedStrings() {
            String output = "FAILED test_str - AssertionError: assert 'Take one down' == 'Take it down'";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("Take one down", failures.get(0).expected());
            assertEquals("Take it down", failures.get(0).actual());
        }

        @Test
        @DisplayName("Parse strings con comillas dobles")
        void parse_doubleQuotedStrings() {
            String output = "FAILED test_str - AssertionError: assert \"expected\" == \"actual\"";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("expected", failures.get(0).expected());
            assertEquals("actual", failures.get(0).actual());
        }
    }

    @Nested
    @DisplayName("Tipos de Error")
    class ErrorTypeTests {
        @Test
        @DisplayName("Parse TypeError")
        void parse_typeError() {
            String output = """
                FAILED test_type - TypeError
                E       TypeError: unsupported operand type
                """;

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("TypeError", failures.get(0).errorType());
        }

        @Test
        @DisplayName("Parse ValueError")
        void parse_valueError() {
            String output = "FAILED test_val - ValueError: invalid value";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("ValueError", failures.get(0).errorType());
        }

        @Test
        @DisplayName("Parse IndexError")
        void parse_indexError() {
            String output = "FAILED test_idx - IndexError: list index out of range";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("IndexError", failures.get(0).errorType());
        }
    }

    @Nested
    @DisplayName("parseSingleLine")
    class SingleLineTests {
        @Test
        @DisplayName("parseSingleLine extrae TestFailure")
        void parseSingleLine_extractsFailure() {
            String line = "FAILED test_add - AssertionError: assert 5 == 4";

            Optional<TestFailure> result = parser.parseSingleLine(line);

            assertTrue(result.isPresent());
            assertEquals("test_add", result.get().testName());
            assertEquals("5", result.get().expected());
            assertEquals("4", result.get().actual());
        }

        @Test
        @DisplayName("parseSingleLine con linea vacia retorna empty")
        void parseSingleLine_empty_returnsEmpty() {
            assertTrue(parser.parseSingleLine("").isEmpty());
            assertTrue(parser.parseSingleLine("   ").isEmpty());
            assertTrue(parser.parseSingleLine(null).isEmpty());
        }

        @Test
        @DisplayName("parseSingleLine con PASSED retorna empty")
        void parseSingleLine_passed_returnsEmpty() {
            assertTrue(parser.parseSingleLine("PASSED test_add").isEmpty());
        }
    }

    @Nested
    @DisplayName("Metodos de Utilidad")
    class UtilityMethodTests {
        @Test
        @DisplayName("hasFailures detecta FAILED")
        void hasFailures_detectsFailed() {
            assertTrue(parser.hasFailures("FAILED test_add"));
            assertTrue(parser.hasFailures("2 failed, 3 passed"));
            assertTrue(parser.hasFailures("Error in test"));
        }

        @Test
        @DisplayName("hasFailures retorna false sin errores")
        void hasFailures_falseWithoutErrors() {
            assertFalse(parser.hasFailures("PASSED test_add"));
            assertFalse(parser.hasFailures("5 passed"));
            assertFalse(parser.hasFailures(""));
            assertFalse(parser.hasFailures(null));
        }

        @Test
        @DisplayName("countFailures cuenta correctamente")
        void countFailures_countsCorrectly() {
            String output = """
                FAILED test_add - AssertionError
                FAILED test_sub - AssertionError
                PASSED test_mul
                FAILED test_div - ZeroDivisionError
                """;

            assertEquals(3, parser.countFailures(output));
        }

        @Test
        @DisplayName("extractFailedTestNames extrae nombres")
        void extractFailedTestNames_extractsNames() {
            String output = """
                FAILED test_add - AssertionError
                FAILED test_sub - TypeError
                PASSED test_mul
                """;

            List<String> names = parser.extractFailedTestNames(output);

            assertEquals(2, names.size());
            assertTrue(names.contains("test_add"));
            assertTrue(names.contains("test_sub"));
        }
    }

    @Nested
    @DisplayName("Ejemplos del Benchmark Real")
    class RealWorldExamples {
        @Test
        @DisplayName("Parse output de beer-song benchmark")
        void parse_beerSongOutput() {
            String output = """
                ../../../../../../../../../../../../../../../dev::BeerSongTest::test_all_verses FAILED [ 12%]
                E       AssertionError: assert '99 bottles of beer on the wall' == '99 bottles of beer'
                """;

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("test_all_verses", failures.get(0).testName());
            assertTrue(failures.get(0).isStringComparison());
        }

        @Test
        @DisplayName("Parse output con multiples assertions")
        void parse_multipleAssertions() {
            String output = """
                FAILED test_add - AssertionError: assert 5 == 4
                E       assert 5 == 4
                E       +  where 5 = add(2, 3)
                FAILED test_sub - AssertionError: assert 1 == 2
                E       assert 1 == 2
                """;

            List<TestFailure> failures = parser.parse(output);

            assertEquals(2, failures.size());
            assertEquals("5", failures.get(0).expected());
            assertEquals("4", failures.get(0).actual());
            assertEquals("1", failures.get(1).expected());
            assertEquals("2", failures.get(1).actual());
        }

        @Test
        @DisplayName("Parse detecta OFF_BY_ONE")
        void parse_detectsOffByOne() {
            String output = "FAILED test_count - AssertionError: assert 10 == 9";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertTrue(failures.get(0).isOffByOne());
        }

        @Test
        @DisplayName("Parse detecta STRING_TYPO")
        void parse_detectsStringTypo() {
            String output = "FAILED test_song - AssertionError: assert 'Take one down' == 'Take ane down'";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertTrue(failures.get(0).isSingleCharDifference());
        }
    }

    @Nested
    @DisplayName("Casos Borde")
    class EdgeCases {
        @Test
        @DisplayName("Parse con caracteres especiales en valores")
        void parse_specialCharacters() {
            String output = "FAILED test_regex - AssertionError: assert '\\n\\t' == '\\n'";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
        }

        @Test
        @DisplayName("Parse con valores numericos flotantes")
        void parse_floatValues() {
            String output = "FAILED test_pi - AssertionError: assert 3.14159 == 3.14";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertTrue(failures.get(0).isNumericComparison());
        }

        @Test
        @DisplayName("Parse con listas como valores")
        void parse_listValues() {
            String output = "FAILED test_list - AssertionError: assert [1, 2, 3] == [1, 2]";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertEquals("[1, 2, 3]", failures.get(0).expected());
            assertEquals("[1, 2]", failures.get(0).actual());
        }

        @Test
        @DisplayName("Parse con None como valor")
        void parse_noneValue() {
            String output = "FAILED test_none - AssertionError: assert [1, 2] == None";

            List<TestFailure> failures = parser.parse(output);

            assertEquals(1, failures.size());
            assertTrue(failures.get(0).isEmptyActual());
        }
    }
}
