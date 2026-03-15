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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SchemaValidator {
    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

    private static volatile SchemaValidator instance;
    private static final Object LOCK = new Object();

    private final Map<String, TableSchema> expectedSchemas;

    private long validationsPerformed = 0;
    private long validationsFailed = 0;
    private long migrationsExecuted = 0;

    private SchemaValidator() {
        this.expectedSchemas = initializeExpectedSchemas();
        log.info("[SchemaValidator] Initialized with {} table schemas", expectedSchemas.size());
    }

    public static SchemaValidator getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new SchemaValidator();
                }
            }
        }
        return instance;
    }

    private Map<String, TableSchema> initializeExpectedSchemas() {
        Map<String, TableSchema> schemas = new HashMap<>();

        schemas.put("interactions", new TableSchema("interactions", List.of(
                new ColumnDef("id", "TEXT", true, false),
                new ColumnDef("timestamp", "INTEGER", false, true),
                new ColumnDef("prompt", "TEXT", false, true),
                new ColumnDef("response", "TEXT", false, true),
                new ColumnDef("model_config", "TEXT", false, false),
                new ColumnDef("rating", "INTEGER", false, false),
                new ColumnDef("duration_ms", "INTEGER", false, true),
                new ColumnDef("tokens_in", "INTEGER", false, true),
                new ColumnDef("tokens_out", "INTEGER", false, true),
                new ColumnDef("context_type", "TEXT", false, false),
                new ColumnDef("files_count", "INTEGER", false, false),
                new ColumnDef("thinking_used", "BOOLEAN", false, false),
                new ColumnDef("error_occurred", "BOOLEAN", false, false),
                new ColumnDef("feedback_timestamp", "INTEGER", false, false),
                new ColumnDef("session_id", "TEXT", false, false),
                new ColumnDef("signature_hash", "TEXT", false, false)
        ), List.of("idx_timestamp", "idx_rating", "idx_session")));

        schemas.put("semantic_cache", new TableSchema("semantic_cache", List.of(
                new ColumnDef("id", "INTEGER", true, false),
                new ColumnDef("prompt_text", "TEXT", false, true),
                new ColumnDef("prompt_hash", "TEXT", false, true),
                new ColumnDef("response_text", "TEXT", false, true),
                new ColumnDef("embedding_json", "TEXT", false, true),
                new ColumnDef("embedding_dimensions", "INTEGER", false, false),
                new ColumnDef("similarity_threshold", "REAL", false, false),
                new ColumnDef("hit_count", "INTEGER", false, false),
                new ColumnDef("avg_similarity", "REAL", false, false),
                new ColumnDef("quality_score", "REAL", false, false),
                new ColumnDef("timestamp", "INTEGER", false, true),
                new ColumnDef("last_hit_timestamp", "INTEGER", false, false),
                new ColumnDef("expiration_timestamp", "INTEGER", false, false),
                new ColumnDef("source_interaction_id", "TEXT", false, false),
                new ColumnDef("context_type", "TEXT", false, false),
                new ColumnDef("tokens_saved_total", "INTEGER", false, false),
                new ColumnDef("model_version", "TEXT", false, false),
                new ColumnDef("tenant_id", "TEXT", false, false),
                new ColumnDef("tags", "TEXT", false, false),
                new ColumnDef("compression_ratio", "REAL", false, false),
                new ColumnDef("source_content_hash", "TEXT", false, false)
        ), List.of("idx_prompt_hash", "idx_timestamp", "idx_tenant")));

        schemas.put("quarantined_messages", new TableSchema("quarantined_messages", List.of(
                new ColumnDef("id", "INTEGER", true, false),
                new ColumnDef("original_id", "TEXT", false, true),
                new ColumnDef("table_source", "TEXT", false, true),
                new ColumnDef("quarantine_reason", "TEXT", false, true),
                new ColumnDef("original_content", "TEXT", false, true),
                new ColumnDef("expected_hash", "TEXT", false, false),
                new ColumnDef("actual_hash", "TEXT", false, false),
                new ColumnDef("quarantine_timestamp", "INTEGER", false, true),
                new ColumnDef("reviewed", "BOOLEAN", false, false),
                new ColumnDef("reviewer_notes", "TEXT", false, false)
        ), List.of("idx_quarantine_timestamp", "idx_reviewed")));

        return Collections.unmodifiableMap(schemas);
    }

    public ValidationResult validateSchema(Connection connection) {
        return validateSchema(connection, expectedSchemas.keySet());
    }

    public ValidationResult validateSchema(Connection connection, Set<String> tableNames) {
        validationsPerformed++;

        List<SchemaIssue> issues = new ArrayList<>();
        List<String> validatedTables = new ArrayList<>();

        for (String tableName : tableNames) {
            TableSchema expected = expectedSchemas.get(tableName);
            if (expected == null) {
                issues.add(new SchemaIssue(
                        tableName, null, SchemaIssueType.UNKNOWN_TABLE,
                        "No schema definition found for table: " + tableName
                ));
                continue;
            }

            try {
                if (!tableExists(connection, tableName)) {
                    issues.add(new SchemaIssue(
                            tableName, null, SchemaIssueType.MISSING_TABLE,
                            "Table does not exist: " + tableName
                    ));
                    continue;
                }

                issues.addAll(validateColumns(connection, expected));

                issues.addAll(validateIndexes(connection, expected));

                validatedTables.add(tableName);
            } catch (SQLException e) {
                issues.add(new SchemaIssue(
                        tableName, null, SchemaIssueType.VALIDATION_ERROR,
                        "Error validating table: " + e.getMessage()
                ));
            }
        }

        boolean valid = issues.isEmpty();
        if (!valid) {
            validationsFailed++;
            log.warn("[SchemaValidator] Schema validation failed with {} issues", issues.size());
        } else {
            log.info("[SchemaValidator] Schema validation passed for {} tables", validatedTables.size());
        }

        return new ValidationResult(valid, issues, validatedTables);
    }

    public ValidationResult validateTable(Connection connection, String tableName) {
        return validateSchema(connection, Set.of(tableName));
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<SchemaIssue> validateColumns(Connection connection, TableSchema expected) throws SQLException {
        List<SchemaIssue> issues = new ArrayList<>();
        Map<String, ColumnInfo> actualColumns = getColumnInfo(connection, expected.tableName());

        for (ColumnDef expectedCol : expected.columns()) {
            ColumnInfo actual = actualColumns.get(expectedCol.name().toLowerCase());

            if (actual == null) {
                SchemaIssueType type = expectedCol.required()
                        ? SchemaIssueType.MISSING_CRITICAL_COLUMN
                        : SchemaIssueType.MISSING_COLUMN;
                issues.add(new SchemaIssue(
                        expected.tableName(), expectedCol.name(), type,
                        "Column missing: " + expectedCol.name() + " (expected type: " + expectedCol.type() + ")"
                ));
            } else {
                if (!isTypeCompatible(expectedCol.type(), actual.type())) {
                    issues.add(new SchemaIssue(
                            expected.tableName(), expectedCol.name(), SchemaIssueType.TYPE_MISMATCH,
                            String.format("Type mismatch for %s: expected %s, found %s",
                                    expectedCol.name(), expectedCol.type(), actual.type())
                    ));
                }

                if (expectedCol.primaryKey() && !actual.isPk()) {
                    issues.add(new SchemaIssue(
                            expected.tableName(), expectedCol.name(), SchemaIssueType.CONSTRAINT_MISMATCH,
                            "Expected primary key on column: " + expectedCol.name()
                    ));
                }
            }
        }

        Set<String> expectedNames = new HashSet<>();
        for (ColumnDef col : expected.columns()) {
            expectedNames.add(col.name().toLowerCase());
        }

        for (String actualCol : actualColumns.keySet()) {
            if (!expectedNames.contains(actualCol.toLowerCase())) {
                issues.add(new SchemaIssue(
                        expected.tableName(), actualCol, SchemaIssueType.UNEXPECTED_COLUMN,
                        "Unexpected column found (possible schema tampering): " + actualCol
                ));
            }
        }

        return issues;
    }

    private Map<String, ColumnInfo> getColumnInfo(Connection connection, String tableName) throws SQLException {
        Map<String, ColumnInfo> columns = new LinkedHashMap<>();
        String sql = "PRAGMA table_info(" + tableName + ")";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                String type = rs.getString("type");
                boolean notNull = rs.getInt("notnull") == 1;
                boolean pk = rs.getInt("pk") == 1;
                columns.put(name.toLowerCase(), new ColumnInfo(name, type, notNull, pk));
            }
        }

        return columns;
    }

    private boolean isTypeCompatible(String expected, String actual) {
        if (expected == null || actual == null) return true;

        String expNorm = expected.toUpperCase().trim();
        String actNorm = actual.toUpperCase().trim();

        if (expNorm.equals(actNorm)) return true;

        if (expNorm.equals("BOOLEAN") && actNorm.contains("INT")) return true;
        if (expNorm.equals("INTEGER") && actNorm.contains("INT")) return true;
        if (expNorm.equals("REAL") && (actNorm.contains("REAL") || actNorm.contains("FLOAT") || actNorm.contains("DOUBLE"))) return true;
        if (expNorm.equals("TEXT") && (actNorm.contains("TEXT") || actNorm.contains("VARCHAR") || actNorm.contains("CHAR"))) return true;

        return false;
    }

    private List<SchemaIssue> validateIndexes(Connection connection, TableSchema expected) throws SQLException {
        List<SchemaIssue> issues = new ArrayList<>();
        Set<String> actualIndexes = getIndexNames(connection, expected.tableName());

        for (String expectedIndex : expected.expectedIndexes()) {
            if (!actualIndexes.contains(expectedIndex)) {
                issues.add(new SchemaIssue(
                        expected.tableName(), expectedIndex, SchemaIssueType.MISSING_INDEX,
                        "Index missing: " + expectedIndex
                ));
            }
        }

        return issues;
    }

    private Set<String> getIndexNames(Connection connection, String tableName) throws SQLException {
        Set<String> indexes = new HashSet<>();
        String sql = "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name=?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, tableName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null && !name.startsWith("sqlite_")) {
                        indexes.add(name);
                    }
                }
            }
        }

        return indexes;
    }

    public MigrationResult attemptRepair(Connection connection, List<SchemaIssue> issues) {
        List<String> repaired = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (SchemaIssue issue : issues) {
            try {
                boolean success = repairIssue(connection, issue);
                if (success) {
                    repaired.add(issue.description());
                    migrationsExecuted++;
                } else {
                    failed.add(issue.description());
                }
            } catch (SQLException e) {
                failed.add(issue.description() + " - Error: " + e.getMessage());
            }
        }

        return new MigrationResult(repaired, failed);
    }

    private boolean repairIssue(Connection connection, SchemaIssue issue) throws SQLException {
        switch (issue.type()) {
            case MISSING_COLUMN:
            case MISSING_CRITICAL_COLUMN:
                return addMissingColumn(connection, issue);
            case MISSING_INDEX:
                return addMissingIndex(connection, issue);
            case MISSING_TABLE:
                return createTable(connection, issue.tableName());
            default:
                log.warn("[SchemaValidator] Cannot auto-repair issue type: {}", issue.type());
                return false;
        }
    }

    private boolean addMissingColumn(Connection connection, SchemaIssue issue) throws SQLException {
        TableSchema schema = expectedSchemas.get(issue.tableName());
        if (schema == null) return false;

        ColumnDef colDef = schema.columns().stream()
                .filter(c -> c.name().equalsIgnoreCase(issue.columnName()))
                .findFirst()
                .orElse(null);

        if (colDef == null) return false;

        String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s",
                issue.tableName(), colDef.name(), colDef.type());

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("[SchemaValidator] Added missing column: {}.{}", issue.tableName(), colDef.name());
            return true;
        }
    }

    private boolean addMissingIndex(Connection connection, SchemaIssue issue) throws SQLException {
        String indexName = issue.columnName();
        String colGuess = indexName.replace("idx_", "");

        String sql = String.format("CREATE INDEX IF NOT EXISTS %s ON %s(%s)",
                indexName, issue.tableName(), colGuess);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("[SchemaValidator] Created missing index: {}", indexName);
            return true;
        }
    }

    private boolean createTable(Connection connection, String tableName) throws SQLException {
        if ("quarantined_messages".equals(tableName)) {
            String sql = """
                CREATE TABLE IF NOT EXISTS quarantined_messages (
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
                """;
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_quarantine_timestamp ON quarantined_messages(quarantine_timestamp)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_reviewed ON quarantined_messages(reviewed)");
                log.info("[SchemaValidator] Created table: quarantined_messages");
                return true;
            }
        }
        return false;
    }

    public String getStatsSummary() {
        return String.format(
                "[SchemaValidator] Stats: validations=%d, failures=%d, migrations=%d",
                validationsPerformed, validationsFailed, migrationsExecuted
        );
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    public record ColumnDef(
            String name,
            String type,
            boolean primaryKey,
            boolean required
    ) {}

    private record ColumnInfo(
            String name,
            String type,
            boolean notNull,
            boolean isPk
    ) {}

    public record TableSchema(
            String tableName,
            List<ColumnDef> columns,
            List<String> expectedIndexes
    ) {}

    public enum SchemaIssueType {
        MISSING_TABLE,
        MISSING_COLUMN,
        MISSING_CRITICAL_COLUMN,
        MISSING_INDEX,
        TYPE_MISMATCH,
        CONSTRAINT_MISMATCH,
        UNEXPECTED_COLUMN,
        UNKNOWN_TABLE,
        VALIDATION_ERROR
    }

    public record SchemaIssue(
            String tableName,
            String columnName,
            SchemaIssueType type,
            String description
    ) {
        public boolean isCritical() {
            return type == SchemaIssueType.MISSING_CRITICAL_COLUMN ||
                   type == SchemaIssueType.MISSING_TABLE ||
                   type == SchemaIssueType.TYPE_MISMATCH;
        }
    }

    public record ValidationResult(
            boolean valid,
            List<SchemaIssue> issues,
            List<String> validatedTables
    ) {
        public boolean hasCriticalIssues() {
            return issues.stream().anyMatch(SchemaIssue::isCritical);
        }

        public String getSummary() {
            if (valid) {
                return String.format("Schema valid: %d tables validated", validatedTables.size());
            } else {
                long critical = issues.stream().filter(SchemaIssue::isCritical).count();
                return String.format("Schema invalid: %d issues (%d critical)", issues.size(), critical);
            }
        }
    }

    public record MigrationResult(
            List<String> repaired,
            List<String> failed
    ) {
        public boolean isFullySuccessful() {
            return failed.isEmpty();
        }

        public String getSummary() {
            return String.format("Migration: %d repaired, %d failed", repaired.size(), failed.size());
        }
    }
}
