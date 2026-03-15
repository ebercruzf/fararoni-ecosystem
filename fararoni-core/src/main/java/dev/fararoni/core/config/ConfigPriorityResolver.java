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
package dev.fararoni.core.config;

import dev.fararoni.core.cli.ConfigCommand;
import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.security.SecureConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ConfigPriorityResolver {
    private static final Logger log = LoggerFactory.getLogger(ConfigPriorityResolver.class);

    private final SecureConfigService configService;

    public ConfigPriorityResolver() {
        this.configService = SecureConfigService.getInstance();
    }

    ConfigPriorityResolver(SecureConfigService configService) {
        this.configService = configService;
    }

    public String resolveApiKey(String cliValue) {
        return resolve("api-key", cliValue);
    }

    public String resolveServerUrl(String cliValue) {
        String resolved = resolve("server-url", cliValue);
        return resolved != null ? resolved : AppDefaults.DEFAULT_SERVER_URL;
    }

    public String resolveModelName(String cliValue) {
        String resolved = resolve("model-name", cliValue);
        if (resolved == null) {
            return AppDefaults.DEFAULT_MODEL_NAME;
        }
        return sanitizeModelName(resolved);
    }

    private String sanitizeModelName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return AppDefaults.DEFAULT_MODEL_NAME;
        }

        String original = rawName;
        String clean = rawName.trim()
                             .replaceAll("~$", "")
                             .replaceAll("\\s+", "-")
                             .replaceAll("[^a-zA-Z0-9._:-]", "");

        if (!clean.equals(original)) {
            log.warn("[SELF-HEAL] Configuracion corrupta detectada ('{}'). Corregida en vuelo a: '{}'",
                     original, clean);
        }

        return clean.isEmpty() ? AppDefaults.DEFAULT_MODEL_NAME : clean;
    }

    public String resolveLlmProvider(String cliValue) {
        return resolve("llm-provider", cliValue);
    }

    @Deprecated(forRemoval = true)
    public String resolveServerUrl(String cliValue, String defaultValue) {
        return resolveServerUrl(cliValue);
    }

    @Deprecated(forRemoval = true)
    public String resolveModelName(String cliValue, String defaultValue) {
        return resolveModelName(cliValue);
    }

    public String resolve(String key, String cliValue) {
        return resolveWithSource(key, cliValue).value();
    }

    public ResolvedValue resolveWithSource(String key, String cliValue) {
        if (isNotEmpty(cliValue)) {
            log.debug("[ConfigResolver] {} resolved from CLI", key);
            return new ResolvedValue(cliValue, Source.CLI);
        }

        var keyInfo = ConfigCommand.AVAILABLE_KEYS.get(key);
        if (keyInfo == null) {
            log.warn("[ConfigResolver] Unknown key: {}", key);
            return ResolvedValue.empty();
        }

        if (keyInfo.envVar() != null) {
            String envValue = System.getenv(keyInfo.envVar());
            if (isNotEmpty(envValue)) {
                log.debug("[ConfigResolver] {} resolved from ENV:{}", key, keyInfo.envVar());
                return new ResolvedValue(envValue, Source.ENVIRONMENT, keyInfo.envVar());
            }
        }

        String fileValue;
        if (keyInfo.secure()) {
            fileValue = configService.getSecureProperty(keyInfo.internalKey());
        } else {
            fileValue = configService.getProperty(keyInfo.internalKey());
        }

        if (isNotEmpty(fileValue)) {
            log.debug("[ConfigResolver] {} resolved from config file", key);
            return new ResolvedValue(fileValue, Source.FILE);
        }

        return ResolvedValue.empty();
    }

    public boolean hasValue(String key) {
        return resolveWithSource(key, null).isPresent();
    }

    public String getSource(String key, String cliValue) {
        return resolveWithSource(key, cliValue).getSourceDescription();
    }

    private static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public enum Source {
        CLI,
        ENVIRONMENT,
        FILE,
        NONE
    }

    public record ResolvedValue(
        String value,
        Source source,
        String envVar
    ) {
        public ResolvedValue(String value, Source source) {
            this(value, source, null);
        }

        public static ResolvedValue empty() {
            return new ResolvedValue(null, Source.NONE, null);
        }

        public boolean isPresent() {
            return value != null && !value.isEmpty();
        }

        public String getSourceDescription() {
            return switch (source) {
                case CLI -> "CLI";
                case ENVIRONMENT -> envVar != null ? "ENV:" + envVar : "ENV";
                case FILE -> "FILE";
                case NONE -> "NONE";
            };
        }

        public Optional<String> toOptional() {
            return Optional.ofNullable(value);
        }
    }
}
