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
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import dev.fararoni.core.core.analysis.AnalysisContext;

import java.nio.file.Path;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ParserFactory {
    public static ParserConfiguration createLightConfig() {
        return new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setAttributeComments(true);
    }

    public static ParserConfiguration createEnterpriseConfig(Path projectSourcePath) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
            new ReflectionTypeSolver(false),
            new JavaParserTypeSolver(projectSourcePath.toFile())
        );

        return new ParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(typeSolver))
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setAttributeComments(true);
    }

    public static ParserConfiguration createEnterpriseConfigReflectionOnly() {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
            new ReflectionTypeSolver(false)
        );

        return new ParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(typeSolver))
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
            .setAttributeComments(true);
    }

    public static AnalysisContext createContext(String filename, String content) {
        return createContext(filename, content, createLightConfig());
    }

    public static AnalysisContext createContext(String filename, String content, ParserConfiguration config) {
        if (content == null || content.isBlank()) {
            return AnalysisContext.invalid(filename);
        }

        if (filename == null || !filename.endsWith(".java")) {
            return AnalysisContext.invalid(filename);
        }

        try {
            JavaParser parser = new JavaParser(config);
            ParseResult<CompilationUnit> result = parser.parse(content);

            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                return AnalysisContext.invalid(filename);
            }

            CompilationUnit cu = result.getResult().get();

            LexicalPreservingPrinter.setup(cu);

            return AnalysisContext.valid(cu, filename);
        } catch (Exception e) {
            return AnalysisContext.invalid(filename);
        }
    }

    public static AnalysisContext createEnterpriseContext(
            String filename,
            String content,
            Path projectSourcePath) {
        return createContext(filename, content, createEnterpriseConfig(projectSourcePath));
    }

    public static JavaParser createParser(ParserConfiguration config) {
        return new JavaParser(config);
    }

    public static Optional<CompilationUnit> safeParse(String content, ParserConfiguration config) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        try {
            JavaParser parser = new JavaParser(config);
            ParseResult<CompilationUnit> result = parser.parse(content);

            if (result.isSuccessful() && result.getResult().isPresent()) {
                return result.getResult();
            }
        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

    public static Optional<String> extractMethodSource(AnalysisContext context, String targetMethod) {
        if (context == null || !context.isValid() || targetMethod == null) {
            return Optional.empty();
        }

        return context.compilationUnit().findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
            .stream()
            .filter(md -> matchesMethodSignature(md, targetMethod))
            .findFirst()
            .map(md -> LexicalPreservingPrinter.print(md));
    }

    private static boolean matchesMethodSignature(com.github.javaparser.ast.body.MethodDeclaration md, String signature) {
        String simpleName = md.getNameAsString();
        String params = md.getParameters().stream()
            .map(p -> p.getType().asString())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
        String fullSig = simpleName + "(" + params + ")";
        return fullSig.equals(signature) || simpleName.equals(signature.replace("()", ""));
    }

    public static boolean validateMethodSignatureMatch(String methodName, String newCode) {
        if (methodName == null || newCode == null || newCode.isBlank()) {
            return false;
        }

        try {
            String wrappedCode = "class Temp { " + newCode + " }";
            Optional<CompilationUnit> cu = safeParse(wrappedCode, createLightConfig());

            if (cu.isEmpty()) {
                return false;
            }

            return cu.get().findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
                .stream()
                .anyMatch(md -> {
                    String parsedName = md.getNameAsString();
                    String expectedName = extractSimpleName(methodName);
                    return parsedName.equals(expectedName);
                });
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractSimpleName(String signature) {
        if (signature == null) return "";
        int parenIndex = signature.indexOf('(');
        return parenIndex > 0 ? signature.substring(0, parenIndex) : signature;
    }
}
