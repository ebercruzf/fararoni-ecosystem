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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("GitignoreFilter Tests - Paso 4: Filtro de Exclusion Militar")
class GitignoreFilterTest {
    private GitignoreFilter filter;

    @BeforeEach
    void setUp() {
        filter = GitignoreFilter.withDefaults();
    }

    @Nested
    @DisplayName("Directorios por Defecto")
    class DefaultDirectoriesTests {
        @Test
        @DisplayName("Ignora node_modules")
        void testIgnoresNodeModules() {
            assertTrue(filter.isIgnored("node_modules"));
            assertTrue(filter.isIgnored("node_modules/package.json"));
            assertTrue(filter.isIgnored("frontend/node_modules/lodash"));
        }

        @Test
        @DisplayName("Ignora target (Maven)")
        void testIgnoresTarget() {
            assertTrue(filter.isIgnored("target"));
            assertTrue(filter.isIgnored("target/classes"));
        }

        @Test
        @DisplayName("Ignora build (Gradle)")
        void testIgnoresBuild() {
            assertTrue(filter.isIgnored("build"));
            assertTrue(filter.isIgnored("build/libs"));
        }

        @Test
        @DisplayName("Ignora .git")
        void testIgnoresGit() {
            assertTrue(filter.isIgnored(".git"));
            assertTrue(filter.isIgnored(".git/objects"));
        }

        @Test
        @DisplayName("Ignora __pycache__")
        void testIgnoresPycache() {
            assertTrue(filter.isIgnored("__pycache__"));
            assertTrue(filter.isIgnored("src/__pycache__/module.pyc"));
        }

        @Test
        @DisplayName("Ignora venv y .venv")
        void testIgnoresVenv() {
            assertTrue(filter.isIgnored("venv"));
            assertTrue(filter.isIgnored(".venv"));
            assertTrue(filter.isIgnored("venv/bin/python"));
        }

        @Test
        @DisplayName("Ignora .idea y .vscode")
        void testIgnoresIdeDirectories() {
            assertTrue(filter.isIgnored(".idea"));
            assertTrue(filter.isIgnored(".vscode"));
        }

        @Test
        @DisplayName("Ignora dist")
        void testIgnoresDist() {
            assertTrue(filter.isIgnored("dist"));
            assertTrue(filter.isIgnored("dist/index.js"));
        }
    }

    @Nested
    @DisplayName("Patrones por Defecto")
    class DefaultPatternsTests {
        @Test
        @DisplayName("Ignora archivos .pyc")
        void testIgnoresPycFiles() {
            assertTrue(filter.isIgnored("module.pyc"));
            assertTrue(filter.isIgnored("src/utils.pyc"));
        }

        @Test
        @DisplayName("Ignora archivos .class")
        void testIgnoresClassFiles() {
            assertTrue(filter.isIgnored("Main.class"));
            assertTrue(filter.isIgnored("target/classes/Main.class"));
        }

        @Test
        @DisplayName("Ignora archivos .log")
        void testIgnoresLogFiles() {
            assertTrue(filter.isIgnored("app.log"));
            assertTrue(filter.isIgnored("logs/error.log"));
        }

        @Test
        @DisplayName("Ignora .DS_Store")
        void testIgnoresDsStore() {
            assertTrue(filter.isIgnored(".DS_Store"));
        }
    }

    @Nested
    @DisplayName("Archivos No Ignorados")
    class NonIgnoredFilesTests {
        @Test
        @DisplayName("No ignora archivos de codigo")
        void testDoesNotIgnoreCodeFiles() {
            assertFalse(filter.isIgnored("src/Main.java"));
            assertFalse(filter.isIgnored("app.py"));
            assertFalse(filter.isIgnored("index.js"));
            assertFalse(filter.isIgnored("main.go"));
        }

        @Test
        @DisplayName("No ignora archivos de configuracion")
        void testDoesNotIgnoreConfigFiles() {
            assertFalse(filter.isIgnored("pom.xml"));
            assertFalse(filter.isIgnored("package.json"));
            assertFalse(filter.isIgnored("tsconfig.json"));
        }

        @Test
        @DisplayName("No ignora README y documentacion")
        void testDoesNotIgnoreDocs() {
            assertFalse(filter.isIgnored("README.md"));
            assertFalse(filter.isIgnored("docs/api.md"));
        }

        @Test
        @DisplayName("Null y vacio retornan false")
        void testNullAndEmptyNotIgnored() {
            assertFalse(filter.isIgnored((String) null));
            assertFalse(filter.isIgnored(""));
            assertFalse(filter.isIgnored("   "));
        }
    }

    @Nested
    @DisplayName("API")
    class ApiTests {
        @Test
        @DisplayName("isIgnored acepta Path")
        void testIsIgnoredWithPath() {
            assertTrue(filter.isIgnored(Path.of("node_modules/package.json")));
            assertFalse(filter.isIgnored(Path.of("src/Main.java")));
        }

        @Test
        @DisplayName("hasGitignore retorna false para defaults")
        void testHasGitignoreReturnsFalse() {
            assertFalse(filter.hasGitignore());
        }

        @Test
        @DisplayName("getPatternCount retorna numero positivo")
        void testGetPatternCount() {
            assertTrue(filter.getPatternCount() > 0);
        }
    }
}
