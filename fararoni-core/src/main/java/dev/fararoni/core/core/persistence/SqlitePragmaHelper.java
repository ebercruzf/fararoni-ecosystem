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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SqlitePragmaHelper {
    private static final int DEFAULT_BUSY_TIMEOUT_MS = 30000;
    private static final int DEFAULT_CACHE_SIZE_KB = 10000;
    private static final String DEFAULT_JOURNAL_MODE = "WAL";
    private static final String DEFAULT_SYNCHRONOUS = "NORMAL";
    private static final String DEFAULT_TEMP_STORE = "MEMORY";

    private SqlitePragmaHelper() {
    }

    public static void configureForPersistence(Connection conn) throws SQLException {
        configureForPersistence(conn, PragmaConfig.defaults());
    }

    public static void configureForPersistence(Connection conn, PragmaConfig config) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = " + config.journalMode());

            stmt.execute("PRAGMA synchronous = " + config.synchronous());

            stmt.execute("PRAGMA busy_timeout = " + config.busyTimeoutMs());

            stmt.execute("PRAGMA cache_size = -" + config.cacheSizeKb());

            stmt.execute("PRAGMA temp_store = " + config.tempStore());

            if (config.foreignKeys()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }

            if (config.autoVacuum()) {
                stmt.execute("PRAGMA auto_vacuum = INCREMENTAL");
            }
        }
    }

    public static void configureWalOnly(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA synchronous = NORMAL");
            stmt.execute("PRAGMA busy_timeout = " + DEFAULT_BUSY_TIMEOUT_MS);
        }
    }

    public static boolean isWalEnabled(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA journal_mode")) {
            if (rs.next()) {
                return "wal".equalsIgnoreCase(rs.getString(1));
            }
        }
        return false;
    }

    public static String getJournalMode(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA journal_mode")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return "unknown";
    }

    public static int walCheckpoint(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }

    public static String getDiagnostics(Connection conn) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("SQLite Diagnostics:\n");

        try (Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("PRAGMA journal_mode");
            if (rs.next()) sb.append("  journal_mode: ").append(rs.getString(1)).append("\n");
            rs.close();

            rs = stmt.executeQuery("PRAGMA synchronous");
            if (rs.next()) sb.append("  synchronous: ").append(rs.getInt(1)).append("\n");
            rs.close();

            rs = stmt.executeQuery("PRAGMA cache_size");
            if (rs.next()) sb.append("  cache_size: ").append(rs.getInt(1)).append("\n");
            rs.close();

            rs = stmt.executeQuery("PRAGMA page_size");
            if (rs.next()) sb.append("  page_size: ").append(rs.getInt(1)).append(" bytes\n");
            rs.close();

            rs = stmt.executeQuery("PRAGMA busy_timeout");
            if (rs.next()) sb.append("  busy_timeout: ").append(rs.getInt(1)).append(" ms\n");
            rs.close();
        }

        return sb.toString();
    }

    public record PragmaConfig(
        String journalMode,
        String synchronous,
        int busyTimeoutMs,
        int cacheSizeKb,
        String tempStore,
        boolean foreignKeys,
        boolean autoVacuum
    ) {
        public static PragmaConfig defaults() {
            return new PragmaConfig(
                DEFAULT_JOURNAL_MODE,
                DEFAULT_SYNCHRONOUS,
                DEFAULT_BUSY_TIMEOUT_MS,
                DEFAULT_CACHE_SIZE_KB,
                DEFAULT_TEMP_STORE,
                true,
                true
            );
        }

        public static PragmaConfig highPerformance() {
            return new PragmaConfig(
                "WAL",
                "OFF",
                10000,
                20000,
                "MEMORY",
                false,
                false
            );
        }

        public static PragmaConfig maxDurability() {
            return new PragmaConfig(
                "WAL",
                "FULL",
                60000,
                5000,
                "FILE",
                true,
                true
            );
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String journalMode = DEFAULT_JOURNAL_MODE;
            private String synchronous = DEFAULT_SYNCHRONOUS;
            private int busyTimeoutMs = DEFAULT_BUSY_TIMEOUT_MS;
            private int cacheSizeKb = DEFAULT_CACHE_SIZE_KB;
            private String tempStore = DEFAULT_TEMP_STORE;
            private boolean foreignKeys = true;
            private boolean autoVacuum = true;

            public Builder journalMode(String mode) {
                this.journalMode = mode;
                return this;
            }

            public Builder synchronous(String sync) {
                this.synchronous = sync;
                return this;
            }

            public Builder busyTimeoutMs(int timeout) {
                this.busyTimeoutMs = timeout;
                return this;
            }

            public Builder cacheSizeKb(int size) {
                this.cacheSizeKb = size;
                return this;
            }

            public Builder tempStore(String store) {
                this.tempStore = store;
                return this;
            }

            public Builder foreignKeys(boolean enabled) {
                this.foreignKeys = enabled;
                return this;
            }

            public Builder autoVacuum(boolean enabled) {
                this.autoVacuum = enabled;
                return this;
            }

            public PragmaConfig build() {
                return new PragmaConfig(
                    journalMode, synchronous, busyTimeoutMs,
                    cacheSizeKb, tempStore, foreignKeys, autoVacuum
                );
            }
        }
    }
}
