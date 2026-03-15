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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SelfHealer {
    private static final Logger LOG = Logger.getLogger(SelfHealer.class.getName());

    private static final int MAX_ATTEMPTS = 50;

    private static final int MAX_ATTEMPTS_EXPENSIVE = 10;

    private static final int MAX_VISITED = 500;

    @FunctionalInterface
    public interface SyntaxValidator {
        int countErrors(String code);
    }

    public interface DetailedSyntaxValidator extends SyntaxValidator {
        String getErrorMessage(String code);

        default int extractErrorLine(String errorMessage) {
            if (errorMessage == null) return -1;

            java.util.regex.Pattern[] patterns = {
                java.util.regex.Pattern.compile("[Ll]ine\\s+(\\d+)"),
                java.util.regex.Pattern.compile(":(\\d+):"),
                java.util.regex.Pattern.compile("\\[(\\d+)\\]"),
                java.util.regex.Pattern.compile("at line (\\d+)")
            };

            for (java.util.regex.Pattern p : patterns) {
                java.util.regex.Matcher m = p.matcher(errorMessage);
                if (m.find()) {
                    try {
                        return Integer.parseInt(m.group(1));
                    } catch (NumberFormatException e) {
                    }
                }
            }
            return -1;
        }
    }

    public static class ErrorInfo {
        public final int errorCount;
        public final String errorMessage;
        public final int errorLine;

        public ErrorInfo(int errorCount, String errorMessage, int errorLine) {
            this.errorCount = errorCount;
            this.errorMessage = errorMessage;
            this.errorLine = errorLine;
        }
    }

    private final SyntaxValidator validator;
    private final DetailedSyntaxValidator detailedValidator;
    private final boolean expensiveValidation;
    private int lastAttempts = 0;
    private boolean lastHealSuccessful = false;

    public SelfHealer(SyntaxValidator validator) {
        this(validator, false);
    }

    public SelfHealer(SyntaxValidator validator, boolean expensiveValidation) {
        this.validator = Objects.requireNonNull(validator, "validator no puede ser null");
        this.expensiveValidation = expensiveValidation;

        if (validator instanceof DetailedSyntaxValidator) {
            this.detailedValidator = (DetailedSyntaxValidator) validator;
        } else {
            this.detailedValidator = null;
        }
    }

    public String heal(String brokenCode) {
        Objects.requireNonNull(brokenCode, "brokenCode no puede ser null");

        PriorityQueue<CodeNode> openSet = new PriorityQueue<>();
        Set<String> visited = new HashSet<>();

        int initialErrors = validator.countErrors(brokenCode);
        if (initialErrors == 0) {
            LOG.info("[HEALER] Codigo ya es valido. No requiere curacion.");
            lastHealSuccessful = true;
            lastAttempts = 0;
            return brokenCode;
        }

        LOG.info("[HEALER] Iniciando A* con " + initialErrors + " errores iniciales.");

        int maxAttempts = expensiveValidation ? MAX_ATTEMPTS_EXPENSIVE : MAX_ATTEMPTS;

        openSet.add(new CodeNode(brokenCode, 0, initialErrors));
        visited.add(brokenCode);

        lastAttempts = 0;

        while (!openSet.isEmpty() && lastAttempts < maxAttempts && visited.size() < MAX_VISITED) {
            CodeNode current = openSet.poll();
            lastAttempts++;

            if (current.heuristic == 0) {
                LOG.info("[HEALER] Codigo curado en " + lastAttempts + " intentos.");
                lastHealSuccessful = true;
                return current.code;
            }

            List<String> mutations;
            if (detailedValidator != null) {
                String errorMsg = detailedValidator.getErrorMessage(current.code);
                mutations = generateTargetedMutations(current.code, errorMsg);
            } else {
                mutations = generateMutations(current.code);
            }

            for (String mutatedCode : mutations) {
                if (!visited.contains(mutatedCode)) {
                    int errors = validator.countErrors(mutatedCode);

                    if (errors <= current.heuristic + 1) {
                        CodeNode neighbor = new CodeNode(mutatedCode, current.cost + 1, errors);
                        openSet.add(neighbor);
                        visited.add(mutatedCode);
                    }
                }
            }
        }

        LOG.warning("[HEALER] No se pudo curar el codigo despues de " +
                   lastAttempts + " intentos y " + visited.size() + " nodos visitados.");
        lastHealSuccessful = false;
        return brokenCode;
    }

    private List<String> generateMutations(String code) {
        List<String> mutations = new ArrayList<>();

        mutations.add(code + ")");
        mutations.add(code + "}");
        mutations.add(code + "]");
        mutations.add(code + ";");
        mutations.add(code + "\"");
        mutations.add(code + "'");

        if (code.length() > 1) {
            String beforeLast = code.substring(0, code.length() - 1);
            String lastChar = code.substring(code.length() - 1);
            mutations.add(beforeLast + ")" + lastChar);
            mutations.add(beforeLast + "}" + lastChar);
            mutations.add(beforeLast + "]" + lastChar);
            mutations.add(beforeLast + ";" + lastChar);
        }

        mutations.add(code + "}}");
        mutations.add(code + "))");
        mutations.add(code + "})");
        mutations.add(code + ");");
        mutations.add(code + "}");

        if (!code.contains("import java.util")) {
            mutations.add("import java.util.*;\n" + code);
        }
        if (!code.contains("import java.io")) {
            mutations.add("import java.io.*;\n" + code);
        }
        if (code.contains("List") && !code.contains("import java.util.List")) {
            mutations.add("import java.util.List;\n" + code);
        }
        if (code.contains("Map") && !code.contains("import java.util.Map")) {
            mutations.add("import java.util.Map;\n" + code);
        }

        if (!code.contains("import os") && code.contains("os.")) {
            mutations.add("import os\n" + code);
        }
        if (!code.contains("import sys") && code.contains("sys.")) {
            mutations.add("import sys\n" + code);
        }
        if (!code.contains("import re") && code.contains("re.")) {
            mutations.add("import re\n" + code);
        }

        if (code.contains(":") && code.endsWith(":")) {
            mutations.add(code + "\n    pass");
        }

        if (countOccurrences(code, "\"\"\"") % 2 != 0) {
            mutations.add(code + "\"\"\"");
        }
        if (countOccurrences(code, "'''") % 2 != 0) {
            mutations.add(code + "'''");
        }

        return mutations;
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private List<String> generateTargetedMutations(String code, String errorMessage) {
        List<String> mutations = new ArrayList<>();

        if (errorMessage == null || errorMessage.isEmpty()) {
            return generateMutations(code);
        }

        String errorLower = errorMessage.toLowerCase();
        int errorLine = detailedValidator.extractErrorLine(errorMessage);

        LOG.fine("[HEALER] Error-Driven: \"" + errorMessage + "\" en linea " + errorLine);

        if (containsAny(errorLower, "unexpected eof", "unterminated", "unclosed", "expected '}'")) {
            mutations.addAll(generateClosingMutations(code, errorLine));
        }

        if (containsAny(errorLower, "missing import", "cannot find symbol", "undefined", "not defined")) {
            mutations.addAll(generateImportMutations(code, errorMessage));
        }

        if (containsAny(errorLower, "expected ';'", "missing semicolon")) {
            mutations.addAll(generateSemicolonMutations(code, errorLine));
        }

        if (containsAny(errorLower, "indentation", "indent")) {
            mutations.addAll(generateIndentationMutations(code, errorLine));
        }

        if (containsAny(errorLower, "unexpected '}'", "unexpected ')'", "unexpected ']'")) {
            mutations.addAll(generateRemoveClosingMutations(code, errorLine));
        }

        if (mutations.isEmpty()) {
            return generateMutations(code);
        }

        return mutations;
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private List<String> generateClosingMutations(String code, int errorLine) {
        List<String> mutations = new ArrayList<>();

        mutations.add(code + "}");
        mutations.add(code + ")");
        mutations.add(code + "]");
        mutations.add(code + "}}");
        mutations.add(code + "))");

        if (errorLine > 0) {
            String[] lines = code.split("\n", -1);
            if (errorLine <= lines.length) {
                mutations.add(insertAtLineEnd(lines, errorLine - 1, "}"));
                mutations.add(insertAtLineEnd(lines, errorLine - 1, ")"));
                mutations.add(insertAtLineEnd(lines, errorLine - 1, ";"));
            }
        }

        return mutations;
    }

    private List<String> generateImportMutations(String code, String errorMessage) {
        List<String> mutations = new ArrayList<>();

        java.util.regex.Pattern symbolPattern = java.util.regex.Pattern.compile(
            "(?:cannot find symbol|undefined|not defined)[^'\"]*['\"]?(\\w+)['\"]?"
        );
        java.util.regex.Matcher m = symbolPattern.matcher(errorMessage);

        if (m.find()) {
            String symbol = m.group(1);

            if (symbol.equals("List") || symbol.equals("ArrayList")) {
                mutations.add("import java.util.List;\nimport java.util.ArrayList;\n" + code);
            }
            if (symbol.equals("Map") || symbol.equals("HashMap")) {
                mutations.add("import java.util.Map;\nimport java.util.HashMap;\n" + code);
            }
            if (symbol.equals("Set") || symbol.equals("HashSet")) {
                mutations.add("import java.util.Set;\nimport java.util.HashSet;\n" + code);
            }
            if (symbol.equals("Path") || symbol.equals("Files")) {
                mutations.add("import java.nio.file.*;\n" + code);
            }
        }

        if (!code.contains("import java.util")) {
            mutations.add("import java.util.*;\n" + code);
        }

        return mutations;
    }

    private List<String> generateSemicolonMutations(String code, int errorLine) {
        List<String> mutations = new ArrayList<>();

        if (errorLine > 0) {
            String[] lines = code.split("\n", -1);
            if (errorLine <= lines.length) {
                mutations.add(insertAtLineEnd(lines, errorLine - 1, ";"));
            }
            if (errorLine > 1) {
                mutations.add(insertAtLineEnd(lines, errorLine - 2, ";"));
            }
        }

        if (!code.trim().endsWith(";")) {
            mutations.add(code + ";");
        }

        return mutations;
    }

    private List<String> generateIndentationMutations(String code, int errorLine) {
        List<String> mutations = new ArrayList<>();

        if (errorLine > 0) {
            String[] lines = code.split("\n", -1);
            if (errorLine <= lines.length) {
                int lineIdx = errorLine - 1;
                String line = lines[lineIdx];

                lines[lineIdx] = "    " + line.trim();
                mutations.add(String.join("\n", lines));

                lines[lineIdx] = line.trim();
                mutations.add(String.join("\n", lines));
            }
        }

        return mutations;
    }

    private List<String> generateRemoveClosingMutations(String code, int errorLine) {
        List<String> mutations = new ArrayList<>();

        if (errorLine > 0) {
            String[] lines = code.split("\n", -1);
            if (errorLine <= lines.length) {
                String line = lines[errorLine - 1];

                if (line.contains("}")) {
                    lines[errorLine - 1] = removeLastOccurrence(line, "}");
                    mutations.add(String.join("\n", lines));
                }
                if (line.contains(")")) {
                    lines[errorLine - 1] = removeLastOccurrence(line, ")");
                    mutations.add(String.join("\n", lines));
                }
            }
        }

        return mutations;
    }

    private String insertAtLineEnd(String[] lines, int lineIndex, String toInsert) {
        String[] copy = lines.clone();
        copy[lineIndex] = copy[lineIndex] + toInsert;
        return String.join("\n", copy);
    }

    private String removeLastOccurrence(String str, String toRemove) {
        int lastIndex = str.lastIndexOf(toRemove);
        if (lastIndex == -1) return str;
        return str.substring(0, lastIndex) + str.substring(lastIndex + toRemove.length());
    }

    public String healErrorDriven(String brokenCode) {
        if (detailedValidator == null) {
            LOG.warning("[HEALER] healErrorDriven requiere DetailedSyntaxValidator. Usando heal() normal.");
            return heal(brokenCode);
        }

        Objects.requireNonNull(brokenCode, "brokenCode no puede ser null");

        String currentCode = brokenCode;
        int maxIterations = MAX_ATTEMPTS_EXPENSIVE;
        lastAttempts = 0;

        for (int i = 0; i < maxIterations; i++) {
            int errors = detailedValidator.countErrors(currentCode);

            if (errors == 0) {
                LOG.info("[HEALER] Error-Driven: Codigo curado en " + lastAttempts + " intentos.");
                lastHealSuccessful = true;
                return currentCode;
            }

            String errorMsg = detailedValidator.getErrorMessage(currentCode);
            List<String> mutations = generateTargetedMutations(currentCode, errorMsg);

            String bestMutation = null;
            int bestErrors = errors;

            for (String mutation : mutations) {
                lastAttempts++;
                int mutationErrors = detailedValidator.countErrors(mutation);

                if (mutationErrors < bestErrors) {
                    bestMutation = mutation;
                    bestErrors = mutationErrors;
                }

                if (mutationErrors == 0) {
                    LOG.info("[HEALER] Error-Driven: Codigo curado en " + lastAttempts + " intentos.");
                    lastHealSuccessful = true;
                    return mutation;
                }
            }

            if (bestMutation != null && bestErrors < errors) {
                currentCode = bestMutation;
            } else {
                break;
            }
        }

        LOG.warning("[HEALER] Error-Driven: No se pudo curar despues de " + lastAttempts + " intentos.");
        lastHealSuccessful = false;
        return brokenCode;
    }

    public int getLastAttempts() {
        return lastAttempts;
    }

    public boolean wasLastHealSuccessful() {
        return lastHealSuccessful;
    }

    private static class CodeNode implements Comparable<CodeNode> {
        final String code;
        final int cost;
        final int heuristic;

        CodeNode(String code, int cost, int heuristic) {
            this.code = code;
            this.cost = cost;
            this.heuristic = heuristic;
        }

        int getF() {
            return cost + heuristic;
        }

        @Override
        public int compareTo(CodeNode other) {
            return Integer.compare(this.getF(), other.getF());
        }
    }
}
