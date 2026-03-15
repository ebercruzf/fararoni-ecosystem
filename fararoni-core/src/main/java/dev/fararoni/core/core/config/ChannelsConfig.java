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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelsConfig {
    private static final Logger LOG = Logger.getLogger(ChannelsConfig.class.getName());

    private static final String DEFAULT_CONFIG_PATH = System.getProperty("user.home")
        + "/.fararoni/config/channels.yaml";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ChannelSettings channels = new ChannelSettings();

    public ChannelSettings getChannels() {
        return channels;
    }

    public void setChannels(ChannelSettings channels) {
        this.channels = channels;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChannelSettings {
        private WhatsAppConfig whatsapp = new WhatsAppConfig();
        private MatrixConfig matrix = new MatrixConfig();
        private TelegramConfig telegram = new TelegramConfig();

        public WhatsAppConfig getWhatsapp() {
            return whatsapp;
        }

        public void setWhatsapp(WhatsAppConfig whatsapp) {
            this.whatsapp = whatsapp;
        }

        public MatrixConfig getMatrix() {
            return matrix;
        }

        public void setMatrix(MatrixConfig matrix) {
            this.matrix = matrix;
        }

        public TelegramConfig getTelegram() {
            return telegram;
        }

        public void setTelegram(TelegramConfig telegram) {
            this.telegram = telegram;
        }

        public List<String> getEnabledChannels() {
            List<String> enabled = new ArrayList<>();
            if (whatsapp.isEnabled()) enabled.add("whatsapp");
            if (matrix.isEnabled()) enabled.add("matrix");
            if (telegram.isEnabled()) enabled.add("telegram");
            return enabled;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WhatsAppConfig {
        private boolean enabled = false;
        private String sessionPath = System.getProperty("user.home") + "/.fararoni/data/whatsapp-session";
        private int rateLimitPerSecond = 10;
        private int reconnectDelayMs = 5000;
        private int maxReconnectAttempts = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSessionPath() {
            return sessionPath;
        }

        public void setSessionPath(String sessionPath) {
            this.sessionPath = sessionPath;
        }

        public int getRateLimitPerSecond() {
            return rateLimitPerSecond;
        }

        public void setRateLimitPerSecond(int rateLimitPerSecond) {
            this.rateLimitPerSecond = rateLimitPerSecond;
        }

        public int getReconnectDelayMs() {
            return reconnectDelayMs;
        }

        public void setReconnectDelayMs(int reconnectDelayMs) {
            this.reconnectDelayMs = reconnectDelayMs;
        }

        public int getMaxReconnectAttempts() {
            return maxReconnectAttempts;
        }

        public void setMaxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatrixConfig {
        private boolean enabled = false;
        private String homeserver = "https://matrix.example.com";
        private String accessToken;
        private String roomId;
        private String userId;
        private int syncTimeoutMs = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHomeserver() {
            return homeserver;
        }

        public void setHomeserver(String homeserver) {
            this.homeserver = homeserver;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRoomId() {
            return roomId;
        }

        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public int getSyncTimeoutMs() {
            return syncTimeoutMs;
        }

        public void setSyncTimeoutMs(int syncTimeoutMs) {
            this.syncTimeoutMs = syncTimeoutMs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramConfig {
        private boolean enabled = false;
        private String botToken;
        private String botUsername;
        private int pollingTimeoutSec = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getBotUsername() {
            return botUsername;
        }

        public void setBotUsername(String botUsername) {
            this.botUsername = botUsername;
        }

        public int getPollingTimeoutSec() {
            return pollingTimeoutSec;
        }

        public void setPollingTimeoutSec(int pollingTimeoutSec) {
            this.pollingTimeoutSec = pollingTimeoutSec;
        }
    }

    public static ChannelsConfig load() {
        return load(Path.of(DEFAULT_CONFIG_PATH));
    }

    public static ChannelsConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            LOG.info(() -> "[ChannelsConfig] Archivo no existe, usando defaults: " + configPath);
            return new ChannelsConfig();
        }

        try {
            ChannelsConfig config = YAML_MAPPER.readValue(configPath.toFile(), ChannelsConfig.class);
            LOG.info(() -> "[ChannelsConfig] Cargado desde: " + configPath);
            return config;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[ChannelsConfig] Error cargando, usando defaults", e);
            return new ChannelsConfig();
        }
    }

    public void save() throws IOException {
        save(Path.of(DEFAULT_CONFIG_PATH));
    }

    public void save(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
        YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), this);
        LOG.info(() -> "[ChannelsConfig] Guardado en: " + configPath);
    }

    public static Path getDefaultPath() {
        return Path.of(DEFAULT_CONFIG_PATH);
    }

    public static void createExample(Path path) throws IOException {
        String example = """
            # ═══════════════════════════════════════════════════════════════════
            # CONFIGURACION DE CANALES - FARARONI FASE 45
            # ═══════════════════════════════════════════════════════════════════
            #
            # Este archivo define los canales de comunicacion externos
            # (WhatsApp, Matrix, Telegram).
            #
            # Ubicacion: ~/.fararoni/config/channels.yaml
            #
            # IMPORTANTE: No compartas este archivo - contiene tokens sensibles.
            # ═══════════════════════════════════════════════════════════════════

            channels:
              # ─────────────────────────────────────────────────────────────────
              # WHATSAPP - Canal "The Diplomat" (UNTRUSTED_EXTERNAL)
              # Requiere libreria Cobalt o whatsapp-web.js bridge
              # ─────────────────────────────────────────────────────────────────
              whatsapp:
                enabled: false

                # Ruta donde se guarda la sesion de WhatsApp Web
                session_path: "~/.fararoni/data/whatsapp-session"

                # Limite de mensajes por segundo (evitar ban)
                rate_limit_per_second: 10

                # Tiempo de espera antes de reconectar (ms)
                reconnect_delay_ms: 5000

                # Intentos maximos de reconexion
                max_reconnect_attempts: 5

              # ─────────────────────────────────────────────────────────────────
              # MATRIX - Canal "The Bunker" (SECURE_ENCRYPTED)
              # Servidor Synapse autoalojado con E2EE
              # ─────────────────────────────────────────────────────────────────
              matrix:
                enabled: false

                # URL del servidor Synapse
                homeserver: "https://matrix.example.com"

                # Token de acceso del bot
                # Obtener con: curl -X POST -d '{"type":"m.login.password","user":"bot","password":"xxx"}' https:
                access_token: "syt_xxx..."

                # ID del usuario bot
                user_id: "@fararoni:example.com"

                # Room ID para comandos (crear room privado)
                room_id: "!abc123:example.com"

                # Timeout del long-polling sync (ms)
                sync_timeout_ms: 30000

              # ─────────────────────────────────────────────────────────────────
              # TELEGRAM - Canal "The Mercenary" (UNTRUSTED_EXTERNAL)
              # Bot API oficial
              # ─────────────────────────────────────────────────────────────────
              telegram:
                enabled: false

                # Token del bot (obtener de @BotFather)
                bot_token: "123456789:ABCdefGHIjklMNOpqrsTUVwxyz"

                # Username del bot (sin @)
                bot_username: "fararoni_bot"

                # Timeout del long-polling (segundos)
                polling_timeout_sec: 30
            """;

        Files.createDirectories(path.getParent());
        Files.writeString(path, example);
        LOG.info(() -> "[ChannelsConfig] Ejemplo creado en: " + path);
    }
}
