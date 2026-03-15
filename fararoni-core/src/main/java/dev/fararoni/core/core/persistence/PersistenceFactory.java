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

import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.persistence.spi.SovereignRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class PersistenceFactory {
    private static final Logger LOG = Logger.getLogger(PersistenceFactory.class.getName());

    private PersistenceFactory() {
        throw new AssertionError("Factory class - no instantiation");
    }

    public static SovereignRepository createRepository() {
        String dbType = AppDefaults.CHANNELS_DB_TYPE.toLowerCase();

        LOG.info(() -> "[PersistenceFactory] Tipo de DB: " + dbType);

        return switch (dbType) {
            case "sqlite" -> createSqliteRepository();
            case "postgresql", "postgres" -> createPostgresRepository();
            default -> {
                LOG.warning("[PersistenceFactory] Tipo desconocido: " + dbType + ". Usando SQLite.");
                yield createSqliteRepository();
            }
        };
    }

    private static SovereignRepository createSqliteRepository() {
        String dbPath = AppDefaults.CHANNELS_DB_PATH;

        LOG.info(() -> "[PersistenceFactory] SQLite path: " + dbPath);

        try {
            Path dir = Path.of(dbPath).getParent();
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
                LOG.info(() -> "[PersistenceFactory] Directorio creado: " + dir);
            }
        } catch (Exception e) {
            LOG.warning("[PersistenceFactory] Error creando directorio: " + e.getMessage());
        }

        return new SqliteChannelRepository(dbPath);
    }

    private static SovereignRepository createPostgresRepository() {
        String url = AppDefaults.CHANNELS_DB_URL;
        String user = AppDefaults.CHANNELS_DB_USER;
        String password = AppDefaults.CHANNELS_DB_PASSWORD;

        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                "PostgreSQL configurado pero FARARONI_CHANNELS_DB_URL no definida. " +
                "Configurar o cambiar a sqlite."
            );
        }

        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                "URL invalida. Formato esperado: jdbc:postgresql://host:port/database. " +
                "Actual: " + url
            );
        }

        LOG.info(() -> "[PersistenceFactory] PostgreSQL URL: " + url);

        throw new UnsupportedOperationException(
            "PostgreSQL no implementado aun (FASE futura). " +
            "Usar SQLite: export FARARONI_CHANNELS_DB_TYPE=sqlite"
        );
    }

    public static boolean isEncryptionConfigured() {
        String key = AppDefaults.CHANNELS_ENCRYPTION_KEY;
        boolean hasKey = key != null && !key.isBlank();

        if (!hasKey && !AppDefaults.DEV_MODE) {
            LOG.severe(
                "[PersistenceFactory] FARARONI_CHANNELS_ENCRYPTION_KEY no configurada. " +
                "Requerida en produccion. Generar con: openssl rand -base64 32"
            );
        }

        return hasKey;
    }

    public static String getDiagnostics() {
        return String.format("""
            === Channels DB Configuration ===
            Type: %s
            SQLite Path: %s
            PostgreSQL URL: %s
            Encryption Key: %s
            Key ID: %s
            Dev Mode: %s
            """,
            AppDefaults.CHANNELS_DB_TYPE,
            AppDefaults.CHANNELS_DB_PATH,
            AppDefaults.CHANNELS_DB_URL.isBlank() ? "(not set)" : "(set)",
            AppDefaults.CHANNELS_ENCRYPTION_KEY.isBlank() ? "NOT CONFIGURED" : "CONFIGURED",
            AppDefaults.CHANNELS_KEY_ID,
            AppDefaults.DEV_MODE
        );
    }
}
