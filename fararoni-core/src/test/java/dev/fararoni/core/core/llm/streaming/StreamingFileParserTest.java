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
package dev.fararoni.core.core.llm.streaming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("StreamingFileParser")
class StreamingFileParserTest {
    private List<String> startedFiles;
    private List<String> chunks;
    private List<String> endedFiles;
    private StreamingFileParser parser;

    @BeforeEach
    void setUp() {
        startedFiles = new ArrayList<>();
        chunks = new ArrayList<>();
        endedFiles = new ArrayList<>();

        parser = new StreamingFileParser(
            path -> startedFiles.add(path),
            (path, chunk) -> chunks.add(path + ":" + chunk),
            path -> endedFiles.add(path)
        );
    }

    @Nested
    @DisplayName("Detección de archivos")
    class FileDetection {
        @Test
        @DisplayName("Detecta archivo simple en un solo token")
        void detectsSingleFileInOneToken() {
            parser.onToken(">>>FILE:App.java\npublic class App {}");
            parser.flush();

            assertEquals(1, startedFiles.size());
            assertEquals("App.java", startedFiles.get(0));
            assertEquals(1, endedFiles.size());
            assertTrue(chunks.stream().anyMatch(c -> c.contains("public class App")));
        }

        @Test
        @DisplayName("Detecta archivo con path dividido en múltiples tokens")
        void detectsFileWithSplitPath() {
            parser.onToken(">>>FILE:src/main/");
            parser.onToken("java/App.java\n");
            parser.onToken("content here");
            parser.flush();

            assertEquals(1, startedFiles.size());
            assertEquals("src/main/java/App.java", startedFiles.get(0));
        }

        @Test
        @DisplayName("Detecta múltiples archivos en secuencia")
        void detectsMultipleFiles() {
            parser.onToken(">>>FILE:pom.xml\n<project>");
            parser.onToken(">>>FILE:App.java\npublic class App {}");
            parser.flush();

            assertEquals(2, startedFiles.size());
            assertEquals("pom.xml", startedFiles.get(0));
            assertEquals("App.java", startedFiles.get(1));
            assertEquals(2, endedFiles.size());
        }

        @Test
        @DisplayName("Ignora preámbulo antes del primer archivo")
        void ignoresPreamble() {
            parser.onToken("Voy a crear los archivos:\n");
            parser.onToken(">>>FILE:App.java\ncontent");
            parser.flush();

            assertEquals(1, startedFiles.size());
            assertEquals("App.java", startedFiles.get(0));
        }
    }

    @Nested
    @DisplayName("Manejo de contenido")
    class ContentHandling {
        @Test
        @DisplayName("Envía chunks de contenido correctamente")
        void sendsContentChunks() {
            parser.onToken(">>>FILE:test.txt\n");
            parser.onToken("line1\n");
            parser.onToken("line2\n");
            parser.onToken("line3");
            parser.flush();

            assertEquals(3, chunks.size());
            assertTrue(chunks.get(0).contains("line1"));
            assertTrue(chunks.get(1).contains("line2"));
            assertTrue(chunks.get(2).contains("line3"));
        }

        @Test
        @DisplayName("Separa contenido de dos archivos correctamente")
        void separatesContentBetweenFiles() {
            parser.onToken(">>>FILE:a.txt\ncontent-a");
            parser.onToken(">>>FILE:b.txt\ncontent-b");
            parser.flush();

            assertEquals(2, startedFiles.size());
            assertEquals("a.txt", startedFiles.get(0));
            assertEquals("b.txt", startedFiles.get(1));

            assertTrue(chunks.stream().anyMatch(c -> c.contains("content-a")));
            assertTrue(chunks.stream().anyMatch(c -> c.contains("content-b")));
        }
    }

    @Nested
    @DisplayName("Estado del parser")
    class ParserState {
        @Test
        @DisplayName("getCurrentFilePath devuelve path actual")
        void getCurrentFilePathReturnsCurrentPath() {
            assertNull(parser.getCurrentFilePath());

            parser.onToken(">>>FILE:test.java\n");
            assertEquals("test.java", parser.getCurrentFilePath());

            parser.flush();
            assertNull(parser.getCurrentFilePath());
        }

        @Test
        @DisplayName("flush cierra archivo pendiente")
        void flushClosesCurrentFile() {
            parser.onToken(">>>FILE:test.java\ncontent");
            assertEquals(0, endedFiles.size());

            parser.flush();
            assertEquals(1, endedFiles.size());
            assertEquals("test.java", endedFiles.get(0));
        }

        @Test
        @DisplayName("Tokens null o vacíos son ignorados")
        void ignoresNullAndEmptyTokens() {
            parser.onToken(null);
            parser.onToken("");
            parser.onToken(">>>FILE:test.java\ncontent");
            parser.flush();

            assertEquals(1, startedFiles.size());
        }
    }

    @Nested
    @DisplayName("Casos edge")
    class EdgeCases {
        @Test
        @DisplayName("Maneja archivo sin contenido")
        void handlesEmptyFile() {
            parser.onToken(">>>FILE:empty.txt\n");
            parser.onToken(">>>FILE:next.txt\ncontent");
            parser.flush();

            assertEquals(2, startedFiles.size());
            assertEquals("empty.txt", startedFiles.get(0));
            assertEquals("next.txt", startedFiles.get(1));
            assertEquals(2, endedFiles.size());
        }

        @Test
        @DisplayName("Maneja path con espacios")
        void handlesPathWithSpaces() {
            parser.onToken(">>>FILE:  path/with/spaces.txt  \ncontent");
            parser.flush();

            assertEquals("path/with/spaces.txt", startedFiles.get(0));
        }

        @Test
        @DisplayName("Maneja múltiples newlines")
        void handlesMultipleNewlines() {
            parser.onToken(">>>FILE:test.txt\n\n\ncontent\n\n");
            parser.flush();

            assertEquals(1, startedFiles.size());
            assertTrue(chunks.size() >= 1);
        }
    }
}
