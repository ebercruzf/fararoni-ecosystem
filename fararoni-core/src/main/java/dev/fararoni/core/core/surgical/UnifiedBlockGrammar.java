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
package dev.fararoni.core.core.surgical;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class UnifiedBlockGrammar {
    private UnifiedBlockGrammar() {
    }

    public static final String SEARCH_START = "<<<<<<< SEARCH";
    public static final String SEPARATOR = "=======";
    public static final String REPLACE_END = ">>>>>>> REPLACE";
    public static final String NO_CHANGES = "NO_CHANGES_REQUIRED";

    public static final String GBNF_GRAMMAR = """
        root          ::= (unified_block | no_changes)
        unified_block ::= "<<<<<<< SEARCH\\n" code_block "=======\\n" code_block ">>>>>>> REPLACE\\n"
        no_changes    ::= "NO_CHANGES_REQUIRED"
        code_block    ::= [^\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F]*
        """;

    public static final String PYTHON_GBNF_GRAMMAR =
            "root ::= (block)+\n" +
                    "block ::= search_section replace_section\n" +
                    "search_section ::= \"" + SEARCH_START + "\\n\" content \"" + SEPARATOR + "\\n\"\n" +
                    "replace_section ::= content \"" + REPLACE_END + "\\n\"\n" +
                    "content ::= (line)+\n" +
                    "line ::= [^\\n]* \"\\n\"";

    public static String getGrammarForLanguage(String fileExtension) {
        if (fileExtension == null || fileExtension.isBlank()) {
            return GBNF_GRAMMAR;
        }

        String ext = fileExtension.toLowerCase().trim();
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }

        int lastDot = ext.lastIndexOf('.');
        if (lastDot >= 0) {
            ext = ext.substring(lastDot + 1);
        }

        return switch (ext) {
            case "py", "pyw", "pyi" -> PYTHON_GBNF_GRAMMAR;
            case "java", "js", "ts", "jsx", "tsx", "c", "cpp", "h", "hpp",
                 "cs", "go", "rs", "swift", "kt", "scala", "php" -> GBNF_GRAMMAR;
            default -> GBNF_GRAMMAR;
        };
    }

    public static boolean isIndentationSensitive(String fileExtension) {
        if (fileExtension == null) return false;

        String ext = fileExtension.toLowerCase().replace(".", "");
        return switch (ext) {
            case "py", "pyw", "pyi", "yaml", "yml", "haml", "slim", "pug" -> true;
            default -> false;
        };
    }

    public enum IndentationStyle {
        TABS,
        SPACES_2,
        SPACES_4,
        SPACES_8,
        UNKNOWN
    }

    public static IndentationStyle detectIndentation(String fileContent) {
        if (fileContent == null || fileContent.isBlank()) {
            return IndentationStyle.UNKNOWN;
        }

        int tabCount = 0;
        int space2Count = 0;
        int space4Count = 0;
        int space8Count = 0;

        for (String line : fileContent.lines().toList()) {
            if (line.isEmpty() || !Character.isWhitespace(line.charAt(0))) {
                continue;
            }

            if (line.startsWith("\t")) {
                tabCount++;
            } else if (line.startsWith("        ")) {
                space8Count++;
            } else if (line.startsWith("    ")) {
                space4Count++;
            } else if (line.startsWith("  ")) {
                space2Count++;
            }

            int total = tabCount + space2Count + space4Count + space8Count;
            if (total >= 20) {
                break;
            }
        }

        if (tabCount > space2Count && tabCount > space4Count && tabCount > space8Count) {
            return IndentationStyle.TABS;
        }
        if (space8Count > tabCount && space8Count > space2Count && space8Count > space4Count) {
            return IndentationStyle.SPACES_8;
        }
        if (space4Count > tabCount && space4Count > space2Count) {
            return IndentationStyle.SPACES_4;
        }
        if (space2Count > tabCount) {
            return IndentationStyle.SPACES_2;
        }
        if (tabCount > 0) {
            return IndentationStyle.TABS;
        }

        return IndentationStyle.UNKNOWN;
    }

    public static String normalizeIndentation(String code, IndentationStyle targetStyle) {
        if (code == null || code.isBlank() || targetStyle == IndentationStyle.UNKNOWN) {
            return code;
        }

        StringBuilder result = new StringBuilder();
        final int TAB_WIDTH_CALC = 4;

        for (String line : code.lines().toList()) {
            if (line.isBlank()) {
                result.append("\n");
                continue;
            }

            int currentVisualWidth = 0;
            int i = 0;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == ' ') {
                    currentVisualWidth += 1;
                } else if (c == '\t') {
                    currentVisualWidth += TAB_WIDTH_CALC;
                } else {
                    break;
                }
                i++;
            }

            String content = line.substring(i);

            if (currentVisualWidth == 0) {
                result.append(content).append("\n");
                continue;
            }

            String newIndent;
            switch (targetStyle) {
                case TABS:
                    int tabs = currentVisualWidth / TAB_WIDTH_CALC;
                    int spacesRemainder = currentVisualWidth % TAB_WIDTH_CALC;
                    newIndent = "\t".repeat(tabs) + " ".repeat(spacesRemainder);
                    break;
                case SPACES_2:
                case SPACES_4:
                case SPACES_8:
                    newIndent = " ".repeat(currentVisualWidth);
                    break;
                default:
                    newIndent = " ".repeat(currentVisualWidth);
            }

            result.append(newIndent).append(content).append("\n");
        }

        String normalized = result.toString();
        if (!code.endsWith("\n") && normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static final Pattern UNIFIED_BLOCK_PATTERN = Pattern.compile(
        "<<<<<<< SEARCH\\s*\\n(.*?)=======\\s*\\n(.*?)>>>>>>> REPLACE",
        Pattern.DOTALL
    );

    public static Optional<EditBlock> parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            throw new UnifiedBlockParseException("Output del LLM esta vacio");
        }

        String trimmed = llmOutput.trim();

        if (trimmed.equals(NO_CHANGES) || trimmed.contains(NO_CHANGES)) {
            return Optional.empty();
        }

        try {
            return parseTolerant(trimmed);
        } catch (Exception e) {
            return parseWithRegex(trimmed);
        }
    }

    private static Optional<EditBlock> parseTolerant(String input) {
        java.util.List<String> lines = input.lines().toList();

        StringBuilder searchBlock = new StringBuilder();
        StringBuilder replaceBlock = new StringBuilder();

        boolean inSearch = false;
        boolean inReplace = false;
        boolean foundSearch = false;
        boolean foundSeparator = false;
        boolean foundReplace = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("<<<<<") && trimmedLine.toUpperCase().contains("SEARCH")) {
                inSearch = true;
                inReplace = false;
                foundSearch = true;
                continue;
            }

            if (trimmedLine.startsWith("=====") || trimmedLine.equals(SEPARATOR)) {
                inSearch = false;
                inReplace = true;
                foundSeparator = true;
                continue;
            }

            if (trimmedLine.startsWith(">>>>>") && trimmedLine.toUpperCase().contains("REPLACE")) {
                inReplace = false;
                foundReplace = true;
                continue;
            }

            if (inSearch) {
                if (searchBlock.length() > 0) searchBlock.append("\n");
                searchBlock.append(line);
            } else if (inReplace) {
                if (replaceBlock.length() > 0) replaceBlock.append("\n");
                replaceBlock.append(line);
            }
        }

        if (!foundSearch || !foundSeparator || !foundReplace) {
            throw new UnifiedBlockParseException(
                "Delimitadores incompletos. Encontrado: SEARCH=" + foundSearch +
                ", SEPARATOR=" + foundSeparator + ", REPLACE=" + foundReplace
            );
        }

        String search = searchBlock.toString().trim();
        String replace = replaceBlock.toString().trim();

        if (search.isEmpty()) {
            throw new UnifiedBlockParseException("Bloque SEARCH esta vacio");
        }

        return Optional.of(new EditBlock(
            "unified-" + System.currentTimeMillis(),
            search,
            replace,
            1,
            0,
            0
        ));
    }

    private static Optional<EditBlock> parseWithRegex(String input) {
        Matcher matcher = UNIFIED_BLOCK_PATTERN.matcher(input);
        if (!matcher.find()) {
            throw new UnifiedBlockParseException(
                "Formato invalido. Se esperaba <<<<<<< SEARCH ... ======= ... >>>>>>> REPLACE"
            );
        }

        String searchBlock = matcher.group(1).trim();
        String replaceBlock = matcher.group(2).trim();

        if (searchBlock.isEmpty()) {
            throw new UnifiedBlockParseException("Bloque SEARCH esta vacio");
        }

        return Optional.of(new EditBlock(
            "unified-" + System.currentTimeMillis(),
            searchBlock,
            replaceBlock,
            1,
            0,
            0
        ));
    }

    public static boolean isValid(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return false;
        }

        String trimmed = llmOutput.trim();

        if (trimmed.equals(NO_CHANGES) || trimmed.contains(NO_CHANGES)) {
            return true;
        }

        return UNIFIED_BLOCK_PATTERN.matcher(trimmed).find();
    }

    public static String format(EditBlock block) {
        if (block == null) {
            return NO_CHANGES;
        }

        return String.format(
            "%s\n%s\n%s\n%s\n%s\n",
            SEARCH_START,
            block.search(),
            SEPARATOR,
            block.replace(),
            REPLACE_END
        );
    }

    public static String formatMultiple(java.util.List<EditBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return NO_CHANGES;
        }

        StringBuilder sb = new StringBuilder();
        for (EditBlock block : blocks) {
            sb.append(format(block)).append("\n");
        }
        return sb.toString().trim();
    }

    public static class UnifiedBlockParseException extends RuntimeException {
        public UnifiedBlockParseException(String message) {
            super(message);
        }

        public UnifiedBlockParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
