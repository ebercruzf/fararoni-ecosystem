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
import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import dev.fararoni.core.core.reflexion.testoutput.FailurePatternMatcher;
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
public final class DiffStrategyCritic implements Critic {
    private static final String NAME = "DiffStrategyCritic";

    private final PytestOutputParser parser;

    private final int maxStrategies;

    private final boolean includeCodeExamples;

    public DiffStrategyCritic() {
        this(new PytestOutputParser(), 3, true);
    }

    public DiffStrategyCritic(PytestOutputParser parser) {
        this(parser, 3, true);
    }

    public DiffStrategyCritic(PytestOutputParser parser, int maxStrategies, boolean includeCodeExamples) {
        this.parser = Objects.requireNonNull(parser, "parser no puede ser null");
        this.maxStrategies = maxStrategies > 0 ? maxStrategies : 3;
        this.includeCodeExamples = includeCodeExamples;
    }

    public DiffStrategyCritic withMaxStrategies(int max) {
        return new DiffStrategyCritic(this.parser, max, this.includeCodeExamples);
    }

    public DiffStrategyCritic withCodeExamples(boolean include) {
        return new DiffStrategyCritic(this.parser, this.maxStrategies, include);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response no puede ser null");
        Objects.requireNonNull(context, "context no puede ser null");

        Optional<String> testOutput = context.getMetadata(EvaluationContext.KEY_TEST_OUTPUT);

        if (testOutput.isEmpty() || testOutput.get().isBlank()) {
            return Evaluation.skip(NAME, "No hay test output para generar estrategias");
        }

        String output = testOutput.get();

        if (!parser.hasFailures(output)) {
            return Evaluation.pass(NAME, "Todos los tests pasaron - no se necesitan estrategias");
        }

        List<TestFailure> failures = parser.parse(output);

        if (failures.isEmpty()) {
            return Evaluation.skip(NAME, "No se pudieron extraer failures para estrategias");
        }

        String strategies = generateStrategies(failures);
        String reason = String.format("Estrategias de correccion para %d test(s) fallido(s)",
            Math.min(failures.size(), maxStrategies));

        return new Evaluation.Fail(NAME, reason, Optional.of(strategies), Optional.empty());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Genera estrategias de correccion especificas basadas en diferencias expected/actual";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.CODE;
    }

    String generateStrategies(List<TestFailure> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Estrategias de Correccion\n\n");

        int count = Math.min(failures.size(), maxStrategies);

        for (int i = 0; i < count; i++) {
            TestFailure failure = failures.get(i);
            sb.append(generateStrategyForFailure(failure, i + 1));
            sb.append("\n---\n\n");
        }

        if (failures.size() > maxStrategies) {
            sb.append(String.format("*... y %d failures adicionales*\n",
                failures.size() - maxStrategies));
        }

        return sb.toString();
    }

    String generateStrategyForFailure(TestFailure failure, int number) {
        StringBuilder sb = new StringBuilder();

        FailurePattern pattern = FailurePatternMatcher.match(failure);

        sb.append(String.format("## %d. Estrategia para `%s`\n\n", number, failure.testName()));

        sb.append("### Diferencia Detectada\n\n");
        if (failure.hasComparison()) {
            sb.append(String.format("- **Expected:** `%s`\n", failure.expected()));
            sb.append(String.format("- **Actual:** `%s`\n", failure.actual()));

            failure.numericDifference().ifPresent(diff ->
                sb.append(String.format("- **Diferencia numerica:** %+.2f\n", diff))
            );

            if (failure.isStringComparison() && !failure.isNumericComparison()) {
                int editDist = failure.stringEditDistance();
                sb.append(String.format("- **Distancia de edicion:** %d caracteres\n", editDist));
            }
        } else {
            sb.append(String.format("- **Error:** %s\n", failure.errorType()));
            if (!failure.fullMessage().isBlank()) {
                sb.append(String.format("- **Mensaje:** %s\n", truncate(failure.fullMessage(), 200)));
            }
        }
        sb.append("\n");

        sb.append(String.format("### Patron: %s\n\n", pattern.name()));
        sb.append(String.format("*%s*\n\n", pattern.getDescription()));

        sb.append("### Estrategia Recomendada\n\n");
        sb.append(generateStrategySteps(failure, pattern));

        if (includeCodeExamples) {
            String examples = generateCodeExamples(pattern);
            if (!examples.isEmpty()) {
                sb.append("\n### Ejemplos de Correccion\n\n");
                sb.append(examples);
            }
        }

        return sb.toString();
    }

