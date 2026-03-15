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
package dev.fararoni.core.core.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.gateway.security.SecureChannelAdapter;
import dev.fararoni.core.core.gateway.spi.ChannelFactory;
import dev.fararoni.core.core.gateway.spi.ChannelFactory.ChannelCreationException;
import dev.fararoni.core.core.gateway.spi.GenericWebhookFactory;
import dev.fararoni.core.core.security.ChannelAccessGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ChannelLoader {
    private static final Logger LOG = Logger.getLogger(ChannelLoader.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SovereignEventBus bus;
    private final ChannelAccessGuard guard;

    private final Map<String, ChannelFactory> factories = new ConcurrentHashMap<>();

    public ChannelLoader(SovereignEventBus bus, ChannelAccessGuard guard) {
        this.bus = bus;
        this.guard = guard;

        registerFactory(new GenericWebhookFactory());

        LOG.info("[ChannelLoader] Inicializado con fabrica GenericWebhook");
    }

    public void registerFactory(ChannelFactory factory) {
        String type = factory.getSupportedType().toUpperCase();
        factories.put(type, factory);
        LOG.info(() -> "[ChannelLoader] Fabrica registrada: " + type);
    }

    public Set<String> getSupportedTypes() {
        return Set.copyOf(factories.keySet());
    }

    public List<SecureChannelAdapter> loadFromDatabase(Connection connection) {
        List<SecureChannelAdapter> adapters = new ArrayList<>();

        String sql = "SELECT id, type, name, config_json FROM agency_channels WHERE status = 'ACTIVE'";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String type = rs.getString("type").toUpperCase();
                String name = rs.getString("name");
                String configJson = rs.getString("config_json");

                try {
                    SecureChannelAdapter adapter = createFromConfig(id, type, configJson);
                    if (adapter != null) {
                        adapters.add(adapter);
                        LOG.info(() -> String.format(
                            "[ChannelLoader] Cargado: %s (%s) - %s",
                            id, type, name
                        ));
                    }
                } catch (Exception e) {
                    LOG.warning(() -> String.format(
                        "[ChannelLoader] Error cargando canal %s: %s",
                        id, e.getMessage()
                    ));
                }
            }
        } catch (SQLException e) {
            LOG.warning("[ChannelLoader] Error leyendo DB: " + e.getMessage());
        }

        LOG.info(() -> String.format(
            "[ChannelLoader] Cargados %d canales desde DB",
            adapters.size()
        ));

        return adapters;
    }

    public List<SecureChannelAdapter> loadFromYaml(Path yamlPath) {
        List<SecureChannelAdapter> adapters = new ArrayList<>();

        if (!Files.exists(yamlPath)) {
            LOG.info(() -> "[ChannelLoader] Archivo YAML no existe: " + yamlPath);
            return adapters;
        }

        try {
            JsonNode root = JSON.readTree(yamlPath.toFile());
            JsonNode channels = root.get("channels");

            if (channels != null && channels.isArray()) {
                for (JsonNode channelNode : channels) {
                    String id = channelNode.get("id").asText();
                    String type = channelNode.get("type").asText().toUpperCase();
                    String status = channelNode.has("status")
                        ? channelNode.get("status").asText()
                        : "ACTIVE";

                    if (!"ACTIVE".equalsIgnoreCase(status)) {
                        continue;
                    }

                    JsonNode config = channelNode.get("config");

                    try {
                        SecureChannelAdapter adapter = createAdapter(id, type, config);
                        if (adapter != null) {
                            adapters.add(adapter);
                            LOG.info(() -> "[ChannelLoader] Cargado desde YAML: " + id);
                        }
                    } catch (Exception e) {
                        LOG.warning(() -> String.format(
                            "[ChannelLoader] Error cargando canal %s desde YAML: %s",
                            id, e.getMessage()
                        ));
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("[ChannelLoader] Error leyendo YAML: " + e.getMessage());
        }

        return adapters;
    }

    private SecureChannelAdapter createFromConfig(String channelId, String type, String configJson)
            throws ChannelCreationException {
        try {
            JsonNode config = JSON.readTree(configJson);
            return createAdapter(channelId, type, config);
        } catch (IOException e) {
            throw new ChannelCreationException("JSON invalido para canal: " + channelId, e);
        }
    }

    private SecureChannelAdapter createAdapter(String channelId, String type, JsonNode config)
            throws ChannelCreationException {
        ChannelFactory factory = factories.get(type);

        if (factory == null) {
            throw new ChannelCreationException(
                "No existe fabrica para tipo: " + type +
                ". Tipos soportados: " + factories.keySet()
            );
        }

        return factory.create(channelId, config, bus, guard);
    }

    public ValidationResult validateConfig(String type, String configJson) {
        ChannelFactory factory = factories.get(type.toUpperCase());

        if (factory == null) {
            return new ValidationResult(false,
                "Tipo no soportado: " + type + ". Soportados: " + factories.keySet());
        }

        try {
            JsonNode config = JSON.readTree(configJson);

            if (factory.validateConfig(config)) {
                return new ValidationResult(true, "Configuracion valida");
            } else {
                return new ValidationResult(false,
                    "Campos requeridos faltantes: " + Arrays.toString(factory.getRequiredFields()));
            }
        } catch (IOException e) {
            return new ValidationResult(false, "JSON invalido: " + e.getMessage());
        }
    }

    public record ValidationResult(boolean valid, String message) {}

    public static String getCreateTableSql() {
        return """
            CREATE TABLE IF NOT EXISTS agency_channels (
                id VARCHAR(64) PRIMARY KEY,
                type VARCHAR(32) NOT NULL,
                name VARCHAR(128),
                config_json TEXT NOT NULL,
                status VARCHAR(16) DEFAULT 'ACTIVE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE INDEX IF NOT EXISTS idx_channels_status ON agency_channels(status);
            CREATE INDEX IF NOT EXISTS idx_channels_type ON agency_channels(type);
            """;
    }
}
