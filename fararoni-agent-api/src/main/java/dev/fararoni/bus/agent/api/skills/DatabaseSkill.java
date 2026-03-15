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
package dev.fararoni.bus.agent.api.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.Sanitized;
import dev.fararoni.bus.agent.api.saga.SagaCapableSkill;
import dev.fararoni.bus.agent.api.security.AuditLog;
import dev.fararoni.bus.agent.api.security.RateLimit;
import dev.fararoni.bus.agent.api.security.RequiresRole;
import dev.fararoni.bus.agent.api.state.StatefulSkill;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Contract for database operations with stateful connections and Saga support.
 *
 * <p>This interface defines database operations that the AI agent can perform.
 * Unlike MCP's stateless model, FNL maintains persistent database connections
 * for performance. All mutating operations support automatic rollback.</p>
 *
 * <h2>Performance Advantage</h2>
 * <pre>
 * MCP (Stateless):
 *   Query 1: Connect → Auth → Execute → Close (200ms)
 *   Query 2: Connect → Auth → Execute → Close (200ms)
 *   ...
 *   100 queries = 20 seconds total
 *
 * FNL (Stateful):
 *   Connect once → Auth once → Execute 100 queries → Close
 *   100 queries = 2 seconds total (10x faster!)
 * </pre>
 *
 * <h2>Saga Compensation</h2>
 * <pre>
 * Agent: INSERT INTO users (name) VALUES ('Alice')
 *        ↓
 * FNL: 1. Execute INSERT, get generated ID: 42
 *      2. Return success + CompensationInstruction("DELETE FROM users WHERE id = 42")
 *        ↓
 * Later step fails...
 *        ↓
 * FNL: Execute compensation → DELETE → Row removed
 * </pre>
 *
 * <h2>Security</h2>
 * <ul>
 *   <li>SQL injection prevented via parameterized queries</li>
 *   <li>DDL operations require elevated role</li>
 *   <li>All queries logged for audit</li>
 *   <li>Connection strings sanitized</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Open connection (reused for multiple queries)
 * FNLResult<SessionHandle> conn = dbSkill.openSession("jdbc:postgresql://...");
 *
 * if (conn.success()) {
 *     String sessionId = conn.data().sessionId();
 *
 *     // Execute 100 queries on same connection (fast!)
 *     for (String query : queries) {
 *         FNLResult<QueryResult> result = dbSkill.query(sessionId, query);
 *     }
 *
 *     // Insert with automatic Saga
 *     FNLResult<ExecuteResult> insert = dbSkill.execute(sessionId,
 *         "INSERT INTO users (name, email) VALUES (?, ?)",
 *         List.of("Alice", "alice@example.com"));
 *
 *     // Close when done
 *     dbSkill.closeSession(sessionId);
 * }
 * }</pre>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see StatefulSkill
 * @see SagaCapableSkill
 */
public interface DatabaseSkill extends StatefulSkill, SagaCapableSkill {

    // ==================== Query Operations ====================

