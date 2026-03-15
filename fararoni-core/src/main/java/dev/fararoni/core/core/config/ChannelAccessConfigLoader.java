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
package dev.fararoni.core.core.config;

import dev.fararoni.core.core.persistence.ChannelContactStore;
import dev.fararoni.core.core.config.ChannelAccessConfig.AllowEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ChannelAccessConfigLoader {
    private static final Logger LOG = Logger.getLogger(ChannelAccessConfigLoader.class.getName());

    private final ChannelContactStore identityStore;

    public ChannelAccessConfigLoader(ChannelContactStore identityStore) {
        this.identityStore = identityStore;
    }

    public int loadFromDefault() {
        return loadFrom(ChannelAccessConfig.getDefaultPath());
    }

    public int loadFrom(Path configPath) {
        if (!Files.exists(configPath)) {
            LOG.info(() -> "[ChannelAccessConfigLoader] No existe " + configPath + ", saltando carga inicial");
            return 0;
        }

        ChannelAccessConfig config = ChannelAccessConfig.load(configPath);
        return applyConfig(config);
    }

    public int applyConfig(ChannelAccessConfig config) {
        int count = 0;
        var settings = config.getSecurity();

        if (settings.getOwner() != null && !settings.getOwner().isBlank()) {
            if (identityStore.getOwner().isEmpty()) {
                identityStore.setOwner(settings.getOwner());
                LOG.info(() -> "[ChannelAccessConfigLoader] Owner cargado desde YAML: " + settings.getOwner());
                count++;
            } else {
                LOG.fine(() -> "[ChannelAccessConfigLoader] Owner ya existe en DB, ignorando YAML");
            }
        }

        if (settings.getAllowlist() != null) {
            for (AllowEntry entry : settings.getAllowlist()) {
                if (entry.getId() != null && !identityStore.isAllowed(entry.getId())) {
                    String note = entry.getNote() != null ? entry.getNote() : "Cargado desde security.yaml";
                    identityStore.addToAllowList(entry.getId(), note);
                    LOG.info(() -> "[ChannelAccessConfigLoader] Contacto cargado: " + entry.getId());
                    count++;
                }
            }
        }

        if (settings.getGroups() != null) {
            for (AllowEntry entry : settings.getGroups()) {
                if (entry.getId() != null && !identityStore.isGroupAllowed(entry.getId())) {
                    String note = entry.getNote() != null ? entry.getNote() : "Cargado desde security.yaml";
                    identityStore.addGroupToAllowList(entry.getId(), note);
                    LOG.info(() -> "[ChannelAccessConfigLoader] Grupo cargado: " + entry.getId());
                    count++;
                }
            }
        }

        if (count > 0) {
            final int totalLoaded = count;
            LOG.info(() -> "[ChannelAccessConfigLoader] Total entradas cargadas: " + totalLoaded);
        }

        return count;
    }

    public static void ensureExampleConfigs() {
        try {
            Path securityPath = ChannelAccessConfig.getDefaultPath();
            if (!Files.exists(securityPath)) {
                ChannelAccessConfig.createExample(securityPath);
                LOG.info("[ChannelAccessConfigLoader] Creado ejemplo: " + securityPath);
            }

            Path channelsPath = ChannelsConfig.getDefaultPath();
            if (!Files.exists(channelsPath)) {
                ChannelsConfig.createExample(channelsPath);
                LOG.info("[ChannelAccessConfigLoader] Creado ejemplo: " + channelsPath);
            }
        } catch (Exception e) {
            LOG.warning("[ChannelAccessConfigLoader] Error creando ejemplos: " + e.getMessage());
        }
    }
}
