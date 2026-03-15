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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Evaluation - Resultados de Evaluacion")
class EvaluationTest {
    @Nested
    @DisplayName("Evaluation.Pass")
    class PassTests {
        @Test
        @DisplayName("Constructor completo crea Pass correctamente")
        void fullConstructor_createsPass() {
            Evaluation.Pass pass = new Evaluation.Pass("TestCritic", "Mensaje de exito", 0.95);

            assertEquals("TestCritic", pass.criticName());
            assertEquals("Mensaje de exito", pass.message());
            assertEquals(0.95, pass.confidence());
        }

        @Test
        @DisplayName("Constructor simple usa defaults")
        void simpleConstructor_usesDefaults() {
            Evaluation.Pass pass = new Evaluation.Pass("TestCritic");

            assertEquals("TestCritic", pass.criticName());
            assertEquals("Evaluacion exitosa", pass.message());
            assertEquals(1.0, pass.confidence());
        }

        @Test
        @DisplayName("Pass no es bloqueante")
        void pass_isNotBlocking() {
            Evaluation.Pass pass = new Evaluation.Pass("TestCritic");

            assertFalse(pass.isBlocking());
            assertTrue(pass.isPassed());
            assertFalse(pass.hasWarnings());
        }

        @Test
        @DisplayName("Pass tiene severidad INFO")
        void pass_hasSeverityInfo() {
            Evaluation.Pass pass = new Evaluation.Pass("TestCritic");

            assertEquals(Evaluation.Severity.INFO, pass.severity());
        }

        @Test
        @DisplayName("toSummary() genera resumen correcto")
        void toSummary_generatesCorrectSummary() {
            Evaluation.Pass pass = new Evaluation.Pass("TestCritic", "OK", 0.9);

            String summary = pass.toSummary();

            assertTrue(summary.contains("[PASS]"));
            assertTrue(summary.contains("TestCritic"));
            assertTrue(summary.contains("OK"));
            assertTrue(summary.contains("90%"));
        }
    }

    @Nested
    @DisplayName("Evaluation.Warning")
    class WarningTests {
        @Test
        @DisplayName("Constructor completo crea Warning correctamente")
        void fullConstructor_createsWarning() {
            Evaluation.Warning warn = new Evaluation.Warning(
                "TestCritic",
                List.of("Issue 1", "Issue 2"),
                List.of("Sugerencia 1")
            );

            assertEquals("TestCritic", warn.criticName());
            assertEquals(2, warn.issues().size());
            assertEquals(1, warn.suggestions().size());
        }

        @Test
        @DisplayName("Constructor simple con un issue")
        void simpleConstructor_withOneIssue() {
            Evaluation.Warning warn = new Evaluation.Warning("TestCritic", "Un problema");

            assertEquals("TestCritic", warn.criticName());
            assertEquals(1, warn.issues().size());
            assertEquals("Un problema", warn.issues().get(0));
            assertTrue(warn.suggestions().isEmpty());
        }

        @Test
        @DisplayName("Warning no es bloqueante pero tiene warnings")
        void warning_isNotBlockingButHasWarnings() {
            Evaluation.Warning warn = new Evaluation.Warning("TestCritic", "Issue");

            assertFalse(warn.isBlocking());
            assertFalse(warn.isPassed());
            assertTrue(warn.hasWarnings());
        }

        @Test
        @DisplayName("Warning tiene severidad WARNING")
        void warning_hasSeverityWarning() {
            Evaluation.Warning warn = new Evaluation.Warning("TestCritic", "Issue");

            assertEquals(Evaluation.Severity.WARNING, warn.severity());
        }

        @Test
        @DisplayName("Issues list es inmutable")
        void issues_isImmutable() {
            Evaluation.Warning warn = new Evaluation.Warning("TestCritic", List.of("Issue"));

            assertThrows(UnsupportedOperationException.class, () ->
                warn.issues().add("Nuevo issue"));
        }

        @Test
        @DisplayName("Constructor maneja null issues")
        void constructor_handlesNullIssues() {
            Evaluation.Warning warn = new Evaluation.Warning("TestCritic", null, null);

            assertNotNull(warn.issues());
            assertTrue(warn.issues().isEmpty());
        }
    }

    @Nested
    @DisplayName("Evaluation.Fail")
    class FailTests {
        @Test
        @DisplayName("Constructor completo crea Fail correctamente")
        void fullConstructor_createsFail() {
            Evaluation.Fail fail = new Evaluation.Fail(
                "TestCritic",
                "Razon del fallo",
                Optional.of("Evidencia"),
                Optional.of("Sugerencia de fix")
            );

            assertEquals("TestCritic", fail.criticName());
            assertEquals("Razon del fallo", fail.reason());
            assertTrue(fail.evidence().isPresent());
            assertTrue(fail.suggestedFix().isPresent());
        }

        @Test
        @DisplayName("Constructor simple con solo razon")
        void simpleConstructor_withOnlyReason() {
            Evaluation.Fail fail = new Evaluation.Fail("TestCritic", "Razon");

            assertEquals("TestCritic", fail.criticName());
            assertEquals("Razon", fail.reason());
            assertTrue(fail.evidence().isEmpty());
            assertTrue(fail.suggestedFix().isEmpty());
        }

        @Test
        @DisplayName("Constructor con razon y fix")
        void constructorWithReasonAndFix() {
            Evaluation.Fail fail = new Evaluation.Fail("TestCritic", "Razon", "Fix sugerido");

            assertEquals("Fix sugerido", fail.suggestedFix().orElse(null));
        }

