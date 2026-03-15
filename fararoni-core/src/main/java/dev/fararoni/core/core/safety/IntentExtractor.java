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
package dev.fararoni.core.core.safety;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class IntentExtractor {
    private static final Pattern ADD_FIELD_PATTERN = Pattern.compile(
        "(?i)(agrega|agregar|añade|añadir|add|crear|crea)\\s+" +
        "(un\\s+)?(atributo|campo|field|attribute|variable|propiedad|property)\\s+" +
        "(?:de\\s+)?(?:tipo\\s+)?(?:\\w+\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    private static final Pattern ADD_FIELD_SIMPLE = Pattern.compile(
        "(?i)(agrega|agregar|añade|add)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*:"
    );

    private static final Pattern ADD_METHOD_PATTERN = Pattern.compile(
        "(?i)(agrega|agregar|añade|añadir|add|crear|crea)\\s+" +
        "(un\\s+)?(metodo|método|method|funcion|función|function)\\s+" +
        "([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    private static final Pattern DELETE_METHOD_PATTERN = Pattern.compile(
        "(?i)(elimina|eliminar|borra|borrar|delete|remove|quita|quitar)\\s+" +
        "(el\\s+)?(metodo|método|method|funcion|función|function)\\s+" +
        "([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    private static final Pattern REFACTOR_PATTERN = Pattern.compile(
        "(?i)(refactoriza|refactorizar|refactor|restructura|reestructura|rewrite)"
    );

    private static final Pattern RENAME_PATTERN = Pattern.compile(
        "(?i)(renombra|renombrar|rename|cambia\\s+el\\s+nombre)\\s+" +
        "(?:de\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s+" +
        "(?:a|to|por)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"
    );

    public static Intent parse(String request) {
        if (request == null || request.isBlank()) {
            return new Intent(IntentType.UNKNOWN, null);
        }

        String normalized = request.toLowerCase().trim();

        Matcher addFieldMatcher = ADD_FIELD_PATTERN.matcher(request);
        if (addFieldMatcher.find()) {
            String fieldName = addFieldMatcher.group(4);
            return new Intent(IntentType.ADD_FIELD, fieldName);
        }

        Matcher addFieldSimple = ADD_FIELD_SIMPLE.matcher(request);
        if (addFieldSimple.find()) {
            String fieldName = addFieldSimple.group(2);
            return new Intent(IntentType.ADD_FIELD, fieldName);
        }

        Matcher addMethodMatcher = ADD_METHOD_PATTERN.matcher(request);
        if (addMethodMatcher.find()) {
            String methodName = addMethodMatcher.group(4);
            return new Intent(IntentType.ADD_METHOD, methodName);
        }

        Matcher deleteMethodMatcher = DELETE_METHOD_PATTERN.matcher(request);
        if (deleteMethodMatcher.find()) {
            String methodName = deleteMethodMatcher.group(4);
            return new Intent(IntentType.DELETE_METHOD, methodName);
        }

        Matcher renameMatcher = RENAME_PATTERN.matcher(request);
        if (renameMatcher.find()) {
            String oldName = renameMatcher.group(2);
            String newName = renameMatcher.group(3);
            return new Intent(IntentType.RENAME, oldName + " -> " + newName);
        }

        if (REFACTOR_PATTERN.matcher(normalized).find()) {
            return new Intent(IntentType.REFACTOR, null);
        }

        return new Intent(IntentType.UNKNOWN, null);
    }

    public enum IntentType {
        ADD_FIELD,
        ADD_METHOD,
        DELETE_METHOD,
        REFACTOR,
        RENAME,
        UNKNOWN
    }

    public record Intent(IntentType type, String subject) {
        public boolean requiresComplianceCheck() {
            return type == IntentType.ADD_FIELD ||
                   type == IntentType.ADD_METHOD ||
                   type == IntentType.RENAME;
        }

        public boolean isRefactor() {
            return type == IntentType.REFACTOR;
        }
    }
}
