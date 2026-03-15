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
package dev.fararoni.core.core.analysis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.fararoni.core.core.indexing.ParserFactory;
import dev.fararoni.core.core.indexing.SentinelVisitor;
import dev.fararoni.core.core.indexing.model.LineRange;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record AnalysisContext(
    CompilationUnit compilationUnit,
    String filename,
    long timestamp,
    Map<String, Object> metadata,
    boolean syntacticallyValid
) {
    public AnalysisContext {
        if (metadata == null) {
            metadata = new ConcurrentHashMap<>();
        } else {
            metadata = new ConcurrentHashMap<>(metadata);
        }
    }

    public static AnalysisContext valid(CompilationUnit cu, String filename) {
        return new AnalysisContext(
            cu,
            filename,
            System.currentTimeMillis(),
            new ConcurrentHashMap<>(),
            true
        );
    }

    public static AnalysisContext invalid(String filename) {
        return new AnalysisContext(
            null,
            filename,
            System.currentTimeMillis(),
            new ConcurrentHashMap<>(),
            false
        );
    }

    public boolean isMethodOverride(MethodDeclaration md) {
        if (md == null || !syntacticallyValid) {
            return false;
        }

        if (md.isAnnotationPresent(Override.class)) {
            return true;
        }

        try {
            var resolved = md.resolve();
            return resolved != null && md.getParentNode()
                .filter(p -> p instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration)
                .map(p -> (com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) p)
                .map(c -> !c.getExtendedTypes().isEmpty() || !c.getImplementedTypes().isEmpty())
                .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    public void cacheResult(String key, Object value) {
        metadata.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getCached(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public boolean isValid() {
        return syntacticallyValid && compilationUnit != null;
    }

    public boolean isJavaFile() {
        return filename != null && filename.endsWith(".java");
    }

    public LineRange getMethodRange(String targetMethod) {
        if (!isValid() || targetMethod == null) {
            return null;
        }

        return compilationUnit.findAll(MethodDeclaration.class)
            .stream()
            .filter(md -> matchesMethodSignature(md, targetMethod))
            .findFirst()
            .flatMap(md -> md.getRange().map(range -> {
                int startLine = range.begin.line;
                int endLine = range.end.line;
                int startCol = md.getBegin().map(p -> p.column).orElse(1);
                int endCol = md.getEnd().map(p -> p.column).orElse(1);

                String sourceCode = getCached("sourceCode", String.class);
                int startOffset = 0;
                int endOffset = 0;

                if (sourceCode != null) {
                    int[] lineOffsets = calculateLineOffsets(sourceCode);
                    startOffset = calculateOffset(lineOffsets, startLine, startCol);
                    endOffset = calculateOffset(lineOffsets, endLine, endCol);
                }

                return new LineRange(startLine, endLine, startCol, endCol, startOffset, endOffset);
            }))
            .orElse(null);
    }

    private boolean matchesMethodSignature(MethodDeclaration md, String signature) {
        String simpleName = md.getNameAsString();
        String params = md.getParameters().stream()
            .map(p -> p.getType().asString())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
        String fullSig = simpleName + "(" + params + ")";
        return fullSig.equals(signature) || simpleName.equals(signature.replace("()", ""));
    }

    private int[] calculateLineOffsets(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return new int[]{0, 0};
        }

        java.util.List<Integer> offsets = new java.util.ArrayList<>();
        offsets.add(0);
        offsets.add(0);

        for (int i = 0; i < sourceCode.length(); i++) {
            if (sourceCode.charAt(i) == '\n') {
                offsets.add(i + 1);
            }
        }

        return offsets.stream().mapToInt(Integer::intValue).toArray();
    }

    private int calculateOffset(int[] lineOffsets, int line, int column) {
        if (lineOffsets == null || line < 1 || line >= lineOffsets.length) {
            return 0;
        }
        return lineOffsets[line] + (column - 1);
    }
}
