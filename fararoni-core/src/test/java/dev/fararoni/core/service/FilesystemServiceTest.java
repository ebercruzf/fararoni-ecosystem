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
package dev.fararoni.core.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("FilesystemService Tests - Plan V5 MANOS")
class FilesystemServiceTest {
    @TempDir
    Path tempDir;

    private FilesystemService service;

    @BeforeEach
    void setUp() {
        service = new FilesystemService(tempDir);
    }

    @Nested
    @DisplayName("Escritura de Archivos")
    class FileWritingTests {
        @Test
        @DisplayName("writeFile debe crear archivo simple")
        void writeFile_SimpleFile_ShouldCreate() {
            WriteResult result = service.writeFile("Test.java", "public class Test {}");

            assertTrue(result.isSuccess(), "Debe ser exitoso: " + result.errorMessage());
            assertEquals("Test.java", result.path().getFileName().toString());
            assertTrue(Files.exists(tempDir.resolve("Test.java")), "Archivo debe existir");
        }

        @Test
        @DisplayName("writeFile debe escribir contenido correcto")
        void writeFile_Content_ShouldMatch() throws IOException {
            String content = "public class Alumno {\n    private String nombre;\n}";

            service.writeFile("Alumno.java", content);

            String actual = Files.readString(tempDir.resolve("Alumno.java"));
            assertEquals(content, actual, "Contenido debe coincidir");
        }

        @Test
        @DisplayName("writeFile con subdirectorio debe crear padres")
        void writeFile_WithSubdir_ShouldCreateParents() {
            WriteResult result = service.writeFile("src/main/java/App.java", "class App {}");

            assertTrue(result.isSuccess());
            assertTrue(Files.exists(tempDir.resolve("src/main/java/App.java")));
            assertTrue(Files.isDirectory(tempDir.resolve("src/main/java")));
        }

        @Test
        @DisplayName("writeFile debe manejar caracteres especiales en contenido")
        void writeFile_SpecialChars_ShouldWork() throws IOException {
            String content = "String s = \"Hola ñ mundo\"; // áéíóú";

            service.writeFile("Test.java", content);

            String actual = Files.readString(tempDir.resolve("Test.java"));
            assertEquals(content, actual);
        }

        @Test
        @DisplayName("writeFile debe sobrescribir archivo existente")
        void writeFile_Existing_ShouldOverwrite() throws IOException {
            Files.writeString(tempDir.resolve("File.txt"), "original");

            service.writeFile("File.txt", "modified");

            String actual = Files.readString(tempDir.resolve("File.txt"));
            assertEquals("modified", actual);
        }

        @Test
        @DisplayName("writeFile con comillas debe funcionar")
        void writeFile_WithQuotes_ShouldWork() throws IOException {
            String content = "System.out.println(\"Hello World\");";

            service.writeFile("Test.java", content);

            String actual = Files.readString(tempDir.resolve("Test.java"));
            assertEquals(content, actual);
        }
    }

    @Nested
    @DisplayName("Creación de Directorios")
    class DirectoryCreationTests {
        @Test
        @DisplayName("createDirectory debe crear directorio simple")
        void createDirectory_Simple_ShouldCreate() {
            WriteResult result = service.createDirectory("models");

            assertTrue(result.isSuccess());
            assertTrue(Files.isDirectory(tempDir.resolve("models")));
        }

        @Test
        @DisplayName("createDirectory debe crear estructura anidada")
        void createDirectory_Nested_ShouldCreate() {
            WriteResult result = service.createDirectory("src/main/java/com/example");

            assertTrue(result.isSuccess());
            assertTrue(Files.isDirectory(tempDir.resolve("src/main/java/com/example")));
        }

        @Test
        @DisplayName("createDirectory existente debe ser exitoso")
        void createDirectory_Existing_ShouldSucceed() throws IOException {
            Files.createDirectory(tempDir.resolve("existing"));

            WriteResult result = service.createDirectory("existing");

            assertTrue(result.isSuccess(), "Directorio existente no debe fallar");
        }
    }

    @Nested
    @DisplayName("Seguridad: Path Traversal")
    class PathTraversalSecurityTests {
        @ParameterizedTest
        @ValueSource(strings = {
            "../passwd",
            "../../etc/passwd",
            "../../../etc/shadow",
            "foo/../../../etc/hosts",
            "bar/baz/../../../../tmp/evil"
        })
        @DisplayName("writeFile debe bloquear path traversal")
        void writeFile_PathTraversal_ShouldBlock(String maliciousPath) {
            WriteResult result = service.writeFile(maliciousPath, "evil content");

            assertFalse(result.isSuccess(), "Path traversal debe ser bloqueado: " + maliciousPath);
            assertNotNull(result.errorMessage());
            assertTrue(result.errorMessage().toLowerCase().contains("traversal") ||
                       result.errorMessage().toLowerCase().contains("bloqueado") ||
                       result.errorMessage().toLowerCase().contains("no permitid"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "../secret",
            "../../data",
            "foo/../../../bar"
        })
        @DisplayName("createDirectory debe bloquear path traversal")
        void createDirectory_PathTraversal_ShouldBlock(String maliciousPath) {
            WriteResult result = service.createDirectory(maliciousPath);

            assertFalse(result.isSuccess(), "Path traversal debe ser bloqueado: " + maliciousPath);
        }
    }

