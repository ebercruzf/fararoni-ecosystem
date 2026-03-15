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
package dev.fararoni.core.cli;

import dev.fararoni.core.core.security.SecureConfigService;
import dev.fararoni.core.core.workspace.WorkspaceManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.fusesource.jansi.Ansi.ansi;

@Command(
    name = "config",
    description = "Gestionar configuracion de FARARONI",
    mixinStandardHelpOptions = true,
    subcommands = {
        ConfigCommand.SetCommand.class,
        ConfigCommand.GetCommand.class,
        ConfigCommand.ShowCommand.class,
        ConfigCommand.ListCommand.class,
        ConfigCommand.UnsetCommand.class
    }
)
public class ConfigCommand implements Callable<Integer> {
    public static final Map<String, ConfigKeyInfo> AVAILABLE_KEYS = new LinkedHashMap<>();

    static {
        AVAILABLE_KEYS.put("api-key", new ConfigKeyInfo(
            SecureConfigService.KEY_API_KEY,
            "API key principal del servidor LLM",
            true,
            "LLM_API_KEY"
        ));
        AVAILABLE_KEYS.put("api-key-backup", new ConfigKeyInfo(
            SecureConfigService.KEY_API_KEY_BACKUP,
            "API key de respaldo",
            true,
            "LLM_API_KEY_BACKUP"
        ));
        AVAILABLE_KEYS.put("openai-api-key", new ConfigKeyInfo(
            SecureConfigService.KEY_OPENAI_API_KEY,
            "API key de OpenAI",
            true,
            "OPENAI_API_KEY"
        ));
        AVAILABLE_KEYS.put("anthropic-api-key", new ConfigKeyInfo(
            SecureConfigService.KEY_ANTHROPIC_API_KEY,
            "API key de Anthropic",
            true,
            "ANTHROPIC_API_KEY"
        ));

        AVAILABLE_KEYS.put("server-url", new ConfigKeyInfo(
            "server.url",
            "URL del servidor LLM",
            false,
            "LLM_SERVER_URL"
        ));
        AVAILABLE_KEYS.put("model-name", new ConfigKeyInfo(
            "model.name",
            "Nombre del modelo por defecto",
            false,
            "LLM_MODEL_NAME"
        ));
        AVAILABLE_KEYS.put("max-tokens", new ConfigKeyInfo(
            "generation.max_tokens",
            "Maximo tokens a generar",
            false,
            "LLM_MAX_TOKENS"
        ));
        AVAILABLE_KEYS.put("temperature", new ConfigKeyInfo(
            "generation.temperature",
            "Temperature para generacion (0.0-2.0)",
            false,
            "LLM_TEMPERATURE"
        ));
        AVAILABLE_KEYS.put("context-window", new ConfigKeyInfo(
            "context.window_size",
            "Tamano de ventana de contexto",
            false,
            "LLM_CONTEXT_WINDOW"
        ));
        AVAILABLE_KEYS.put("streaming", new ConfigKeyInfo(
            "ui.streaming",
            "Habilitar streaming (true/false)",
            false,
            "LLM_STREAMING"
        ));

        AVAILABLE_KEYS.put("llm-provider", new ConfigKeyInfo(
            "llm.provider",
            "Proveedor LLM (openai, ollama, anthropic, local)",
            false,
            "LLM_PROVIDER"
        ));

        AVAILABLE_KEYS.put("mail-host", new ConfigKeyInfo(
            "mail.host",
            "Servidor SMTP (ej: smtp.gmail.com)",
            false,
            "MAIL_HOST"
        ));
        AVAILABLE_KEYS.put("mail-port", new ConfigKeyInfo(
            "mail.port",
            "Puerto SMTP (587 TLS)",
            false,
            "MAIL_PORT"
        ));
        AVAILABLE_KEYS.put("mail-username", new ConfigKeyInfo(
            "mail.username",
            "Usuario IMAP/SMTP (email)",
            false,
            "MAIL_USERNAME"
        ));
        AVAILABLE_KEYS.put("mail-password", new ConfigKeyInfo(
            "mail.password",
            "Password IMAP/SMTP (encriptado AES-256-GCM)",
            true,
            "MAIL_PASSWORD"
        ));
        AVAILABLE_KEYS.put("mail-imap-host", new ConfigKeyInfo(
            "mail.imap.host",
            "Servidor IMAP (si difiere de SMTP, ej: imap.gmail.com)",
            false,
            "MAIL_IMAP_HOST"
        ));
        AVAILABLE_KEYS.put("mail-imap-port", new ConfigKeyInfo(
            "mail.imap.port",
            "Puerto IMAP (993 SSL)",
            false,
            "MAIL_IMAP_PORT"
        ));
        AVAILABLE_KEYS.put("mail-sender", new ConfigKeyInfo(
            "mail.sender",
            "Email del remitente (si difiere del username)",
            false,
            "MAIL_SENDER"
        ));
        AVAILABLE_KEYS.put("mail-sender-name", new ConfigKeyInfo(
            "mail.sender.name",
            "Nombre del remitente",
            false,
            "MAIL_SENDER_NAME"
        ));
    }

