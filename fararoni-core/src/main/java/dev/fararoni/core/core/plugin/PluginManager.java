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

import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.core.cli.CommandProvider;
import dev.fararoni.core.core.download.DownloadProgress;
import dev.fararoni.core.core.download.DownloadState;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PluginManager {

    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());
    private static final String LOG_PREFIX = "[PluginManager] ";

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    private static volatile PluginManager INSTANCE;

    private final Path libsDir;
    private final Path pluginsDir;
    private final PluginCatalog catalog;
    private final HttpClient httpClient;

    private final Map<String, URLClassLoader> loadedPlugins;

    private final Map<String, ConsoleCommand> pluginCommands;

    private final AtomicBoolean installing;

    private PluginManager() {
        Path homeDir = Path.of(System.getProperty("user.home"), ".llm-fararoni");
        this.libsDir = homeDir.resolve("libs");
        this.pluginsDir = homeDir.resolve("plugins");
        this.catalog = new PluginCatalog(pluginsDir);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.loadedPlugins = new ConcurrentHashMap<>();
        this.pluginCommands = new ConcurrentHashMap<>();
        this.installing = new AtomicBoolean(false);

        try {
            Files.createDirectories(libsDir);
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX + "Error creando directorios", e);
        }

        loadInstalledPlugins();
    }

    public static PluginManager getInstance() {
        if (INSTANCE == null) {
            synchronized (PluginManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PluginManager();
                }
            }
        }
        return INSTANCE;
    }

    public boolean isDownloadableCommand(String trigger) {
        return catalog.getCommandToPluginMap().containsKey(trigger.toLowerCase());
    }

    public Optional<PluginInfo> getPluginForCommand(String trigger) {
        return catalog.findPluginForCommand(trigger);
    }

    public boolean isInstalled(String pluginId) {
        String id = pluginId.toLowerCase();
        return loadedPlugins.containsKey(id) ||
               Files.exists(libsDir.resolve("fararoni-extension-" + id + ".jar"));
    }

    public boolean isLoaded(String pluginId) {
        return loadedPlugins.containsKey(pluginId.toLowerCase());
    }

    public Optional<ConsoleCommand> getPluginCommand(String trigger) {
        return Optional.ofNullable(pluginCommands.get(trigger.toLowerCase()));
    }

    public boolean installPlugin(String pluginId, Consumer<DownloadProgress> progressCallback)
            throws PluginInstallException {

        if (!installing.compareAndSet(false, true)) {
            throw new PluginInstallException("Ya hay una instalacion en progreso");
        }

        try {
            String id = pluginId.toLowerCase();

            PluginInfo info = catalog.findPlugin(id)
                .orElseThrow(() -> new PluginInstallException(
                    "Plugin no encontrado: " + pluginId));

            LOGGER.info(LOG_PREFIX + "Instalando plugin: " + info.name() +
                " v" + info.version());

            if (progressCallback != null) {
                progressCallback.accept(new DownloadProgress(
                    DownloadState.CONNECTING, 0, info.sizeBytes(), 0,
                    "Conectando con servidor..."));
            }

            Path jarPath = libsDir.resolve(info.getJarFileName());

            downloadPlugin(info, jarPath, progressCallback);

            if (!"pending".equals(info.sha256())) {
                if (progressCallback != null) {
                    progressCallback.accept(new DownloadProgress(
                        DownloadState.VERIFYING, info.sizeBytes(), info.sizeBytes(), 0,
                        "Verificando integridad..."));
                }
                verifyChecksum(jarPath, info.sha256());
            }

            if (progressCallback != null) {
                progressCallback.accept(new DownloadProgress(
                    DownloadState.COMPLETED, info.sizeBytes(), info.sizeBytes(), 0,
                    "Cargando plugin..."));
            }

            loadPlugin(id, jarPath);

            LOGGER.info(LOG_PREFIX + "Plugin instalado: " + info.name());
            return true;

        } finally {
            installing.set(false);
        }
    }

    public boolean installPlugin(String pluginId) throws PluginInstallException {
        return installPlugin(pluginId, null);
    }

    private void downloadPlugin(PluginInfo info, Path destPath,
            Consumer<DownloadProgress> progressCallback) throws PluginInstallException {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(info.downloadUrl()))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new PluginInstallException(
                    "Error descargando plugin: HTTP " + response.statusCode());
            }

            long contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(info.sizeBytes());

            try (InputStream in = response.body()) {
                Path tempFile = Files.createTempFile("fararoni-plugin-", ".jar");

                byte[] buffer = new byte[8192];
                long totalRead = 0;
                int bytesRead;

                try (var out = Files.newOutputStream(tempFile)) {
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        if (progressCallback != null) {
                            double speed = 0;
                            progressCallback.accept(new DownloadProgress(
                                DownloadState.DOWNLOADING,
                                totalRead, contentLength, speed,
                                String.format("Descargando... %.1f%%",
                                    (totalRead * 100.0) / contentLength)));
                        }
                    }
                }

                Files.move(tempFile, destPath, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException | InterruptedException e) {
            throw new PluginInstallException("Error descargando plugin: " + e.getMessage(), e);
        }
    }

    private void verifyChecksum(Path jarPath, String expectedSha256)
            throws PluginInstallException {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(jarPath);
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String actualHash = sb.toString();

            if (!actualHash.equalsIgnoreCase(expectedSha256)) {
                Files.deleteIfExists(jarPath);
                throw new PluginInstallException(
                    "Verificacion de integridad fallida. JAR corrupto o modificado.");
            }

        } catch (Exception e) {
            if (e instanceof PluginInstallException) {
                throw (PluginInstallException) e;
            }
            throw new PluginInstallException("Error verificando checksum: " + e.getMessage(), e);
        }
    }

    private void loadPlugin(String pluginId, Path jarPath) throws PluginInstallException {
        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(
                new URL[] { jarUrl },
                getClass().getClassLoader()
            );

            ServiceLoader<CommandProvider> loader =
                ServiceLoader.load(CommandProvider.class, classLoader);

            boolean found = false;
            for (CommandProvider provider : loader) {
                if (!provider.isEnabled()) {
                    continue;
                }

                LOGGER.info(LOG_PREFIX + "Cargando comandos de " + provider.getProviderName());

                for (ConsoleCommand cmd : provider.provideConsoleCommands()) {
                    pluginCommands.put(cmd.getTrigger().toLowerCase(), cmd);

                    for (String alias : cmd.getAliases()) {
                        pluginCommands.put(alias.toLowerCase(), cmd);
                    }

                    LOGGER.fine(LOG_PREFIX + "Registrado comando: " + cmd.getTrigger());
                }
                found = true;
            }

            if (!found) {
                LOGGER.warning(LOG_PREFIX + "Plugin no contiene CommandProvider: " + pluginId);
            }

            loadedPlugins.put(pluginId, classLoader);

        } catch (Exception e) {
            throw new PluginInstallException("Error cargando plugin: " + e.getMessage(), e);
        }
    }

    private void loadInstalledPlugins() {
        try {
            if (!Files.exists(libsDir)) {
                return;
            }

            Files.list(libsDir)
                .filter(p -> p.toString().endsWith(".jar"))
                .filter(p -> p.getFileName().toString().startsWith("fararoni-extension-"))
                .forEach(jarPath -> {
                    try {
                        String fileName = jarPath.getFileName().toString();
                        String withoutPrefix = fileName.replace("fararoni-extension-", "");
                        String pluginId = withoutPrefix.substring(0, withoutPrefix.indexOf('-'));

                        loadPlugin(pluginId, jarPath);
                        LOGGER.info(LOG_PREFIX + "Plugin cargado: " + pluginId);

                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING,
                            LOG_PREFIX + "Error cargando plugin: " + jarPath, e);
                    }
                });

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX + "Error listando plugins", e);
        }
    }

    public boolean uninstallPlugin(String pluginId) {
        String id = pluginId.toLowerCase();

        URLClassLoader loader = loadedPlugins.remove(id);
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, LOG_PREFIX + "Error cerrando classloader", e);
            }
        }

        PluginInfo info = catalog.findPlugin(id).orElse(null);
        if (info != null) {
            for (String cmd : info.commands()) {
                pluginCommands.remove(cmd.toLowerCase());
            }
        }

        try {
            Path jarPath = libsDir.resolve("fararoni-extension-" + id + ".jar");
            Files.deleteIfExists(jarPath);
            LOGGER.info(LOG_PREFIX + "Plugin desinstalado: " + id);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, LOG_PREFIX + "Error eliminando JAR", e);
            return false;
        }
    }

    public List<PluginInfo> getAvailablePlugins() {
        return catalog.getAvailablePlugins();
    }

    public boolean refreshCatalog() {
        return catalog.refresh();
    }

    public Map<String, ConsoleCommand> getPluginCommands() {
        return Map.copyOf(pluginCommands);
    }

    public static class PluginInstallException extends Exception {
        public PluginInstallException(String message) {
            super(message);
        }

        public PluginInstallException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
