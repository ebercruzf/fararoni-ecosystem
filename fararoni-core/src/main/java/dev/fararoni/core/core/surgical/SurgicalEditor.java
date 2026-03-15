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

import dev.fararoni.core.core.config.AgentConfig;
import dev.fararoni.core.core.indexing.SentinelJavaParser;
import dev.fararoni.core.core.indexing.model.LineRange;
import com.github.javaparser.StaticJavaParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.4.0
 * @since 1.0.0
 */
public class SurgicalEditor {
    private final SentinelJavaParser astParser;
    private final OverlapDetector overlapDetector;
    private final BitapMatcher bitap;
    private final AgentConfig config;
    private final IndentationNormalizer indentNormalizer;

    public SurgicalEditor(SentinelJavaParser astParser) {
        this(AgentConfig.defaults(), astParser);
    }

    public SurgicalEditor(AgentConfig config, SentinelJavaParser astParser) {
        this.bitap = new BitapMatcher();
        this.astParser = astParser;
        this.config = config;
        this.overlapDetector = new OverlapDetector();
        this.indentNormalizer = new IndentationNormalizer();
    }

    public SurgicalEditor(AgentConfig config) {
        this(config, new SentinelJavaParser());
    }

    public SurgicalEditor(SentinelJavaParser astParser, AgentConfig config) {
        this(config, astParser);
    }

    public String applySurgery(String source, String search, String replace, LineRange scope) {
        String methodSource = source.substring(scope.startOffset(), scope.endOffset());

        int k = (int) (search.length() * config.fuzzyThreshold());
        int localIndex = bitap.find(methodSource, search, k);

        if (localIndex == -1) {
            throw new SurgicalException("Error: Bloque SEARCH no encontrado en el scope del metodo.");
        }

        int absoluteIndex = scope.startOffset() + localIndex;

        String normalizedReplace = indentNormalizer.alignIndentation(source, absoluteIndex, replace);

        return source.substring(0, absoluteIndex)
             + normalizedReplace
             + source.substring(absoluteIndex + search.length());
    }

    public String applySurgeryToMethod(String source, String search, String replace, String targetMethod) {
        Map<String, LineRange> ranges = astParser.extractMethodRanges(source);
        LineRange scope = ranges.get(targetMethod);

        if (scope == null) {
            throw new SurgicalException("Metodo no encontrado: " + targetMethod);
        }

        return applySurgery(source, search, replace, scope);
    }

    public SurgeryReport executeSurgery(String source, List<EditBlock> edits, String targetMethod) {
        try {
            overlapDetector.validate(edits);
        } catch (OverlapConflictException e) {
            throw e;
        }

        Map<String, LineRange> initialRanges = astParser.extractMethodRanges(source);
        LineRange methodScope = initialRanges.get(targetMethod);

        if (methodScope == null) throw new SurgicalException("Scope no encontrado.");

        edits.sort(Comparator.comparingInt(EditBlock::estimatedLine).reversed());

        StringBuilder buffer = new StringBuilder(source);
        List<ChangeLog> logs = new ArrayList<>();
        int accumulatedDelta = 0;

        for (EditBlock edit : edits) {
            int matchIdx = bitap.find(buffer.toString(), edit.search(), config.fuzzyThreshold());

            if (matchIdx != -1) {
                int originalLine = countLines(source, matchIdx);

                String normalizedReplacement = indentNormalizer.alignIndentation(
                    buffer.toString(), matchIdx, edit.replace());

                buffer.replace(matchIdx, matchIdx + edit.search().length(), normalizedReplacement);

                int currentDelta = normalizedReplacement.length() - edit.search().length();
                accumulatedDelta += currentDelta;
                int newLine = countLines(buffer.toString(), matchIdx);

                logs.add(new ChangeLog(edit.id(), originalLine, newLine, currentDelta));
            }
        }

        String finalResult = buffer.toString();
        try {
            StaticJavaParser.parse(finalResult);
        } catch (Exception e) {
            throw new SurgicalException("La cirugia corrompio la estructura Java. Rollback ejecutado.");
        }

        return new SurgeryReport(finalResult, logs, accumulatedDelta);
    }

    public SurgeryReport executeSurgery(String source, List<EditBlock> edits) {
        if (source == null || source.isBlank()) {
            throw new SurgicalException("Source code no puede ser null o vacio");
        }

        if (edits == null || edits.isEmpty()) {
            return SurgeryReport.noChanges(source);
        }

        overlapDetector.validate(edits);

        List<EditBlock> sortedEdits = new ArrayList<>(edits);
        sortedEdits.sort(Comparator.comparingInt(EditBlock::estimatedLine).reversed());

        StringBuilder buffer = new StringBuilder(source);
        List<ChangeLog> logs = new ArrayList<>();
        int accumulatedDelta = 0;

        for (EditBlock edit : sortedEdits) {
            int matchIdx = buffer.indexOf(edit.search());

            if (matchIdx != -1) {
                int originalLine = countLines(source, matchIdx);

                String normalizedReplacement = indentNormalizer.alignIndentation(
                    buffer.toString(), matchIdx, edit.replace());

                buffer.replace(matchIdx, matchIdx + edit.search().length(), normalizedReplacement);

                int currentDelta = normalizedReplacement.length() - edit.search().length();
                accumulatedDelta += currentDelta;
                int newLine = countLines(buffer.toString(), matchIdx);

                logs.add(new ChangeLog(edit.id(), originalLine, newLine, currentDelta));
            }
        }

        if (logs.isEmpty()) {
            return SurgeryReport.noChanges(source);
        }

        String finalResult = buffer.toString();
        validateFinalIntegrity(finalResult);

        return new SurgeryReport(finalResult, logs, accumulatedDelta);
    }

    private void validateFinalIntegrity(String code) {
        try {
            StaticJavaParser.parse(code);
        } catch (Exception e) {
            throw new SurgicalException("La cirugia fallo: El resultado no compila.");
        }
    }

    private int countLines(String text, int offset) {
        return (int) text.substring(0, offset).chars().filter(ch -> ch == '\n').count() + 1;
    }

    public String replaceMethodComplete(String originalSource, String targetMethod, String newCode) {
        Map<String, LineRange> ranges = astParser.extractMethodRanges(originalSource);
        LineRange methodRange = ranges.get(targetMethod);

        if (methodRange == null) {
            throw new SurgicalException("Metodo no encontrado para reemplazo: " + targetMethod);
        }

        StringBuilder buffer = new StringBuilder(originalSource);
        buffer.replace(methodRange.startOffset(), methodRange.endOffset(), newCode);

        String result = buffer.toString();
        validateFinalIntegrity(result);

        return result;
    }

    public String replaceRange(String originalSource, LineRange range, String newCode) {
        if (originalSource == null || originalSource.isBlank()) {
            throw new SurgicalException("Source code no puede ser null o vacio");
        }

        if (range == null) {
            throw new SurgicalException("LineRange no puede ser null");
        }

        if (newCode == null) {
            throw new SurgicalException("New code no puede ser null");
        }

        int startOffset = range.startOffset();
        int endOffset = range.endOffset();

        if (startOffset < 0 || endOffset < startOffset || endOffset > originalSource.length()) {
            throw new SurgicalException(String.format(
                "Offsets invalidos: start=%d, end=%d, sourceLength=%d",
                startOffset, endOffset, originalSource.length()
            ));
        }

        StringBuilder buffer = new StringBuilder(originalSource);
        buffer.replace(startOffset, endOffset, newCode);

        String result = buffer.toString();
        validateFinalIntegrity(result);

        return result;
    }
}
