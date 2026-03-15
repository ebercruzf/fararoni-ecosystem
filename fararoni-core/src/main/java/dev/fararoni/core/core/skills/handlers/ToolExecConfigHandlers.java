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
package dev.fararoni.core.core.skills.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.core.cli.ConfigCommand;
import dev.fararoni.core.cli.ConfigCommand.ConfigKeyInfo;
import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.core.skills.ToolExecutionResult;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ToolExecConfigHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecConfigHandlers.class.getName());
    private final ObjectMapper mapper;

    public ToolExecConfigHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ToolExecutionResult handleConfigSet(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("key")) {
            return new ToolExecutionResult(false,
                "Error: config_set requiere parametro 'key'",
                Optional.empty(), Optional.empty());
        }
        if (!args.has("value")) {
            return new ToolExecutionResult(false,
                "Error: config_set requiere parametro 'value'",
                Optional.empty(), Optional.empty());
        }

        String key = args.get("key").asText().trim();
        String value = args.get("value").asText();

        ConfigKeyInfo keyInfo = ConfigCommand.AVAILABLE_KEYS.get(key);
        if (keyInfo == null) {
            String availableKeys = String.join(", ", ConfigCommand.AVAILABLE_KEYS.keySet());
            return new ToolExecutionResult(false,
                "Error: Clave '" + key + "' no es una clave de configuracion valida. " +
                "Claves permitidas: " + availableKeys,
                Optional.empty(), Optional.empty());
        }

        if (keyInfo.secure()) {
            logger.info("[CONFIG_SET] Configurando: " + key + " = ****");
        } else {
            logger.info("[CONFIG_SET] Configurando: " + key + " = " + value);
        }

        try {
            SecureConfigService config = SecureConfigService.getInstance();

            if (keyInfo.secure()) {
                config.setSecureProperty(keyInfo.internalKey(), value);
            } else {
                config.setProperty(keyInfo.internalKey(), value);
            }

            String displayValue = keyInfo.secure()
                ? maskSecret(value)
                : value;

            String confirmation = String.format(
                "OK: %s = %s%s",
                key, displayValue,
                keyInfo.secure() ? " (encriptado AES-256-GCM)" : ""
            );

            return new ToolExecutionResult(true, confirmation,
                Optional.of(confirmation), Optional.of(key));
        } catch (Exception e) {
            logger.warning("[CONFIG_SET] Error configurando " + key + ": " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error al configurar '" + key + "': " + e.getMessage(),
                Optional.empty(), Optional.of(key));
        }
    }

    private static String maskSecret(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
