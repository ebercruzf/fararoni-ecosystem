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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SyntaxCritic implements Critic {
    private static final String NAME = "SyntaxCritic";

    private static final Pattern CODE_BLOCK_PATTERN =
        Pattern.compile("```(\\w+)?\\s*\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    private static final Pattern LANGUAGE_PATTERN =
        Pattern.compile("```(java|python|py|javascript|js|typescript|ts|json|sql|bash|sh)\\s*\\n",
            Pattern.CASE_INSENSITIVE);

    private final boolean failOnAnyError;
    private final boolean checkAllCodeBlocks;

    public SyntaxCritic() {
        this(false, true);
    }

    public SyntaxCritic(boolean failOnAnyError, boolean checkAllCodeBlocks) {
        this.failOnAnyError = failOnAnyError;
        this.checkAllCodeBlocks = checkAllCodeBlocks;
    }

    public SyntaxCritic withFailOnAnyError(boolean fail) {
        return new SyntaxCritic(fail, this.checkAllCodeBlocks);
    }

    @Override
    public Evaluation evaluate(String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        if (response.isBlank()) {
            return Evaluation.skip(NAME, "Respuesta vacia");
        }

        List<CodeBlock> codeBlocks = extractCodeBlocks(response);

        if (codeBlocks.isEmpty()) {
            if (context.expectsCode()) {
                return new Evaluation.Warning(NAME,
                    List.of("Se esperaba codigo pero no se encontraron bloques de codigo"),
                    List.of("Usar bloques de codigo markdown (```)"));
            }
            return Evaluation.skip(NAME, "No se encontraron bloques de codigo");
        }

        List<String> allIssues = new ArrayList<>();
        List<String> allSuggestions = new ArrayList<>();

        for (CodeBlock block : codeBlocks) {
            SyntaxResult result = analyzeBlock(block);
            allIssues.addAll(result.issues());
            allSuggestions.addAll(result.suggestions());

            if (!checkAllCodeBlocks && !result.issues().isEmpty()) {
                break;
            }
        }

        if (allIssues.isEmpty()) {
            return Evaluation.pass(NAME,
                String.format("Sintaxis correcta en %d bloque(s) de codigo", codeBlocks.size()));
        }

        if (failOnAnyError) {
            return new Evaluation.Fail(
                NAME,
                String.format("Errores de sintaxis encontrados: %d", allIssues.size()),
                Optional.of(allIssues.get(0)),
                Optional.of(allSuggestions.isEmpty() ? "Corregir errores de sintaxis" : allSuggestions.get(0))
            );
        }

        return new Evaluation.Warning(NAME, allIssues, allSuggestions);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Verifica sintaxis de codigo en respuestas";
    }

    @Override
    public CriticCategory getCategory() {
        return CriticCategory.CODE;
    }

    private List<CodeBlock> extractCodeBlocks(String response) {
        List<CodeBlock> blocks = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);

        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2);
            blocks.add(new CodeBlock(
                language != null ? language.toLowerCase() : "unknown",
                code,
                matcher.start()
            ));
        }

        return blocks;
    }

    private SyntaxResult analyzeBlock(CodeBlock block) {
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        analyzeDelimiters(block.code(), issues, suggestions);

        switch (block.language()) {
            case "java" -> analyzeJava(block.code(), issues, suggestions);
            case "python", "py" -> analyzePython(block.code(), issues, suggestions);
            case "javascript", "js", "typescript", "ts" -> analyzeJavaScript(block.code(), issues, suggestions);
            case "json" -> analyzeJson(block.code(), issues, suggestions);
            case "sql" -> analyzeSql(block.code(), issues, suggestions);
        }

        return new SyntaxResult(issues, suggestions);
    }

    private void analyzeDelimiters(String code, List<String> issues, List<String> suggestions) {
        int braces = countBalance(code, '{', '}');
        int parens = countBalance(code, '(', ')');
        int brackets = countBalance(code, '[', ']');

        if (braces != 0) {
            issues.add(String.format("Llaves desbalanceadas: %+d", braces));
            suggestions.add("Verificar llaves de apertura/cierre");
        }

        if (parens != 0) {
            issues.add(String.format("Parentesis desbalanceados: %+d", parens));
            suggestions.add("Verificar parentesis de apertura/cierre");
        }

        if (brackets != 0) {
            issues.add(String.format("Corchetes desbalanceados: %+d", brackets));
            suggestions.add("Verificar corchetes de apertura/cierre");
        }
    }

    private void analyzeJava(String code, List<String> issues, List<String> suggestions) {
        String[] lines = code.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNum = i + 1;

            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                continue;
            }

            if (shouldHaveSemicolon(line) && !line.endsWith(";") && !line.endsWith("{") && !line.endsWith("}")) {
                issues.add(String.format("Linea %d: posible punto y coma faltante", lineNum));
            }

            if (line.contains("System.out.print(") && !line.contains("System.out.println(") && !line.contains("System.out.printf(")) {
            }

            if (line.matches(".*\"[^\"]*\"\\s*==\\s*\\w+.*") || line.matches(".*\\w+\\s*==\\s*\"[^\"]*\".*")) {
                issues.add(String.format("Linea %d: posible uso de == con String (usar .equals())", lineNum));
                suggestions.add("Usar .equals() para comparar Strings");
            }
        }
    }

    private void analyzePython(String code, List<String> issues, List<String> suggestions) {
        String[] lines = code.split("\\n");
        int prevIndent = 0;
        boolean expectIndent = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            if (line.trim().isEmpty()) {
                continue;
            }

            int indent = countLeadingSpaces(line);

            if (expectIndent && indent <= prevIndent && !line.trim().isEmpty()) {
                issues.add(String.format("Linea %d: se esperaba indentacion despues de ':'", lineNum));
                suggestions.add("Indentar el bloque de codigo correctamente");
            }

            String trimmed = line.trim();
            if (trimmed.matches("^(if|elif|else|for|while|def|class|try|except|finally|with)\\b.*") &&
                !trimmed.endsWith(":") && !trimmed.endsWith("\\")) {
                issues.add(String.format("Linea %d: posible ':' faltante", lineNum));
            }

            expectIndent = trimmed.endsWith(":");
            prevIndent = indent;
        }

        if (code.contains("\t") && code.contains("    ")) {
            issues.add("Mezcla de tabs y espacios en indentacion");
            suggestions.add("Usar consistentemente tabs o espacios (se recomienda 4 espacios)");
        }
    }

    private void analyzeJavaScript(String code, List<String> issues, List<String> suggestions) {
        String[] lines = code.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNum = i + 1;

            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }

            if (line.matches("^var\\s+.*")) {
                issues.add(String.format("Linea %d: uso de 'var' (preferir 'let' o 'const')", lineNum));
                suggestions.add("Usar 'let' para variables mutables, 'const' para inmutables");
            }

            if (line.matches(".*[^=!]==[^=].*") && !line.contains("===")) {
                if (!line.matches(".*[^=!<>]=\\s*[^=].*")) {
                    issues.add(String.format("Linea %d: considerar usar === en lugar de ==", lineNum));
                }
            }
        }
    }

    private void analyzeJson(String code, List<String> issues, List<String> suggestions) {
        String trimmed = code.trim();

        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            issues.add("JSON debe empezar con '{' o '['");
        }

        if (!trimmed.endsWith("}") && !trimmed.endsWith("]")) {
            issues.add("JSON debe terminar con '}' o ']'");
        }

        if (trimmed.matches("[\\s\\S]*,\\s*[}\\]]")) {
            issues.add("Coma final no permitida en JSON");
            suggestions.add("Eliminar la coma antes del cierre de objeto/array");
        }

        if (trimmed.contains("'")) {
            issues.add("JSON requiere comillas dobles, no simples");
            suggestions.add("Reemplazar comillas simples por dobles");
        }
    }

    private void analyzeSql(String code, List<String> issues, List<String> suggestions) {
        String upper = code.toUpperCase();

        if (upper.contains("SELECT") && !upper.contains("FROM") && !upper.contains("SELECT 1")) {
            issues.add("SELECT sin clausula FROM");
        }

        if (upper.matches("(?s).*DELETE\\s+FROM\\s+\\w+(?!.*WHERE).*")) {
            issues.add("DELETE sin clausula WHERE (peligroso - afecta todas las filas)");
            suggestions.add("Agregar clausula WHERE para limitar el delete");
        }

        if (upper.matches("(?s).*UPDATE\\s+\\w+\\s+SET(?!.*WHERE).*")) {
            issues.add("UPDATE sin clausula WHERE (peligroso - afecta todas las filas)");
            suggestions.add("Agregar clausula WHERE para limitar el update");
        }
    }

    private boolean shouldHaveSemicolon(String line) {
        return line.matches(".*\\)$") ||
               line.matches(".*\\w$") ||
               line.matches(".*\"$") ||
               line.matches(".*\\d$") ||
               line.matches(".*\\+\\+$") ||
               line.matches(".*--$");
    }

    private int countBalance(String code, char open, char close) {
        int balance = 0;
        boolean inString = false;
        boolean inChar = false;
        char prev = 0;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            if (c == '"' && prev != '\\' && !inChar) {
                inString = !inString;
            } else if (c == '\'' && prev != '\\' && !inString) {
                inChar = !inChar;
            }

            if (!inString && !inChar) {
                if (c == open) balance++;
                else if (c == close) balance--;
            }

            prev = c;
        }

        return balance;
    }

    private int countLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    private record CodeBlock(String language, String code, int position) {}

    private record SyntaxResult(List<String> issues, List<String> suggestions) {}
}
