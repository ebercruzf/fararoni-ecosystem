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
package dev.fararoni.core.core.hybrid;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.indexing.ParserFactory;

import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 2.1.0
 * @since 1.0.0
 */
public final class IntentAwarePruner {
    private static final IntentAwarePruner INSTANCE = new IntentAwarePruner();

    private IntentAwarePruner() {
    }

    public static IntentAwarePruner getInstance() {
        return INSTANCE;
    }

    public String applyFocalPoint(AnalysisContext context, String targetMethodName) {
        Objects.requireNonNull(context, "context no puede ser null");
        Objects.requireNonNull(targetMethodName, "targetMethodName no puede ser null");

        if (!context.isValid()) {
            throw ContextPruningException.parsingFailed(
                new IllegalStateException("AnalysisContext inválido para: " + context.filename())
            );
        }

        try {
            CompilationUnit cu = context.compilationUnit();
            boolean methodFound = false;

            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (method.getNameAsString().equals(targetMethodName)) {
                    methodFound = true;
                } else if (shouldPreserveMethod(method, context)) {
                } else {
                    method.setBody(null);
                }
            }

            cu.getAllContainedComments().forEach(Comment::remove);

            if (!methodFound) {
                throw ContextPruningException.methodNotFound(targetMethodName);
            }

            return cu.toString();
        } catch (ContextPruningException e) {
            throw e;
        } catch (Exception e) {
            throw ContextPruningException.parsingFailed(e);
        }
    }

    public String extractSkeleton(AnalysisContext context) {
        Objects.requireNonNull(context, "context no puede ser null");

        if (!context.isValid()) {
            throw ContextPruningException.parsingFailed(
                new IllegalStateException("AnalysisContext inválido para: " + context.filename())
            );
        }

        try {
            CompilationUnit cu = context.compilationUnit();

            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (!shouldPreserveMethod(method, context)) {
                    method.setBody(null);
                }
            }

            cu.getAllContainedComments().forEach(Comment::remove);

            return cu.toString();
        } catch (Exception e) {
            throw ContextPruningException.parsingFailed(e);
        }
    }

    public String extractInterfacesOnly(AnalysisContext context) {
        Objects.requireNonNull(context, "context no puede ser null");

        if (!context.isValid()) {
            throw ContextPruningException.parsingFailed(
                new IllegalStateException("AnalysisContext inválido para: " + context.filename())
            );
        }

        try {
            CompilationUnit cu = context.compilationUnit();
            StringBuilder result = new StringBuilder();

            cu.getPackageDeclaration().ifPresent(pkg ->
                result.append(pkg.toString()).append("\n")
            );
            cu.getImports().forEach(imp ->
                result.append(imp.toString())
            );
            result.append("\n");

            boolean foundInterface = false;

            for (ClassOrInterfaceDeclaration decl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (decl.isInterface() || decl.isAbstract()) {
                    foundInterface = true;
                    decl.getAllContainedComments().forEach(Comment::remove);
                    result.append(decl.toString()).append("\n\n");
                }
            }

            if (!foundInterface) {
                return extractSkeleton(context);
            }

            return result.toString();
        } catch (Exception e) {
            throw ContextPruningException.parsingFailed(e);
        }
    }

    public String applyStrategy(AnalysisContext context, PruningStrategy strategy, String targetMethod) {
        Objects.requireNonNull(context, "context no puede ser null");
        Objects.requireNonNull(strategy, "strategy no puede ser null");

        return switch (strategy) {
            case SKELETON_ONLY -> extractSkeleton(context);
            case FOCAL_POINT -> {
                if (targetMethod == null || targetMethod.isBlank()) {
                    throw new IllegalArgumentException("FOCAL_POINT requiere targetMethod");
                }
                yield applyFocalPoint(context, targetMethod);
            }
            case INTERFACE_MODE -> extractInterfacesOnly(context);
            case FULL_FIDELITY -> context.compilationUnit().toString();
            case ABORT -> throw new IllegalArgumentException("ABORT no es una estrategia de poda");
        };
    }

    public int estimateSize(AnalysisContext context, PruningStrategy strategy, String targetMethod) {
        if (strategy == PruningStrategy.FULL_FIDELITY && context.isValid()) {
            return context.compilationUnit().toString().length();
        }
        if (strategy == PruningStrategy.ABORT) {
            return -1;
        }

        try {
            String pruned = applyStrategy(context, strategy, targetMethod);
            return pruned.length();
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean shouldPreserveMethod(MethodDeclaration method, AnalysisContext context) {
        if (context.isMethodOverride(method)) {
            return true;
        }

        if ("main".equals(method.getNameAsString()) && method.isStatic()) {
            return true;
        }

        return false;
    }

    public String applyFocalPoint(String sourceCode, String targetMethodName) {
        Objects.requireNonNull(sourceCode, "sourceCode no puede ser null");
        AnalysisContext context = ParserFactory.createContext("legacy.java", sourceCode);
        return applyFocalPoint(context, targetMethodName);
    }

    public String extractSkeleton(String sourceCode) {
        Objects.requireNonNull(sourceCode, "sourceCode no puede ser null");
        AnalysisContext context = ParserFactory.createContext("legacy.java", sourceCode);
        return extractSkeleton(context);
    }

    public String extractInterfacesOnly(String sourceCode) {
        Objects.requireNonNull(sourceCode, "sourceCode no puede ser null");
        AnalysisContext context = ParserFactory.createContext("legacy.java", sourceCode);
        return extractInterfacesOnly(context);
    }

    public String applyStrategy(String sourceCode, PruningStrategy strategy, String targetMethod) {
        Objects.requireNonNull(sourceCode, "sourceCode no puede ser null");
        Objects.requireNonNull(strategy, "strategy no puede ser null");

        if (strategy == PruningStrategy.FULL_FIDELITY) {
            return sourceCode;
        }
        if (strategy == PruningStrategy.ABORT) {
            throw new IllegalArgumentException("ABORT no es una estrategia de poda");
        }

        AnalysisContext context = ParserFactory.createContext("legacy.java", sourceCode);
        return applyStrategy(context, strategy, targetMethod);
    }

    public int estimateSize(String sourceCode, PruningStrategy strategy, String targetMethod) {
        if (strategy == PruningStrategy.FULL_FIDELITY) {
            return sourceCode.length();
        }
        if (strategy == PruningStrategy.ABORT) {
            return -1;
        }

        try {
            String pruned = applyStrategy(sourceCode, strategy, targetMethod);
            return pruned.length();
        } catch (Exception e) {
            return -1;
        }
    }
}
