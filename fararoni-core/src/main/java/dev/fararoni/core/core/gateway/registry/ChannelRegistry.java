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
package dev.fararoni.core.core.gateway.registry;

import dev.fararoni.core.core.gateway.routing.OmniChannelRouter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ChannelRegistry {
    private static final Logger LOG = Logger.getLogger(ChannelRegistry.class.getName());

    private static final String TOPIC_PREFIX = "agency.input.";

    private final AtomicReference<List<String>> activeTopics;

    private final AtomicReference<Long> lastReloadTimestamp;

    private final Path modulesYmlPath;

    private static volatile ChannelRegistry instance;

    public ChannelRegistry(Path modulesYmlPath) {
        this.modulesYmlPath = modulesYmlPath;
        this.activeTopics = new AtomicReference<>(List.of());
        this.lastReloadTimestamp = new AtomicReference<>(0L);
    }

    public static ChannelRegistry getInstance() {
        if (instance == null) {
            synchronized (ChannelRegistry.class) {
                if (instance == null) {
                    Path defaultPath = Path.of(
                        System.getProperty("user.home"),
                        ".fararoni", "config", "modules.yml"
                    );
                    instance = new ChannelRegistry(defaultPath);
                }
            }
        }
        return instance;
    }

    public static ChannelRegistry configure(Path modulesYmlPath) {
        synchronized (ChannelRegistry.class) {
            instance = new ChannelRegistry(modulesYmlPath);
            return instance;
        }
    }

    public void initialize() {
        LOG.info("[ChannelRegistry] Inicializando registro de canales...");
        LOG.info("[ChannelRegistry] Archivo: " + modulesYmlPath);
        reloadState();
    }

    public synchronized boolean reloadState() {
        LOG.info("[ChannelRegistry] Iniciando recarga de configuracion (Control Plane)...");

        try {
            if (!Files.exists(modulesYmlPath)) {
                LOG.warning("[ChannelRegistry] Archivo no encontrado: " + modulesYmlPath);
                LOG.warning("[ChannelRegistry] Usando lista de canales por defecto");
                activeTopics.set(getDefaultTopics());
                return false;
            }

            String content = Files.readString(modulesYmlPath);
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(content);

            if (root == null) {
                LOG.warning("[ChannelRegistry] Archivo YAML vacio o malformado");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> channels = (Map<String, Object>) root.get("channels");

            if (channels == null || channels.isEmpty()) {
                LOG.warning("[ChannelRegistry] Seccion 'channels' no encontrada o vacia");
                activeTopics.set(getDefaultTopics());
                return false;
            }

            List<String> newTopics = new ArrayList<>();

            for (Map.Entry<String, Object> entry : channels.entrySet()) {
                String channelId = entry.getKey();

                if ("cli".equalsIgnoreCase(channelId)) {
                    continue;
                }

                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> channelConfig = (Map<String, Object>) entry.getValue();

                Object enabledObj = channelConfig.get("enabled");
                boolean enabled = enabledObj instanceof Boolean b ? b : true;

                if (enabled) {
                    String topic = TOPIC_PREFIX + channelId.toLowerCase();
                    newTopics.add(topic);
                    LOG.fine("[ChannelRegistry] Canal habilitado: " + channelId + " -> " + topic);
                }
            }

            activeTopics.set(List.copyOf(newTopics));
            lastReloadTimestamp.set(System.currentTimeMillis());

            LOG.info("[ChannelRegistry] Recarga exitosa. Topics activos: " + newTopics.size());
            LOG.info("[ChannelRegistry] Topics: " + newTopics);
            return true;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[ChannelRegistry] Error leyendo modules.yml: " + e.getMessage(), e);
            LOG.warning("[ChannelRegistry] Manteniendo configuracion anterior");
            return false;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[ChannelRegistry] Error parseando modules.yml: " + e.getMessage(), e);
            LOG.warning("[ChannelRegistry] Manteniendo configuracion anterior");
            return false;
        }
    }

    public List<String> getInputTopics() {
        return activeTopics.get();
    }

    public long getLastReloadTimestamp() {
        return lastReloadTimestamp.get();
    }

    public boolean isTopicActive(String topic) {
        return activeTopics.get().contains(topic);
    }

    public int getActiveChannelCount() {
        return activeTopics.get().size();
    }

    private List<String> getDefaultTopics() {
        return List.of(
            "agency.input.whatsapp",
            "agency.input.telegram",
            "agency.input.slack",
            "agency.input.discord",
            "agency.input.imessage",
            "agency.input.web"
        );
    }

    public String getStatusJson() {
        List<String> topics = activeTopics.get();
        long lastReload = lastReloadTimestamp.get();

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"activeChannels\":").append(topics.size()).append(",");
        json.append("\"lastReloadTimestamp\":").append(lastReload).append(",");
        json.append("\"topics\":[");
        for (int i = 0; i < topics.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(topics.get(i)).append("\"");
        }
        json.append("],");
        json.append("\"configPath\":\"").append(modulesYmlPath).append("\"");
        json.append("}");

        return json.toString();
    }
}
