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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("StreamingFileWriter MIL-SPEC")
class StreamingFileWriterTest {
    @TempDir
    Path tempDir;

    private StreamingFileWriter writer;

    @BeforeEach
    void setUp() {
        writer = new StreamingFileWriter(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (writer != null) {
            writer.shutdown();
        }
    }

    @Nested
    @DisplayName("Operaciones básicas")
    class BasicOperations {
        @Test
        @DisplayName("Escribe archivo simple correctamente")
        void writesSimpleFile() throws Exception {
            writer.start();

            writer.startFile("test.txt");
            writer.writeChunk("test.txt", "Hello, ");
            writer.writeChunk("test.txt", "World!");
            writer.endFile("test.txt");

            Thread.sleep(500);

            Path file = tempDir.resolve("test.txt");
            assertTrue(Files.exists(file), "Archivo debe existir");
            assertEquals("Hello, World!", Files.readString(file));
        }

        @Test
        @DisplayName("Escribe múltiples archivos")
        void writesMultipleFiles() throws Exception {
            writer.start();

            writer.startFile("a.txt");
            writer.writeChunk("a.txt", "content-a");
            writer.endFile("a.txt");

            writer.startFile("b.txt");
            writer.writeChunk("b.txt", "content-b");
            writer.endFile("b.txt");

            Thread.sleep(500);

            assertEquals("content-a", Files.readString(tempDir.resolve("a.txt")));
            assertEquals("content-b", Files.readString(tempDir.resolve("b.txt")));
        }

        @Test
        @DisplayName("Crea directorios padre automáticamente")
        void createsParentDirectories() throws Exception {
            writer.start();

            writer.startFile("deep/nested/path/file.txt");
            writer.writeChunk("deep/nested/path/file.txt", "content");
            writer.endFile("deep/nested/path/file.txt");

            Thread.sleep(500);

            Path file = tempDir.resolve("deep/nested/path/file.txt");
            assertTrue(Files.exists(file));
            assertEquals("content", Files.readString(file));
        }
    }

    @Nested
    @DisplayName("Seguridad MIL-SPEC")
    class SecurityTests {
        @Test
        @DisplayName("Bloquea path traversal con ../")
        void blocksPathTraversal() throws Exception {
            writer.start();

            writer.startFile("../../../etc/passwd");

            Thread.sleep(500);

            assertFalse(Files.exists(Path.of("/etc/passwd.tmp")));
            assertFalse(Files.exists(tempDir.resolve("../../../etc/passwd")));
        }

        @Test
        @DisplayName("Bloquea path traversal con path absoluto")
        void blocksAbsolutePathEscape() throws Exception {
            writer.start();

            writer.startFile("safe/../../escape.txt");

            Thread.sleep(500);

            assertFalse(Files.exists(tempDir.resolve("escape.txt")));
        }

        @Test
        @DisplayName("Shadow Writing: usa archivos .tmp durante escritura")
        void usesTempFilesDuringWrite() throws Exception {
            writer.start();

            writer.startFile("shadow-test.txt");
            writer.writeChunk("shadow-test.txt", "partial content");

            Thread.sleep(200);

            Path tempFile = tempDir.resolve("shadow-test.txt.tmp");
            Path finalFile = tempDir.resolve("shadow-test.txt");

            assertTrue(Files.exists(tempFile), "Archivo .tmp debe existir durante escritura");
            assertFalse(Files.exists(finalFile), "Archivo final NO debe existir aún");

            writer.endFile("shadow-test.txt");
            Thread.sleep(300);

            assertFalse(Files.exists(tempFile), "Archivo .tmp debe ser eliminado");
            assertTrue(Files.exists(finalFile), "Archivo final debe existir");
        }

        @Test
        @DisplayName("Atomic Commit: archivo aparece completo o no aparece")
        void atomicCommit() throws Exception {
            writer.start();

            String content = "Line 1\nLine 2\nLine 3\n";

            writer.startFile("atomic.txt");
            for (char c : content.toCharArray()) {
                writer.writeChunk("atomic.txt", String.valueOf(c));
            }
            writer.endFile("atomic.txt");

            Thread.sleep(500);

            String result = Files.readString(tempDir.resolve("atomic.txt"));
            assertEquals(content, result);
        }

        @Test
        @DisplayName("Cleanup: .tmp huérfanos se limpian en shutdown")
        void cleanupOrphanTempFiles() throws Exception {
            writer.start();

            writer.startFile("orphan.txt");
            writer.writeChunk("orphan.txt", "orphan content");

            Thread.sleep(200);

            Path tempFile = tempDir.resolve("orphan.txt.tmp");
            assertTrue(Files.exists(tempFile));

            writer.shutdown();

            Thread.sleep(300);

            assertFalse(Files.exists(tempFile), "Archivo .tmp huérfano debe ser limpiado");
            assertFalse(Files.exists(tempDir.resolve("orphan.txt")), "Archivo incompleto NO debe existir");
        }
    }

    @Nested
    @DisplayName("HealthCheck")
    class HealthCheckTests {
        @Test
        @DisplayName("isHealthy retorna true cuando está corriendo")
        void isHealthyWhenRunning() {
            assertFalse(writer.isHealthy(), "No debe estar healthy antes de start");

            writer.start();
            assertTrue(writer.isHealthy(), "Debe estar healthy después de start");

            writer.shutdown();
            assertFalse(writer.isHealthy(), "No debe estar healthy después de shutdown");
        }

        @Test
        @DisplayName("isRunning refleja estado correcto")
        void isRunningReflectsState() {
            assertFalse(writer.isRunning());

            writer.start();
            assertTrue(writer.isRunning());

            writer.shutdown();
            assertFalse(writer.isRunning());
        }

        @Test
        @DisplayName("getActiveFileCount retorna conteo correcto")
        void activeFileCountIsCorrect() throws Exception {
            writer.start();

            assertEquals(0, writer.getActiveFileCount());

            writer.startFile("file1.txt");
            Thread.sleep(100);
            assertEquals(1, writer.getActiveFileCount());

            writer.startFile("file2.txt");
            Thread.sleep(100);
            assertEquals(2, writer.getActiveFileCount());

            writer.endFile("file1.txt");
            Thread.sleep(100);
            assertEquals(1, writer.getActiveFileCount());

            writer.endFile("file2.txt");
            Thread.sleep(100);
            assertEquals(0, writer.getActiveFileCount());
        }
    }

    @Nested
    @DisplayName("Callbacks")
    class CallbackTests {
        @Test
        @DisplayName("Callback de completado se invoca correctamente")
        void completionCallbackInvoked() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Path> completedPath = new AtomicReference<>();
            AtomicReference<Long> completedBytes = new AtomicReference<>();

            StreamingFileWriter writerWithCallback = new StreamingFileWriter(tempDir,
                (path, bytes) -> {
                    completedPath.set(path);
                    completedBytes.set(bytes);
                    latch.countDown();
                });

            writerWithCallback.start();

            writerWithCallback.startFile("callback-test.txt");
            writerWithCallback.writeChunk("callback-test.txt", "12345");
            writerWithCallback.endFile("callback-test.txt");

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Callback debe ser invocado");
            assertEquals(tempDir.resolve("callback-test.txt"), completedPath.get());
            assertEquals(5L, completedBytes.get());

            writerWithCallback.shutdown();
        }
    }

    @Nested
    @DisplayName("Concurrencia")
    class ConcurrencyTests {
        @Test
        @DisplayName("Maneja múltiples archivos concurrentes")
        void handlesConcurrentFiles() throws Exception {
            writer.start();

            int fileCount = 10;
            for (int i = 0; i < fileCount; i++) {
                String fileName = "concurrent-" + i + ".txt";
                writer.startFile(fileName);
                writer.writeChunk(fileName, "content-" + i);
                writer.endFile(fileName);
            }

            Thread.sleep(1000);

            for (int i = 0; i < fileCount; i++) {
                Path file = tempDir.resolve("concurrent-" + i + ".txt");
                assertTrue(Files.exists(file), "Archivo " + i + " debe existir");
                assertEquals("content-" + i, Files.readString(file));
            }
        }

        @Test
        @DisplayName("start() es idempotente")
        void startIsIdempotent() {
            writer.start();
            writer.start();
            writer.start();

            assertTrue(writer.isRunning());
        }

        @Test
        @DisplayName("shutdown() es idempotente")
        void shutdownIsIdempotent() {
            writer.start();
            writer.shutdown();
            writer.shutdown();
            writer.shutdown();

            assertFalse(writer.isRunning());
        }
    }
}
