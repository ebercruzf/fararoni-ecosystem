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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.core.core.persistence.spi.SovereignRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SqliteChannelRepository implements SovereignRepository {
    private static final Logger LOG = Logger.getLogger(SqliteChannelRepository.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String dbPath;
    private Connection connection;

    public SqliteChannelRepository(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void initialize() throws RepositoryException {
        try {
            Class.forName("org.sqlite.JDBC");

            String jdbcUrl = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(jdbcUrl);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }

            createTablesIfNeeded();

            loadSeedDataIfEmpty();

            LOG.info(() -> "[SqliteChannelRepo] Inicializado: " + dbPath);
        } catch (ClassNotFoundException e) {
            throw new RepositoryException("Driver SQLite no encontrado", e);
        } catch (SQLException e) {
            throw new RepositoryException("Error conectando a SQLite: " + e.getMessage(), e);
        }
    }

    private void createTablesIfNeeded() throws SQLException, RepositoryException {
        String schemaSql = loadSchemaFromResources();
        if (schemaSql != null) {
            LOG.info("[SqliteChannelRepo] Cargando schema.sql desde recursos (" + schemaSql.length() + " chars)");
            runScript(schemaSql);
            LOG.info("[SqliteChannelRepo] Schema creado exitosamente");
        } else {
            LOG.warning("[SqliteChannelRepo] schema.sql no encontrado, usando schema minimo");
            createMinimalSchema();
        }
    }

    private void loadSeedDataIfEmpty() {
        try {
            if (countChannels() == 0) {
                LOG.info("[SqliteChannelRepo] DB vacia. Cargando datos semilla (data.sql)...");
                String dataSql = loadDataFromResources();
                if (dataSql != null) {
                    runScript(dataSql);
                    LOG.info(() -> "[SqliteChannelRepo] Datos semilla cargados: " + countChannels() + " canales");
                }
            }
        } catch (Exception e) {
            LOG.warning("[SqliteChannelRepo] Error cargando datos semilla: " + e.getMessage());
        }
    }

    private String loadDataFromResources() {
        try (InputStream is = getClass().getResourceAsStream("/db/data.sql")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warning("[SqliteChannelRepo] No se pudo cargar data.sql: " + e.getMessage());
        }
        return null;
    }

    private String loadSchemaFromResources() {
        try (InputStream is = getClass().getResourceAsStream("/db/schema.sql")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOG.warning("[SqliteChannelRepo] No se pudo cargar schema.sql: " + e.getMessage());
        }
        return null;
    }

    private void createMinimalSchema() throws SQLException, RepositoryException {
        String sql = """
            CREATE TABLE IF NOT EXISTS agency_channels (
                id VARCHAR(64) PRIMARY KEY,
                type VARCHAR(32) NOT NULL,
                name VARCHAR(128),
                config_json TEXT NOT NULL,
                status VARCHAR(16) DEFAULT 'ACTIVE',
                created_at INTEGER DEFAULT (strftime('%s', 'now')),
                updated_at INTEGER DEFAULT (strftime('%s', 'now'))
            );

            CREATE TABLE IF NOT EXISTS channel_secrets (
                channel_id VARCHAR(64) PRIMARY KEY,
                encrypted_secret BLOB NOT NULL,
                encryption_iv BLOB NOT NULL,
                auth_tag BLOB NOT NULL,
                encryption_version INTEGER DEFAULT 1,
                key_id VARCHAR(64),
                FOREIGN KEY (channel_id) REFERENCES agency_channels(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS channel_audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                channel_id VARCHAR(64) NOT NULL,
                action VARCHAR(16) NOT NULL,
                changed_by VARCHAR(64),
                changed_at INTEGER DEFAULT (strftime('%s', 'now'))
            );
            """;
        runScript(sql);
    }

    @Override
    public boolean isAvailable() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            LOG.info("[SqliteChannelRepo] Conexion cerrada");
        }
    }

    @Override
    public List<ChannelRecord> findActiveChannels() {
        String sql = "SELECT id, type, name, config_json, status, created_at, updated_at " +
                     "FROM agency_channels WHERE status = 'ACTIVE'";
        return queryChannels(sql);
    }

    @Override
    public Optional<ChannelRecord> findById(String channelId) {
        String sql = "SELECT id, type, name, config_json, status, created_at, updated_at " +
                     "FROM agency_channels WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapToChannelRecord(rs));
            }
        } catch (SQLException e) {
            LOG.warning("[SqliteChannelRepo] Error buscando canal: " + e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public List<ChannelRecord> findByType(String type) {
        String sql = "SELECT id, type, name, config_json, status, created_at, updated_at " +
                     "FROM agency_channels WHERE type = ?";

        List<ChannelRecord> results = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, type);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                results.add(mapToChannelRecord(rs));
            }
        } catch (SQLException e) {
            LOG.warning("[SqliteChannelRepo] Error buscando por tipo: " + e.getMessage());
        }

        return results;
    }

    @Override
    public void save(ChannelRecord record) throws RepositoryException {
        String sql = """
            INSERT INTO agency_channels (id, type, name, config_json, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                type = excluded.type,
                name = excluded.name,
                config_json = excluded.config_json,
                status = excluded.status,
                updated_at = excluded.updated_at
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, record.id());
            stmt.setString(2, record.type());
            stmt.setString(3, record.name());
            stmt.setString(4, record.config().toString());
            stmt.setString(5, record.status());
            stmt.setLong(6, record.createdAt());
            stmt.setLong(7, System.currentTimeMillis() / 1000);

            stmt.executeUpdate();

            LOG.fine(() -> "[SqliteChannelRepo] Canal guardado: " + record.id());
        } catch (SQLException e) {
            throw new RepositoryException("Error guardando canal: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateStatus(String channelId, String status) throws RepositoryException {
        String sql = "UPDATE agency_channels SET status = ?, updated_at = ? WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setLong(2, System.currentTimeMillis() / 1000);
            stmt.setString(3, channelId);

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new RepositoryException("Canal no encontrado: " + channelId);
            }

            LOG.info(() -> "[SqliteChannelRepo] Estado actualizado: " + channelId + " -> " + status);
        } catch (SQLException e) {
            throw new RepositoryException("Error actualizando estado: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String channelId) throws RepositoryException {
        String sql = "DELETE FROM agency_channels WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            stmt.executeUpdate();

            LOG.info(() -> "[SqliteChannelRepo] Canal eliminado: " + channelId);
        } catch (SQLException e) {
            throw new RepositoryException("Error eliminando canal: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveSecret(String channelId, byte[] encryptedSecret, byte[] iv, byte[] authTag, String keyId)
            throws RepositoryException {
        String sql = """
            INSERT INTO channel_secrets (channel_id, encrypted_secret, encryption_iv, auth_tag, key_id)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(channel_id) DO UPDATE SET
                encrypted_secret = excluded.encrypted_secret,
                encryption_iv = excluded.encryption_iv,
                auth_tag = excluded.auth_tag,
                key_id = excluded.key_id
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            stmt.setBytes(2, encryptedSecret);
            stmt.setBytes(3, iv);
            stmt.setBytes(4, authTag);
            stmt.setString(5, keyId);

            stmt.executeUpdate();

            LOG.fine(() -> "[SqliteChannelRepo] Secreto guardado para: " + channelId);
        } catch (SQLException e) {
            throw new RepositoryException("Error guardando secreto: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<EncryptedSecretRecord> getSecret(String channelId) {
        String sql = "SELECT channel_id, encrypted_secret, encryption_iv, auth_tag, " +
                     "encryption_version, key_id FROM channel_secrets WHERE channel_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(new EncryptedSecretRecord(
                    rs.getString("channel_id"),
                    rs.getBytes("encrypted_secret"),
                    rs.getBytes("encryption_iv"),
                    rs.getBytes("auth_tag"),
                    rs.getInt("encryption_version"),
                    rs.getString("key_id")
                ));
            }
        } catch (SQLException e) {
            LOG.warning("[SqliteChannelRepo] Error obteniendo secreto: " + e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public void deleteSecret(String channelId) {
        String sql = "DELETE FROM channel_secrets WHERE channel_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("[SqliteChannelRepo] Error eliminando secreto: " + e.getMessage());
        }
    }

    @Override
    public void logAudit(String channelId, String action, String changedBy) {
        String sql = "INSERT INTO channel_audit_log (channel_id, action, changed_by) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, channelId);
            stmt.setString(2, action);
            stmt.setString(3, changedBy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("[SqliteChannelRepo] Error en auditoria: " + e.getMessage());
        }
    }

    @Override
    public int countChannels() {
        String sql = "SELECT COUNT(*) FROM agency_channels";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.warning("[SqliteChannelRepo] Error contando canales: " + e.getMessage());
        }

        return 0;
    }

    @Override
    public void runScript(String sqlScript) throws RepositoryException {
        try (Statement stmt = connection.createStatement()) {
            String[] statements = sqlScript.split(";(?=(?:[^']*'[^']*')*[^']*$)");

            int executed = 0;
            for (String s : statements) {
                String cleaned = s.lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .reduce("", (a, b) -> a + "\n" + b)
                    .trim();

                if (!cleaned.isEmpty()) {
                    try {
                        stmt.execute(cleaned);
                        executed++;
                    } catch (SQLException e) {
                        LOG.warning("[SqliteChannelRepo] Error en statement #" + executed +
                            ": " + e.getMessage() + "\nSQL: " + cleaned.substring(0, Math.min(100, cleaned.length())));
                        throw e;
                    }
                }
            }
            LOG.fine("[SqliteChannelRepo] Script ejecutado: " + executed + " statements");
        } catch (SQLException e) {
            throw new RepositoryException("Error ejecutando script: " + e.getMessage(), e);
        }
    }

    private List<ChannelRecord> queryChannels(String sql) {
        List<ChannelRecord> results = new ArrayList<>();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapToChannelRecord(rs));
            }
        } catch (SQLException e) {
            LOG.warning("[SqliteChannelRepo] Error en query: " + e.getMessage());
        }

        return results;
    }

    private ChannelRecord mapToChannelRecord(ResultSet rs) throws SQLException {
        JsonNode config;
        try {
            config = JSON.readTree(rs.getString("config_json"));
        } catch (Exception e) {
            config = JSON.createObjectNode();
        }

        return new ChannelRecord(
            rs.getString("id"),
            rs.getString("type"),
            rs.getString("name"),
            config,
            rs.getString("status"),
            rs.getLong("created_at"),
            rs.getLong("updated_at")
        );
    }
}
