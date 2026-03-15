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
package dev.fararoni.core.core.mission.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.mission.model.MissionState;
import dev.fararoni.core.core.mission.model.MissionState.ExecutedStep;
import dev.fararoni.core.core.mission.model.MissionState.ExecutionStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class JdbcMissionStateRepository implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(JdbcMissionStateRepository.class.getName());

    private static final String TABLE_NAME = "mission_executions";

    private final String jdbcUrl;
    private final HikariDataSource dataSource;
    private final ObjectMapper objectMapper;
    private final boolean isSqlite;

    public JdbcMissionStateRepository() {
        this(AppDefaults.MISSION_DB_URL);
    }

    public JdbcMissionStateRepository(String dbPathOrUrl) {
        this.objectMapper = createObjectMapper();
        this.jdbcUrl = resolveJdbcUrl(dbPathOrUrl);
        this.isSqlite = this.jdbcUrl.startsWith("jdbc:sqlite:");

        LOG.info("JdbcMissionStateRepository initializing...");
        LOG.info("JDBC URL: " + sanitizeUrlForLog(this.jdbcUrl));
        LOG.info("Driver: " + (isSqlite ? "SQLite" : "PostgreSQL"));

        this.dataSource = createDataSource();
        initializeSchema();

        LOG.info("Repository initialized successfully");
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    private String resolveJdbcUrl(String dbPathOrUrl) {
        if (dbPathOrUrl == null || dbPathOrUrl.isBlank()) {
            dbPathOrUrl = System.getProperty("user.home") + "/.fararoni/data/missions.db";
        }
        if (dbPathOrUrl.startsWith("jdbc:")) {
            return dbPathOrUrl;
        }
        try {
            java.nio.file.Path dbPath = java.nio.file.Paths.get(dbPathOrUrl);
            java.nio.file.Path parentDir = dbPath.getParent();
            if (parentDir != null && !java.nio.file.Files.exists(parentDir)) {
                java.nio.file.Files.createDirectories(parentDir);
                LOG.info("Created mission DB directory: " + parentDir);
            }
        } catch (Exception e) {
            LOG.warning("Could not create DB directory: " + e.getMessage());
        }
        return "jdbc:sqlite:" + dbPathOrUrl;
    }

    private HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(this.jdbcUrl);

        config.setMaximumPoolSize(AppDefaults.DB_MAX_POOL_SIZE);
        config.setMinimumIdle(AppDefaults.DB_MIN_IDLE);
        config.setConnectionTimeout(AppDefaults.DB_CONN_TIMEOUT);
        config.setIdleTimeout(AppDefaults.DB_IDLE_TIMEOUT);
        config.setMaxLifetime(AppDefaults.DB_MAX_LIFETIME);

        config.setPoolName("FararoniMissionPool");

        if (isSqlite) {
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
        } else {
            config.setDriverClassName("org.postgresql.Driver");
        }

        LOG.info("HikariCP config: maxPool=" + config.getMaximumPoolSize() +
                 ", minIdle=" + config.getMinimumIdle() +
                 ", connTimeout=" + config.getConnectionTimeout() + "ms");

        return new HikariDataSource(config);
    }

    private void initializeSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            if (isSqlite) {
                LOG.info("Applying SQLite PRAGMAs...");
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA cache_size=-64000;");
                stmt.execute("PRAGMA temp_store=MEMORY;");
                stmt.execute("PRAGMA busy_timeout=5000;");
            }

            String createTable = """
                CREATE TABLE IF NOT EXISTS %s (
                    execution_id          TEXT PRIMARY KEY,
                    original_correlation_id TEXT,
                    mission_id            TEXT NOT NULL,
                    current_step_id       TEXT NOT NULL,
                    iterations            INTEGER DEFAULT 0,
                    status                TEXT NOT NULL,
                    payload_json          TEXT,
                    created_at            TEXT NOT NULL,
                    updated_at            TEXT NOT NULL,
                    executed_steps_json   TEXT,
                    retry_counts_json     TEXT
                )
                """.formatted(TABLE_NAME);
            stmt.execute(createTable);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mission_status ON " +
                         TABLE_NAME + " (status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mission_id ON " +
                         TABLE_NAME + " (mission_id)");

            migrateAddRetryCountsColumn(conn);

            migrateAddOriginalCorrelationIdColumn(conn);

            LOG.info("Schema initialized successfully");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to initialize schema", e);
            throw new RuntimeException("Failed to initialize mission database", e);
        }
    }

    private void migrateAddRetryCountsColumn(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            try (var rs = stmt.executeQuery("PRAGMA table_info(" + TABLE_NAME + ")")) {
                boolean hasRetryCountsColumn = false;
                while (rs.next()) {
                    if ("retry_counts_json".equals(rs.getString("name"))) {
                        hasRetryCountsColumn = true;
                        break;
                    }
                }

                if (!hasRetryCountsColumn) {
                    LOG.info("Migrating: adding retry_counts_json column...");
                    stmt.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN retry_counts_json TEXT");
                    LOG.info("Migration complete: retry_counts_json column added");
                }
            }
        } catch (SQLException e) {
            LOG.warning("Migration check failed (may be PostgreSQL): " + e.getMessage());
        }
    }

    private void migrateAddOriginalCorrelationIdColumn(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            try (var rs = stmt.executeQuery("PRAGMA table_info(" + TABLE_NAME + ")")) {
                boolean hasColumn = false;
                while (rs.next()) {
                    if ("original_correlation_id".equals(rs.getString("name"))) {
                        hasColumn = true;
                        break;
                    }
                }

                if (!hasColumn) {
                    LOG.info("Migrating: adding original_correlation_id column...");
                    stmt.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN original_correlation_id TEXT");
                    LOG.info("Migration complete: original_correlation_id column added");
                }
            }
        } catch (SQLException e) {
            LOG.warning("Migration check failed (may be PostgreSQL): " + e.getMessage());
        }
    }

    private String sanitizeUrlForLog(String url) {
        return url.replaceAll("password=[^&]*", "password=***");
    }

    public void save(MissionState state) {
        String sql;
        if (isSqlite) {
            sql = """
                INSERT OR REPLACE INTO %s
                (execution_id, original_correlation_id, mission_id, current_step_id, iterations, status,
                 payload_json, created_at, updated_at, executed_steps_json, retry_counts_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(TABLE_NAME);
        } else {
            sql = """
                INSERT INTO %s
                (execution_id, original_correlation_id, mission_id, current_step_id, iterations, status,
                 payload_json, created_at, updated_at, executed_steps_json, retry_counts_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (execution_id) DO UPDATE SET
                    original_correlation_id = EXCLUDED.original_correlation_id,
                    current_step_id = EXCLUDED.current_step_id,
                    iterations = EXCLUDED.iterations,
                    status = EXCLUDED.status,
                    payload_json = EXCLUDED.payload_json,
                    updated_at = EXCLUDED.updated_at,
                    executed_steps_json = EXCLUDED.executed_steps_json,
                    retry_counts_json = EXCLUDED.retry_counts_json
                """.formatted(TABLE_NAME);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, state.executionId());
            ps.setString(2, state.originalCorrelationId());
            ps.setString(3, state.missionId());
            ps.setString(4, state.currentStepId());
            ps.setInt(5, state.iterations());
            ps.setString(6, state.status().name());
            ps.setString(7, state.payloadJson());
            ps.setString(8, state.createdAt().toString());
            ps.setString(9, state.updatedAt().toString());
            ps.setString(10, serializeExecutedSteps(state.executedSteps()));
            ps.setString(11, serializeRetryCounts(state.retryCounts()));

            ps.executeUpdate();

            LOG.fine(String.format("Saved mission state: [%s (ref:%s)] status=%s",
                state.executionId(),
                state.originalCorrelationId() != null ? state.originalCorrelationId() : "N/A",
                state.status()));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to save mission state: " +
                    state.executionId(), e);
            throw new RuntimeException("Failed to save mission state", e);
        }
    }

    public Optional<MissionState> findById(String executionId) {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE execution_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to find mission: " + executionId, e);
            throw new RuntimeException("Failed to find mission state", e);
        }
    }

    public List<MissionState> findOrphanedMissions() {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE status = 'RUNNING'";
        List<MissionState> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }

            LOG.info("Found " + results.size() + " orphaned missions");
            return results;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to find orphaned missions", e);
            throw new RuntimeException("Failed to find orphaned missions", e);
        }
    }

    public List<MissionState> findStaleMissions(Instant threshold) {
        String sql = "SELECT * FROM " + TABLE_NAME +
                     " WHERE status = 'RUNNING' AND updated_at < ?";
        List<MissionState> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, threshold.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }

            if (!results.isEmpty()) {
                LOG.warning("Found " + results.size() +
                    " stale missions (inactive since " + threshold + ")");
            }
            return results;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to find stale missions", e);
            throw new RuntimeException("Failed to find stale missions", e);
        }
    }

    private MissionState mapRow(ResultSet rs) throws SQLException {
        String executedStepsJson = rs.getString("executed_steps_json");
        List<ExecutedStep> executedSteps = deserializeExecutedSteps(executedStepsJson);

        String retryCountsJson = rs.getString("retry_counts_json");
        Map<String, Integer> retryCounts = deserializeRetryCounts(retryCountsJson);

        return new MissionState(
            rs.getString("execution_id"),
            rs.getString("original_correlation_id"),
            rs.getString("mission_id"),
            rs.getString("current_step_id"),
            rs.getInt("iterations"),
            ExecutionStatus.valueOf(rs.getString("status")),
            rs.getString("payload_json"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at")),
            executedSteps,
            retryCounts
        );
    }

    private String serializeExecutedSteps(List<ExecutedStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to serialize executed steps", e);
            return "[]";
        }
    }

    private List<ExecutedStep> deserializeExecutedSteps(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ExecutedStep>>() {});
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to deserialize executed steps", e);
            return List.of();
        }
    }

    private String serializeRetryCounts(Map<String, Integer> retryCounts) {
        if (retryCounts == null || retryCounts.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(retryCounts);
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to serialize retry counts", e);
            return "{}";
        }
    }

    private Map<String, Integer> deserializeRetryCounts(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to deserialize retry counts", e);
            return Map.of();
        }
    }

    public String getPoolStats() {
        return String.format(
            "HikariPool[active=%d, idle=%d, waiting=%d, total=%d]",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
            dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }

    public boolean isHealthy() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOG.info("Closing JdbcMissionStateRepository...");
            dataSource.close();
            LOG.info("Repository closed");
        }
    }
}
