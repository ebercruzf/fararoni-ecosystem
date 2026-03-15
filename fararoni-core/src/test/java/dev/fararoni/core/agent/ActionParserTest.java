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

import dev.fararoni.core.service.FilesystemService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ActionParser Tests - Plan V5 MANOS")
class ActionParserTest {
    @TempDir
    Path tempDir;

    private FilesystemService filesystemService;
    private ActionParser parser;
    private List<String> capturedOutput;

    @BeforeEach
    void setUp() {
        filesystemService = new FilesystemService(tempDir);
        capturedOutput = new ArrayList<>();
        parser = new ActionParser(filesystemService, capturedOutput::add);
    }

    @Nested
    @DisplayName("Creación Básica de Archivos")
    class BasicFileCreationTests {
        @Test
        @DisplayName("debe crear archivo simple con >>>FILE:")
        void processLine_SimpleFile_ShouldCreate() throws IOException {
            parser.processLine(">>>FILE: Test.java");
            parser.processLine("public class Test {}");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("Test.java")));
            assertEquals("public class Test {}", Files.readString(tempDir.resolve("Test.java")));
        }

        @Test
        @DisplayName("debe mostrar mensaje de archivo guardado")
        void processLine_File_ShouldShowMessage() {
            parser.processLine(">>>FILE: Test.java");
            parser.processLine("content");
            parser.processLine("<<<END_FILE");

            assertTrue(capturedOutput.stream().anyMatch(s -> s.contains("Archivo guardado")));
            assertTrue(capturedOutput.stream().anyMatch(s -> s.contains("Test.java")));
        }

        @Test
        @DisplayName("debe crear archivo con múltiples líneas")
        void processLine_MultiLine_ShouldCreate() throws IOException {
            parser.processLine(">>>FILE: Alumno.java");
            parser.processLine("public class Alumno {");
            parser.processLine("    private String nombre;");
            parser.processLine("    private int edad;");
            parser.processLine("}");
            parser.processLine("<<<END_FILE");

            String content = Files.readString(tempDir.resolve("Alumno.java"));
            assertTrue(content.contains("public class Alumno"));
            assertTrue(content.contains("private String nombre"));
            assertTrue(content.contains("private int edad"));
        }

        @Test
        @DisplayName("debe preservar indentación")
        void processLine_Indentation_ShouldPreserve() throws IOException {
            parser.processLine(">>>FILE: Indented.java");
            parser.processLine("class Test {");
            parser.processLine("    void method() {");
            parser.processLine("        System.out.println();");
            parser.processLine("    }");
            parser.processLine("}");
            parser.processLine("<<<END_FILE");

            String content = Files.readString(tempDir.resolve("Indented.java"));
            assertTrue(content.contains("    void method()"), "Debe preservar 4 espacios");
            assertTrue(content.contains("        System.out"), "Debe preservar 8 espacios");
        }
    }

    @Nested
    @DisplayName("Salida de Chat")
    class ChatOutputTests {
        @Test
        @DisplayName("debe pasar texto normal al callback")
        void processLine_Chat_ShouldPassThrough() {
            parser.processLine("Aquí está tu código:");
            parser.processLine("He creado la clase.");

            assertTrue(capturedOutput.contains("Aquí está tu código:"));
            assertTrue(capturedOutput.contains("He creado la clase."));
        }

        @Test
        @DisplayName("no debe mostrar delimitadores en output")
        void processLine_Delimiters_ShouldNotShow() {
            parser.processLine(">>>FILE: Test.java");
            parser.processLine("content");
            parser.processLine("<<<END_FILE");

            assertFalse(capturedOutput.stream().anyMatch(s -> s.contains(">>>FILE:")));
            assertFalse(capturedOutput.stream().anyMatch(s -> s.contains("<<<END_FILE")));
        }

        @Test
        @DisplayName("no debe mostrar contenido del archivo en chat")
        void processLine_FileContent_ShouldNotShowInChat() {
            parser.processLine(">>>FILE: Secret.java");
            parser.processLine("class SecretContent {}");
            parser.processLine("<<<END_FILE");

            assertFalse(capturedOutput.stream().anyMatch(s -> s.contains("SecretContent") && !s.contains("guardado")));
        }

        @Test
        @DisplayName("debe mezclar chat y archivos correctamente")
        void processLine_MixedChatAndFiles_ShouldWork() {
            parser.processLine("Voy a crear dos archivos:");
            parser.processLine(">>>FILE: First.java");
            parser.processLine("class First {}");
            parser.processLine("<<<END_FILE");
            parser.processLine("Y el segundo:");
            parser.processLine(">>>FILE: Second.java");
            parser.processLine("class Second {}");
            parser.processLine("<<<END_FILE");
            parser.processLine("Listo!");

            assertTrue(capturedOutput.contains("Voy a crear dos archivos:"));
            assertTrue(capturedOutput.contains("Y el segundo:"));
            assertTrue(capturedOutput.contains("Listo!"));
            assertTrue(Files.exists(tempDir.resolve("First.java")));
            assertTrue(Files.exists(tempDir.resolve("Second.java")));
        }
    }

    @Nested
    @DisplayName("Parsing Flexible")
    class FlexibleParsingTests {
        @Test
        @DisplayName("debe aceptar >>> FILE: con espacio")
        void processLine_WithSpace_ShouldWork() throws IOException {
            parser.processLine(">>> FILE: Spaced.java");
            parser.processLine("class Spaced {}");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("Spaced.java")));
        }

        @Test
        @DisplayName("debe aceptar >>>file: en minúsculas")
        void processLine_Lowercase_ShouldWork() throws IOException {
            parser.processLine(">>>file: Lower.java");
            parser.processLine("class Lower {}");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("Lower.java")));
        }

        @Test
        @DisplayName("debe manejar espacios antes del path")
        void processLine_SpacesBeforePath_ShouldWork() throws IOException {
            parser.processLine(">>>FILE:   Trimmed.java");
            parser.processLine("content");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("Trimmed.java")));
        }
    }

    @Nested
    @DisplayName("Creación de Directorios")
    class DirectoryCreationTests {
        @Test
        @DisplayName("debe crear directorio con >>>MKDIR:")
        void processLine_Mkdir_ShouldCreate() {
            parser.processLine(">>>MKDIR: models");
            parser.processLine("<<<END_MKDIR");

            assertTrue(Files.isDirectory(tempDir.resolve("models")));
        }

        @Test
        @DisplayName("debe mostrar mensaje de directorio creado")
        void processLine_Mkdir_ShouldShowMessage() {
            parser.processLine(">>>MKDIR: services");
            parser.processLine("<<<END_MKDIR");

            assertTrue(capturedOutput.stream().anyMatch(s -> s.contains("Directorio creado")));
        }
    }

    @Nested
    @DisplayName("Subdirectorios")
    class SubdirectoryTests {
        @Test
        @DisplayName("debe crear archivo en subdirectorio")
        void processLine_Subdir_ShouldCreate() throws IOException {
            parser.processLine(">>>FILE: src/main/java/App.java");
            parser.processLine("class App {}");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.exists(tempDir.resolve("src/main/java/App.java")));
        }

        @Test
        @DisplayName("debe crear directorios padre automáticamente")
        void processLine_Subdir_ShouldCreateParents() {
            parser.processLine(">>>FILE: deep/nested/path/File.java");
            parser.processLine("content");
            parser.processLine("<<<END_FILE");

            assertTrue(Files.isDirectory(tempDir.resolve("deep/nested/path")));
        }
    }

    @Nested
    @DisplayName("Flush y Recuperación")
    class FlushTests {
        @Test
        @DisplayName("flush debe guardar archivo sin END_FILE")
        void flush_IncompleteBlock_ShouldSave() throws IOException {
            parser.processLine(">>>FILE: Incomplete.java");
            parser.processLine("class Incomplete {}");

            parser.flush();

            assertTrue(Files.exists(tempDir.resolve("Incomplete.java")));
            assertTrue(capturedOutput.stream().anyMatch(s -> s.contains("auto-cerrado")));
        }

        @Test
        @DisplayName("flush sin bloque pendiente no debe hacer nada")
        void flush_NoBlock_ShouldDoNothing() {
            parser.processLine("Solo texto");
            parser.flush();

            assertEquals(1, capturedOutput.size());
            assertEquals("Solo texto", capturedOutput.get(0));
        }

        @Test
        @DisplayName("reset debe limpiar estado")
        void reset_ShouldClearState() {
            parser.processLine(">>>FILE: Test.java");
            parser.processLine("content");

            parser.reset();
            parser.flush();

            assertFalse(Files.exists(tempDir.resolve("Test.java")));
        }
    }

    @Nested
    @DisplayName("Tracking de Resultados")
    class ResultsTrackingTests {
        @Test
        @DisplayName("getResults debe retornar acciones ejecutadas")
        void getResults_ShouldReturnActions() {
            parser.processLine(">>>FILE: A.java");
            parser.processLine("class A {}");
            parser.processLine("<<<END_FILE");
            parser.processLine(">>>FILE: B.java");
            parser.processLine("class B {}");
            parser.processLine("<<<END_FILE");

            List<ActionParser.ActionResult> results = parser.getResults();

            assertEquals(2, results.size());
            assertTrue(results.stream().anyMatch(r -> r.path().equals("A.java")));
            assertTrue(results.stream().anyMatch(r -> r.path().equals("B.java")));
        }

        @Test
        @DisplayName("hasFileOperations debe ser true si hubo archivos")
        void hasFileOperations_WithFiles_ShouldBeTrue() {
            parser.processLine(">>>FILE: Test.java");
            parser.processLine("content");
            parser.processLine("<<<END_FILE");

            assertTrue(parser.hasFileOperations());
        }

        @Test
        @DisplayName("hasFileOperations debe ser false sin archivos")
        void hasFileOperations_NoFiles_ShouldBeFalse() {
            parser.processLine("Solo chat");

            assertFalse(parser.hasFileOperations());
        }

        @Test
        @DisplayName("isCapturing debe indicar estado actual")
        void isCapturing_ShouldIndicateState() {
            assertFalse(parser.isCapturing());

            parser.processLine(">>>FILE: Test.java");
            assertTrue(parser.isCapturing());

            parser.processLine("<<<END_FILE");
            assertFalse(parser.isCapturing());
        }
    }

    @Nested
    @DisplayName("Contenido Especial")
    class SpecialContentTests {
        @Test
        @DisplayName("debe manejar comillas en contenido")
        void processLine_Quotes_ShouldWork() throws IOException {
            parser.processLine(">>>FILE: Quotes.java");
            parser.processLine("String s = \"Hello World\";");
            parser.processLine("<<<END_FILE");

            String content = Files.readString(tempDir.resolve("Quotes.java"));
            assertTrue(content.contains("\"Hello World\""));
        }

        @Test
        @DisplayName("debe manejar caracteres especiales")
        void processLine_SpecialChars_ShouldWork() throws IOException {
            parser.processLine(">>>FILE: Spanish.java");
            parser.processLine("// Comentario con ñ y acentos: áéíóú");
            parser.processLine("<<<END_FILE");

            String content = Files.readString(tempDir.resolve("Spanish.java"));
            assertTrue(content.contains("ñ"));
            assertTrue(content.contains("áéíóú"));
        }

        @Test
        @DisplayName("debe manejar líneas vacías en contenido")
        void processLine_EmptyLines_ShouldPreserve() throws IOException {
            parser.processLine(">>>FILE: EmptyLines.java");
            parser.processLine("class Test {");
            parser.processLine("");
            parser.processLine("    void method() {}");
            parser.processLine("");
            parser.processLine("}");
            parser.processLine("<<<END_FILE");

            String content = Files.readString(tempDir.resolve("EmptyLines.java"));
            assertTrue(content.contains("\n\n"), "Debe preservar líneas vacías");
        }

        @Test
        @DisplayName("debe manejar <<< en contenido que no sea END_FILE")
        void processLine_TripleLessThan_ShouldNotCloseBlock() throws IOException {
            parser.processLine(">>>FILE: Heredoc.java");
            parser.processLine("String text = <<<CONTENT");
            parser.processLine("some text");
            parser.processLine("CONTENT;");
            parser.processLine("<<<END_FILE");

            String content = Files.readString(tempDir.resolve("Heredoc.java"));
            assertTrue(content.contains("<<<CONTENT"));
        }
    }

    @Nested
    @DisplayName("Manejo de Errores")
    class ErrorHandlingTests {
        @Test
        @DisplayName("path traversal debe mostrar error")
        void processLine_PathTraversal_ShouldShowError() {
            parser.processLine(">>>FILE: ../../../etc/passwd");
            parser.processLine("evil");
            parser.processLine("<<<END_FILE");

            assertTrue(capturedOutput.stream().anyMatch(s -> s.contains("Error")));
            assertTrue(parser.getResults().stream().anyMatch(r -> !r.success()));
        }

        @Test
        @DisplayName("resultado fallido debe registrarse")
        void processLine_FailedWrite_ShouldRecordResult() {
            parser.processLine(">>>FILE: ../outside.txt");
            parser.processLine("content");
            parser.processLine("<<<END_FILE");

            List<ActionParser.ActionResult> results = parser.getResults();
            assertTrue(results.stream().anyMatch(r -> !r.success()));
        }
    }
}
