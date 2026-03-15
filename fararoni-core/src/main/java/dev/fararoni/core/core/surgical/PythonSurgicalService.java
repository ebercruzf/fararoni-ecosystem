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

import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterPython;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class PythonSurgicalService {
    private final IndentationNormalizer indentNormalizer;
    private final BitapMatcher bitap;

    public PythonSurgicalService() {
        this(new IndentationNormalizer(), new BitapMatcher());
    }

    public PythonSurgicalService(IndentationNormalizer indentNormalizer, BitapMatcher bitap) {
        this.indentNormalizer = indentNormalizer;
        this.bitap = bitap;
    }

    public PythonSurgeryResult executeSurgery(String source, List<EditBlock> edits) {
        if (source == null || source.isBlank()) throw new PythonSurgicalException("Source code vacío");
        if (edits == null || edits.isEmpty()) return PythonSurgeryResult.noChanges(source);

        UnifiedBlockGrammar.IndentationStyle originalStyle = UnifiedBlockGrammar.detectIndentation(source);

        List<ResolvedEdit> plannedEdits = resolveEdits(source, edits, originalStyle);

        validateNoOverlaps(plannedEdits);

        plannedEdits.sort(Comparator.comparingInt(ResolvedEdit::startIndex).reversed());

        StringBuilder buffer = new StringBuilder(source);
        List<ChangeLog> logs = new ArrayList<>();
        int totalDelta = 0;

        for (ResolvedEdit plan : plannedEdits) {
            buffer.replace(plan.startIndex(), plan.endIndex(), plan.replacementContent());

            logs.add(new ChangeLog(
                    plan.originalBlock().id(),
                    plan.originalLine(),
                    countLines(buffer.toString(), plan.startIndex()),
                    plan.replacementContent().length() - (plan.endIndex() - plan.startIndex())
            ));

            totalDelta += (plan.replacementContent().length() - (plan.endIndex() - plan.startIndex()));
        }

        String finalResult = buffer.toString();

        try (var validator = new TreeSitterResourceScope()) {
            PythonSyntaxValidation validation = validator.validate(finalResult);

            if (!validation.isValid()) {
                throw new PythonSurgicalException(
                        "ROLLBACK: La cirugía generó sintaxis inválida. " + validation.getErrorMessage());
            }

            return new PythonSurgeryResult(finalResult, logs, totalDelta, originalStyle, validation);
        }
    }

    public PythonSurgeryResult applySurgeryFromLlmOutput(String source, String llmOutput) {
        Optional<EditBlock> maybeEdit = UnifiedBlockGrammar.parse(llmOutput);
        if (maybeEdit.isEmpty()) {
            return PythonSurgeryResult.noChanges(source);
        }
        return executeSurgery(source, List.of(maybeEdit.get()));
    }

    private List<ResolvedEdit> resolveEdits(String source, List<EditBlock> edits, UnifiedBlockGrammar.IndentationStyle style) {
        List<ResolvedEdit> resolved = new ArrayList<>();

        for (EditBlock edit : edits) {
            String normalizedReplace = UnifiedBlockGrammar.normalizeIndentation(edit.replace(), style);
            MatchResult match = findRobustMatch(source, edit.search(), edit.estimatedLine());

            if (!match.found()) {
                throw new PythonSurgicalException("No se encontró el bloque SEARCH: " + truncate(edit.search(), 40));
            }

            String alignedReplace = indentNormalizer.alignIndentation(
                    source, match.startIndex(), normalizedReplace);

            resolved.add(new ResolvedEdit(edit, match.startIndex(), match.endIndex(), alignedReplace, countLines(source, match.startIndex())));
        }
        return resolved;
    }

    private MatchResult findRobustMatch(String source, String searchBlock, int estimatedLine) {
        List<Integer> allOccurrences = findAllOccurrences(source, searchBlock);

        if (allOccurrences.isEmpty()) {
            int fuzzyIdx = bitap.find(source, searchBlock, (int)(searchBlock.length() * 0.15));
            return fuzzyIdx != -1 ? new MatchResult(true, fuzzyIdx, fuzzyIdx + searchBlock.length()) : new MatchResult(false, -1, -1);
        }

        if (allOccurrences.size() == 1) {
            int start = allOccurrences.get(0);
            return new MatchResult(true, start, start + searchBlock.length());
        }

        int estimatedIndex = estimateIndexFromLine(source, estimatedLine);
        int bestStart = allOccurrences.stream()
                .min(Comparator.comparingInt(idx -> Math.abs(idx - estimatedIndex)))
                .orElse(allOccurrences.get(0));

        return new MatchResult(true, bestStart, bestStart + searchBlock.length());
    }

    private List<Integer> findAllOccurrences(String text, String search) {
        List<Integer> indexes = new ArrayList<>();
        int index = 0;
        while (true) {
            index = text.indexOf(search, index);
            if (index == -1) break;
            indexes.add(index);
            index += search.length();
        }
        return indexes;
    }

    private int estimateIndexFromLine(String source, int line) {
        if (line <= 1) return 0;
        int currentLine = 1;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                currentLine++;
                if (currentLine == line) return i + 1;
            }
        }
        return source.length();
    }

    private void validateNoOverlaps(List<ResolvedEdit> edits) {
        edits.sort(Comparator.comparingInt(ResolvedEdit::startIndex));
        for (int i = 0; i < edits.size() - 1; i++) {
            ResolvedEdit current = edits.get(i);
            ResolvedEdit next = edits.get(i + 1);
            if (current.endIndex() > next.startIndex()) {
                throw new PythonSurgicalException(
                        String.format("Conflicto de edición: Bloque en línea %d se solapa con bloque en línea %d.",
                                current.originalLine(), next.originalLine()));
            }
        }
    }

    public PythonSyntaxValidation validatePythonSyntax(String pythonCode) {
        if (pythonCode == null || pythonCode.isBlank()) {
            return PythonSyntaxValidation.invalid("Codigo vacio o null");
        }
        try (var validator = new TreeSitterResourceScope()) {
            return validator.validate(pythonCode);
        } catch (Exception e) {
            return PythonSyntaxValidation.invalid("Excepcion validando sintaxis: " + e.getMessage());
        }
    }

    private static class TreeSitterResourceScope implements AutoCloseable {
        private static final ThreadLocal<TSParser> THREAD_LOCAL_PARSER = ThreadLocal.withInitial(() -> {
            TSParser p = new TSParser();
            p.setLanguage(new TreeSitterPython());
            return p;
        });

        public TreeSitterResourceScope() {
        }

        public PythonSyntaxValidation validate(String code) {
            TSParser parser = THREAD_LOCAL_PARSER.get();
            parser.reset();

            TSTree tree = null;
            try {
                tree = parser.parseString(null, code);
                if (tree == null) return PythonSyntaxValidation.invalid("Parser error: Tree is null");

                TSNode root = tree.getRootNode();
                if (root.hasError()) {
                    return PythonSyntaxValidation.invalid(findFirstError(root, code));
                }
                return PythonSyntaxValidation.valid(root.getChildCount());
            } catch (Exception e) {
                return PythonSyntaxValidation.invalid("Error JNI: " + e.getMessage());
            } finally {
            }
        }

        private String findFirstError(TSNode node, String source) {
            if ("ERROR".equals(node.getType())) {
                int line = node.getStartPoint().getRow() + 1;
                int col = node.getStartPoint().getColumn() + 1;
                return String.format("Error de sintaxis en línea %d, columna %d", line, col);
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                String err = findFirstError(node.getChild(i), source);
                if (err != null) return err;
            }
            return null;
        }

        @Override
        public void close() {
        }
    }

    public record ChangeLog(
            String blockId,
            int originalLine,
            int appliedLine,
            int deltaSize
    ) {}

    private record ResolvedEdit(
            EditBlock originalBlock,
            int startIndex,
            int endIndex,
            String replacementContent,
            int originalLine
    ) {}

    private record MatchResult(boolean found, int startIndex, int endIndex) {}

    private int countLines(String text, int offset) {
        if (offset > text.length()) offset = text.length();
        return (int) text.substring(0, offset).chars().filter(ch -> ch == '\n').count() + 1;
    }

    private String truncate(String str, int maxLen) {
        return (str != null && str.length() > maxLen) ? str.substring(0, maxLen - 3) + "..." : str;
    }

    public record PythonSurgeryResult(
            String content,
            List<ChangeLog> logs,
            int totalDelta,
            UnifiedBlockGrammar.IndentationStyle detectedStyle,
            PythonSyntaxValidation syntaxValidation
    ) {
        public static PythonSurgeryResult noChanges(String source) {
            return new PythonSurgeryResult(source, List.of(), 0, UnifiedBlockGrammar.detectIndentation(source), PythonSyntaxValidation.valid(0));
        }
    }

    public record PythonSyntaxValidation(boolean isValid, String errorMessage, int nodeCount) {
        public static PythonSyntaxValidation valid(int nodeCount) { return new PythonSyntaxValidation(true, null, nodeCount); }
        public static PythonSyntaxValidation invalid(String error) { return new PythonSyntaxValidation(false, error, 0); }
        public String getErrorMessage() { return errorMessage; }
    }

    public static class PythonSurgicalException extends RuntimeException {
        public PythonSurgicalException(String message) { super(message); }
    }
}
