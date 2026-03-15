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
@DisplayName("FeedbackFormatter - Formateo de Feedback para LLM")
class FeedbackFormatterTest {
    private FeedbackFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new FeedbackFormatter();
    }

    @Nested
    @DisplayName("Empty/Null Input")
    class EmptyNullInputTests {
        @Test
        @DisplayName("format retorna mensaje para null (hibrido)")
        void format_returnsMessageForNull() {
            String result = formatter.format(null);

            assertEquals("No evaluations to show.", result);
        }

        @Test
        @DisplayName("format retorna mensaje para lista vacia (hibrido)")
        void format_returnsMessageForEmptyList() {
            String result = formatter.format(List.of());

            assertEquals("No evaluations to show.", result);
        }

        @Test
        @DisplayName("formatLegacy retorna mensaje para null")
        void formatLegacy_returnsMessageForNull() {
            String result = formatter.formatLegacy(null);

            assertEquals("Sin evaluaciones para mostrar.", result);
        }

        @Test
        @DisplayName("formatLegacy retorna mensaje para lista vacia")
        void formatLegacy_returnsMessageForEmptyList() {
            String result = formatter.formatLegacy(List.of());

            assertEquals("Sin evaluaciones para mostrar.", result);
        }

        @Test
        @DisplayName("formatCompact retorna vacio para null")
        void formatCompact_returnsEmptyForNull() {
            String result = formatter.formatCompact(null);

            assertEquals("", result);
        }

        @Test
        @DisplayName("formatCompact retorna vacio para lista vacia")
        void formatCompact_returnsEmptyForEmptyList() {
            String result = formatter.formatCompact(List.of());

            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("Fail Formatting - Hybrid (v2.1)")
    class FailFormattingTests {
        @Test
        @DisplayName("format incluye CRITICAL FAILURE ANALYSIS header")
        void format_includesCriticalHeader() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestCritic",
                "Test fallo",
                Optional.empty(),
                Optional.empty()
            );

            String result = formatter.format(List.of(fail));

            assertTrue(result.contains("## CRITICAL FAILURE ANALYSIS:"));
            assertTrue(result.contains("## REQUIRED FIX:"));
        }

        @Test
        @DisplayName("format incluye diff crudo para TestOutputCritic")
        void format_includesDiffForTestOutput() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestOutputCritic",
                "Assertion fallo",
                Optional.of("AssertionError: 'hello' == 'world'\nExpected: hello\nGot: world"),
                Optional.empty()
            );

            String result = formatter.format(List.of(fail));

            assertTrue(result.contains("Expected"));
            assertTrue(result.contains("Got"));
        }

        @Test
        @DisplayName("format incluye Pattern y Fix para PatternCritic")
        void format_includesPatternAndFix() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "FailurePatternCritic",
                "OFF_BY_ONE",
                Optional.empty(),
                Optional.of("Check loop boundaries")
            );

            String result = formatter.format(List.of(fail));

            assertTrue(result.contains("Pattern:"));
            assertTrue(result.contains("OFF_BY_ONE"));
            assertTrue(result.contains("Fix:"));
        }

        @Test
        @DisplayName("format incluye instrucciones REQUIRED FIX")
        void format_includesRequiredFix() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestCritic",
                "Error",
                Optional.empty(),
                Optional.empty()
            );

            String result = formatter.format(List.of(fail));

            assertTrue(result.contains("## REQUIRED FIX:"));
            assertTrue(result.contains("Identify WHY"));
            assertTrue(result.contains("do NOT rewrite everything"));
        }

        @Test
        @DisplayName("format limita a MAX 3 fails")
        void format_limitsToThreeFails() {
            List<Evaluation> evals = List.of(
                new Evaluation.Fail("Critic1", "Error 1", Optional.of("evidence 1"), Optional.empty()),
                new Evaluation.Fail("Critic2", "Error 2", Optional.of("evidence 2"), Optional.empty()),
                new Evaluation.Fail("Critic3", "Error 3", Optional.of("evidence 3"), Optional.empty()),
                new Evaluation.Fail("Critic4", "Error 4", Optional.of("evidence 4"), Optional.empty())
            );

            String result = formatter.format(evals);

            assertTrue(result.contains("Error 1") || result.contains("evidence 1"));
            assertTrue(result.contains("Error 2") || result.contains("evidence 2"));
            assertTrue(result.contains("Error 3") || result.contains("evidence 3"));
            assertFalse(result.contains("evidence 4"));
        }
    }

    @Nested
    @DisplayName("Fail Formatting - Legacy (v2.0)")
    class FailFormattingLegacyTests {
        @Test
        @DisplayName("formatLegacy incluye seccion de problemas")
        void formatLegacy_includesProblemsSection() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestCritic",
                "Test fallo",
                Optional.empty(),
                Optional.empty()
            );

            String result = formatter.formatLegacy(List.of(fail));

            assertTrue(result.contains("# Feedback de Correccion"));
            assertTrue(result.contains("## Problemas Detectados"));
            assertTrue(result.contains("### TestCritic"));
            assertTrue(result.contains("Test fallo"));
        }

        @Test
        @DisplayName("formatLegacy incluye evidence cuando existe")
        void formatLegacy_includesEvidence() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestCritic",
                "Assertion fallo",
                Optional.of("Expected 5 but got 4"),
                Optional.empty()
            );

            String result = formatter.formatLegacy(List.of(fail));

            assertTrue(result.contains("**Detalle:**"));
            assertTrue(result.contains("Expected 5 but got 4"));
        }

        @Test
        @DisplayName("formatLegacy incluye sugerencia cuando existe")
        void formatLegacy_includesSuggestion() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestCritic",
                "Off by one error",
                Optional.empty(),
                Optional.of("Revisa el indice del loop")
            );

            String result = formatter.formatLegacy(List.of(fail));

            assertTrue(result.contains("**Sugerencia:**"));
            assertTrue(result.contains("Revisa el indice del loop"));
        }

        @Test
        @DisplayName("formatLegacy incluye instrucciones cuando hay fails")
        void formatLegacy_includesInstructions() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestCritic",
                "Error",
                Optional.empty(),
                Optional.empty()
            );

            String result = formatter.formatLegacy(List.of(fail));

            assertTrue(result.contains("## Instrucciones"));
            assertTrue(result.contains("Corrige el codigo"));
        }

        @Test
        @DisplayName("formatLegacy agrupa multiples fails")
        void formatLegacy_groupsMultipleFails() {
            List<Evaluation> evals = List.of(
                new Evaluation.Fail("Critic1", "Error 1", Optional.empty(), Optional.empty()),
                new Evaluation.Fail("Critic2", "Error 2", Optional.empty(), Optional.empty())
            );

            String result = formatter.formatLegacy(evals);

            assertTrue(result.contains("### Critic1"));
            assertTrue(result.contains("### Critic2"));
            assertTrue(result.contains("Error 1"));
            assertTrue(result.contains("Error 2"));
        }
    }

    @Nested
    @DisplayName("Warning Formatting - Legacy")
    class WarningFormattingTests {
        @Test
        @DisplayName("formatLegacy incluye seccion de advertencias")
        void formatLegacy_includesWarningsSection() {
            Evaluation.Warning warning = new Evaluation.Warning(
                "MemoryCritic",
                List.of("Patron repetido"),
                List.of("Cambia tu estrategia")
            );

            String result = formatter.formatLegacy(List.of(warning));

            assertTrue(result.contains("## Advertencias"));
            assertTrue(result.contains("### MemoryCritic"));
        }

        @Test
        @DisplayName("formatLegacy incluye issues de warning")
        void formatLegacy_includesWarningIssues() {
            Evaluation.Warning warning = new Evaluation.Warning(
                "Critic",
                List.of("Issue 1", "Issue 2"),
                List.of()
            );

            String result = formatter.formatLegacy(List.of(warning));

            assertTrue(result.contains("**Issues:**"));
            assertTrue(result.contains("- Issue 1"));
            assertTrue(result.contains("- Issue 2"));
        }

        @Test
        @DisplayName("formatLegacy incluye sugerencias de warning")
        void formatLegacy_includesWarningSuggestions() {
            Evaluation.Warning warning = new Evaluation.Warning(
                "Critic",
                List.of(),
                List.of("Sugerencia A", "Sugerencia B")
            );

            String result = formatter.formatLegacy(List.of(warning));

            assertTrue(result.contains("**Sugerencias:**"));
            assertTrue(result.contains("- Sugerencia A"));
            assertTrue(result.contains("- Sugerencia B"));
        }
    }

    @Nested
    @DisplayName("Pass Formatting - Legacy")
    class PassFormattingTests {
        @Test
        @DisplayName("format (hibrido) NO incluye Pass")
        void format_excludesPassByDefault() {
            Evaluation.Pass pass = Evaluation.pass("TestCritic", "Todo bien");

            String result = formatter.format(List.of(pass));

            assertFalse(result.contains("Validaciones Exitosas"));
            assertFalse(result.contains("Todo bien"));
        }

        @Test
        @DisplayName("formatLegacy incluye Pass cuando configurado")
        void formatLegacy_includesPassWhenConfigured() {
            FeedbackFormatter withPass = formatter.withIncludePass(true);
            Evaluation.Pass pass = Evaluation.pass("TestCritic", "Todos los tests pasaron");

            String result = withPass.formatLegacy(List.of(pass));

            assertTrue(result.contains("## Validaciones Exitosas"));
            assertTrue(result.contains("TestCritic"));
            assertTrue(result.contains("Todos los tests pasaron"));
        }
    }

    @Nested
    @DisplayName("Compact Format")
    class CompactFormatTests {
        @Test
        @DisplayName("formatCompact retorna mensaje cuando no hay fails")
        void formatCompact_returnsMessageWhenNoFails() {
            Evaluation.Pass pass = Evaluation.pass("Critic", "OK");

            String result = formatter.formatCompact(List.of(pass));

            assertEquals("All tests passed.", result);
        }

        @Test
        @DisplayName("formatCompact lista fails de forma concisa")
        void formatCompact_listsFailsConcisely() {
            List<Evaluation> evals = List.of(
                new Evaluation.Fail("Critic1", "Error 1", Optional.empty(), Optional.empty()),
                new Evaluation.Fail("Critic2", "Error 2", Optional.empty(), Optional.empty())
            );

            String result = formatter.formatCompact(evals);

            assertEquals("- Critic1: Error 1\n- Critic2: Error 2", result);
        }

        @Test
        @DisplayName("formatCompact ignora warnings")
        void formatCompact_ignoresWarnings() {
            List<Evaluation> evals = List.of(
                new Evaluation.Fail("Critic1", "Error", Optional.empty(), Optional.empty()),
                new Evaluation.Warning("MemCritic", List.of("Issue"), List.of())
            );

            String result = formatter.formatCompact(evals);

            assertEquals("- Critic1: Error", result);
            assertFalse(result.contains("MemCritic"));
        }
    }

    @Nested
    @DisplayName("Truncation")
    class TruncationTests {
        @Test
        @DisplayName("trunca evidence cuando excede maxContentLength (hibrido)")
        void truncatesEvidence_whenExceedsMax() {
            FeedbackFormatter shortFormatter = new FeedbackFormatter(false, false, 50);
            String longEvidence = "x".repeat(100);

            Evaluation.Fail fail = new Evaluation.Fail(
                "Critic",
                "Error",
                Optional.of(longEvidence),
                Optional.empty()
            );

            String result = shortFormatter.format(List.of(fail));

            assertTrue(result.contains("... (truncated)"));
            assertFalse(result.contains("x".repeat(100)));
        }

        @Test
        @DisplayName("trunca evidence (formatLegacy)")
        void truncatesEvidence_legacy() {
            FeedbackFormatter shortFormatter = new FeedbackFormatter(false, false, 50);
            String longEvidence = "x".repeat(100);

            Evaluation.Fail fail = new Evaluation.Fail(
                "Critic",
                "Error",
                Optional.of(longEvidence),
                Optional.empty()
            );

            String result = shortFormatter.formatLegacy(List.of(fail));

            assertTrue(result.contains("... (truncated)"));
            assertFalse(result.contains("x".repeat(100)));
        }

        @Test
        @DisplayName("NO trunca cuando esta dentro del limite")
        void doesNotTruncate_whenWithinLimit() {
            FeedbackFormatter formatter = new FeedbackFormatter(false, false, 200);
            String shortEvidence = "Este es un error corto";

            Evaluation.Fail fail = new Evaluation.Fail(
                "Critic",
                "Error",
                Optional.of(shortEvidence),
                Optional.empty()
            );

            String result = formatter.formatLegacy(List.of(fail));

            assertTrue(result.contains(shortEvidence));
            assertFalse(result.contains("(truncated)"));
        }

        @Test
        @DisplayName("withMaxContentLength crea nueva instancia")
        void withMaxContentLength_createsNewInstance() {
            FeedbackFormatter configured = formatter.withMaxContentLength(100);

            assertNotSame(formatter, configured);
        }
    }

    @Nested
    @DisplayName("Fluent Configuration")
    class FluentConfigurationTests {
        @Test
        @DisplayName("withIncludePass crea nueva instancia")
        void withIncludePass_createsNewInstance() {
            FeedbackFormatter configured = formatter.withIncludePass(true);

            assertNotSame(formatter, configured);
        }

        @Test
        @DisplayName("withIncludeSkip crea nueva instancia")
        void withIncludeSkip_createsNewInstance() {
            FeedbackFormatter configured = formatter.withIncludeSkip(true);

            assertNotSame(formatter, configured);
        }

        @Test
        @DisplayName("configuracion es chainable")
        void configuration_isChainable() {
            FeedbackFormatter configured = formatter
                .withIncludePass(true)
                .withIncludeSkip(true)
                .withMaxContentLength(500);

            assertNotNull(configured);
            assertNotSame(formatter, configured);
        }
    }

    @Nested
    @DisplayName("Mixed Evaluations - Legacy")
    class MixedEvaluationTests {
        @Test
        @DisplayName("formatLegacy ordena secciones correctamente")
        void formatLegacy_ordersSectionsCorrectly() {
            List<Evaluation> evals = List.of(
                new Evaluation.Warning("WarnCritic", List.of("Advertencia"), List.of()),
                new Evaluation.Fail("FailCritic", "Error", Optional.empty(), Optional.empty()),
                Evaluation.pass("PassCritic", "OK")
            );

            String result = formatter.withIncludePass(true).formatLegacy(evals);

            int problemsIndex = result.indexOf("## Problemas Detectados");
            int warningsIndex = result.indexOf("## Advertencias");
            int passIndex = result.indexOf("## Validaciones Exitosas");

            assertTrue(problemsIndex < warningsIndex, "Problemas debe ir antes de Advertencias");
            assertTrue(warningsIndex < passIndex, "Advertencias debe ir antes de Pass");
        }

        @Test
        @DisplayName("formatLegacy no incluye instrucciones sin fails")
        void formatLegacy_excludesInstructionsWithoutFails() {
            Evaluation.Warning warning = new Evaluation.Warning(
                "Critic",
                List.of("Advertencia"),
                List.of()
            );

            String result = formatter.formatLegacy(List.of(warning));

            assertFalse(result.contains("## Instrucciones"));
        }
    }

    @Nested
    @DisplayName("Hybrid Format (v2.1)")
    class HybridFormatTests {
        @Test
        @DisplayName("formatHybrid incluye test output truncado")
        void formatHybrid_includesTestOutput() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestOutputCritic",
                "Assertion fallo",
                Optional.of("Expected: hello\nGot: world"),
                Optional.empty()
            );

            String rawOutput = "FAILED test_example - AssertionError";
            String result = formatter.formatHybrid(List.of(fail), rawOutput);

            assertTrue(result.contains("## Test Output (truncated):"));
            assertTrue(result.contains("FAILED test_example"));
        }

        @Test
        @DisplayName("formatHybrid extrae lineas de diff correctamente")
        void formatHybrid_extractsDiffLines() {
            String evidence = """
                Some preamble
                AssertionError: 'foo' == 'bar'
                E       'foo' != 'bar'
                Expected: foo
                Got: bar
                Some epilogue
                """;

            Evaluation.Fail fail = new Evaluation.Fail(
                "TestOutputCritic",
                "Test fallo",
                Optional.of(evidence),
                Optional.empty()
            );

            String result = formatter.format(List.of(fail));

            assertTrue(result.contains("Expected") || result.contains("ASSERTION FAILED") || result.contains("MISMATCH"));
        }

        @Test
        @DisplayName("format solo incluye failures, no warnings ni pass")
        void format_onlyIncludesFailures() {
            List<Evaluation> evals = List.of(
                new Evaluation.Fail("Critic1", "Error 1", Optional.empty(), Optional.empty()),
                new Evaluation.Warning("WarnCritic", List.of("Warning"), List.of()),
                Evaluation.pass("PassCritic", "OK")
            );

            String result = formatter.format(evals);

            assertTrue(result.contains("CRITICAL FAILURE ANALYSIS"));
            assertFalse(result.contains("Warning"));
            assertFalse(result.contains("Validaciones"));
        }
    }
}
