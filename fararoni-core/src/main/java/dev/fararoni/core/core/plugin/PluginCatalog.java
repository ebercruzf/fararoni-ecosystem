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
package dev.fararoni.core.core.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PluginCatalog {

    private static final Logger LOGGER = Logger.getLogger(PluginCatalog.class.getName());
    private static final String LOG_PREFIX = "[PluginCatalog] ";

    private static final String CATALOG_URL =
        System.getenv().getOrDefault("FARARONI_PLUGIN_CATALOG_URL",
            "https://releases.fararoni.dev/fararoni/plugins.json");

    private static final Duration CACHE_TTL = Duration.ofHours(4);

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final Path cacheDir;
    private final Path cachePath;
    private final HttpClient httpClient;
    private final Gson gson;

    private final Map<String, PluginInfo> pluginCache;

    private final Map<String, String> commandToPlugin;

    private volatile Instant lastLoaded;

    public PluginCatalog() {
        this(Path.of(System.getProperty("user.home"), ".llm-fararoni", "plugins"));
    }

    public PluginCatalog(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.cachePath = cacheDir.resolve("catalog.json");
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        this.pluginCache = new ConcurrentHashMap<>();
        this.commandToPlugin = new ConcurrentHashMap<>();
        this.lastLoaded = null;
    }

    public List<PluginInfo> getAvailablePlugins() {
        ensureLoaded();
        return List.copyOf(pluginCache.values());
    }

    public Optional<PluginInfo> findPlugin(String pluginId) {
        ensureLoaded();
        return Optional.ofNullable(pluginCache.get(pluginId.toLowerCase()));
    }

    public Optional<PluginInfo> findPluginForCommand(String trigger) {
        ensureLoaded();
        String pluginId = commandToPlugin.get(trigger.toLowerCase());
        if (pluginId != null) {
            return Optional.ofNullable(pluginCache.get(pluginId));
        }
        return Optional.empty();
    }

    public Map<String, String> getCommandToPluginMap() {
        ensureLoaded();
        return Map.copyOf(commandToPlugin);
    }

    public boolean refresh() {
        return loadFromRemote();
    }

    private void ensureLoaded() {
        if (lastLoaded == null || isCacheExpired()) {
            loadCatalog();
        }
    }

    private boolean isCacheExpired() {
        return lastLoaded != null &&
            Duration.between(lastLoaded, Instant.now()).compareTo(CACHE_TTL) > 0;
    }

    private synchronized void loadCatalog() {
        if (loadFromCache()) {
            LOGGER.fine(LOG_PREFIX + "Catalogo cargado del cache local");
            return;
        }

        if (loadFromRemote()) {
            LOGGER.info(LOG_PREFIX + "Catalogo actualizado desde servidor");
            return;
        }

        loadEmbeddedCatalog();
        LOGGER.warning(LOG_PREFIX + "Usando catalogo embebido (offline)");
    }

    private boolean loadFromCache() {
        if (!Files.exists(cachePath)) {
            return false;
        }

        try {
            Instant lastModified = Files.getLastModifiedTime(cachePath).toInstant();
            if (Duration.between(lastModified, Instant.now()).compareTo(CACHE_TTL) > 0) {
                return false;
            }

            String json = Files.readString(cachePath);
            return parseCatalog(json);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX + "Error leyendo cache", e);
            return false;
        }
    }

    private boolean loadFromRemote() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CATALOG_URL))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.fine(LOG_PREFIX + "Servidor retorno HTTP " + response.statusCode());
                return false;
            }

            String json = response.body();

            saveToCache(json);

            return parseCatalog(json);

        } catch (java.nio.channels.UnresolvedAddressException e) {
            LOGGER.fine(LOG_PREFIX + "Servidor no alcanzable (DNS). Iniciando en modo offline.");
            return false;

        } catch (java.net.ConnectException e) {
            LOGGER.fine(LOG_PREFIX + "Servidor no responde. Iniciando en modo offline.");
            return false;

        } catch (java.net.http.HttpTimeoutException e) {
            LOGGER.fine(LOG_PREFIX + "Timeout de conexion. Iniciando en modo offline.");
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.fine(LOG_PREFIX + "Descarga interrumpida.");
            return false;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX + "Error inesperado descargando catalogo: " + e.getMessage());
            return false;
        }
    }

    private void saveToCache(String json) {
        try {
            Files.createDirectories(cacheDir);
            Files.writeString(cachePath, json);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX + "Error guardando cache", e);
        }
    }

    private boolean parseCatalog(String json) {
        try {
            Type type = new TypeToken<CatalogWrapper>(){}.getType();
            CatalogWrapper wrapper = gson.fromJson(json, type);

            if (wrapper == null || wrapper.plugins == null) {
                return false;
            }

            pluginCache.clear();
            commandToPlugin.clear();

            for (PluginInfo plugin : wrapper.plugins) {
                pluginCache.put(plugin.id().toLowerCase(), plugin);

                for (String cmd : plugin.commands()) {
                    commandToPlugin.put(cmd.toLowerCase(), plugin.id().toLowerCase());
                }
            }

            lastLoaded = Instant.now();
            LOGGER.info(LOG_PREFIX + "Cargados " + pluginCache.size() + " plugins, " +
                commandToPlugin.size() + " comandos");

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX + "Error parseando catalogo", e);
            return false;
        }
    }

    private void loadEmbeddedCatalog() {
        pluginCache.clear();
        commandToPlugin.clear();

        PluginInfo vision = new PluginInfo(
            "vision",
            "Vision Extension",
            "Analisis de imagenes con LLaVA 1.5 local",
            "1.0.0",
            "https://releases.fararoni.dev/fararoni/plugins/vision-1.0.0.jar",
            "pending",
            52428800L,
            List.of("/vision", "/vision-status", "/clipboard", "/ocr"),
            List.of()
        );

        PluginInfo voice = new PluginInfo(
            "voice",
            "Voice Extension",
            "Transcripcion de audio con Whisper local",
            "1.0.0",
            "https://releases.fararoni.dev/fararoni/plugins/voice-1.0.0.jar",
            "pending",
            104857600L,
            List.of("/voice"),
            List.of()
        );

        PluginInfo audioCore = new PluginInfo(
            "audio-core",
            "Audio Core Extension",
            "Text-to-Speech con Qwen3-TTS-1.7B (ONNX + CoreAudio)",
            "1.0.0",
            "https://releases.fararoni.dev/fararoni/plugins/audio-core-1.0.0.jar",
            "pending",
            83886080L,
            List.of("/tts", "/hablar", "/speak", "/audio-status", "/clonar-voz", "/clone-voice"),
            List.of()
        );

        pluginCache.put("vision", vision);
        pluginCache.put("voice", voice);
        pluginCache.put("audio-core", audioCore);

        for (PluginInfo plugin : pluginCache.values()) {
            for (String cmd : plugin.commands()) {
                commandToPlugin.put(cmd.toLowerCase(), plugin.id().toLowerCase());
            }
        }

        lastLoaded = Instant.now();
    }

    private static class CatalogWrapper {
        String version;
        String lastUpdated;
        List<PluginInfo> plugins;
    }
}
