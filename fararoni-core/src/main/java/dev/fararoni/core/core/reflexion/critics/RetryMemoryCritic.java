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
import dev.fararoni.core.core.reflexion.memory.AttemptMemory;
import dev.fararoni.core.core.reflexion.memory.RetryAttempt;
import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import dev.fararoni.core.core.reflexion.testoutput.PytestOutputParser;
import dev.fararoni.core.core.reflexion.testoutput.TestFailure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class RetryMemoryCritic implements Critic {
    private static final String NAME = "RetryMemoryCritic";

    public static final String KEY_EXERCISE_ID = "exerciseId";

    private final AttemptMemory memory;

    private final PytestOutputParser parser;

    private final boolean blockOnIdenticalCode;

    public RetryMemoryCritic() {
        this(new AttemptMemory(), new PytestOutputParser(), false);
    }

    public RetryMemoryCritic(AttemptMemory memory) {
        this(memory, new PytestOutputParser(), false);
    }

    public RetryMemoryCritic(AttemptMemory memory, PytestOutputParser parser, boolean blockOnIdenticalCode) {
        this.memory = Objects.requireNonNull(memory, "memory no puede ser null");
        this.parser = Objects.requireNonNull(parser, "parser no puede ser null");
        this.blockOnIdenticalCode = blockOnIdenticalCode;
    }

    public RetryMemoryCritic withBlockOnIdenticalCode(boolean block) {
        return new RetryMemoryCritic(this.memory, this.parser, block);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response no puede ser null");
        Objects.requireNonNull(context, "context no puede ser null");

        String exerciseId = getExerciseId(context);

        int attemptNumber = context.getMetadataOrDefault(EvaluationContext.KEY_ATTEMPT_NUMBER, 1);

        List<TestFailure> failures = parseFailures(context);

        RetryAttempt currentAttempt = RetryAttempt.of(attemptNumber, response, failures);
        memory.recordAttempt(exerciseId, currentAttempt);

        if (attemptNumber <= 1 || memory.getAttemptCount(exerciseId) < 2) {
            return Evaluation.pass(NAME, "Primer intento registrado");
        }

        return checkForRepetitions(exerciseId, currentAttempt);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Detecta repeticion de errores entre intentos y sugiere cambios de estrategia";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.CODE;
    }

    private Evaluation checkForRepetitions(String exerciseId, RetryAttempt currentAttempt) {
        StringBuilder warnings = new StringBuilder();
        boolean hasSerious = false;

        if (memory.isRepeatingCode(exerciseId)) {
            warnings.append("**CODIGO IDENTICO:** El codigo es igual al intento anterior. ");
            warnings.append("Debes cambiar tu implementacion.\n\n");
            hasSerious = true;
        }

        Set<FailurePattern> repeatedPatterns = memory.getRepeatedPatterns(exerciseId, 3);
        if (!repeatedPatterns.isEmpty()) {
            warnings.append("**PATRONES REPETIDOS:**\n");
            for (FailurePattern pattern : repeatedPatterns) {
                warnings.append(String.format("- %s: %s\n", pattern.name(), pattern.getSuggestion()));
            }
            warnings.append("\n");
        }

        if (memory.isRepeatingSameFailingTests(exerciseId)) {
            warnings.append("**MISMOS TESTS FALLANDO:** Los mismos tests siguen fallando.\n");
            warnings.append("Tests: ");
            warnings.append(String.join(", ", currentAttempt.failingTestNames()));
            warnings.append("\n\n");
        }

        if (warnings.isEmpty()) {
            return Evaluation.pass(NAME, "Sin repeticiones detectadas");
        }

        String suggestion = generateSuggestion(exerciseId, repeatedPatterns);

        if (hasSerious && blockOnIdenticalCode) {
            return new Evaluation.Fail(
                NAME,
                "Codigo identico detectado - se requiere cambio de estrategia",
                Optional.of(warnings.toString()),
                Optional.of(suggestion)
            );
        }

        return new Evaluation.Warning(
            NAME,
            List.of("Repeticion de errores detectada", warnings.toString()),
            List.of(suggestion)
        );
    }

    private String generateSuggestion(String exerciseId, Set<FailurePattern> repeatedPatterns) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Sugerencias para Evitar Repeticiones\n\n");

        sb.append("1. **Cambia tu enfoque:** El codigo anterior no funciono, prueba algo diferente.\n");
        sb.append("2. **Lee el error con atencion:** No ignores el feedback de los tests.\n");

        if (repeatedPatterns.contains(FailurePattern.OFF_BY_ONE)) {
            sb.append("3. **OFF_BY_ONE recurrente:** Revisa TODOS los indices y rangos, no solo uno.\n");
        }

        if (repeatedPatterns.contains(FailurePattern.EMPTY_RESULT)) {
            sb.append("3. **EMPTY_RESULT recurrente:** Asegurate de que TODAS las ramas tienen return.\n");
        }

        if (repeatedPatterns.contains(FailurePattern.LOGIC_INVERSION)) {
            sb.append("3. **LOGIC_INVERSION recurrente:** Verifica la logica boolean completa.\n");
        }

        sb.append("\n**Historico:**\n");
        sb.append(memory.getHistorySummary(exerciseId));

        return sb.toString();
    }

    private String getExerciseId(EvaluationContext context) {
        Optional<String> fromMetadata = context.getMetadata(KEY_EXERCISE_ID);
        if (fromMetadata.isPresent() && !fromMetadata.get().isBlank()) {
            return fromMetadata.get();
        }

        String prompt = context.getUserPrompt();
        if (prompt != null && !prompt.isBlank()) {
            return "exercise-" + Math.abs(prompt.hashCode());
        }

        return "exercise-unknown";
    }

    private List<TestFailure> parseFailures(EvaluationContext context) {
        Optional<String> testOutput = context.getMetadata(EvaluationContext.KEY_TEST_OUTPUT);
        if (testOutput.isEmpty() || testOutput.get().isBlank()) {
            return List.of();
        }

        return parser.parse(testOutput.get());
    }

    public AttemptMemory getMemory() {
        return memory;
    }

    public boolean hasRepetitions(String exerciseId) {
        return memory.isRepeatingCode(exerciseId) ||
               memory.isRepeatingSameFailingTests(exerciseId) ||
               !memory.getRepeatedPatterns(exerciseId, 3).isEmpty();
    }

    public Optional<String> getSuggestion(String exerciseId) {
        return memory.getSuggestionToAvoidRepeat(exerciseId);
    }
}
