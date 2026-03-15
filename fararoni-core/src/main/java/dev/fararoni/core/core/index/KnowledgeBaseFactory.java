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
package dev.fararoni.core.core.index;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class KnowledgeBaseFactory {
    private static final Logger LOG = Logger.getLogger(KnowledgeBaseFactory.class.getName());

    public enum DbType {
        EMBEDDED_SQL,

        IN_MEMORY,

        ENTERPRISE
    }

    private KnowledgeBaseFactory() {
        throw new AssertionError("Factory class - no instantiation");
    }

    public static ProjectKnowledgeBase create() {
        String dbTypeConfig = System.getProperty("fararoni.db.type", "EMBEDDED_SQL");
        return create(dbTypeConfig, null);
    }

    public static ProjectKnowledgeBase create(String dbTypeConfig, Path dbPath) {
        DbType type = DbType.EMBEDDED_SQL;
        try {
            if (dbTypeConfig != null && !dbTypeConfig.isBlank()) {
                type = DbType.valueOf(dbTypeConfig.toUpperCase().replace("-", "_"));
            }
        } catch (IllegalArgumentException e) {
            LOG.warning("[FACTORY] Tipo de DB desconocido: " + dbTypeConfig + ". Usando default EMBEDDED_SQL.");
        }

        LOG.info("[FACTORY] Inicializando KnowledgeBase tipo: " + type);

        return switch (type) {
            case EMBEDDED_SQL -> {
                if (dbPath != null) {
                    yield new IndexStore(dbPath);
                } else {
                    yield new IndexStore();
                }
            }

            case IN_MEMORY -> IndexStore.inMemory();

            case ENTERPRISE -> {
                LOG.warning("[FACTORY] Enterprise DB no implementada aún. Usando fallback EMBEDDED_SQL.");
                yield new IndexStore();
            }
        };
    }

    public static ProjectKnowledgeBase createForTesting() {
        LOG.info("[FACTORY] Creando KnowledgeBase IN_MEMORY para testing");
        return IndexStore.inMemory();
    }
}