    private String generateStrategySteps(TestFailure failure, FailurePattern pattern) {
        StringBuilder sb = new StringBuilder();

        switch (pattern) {
            case OFF_BY_ONE -> {
                sb.append("1. **Buscar operaciones +1/-1** en la funcion\n");
                sb.append("2. **Verificar condiciones de rango:**\n");
                sb.append("   - `<` deberia ser `<=`?\n");
                sb.append("   - `>` deberia ser `>=`?\n");
                sb.append("3. **Revisar indices:**\n");
                sb.append("   - `range(n)` produce `[0, n-1]`, no `[0, n]`\n");
                sb.append("   - `len(x)` es 1 mas que el ultimo indice valido\n");
            }
            case STRING_TYPO -> {
                sb.append("1. **Comparar strings caracter por caracter**\n");
                sb.append("2. **Verificar:**\n");
                sb.append("   - Mayusculas/minusculas\n");
                sb.append("   - Espacios extra\n");
                sb.append("   - Caracteres especiales\n");
                sb.append("3. **Revisar concatenaciones y f-strings**\n");
            }
            case EMPTY_RESULT -> {
                sb.append("1. **Verificar que la funcion tiene `return`**\n");
                sb.append("2. **Buscar early returns** que retornan None implicitamente\n");
                sb.append("3. **Verificar logica de inicializacion** de la variable de resultado\n");
                sb.append("4. **Revisar condiciones** que podrian saltarse el codigo principal\n");
            }
            case LOGIC_INVERSION -> {
                sb.append("1. **Revisar condiciones booleanas:**\n");
                sb.append("   - `and` vs `or`\n");
                sb.append("   - `==` vs `!=`\n");
                sb.append("   - `not` donde no deberia estar (o falta)\n");
                sb.append("2. **Verificar valor de retorno** en caso True vs False\n");
                sb.append("3. **Revisar logica if/else** (pueden estar invertidos)\n");
            }
            case TYPE_MISMATCH -> {
                sb.append("1. **Verificar tipos de entrada** de la funcion\n");
                sb.append("2. **Agregar conversiones** donde sea necesario:\n");
                sb.append("   - `str()`, `int()`, `float()`, `list()`\n");
                sb.append("3. **Revisar operaciones entre tipos** incompatibles\n");
            }
            case INDEX_ERROR -> {
                sb.append("1. **Verificar indices antes de acceder:**\n");
                sb.append("   - `if i < len(lista):`\n");
                sb.append("2. **Revisar bucles:**\n");
                sb.append("   - `range(len(x))` vs `range(len(x)+1)`\n");
                sb.append("3. **Manejar caso de lista vacia**\n");
            }
            case KEY_ERROR -> {
                sb.append("1. **Usar `.get(key, default)`** en lugar de `dict[key]`\n");
                sb.append("2. **Verificar existencia:** `if key in dict:`\n");
                sb.append("3. **Revisar nombre de la clave** (typos, case sensitivity)\n");
            }
            case PRECISION_ERROR -> {
                sb.append("1. **Usar comparacion con tolerancia:**\n");
                sb.append("   - `abs(a - b) < epsilon`\n");
                sb.append("2. **Considerar `round()`** para redondear resultados\n");
                sb.append("3. **Usar `decimal.Decimal`** si se requiere precision exacta\n");
            }
            case ORDER_MISMATCH -> {
                sb.append("1. **Revisar algoritmo de ordenamiento**\n");
                sb.append("2. **Verificar si se necesita:**\n");
                sb.append("   - `sort()` o `sorted()`\n");
                sb.append("   - `reverse=True` o `reverse=False`\n");
                sb.append("3. **Revisar orden de iteracion** en bucles\n");
            }
            case UNHANDLED_EXCEPTION -> {
                sb.append("1. **Agregar try/except** para manejar excepciones\n");
                sb.append("2. **Validar inputs** antes de operar:\n");
                sb.append("   - Division por cero: `if divisor != 0:`\n");
                sb.append("   - Valores None: `if value is not None:`\n");
                sb.append("3. **Retornar valor por defecto** en casos de error\n");
            }
            default -> {
                sb.append("1. **Analizar el error manualmente**\n");
                sb.append("2. **Comparar expected vs actual** linea por linea\n");
                sb.append("3. **Agregar prints de debug** para entender el flujo\n");
                sb.append("4. **Revisar la especificacion** del problema\n");
            }
        }

        return sb.toString();
    }

    private String generateCodeExamples(FailurePattern pattern) {
        return switch (pattern) {
            case OFF_BY_ONE -> """
                ```python
                # ANTES (incorrecto)
                for i in range(len(items)):  # Itera 0 a len-1
                    if i < len(items) - 1:   # Pierde el ultimo

                # DESPUES (correcto)
                for i in range(len(items)):  # Itera 0 a len-1
                    if i <= len(items) - 1:  # Incluye el ultimo

                # O simplemente:
                for i in range(len(items)):
                    # procesar items[i]
                ```
                """;
            case LOGIC_INVERSION -> """
                ```python
                # ANTES (incorrecto)
                def is_valid(x):
                    if x > 0:
                        return False  # Invertido!
                    return True

                # DESPUES (correcto)
                def is_valid(x):
                    if x > 0:
                        return True
                    return False

                # O mas simple:
                def is_valid(x):
                    return x > 0
                ```
                """;
            case EMPTY_RESULT -> """
                ```python
                # ANTES (incorrecto)
                def get_items():
                    items = []
                    for x in data:
                        items.append(x)
                    # Falta return!

                # DESPUES (correcto)
                def get_items():
                    items = []
                    for x in data:
                        items.append(x)
                    return items  # Retornar resultado
                ```
                """;
            case INDEX_ERROR -> """
                ```python
                # ANTES (incorrecto)
                def get_last(items):
                    return items[len(items)]  # Index out of range!

                # DESPUES (correcto)
                def get_last(items):
                    if not items:
                        return None
                    return items[len(items) - 1]  # O items[-1]
                ```
                """;
            case KEY_ERROR -> """
                ```python
                # ANTES (incorrecto)
                value = data['key']  # KeyError si no existe

                # DESPUES (correcto)
                value = data.get('key', default_value)
                # O:
                if 'key' in data:
                    value = data['key']
                ```
                """;
            default -> "";
        };
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    public String generateStrategy(TestFailure failure) {
        return generateStrategyForFailure(failure, 1);
    }

    public Optional<String> generateStrategiesFromOutput(String testOutput) {
        if (testOutput == null || testOutput.isBlank()) {
            return Optional.empty();
        }

        List<TestFailure> failures = parser.parse(testOutput);
        if (failures.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(generateStrategies(failures));
    }
}