        @Test
        @DisplayName("Fail es bloqueante")
        void fail_isBlocking() {
            Evaluation.Fail fail = new Evaluation.Fail("TestCritic", "Razon");

            assertTrue(fail.isBlocking());
            assertFalse(fail.isPassed());
            assertFalse(fail.hasWarnings());
        }

        @Test
        @DisplayName("Fail tiene severidad ERROR")
        void fail_hasSeverityError() {
            Evaluation.Fail fail = new Evaluation.Fail("TestCritic", "Razon");

            assertEquals(Evaluation.Severity.ERROR, fail.severity());
        }

        @Test
        @DisplayName("Constructor lanza excepcion para razon null")
        void constructor_throwsForNullReason() {
            assertThrows(IllegalArgumentException.class, () ->
                new Evaluation.Fail("TestCritic", null));
        }

        @Test
        @DisplayName("Constructor lanza excepcion para razon vacia")
        void constructor_throwsForBlankReason() {
            assertThrows(IllegalArgumentException.class, () ->
                new Evaluation.Fail("TestCritic", "   "));
        }
    }

    @Nested
    @DisplayName("Evaluation.Skip")
    class SkipTests {
        @Test
        @DisplayName("Constructor completo crea Skip correctamente")
        void fullConstructor_createsSkip() {
            Evaluation.Skip skip = new Evaluation.Skip("TestCritic", "No aplica");

            assertEquals("TestCritic", skip.criticName());
            assertEquals("No aplica", skip.reason());
        }

        @Test
        @DisplayName("Constructor simple usa default reason")
        void simpleConstructor_usesDefaultReason() {
            Evaluation.Skip skip = new Evaluation.Skip("TestCritic");

            assertEquals("TestCritic", skip.criticName());
            assertEquals("Evaluacion no aplica", skip.reason());
        }

        @Test
        @DisplayName("Skip no es bloqueante ni passed")
        void skip_isNotBlockingOrPassed() {
            Evaluation.Skip skip = new Evaluation.Skip("TestCritic");

            assertFalse(skip.isBlocking());
            assertFalse(skip.isPassed());
            assertFalse(skip.hasWarnings());
        }

        @Test
        @DisplayName("Skip tiene severidad DEBUG")
        void skip_hasSeverityDebug() {
            Evaluation.Skip skip = new Evaluation.Skip("TestCritic");

            assertEquals(Evaluation.Severity.DEBUG, skip.severity());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {
        @Test
        @DisplayName("pass() crea Pass simple")
        void pass_createsSimplePass() {
            Evaluation eval = Evaluation.pass("TestCritic");

            assertInstanceOf(Evaluation.Pass.class, eval);
            assertEquals("TestCritic", eval.criticName());
        }

        @Test
        @DisplayName("pass() con mensaje")
        void pass_withMessage() {
            Evaluation eval = Evaluation.pass("TestCritic", "Mensaje");

            assertInstanceOf(Evaluation.Pass.class, eval);
            assertEquals("Mensaje", ((Evaluation.Pass) eval).message());
        }

        @Test
        @DisplayName("warning() crea Warning")
        void warning_createsWarning() {
            Evaluation eval = Evaluation.warning("TestCritic", "Issue");

            assertInstanceOf(Evaluation.Warning.class, eval);
        }

        @Test
        @DisplayName("warning() con lista de issues")
        void warning_withIssueList() {
            Evaluation eval = Evaluation.warning("TestCritic", List.of("Issue1", "Issue2"));

            assertInstanceOf(Evaluation.Warning.class, eval);
            assertEquals(2, ((Evaluation.Warning) eval).issues().size());
        }

        @Test
        @DisplayName("fail() crea Fail")
        void fail_createsFail() {
            Evaluation eval = Evaluation.fail("TestCritic", "Razon");

            assertInstanceOf(Evaluation.Fail.class, eval);
        }

        @Test
        @DisplayName("fail() con fix sugerido")
        void fail_withSuggestedFix() {
            Evaluation eval = Evaluation.fail("TestCritic", "Razon", "Fix");

            assertInstanceOf(Evaluation.Fail.class, eval);
            assertEquals("Fix", ((Evaluation.Fail) eval).suggestedFix().orElse(null));
        }

        @Test
        @DisplayName("skip() crea Skip")
        void skip_createsSkip() {
            Evaluation eval = Evaluation.skip("TestCritic");

            assertInstanceOf(Evaluation.Skip.class, eval);
        }

        @Test
        @DisplayName("skip() con razon")
        void skip_withReason() {
            Evaluation eval = Evaluation.skip("TestCritic", "No aplica");

            assertInstanceOf(Evaluation.Skip.class, eval);
            assertEquals("No aplica", ((Evaluation.Skip) eval).reason());
        }
    }

    @Nested
    @DisplayName("Pattern Matching")
    class PatternMatchingTests {
        @Test
        @DisplayName("Switch expression maneja todos los tipos")
        void switchExpression_handlesAllTypes() {
            List<Evaluation> evaluations = List.of(
                Evaluation.pass("C1"),
                Evaluation.warning("C2", "Issue"),
                Evaluation.fail("C3", "Razon"),
                Evaluation.skip("C4")
            );

            for (Evaluation eval : evaluations) {
                String result = switch (eval) {
                    case Evaluation.Pass p -> "PASS";
                    case Evaluation.Warning w -> "WARN";
                    case Evaluation.Fail f -> "FAIL";
                    case Evaluation.Skip s -> "SKIP";
                };
                assertNotNull(result);
            }
        }
    }
}
