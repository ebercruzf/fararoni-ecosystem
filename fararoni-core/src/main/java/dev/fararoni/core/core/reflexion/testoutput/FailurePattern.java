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

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public enum FailurePattern {
    OFF_BY_ONE(
        "Diferencia de 1 en resultado numerico",
        "Revisa indices, rangos (<=/<), y operaciones +1/-1. "
            + "Verifica si el rango deberia ser inclusive o exclusive.",
        Severity.HIGH
    ),

    PRECISION_ERROR(
        "Diferencia de precision en flotantes",
        "Usa comparacion con tolerancia (abs(a-b) < epsilon). "
            + "Considera usar round() o decimal.Decimal para precision exacta.",
        Severity.MEDIUM
    ),

    STRING_TYPO(
        "Diferencia de un caracter en string",
        "Revisa el string caracter por caracter. "
            + "Verifica mayusculas/minusculas y caracteres especiales.",
        Severity.HIGH
    ),

    STRING_MISMATCH(
        "Strings difieren en multiples caracteres",
        "Compara los strings lado a lado. Verifica la logica de "
            + "concatenacion, formateo, o template strings.",
        Severity.MEDIUM
    ),

    EMPTY_RESULT(
        "Resultado vacio (None/[]/{})",
        "Verifica que la funcion retorne un valor. "
            + "Revisa si falta un 'return' o si hay early exit inesperado.",
        Severity.HIGH
    ),

    EXPECTED_EMPTY(
        "Se esperaba resultado vacio pero hay contenido",
        "Revisa la condicion para casos vacios/nulos. "
            + "Verifica el manejo de edge cases.",
        Severity.MEDIUM
    ),

    TYPE_MISMATCH(
        "Tipos incompatibles en operacion",
        "Verifica los tipos de las variables. "
            + "Agrega conversiones (str(), int(), float()) donde sea necesario.",
        Severity.HIGH
    ),

    WRONG_RETURN_TYPE(
        "Tipo de retorno incorrecto",
        "Verifica que el return sea del tipo esperado. "
            + "Revisa si debes retornar lista vs elemento, string vs int, etc.",
        Severity.MEDIUM
    ),

    INDEX_ERROR(
        "Indice fuera de rango",
        "Verifica los limites del array/lista. "
            + "Revisa si el indice deberia ser len()-1 en lugar de len().",
        Severity.HIGH
    ),

    KEY_ERROR(
        "Clave no encontrada en diccionario",
        "Usa .get(key, default) para evitar KeyError. "
            + "Verifica que la clave exista antes de acceder.",
        Severity.HIGH
    ),

    LOGIC_INVERSION(
        "Resultado logico invertido",
        "Revisa la logica de la condicion. "
            + "Verifica si deberias usar 'and' vs 'or', '==' vs '!=', etc.",
        Severity.HIGH
    ),

    ORDER_MISMATCH(
        "Orden de elementos incorrecto",
        "Revisa el algoritmo de ordenamiento. "
            + "Verifica si debes usar sort(), reverse(), o cambiar la iteracion.",
        Severity.MEDIUM
    ),

    UNHANDLED_EXCEPTION(
        "Excepcion no manejada",
        "Agrega manejo de excepciones con try/except. "
            + "Valida inputs antes de operar con ellos.",
        Severity.HIGH
    ),

    ATTRIBUTE_ERROR(
        "Atributo o metodo no existe",
        "Verifica el nombre del atributo/metodo. "
            + "Revisa si el objeto es del tipo esperado.",
        Severity.MEDIUM
    ),

    UNKNOWN(
        "Patron de error no identificado",
        "Analiza el error manualmente. "
            + "Compara expected vs actual para identificar la causa.",
        Severity.LOW
    );

    private final String description;
    private final String suggestion;
    private final Severity severity;

    FailurePattern(String description, String suggestion, Severity severity) {
        this.description = description;
        this.suggestion = suggestion;
        this.severity = severity;
    }

    public String getDescription() {
        return description;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean isHighSeverity() {
        return severity == Severity.HIGH;
    }

    public String toSummary() {
        return String.format("%s: %s", name(), description);
    }

    public String toFeedback() {
        return String.format("**Patron Detectado:** %s\n- %s\n- **Sugerencia:** %s",
            name(), description, suggestion);
    }

    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }
}
