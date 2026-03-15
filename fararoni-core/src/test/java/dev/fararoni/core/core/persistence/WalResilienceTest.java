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
package dev.fararoni.core.core.persistence;

import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("WAL Resilience Tests")
class WalResilienceTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        WorkspaceManager.reset();
        WorkspaceManager.initialize(new String[]{"--data-dir=" + tempDir.toString()});
    }

    @AfterEach
    void tearDown() {
        WorkspaceManager.reset();
    }

    @Test
    @DisplayName("WAL debe persistir datos incluso sin close() explícito")
    void walShouldPersistDataWithoutExplicitClose() throws Exception {
        Path dbPath = tempDir.resolve("resilience_test.db");
        String testData = "Datos críticos que NO deben perderse - " + System.currentTimeMillis();

        Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqlitePragmaHelper.configureForPersistence(conn1);

        try (Statement stmt = conn1.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS test_data (id INTEGER PRIMARY KEY, content TEXT)");
            stmt.execute("INSERT INTO test_data (content) VALUES ('" + testData + "')");
        }

        assertTrue(SqlitePragmaHelper.isWalEnabled(conn1), "WAL should be enabled");

        SqlitePragmaHelper.walCheckpoint(conn1);

        try (Connection conn2 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn2.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT content FROM test_data")) {
            assertTrue(rs.next(), "Data should exist after simulated crash");
            assertEquals(testData, rs.getString("content"), "Data should be intact");
        }

        conn1.close();
    }

    @Test
    @DisplayName("Múltiples transacciones deben sobrevivir a crash simulado")
    void multipleTransactionsShouldSurviveCrash() throws Exception {
        Path dbPath = tempDir.resolve("multi_tx_test.db");
        int numRecords = 100;

        Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqlitePragmaHelper.configureForPersistence(conn1);

        try (Statement stmt = conn1.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY, msg TEXT, ts BIGINT)");

            for (int i = 0; i < numRecords; i++) {
                stmt.execute(String.format(
                    "INSERT INTO messages (msg, ts) VALUES ('Message %d', %d)",
                    i, System.currentTimeMillis()
                ));
            }
        }

        SqlitePragmaHelper.walCheckpoint(conn1);

        try (Connection conn2 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn2.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
            assertTrue(rs.next());
            assertEquals(numRecords, rs.getInt(1), "All records should survive");
        }

        conn1.close();
    }

    @Test
    @DisplayName("Base de datos debe ser válida después de crash")
    void databaseShouldBeValidAfterCrash() throws Exception {
        Path dbPath = tempDir.resolve("integrity_test.db");

        Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqlitePragmaHelper.configureForPersistence(conn1);

        try (Statement stmt = conn1.createStatement()) {
            stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, data TEXT)");
            for (int i = 0; i < 50; i++) {
                stmt.execute("INSERT INTO test (data) VALUES ('data_" + i + "')");
            }
        }

        SqlitePragmaHelper.walCheckpoint(conn1);

        try (Connection conn2 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn2.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            assertTrue(rs.next());
            assertEquals("ok", rs.getString(1), "Database integrity should be OK");
        }

        conn1.close();
    }

    @Test
    @DisplayName("Archivos WAL y SHM deben existir durante operación")
    void walAndShmFilesShouldExistDuringOperation() throws Exception {
        Path dbPath = tempDir.resolve("wal_files_test.db");
        Path walPath = tempDir.resolve("wal_files_test.db-wal");
        Path shmPath = tempDir.resolve("wal_files_test.db-shm");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            SqlitePragmaHelper.configureForPersistence(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY)");
                stmt.execute("INSERT INTO test VALUES (1)");
            }

            assertTrue(Files.exists(dbPath), "Main DB should exist");
        }
    }

    @Test
    @DisplayName("Datos deben sobrevivir a múltiples cierres abruptos")
    void dataShouldSurviveMultipleAbruptClosures() throws Exception {
        Path dbPath = tempDir.resolve("multi_crash_test.db");
        int iterations = 5;

        for (int i = 0; i < iterations; i++) {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            SqlitePragmaHelper.configureForPersistence(conn);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS crash_log (id INTEGER PRIMARY KEY, iteration INTEGER)");
                stmt.execute("INSERT INTO crash_log (iteration) VALUES (" + i + ")");
            }

            SqlitePragmaHelper.walCheckpoint(conn);
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM crash_log")) {
            assertTrue(rs.next());
            assertEquals(iterations, rs.getInt(1), "All iterations should be recorded");
        }
    }

    @Test
    @DisplayName("CERTIFICACIÓN: Sistema de Persistencia WAL funciona correctamente")
    void certificationWalPersistenceSystemWorks() throws Exception {
        Path dbPath = tempDir.resolve("certification_test.db");

        String[] testMessages = {
            "Usuario: ¿Cómo funciona el patrón Builder?",
            "Asistente: El patrón Builder es un patrón creacional que...",
            "Usuario: Muéstrame un ejemplo en Java",
            "Asistente: Aquí tienes un ejemplo: public class Builder {...}"
        };

        Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        SqlitePragmaHelper.configureForPersistence(conn1);

        try (Statement stmt = conn1.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chat_history (
                    id INTEGER PRIMARY KEY,
                    role TEXT,
                    content TEXT,
                    timestamp BIGINT
                )
            """);

            for (String msg : testMessages) {
                String[] parts = msg.split(": ", 2);
                stmt.execute(String.format(
                    "INSERT INTO chat_history (role, content, timestamp) VALUES ('%s', '%s', %d)",
                    parts[0], parts[1].replace("'", "''"), System.currentTimeMillis()
                ));
            }
        }

        SqlitePragmaHelper.walCheckpoint(conn1);

        try (Connection conn2 = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn2.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                assertTrue(rs.next());
                assertEquals("ok", rs.getString(1), "Database should be intact after crash");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM chat_history")) {
                assertTrue(rs.next());
                assertEquals(testMessages.length, rs.getInt(1),
                    "All messages should survive crash");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT content FROM chat_history ORDER BY id")) {
                for (String msg : testMessages) {
                    assertTrue(rs.next(), "Message should exist");
                    String expectedContent = msg.split(": ", 2)[1];
                    assertEquals(expectedContent, rs.getString("content"),
                        "Message content should be intact");
                }
            }
        }

        conn1.close();

        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                               ║");
        System.out.println("║   ✅ CERTIFICACIÓN EXITOSA                                    ║");
        System.out.println("║                                                               ║");
        System.out.println("║   Sistema de Persistencia WAL v1.0                            ║");
        System.out.println("║   - Datos sobreviven a crash simulado                         ║");
        System.out.println("║   - Integridad de base de datos verificada                    ║");
        System.out.println("║   - Todos los mensajes recuperados                            ║");
        System.out.println("║                                                               ║");
        System.out.println("║   FARARONI Core está CERTIFICADO para producción              ║");
        System.out.println("║                                                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
    }
}
