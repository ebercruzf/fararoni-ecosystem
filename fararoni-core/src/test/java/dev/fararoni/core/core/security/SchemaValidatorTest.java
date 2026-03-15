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
package dev.fararoni.core.core.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SchemaValidator Tests")
class SchemaValidatorTest {
    @TempDir
    Path tempDir;

    private Connection connection;
    private SchemaValidator validator;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaValidator.resetForTesting();
        validator = SchemaValidator.getInstance();
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        SchemaValidator.resetForTesting();
    }

    @Nested
    @DisplayName("Singleton Pattern")
    class SingletonTests {
        @Test
        @DisplayName("getInstance debe retornar la misma instancia")
        void getInstance_ShouldReturnSameInstance() {
            SchemaValidator v1 = SchemaValidator.getInstance();
            SchemaValidator v2 = SchemaValidator.getInstance();

            assertSame(v1, v2);
        }

        @Test
        @DisplayName("resetForTesting debe crear nueva instancia")
        void resetForTesting_ShouldCreateNewInstance() {
            SchemaValidator v1 = SchemaValidator.getInstance();
            SchemaValidator.resetForTesting();
            SchemaValidator v2 = SchemaValidator.getInstance();

            assertNotSame(v1, v2);
        }
    }

    @Nested
    @DisplayName("Table Validation")
    class TableValidationTests {
        @Test
        @DisplayName("debe detectar tabla faltante")
        void shouldDetectMissingTable() {
            var result = validator.validateTable(connection, "interactions");

            assertFalse(result.valid());
            assertEquals(1, result.issues().size());
            assertEquals(SchemaValidator.SchemaIssueType.MISSING_TABLE, result.issues().get(0).type());
        }

        @Test
        @DisplayName("debe validar tabla existente con esquema correcto")
        void shouldValidateCorrectSchema() throws SQLException {
            createInteractionsTable();

            var result = validator.validateTable(connection, "interactions");

            assertTrue(result.valid(), "Validation issues: " + result.issues());
            assertTrue(result.issues().isEmpty());
        }

        @Test
        @DisplayName("debe detectar columna faltante")
        void shouldDetectMissingColumn() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE interactions (
                        id TEXT PRIMARY KEY,
                        timestamp INTEGER NOT NULL,
                        prompt TEXT NOT NULL,
                        response TEXT NOT NULL
                    )
                """);
            }

            var result = validator.validateTable(connection, "interactions");

            assertFalse(result.valid());
            assertTrue(result.issues().stream()
                    .anyMatch(i -> i.type() == SchemaValidator.SchemaIssueType.MISSING_COLUMN ||
                                   i.type() == SchemaValidator.SchemaIssueType.MISSING_CRITICAL_COLUMN));
        }

        @Test
        @DisplayName("debe detectar columna inesperada (posible manipulación)")
        void shouldDetectUnexpectedColumn() throws SQLException {
            createInteractionsTable();
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE interactions ADD COLUMN malicious_column TEXT");
            }

            var result = validator.validateTable(connection, "interactions");

            assertTrue(result.issues().stream()
                    .anyMatch(i -> i.type() == SchemaValidator.SchemaIssueType.UNEXPECTED_COLUMN));
        }

        @Test
        @DisplayName("debe detectar índice faltante")
        void shouldDetectMissingIndex() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE interactions (
                        id TEXT PRIMARY KEY,
                        timestamp INTEGER NOT NULL,
                        prompt TEXT NOT NULL,
                        response TEXT NOT NULL,
                        model_config TEXT,
                        rating INTEGER,
                        duration_ms INTEGER NOT NULL,
                        tokens_in INTEGER NOT NULL,
                        tokens_out INTEGER NOT NULL,
                        context_type TEXT,
                        files_count INTEGER DEFAULT 0,
                        thinking_used BOOLEAN DEFAULT FALSE,
                        error_occurred BOOLEAN DEFAULT FALSE,
                        feedback_timestamp INTEGER,
                        session_id TEXT,
                        signature_hash TEXT
                    )
                """);
            }

            var result = validator.validateTable(connection, "interactions");

            assertTrue(result.issues().stream()
                    .anyMatch(i -> i.type() == SchemaValidator.SchemaIssueType.MISSING_INDEX));
        }
    }

    @Nested
    @DisplayName("Multiple Table Validation")
    class MultipleTableValidationTests {
        @Test
        @DisplayName("debe validar múltiples tablas")
        void shouldValidateMultipleTables() throws SQLException {
            createInteractionsTable();
            createQuarantineTable();

            var result = validator.validateSchema(connection, Set.of("interactions", "quarantined_messages"));

            assertTrue(result.valid(), "Issues: " + result.issues());
            assertEquals(2, result.validatedTables().size());
        }

        @Test
        @DisplayName("debe reportar tabla desconocida")
        void shouldReportUnknownTable() {
            var result = validator.validateSchema(connection, Set.of("unknown_table"));

            assertFalse(result.valid());
            assertTrue(result.issues().stream()
                    .anyMatch(i -> i.type() == SchemaValidator.SchemaIssueType.UNKNOWN_TABLE));
        }
    }

    @Nested
    @DisplayName("Schema Repair")
    class RepairTests {
        @Test
        @DisplayName("debe agregar columna faltante")
        void shouldAddMissingColumn() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE interactions (
                        id TEXT PRIMARY KEY,
                        timestamp INTEGER NOT NULL,
                        prompt TEXT NOT NULL,
                        response TEXT NOT NULL,
                        model_config TEXT,
                        rating INTEGER,
                        duration_ms INTEGER NOT NULL,
                        tokens_in INTEGER NOT NULL,
                        tokens_out INTEGER NOT NULL,
                        context_type TEXT,
                        files_count INTEGER DEFAULT 0,
                        thinking_used BOOLEAN DEFAULT FALSE,
                        error_occurred BOOLEAN DEFAULT FALSE,
                        feedback_timestamp INTEGER,
                        session_id TEXT
                    )
                """);
                stmt.execute("CREATE INDEX idx_timestamp ON interactions(timestamp)");
                stmt.execute("CREATE INDEX idx_rating ON interactions(rating)");
                stmt.execute("CREATE INDEX idx_session ON interactions(session_id)");
            }

            var validationResult = validator.validateTable(connection, "interactions");
            assertFalse(validationResult.valid());

            var repairResult = validator.attemptRepair(connection, validationResult.issues());

            assertFalse(repairResult.repaired().isEmpty());

            var revalidation = validator.validateTable(connection, "interactions");
            assertTrue(revalidation.valid(), "Still has issues: " + revalidation.issues());
        }

        @Test
        @DisplayName("debe crear tabla quarantined_messages")
        void shouldCreateQuarantineTable() throws SQLException {
            var beforeResult = validator.validateTable(connection, "quarantined_messages");
            assertFalse(beforeResult.valid());

            var repairResult = validator.attemptRepair(connection, beforeResult.issues());
            assertTrue(repairResult.isFullySuccessful());

            var afterResult = validator.validateTable(connection, "quarantined_messages");
            assertTrue(afterResult.valid(), "Issues: " + afterResult.issues());
        }

        @Test
        @DisplayName("debe crear índice faltante")
        void shouldCreateMissingIndex() throws SQLException {
            createInteractionsTableWithoutIndexes();

            var validationResult = validator.validateTable(connection, "interactions");
            List<SchemaValidator.SchemaIssue> indexIssues = validationResult.issues().stream()
                    .filter(i -> i.type() == SchemaValidator.SchemaIssueType.MISSING_INDEX)
                    .toList();

            assertFalse(indexIssues.isEmpty());

            validator.attemptRepair(connection, indexIssues);

            var afterResult = validator.validateTable(connection, "interactions");
            long remainingIndexIssues = afterResult.issues().stream()
                    .filter(i -> i.type() == SchemaValidator.SchemaIssueType.MISSING_INDEX)
                    .count();

            assertTrue(remainingIndexIssues < indexIssues.size(), "Should have fewer index issues");
        }
    }

    @Nested
    @DisplayName("Validation Results")
    class ResultTests {
        @Test
        @DisplayName("hasCriticalIssues debe detectar issues críticos")
        void hasCriticalIssues_ShouldDetectCritical() throws SQLException {
            var result = validator.validateTable(connection, "interactions");

            assertTrue(result.hasCriticalIssues());
        }

        @Test
        @DisplayName("getSummary debe generar resumen legible")
        void getSummary_ShouldGenerateReadableSummary() throws SQLException {
            createInteractionsTable();
            var result = validator.validateTable(connection, "interactions");

            String summary = result.getSummary();

            assertNotNull(summary);
            assertTrue(summary.contains("valid") || summary.contains("invalid"));
        }

        @Test
        @DisplayName("getStatsSummary debe mostrar estadísticas")
        void getStatsSummary_ShouldShowStats() throws SQLException {
            createInteractionsTable();
            validator.validateTable(connection, "interactions");

            String stats = validator.getStatsSummary();

            assertNotNull(stats);
            assertTrue(stats.contains("validations"));
        }
    }

    @Nested
    @DisplayName("Type Compatibility")
    class TypeCompatibilityTests {
        @Test
        @DisplayName("BOOLEAN debe ser compatible con INTEGER en SQLite")
        void booleanShouldBeCompatibleWithInteger() throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE interactions (
                        id TEXT PRIMARY KEY,
                        timestamp INTEGER NOT NULL,
                        prompt TEXT NOT NULL,
                        response TEXT NOT NULL,
                        model_config TEXT,
                        rating INTEGER,
                        duration_ms INTEGER NOT NULL,
                        tokens_in INTEGER NOT NULL,
                        tokens_out INTEGER NOT NULL,
                        context_type TEXT,
                        files_count INTEGER DEFAULT 0,
                        thinking_used INTEGER DEFAULT 0,
                        error_occurred INTEGER DEFAULT 0,
                        feedback_timestamp INTEGER,
                        session_id TEXT,
                        signature_hash TEXT
                    )
                """);
                stmt.execute("CREATE INDEX idx_timestamp ON interactions(timestamp)");
                stmt.execute("CREATE INDEX idx_rating ON interactions(rating)");
                stmt.execute("CREATE INDEX idx_session ON interactions(session_id)");
            }

            var result = validator.validateTable(connection, "interactions");

            long typeIssues = result.issues().stream()
                    .filter(i -> i.type() == SchemaValidator.SchemaIssueType.TYPE_MISMATCH)
                    .count();

            assertEquals(0, typeIssues, "BOOLEAN should be compatible with INTEGER");
        }
    }

    private void createInteractionsTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE interactions (
                    id TEXT PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    prompt TEXT NOT NULL,
                    response TEXT NOT NULL,
                    model_config TEXT,
                    rating INTEGER,
                    duration_ms INTEGER NOT NULL,
                    tokens_in INTEGER NOT NULL,
                    tokens_out INTEGER NOT NULL,
                    context_type TEXT,
                    files_count INTEGER DEFAULT 0,
                    thinking_used BOOLEAN DEFAULT FALSE,
                    error_occurred BOOLEAN DEFAULT FALSE,
                    feedback_timestamp INTEGER,
                    session_id TEXT,
                    signature_hash TEXT
                )
            """);
            stmt.execute("CREATE INDEX idx_timestamp ON interactions(timestamp)");
            stmt.execute("CREATE INDEX idx_rating ON interactions(rating)");
            stmt.execute("CREATE INDEX idx_session ON interactions(session_id)");
        }
    }

    private void createInteractionsTableWithoutIndexes() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE interactions (
                    id TEXT PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    prompt TEXT NOT NULL,
                    response TEXT NOT NULL,
                    model_config TEXT,
                    rating INTEGER,
                    duration_ms INTEGER NOT NULL,
                    tokens_in INTEGER NOT NULL,
                    tokens_out INTEGER NOT NULL,
                    context_type TEXT,
                    files_count INTEGER DEFAULT 0,
                    thinking_used BOOLEAN DEFAULT FALSE,
                    error_occurred BOOLEAN DEFAULT FALSE,
                    feedback_timestamp INTEGER,
                    session_id TEXT,
                    signature_hash TEXT
                )
            """);
        }
    }

    private void createQuarantineTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE quarantined_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    original_id TEXT NOT NULL,
                    table_source TEXT NOT NULL,
                    quarantine_reason TEXT NOT NULL,
                    original_content TEXT NOT NULL,
                    expected_hash TEXT,
                    actual_hash TEXT,
                    quarantine_timestamp INTEGER NOT NULL,
                    reviewed BOOLEAN DEFAULT FALSE,
                    reviewer_notes TEXT
                )
            """);
            stmt.execute("CREATE INDEX idx_quarantine_timestamp ON quarantined_messages(quarantine_timestamp)");
            stmt.execute("CREATE INDEX idx_reviewed ON quarantined_messages(reviewed)");
        }
    }
}
