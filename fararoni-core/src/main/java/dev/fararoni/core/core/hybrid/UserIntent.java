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

import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public enum UserIntent {
    EXPLAIN("Explicar código", Set.of(
        "explica", "explain", "qué hace", "what does", "how does",
        "cómo funciona", "describe", "entender"
    )),

    AUDIT("Auditar/revisar código", Set.of(
        "revisa", "review", "audit", "analiza", "analyze",
        "examina", "examine", "evalúa", "evaluate"
    )),

    DOCUMENTATION("Generar documentación", Set.of(
        "documenta", "document", "javadoc", "comentarios", "comments",
        "docstring", "readme", "documentación"
    )),

    REFACTOR("Refactorizar código", Set.of(
        "refactoriza", "refactor", "mejora", "improve", "optimiza",
        "optimize", "simplifica", "simplify", "clean up", "limpia"
    )),

    DEBUG("Depurar/arreglar bug", Set.of(
        "debug", "depura", "arregla", "fix", "error", "bug",
        "falla", "fails", "no funciona", "doesn't work", "broken"
    )),

    WRITE_TESTS("Escribir tests", Set.of(
        "test", "tests", "prueba", "pruebas", "unit test",
        "coverage", "cobertura", "junit", "pytest", "testing"
    )),

    CODE_GEN("Generar código nuevo", Set.of(
        "crea", "create", "genera", "generate", "implement",
        "implementa", "add", "agrega", "nuevo", "new", "escribe", "write"
    )),

    GENERAL("Intención general", Set.of());

    private final String description;
    private final Set<String> keywords;

    UserIntent(String description, Set<String> keywords) {
        this.description = description;
        this.keywords = keywords;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    public boolean isSkeletonCompatible() {
        return this == EXPLAIN || this == AUDIT || this == DOCUMENTATION;
    }

    public boolean requiresFocalPoint() {
        return this == REFACTOR || this == DEBUG || this == WRITE_TESTS;
    }

    public boolean isCodeGeneration() {
        return this == CODE_GEN;
    }

    public PruningStrategy getRecommendedStrategy() {
        if (isSkeletonCompatible()) {
            return PruningStrategy.SKELETON_ONLY;
        }
        if (requiresFocalPoint()) {
            return PruningStrategy.FOCAL_POINT;
        }
        if (isCodeGeneration()) {
            return PruningStrategy.INTERFACE_MODE;
        }
        return PruningStrategy.ABORT;
    }
}
