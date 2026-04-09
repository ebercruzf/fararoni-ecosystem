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
package dev.fararoni.core.core.constants;

import dev.fararoni.core.cli.FararoniCli;
import dev.fararoni.core.core.agents.RabbitAgent;
import dev.fararoni.core.core.agents.TurtleAgent;
import dev.fararoni.core.core.clients.OpenAICompatibleClient;
import dev.fararoni.core.core.config.AgentConfig;
import dev.fararoni.core.core.hybrid.HybridBrainService;
import dev.fararoni.core.core.llm.LLMProviderFactory;
import dev.fararoni.core.core.llm.providers.OpenAIProvider;
import dev.fararoni.core.core.mission.persistence.JdbcMissionStateRepository;
import dev.fararoni.core.core.reflexion.TestCorrectionService;
import dev.fararoni.core.core.routing.EnterpriseRouter;
import dev.fararoni.core.core.session.RequestOrigin;
import dev.fararoni.core.core.session.SessionContext;
import dev.fararoni.core.server.FararoniServer;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class AppDefaults {
    private AppDefaults() {
    }

    public static final String DEFAULT_SERVER_URL = "http://localhost:8000";

    public static final int DEFAULT_SERVER_PORT = resolveInt("FARARONI_SERVER_PORT", 7070);

    public static final String ENV_SERVER_PORT = "FARARONI_SERVER_PORT";

    public static final String DEFAULT_SERVER_HOST = resolveString("FARARONI_SERVER_HOST", "localhost");

    public static final String ENV_SERVER_HOST = "FARARONI_SERVER_HOST";

    public static final String DEFAULT_SERVER_PROTOCOL = resolveString("FARARONI_SERVER_PROTOCOL", "http");

    public static final String ENV_SERVER_PROTOCOL = "FARARONI_SERVER_PROTOCOL";

    public static final int DEFAULT_GATEWAY_PORT = resolveInt("FARARONI_GATEWAY_PORT", 7071);

    public static final String ENV_GATEWAY_PORT = "FARARONI_GATEWAY_PORT";

    public static final boolean GATEWAY_ENABLED = resolveBoolean("FARARONI_GATEWAY_ENABLED", true);

    public static final String ENV_GATEWAY_ENABLED = "FARARONI_GATEWAY_ENABLED";

    public static String buildServerUrl(int port) {
        return DEFAULT_SERVER_PROTOCOL + "://" + DEFAULT_SERVER_HOST + ":" + port;
    }

    public static final boolean REMOTE_DISABLED = resolveBoolean("FARARONI_REMOTE_DISABLED", false);

    public static final String ENV_REMOTE_DISABLED = "FARARONI_REMOTE_DISABLED";

    public static boolean isRemoteDisabled() {
        return REMOTE_DISABLED;
    }

    public static final String DEFAULT_MODEL_NAME = "qwen-coder";

    public static final String ENV_SERVER_URL = "LLM_SERVER_URL";

    public static final String ENV_MODEL_NAME = "LLM_MODEL_NAME";

    public static final String ENV_API_KEY = "LLM_API_KEY";

    public static final String ENV_MAX_TOKENS = "LLM_MAX_TOKENS";

    public static final String ENV_TEMPERATURE = "LLM_TEMPERATURE";

    public static final String ENV_CONTEXT_WINDOW = "LLM_CONTEXT_WINDOW";

    public static final String ENV_STREAMING = "LLM_STREAMING";

    public static final int DEFAULT_MAX_TOKENS = 4096;

    public static final double DEFAULT_TEMPERATURE = 0.7;

    public static final double DEFAULT_TOP_P = 0.95;

    public static final int DEFAULT_CONTEXT_WINDOW = 8192;

    public static final int RABBIT_CONTEXT_WINDOW = resolveInt("LLM_RABBIT_CONTEXT_WINDOW", 8192);

    public static final int TURTLE_CONTEXT_WINDOW = resolveInt("LLM_TURTLE_CONTEXT_WINDOW", 32768);

    public enum RabbitPower {
        WEAK_1B(32 * 1024, "1.5B (Estudiante)", "Solo saludos y comandos simples"),

        BALANCED_7B(16 * 1024, "7B (Pro)", "Código simple y refactoring básico"),

        TITAN_30B(8 * 1024, "30B (Titan)", "Lógica compleja, pero análisis grandes van a Tortuga");

        public final int maxSafeContextTokens;

        public final String displayName;

        public final String description;

        RabbitPower(int tokens, String displayName, String description) {
            this.maxSafeContextTokens = tokens;
            this.displayName = displayName;
            this.description = description;
        }

        public static RabbitPower fromModelName(String modelName) {
            if (modelName == null) return WEAK_1B;
            String lower = modelName.toLowerCase();
            if (lower.contains("480b") || lower.contains("35b") || lower.contains("32b") || lower.contains("30b") || lower.contains("70b")) {
                return TITAN_30B;
            }
            if (lower.contains("7b") || lower.contains("8b") || lower.contains("14b")) {
                return BALANCED_7B;
            }
            return WEAK_1B;
        }
    }

    public static final int TOKENS_SKELETAL = 500;

    public static final int TOKENS_TACTICAL = 4000;

    public static final int TOKENS_STRATEGIC = 12000;

    private static int resolveInt(String envKey, int defaultValue) {
        try {
            String sysPropKey = envKeyToSysProp(envKey);
            String value = System.getProperty(sysPropKey);

            if (value == null || value.isBlank()) {
                value = System.getenv(envKey);
            }

            if (value != null && !value.isBlank()) {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (NumberFormatException e) {
        }
        return defaultValue;
    }

    private static String envKeyToSysProp(String envKey) {
        return envKey.toLowerCase().replace("_", ".");
    }

    private static String resolveString(String envKey, String defaultValue) {
        String sysPropKey = envKeyToSysProp(envKey);
        String value = System.getProperty(sysPropKey);

        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }

        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return defaultValue;
    }

    private static boolean resolveBoolean(String envKey, boolean defaultValue) {
        String sysPropKey = envKeyToSysProp(envKey);
        String value = System.getProperty(sysPropKey);

        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }

        if (value != null && !value.isBlank()) {
            String trimmed = value.trim().toLowerCase();
            return "true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed);
        }
        return defaultValue;
    }

    private static long resolveLong(String envKey, long defaultValue) {
        try {
            String value = System.getenv(envKey);
            if (value != null && !value.isBlank()) {
                long parsed = Long.parseLong(value.trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (NumberFormatException e) {
        }
        return defaultValue;
    }

    public static final int DEFAULT_MAX_HISTORY = 50;

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30000;

    public static final int DEFAULT_READ_TIMEOUT_MS = 120000;

    public static final int DEFAULT_MAX_RETRIES = 3;

    public static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";

    public static final String DEFAULT_TURTLE_MODEL = "qwen2.5-coder:32b";

    public static final String DEFAULT_RABBIT_MODEL = "qwen2.5-coder:1.5b";

    public static final int DEFAULT_TURTLE_TIMEOUT_SECONDS = 180;

    public static final String DEFAULT_PROVIDER_TYPE = "ollama";

    public static final String ENV_OLLAMA_URL = "FARARONI_OLLAMA_URL";

    public static final String ENV_TURTLE_MODEL = "FARARONI_TURTLE_MODEL";

    public static final String ENV_RABBIT_MODEL = "FARARONI_RABBIT_MODEL";

    public static final String ENV_TURTLE_TIMEOUT = "FARARONI_TURTLE_TIMEOUT";

    public static final String ENV_PROVIDER_TYPE = "FARARONI_PROVIDER_TYPE";

    public static final String PREFIX_THOUGHT = "[THOUGHT]";

    public static final String ENV_SHOW_REASONING = "FARARONI_SHOW_REASONING";

    public static final String OLLAMA_API_CHAT = "/api/chat";

    public static final String OLLAMA_API_GENERATE = "/api/generate";

    public static final String DEFAULT_CLOUD_MODEL = "gpt-4o";

    public static final String DEFAULT_OPENAI_URL = "https://api.openai.com/v1";

    public static final String DEFAULT_GROQ_URL = "https://api.groq.com/openai/v1";

    public static final int DEFAULT_CLOUD_TIMEOUT_SECONDS = 120;

    public static final String ENV_CLOUD_MODEL = "FARARONI_CLOUD_MODEL";

    public static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";

    public static final String ENV_GROQ_API_KEY = "GROQ_API_KEY";

    public static final String ENV_CLOUD_BASE_URL = "FARARONI_CLOUD_BASE_URL";

    public static final String ENV_CLOUD_TIMEOUT = "FARARONI_CLOUD_TIMEOUT";

    public static final String ENV_CLAUDE_API_KEY = "FARARONI_CLAUDE_API_KEY";
    public static final String ENV_CLAUDE_MODEL = "FARARONI_CLAUDE_MODEL";
    public static final String ENV_CLAUDE_PREFERRED = "FARARONI_CLAUDE_PREFERRED";
    public static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-6";
    public static final String DEFAULT_CLAUDE_BASE_URL = "https://api.anthropic.com";
    public static final String ANTHROPIC_API_VERSION = "2023-06-01";

    public static final String ENV_DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY";
    public static final String ENV_DEEPSEEK_MODEL = "FARARONI_DEEPSEEK_MODEL";
    public static final String ENV_DEEPSEEK_PREFERRED = "FARARONI_DEEPSEEK_PREFERRED";
    public static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
    public static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    public static final boolean DEFAULT_AGENTIC_ENABLED = true;

    public static final String DEFAULT_AGENTIC_URL = "http://localhost:11434";

    public static final String DEFAULT_AGENTIC_MODEL = "qwen2.5-coder:32b";

    public static final String DEFAULT_TOOL_CHOICE = "auto";

    public static final String DEFAULT_AGENTIC_API_KEY = "ollama";

    public static final String ENV_AGENTIC_ENABLED = "FARARONI_AGENTIC_ENABLED";

    public static final String ENV_AGENTIC_URL = "FARARONI_AGENTIC_URL";

    public static final String ENV_AGENTIC_MODEL = "FARARONI_AGENTIC_MODEL";

    public static final String ENV_TOOL_CHOICE = "FARARONI_TOOL_CHOICE";

    public static final String ENV_AGENTIC_API_KEY = "FARARONI_AGENTIC_API_KEY";

    public static final String APP_VERSION = "1.0.0";

    public static final String APP_NAME = "FARARONI";

    public static final String DEFAULT_DATA_DIR = ".llm-fararoni";

    private static final String BOLD = "\u001B[1m";
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";

    public static final String CREATOR_BIO = """
        Eber Cruz Fararoni es Ingeniero de Software y Arquitecto de Sistemas, especializado en
        Inteligencia Artificial y Desarrollo Backend Java. Es el creador y arquitecto principal
        del ecosistema Fararoni. Su enfoque tecnico se centra en la Soberania de Datos,
        Arquitecturas de Alto Rendimiento y Soluciones Enterprise.
        """;

    public static final String IDENTITY_TRIGGER_REGEX =
        "(?i).*(quien eres|quién eres|que eres|qué eres|presentate|preséntate|identificate|identifícate|quien te creo|quién te creó|quien es tu autor|quién es tu autor).*";

    public static final String IDENTITY_RESPONSE =
        CYAN + BOLD + "IDENTIDAD DEL SISTEMA:" + RESET + "\n" +
        "Soy " + BOLD + "Fararoni" + RESET + ", un Orquestador de IA Soberano (v1.0).\n\n" +
        BOLD + "Arquitecto:" + RESET + " Ing. Eber Cruz Fararoni\n" +
        BOLD + "Arquitectura:" + RESET + " Dual-Core Hibrida (Rabbit/Turtle)\n" +
        BOLD + "Mision:" + RESET + " Asistirte como Oficial Tecnico en desarrollo y seguridad.\n\n" +
        "Para informacion sobre mi creador, puedes preguntar: \"Quien es Eber Cruz?\"";

    public static final String CREATOR_TRIGGER_REGEX =
        "(?i).*(quien es eber cruz|quién es eber cruz|sobre eber cruz|eber cruz fararoni|quien te creo|quién te creó|quien es el autor|quién es el autor).*";

    public static final String CREATOR_RESPONSE_HEADER = CYAN + BOLD + "PERFIL DEL ARQUITECTO:" + RESET + "\n\n";

    public static final String CHANNELS_DB_TYPE = resolveString("FARARONI_CHANNELS_DB_TYPE", "sqlite");

    public static final String CHANNELS_DB_PATH = resolveString(
        "FARARONI_CHANNELS_DB_PATH",
        System.getProperty("user.home") + "/.fararoni/data/channels.db"
    );

    public static final String CHANNELS_DB_URL = resolveString("FARARONI_CHANNELS_DB_URL", "");

    public static final String CHANNELS_DB_USER = resolveString("FARARONI_CHANNELS_DB_USER", "");

    public static final String CHANNELS_DB_PASSWORD = resolveString("FARARONI_CHANNELS_DB_PASSWORD", "");

    public static final boolean DEV_MODE = "true".equalsIgnoreCase(
        resolveString("FARARONI_DEV_MODE", "false")
    );

    public static final String CHANNELS_ENCRYPTION_KEY = resolveEncryptionKey();

    public static final String CHANNELS_KEY_ID = resolveString("FARARONI_CHANNELS_KEY_ID", "default");

    private static String resolveEncryptionKey() {
        String key = resolveString("FARARONI_CHANNELS_ENCRYPTION_KEY", "");
        if (!key.isBlank()) {
            return key;
        }
        if (DEV_MODE) {
            return resolveOrCreateDevKey();
        }
        return "";
    }

    private static String resolveOrCreateDevKey() {
        java.nio.file.Path keyFile = java.nio.file.Path.of(
            System.getProperty("user.home"), ".fararoni", "config", ".dev-encryption-key");
        try {
            if (java.nio.file.Files.exists(keyFile)) {
                String saved = java.nio.file.Files.readString(keyFile).strip();
                if (!saved.isBlank()) {
                    return saved;
                }
            }
            byte[] randomBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(randomBytes);
            String generated = java.util.Base64.getEncoder().encodeToString(randomBytes);
            java.nio.file.Files.createDirectories(keyFile.getParent());
            java.nio.file.Files.writeString(keyFile, generated);
            return generated;
        } catch (java.io.IOException e) {
            byte[] randomBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(randomBytes);
            return java.util.Base64.getEncoder().encodeToString(randomBytes);
        }
    }

    public static final String ENV_CHANNELS_DB_TYPE = "FARARONI_CHANNELS_DB_TYPE";

    public static final String ENV_CHANNELS_DB_PATH = "FARARONI_CHANNELS_DB_PATH";

    public static final String ENV_CHANNELS_DB_URL = "FARARONI_CHANNELS_DB_URL";

    public static final String ENV_CHANNELS_DB_USER = "FARARONI_CHANNELS_DB_USER";

    public static final String ENV_CHANNELS_DB_PASSWORD = "FARARONI_CHANNELS_DB_PASSWORD";

    public static final String ENV_CHANNELS_ENCRYPTION_KEY = "FARARONI_CHANNELS_ENCRYPTION_KEY";

    public static final String ENV_CHANNELS_KEY_ID = "FARARONI_CHANNELS_KEY_ID";

    public static final String ENV_DEV_MODE = "FARARONI_DEV_MODE";

    public static final String MISSION_DB_URL = resolveString("FARARONI_MISSION_DB_URL",
        System.getProperty("user.home") + "/.fararoni/data/missions.db");

    public static final int DB_MAX_POOL_SIZE = resolveInt("FARARONI_DB_MAX_POOL", 10);

    public static final int DB_MIN_IDLE = resolveInt("FARARONI_DB_MIN_IDLE", 2);

    public static final long DB_CONN_TIMEOUT = resolveLong("FARARONI_DB_CONN_TIMEOUT", 5000L);

    public static final long DB_IDLE_TIMEOUT = resolveLong("FARARONI_DB_IDLE_TIMEOUT", 300000L);

    public static final long DB_MAX_LIFETIME = resolveLong("FARARONI_DB_MAX_LIFETIME", 600000L);

    public static final String ENV_MISSION_DB_URL = "FARARONI_MISSION_DB_URL";

    public static final String ENV_DB_MAX_POOL = "FARARONI_DB_MAX_POOL";

    public static final String ENV_DB_MIN_IDLE = "FARARONI_DB_MIN_IDLE";

    public static final String ENV_DB_CONN_TIMEOUT = "FARARONI_DB_CONN_TIMEOUT";

    public static final String ENV_DB_IDLE_TIMEOUT = "FARARONI_DB_IDLE_TIMEOUT";

    public static final String ENV_DB_MAX_LIFETIME = "FARARONI_DB_MAX_LIFETIME";

    public static final String AMBIGUITY_HEADER = "[!] Ambiguedad detectada";

    public static final String AMBIGUITY_INSTRUCTION = "rutas exactas:";

    public static final String AMBIGUITY_PATH_REGEX = "^\\s*(\\d+)\\.\\s+(\\S+)\\s*$";
}