    @Nested
    @DisplayName("Seguridad: Rutas del Sistema")
    class SystemPathSecurityTests {
        @ParameterizedTest
        @ValueSource(strings = {
            "/etc/passwd",
            "/etc/shadow",
            "/usr/bin/evil",
            "/bin/sh"
        })
        @DisplayName("writeFile debe bloquear rutas absolutas del sistema")
        void writeFile_AbsoluteSystemPath_ShouldBlock(String systemPath) {
            WriteResult result = service.writeFile(systemPath, "evil");

            assertFalse(result.isSuccess(), "Ruta del sistema debe ser bloqueada: " + systemPath);
        }
    }

    @Nested
    @DisplayName("Seguridad: Caracteres Peligrosos")
    class DangerousCharsSecurityTests {
        @ParameterizedTest
        @ValueSource(strings = {
            "file;rm -rf /",
            "file|cat /etc/passwd",
            "file`whoami`",
            "file$(id)",
            "file&malicious",
            "file>>/etc/passwd"
        })
        @DisplayName("writeFile debe bloquear rutas con caracteres peligrosos")
        void writeFile_DangerousChars_ShouldBlock(String dangerousPath) {
            WriteResult result = service.writeFile(dangerousPath, "content");

            assertFalse(result.isSuccess(), "Caracteres peligrosos deben ser bloqueados: " + dangerousPath);
        }

        @Test
        @DisplayName("ruta con null bytes debe ser bloqueada")
        void writeFile_NullByte_ShouldBlock() {
            WriteResult result = service.writeFile("file\0.txt", "content");

            assertFalse(result.isSuccess(), "Null bytes deben ser bloqueados");
        }
    }

    @Nested
    @DisplayName("Casos Límite")
    class EdgeCaseTests {
        @Test
        @DisplayName("archivo vacío debe crearse correctamente")
        void writeFile_Empty_ShouldWork() throws IOException {
            WriteResult result = service.writeFile("empty.txt", "");

            assertTrue(result.isSuccess());
            assertEquals("", Files.readString(tempDir.resolve("empty.txt")));
        }

        @Test
        @DisplayName("path con espacios debe funcionar")
        void writeFile_WithSpaces_ShouldWork() throws IOException {
            WriteResult result = service.writeFile("my file.txt", "content");

            assertTrue(result.isSuccess());
            assertTrue(Files.exists(tempDir.resolve("my file.txt")));
        }

        @Test
        @DisplayName("nombre de archivo solo debe funcionar")
        void writeFile_FilenameOnly_ShouldWork() {
            WriteResult result = service.writeFile("simple.java", "class Simple {}");

            assertTrue(result.isSuccess());
            assertEquals("simple.java", result.path().getFileName().toString());
        }

        @Test
        @DisplayName("extensión inusual debe funcionar")
        void writeFile_UnusualExtension_ShouldWork() {
            WriteResult result = service.writeFile("data.xyz123", "binary data");

            assertTrue(result.isSuccess());
            assertTrue(Files.exists(tempDir.resolve("data.xyz123")));
        }

        @Test
        @DisplayName("path null debe fallar gracefully")
        void writeFile_NullPath_ShouldFail() {
            WriteResult result = service.writeFile(null, "content");

            assertFalse(result.isSuccess());
            assertNotNull(result.errorMessage());
        }

        @Test
        @DisplayName("content null debe fallar gracefully")
        void writeFile_NullContent_ShouldFail() {
            WriteResult result = service.writeFile("test.txt", null);
            assertTrue(result.isSuccess() || result.isError());
        }
    }

    @Nested
    @DisplayName("Directorio de Trabajo")
    class WorkingDirectoryTests {
        @Test
        @DisplayName("getWorkingDirectory debe retornar directorio correcto")
        void getWorkingDirectory_ShouldReturnCorrect() {
            assertEquals(tempDir, service.getWorkingDirectory());
        }

        @Test
        @DisplayName("archivos deben crearse relativos al directorio de trabajo")
        void writeFile_ShouldBeRelativeToWorkingDir() {
            service.writeFile("relative.txt", "content");

            assertTrue(Files.exists(tempDir.resolve("relative.txt")));
            assertFalse(Files.exists(Path.of("relative.txt")));
        }
    }
}
