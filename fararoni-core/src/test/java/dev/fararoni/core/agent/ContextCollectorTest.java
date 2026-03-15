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
package dev.fararoni.core.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ContextCollector Tests - Plan V5 OJOS")
class ContextCollectorTest {
    @TempDir
    Path tempDir;

    private ContextCollector collector;

    @BeforeEach
    void setUp() {
        collector = new ContextCollector(tempDir);
    }

    @Nested
    @DisplayName("Detección de Tipo de Proyecto")
    class ProjectTypeDetectionTests {
        @Test
        @DisplayName("debe detectar proyecto Maven con pom.xml")
        void detectProjectType_Maven_ShouldDetect() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.JAVA_MAVEN, info.type());
            assertEquals("Java", info.language());
            assertEquals("Maven", info.buildTool());
        }

        @Test
        @DisplayName("debe detectar proyecto Gradle con build.gradle")
        void detectProjectType_Gradle_ShouldDetect() throws IOException {
            Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");

            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.JAVA_GRADLE, info.type());
            assertTrue(info.language().contains("Java"));
            assertEquals("Gradle", info.buildTool());
        }

        @Test
        @DisplayName("debe detectar proyecto Node con package.json")
        void detectProjectType_Node_ShouldDetect() throws IOException {
            Files.writeString(tempDir.resolve("package.json"), "{\"name\": \"test\"}");

            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.NODE, info.type());
            assertEquals("JavaScript/TypeScript", info.language());
            assertTrue(info.buildTool().contains("npm"), "Build tool debe contener npm");
        }

        @Test
        @DisplayName("debe detectar proyecto Python con requirements.txt")
        void detectProjectType_Python_ShouldDetect() throws IOException {
            Files.writeString(tempDir.resolve("requirements.txt"), "flask==2.0");

            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.PYTHON, info.type());
            assertEquals("Python", info.language());
            assertTrue(info.buildTool().contains("pip"), "Build tool debe contener pip");
        }

        @Test
        @DisplayName("debe detectar proyecto Rust con Cargo.toml")
        void detectProjectType_Rust_ShouldDetect() throws IOException {
            Files.writeString(tempDir.resolve("Cargo.toml"), "[package]\nname = \"test\"");

            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.RUST, info.type());
            assertEquals("Rust", info.language());
            assertEquals("Cargo", info.buildTool());
        }

        @Test
        @DisplayName("debe detectar proyecto Go con go.mod")
        void detectProjectType_Go_ShouldDetect() throws IOException {
            Files.writeString(tempDir.resolve("go.mod"), "module test");

            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.GO, info.type());
            assertEquals("Go", info.language());
            assertTrue(info.buildTool().toLowerCase().contains("go"), "Build tool debe contener go");
        }

        @Test
        @DisplayName("debe retornar UNKNOWN para proyecto sin configuración")
        void detectProjectType_Unknown_ShouldReturnUnknown() {
            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.UNKNOWN, info.type());
        }
    }

    @Nested
    @DisplayName("Contexto Compacto")
    class CompactContextTests {
        @Test
        @DisplayName("debe listar archivos de código")
        void collectCompactContext_CodeFiles_ShouldList() throws IOException {
            Files.writeString(tempDir.resolve("App.java"), "class App {}");
            Files.writeString(tempDir.resolve("Utils.java"), "class Utils {}");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("App.java"));
            assertTrue(context.contains("Utils.java"));
        }

        @Test
        @DisplayName("debe mostrar estructura de directorios")
        void collectCompactContext_Directories_ShouldShow() throws IOException {
            Files.createDirectories(tempDir.resolve("src/main/java"));
            Files.writeString(tempDir.resolve("src/main/java/App.java"), "class App {}");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("src"));
            assertTrue(context.contains("main"));
            assertTrue(context.contains("java"));
        }

        @Test
        @DisplayName("debe mostrar conteo de archivos")
        void collectCompactContext_FileCount_ShouldShow() throws IOException {
            for (int i = 0; i < 5; i++) {
                Files.writeString(tempDir.resolve("File" + i + ".java"), "class File" + i + " {}");
            }

            String context = collector.collectCompactContext();

            assertTrue(context.contains("5 files") || context.contains("5 archivos"),
                       "Debe mostrar conteo de archivos");
        }
    }

    @Nested
    @DisplayName("Contexto Árbol")
    class TreeContextTests {
        @Test
        @DisplayName("debe usar caracteres de árbol")
        void collectTreeContext_TreeChars_ShouldUse() throws IOException {
            Files.createDirectories(tempDir.resolve("src"));
            Files.writeString(tempDir.resolve("src/App.java"), "class App {}");
            Files.writeString(tempDir.resolve("README.md"), "# Test");

            String context = collector.collectTreeContext();

            assertTrue(context.contains("├") || context.contains("└") || context.contains("│"),
                       "Debe usar caracteres de árbol");
        }

        @Test
        @DisplayName("debe mostrar jerarquía correctamente")
        void collectTreeContext_Hierarchy_ShouldShow() throws IOException {
            Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
            Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"), "class App {}");

            String context = collector.collectTreeContext();

            assertTrue(context.contains("src"));
            assertTrue(context.contains("App.java"));
        }
    }

    @Nested
    @DisplayName("Contexto Completo")
    class FullContextTests {
        @Test
        @DisplayName("debe incluir información del proyecto")
        void collectFullContext_ProjectInfo_ShouldInclude() throws IOException {
            Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
            Files.writeString(tempDir.resolve("App.java"), "class App {}");

            String context = collector.collectFullContext();

            assertTrue(context.contains("PROJECT INFO") || context.contains("Proyecto"));
            assertTrue(context.contains("Maven") || context.contains("Java"));
        }

        @Test
        @DisplayName("debe incluir estructura de archivos")
        void collectFullContext_Structure_ShouldInclude() throws IOException {
            Files.writeString(tempDir.resolve("Main.java"), "class Main {}");

            String context = collector.collectFullContext();

            assertTrue(context.contains("PROJECT STRUCTURE") || context.contains("Main.java"));
        }

        @Test
        @DisplayName("debe incluir nombre del proyecto")
        void collectFullContext_ProjectName_ShouldInclude() throws IOException {
            String context = collector.collectFullContext();

            assertTrue(context.contains("Name:") || context.contains("Nombre:") ||
                       context.contains(tempDir.getFileName().toString()));
        }
    }

    @Nested
    @DisplayName("Patrones de Ignorar")
    class IgnorePatternsTests {
        @Test
        @DisplayName("debe ignorar node_modules")
        void collect_NodeModules_ShouldIgnore() throws IOException {
            Files.createDirectories(tempDir.resolve("node_modules/lodash"));
            Files.writeString(tempDir.resolve("node_modules/lodash/index.js"), "module.exports = {}");
            Files.writeString(tempDir.resolve("app.js"), "const _ = require('lodash')");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("app.js"));
            assertFalse(context.contains("lodash"));
        }

        @Test
        @DisplayName("debe ignorar .git")
        void collect_GitDir_ShouldIgnore() throws IOException {
            Files.createDirectories(tempDir.resolve(".git/objects"));
            Files.writeString(tempDir.resolve(".git/config"), "[core]");
            Files.writeString(tempDir.resolve("src.java"), "class Src {}");

            String context = collector.collectCompactContext();

            assertFalse(context.contains(".git"));
            assertTrue(context.contains("src.java"));
        }

        @Test
        @DisplayName("debe ignorar target")
        void collect_TargetDir_ShouldIgnore() throws IOException {
            Files.createDirectories(tempDir.resolve("target/classes"));
            Files.writeString(tempDir.resolve("target/classes/App.class"), "bytecode");
            Files.writeString(tempDir.resolve("App.java"), "class App {}");

            String context = collector.collectCompactContext();

            assertFalse(context.contains("target"));
            assertTrue(context.contains("App.java"));
        }

        @Test
        @DisplayName("debe ignorar __pycache__")
        void collect_Pycache_ShouldIgnore() throws IOException {
            Files.createDirectories(tempDir.resolve("__pycache__"));
            Files.writeString(tempDir.resolve("__pycache__/app.cpython-39.pyc"), "bytecode");
            Files.writeString(tempDir.resolve("app.py"), "def main(): pass");

            String context = collector.collectCompactContext();

            assertFalse(context.contains("__pycache__"));
            assertTrue(context.contains("app.py"));
        }
    }

    @Nested
    @DisplayName("Extensiones de Archivos")
    class FileExtensionTests {
        @Test
        @DisplayName("debe incluir archivos .java")
        void collect_JavaFiles_ShouldInclude() throws IOException {
            Files.writeString(tempDir.resolve("App.java"), "class App {}");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("App.java"));
        }

        @Test
        @DisplayName("debe incluir archivos .py")
        void collect_PythonFiles_ShouldInclude() throws IOException {
            Files.writeString(tempDir.resolve("app.py"), "def main(): pass");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("app.py"));
        }

        @Test
        @DisplayName("debe incluir archivos .js y .ts")
        void collect_JsAndTs_ShouldInclude() throws IOException {
            Files.writeString(tempDir.resolve("app.js"), "const x = 1;");
            Files.writeString(tempDir.resolve("app.ts"), "const x: number = 1;");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("app.js"));
            assertTrue(context.contains("app.ts"));
        }

        @Test
        @DisplayName("debe incluir archivos de configuración")
        void collect_ConfigFiles_ShouldInclude() throws IOException {
            Files.writeString(tempDir.resolve("config.json"), "{}");
            Files.writeString(tempDir.resolve("settings.yaml"), "key: value");
            Files.writeString(tempDir.resolve("config.xml"), "<config/>");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("config.json"));
            assertTrue(context.contains("settings.yaml"));
            assertTrue(context.contains("config.xml"));
        }

        @Test
        @DisplayName("debe incluir archivos markdown")
        void collect_MarkdownFiles_ShouldInclude() throws IOException {
            Files.writeString(tempDir.resolve("README.md"), "# Title");

            String context = collector.collectCompactContext();

            assertTrue(context.contains("README.md"));
        }
    }

    @Nested
    @DisplayName("Límites")
    class LimitsTests {
        @Test
        @DisplayName("debe respetar límite de archivos")
        void collect_MaxFiles_ShouldLimit() throws IOException {
            for (int i = 0; i < 250; i++) {
                Files.writeString(tempDir.resolve("File" + i + ".java"), "class File" + i + " {}");
            }

            ContextCollector limitedCollector = new ContextCollector(tempDir, 10, 10);
            String context = limitedCollector.collectCompactContext();

            long fileCount = context.lines()
                .filter(l -> l.contains(".java"))
                .count();

            assertTrue(fileCount <= 50, "Debe respetar límite de archivos, encontrados: " + fileCount);
        }

        @Test
        @DisplayName("debe respetar límite de profundidad")
        void collect_MaxDepth_ShouldLimit() throws IOException {
            Path deep = tempDir;
            for (int i = 0; i < 15; i++) {
                deep = deep.resolve("level" + i);
            }
            Files.createDirectories(deep);
            Files.writeString(deep.resolve("Deep.java"), "class Deep {}");

            ContextCollector limitedCollector = new ContextCollector(tempDir, 100, 3);
            String context = limitedCollector.collectCompactContext();

            assertFalse(context.contains("level14"), "No debe llegar a nivel 14");
        }
    }

    @Nested
    @DisplayName("Refresh")
    class RefreshTests {
        @Test
        @DisplayName("refresh debe actualizar contexto")
        void refresh_ShouldUpdateContext() throws IOException {
            String context1 = collector.collectCompactContext();

            Files.writeString(tempDir.resolve("NewFile.java"), "class NewFile {}");

            String context2 = collector.refresh();

            assertFalse(context1.contains("NewFile.java"));
            assertTrue(context2.contains("NewFile.java"));
        }
    }

    @Nested
    @DisplayName("Proyecto Vacío")
    class EmptyProjectTests {
        @Test
        @DisplayName("debe manejar proyecto vacío")
        void collect_EmptyProject_ShouldNotFail() {
            String context = collector.collectCompactContext();

            assertNotNull(context);
            assertTrue(context.contains("0 files") || context.contains("(empty)") ||
                       context.length() < 100);
        }

        @Test
        @DisplayName("tipo debe ser UNKNOWN para proyecto vacío")
        void detectProjectType_Empty_ShouldBeUnknown() {
            ContextCollector.ProjectInfo info = collector.detectProjectType();

            assertEquals(ContextCollector.ProjectType.UNKNOWN, info.type());
        }
    }
}
