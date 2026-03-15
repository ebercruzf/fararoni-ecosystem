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
package dev.fararoni.core.core.util;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CodeSanitizer {
    private static final Logger LOG = Logger.getLogger(CodeSanitizer.class.getName());

    private CodeSanitizer() {
    }

    public static String sanitize(String codigo, String language) {
        if (codigo == null) return "";
        String limpio = codigo.trim();

        limpio = removeMarkdownBlocks(limpio);

        limpio = removeConversationalPreamble(limpio, language);

        if (usesBraces(language)) {
            limpio = autoCompleteBraces(limpio);
        }

        limpio = cleanProblematicCharacters(limpio);

        return limpio.trim();
    }

    public static String sanitize(String codigo) {
        return sanitize(codigo, detectLanguage(codigo));
    }

    private static String removeMarkdownBlocks(String text) {
        if (text == null) return "";
        String result = text.trim();

        if (result.startsWith("```")) {
            int firstNewline = result.indexOf("\n");
            if (firstNewline != -1) {
                result = result.substring(firstNewline + 1);
            } else {
                result = result.replace("```", "");
            }
        }

        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }

        return result.trim();
    }

    private static String removeConversationalPreamble(String text, String language) {
        if (text == null || text.isBlank()) return "";
        String result = text.trim();

        String[] javaStarts = {"package", "import", "public", "class", "interface", "enum", "@", "/*", "//"};
        String[] pythonStarts = {"def", "class", "import", "from", "#", "if", "for", "while", "try", "with"};
        String[] jsStarts = {"import", "export", "const", "let", "var", "function", "class", "//", "/*"};

        String[] starts = switch (language.toLowerCase()) {
            case "java" -> javaStarts;
            case "python", "py" -> pythonStarts;
            case "javascript", "js", "typescript", "ts" -> jsStarts;
            default -> javaStarts;
        };

        boolean startsWithCode = false;
        for (String start : starts) {
            if (result.startsWith(start)) {
                startsWithCode = true;
                break;
            }
        }

        if (!startsWithCode) {
            int earliestIndex = result.length();
            for (String start : starts) {
                int idx = result.indexOf("\n" + start);
                if (idx != -1 && idx < earliestIndex) {
                    earliestIndex = idx + 1;
                }
                idx = result.indexOf(start);
                if (idx != -1 && idx < earliestIndex) {
                    if (idx == 0 || result.charAt(idx - 1) == '\n') {
                        earliestIndex = idx;
                    }
                }
            }

            if (earliestIndex < result.length()) {
                String removed = result.substring(0, earliestIndex).trim();
                if (!removed.isEmpty()) {
                    LOG.fine(() -> "[CodeSanitizer] Removiendo preámbulo conversacional: " +
                        removed.substring(0, Math.min(50, removed.length())));
                }
                result = result.substring(earliestIndex);
            }
        }

        return result.trim();
    }

    private static String autoCompleteBraces(String text) {
        if (text == null || text.isBlank()) return "";

        long openBraces = text.chars().filter(ch -> ch == '{').count();
        long closeBraces = text.chars().filter(ch -> ch == '}').count();

        StringBuilder result = new StringBuilder(text);

        while (closeBraces < openBraces) {
            result.append("\n}");
            closeBraces++;
            LOG.info(() -> "[CodeSanitizer] Auto-completando llave '}' faltante.");
        }

        if (closeBraces > openBraces) {
            final long extraBraces = closeBraces - openBraces;
            LOG.warning(() -> "[CodeSanitizer] Detectadas " + extraBraces +
                " llaves de cierre extra. AST Guard validará.");
        }

        return result.toString();
    }

    private static String cleanProblematicCharacters(String text) {
        if (text == null) return "";

        return text
            .replace("\u00BF", "")
            .replace("\u00A1", "")
            .replace("\u00AB", "\"")
            .replace("\u00BB", "\"")
            .replace("\u2018", "'")
            .replace("\u2019", "'")
            .replace("\u201C", "\"")
            .replace("\u201D", "\"")
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2026", "...");
    }

    private static String detectLanguage(String code) {
        if (code == null) return "java";
        String lower = code.toLowerCase();

        if (lower.contains("def ") || lower.contains("import ") && lower.contains(":")) {
            return "python";
        }
        if (lower.contains("const ") || lower.contains("let ") || lower.contains("=>")) {
            return "javascript";
        }
        if (lower.contains("package ") || lower.contains("public class")) {
            return "java";
        }

        return "java";
    }

    private static boolean usesBraces(String language) {
        return switch (language.toLowerCase()) {
            case "java", "javascript", "js", "typescript", "ts", "c", "cpp", "csharp", "go", "rust" -> true;
            case "python", "py", "ruby", "yaml" -> false;
            default -> true;
        };
    }
}