    /**
     * Executes a SELECT query and returns results.
     *
     * @param sessionId the database session
     * @param sql the SQL query (SELECT only)
     * @return result containing query results
     */
    @AgentAction(
        name = "query",
        description = "Executes a SELECT query and returns rows"
    )
    @RateLimit(calls = 100, period = 1, unit = TimeUnit.MINUTES)
    @AuditLog(severity = "INFO", category = "DB_QUERY")
    FNLResult<QueryResult> query(
        String sessionId,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.SQL) String sql
    );

    /**
     * Executes a parameterized SELECT query.
     *
     * @param sessionId the database session
     * @param sql the SQL query with ? placeholders
     * @param params parameter values
     * @return result containing query results
     */
    @AgentAction(
        name = "query_params",
        description = "Executes a parameterized SELECT query (safer)"
    )
    @RateLimit(calls = 100, period = 1, unit = TimeUnit.MINUTES)
    @AuditLog(severity = "INFO", category = "DB_QUERY")
    FNLResult<QueryResult> queryWithParams(
        String sessionId,
        String sql,
        List<Object> params
    );

    // ==================== Execute Operations (Saga-Enabled) ====================

    /**
     * Executes an INSERT/UPDATE/DELETE statement.
     *
     * <p>Returns compensation instruction for automatic rollback.</p>
     *
     * @param sessionId the database session
     * @param sql the SQL statement
     * @param params parameter values
     * @return result containing affected row count with compensation
     */
    @AgentAction(
        name = "execute",
        description = "Executes INSERT/UPDATE/DELETE with automatic rollback support"
    )
    @RateLimit(calls = 50, period = 1, unit = TimeUnit.MINUTES)
    @AuditLog(severity = "WARN", category = "DB_EXECUTE")
    FNLResult<ExecuteResult> execute(
        String sessionId,
        String sql,
        List<Object> params
    );

    /**
     * Executes a batch of statements in a transaction.
     *
     * <p>All statements succeed or all are rolled back.</p>
     *
     * @param sessionId the database session
     * @param statements list of SQL statements with params
     * @return result containing batch execution summary
     */
    @AgentAction(
        name = "execute_batch",
        description = "Executes multiple statements in a transaction"
    )
    @RequiresRole("db:batch")
    @AuditLog(severity = "WARN", category = "DB_BATCH")
    FNLResult<BatchResult> executeBatch(
        String sessionId,
        List<Statement> statements
    );

    // ==================== DDL Operations ====================

    /**
     * Executes a DDL statement (CREATE, ALTER, DROP).
     *
     * <p>Requires elevated permissions due to schema impact.</p>
     *
     * @param sessionId the database session
     * @param ddl the DDL statement
     * @return result indicating success
     */
    @AgentAction(
        name = "execute_ddl",
        description = "Executes schema modification (CREATE/ALTER/DROP)"
    )
    @RequiresRole("db:ddl")
    @AuditLog(severity = "CRITICAL", category = "DB_DDL")
    FNLResult<Void> executeDDL(
        String sessionId,
        @Sanitized(strategy = Sanitized.SanitizationStrategy.SQL) String ddl
    );

    // ==================== Transaction Control ====================

    /**
     * Begins a transaction.
     *
     * @param sessionId the database session
     * @return result indicating success
     */
    @AgentAction(
        name = "begin_transaction",
        description = "Starts a database transaction"
    )
    FNLResult<Void> beginTransaction(String sessionId);

    /**
     * Commits the current transaction.
     *
     * @param sessionId the database session
     * @return result indicating success
     */
    @AgentAction(
        name = "commit",
        description = "Commits the current transaction"
    )
    @AuditLog(severity = "INFO", category = "DB_TRANSACTION")
    FNLResult<Void> commit(String sessionId);

    /**
     * Rolls back the current transaction.
     *
     * @param sessionId the database session
     * @return result indicating success
     */
    @AgentAction(
        name = "rollback",
        description = "Rolls back the current transaction"
    )
    @AuditLog(severity = "WARN", category = "DB_TRANSACTION")
    FNLResult<Void> rollback(String sessionId);

    // ==================== Schema Inspection ====================

    /**
     * Lists all tables in the database.
     *
     * @param sessionId the database session
     * @param schema optional schema name filter
     * @return result containing table list
     */
    @AgentAction(
        name = "list_tables",
        description = "Lists all tables in the database"
    )
    FNLResult<List<TableInfo>> listTables(String sessionId, String schema);

    /**
     * Describes a table's structure.
     *
     * @param sessionId the database session
     * @param tableName the table name
     * @return result containing column information
     */
    @AgentAction(
        name = "describe_table",
        description = "Returns table structure (columns, types, constraints)"
    )
    FNLResult<TableDescription> describeTable(String sessionId, String tableName);

    /**
     * Gets the database metadata.
     *
     * @param sessionId the database session
     * @return result containing database info
     */
    @AgentAction(
        name = "database_info",
        description = "Returns database type, version, and settings"
    )
    FNLResult<DatabaseInfo> getDatabaseInfo(String sessionId);

    // ==================== Nested Types ====================

    /**
     * Result of a SELECT query.
     *
     * @param columns column names
     * @param rows list of rows (each row is a map of column→value)
     * @param rowCount number of rows returned
     * @param queryTimeMs query execution time
     */
    record QueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        long queryTimeMs
    ) {
        public boolean isEmpty() {
            return rows == null || rows.isEmpty();
        }
    }

    /**
     * Result of an INSERT/UPDATE/DELETE.
     *
     * @param affectedRows number of rows affected
     * @param generatedKeys auto-generated keys (for INSERT)
     * @param executionTimeMs execution time
     */
    record ExecuteResult(
        int affectedRows,
        List<Object> generatedKeys,
        long executionTimeMs
    ) {}

    /**
     * Result of a batch execution.
     *
     * @param totalStatements number of statements executed
     * @param successCount number of successful statements
     * @param totalAffectedRows total rows affected
     * @param executionTimeMs total execution time
     */
    record BatchResult(
        int totalStatements,
        int successCount,
        int totalAffectedRows,
        long executionTimeMs
    ) {
        public boolean allSucceeded() {
            return successCount == totalStatements;
        }
    }

    /**
     * A SQL statement with parameters.
     *
     * @param sql the SQL statement
     * @param params parameter values
     */
    record Statement(String sql, List<Object> params) {
        public static Statement of(String sql, Object... params) {
            return new Statement(sql, List.of(params));
        }
    }

    /**
     * Table information.
     *
     * @param name table name
     * @param schema schema name
     * @param type table type (TABLE, VIEW, etc.)
     * @param rowCountEstimate estimated row count
     */
    record TableInfo(
        String name,
        String schema,
        String type,
        long rowCountEstimate
    ) {}

    /**
     * Detailed table description.
     *
     * @param name table name
     * @param columns column definitions
     * @param primaryKey primary key columns
     * @param foreignKeys foreign key relationships
     * @param indexes table indexes
     */
    record TableDescription(
        String name,
        List<ColumnInfo> columns,
        List<String> primaryKey,
        List<ForeignKey> foreignKeys,
        List<IndexInfo> indexes
    ) {}

    /**
     * Column information.
     *
     * @param name column name
     * @param dataType SQL data type
     * @param nullable whether NULL is allowed
     * @param defaultValue default value expression
     * @param isPrimaryKey whether part of primary key
     * @param isAutoIncrement whether auto-increment
     */
    record ColumnInfo(
        String name,
        String dataType,
        boolean nullable,
        String defaultValue,
        boolean isPrimaryKey,
        boolean isAutoIncrement
    ) {}

    /**
     * Foreign key relationship.
     *
     * @param name constraint name
     * @param columns local columns
     * @param referencedTable referenced table
     * @param referencedColumns referenced columns
     */
    record ForeignKey(
        String name,
        List<String> columns,
        String referencedTable,
        List<String> referencedColumns
    ) {}

    /**
     * Index information.
     *
     * @param name index name
     * @param columns indexed columns
     * @param isUnique whether unique index
     * @param isPrimary whether primary key index
     */
    record IndexInfo(
        String name,
        List<String> columns,
        boolean isUnique,
        boolean isPrimary
    ) {}

    /**
     * Database metadata.
     *
     * @param databaseType database product (PostgreSQL, MySQL, etc.)
     * @param version database version
     * @param url connection URL
     * @param username connected user
     * @param defaultSchema default schema
     * @param readOnly whether connection is read-only
     */
    record DatabaseInfo(
        String databaseType,
        String version,
        String url,
        String username,
        String defaultSchema,
        boolean readOnly
    ) {}
}
