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
package dev.fararoni.core.core.reflexion.critics;

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.Evaluation;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.reflexion.testoutput.PytestOutputParser;
import dev.fararoni.core.core.reflexion.testoutput.TestFailure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class TestOutputCritic implements Critic {
    private static final String NAME = "TestOutputCritic";

    @Deprecated
    public static final String TEST_OUTPUT_KEY = EvaluationContext.KEY_TEST_OUTPUT;

    @Deprecated
    public static final String ATTEMPT_NUMBER_KEY = EvaluationContext.KEY_ATTEMPT_NUMBER;

    private final PytestOutputParser parser;

    private final boolean detailedFeedback;

    private final int maxFailuresInFeedback;

    public TestOutputCritic() {
        this(new PytestOutputParser(), true, 5);
    }

    public TestOutputCritic(PytestOutputParser parser) {
        this(parser, true, 5);
    }

    public TestOutputCritic(PytestOutputParser parser, boolean detailedFeedback, int maxFailuresInFeedback) {
        this.parser = Objects.requireNonNull(parser, "parser no puede ser null");
        this.detailedFeedback = detailedFeedback;
        this.maxFailuresInFeedback = maxFailuresInFeedback > 0 ? maxFailuresInFeedback : 5;
    }

    public TestOutputCritic withDetailedFeedback(boolean detailed) {
        return new TestOutputCritic(this.parser, detailed, this.maxFailuresInFeedback);
    }

    public TestOutputCritic withMaxFailures(int max) {
        return new TestOutputCritic(this.parser, this.detailedFeedback, max);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response no puede ser null");
        Objects.requireNonNull(context, "context no puede ser null");

        Optional<String> testOutput = context.getMetadata(EvaluationContext.KEY_TEST_OUTPUT);

        if (testOutput.isEmpty() || testOutput.get().isBlank()) {
            return Evaluation.skip(NAME, "No hay test output en el contexto");
        }

        String output = testOutput.get();

        if (!parser.hasFailures(output)) {
            return Evaluation.pass(NAME, "Todos los tests pasaron");
        }

        List<TestFailure> failures = parser.parse(output);

        if (failures.isEmpty()) {
            return new Evaluation.Fail(
                NAME,
                "Tests fallaron pero no se pudo extraer detalles",
                Optional.of(truncate(output, 500)),
                Optional.of("Revisar el codigo y ejecutar tests manualmente")
            );
        }

        String reason = generateReason(failures);
        String suggestion = generateSuggestion(failures);
        String evidence = generateEvidence(failures, output);

        return new Evaluation.Fail(NAME, reason, Optional.of(evidence), Optional.of(suggestion));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Analiza output de pytest para generar feedback rico de self-correction";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.CODE;
    }

    private String generateReason(List<TestFailure> failures) {
        int count = failures.size();
        if (count == 1) {
            TestFailure f = failures.get(0);
            return String.format("Test '%s' fallo: %s", f.testName(), f.errorType());
        }
        return String.format("%d tests fallaron", count);
    }

    private String generateSuggestion(List<TestFailure> failures) {
        StringBuilder sb = new StringBuilder();

        long offByOne = failures.stream().filter(TestFailure::isOffByOne).count();
        long singleChar = failures.stream().filter(TestFailure::isSingleCharDifference).count();
        long emptyResult = failures.stream().filter(TestFailure::isEmptyActual).count();
        long typeErrors = failures.stream().filter(TestFailure::isTypeError).count();

        sb.append("## Sugerencias de correccion\n\n");

        if (offByOne > 0) {
            sb.append(String.format("- **OFF_BY_ONE (%d):** Revisa indices, rangos (<=/<), y operaciones +1/-1\n", offByOne));
        }

        if (singleChar > 0) {
            sb.append(String.format("- **STRING_TYPO (%d):** Revisa strings caracter por caracter\n", singleChar));
        }

        if (emptyResult > 0) {
            sb.append(String.format("- **EMPTY_RESULT (%d):** Verifica que la funcion retorne valor (no None/null)\n", emptyResult));
        }

        if (typeErrors > 0) {
            sb.append(String.format("- **TYPE_ERROR (%d):** Verifica tipos de retorno y conversiones\n", typeErrors));
        }

        if (offByOne == 0 && singleChar == 0 && emptyResult == 0 && typeErrors == 0) {
            sb.append("- Compara expected vs actual para cada test\n");
            sb.append("- Revisa la logica de tu implementacion\n");
        }

        return sb.toString();
    }

    private String generateEvidence(List<TestFailure> failures, String rawOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Tests Fallidos\n\n");

        int shown = Math.min(failures.size(), maxFailuresInFeedback);

        for (int i = 0; i < shown; i++) {
            TestFailure f = failures.get(i);

            if (detailedFeedback) {
                sb.append(f.toDetailedSummary());
            } else {
                sb.append(f.toShortSummary());
            }
            sb.append("\n");
        }

        if (failures.size() > maxFailuresInFeedback) {
            sb.append(String.format("\n... y %d tests mas\n", failures.size() - maxFailuresInFeedback));
        }

        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    public List<TestFailure> parseFailures(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) {
            return List.of();
        }
        return parser.parse(testOutput);
    }

    public Optional<String> generateFeedback(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) {
            return Optional.empty();
        }

        List<TestFailure> failures = parser.parse(testOutput);
        if (failures.isEmpty()) {
            return Optional.empty();
        }

        String feedback = generateEvidence(failures, testOutput) + "\n" + generateSuggestion(failures);
        return Optional.of(feedback);
    }
}
