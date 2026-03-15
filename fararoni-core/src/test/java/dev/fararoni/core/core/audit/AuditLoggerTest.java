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
package dev.fararoni.core.core.audit;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AuditLogger Tests")
class AuditLoggerTest {
    @TempDir
    Path tempDir;

    private AuditLogger logger;
    private Path auditFile;

    @BeforeEach
    void setUp() {
        auditFile = tempDir.resolve("test-audit.log");
        logger = new AuditLogger(auditFile);
    }

    @AfterEach
    void tearDown() {
        if (logger != null) {
            logger.close();
        }
        AuditLogger.resetForTesting();
    }

    @Nested
    @DisplayName("Basic Logging")
    class BasicLoggingTests {
        @Test
        @DisplayName("logCommand debe escribir entrada de comando")
        void logCommand_ShouldWriteEntry() throws Exception {
            logger.logCommand("help", "Usuario solicitó ayuda");

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            assertTrue(lines.size() >= 1, "Debe haber al menos una línea");

            String lastLine = findLineContaining(lines, "COMMAND");
            assertNotNull(lastLine, "Debe contener línea de COMMAND");
            assertTrue(lastLine.contains("help"), "Debe contener el comando");
            assertTrue(lastLine.contains("INFO"), "Debe ser nivel INFO");
        }

        @Test
        @DisplayName("logLlmCall debe escribir entrada de LLM")
        void logLlmCall_ShouldWriteEntry() throws Exception {
            logger.logLlmCall("llama-3.1", 1500, 200, true);

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String llmLine = findLineContaining(lines, "LLM_CALL");

            assertNotNull(llmLine, "Debe contener línea de LLM_CALL");
            assertTrue(llmLine.contains("llama-3.1"), "Debe contener el modelo");
            assertTrue(llmLine.contains("1500"), "Debe contener tokens de entrada");
            assertTrue(llmLine.contains("OK"), "Debe indicar éxito");
        }

        @Test
        @DisplayName("logLlmCall fallido debe usar nivel WARN")
        void logLlmCall_Failed_ShouldUseWarnLevel() throws Exception {
            logger.logLlmCall("gpt-4", 100, 0, false);

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String llmLine = findLineContaining(lines, "LLM_CALL");

            assertNotNull(llmLine);
            assertTrue(llmLine.contains("WARN"), "Fallo debe ser nivel WARN");
            assertTrue(llmLine.contains("FAILED"), "Debe indicar fallo");
        }

        @Test
        @DisplayName("logFileOperation debe escribir operación de archivo")
        void logFileOperation_ShouldWriteEntry() throws Exception {
            logger.logFileOperation("READ", "/home/user/test.java", true);

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String fileLine = findLineContaining(lines, "FILE_OP");

            assertNotNull(fileLine, "Debe contener línea de FILE_OP");
            assertTrue(fileLine.contains("READ"), "Debe contener operación");
            assertTrue(fileLine.contains("test.java"), "Debe contener archivo");
        }

        @Test
        @DisplayName("logSystem debe escribir evento del sistema")
        void logSystem_ShouldWriteEntry() throws Exception {
            logger.logSystem("Startup", "Application started");

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String systemLine = findLineContaining(lines, "SYSTEM");

            assertNotNull(systemLine);
            assertTrue(systemLine.contains("Startup"));
        }

        @Test
        @DisplayName("logSecurity debe escribir evento de seguridad")
        void logSecurity_ShouldWriteEntry() throws Exception {
            logger.logSecurity("Invalid API Key", "Rejected request");

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String secLine = findLineContaining(lines, "SECURITY");

            assertNotNull(secLine);
            assertTrue(secLine.contains("WARN"), "Seguridad debe ser WARN");
            assertTrue(secLine.contains("Invalid API Key"));
        }

        @Test
        @DisplayName("logError debe escribir error con nivel ERROR")
        void logError_ShouldWriteEntry() throws Exception {
            logger.logError(AuditLogger.Category.LLM_CALL, "Connection failed",
                    new IOException("Network unreachable"));

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String errorLine = findLineContaining(lines, "ERROR");

            assertNotNull(errorLine);
            assertTrue(errorLine.contains("Connection failed"));
            assertTrue(errorLine.contains("IOException"));
        }
    }

    @Nested
    @DisplayName("Format Validation")
    class FormatTests {
        @Test
        @DisplayName("formato debe ser [timestamp] [level] [category] message")
        void format_ShouldBeCorrect() throws Exception {
            logger.logCommand("test", "Test command");

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String line = findLineContaining(lines, "COMMAND");

            assertNotNull(line);
            assertTrue(line.matches("\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\\] \\[\\w+\\] \\[\\w+\\] .+"),
                    "Formato incorrecto: " + line);
        }