    @Override
    public Integer call() {
        System.out.println(ansi().fgYellow().a("""

            Uso: fararoni config <subcommand>

            Subcomandos disponibles:
              set <key> <value>  - Establecer un valor de configuracion
              get <key>          - Obtener un valor de configuracion
              show               - Mostrar toda la configuracion
              list               - Listar claves disponibles
              unset <key>        - Eliminar una configuracion

            Ejemplos:
              fararoni config set api-key sk-abc123...
              fararoni config show
              fararoni config list

            Para mas ayuda: fararoni config --help
            """).reset());
        return 0;
    }

    @Command(
        name = "set",
        description = "Establecer un valor de configuracion",
        mixinStandardHelpOptions = true
    )
    static class SetCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Nombre de la clave (ej: api-key, server-url)")
        private String key;

        @Parameters(index = "1", description = "Valor a establecer")
        private String value;

        @Override
        public Integer call() {
            ConfigKeyInfo keyInfo = AVAILABLE_KEYS.get(key);

            if (keyInfo == null) {
                System.out.println(ansi().fgRed().a(
                    "Clave desconocida: " + key + "\nUsa 'fararoni config list' para ver claves disponibles."
                ).reset());
                return 1;
            }

            try {
                SecureConfigService config = SecureConfigService.getInstance();

                if (keyInfo.secure) {
                    config.setSecureProperty(keyInfo.internalKey, value);
                } else {
                    config.setProperty(keyInfo.internalKey, value);
                }

                String displayValue = keyInfo.secure ? maskSecret(value) : value;
                System.out.println(ansi().fgGreen().a(
                    String.format("Configuracion guardada: %s = %s", key, displayValue)
                ).reset());

                return 0;
            } catch (Exception e) {
                System.out.println(ansi().fgRed().a(
                    "Error guardando configuracion: " + e.getMessage()
                ).reset());
                return 1;
            }
        }
    }

    @Command(
        name = "get",
        description = "Obtener un valor de configuracion",
        mixinStandardHelpOptions = true
    )
    static class GetCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Nombre de la clave")
        private String key;

        @Option(names = {"--raw"}, description = "Mostrar valor sin enmascarar (solo para no-secrets)")
        private boolean raw;

        @Override
        public Integer call() {
            ConfigKeyInfo keyInfo = AVAILABLE_KEYS.get(key);

            if (keyInfo == null) {
                System.out.println(ansi().fgRed().a(
                    "Clave desconocida: " + key
                ).reset());
                return 1;
            }

            String value = resolveValue(keyInfo);

            if (value == null) {
                System.out.println(ansi().fgYellow().a(
                    key + " = (no configurado)"
                ).reset());
                return 0;
            }

            String displayValue = (keyInfo.secure && !raw) ? maskSecret(value) : value;
            String source = getValueSource(keyInfo);

            System.out.println(ansi().fgCyan().a(
                String.format("%s = %s  [%s]", key, displayValue, source)
            ).reset());

            return 0;
        }
    }

    @Command(
        name = "show",
        description = "Mostrar toda la configuracion actual",
        mixinStandardHelpOptions = true
    )
    static class ShowCommand implements Callable<Integer> {
        @Option(names = {"--sources"}, description = "Mostrar fuente de cada valor")
        private boolean showSources;

        @Override
        public Integer call() {
            System.out.println(ansi().fgCyan().a("\nConfiguracion de FARARONI:\n").reset());

            WorkspaceManager workspace = WorkspaceManager.getInstance();
            System.out.println(ansi().fgBlue().a(
                String.format("  Directorio de datos: %s\n", workspace.getWorkspaceDir())
            ).reset());

            for (var entry : AVAILABLE_KEYS.entrySet()) {
                String key = entry.getKey();
                ConfigKeyInfo info = entry.getValue();
                String value = resolveValue(info);

                String displayValue;
                if (value == null) {
                    displayValue = ansi().fgYellow().a("(no configurado)").reset().toString();
                } else if (info.secure) {
                    displayValue = ansi().fgGreen().a(maskSecret(value)).reset().toString();
                } else {
                    displayValue = value;
                }

                String line = String.format("  %-20s : %s", key, displayValue);

                if (showSources && value != null) {
                    String source = getValueSource(info);
                    line += ansi().fgBlue().a("  [" + source + "]").reset().toString();
                }

                System.out.println(line);
            }

            System.out.println();
            return 0;
        }
    }

    @Command(
        name = "list",
        description = "Listar claves de configuracion disponibles",
        mixinStandardHelpOptions = true
    )
    static class ListCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println(ansi().fgCyan().a("\nClaves de configuracion disponibles:\n").reset());

            System.out.println(ansi().fgYellow().a("API Keys (encriptadas):").reset());
            for (var entry : AVAILABLE_KEYS.entrySet()) {
                if (entry.getValue().secure) {
                    printKeyInfo(entry.getKey(), entry.getValue());
                }
            }

            System.out.println(ansi().fgYellow().a("\nConfiguracion general:").reset());
            for (var entry : AVAILABLE_KEYS.entrySet()) {
                if (!entry.getValue().secure) {
                    printKeyInfo(entry.getKey(), entry.getValue());
                }
            }

            System.out.println(ansi().fgBlue().a("""

                Prioridad de resolucion (Priority Cascade):
                  1. Argumentos CLI (--api-key, -k, etc.)
                  2. Variables de entorno (LLM_API_KEY, etc.)
                  3. Archivo de configuracion (~/.llm-fararoni/)
                """).reset());

            return 0;
        }

        private void printKeyInfo(String key, ConfigKeyInfo info) {
            System.out.println(String.format("  %-20s  %s", key, info.description));
            if (info.envVar != null) {
                System.out.println(ansi().fgBlue().a(
                    String.format("  %-20s  ENV: %s", "", info.envVar)
                ).reset());
            }
        }
    }

    @Command(
        name = "unset",
        description = "Eliminar una configuracion",
        mixinStandardHelpOptions = true
    )
    static class UnsetCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Nombre de la clave a eliminar")
        private String key;

        @Override
        public Integer call() {
            ConfigKeyInfo keyInfo = AVAILABLE_KEYS.get(key);

            if (keyInfo == null) {
                System.out.println(ansi().fgRed().a(
                    "Clave desconocida: " + key
                ).reset());
                return 1;
            }

            try {
                SecureConfigService config = SecureConfigService.getInstance();
                config.removeProperty(keyInfo.internalKey);

                System.out.println(ansi().fgGreen().a(
                    "Configuracion eliminada: " + key
                ).reset());

                if (keyInfo.envVar != null && System.getenv(keyInfo.envVar) != null) {
                    System.out.println(ansi().fgYellow().a(
                        "Nota: La variable de entorno " + keyInfo.envVar + " todavia esta definida"
                    ).reset());
                }

                return 0;
            } catch (Exception e) {
                System.out.println(ansi().fgRed().a(
                    "Error eliminando configuracion: " + e.getMessage()
                ).reset());
                return 1;
            }
        }
    }

    static String resolveValue(ConfigKeyInfo keyInfo) {
        if (keyInfo.envVar != null) {
            String envValue = System.getenv(keyInfo.envVar);
            if (envValue != null && !envValue.isEmpty()) {
                return envValue;
            }
        }

        SecureConfigService config = SecureConfigService.getInstance();
        if (keyInfo.secure) {
            return config.getSecureProperty(keyInfo.internalKey);
        } else {
            return config.getProperty(keyInfo.internalKey);
        }
    }

    static String getValueSource(ConfigKeyInfo keyInfo) {
        if (keyInfo.envVar != null) {
            String envValue = System.getenv(keyInfo.envVar);
            if (envValue != null && !envValue.isEmpty()) {
                return "ENV:" + keyInfo.envVar;
            }
        }

        return "FILE";
    }

    static String maskSecret(String value) {
        if (value == null || value.length() < 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    public record ConfigKeyInfo(
        String internalKey,
        String description,
        boolean secure,
        String envVar
    ) {}
}
