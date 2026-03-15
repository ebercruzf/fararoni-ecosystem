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

import dev.fararoni.core.core.persistence.spi.ConversationRepository;
import dev.fararoni.core.model.Message;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SqliteConversationRepository implements ConversationRepository, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SqliteConversationRepository.class.getName());

    private static final int MAX_HISTORY_PER_SESSION = 10;

    private final Connection connection;
    private volatile boolean available = false;

    public SqliteConversationRepository(Connection connection) {
        this.connection = connection;
        initTable();
    }

    public SqliteConversationRepository(String dbPath) throws SQLException {
        try {
            Path path = Path.of(dbPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            configureConnection();
            initTable();

            LOG.info("[ConversationRepo] Inicializado: " + dbPath);
        } catch (Exception e) {
            throw new SQLException("Error inicializando ConversationRepository: " + e.getMessage(), e);
        }
    }

    public SqliteConversationRepository(Path dbPath) throws SQLException {
        this(dbPath.toString());
    }

    private void configureConnection() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA foreign_keys=ON");
            stmt.execute("PRAGMA synchronous=NORMAL");
        }
    }

    private void initTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS conversation_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('user', 'assistant', 'system')),
                content TEXT NOT NULL,
                token_count INTEGER DEFAULT 0,
                created_at INTEGER DEFAULT (strftime('%s', 'now'))
            );
            """;

        String indexSql = """
            CREATE INDEX IF NOT EXISTS idx_conv_session
                ON conversation_history(session_id, id DESC);
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            stmt.execute(indexSql);
            available = true;
            LOG.fine("[ConversationRepo] Tabla e indices creados");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "[ConversationRepo] Error creando tabla", e);
            available = false;
        }
    }

    @Override
    public void saveMessage(String sessionId, Message message) {
        if (!available || sessionId == null || message == null) {
            return;
        }

        String sql = "INSERT INTO conversation_history (session_id, role, content) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setString(2, message.role());
            pstmt.setString(3, message.content());
            pstmt.executeUpdate();

            LOG.fine(() -> "[ConversationRepo] Guardado en " + sessionId + ": " + message.role());

            pruneHistory(sessionId);
        } catch (SQLException e) {
            LOG.warning("[ConversationRepo] Error guardando mensaje: " + e.getMessage());
        }
    }

    @Override
    public List<Message> getHistory(String sessionId, int limit) {
        List<Message> history = new ArrayList<>();

        if (!available || sessionId == null) {
            return history;
        }

        String sql = """
            SELECT role, content FROM conversation_history
            WHERE session_id = ?
            ORDER BY id DESC
            LIMIT ?
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                history.add(0, new Message(
                    rs.getString("role"),
                    rs.getString("content")
                ));
            }

            LOG.fine(() -> "[ConversationRepo] Recuperados " + history.size() + " mensajes de " + sessionId);
        } catch (SQLException e) {
            LOG.warning("[ConversationRepo] Error recuperando historial: " + e.getMessage());
        }

        return history;
    }

    @Override
    public void clear(String sessionId) {
        if (!available || sessionId == null) {
            return;
        }

        String sql = "DELETE FROM conversation_history WHERE session_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            int deleted = pstmt.executeUpdate();
            LOG.info(() -> "[ConversationRepo] Limpiados " + deleted + " mensajes de " + sessionId);
        } catch (SQLException e) {
            LOG.warning("[ConversationRepo] Error limpiando sesion: " + e.getMessage());
        }
    }

    @Override
    public void trimByTokens(String sessionId, int maxTokens) {
        pruneHistory(sessionId);
    }

    private void pruneHistory(String sessionId) {
        String sql = """
            DELETE FROM conversation_history
            WHERE id IN (
                SELECT id FROM conversation_history
                WHERE session_id = ?
                ORDER BY created_at DESC
                LIMIT -1 OFFSET ?
            )
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.setInt(2, MAX_HISTORY_PER_SESSION);
            int pruned = pstmt.executeUpdate();

            if (pruned > 0) {
                LOG.fine(() -> "[ConversationRepo] Purgados " + pruned + " mensajes de " + sessionId);
            }
        } catch (SQLException e) {
            LOG.finest("[ConversationRepo] Pruning fallido: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return available && connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public int countMessages(String sessionId) {
        if (!available || sessionId == null) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM conversation_history WHERE session_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.warning("[ConversationRepo] Error contando mensajes: " + e.getMessage());
        }

        return 0;
    }

    public Stats getStats() {
        int totalMessages = 0;
        int totalSessions = 0;

        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM conversation_history");
            if (rs.next()) {
                totalMessages = rs.getInt(1);
            }

            rs = stmt.executeQuery("SELECT COUNT(DISTINCT session_id) FROM conversation_history");
            if (rs.next()) {
                totalSessions = rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.warning("[ConversationRepo] Error obteniendo stats: " + e.getMessage());
        }

        return new Stats(totalMessages, totalSessions, MAX_HISTORY_PER_SESSION, available);
    }

    public record Stats(
        int totalMessages,
        int totalSessions,
        int maxHistoryPerSession,
        boolean available
    ) {}

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                available = false;
                LOG.info("[ConversationRepo] Conexion cerrada");
            }
        } catch (SQLException e) {
            LOG.warning("[ConversationRepo] Error cerrando conexion: " + e.getMessage());
        }
    }
}
