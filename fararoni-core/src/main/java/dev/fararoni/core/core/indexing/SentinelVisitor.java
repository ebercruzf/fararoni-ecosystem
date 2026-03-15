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

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.indexing.model.SemanticUnit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SentinelVisitor extends VoidVisitorAdapter<List<SemanticUnit>> {
    private final Set<String> classFieldNames = new HashSet<>();

    private AnalysisContext currentContext;

    public List<SemanticUnit> analyze(AnalysisContext context) {
        if (context == null || !context.isValid()) {
            return List.of();
        }

        this.currentContext = context;
        reset();

        List<SemanticUnit> units = new ArrayList<>();
        context.compilationUnit().accept(this, units);

        return units;
    }

    @Override
    public void visit(MethodDeclaration md, List<SemanticUnit> collector) {
        super.visit(md, collector);

        if (md == null || collector == null) {
            return;
        }

        String signature = buildFullyQualifiedSignature(md);
        String content = md.toString();
        int startLine = md.getBegin().map(p -> p.line).orElse(0);
        int endLine = md.getEnd().map(p -> p.line).orElse(0);

        Set<String> dependencies = analyzeDependencies(md);

        boolean isOverride = detectOverride(md);

        collector.add(new SemanticUnit(
            SemanticUnit.TYPE_METHOD,
            signature,
            content,
            startLine,
            endLine,
            dependencies,
            isOverride
        ));
    }

    @Override
    public void visit(FieldDeclaration fd, List<SemanticUnit> collector) {
        super.visit(fd, collector);

        if (fd == null || collector == null) {
            return;
        }

        fd.getVariables().forEach(var -> classFieldNames.add(var.getNameAsString()));

        String content = fd.toString();
        int startLine = fd.getBegin().map(p -> p.line).orElse(0);
        int endLine = fd.getEnd().map(p -> p.line).orElse(0);

        String signature = fd.getVariables().stream()
            .map(v -> v.getNameAsString())
            .reduce((a, b) -> a + ", " + b)
            .orElse("field");

        collector.add(new SemanticUnit(
            SemanticUnit.TYPE_FIELD,
            signature,
            content,
            startLine,
            endLine,
            Set.of(),
            false
        ));
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration cid, List<SemanticUnit> collector) {
        super.visit(cid, collector);

        if (cid == null || collector == null) {
            return;
        }

        StringBuilder header = new StringBuilder();
        cid.getAnnotations().forEach(a -> header.append(a.toString()).append("\n"));

        if (cid.isInterface()) {
            header.append("interface ");
        } else if (cid.isAbstract()) {
            header.append("abstract class ");
        } else {
            header.append("class ");
        }
        header.append(cid.getNameAsString());

        cid.getExtendedTypes().forEach(t -> header.append(" extends ").append(t.getNameAsString()));
        if (!cid.getImplementedTypes().isEmpty()) {
            header.append(" implements ");
            header.append(cid.getImplementedTypes().stream()
                .map(t -> t.getNameAsString())
                .reduce((a, b) -> a + ", " + b)
                .orElse(""));
        }

        int startLine = cid.getBegin().map(p -> p.line).orElse(0);
        int endLine = cid.getEnd().map(p -> p.line).orElse(0);

        collector.add(new SemanticUnit(
            SemanticUnit.TYPE_CLASS,
            cid.getNameAsString(),
            header.toString(),
            startLine,
            endLine,
            Set.of(),
            false
        ));
    }

    @Override
    public void visit(ConstructorDeclaration cd, List<SemanticUnit> collector) {
        super.visit(cd, collector);

        if (cd == null || collector == null) {
            return;
        }

        String signature = cd.getDeclarationAsString(true, true, true);
        String content = cd.toString();
        int startLine = cd.getBegin().map(p -> p.line).orElse(0);
        int endLine = cd.getEnd().map(p -> p.line).orElse(0);

        collector.add(new SemanticUnit(
            SemanticUnit.TYPE_CONSTRUCTOR,
            signature,
            content,
            startLine,
            endLine,
            Set.of(),
            false
        ));
    }

    @Override
    public void visit(LambdaExpr lambda, List<SemanticUnit> collector) {
        super.visit(lambda, collector);

        if (lambda == null || collector == null) {
            return;
        }

        if (!lambda.getBody().isBlockStmt()) {
            return;
        }

        int startLine = lambda.getBegin().map(p -> p.line).orElse(0);
        int endLine = lambda.getEnd().map(p -> p.line).orElse(0);

        if (endLine - startLine < 3) {
            return;
        }

        String content = lambda.toString();
        String signature = "lambda@" + startLine;

        collector.add(new SemanticUnit(
            SemanticUnit.TYPE_LAMBDA,
            signature,
            content,
            startLine,
            endLine,
            Set.of(),
            false
        ));
    }

    private Set<String> analyzeDependencies(MethodDeclaration md) {
        Set<String> deps = new HashSet<>();

        md.findAll(FieldAccessExpr.class).forEach(fa -> {
            String scope = fa.getScope().toString();
            if ("this".equals(scope)) {
                deps.add(fa.getNameAsString());
            }
        });

        md.findAll(NameExpr.class).forEach(ne -> {
            String name = ne.getNameAsString();
            if (classFieldNames.contains(name)) {
                deps.add(name);
            }
        });

        return deps;
    }

    private boolean detectOverride(MethodDeclaration md) {
        if (currentContext != null) {
            return currentContext.isMethodOverride(md);
        }

        return md.isAnnotationPresent(Override.class);
    }

    public void reset() {
        classFieldNames.clear();
        currentContext = null;
    }

    private String buildFullyQualifiedSignature(MethodDeclaration md) {
        String methodSignature = md.getDeclarationAsString(true, true, true);

        List<String> classPath = new ArrayList<>();
        md.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parent -> {
            collectClassPath(parent, classPath);
        });

        if (classPath.isEmpty()) {
            return methodSignature;
        }

        java.util.Collections.reverse(classPath);
        String prefix = String.join("$", classPath);

        return prefix + "#" + methodSignature;
    }

    private void collectClassPath(ClassOrInterfaceDeclaration clazz, List<String> path) {
        path.add(clazz.getNameAsString());
        clazz.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(parent -> {
            collectClassPath(parent, path);
        });
    }
}
