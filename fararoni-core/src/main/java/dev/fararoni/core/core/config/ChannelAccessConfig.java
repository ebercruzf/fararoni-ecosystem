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
public class ChannelAccessConfig {
    private static final Logger LOG = Logger.getLogger(ChannelAccessConfig.class.getName());

    private static final String DEFAULT_CONFIG_PATH = System.getProperty("user.home")
        + "/.fararoni/config/security.yaml";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private SecuritySettings security = new SecuritySettings();

    public SecuritySettings getSecurity() {
        return security;
    }

    public void setSecurity(SecuritySettings security) {
        this.security = security;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecuritySettings {
        private String owner;
        private List<AllowEntry> allowlist = new ArrayList<>();
        private List<AllowEntry> groups = new ArrayList<>();
        private PairingSettings pairing = new PairingSettings();

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public List<AllowEntry> getAllowlist() {
            return allowlist;
        }

        public void setAllowlist(List<AllowEntry> allowlist) {
            this.allowlist = allowlist;
        }

        public List<AllowEntry> getGroups() {
            return groups;
        }

        public void setGroups(List<AllowEntry> groups) {
            this.groups = groups;
        }

        public PairingSettings getPairing() {
            return pairing;
        }

        public void setPairing(PairingSettings pairing) {
            this.pairing = pairing;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AllowEntry {
        private String id;
        private String note;

        public AllowEntry() {}

        public AllowEntry(String id, String note) {
            this.id = id;
            this.note = note;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PairingSettings {
        private int maxPending = 3;
        private int expiryHours = 1;
        private boolean autoApproveOwnerContacts = false;

        public int getMaxPending() {
            return maxPending;
        }

        public void setMaxPending(int maxPending) {
            this.maxPending = maxPending;
        }

        public int getExpiryHours() {
            return expiryHours;
        }

        public void setExpiryHours(int expiryHours) {
            this.expiryHours = expiryHours;
        }

        public boolean isAutoApproveOwnerContacts() {
            return autoApproveOwnerContacts;
        }

        public void setAutoApproveOwnerContacts(boolean autoApproveOwnerContacts) {
            this.autoApproveOwnerContacts = autoApproveOwnerContacts;
        }
    }

    public static ChannelAccessConfig load() {
        return load(Path.of(DEFAULT_CONFIG_PATH));
    }

    public static ChannelAccessConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            LOG.info(() -> "[ChannelAccessConfig] Archivo no existe, usando defaults: " + configPath);
            return new ChannelAccessConfig();
        }

        try {
            ChannelAccessConfig config = YAML_MAPPER.readValue(configPath.toFile(), ChannelAccessConfig.class);
            LOG.info(() -> "[ChannelAccessConfig] Cargado desde: " + configPath);
            return config;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[ChannelAccessConfig] Error cargando, usando defaults", e);
            return new ChannelAccessConfig();
        }
    }

    public void save() throws IOException {
        save(Path.of(DEFAULT_CONFIG_PATH));
    }

    public void save(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
        YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), this);
        LOG.info(() -> "[ChannelAccessConfig] Guardado en: " + configPath);
    }

    public static Path getDefaultPath() {
        return Path.of(DEFAULT_CONFIG_PATH);
    }

    public static void createExample(Path path) throws IOException {
        String example = """
            # ═══════════════════════════════════════════════════════════════════
            # CONFIGURACION DE SEGURIDAD - FARARONI FASE 45
            # ═══════════════════════════════════════════════════════════════════
            #
            # Este archivo define el control de acceso para canales externos
            # (WhatsApp, Telegram, Matrix).
            #
            # Ubicacion: ~/.fararoni/config/security.yaml
            # ═══════════════════════════════════════════════════════════════════

            security:
              # ─────────────────────────────────────────────────────────────────
              # OWNER: Propietario del sistema con acceso total
              # ─────────────────────────────────────────────────────────────────
              owner: "+521234567890"

              # ─────────────────────────────────────────────────────────────────
              # ALLOWLIST: Contactos autorizados
              # Formato WhatsApp: +521234567890
              # Formato Matrix: @user:server.com
              # ─────────────────────────────────────────────────────────────────
              allowlist:
                - id: "+521111111111"
                  note: "Cliente VIP"
                - id: "+522222222222"
                  note: "Soporte Tecnico"
                # - id: "@admin:matrix.local"
                #   note: "Admin Matrix"

              # ─────────────────────────────────────────────────────────────────
              # GROUPS: Grupos autorizados
              # Formato WhatsApp: 120363XXXXXXXXXX@g.us
              # Formato Matrix: !roomid:server.com
              # ─────────────────────────────────────────────────────────────────
              groups:
                # - id: "120363123456789012345@g.us"
                #   note: "Grupo Ventas"
                # - id: "!abc123:matrix.local"
                #   note: "Sala Comandos"

              # ─────────────────────────────────────────────────────────────────
              # PAIRING: Configuracion de emparejamiento
              # ─────────────────────────────────────────────────────────────────
              pairing:
                # Maximo de solicitudes pendientes simultaneas
                max_pending: 3

                # Horas hasta que expira un codigo de pairing
                expiry_hours: 1

                # Auto-aprobar contactos que esten en la lista del owner
                # (Solo aplica si el canal soporta leer contactos)
                auto_approve_owner_contacts: false
            """;

        Files.createDirectories(path.getParent());
        Files.writeString(path, example);
        LOG.info(() -> "[ChannelAccessConfig] Ejemplo creado en: " + path);
    }
}
