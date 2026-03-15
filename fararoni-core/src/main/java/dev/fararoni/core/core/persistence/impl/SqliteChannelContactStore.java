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
package dev.fararoni.core.core.persistence.impl;

import dev.fararoni.core.core.persistence.ChannelContactStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SqliteChannelContactStore implements ChannelContactStore {
    private static final Logger LOG = Logger.getLogger(SqliteChannelContactStore.class.getName());

    private static final String DEFAULT_DB_PATH = System.getProperty("user.home")
        + "/.fararoni/data/identity.db";

    private final Connection connection;
    private final Path dbPath;

    public SqliteChannelContactStore() throws SQLException {
        this(Path.of(DEFAULT_DB_PATH));
    }

    public SqliteChannelContactStore(Path dbPath) throws SQLException {
        this.dbPath = dbPath;
        ensureDirectoryExists();
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        initializeSchema();
        LOG.info(() -> "[ChannelContactStore] Inicializado en: " + dbPath);
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception e) {
            LOG.warning("No se pudo crear directorio: " + e.getMessage());
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS access_list (
                    id TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    note TEXT,
                    created_at INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pairing_requests (
                    code TEXT PRIMARY KEY,
                    sender_id TEXT NOT NULL,
                    sender_name TEXT,
                    protocol TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS system_config (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_pairing_sender
                ON pairing_requests(sender_id)
            """);
        }
    }

    @Override
    public Optional<String> getOwner() {
        String sql = "SELECT value FROM system_config WHERE key = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, "owner");
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(rs.getString("value"));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error obteniendo owner", e);
        }
        return Optional.empty();
    }

    @Override
    public void setOwner(String ownerId) {
        String sql = """
            INSERT INTO system_config (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, "owner");
            stmt.setString(2, ownerId);
            stmt.executeUpdate();

            addToAllowListInternal(ownerId, ChannelContact.EntryType.OWNER, "System Owner");

            LOG.info(() -> "[ChannelContactStore] Owner establecido: " + ownerId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error estableciendo owner", e);
        }
    }

    @Override
    public boolean isAllowed(String senderId) {
        if (senderId == null) return false;

        if (isOwner(senderId)) return true;

        String sql = "SELECT 1 FROM access_list WHERE id = ? AND type IN (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, senderId);
            stmt.setString(2, ChannelContact.EntryType.CONTACT.name());
            stmt.setString(3, ChannelContact.EntryType.OWNER.name());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error verificando allowlist", e);
            return false;
        }
    }

    @Override
    public void addToAllowList(String senderId, String note) {
        addToAllowListInternal(senderId, ChannelContact.EntryType.CONTACT, note);
    }

    private void addToAllowListInternal(String id, ChannelContact.EntryType type, String note) {
        String sql = """
            INSERT INTO access_list (id, type, note, created_at) VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET note = excluded.note, type = excluded.type
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, type.name());
            stmt.setString(3, note);
            stmt.setLong(4, Instant.now().toEpochMilli());
            stmt.executeUpdate();
            LOG.info(() -> "[ChannelContactStore] Agregado a allowlist: " + id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error agregando a allowlist", e);
        }
    }

    @Override
    public boolean removeFromAllowList(String senderId) {
        String sql = "DELETE FROM access_list WHERE id = ? AND type != ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, senderId);
            stmt.setString(2, ChannelContact.EntryType.OWNER.name());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                LOG.info(() -> "[ChannelContactStore] Removido de allowlist: " + senderId);
            }
            return affected > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error removiendo de allowlist", e);
            return false;
        }
    }

    @Override
    public List<ChannelContact> getAllowList() {
        List<ChannelContact> entries = new ArrayList<>();
        String sql = "SELECT id, type, note, created_at FROM access_list WHERE type != ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ChannelContact.EntryType.GROUP.name());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                entries.add(new ChannelContact(
                    rs.getString("id"),
                    ChannelContact.EntryType.valueOf(rs.getString("type")),
                    rs.getString("note"),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
                ));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error listando allowlist", e);
        }
        return entries;
    }

    @Override
    public boolean isGroupAllowed(String groupId) {
        if (groupId == null) return false;

        String sql = "SELECT 1 FROM access_list WHERE id = ? AND type = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setString(2, ChannelContact.EntryType.GROUP.name());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error verificando grupo", e);
            return false;
        }
    }

    @Override
    public void addGroupToAllowList(String groupId, String note) {
        addToAllowListInternal(groupId, ChannelContact.EntryType.GROUP, note);
    }

    @Override
    public boolean removeGroupFromAllowList(String groupId) {
        String sql = "DELETE FROM access_list WHERE id = ? AND type = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setString(2, ChannelContact.EntryType.GROUP.name());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error removiendo grupo", e);
            return false;
        }
    }

    @Override
    public List<ChannelContact> getAllowedGroups() {
        List<ChannelContact> entries = new ArrayList<>();
        String sql = "SELECT id, type, note, created_at FROM access_list WHERE type = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, ChannelContact.EntryType.GROUP.name());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                entries.add(new ChannelContact(
                    rs.getString("id"),
                    ChannelContact.EntryType.GROUP,
                    rs.getString("note"),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
                ));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error listando grupos", e);
        }
        return entries;
    }

    @Override
    public void saveChannelPairingRequest(ChannelPairingRequest request) {
        String sql = """
            INSERT INTO pairing_requests (code, sender_id, sender_name, protocol, expires_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(code) DO UPDATE SET
                sender_id = excluded.sender_id,
                sender_name = excluded.sender_name,
                expires_at = excluded.expires_at
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, request.code());
            stmt.setString(2, request.senderId());
            stmt.setString(3, request.senderName());
            stmt.setString(4, request.protocol());
            stmt.setLong(5, request.expiresAt().toEpochMilli());
            stmt.setLong(6, request.createdAt().toEpochMilli());
            stmt.executeUpdate();
            LOG.info(() -> "[ChannelContactStore] Pairing guardado: " + request.code());
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error guardando pairing", e);
        }
    }

    @Override
    public Optional<ChannelPairingRequest> getChannelPairingRequestByCode(String code) {
        String sql = """
            SELECT code, sender_id, sender_name, protocol, expires_at, created_at
            FROM pairing_requests WHERE code = ? AND expires_at > ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code);
            stmt.setLong(2, Instant.now().toEpochMilli());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapChannelPairingRequest(rs));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error buscando pairing por codigo", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ChannelPairingRequest> getChannelPairingRequestBySender(String senderId) {
        String sql = """
            SELECT code, sender_id, sender_name, protocol, expires_at, created_at
            FROM pairing_requests WHERE sender_id = ? AND expires_at > ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, senderId);
            stmt.setLong(2, Instant.now().toEpochMilli());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapChannelPairingRequest(rs));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error buscando pairing por sender", e);
        }
        return Optional.empty();
    }

    @Override
    public boolean deleteChannelPairingRequest(String code) {
        String sql = "DELETE FROM pairing_requests WHERE code = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code);
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error eliminando pairing", e);
            return false;
        }
    }

    @Override
    public List<ChannelPairingRequest> getPendingChannelPairingRequests() {
        List<ChannelPairingRequest> requests = new ArrayList<>();
        String sql = """
            SELECT code, sender_id, sender_name, protocol, expires_at, created_at
            FROM pairing_requests WHERE expires_at > ?
            ORDER BY created_at DESC
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, Instant.now().toEpochMilli());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                requests.add(mapChannelPairingRequest(rs));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error listando pairings", e);
        }
        return requests;
    }

    @Override
    public int cleanupExpiredPairings() {
        String sql = "DELETE FROM pairing_requests WHERE expires_at <= ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, Instant.now().toEpochMilli());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                LOG.info(() -> "[ChannelContactStore] Limpiados " + affected + " pairings expirados");
            }
            return affected;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error limpiando pairings", e);
            return 0;
        }
    }

    private ChannelPairingRequest mapChannelPairingRequest(ResultSet rs) throws SQLException {
        return new ChannelPairingRequest(
            rs.getString("sender_id"),
            rs.getString("sender_name"),
            rs.getString("code"),
            rs.getString("protocol"),
            Instant.ofEpochMilli(rs.getLong("expires_at")),
            Instant.ofEpochMilli(rs.getLong("created_at"))
        );
    }

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            LOG.info("[ChannelContactStore] Conexion cerrada.");
        }
    }

    public Path getDbPath() {
        return dbPath;
    }
}
