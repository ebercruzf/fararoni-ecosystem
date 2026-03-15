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
package dev.fararoni.core.core.indexing;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.indexing.model.LineRange;
import dev.fararoni.core.core.indexing.model.SemanticUnit;
import dev.fararoni.core.core.services.AnalysisResult;
import dev.fararoni.core.core.services.CodeAnalysisService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class SentinelJavaParser {
    private final ParserConfiguration config;

    private final SentinelVisitor visitor;

    public SentinelJavaParser() {
        this(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    public SentinelJavaParser(ParserConfiguration.LanguageLevel languageLevel) {
        this.config = new ParserConfiguration()
            .setLanguageLevel(languageLevel != null ? languageLevel : ParserConfiguration.LanguageLevel.JAVA_21)
            .setAttributeComments(true);

        this.visitor = new SentinelVisitor();
    }

    public SentinelJavaParser(ParserConfiguration config) {
        this.config = config;
        this.visitor = new SentinelVisitor();
    }

    public static SentinelJavaParser forJava8() {
        return new SentinelJavaParser(ParserConfiguration.LanguageLevel.JAVA_8);
    }

    public static SentinelJavaParser forJava11() {
        return new SentinelJavaParser(ParserConfiguration.LanguageLevel.JAVA_11);
    }

    public static SentinelJavaParser forJava17() {
        return new SentinelJavaParser(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    public static SentinelJavaParser forJava21() {
        return new SentinelJavaParser(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    public ParserConfiguration.LanguageLevel getLanguageLevel() {
        return config.getLanguageLevel();
    }

    public List<SemanticUnit> parse(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return Collections.emptyList();
        }

        List<SemanticUnit> units = new ArrayList<>();

        try {
            JavaParser parser = new JavaParser(config);
            ParseResult<CompilationUnit> result = parser.parse(sourceCode);

            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                return Collections.emptyList();
            }

            CompilationUnit cu = result.getResult().get();

            extractImports(cu, units);

            visitor.reset();
            cu.accept(visitor, units);
        } catch (Exception e) {
            return Collections.emptyList();
        }

        return units;
    }

    private void extractImports(CompilationUnit cu, List<SemanticUnit> units) {
        List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);

        if (imports.isEmpty()) {
            return;
        }

        int startLine = imports.stream()
            .mapToInt(i -> i.getBegin().map(p -> p.line).orElse(0))
            .min()
            .orElse(1);

        int endLine = imports.stream()
            .mapToInt(i -> i.getEnd().map(p -> p.line).orElse(0))
            .max()
            .orElse(startLine);

        String importContent = imports.stream()
            .map(ImportDeclaration::toString)
            .collect(Collectors.joining());

        units.add(new SemanticUnit(
            SemanticUnit.TYPE_IMPORT,
            "Imports",
            importContent,
            startLine,
            endLine,
            Set.of(),
            false
        ));
    }

    public boolean isValidSyntax(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return false;
        }

        try {
            JavaParser parser = new JavaParser(config);
            ParseResult<CompilationUnit> result = parser.parse(sourceCode);
            return result.isSuccessful() && result.getResult().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    public ParserConfiguration getConfiguration() {
        return config;
    }

    public AnalysisContext createContext(String filename, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return AnalysisContext.invalid(filename);
        }

        try {
            JavaParser parser = new JavaParser(config);
            ParseResult<CompilationUnit> result = parser.parse(sourceCode);

            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                return AnalysisContext.invalid(filename);
            }

            CompilationUnit cu = result.getResult().get();

            LexicalPreservingPrinter.setup(cu);

            AnalysisContext ctx = AnalysisContext.valid(cu, filename);
            ctx.cacheResult("sourceCode", sourceCode);
            return ctx;
        } catch (Exception e) {
            return AnalysisContext.invalid(filename);
        }
    }

    public List<SemanticUnit> parse(AnalysisContext context) {
        if (context == null || !context.isValid()) {
            return Collections.emptyList();
        }

        List<SemanticUnit> units = new ArrayList<>();

        extractImports(context.compilationUnit(), units);

        visitor.reset();
        context.compilationUnit().accept(visitor, units);

        return units;
    }

    public Map<String, LineRange> extractMethodRanges(AnalysisContext context) {
        if (context == null || !context.isValid()) {
            return Collections.emptyMap();
        }

        Map<String, LineRange> methodMap = new HashMap<>();

        String sourceCode = context.getCached("sourceCode", String.class);
        int[] lineOffsets = calculateLineOffsets(sourceCode);

        context.compilationUnit().accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration md, Void arg) {
                super.visit(md, arg);

                md.getRange().ifPresent(range -> {
                    String signature = md.getSignature().asString();

                    int startLine = range.begin.line;
                    int endLine = range.end.line;
                    int startCol = md.getBegin().map(p -> p.column).orElse(1);
                    int endCol = md.getEnd().map(p -> p.column).orElse(1);

                    int startOffset = calculateOffset(lineOffsets, startLine, startCol);
                    int endOffset = calculateOffset(lineOffsets, endLine, endCol);

                    LineRange lr = new LineRange(
                        startLine,
                        endLine,
                        startCol,
                        endCol,
                        startOffset,
                        endOffset
                    );
                    methodMap.put(signature, lr);
                });
            }
        }, null);

        return methodMap;
    }

    private int[] calculateLineOffsets(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return new int[]{0, 0};
        }

        List<Integer> offsets = new ArrayList<>();
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

    public Map<String, LineRange> extractMethodRanges(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return Collections.emptyMap();
        }

        AnalysisContext context = createContext("temp.java", sourceCode);
        return extractMethodRanges(context);
    }

    public AnalysisResult parseStructure(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return AnalysisResult.failure("Source code is null or empty");
        }

        AnalysisContext context = createContext("temp.java", sourceCode);

        if (context.isValid()) {
            Map<String, LineRange> ranges = extractMethodRanges(context);
            return AnalysisResult.success(ranges);
        }

        Map<String, LineRange> fallbackRanges = extractMethodRangesWithRegex(sourceCode);

        if (!fallbackRanges.isEmpty()) {
            return AnalysisResult.fallback(fallbackRanges);
        }

        return AnalysisResult.failure(
            "AST parsing failed and Regex heuristic found no methods"
        );
    }

    private Map<String, LineRange> extractMethodRangesWithRegex(String sourceCode) {
        Map<String, LineRange> methodMap = new HashMap<>();

        java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile(
            "^\\s*((?:public|private|protected|static|final|abstract|synchronized|native|strictfp)\\s+)*" +
            "(\\w+(?:<[^>]+>)?(?:\\[\\])*)" +
            "\\s+(\\w+)" +
            "\\s*\\(([^)]*)\\)" +
            "\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{",
            java.util.regex.Pattern.MULTILINE
        );

        String[] lines = sourceCode.split("\n");
        int[] lineOffsets = calculateLineOffsets(sourceCode);

        java.util.regex.Matcher matcher = methodPattern.matcher(sourceCode);

        while (matcher.find()) {
            String methodName = matcher.group(3);
            String params = matcher.group(4);

            int startOffset = matcher.start();
            int startLine = findLineNumber(lineOffsets, startOffset);

            int endLine = findMethodEndLine(lines, startLine);

            String signature = methodName + "(" + normalizeParams(params) + ")";

            LineRange lr = new LineRange(
                startLine,
                endLine,
                1,
                1,
                startOffset,
                0
            );

            methodMap.put(signature, lr);
        }

        return methodMap;
    }

    private int findLineNumber(int[] lineOffsets, int offset) {
        for (int i = lineOffsets.length - 1; i >= 1; i--) {
            if (offset >= lineOffsets[i]) {
                return i;
            }
        }
        return 1;
    }

    private int findMethodEndLine(String[] lines, int startLine) {
        int braceCount = 0;
        boolean foundOpen = false;

        for (int i = startLine - 1; i < lines.length; i++) {
            String line = lines[i];
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    foundOpen = true;
                } else if (c == '}') {
                    braceCount--;
                    if (foundOpen && braceCount == 0) {
                        return i + 1;
                    }
                }
            }
        }

        return Math.min(startLine + 20, lines.length);
    }

    private String normalizeParams(String params) {
        if (params == null || params.isBlank()) {
            return "";
        }

        StringBuilder normalized = new StringBuilder();
        String[] parts = params.split(",");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            String[] tokens = part.split("\\s+");
            if (tokens.length >= 2) {
                if (i > 0) normalized.append(", ");
                normalized.append(tokens[tokens.length - 2]);
            } else if (tokens.length == 1 && !tokens[0].isEmpty()) {
                if (i > 0) normalized.append(", ");
                normalized.append(tokens[0]);
            }
        }

        return normalized.toString();
    }

    public Optional<String> extractMethodSource(AnalysisContext context, String targetMethod) {
        if (context == null || !context.isValid() || targetMethod == null) {
            return Optional.empty();
        }

        return context.compilationUnit().findAll(MethodDeclaration.class)
            .stream()
            .filter(md -> matchesMethodSignature(md, targetMethod))
            .findFirst()
            .map(md -> LexicalPreservingPrinter.print(md));
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

    public boolean validateMethodSignatureMatch(String targetMethod, String newMethodCode, AnalysisContext context) {
        if (targetMethod == null || newMethodCode == null || newMethodCode.isBlank()) {
            return false;
        }

        try {
            String wrappedCode = "class Temp { " + newMethodCode + " }";
            JavaParser parser = new JavaParser(config);
            ParseResult<CompilationUnit> result = parser.parse(wrappedCode);

            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                return false;
            }

            CompilationUnit newCu = result.getResult().get();

            Optional<MethodDeclaration> newMethod = newCu.findAll(MethodDeclaration.class)
                .stream()
                .findFirst();

            if (newMethod.isEmpty()) {
                return false;
            }

            Optional<MethodDeclaration> originalMethod = context.compilationUnit()
                .findAll(MethodDeclaration.class)
                .stream()
                .filter(md -> matchesMethodSignature(md, targetMethod))
                .findFirst();

            if (originalMethod.isEmpty()) {
                return false;
            }

            MethodDeclaration orig = originalMethod.get();
            MethodDeclaration repl = newMethod.get();

            return orig.getNameAsString().equals(repl.getNameAsString())
                && orig.getType().asString().equals(repl.getType().asString())
                && orig.getParameters().size() == repl.getParameters().size();
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<LineRange> getMethodRange(AnalysisContext context, String targetMethod) {
        Map<String, LineRange> ranges = extractMethodRanges(context);

        if (ranges.containsKey(targetMethod)) {
            return Optional.of(ranges.get(targetMethod));
        }

        String simpleName = targetMethod.contains("(")
            ? targetMethod.substring(0, targetMethod.indexOf("("))
            : targetMethod;

        return ranges.entrySet().stream()
            .filter(e -> e.getKey().startsWith(simpleName + "("))
            .map(Map.Entry::getValue)
            .findFirst();
    }
}
