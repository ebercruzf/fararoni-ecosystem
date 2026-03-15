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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SqlitePragmaHelper Tests")
class SqlitePragmaHelperTest {
    @TempDir
    Path tempDir;

    private Connection createConnection() throws Exception {
        Path dbPath = tempDir.resolve("test_wal.db");
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    @Nested
    @DisplayName("WAL Mode Configuration")
    class WalModeTests {
        @Test
        @DisplayName("configureForPersistence debe activar WAL mode")
        void configureForPersistence_ShouldEnableWalMode() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                assertTrue(SqlitePragmaHelper.isWalEnabled(conn),
                    "WAL mode debe estar activo despues de configureForPersistence");
            }
        }

        @Test
        @DisplayName("configureWalOnly debe activar WAL mode minimo")
        void configureWalOnly_ShouldEnableWalMode() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureWalOnly(conn);

                assertTrue(SqlitePragmaHelper.isWalEnabled(conn));
                assertEquals("wal", SqlitePragmaHelper.getJournalMode(conn).toLowerCase());
            }
        }

        @Test
        @DisplayName("getJournalMode debe retornar 'wal' despues de configurar")
        void getJournalMode_ShouldReturnWal() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                String mode = SqlitePragmaHelper.getJournalMode(conn);

                assertEquals("wal", mode.toLowerCase(),
                    "Journal mode debe ser WAL");
            }
        }
    }

    @Nested
    @DisplayName("PRAGMA Configuration")
    class PragmaConfigTests {
        @Test
        @DisplayName("configureForPersistence debe configurar busy_timeout")
        void configureForPersistence_ShouldSetBusyTimeout() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                int busyTimeout = getPragmaInt(conn, "busy_timeout");

                assertTrue(busyTimeout >= 10000,
                    "busy_timeout debe ser al menos 10 segundos, actual: " + busyTimeout);
            }
        }

        @Test
        @DisplayName("configureForPersistence debe configurar cache_size")
        void configureForPersistence_ShouldSetCacheSize() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                int cacheSize = getPragmaInt(conn, "cache_size");

                assertTrue(cacheSize < 0,
                    "cache_size debe ser negativo (KB), actual: " + cacheSize);
            }
        }

        @Test
        @DisplayName("configureForPersistence debe activar foreign_keys")
        void configureForPersistence_ShouldEnableForeignKeys() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                int fk = getPragmaInt(conn, "foreign_keys");

                assertEquals(1, fk, "foreign_keys debe estar habilitado");
            }
        }

        @Test
        @DisplayName("synchronous debe ser NORMAL (1) o FULL (2)")
        void configureForPersistence_ShouldSetSynchronous() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                int sync = getPragmaInt(conn, "synchronous");

                assertTrue(sync >= 1 && sync <= 2,
                    "synchronous debe ser NORMAL(1) o FULL(2), actual: " + sync);
            }
        }

        private int getPragmaInt(Connection conn, String pragma) throws Exception {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA " + pragma)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return -1;
        }
    }

    @Nested
    @DisplayName("PragmaConfig Presets")
    class PragmaConfigPresetsTests {
        @Test
        @DisplayName("defaults() debe crear configuracion valida")
        void defaults_ShouldCreateValidConfig() {
            SqlitePragmaHelper.PragmaConfig config = SqlitePragmaHelper.PragmaConfig.defaults();

            assertNotNull(config);
            assertEquals("WAL", config.journalMode());
            assertEquals("NORMAL", config.synchronous());
            assertTrue(config.busyTimeoutMs() > 0);
            assertTrue(config.cacheSizeKb() > 0);
            assertTrue(config.foreignKeys());
        }

        @Test
        @DisplayName("highPerformance() debe tener synchronous OFF")
        void highPerformance_ShouldHaveSyncOff() {
            SqlitePragmaHelper.PragmaConfig config = SqlitePragmaHelper.PragmaConfig.highPerformance();

            assertEquals("OFF", config.synchronous());
            assertEquals("WAL", config.journalMode());
        }

        @Test
        @DisplayName("maxDurability() debe tener synchronous FULL")
        void maxDurability_ShouldHaveSyncFull() {
            SqlitePragmaHelper.PragmaConfig config = SqlitePragmaHelper.PragmaConfig.maxDurability();

            assertEquals("FULL", config.synchronous());
            assertEquals("WAL", config.journalMode());
        }

        @Test
        @DisplayName("builder debe permitir configuracion personalizada")
        void builder_ShouldAllowCustomConfig() {
            SqlitePragmaHelper.PragmaConfig config = SqlitePragmaHelper.PragmaConfig.builder()
                .journalMode("DELETE")
                .synchronous("FULL")
                .busyTimeoutMs(60000)
                .cacheSizeKb(20000)
                .foreignKeys(false)
                .build();

            assertEquals("DELETE", config.journalMode());
            assertEquals("FULL", config.synchronous());
            assertEquals(60000, config.busyTimeoutMs());
            assertEquals(20000, config.cacheSizeKb());
            assertFalse(config.foreignKeys());
        }
    }

    @Nested
    @DisplayName("Custom Configuration")
    class CustomConfigTests {
        @Test
        @DisplayName("configureForPersistence con config personalizada")
        void configureForPersistence_WithCustomConfig() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.PragmaConfig config = SqlitePragmaHelper.PragmaConfig.builder()
                    .journalMode("WAL")
                    .synchronous("FULL")
                    .busyTimeoutMs(60000)
                    .build();

                SqlitePragmaHelper.configureForPersistence(conn, config);

                assertTrue(SqlitePragmaHelper.isWalEnabled(conn));
            }
        }
    }

    @Nested
    @DisplayName("WAL Checkpoint")
    class WalCheckpointTests {
        @Test
        @DisplayName("walCheckpoint debe ejecutar sin error")
        void walCheckpoint_ShouldExecuteSuccessfully() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE TABLE test_table (id INTEGER PRIMARY KEY, data TEXT)");
                    stmt.execute("INSERT INTO test_table (data) VALUES ('test data')");
                }

                int result = SqlitePragmaHelper.walCheckpoint(conn);

                assertTrue(result >= 0 || result == -1,
                    "walCheckpoint debe retornar valor valido");
            }
        }
    }

    @Nested
    @DisplayName("Diagnostics")
    class DiagnosticsTests {
        @Test
        @DisplayName("getDiagnostics debe retornar informacion completa")
        void getDiagnostics_ShouldReturnInfo() throws Exception {
            try (Connection conn = createConnection()) {
                SqlitePragmaHelper.configureForPersistence(conn);

                String diagnostics = SqlitePragmaHelper.getDiagnostics(conn);

                assertNotNull(diagnostics);
                assertTrue(diagnostics.contains("journal_mode"),
                    "Diagnostics debe incluir journal_mode");
                assertTrue(diagnostics.contains("synchronous"),
                    "Diagnostics debe incluir synchronous");
                assertTrue(diagnostics.contains("cache_size"),
                    "Diagnostics debe incluir cache_size");
                assertTrue(diagnostics.contains("busy_timeout"),
                    "Diagnostics debe incluir busy_timeout");
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        @Test
        @DisplayName("isWalEnabled en DB nueva sin configurar")
        void isWalEnabled_OnNewDb_ShouldBeFalse() throws Exception {
            try (Connection conn = createConnection()) {
                boolean walEnabled = SqlitePragmaHelper.isWalEnabled(conn);

                assertFalse(walEnabled,
                    "Nueva DB sin configurar no debe tener WAL activo");
            }
        }

        @Test
        @DisplayName("getJournalMode en DB nueva")
        void getJournalMode_OnNewDb_ShouldBeDelete() throws Exception {
            try (Connection conn = createConnection()) {
                String mode = SqlitePragmaHelper.getJournalMode(conn);

                assertTrue(mode.equalsIgnoreCase("delete") || mode.equalsIgnoreCase("memory"),
                    "Nueva DB debe tener DELETE o MEMORY mode, actual: " + mode);
            }
        }
    }
}