        @Test
        @DisplayName("timestamp debe ser ISO-8601 UTC")
        void timestamp_ShouldBeIso8601() throws Exception {
            logger.logSystem("Test", "Testing timestamp");

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String line = lines.get(0);

            int start = line.indexOf('[') + 1;
            int end = line.indexOf(']');
            String timestamp = line.substring(start, end);

            assertTrue(timestamp.endsWith("Z"), "Timestamp debe ser UTC: " + timestamp);
            assertTrue(timestamp.contains("T"), "Debe tener separador T");
        }
    }

    @Nested
    @DisplayName("Async Behavior")
    class AsyncTests {
        @Test
        @DisplayName("múltiples logs deben escribirse en orden")
        void multipleLogs_ShouldWriteInOrder() throws Exception {
            for (int i = 0; i < 10; i++) {
                logger.logCommand("cmd" + i, "Command " + i);
            }

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            List<String> commandLines = lines.stream()
                    .filter(l -> l.contains("COMMAND"))
                    .toList();

            assertEquals(10, commandLines.size(), "Deben escribirse las 10 entradas");

            for (int i = 0; i < 10; i++) {
                assertTrue(commandLines.get(i).contains("cmd" + i),
                        "Comando " + i + " debe estar en posición " + i);
            }
        }

        @Test
        @DisplayName("log no debe bloquear el hilo principal")
        void log_ShouldNotBlockMainThread() {
            long start = System.currentTimeMillis();

            for (int i = 0; i < 100; i++) {
                logger.logCommand("cmd" + i, "Fast command");
            }

            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 500,
                    "100 logs deben completarse en menos de 500ms, tomó: " + elapsed + "ms");
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {
        @Test
        @DisplayName("isRunning debe ser true inicialmente")
        void isRunning_Initially_ShouldBeTrue() {
            assertTrue(logger.isRunning());
        }

        @Test
        @DisplayName("isRunning debe ser false después de close")
        void isRunning_AfterClose_ShouldBeFalse() {
            logger.close();
            assertFalse(logger.isRunning());
        }

        @Test
        @DisplayName("close debe escribir todas las entradas pendientes")
        void close_ShouldFlushPendingEntries() throws Exception {
            for (int i = 0; i < 5; i++) {
                logger.logCommand("cmd" + i, "Command");
            }

            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            List<String> commandLines = lines.stream()
                    .filter(l -> l.contains("COMMAND"))
                    .toList();

            assertEquals(5, commandLines.size(), "Todas las entradas deben haberse escrito");
        }

        @Test
        @DisplayName("log después de close debe ser ignorado")
        void log_AfterClose_ShouldBeIgnored() throws Exception {
            logger.logCommand("before", "Before close");
            logger.close();
            logger.logCommand("after", "After close");

            List<String> lines = Files.readAllLines(auditFile);

            assertTrue(lines.stream().anyMatch(l -> l.contains("before")));
            assertFalse(lines.stream().anyMatch(l -> l.contains("after")),
                    "Entrada después de close no debe escribirse");
        }

        @Test
        @DisplayName("close múltiple debe ser seguro")
        void close_Multiple_ShouldBeSafe() {
            assertDoesNotThrow(() -> {
                logger.close();
                logger.close();
                logger.close();
            });
        }
    }

    @Nested
    @DisplayName("Statistics")
    class StatisticsTests {
        @Test
        @DisplayName("getEntryCount debe contar entradas escritas")
        void getEntryCount_ShouldCountEntries() throws Exception {
            logger.logCommand("cmd1", "First");
            logger.logCommand("cmd2", "Second");
            logger.logCommand("cmd3", "Third");

            waitForWrite();

            assertTrue(logger.getEntryCount() >= 3,
                    "Entry count debe ser al menos 3, actual: " + logger.getEntryCount());
        }

        @Test
        @DisplayName("getAuditFilePath debe retornar path correcto")
        void getAuditFilePath_ShouldReturnCorrectPath() {
            assertEquals(auditFile, logger.getAuditFilePath());
        }
    }

    @Nested
    @DisplayName("Path Sanitization")
    class SanitizationTests {
        @Test
        @DisplayName("home directory debe ser reemplazado por ~")
        void homeDirectory_ShouldBeReplaced() throws Exception {
            String home = System.getProperty("user.home");
            logger.logFileOperation("READ", home + "/documents/secret.txt", true);

            waitForWrite();
            logger.close();

            List<String> lines = Files.readAllLines(auditFile);
            String fileLine = findLineContaining(lines, "FILE_OP");

            assertNotNull(fileLine);
            assertTrue(fileLine.contains("~/documents/secret.txt") || fileLine.contains("~\\documents\\secret.txt"),
                    "Home debe reemplazarse por ~: " + fileLine);
            assertFalse(fileLine.contains(home),
                    "No debe contener path completo del home: " + fileLine);
        }
    }

    private void waitForWrite() throws InterruptedException {
        Thread.sleep(200);
    }

    private String findLineContaining(List<String> lines, String text) {
        return lines.stream()
                .filter(l -> l.contains(text))
                .findFirst()
                .orElse(null);
    }
}
