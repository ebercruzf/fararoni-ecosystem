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

import dev.fararoni.core.core.resilience.PoisonPill;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SovereignOutboxRepository implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SovereignOutboxRepository.class.getName());

    private final Connection connection;
    private final Path dbPath;

    public SovereignOutboxRepository(Path dbPath) throws SQLException {
        this.dbPath = dbPath;
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        this.connection = DriverManager.getConnection(url);
        configurePragmas();
        initializeSchema();
        LOG.info("[SOVEREIGN-OUTBOX] Inicializado en: " + dbPath);
    }

    private void configurePragmas() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA cache_size = -10000;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS outbox_events (
                    id TEXT PRIMARY KEY,
                    trace_id TEXT,
                    correlation_id TEXT,
                    topic TEXT NOT NULL,
                    sender_role TEXT,
                    reply_to TEXT,
                    payload_json TEXT NOT NULL,
                    payload_type TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    retry_count INTEGER DEFAULT 0,
                    max_retries INTEGER DEFAULT 3,
                    ttl_ms INTEGER DEFAULT 30000,
                    created_at INTEGER NOT NULL,
                    dispatched_at INTEGER,
                    expires_at INTEGER NOT NULL,
                    last_error TEXT,
                    checksum TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS poison_pills (
                    id TEXT PRIMARY KEY,
                    original_topic TEXT NOT NULL,
                    failure_reason TEXT,
                    stack_trace TEXT,
                    failing_component TEXT,
                    original_envelope_json TEXT,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    is_transient INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    processed_at INTEGER
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_outbox_expires ON outbox_events(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_outbox_topic ON outbox_events(topic)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pills_status ON poison_pills(status)");
        }
    }

    public boolean saveEvent(OutboxEvent event) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO outbox_events
            (id, trace_id, correlation_id, topic, sender_role, reply_to,
             payload_json, payload_type, status, retry_count, max_retries,
             ttl_ms, created_at, expires_at, checksum)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.id());
            stmt.setString(2, event.traceId());
            stmt.setString(3, event.correlationId());
            stmt.setString(4, event.topic());
            stmt.setString(5, event.senderRole());
            stmt.setString(6, event.replyTo());
            stmt.setString(7, event.payloadJson());
            stmt.setString(8, event.payloadType());
            stmt.setString(9, event.status().name());
            stmt.setInt(10, event.retryCount());
            stmt.setInt(11, event.maxRetries());
            stmt.setLong(12, event.ttlMs());
            stmt.setLong(13, event.createdAt().toEpochMilli());
            stmt.setLong(14, event.expiresAt().toEpochMilli());
            stmt.setString(15, event.checksum());
            return stmt.executeUpdate() > 0;
        }
    }

    public void markDispatched(String eventId) throws SQLException {
        String sql = "UPDATE outbox_events SET status = 'DISPATCHED', dispatched_at = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, Instant.now().toEpochMilli());
            stmt.setString(2, eventId);
            stmt.executeUpdate();
        }
    }

    public void markFailed(String eventId, String error) throws SQLException {
        String sql = "UPDATE outbox_events SET status = 'FAILED', last_error = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, error);
            stmt.setString(2, eventId);
            stmt.executeUpdate();
        }
    }

    public List<OutboxEvent> getPendingEvents(int limit) throws SQLException {
        String sql = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT ?
        """;
        List<OutboxEvent> events = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    events.add(mapRowToEvent(rs));
                }
            }
        }
        return events;
    }

    public int countByStatus(OutboxEvent.OutboxStatus status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM outbox_events WHERE status = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int cleanupExpired() throws SQLException {
        String sql = "UPDATE outbox_events SET status = 'EXPIRED' WHERE status = 'PENDING' AND expires_at < ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, Instant.now().toEpochMilli());
            return stmt.executeUpdate();
        }
    }

    public int purgeOldEvents(int daysToKeep) throws SQLException {
        long cutoff = Instant.now().minusSeconds(daysToKeep * 24L * 3600L).toEpochMilli();
        String sql = "DELETE FROM outbox_events WHERE created_at < ? AND status IN ('DISPATCHED', 'EXPIRED', 'FAILED')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, cutoff);
            return stmt.executeUpdate();
        }
    }

    public void savePoisonPill(PoisonPill pill, String jsonEnvelope) throws SQLException {
        String sql = """
            INSERT INTO poison_pills
            (id, original_topic, failure_reason, stack_trace, failing_component,
             original_envelope_json, status, is_transient, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, pill.originalTopic());
            stmt.setString(3, pill.failureReason());
            stmt.setString(4, pill.stackTrace());
            stmt.setString(5, pill.failingComponent());
            stmt.setString(6, jsonEnvelope);
            stmt.setInt(7, pill.isTransientError() ? 1 : 0);
            stmt.setLong(8, Instant.now().toEpochMilli());
            stmt.executeUpdate();
        }
    }

    public List<PoisonPillRecord> getPendingPoisonPills(int limit) throws SQLException {
        String sql = "SELECT * FROM poison_pills WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT ?";
        List<PoisonPillRecord> pills = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pills.add(new PoisonPillRecord(
                        rs.getString("id"),
                        rs.getString("original_topic"),
                        rs.getString("failure_reason"),
                        rs.getString("stack_trace"),
                        rs.getString("original_envelope_json"),
                        rs.getInt("is_transient") == 1,
                        rs.getString("status")
                    ));
                }
            }
        }
        return pills;
    }

    public void markPoisonPillProcessed(String pillId, String status) throws SQLException {
        String sql = "UPDATE poison_pills SET status = ?, processed_at = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, Instant.now().toEpochMilli());
            stmt.setString(3, pillId);
            stmt.executeUpdate();
        }
    }

    private OutboxEvent mapRowToEvent(ResultSet rs) throws SQLException {
        return new OutboxEvent(
            rs.getString("id"),
            rs.getString("trace_id"),
            rs.getString("correlation_id"),
            rs.getString("topic"),
            rs.getString("sender_role"),
            rs.getString("reply_to"),
            rs.getString("payload_json"),
            rs.getString("payload_type"),
            OutboxEvent.OutboxStatus.valueOf(rs.getString("status")),
            rs.getInt("retry_count"),
            rs.getInt("max_retries"),
            rs.getLong("ttl_ms"),
            Instant.ofEpochMilli(rs.getLong("created_at")),
            rs.getLong("dispatched_at") > 0 ? Instant.ofEpochMilli(rs.getLong("dispatched_at")) : null,
            Instant.ofEpochMilli(rs.getLong("expires_at")),
            rs.getString("last_error"),
            rs.getString("checksum")
        );
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            LOG.info("[SOVEREIGN-OUTBOX] Conexión cerrada");
        }
    }

    public record PoisonPillRecord(
        String id,
        String originalTopic,
        String failureReason,
        String stackTrace,
        String originalEnvelopeJson,
        boolean isTransient,
        String status
    ) {}
}
