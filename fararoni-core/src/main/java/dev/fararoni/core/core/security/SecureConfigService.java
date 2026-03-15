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
package dev.fararoni.core.core.security;

import dev.fararoni.core.core.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SecureConfigService {
    private static final Logger log = LoggerFactory.getLogger(SecureConfigService.class);

    private static volatile SecureConfigService instance;
    private static final Object LOCK = new Object();

    private static final String CONFIG_FILENAME = "secure-config.properties";
    private static final String ENCRYPTED_PREFIX = "ENC:";

    public static final String KEY_API_KEY = "api.key";
    public static final String KEY_API_KEY_BACKUP = "api.key.backup";
    public static final String KEY_OPENAI_API_KEY = "openai.api.key";
    public static final String KEY_ANTHROPIC_API_KEY = "anthropic.api.key";
    public static final String KEY_HARDWARE_ID = "hardware.id";

    private final Path configFilePath;
    private final Properties properties;
    private final String masterPassword;
    private long lastModified;

    private SecureConfigService() {
        this(WorkspaceManager.getInstance().getWorkspaceDir().resolve(CONFIG_FILENAME));
    }

    SecureConfigService(Path configFilePath) {
        this.configFilePath = configFilePath;
        this.properties = new Properties();
        this.masterPassword = HardwareIdGenerator.generateHardwareId();
        this.lastModified = 0;

        loadConfig();
        verifyHardwareId();

        log.info("[SecureConfigService] Initialized with config at: {}", configFilePath);
    }

    public static SecureConfigService getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new SecureConfigService();
                }
            }
        }
        return instance;
    }

    public String getApiKey() {
        return getSecureProperty(KEY_API_KEY);
    }

    public void setApiKey(String apiKey) {
        setSecureProperty(KEY_API_KEY, apiKey);
    }

    public String getBackupApiKey() {
        return getSecureProperty(KEY_API_KEY_BACKUP);
    }

    public void setBackupApiKey(String apiKey) {
        setSecureProperty(KEY_API_KEY_BACKUP, apiKey);
    }

    public String getOpenAiApiKey() {
        return getSecureProperty(KEY_OPENAI_API_KEY);
    }

    public void setOpenAiApiKey(String apiKey) {
        setSecureProperty(KEY_OPENAI_API_KEY, apiKey);
    }

    public String getAnthropicApiKey() {
        return getSecureProperty(KEY_ANTHROPIC_API_KEY);
    }

    public void setAnthropicApiKey(String apiKey) {
        setSecureProperty(KEY_ANTHROPIC_API_KEY, apiKey);
    }

    public String getSecureProperty(String key) {
        reloadIfModified();

        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) {
            return null;
        }

        if (value.startsWith(ENCRYPTED_PREFIX)) {
            try {
                String encrypted = value.substring(ENCRYPTED_PREFIX.length());
                return AesHelper.decrypt(encrypted, masterPassword);
            } catch (SecurityException e) {
                log.error("[SecureConfigService] Failed to decrypt '{}': {}", key, e.getMessage());
                return null;
            }
        }

        log.warn("[SecureConfigService] Property '{}' is not encrypted, consider re-saving", key);
        return value;
    }

    public void setSecureProperty(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        if (value == null || value.isEmpty()) {
            properties.remove(key);
        } else {
            String encrypted = AesHelper.encrypt(value, masterPassword);
            properties.setProperty(key, ENCRYPTED_PREFIX + encrypted);
        }

        saveConfig();
    }

    public String getProperty(String key) {
        reloadIfModified();
        return properties.getProperty(key);
    }

    public void setProperty(String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
        saveConfig();
    }

    public boolean hasProperty(String key) {
        reloadIfModified();
        return properties.containsKey(key);
    }

    public void removeProperty(String key) {
        properties.remove(key);
        saveConfig();
    }

    private void loadConfig() {
        if (!Files.exists(configFilePath)) {
            log.info("[SecureConfigService] Config file not found, will create on first save");
            return;
        }

        try (InputStream is = Files.newInputStream(configFilePath)) {
            properties.load(is);
            lastModified = Files.getLastModifiedTime(configFilePath).toMillis();
            log.debug("[SecureConfigService] Loaded {} properties", properties.size());
        } catch (IOException e) {
            log.error("[SecureConfigService] Failed to load config: {}", e.getMessage());
        }
    }

    private synchronized void saveConfig() {
        try {
            Files.createDirectories(configFilePath.getParent());

            try (OutputStream os = Files.newOutputStream(configFilePath)) {
                properties.store(os, "Fararoni Secure Configuration - DO NOT EDIT MANUALLY");
            }

            lastModified = Files.getLastModifiedTime(configFilePath).toMillis();
            log.debug("[SecureConfigService] Saved {} properties", properties.size());
        } catch (IOException e) {
            log.error("[SecureConfigService] Failed to save config: {}", e.getMessage());
            throw new RuntimeException("Failed to save secure config", e);
        }
    }

    private void reloadIfModified() {
        try {
            if (Files.exists(configFilePath)) {
                long currentModified = Files.getLastModifiedTime(configFilePath).toMillis();
                if (currentModified > lastModified) {
                    log.info("[SecureConfigService] Config file modified, reloading...");
                    loadConfig();
                }
            }
        } catch (IOException e) {
            log.warn("[SecureConfigService] Could not check file modification time: {}", e.getMessage());
        }
    }

    private void verifyHardwareId() {
        String savedHwid = properties.getProperty(KEY_HARDWARE_ID);
        String currentHwid = HardwareIdGenerator.generateHardwareId();

        if (savedHwid == null) {
            properties.setProperty(KEY_HARDWARE_ID, currentHwid);
            saveConfig();
            log.info("[SecureConfigService] Hardware ID saved for this machine");
        } else if (!savedHwid.equals(currentHwid)) {
            log.warn("[SecureConfigService] Hardware ID mismatch! Config may have been copied from another machine.");
            log.warn("[SecureConfigService] Encrypted values will not be readable. Re-configure API keys.");
        }
    }

    public Path getConfigFilePath() {
        return configFilePath;
    }

    public boolean hasAnyApiKey() {
        return getApiKey() != null ||
               getBackupApiKey() != null ||
               getOpenAiApiKey() != null ||
               getAnthropicApiKey() != null;
    }

    public String getBestAvailableApiKey() {
        String key = getApiKey();
        if (key != null) return key;

        key = getBackupApiKey();
        if (key != null) return key;

        key = getOpenAiApiKey();
        if (key != null) return key;

        return getAnthropicApiKey();
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            instance = null;
        }
    }
}
