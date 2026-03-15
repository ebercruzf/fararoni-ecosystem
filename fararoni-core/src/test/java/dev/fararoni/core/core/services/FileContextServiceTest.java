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
package dev.fararoni.core.core.services;

import dev.fararoni.core.core.services.FileContextService.AddResult;
import dev.fararoni.core.core.services.FileContextService.DropResult;
import dev.fararoni.core.core.services.FileContextService.FileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("FileContextService")
class FileContextServiceTest {
    private FileContextService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new FileContextService(tempDir);
    }

    @Nested
    @DisplayName("Agregar Archivos")
    class AddFilesTests {
        @Test
        @DisplayName("agrega archivo individual")
        void addFiles_SingleFile_AddsSuccessfully() throws IOException {
            Path javaFile = tempDir.resolve("Test.java");
            Files.writeString(javaFile, "public class Test {}");

            AddResult result = service.addFile("Test.java");

            assertTrue(result.success());
            assertEquals(1, result.addedCount());
            assertEquals(0, result.skipped());
            assertTrue(service.isLoaded(javaFile));
        }

        @Test
        @DisplayName("agrega multiples archivos con glob")
        void addFiles_GlobPattern_AddsMatching() throws IOException {
            Files.writeString(tempDir.resolve("Main.java"), "public class Main {}");
            Files.writeString(tempDir.resolve("Test.java"), "public class Test {}");
            Files.writeString(tempDir.resolve("readme.txt"), "readme");

            AddResult result = service.addFiles(List.of("*.java"));

            assertTrue(result.success());
            assertEquals(2, result.addedCount());
        }

        @Test
        @DisplayName("omite archivos ya cargados")
        void addFiles_AlreadyLoaded_Skips() throws IOException {
            Path file = tempDir.resolve("App.java");
            Files.writeString(file, "public class App {}");

            service.addFile("App.java");
            AddResult result = service.addFile("App.java");

            assertEquals(0, result.addedCount());
            assertEquals(1, result.skipped());
            assertEquals(1, service.getLoadedCount());
        }

        @Test
        @DisplayName("reporta error si archivo no existe")
        void addFiles_NonExistent_ReportsError() {
            AddResult result = service.addFile("nonexistent.java");

            assertFalse(result.success());
            assertTrue(result.hasErrors());
            assertTrue(result.errors().get(0).contains("No se encontraron"));
        }

        @Test
        @DisplayName("ignora directorios de build")
        void addFiles_IgnoresNodeModules() throws IOException {
            Path nodeModules = tempDir.resolve("node_modules");
            Files.createDirectories(nodeModules);
            Files.writeString(nodeModules.resolve("package.js"), "module.exports = {}");

            Files.writeString(tempDir.resolve("App.js"), "const x = 1;");

            AddResult result = service.addFiles(List.of("*.js"));

            assertEquals(1, result.addedCount());
            assertFalse(service.isLoaded(nodeModules.resolve("package.js")));
        }

        @Test
        @DisplayName("escanea directorio recursivamente")
        void addFiles_Directory_ScansRecursively() throws IOException {
            Path src = tempDir.resolve("src/main/java");
            Files.createDirectories(src);
            Files.writeString(src.resolve("App.java"), "public class App {}");
            Files.writeString(src.resolve("Service.java"), "public class Service {}");

            AddResult result = service.addFile("src/");

            assertTrue(result.success());
            assertEquals(2, result.addedCount());
        }

        @Test
        @DisplayName("soporta extensiones de codigo")
        void addFiles_CodeExtensions_Supported() throws IOException {
            Files.writeString(tempDir.resolve("script.py"), "print('hello')");
            Files.writeString(tempDir.resolve("app.ts"), "const x: number = 1;");
            Files.writeString(tempDir.resolve("main.go"), "package main");

            AddResult result = service.addFiles(List.of("*.py", "*.ts", "*.go"));

            assertEquals(3, result.addedCount());
        }

        @Test
        @DisplayName("soporta extensiones de config")
        void addFiles_ConfigExtensions_Supported() throws IOException {
            Files.writeString(tempDir.resolve("config.json"), "{}");
            Files.writeString(tempDir.resolve("settings.yaml"), "key: value");
            Files.writeString(tempDir.resolve("app.properties"), "name=app");

            AddResult result = service.addFiles(List.of("*.json", "*.yaml", "*.properties"));

            assertEquals(3, result.addedCount());
        }

        @Test
        @DisplayName("calcula total de caracteres correctamente")
        void addFiles_CalculatesTotalChars() throws IOException {
            Files.writeString(tempDir.resolve("a.java"), "12345");
            Files.writeString(tempDir.resolve("b.java"), "67890");

            service.addFiles(List.of("*.java"));

            assertEquals(10, service.getTotalChars());
        }
    }

    @Nested
    @DisplayName("Remover Archivos")
    class DropFilesTests {
        @Test
        @DisplayName("remueve archivo especifico")
        void dropFiles_SingleFile_RemovesSuccessfully() throws IOException {
            Path file = tempDir.resolve("Test.java");
            Files.writeString(file, "public class Test {}");
            service.addFile("Test.java");

            DropResult result = service.dropFile("Test.java");

            assertTrue(result.success());
            assertEquals(1, result.droppedCount());
            assertFalse(service.isLoaded(file));
        }

        @Test
        @DisplayName("remueve por patron de extension")
        void dropFiles_ExtensionPattern_RemovesMatching() throws IOException {
            Files.writeString(tempDir.resolve("A.java"), "class A {}");
            Files.writeString(tempDir.resolve("B.java"), "class B {}");
            Files.writeString(tempDir.resolve("C.py"), "pass");
            service.addFiles(List.of("*.java", "*.py"));

            DropResult result = service.dropFiles(List.of("*.java"));

            assertEquals(2, result.droppedCount());
            assertEquals(1, service.getLoadedCount());
        }

        @Test
        @DisplayName("dropAll remueve todo")
        void dropAll_RemovesAllFiles() throws IOException {
            Files.writeString(tempDir.resolve("A.java"), "class A {}");
            Files.writeString(tempDir.resolve("B.py"), "pass");
            service.addFiles(List.of("*.java", "*.py"));

            DropResult result = service.dropAll();

            assertTrue(result.success());
            assertEquals(2, result.droppedCount());
            assertEquals(0, service.getLoadedCount());
        }

        @Test
        @DisplayName("reporta error si no hay coincidencias")
        void dropFiles_NoMatch_ReportsError() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "class Test {}");
            service.addFile("Test.java");

            DropResult result = service.dropFile("nonexistent.java");

            assertFalse(result.success());
            assertTrue(result.hasErrors());
        }

        @Test
        @DisplayName("calcula caracteres restantes")
        void dropFiles_CalculatesRemainingChars() throws IOException {
            Files.writeString(tempDir.resolve("a.java"), "12345");
            Files.writeString(tempDir.resolve("b.java"), "67890");
            service.addFiles(List.of("*.java"));

            service.dropFile("a.java");

            assertEquals(5, service.getTotalChars());
        }
    }

    @Nested
    @DisplayName("Consulta de Estado")
    class QueryTests {
        @Test
        @DisplayName("listLoadedFiles retorna info correcta")
        void listLoadedFiles_ReturnsCorrectInfo() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "public class Test {}");
            service.addFile("Test.java");

            List<FileInfo> files = service.listLoadedFiles();

            assertEquals(1, files.size());
            assertTrue(files.get(0).path().toString().contains("Test.java"));
            assertEquals(20, files.get(0).chars());
            assertTrue(files.get(0).loadedAt() > 0);
        }

        @Test
        @DisplayName("getLoadedCount retorna conteo correcto")
        void getLoadedCount_ReturnsCorrectCount() throws IOException {
            assertEquals(0, service.getLoadedCount());

            Files.writeString(tempDir.resolve("A.java"), "class A {}");
            Files.writeString(tempDir.resolve("B.java"), "class B {}");
            service.addFiles(List.of("*.java"));

            assertEquals(2, service.getLoadedCount());
        }

        @Test
        @DisplayName("isLoaded detecta archivos cargados")
        void isLoaded_DetectsLoadedFiles() throws IOException {
            Path file = tempDir.resolve("Test.java");
            Files.writeString(file, "class Test {}");

            assertFalse(service.isLoaded(file));

            service.addFile("Test.java");

            assertTrue(service.isLoaded(file));
        }
    }

    @Nested
    @DisplayName("Formato para Contexto")
    class FormatTests {
        @Test
        @DisplayName("formatForContext retorna vacio si no hay archivos")
        void formatForContext_Empty_ReturnsEmpty() {
            String formatted = service.formatForContext();
            assertEquals("", formatted);
        }

        @Test
        @DisplayName("formatForContext incluye header con estadisticas")
        void formatForContext_IncludesHeader() throws IOException {
            Files.writeString(tempDir.resolve("Test.java"), "public class Test {}");
            service.addFile("Test.java");

            String formatted = service.formatForContext();

            assertTrue(formatted.contains("LOADED FILES"));
            assertTrue(formatted.contains("1 files"));
        }

        @Test
        @DisplayName("formatForContext incluye contenido de archivos")
        void formatForContext_IncludesContent() throws IOException {
            Files.writeString(tempDir.resolve("App.java"), "public class App { void run() {} }");
            service.addFile("App.java");

            String formatted = service.formatForContext();

            assertTrue(formatted.contains("App.java"));
            assertTrue(formatted.contains("public class App"));
        }

        @Test
        @DisplayName("formatForContext usa rutas relativas")
        void formatForContext_UsesRelativePaths() throws IOException {
            Path src = tempDir.resolve("src");
            Files.createDirectories(src);
            Files.writeString(src.resolve("Main.java"), "class Main {}");
            service.addFile("src/Main.java");

            String formatted = service.formatForContext();

            assertTrue(formatted.contains("src"));
            assertTrue(formatted.contains("Main.java"));
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {
        @Test
        @DisplayName("constructor por defecto usa directorio actual")
        void defaultConstructor_UsesCurrentDirectory() {
            FileContextService defaultService = new FileContextService();
            assertNotNull(defaultService);
        }

        @Test
        @DisplayName("constructor normaliza path")
        void constructor_NormalizesPath() throws IOException {
            Path subDir = tempDir.resolve("src/../src");
            Files.createDirectories(tempDir.resolve("src"));
            Files.writeString(tempDir.resolve("src/Test.java"), "class Test {}");

            FileContextService normalizedService = new FileContextService(subDir);
            normalizedService.addFile("Test.java");

            assertEquals(1, normalizedService.getLoadedCount());
        }
    }
}
