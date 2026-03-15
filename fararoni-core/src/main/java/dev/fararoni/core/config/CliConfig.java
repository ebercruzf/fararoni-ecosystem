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

import dev.fararoni.core.core.constants.AppDefaults;

import java.util.Map;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record CliConfig(
    String serverUrl,
    String apiKey,
    String modelName,

    TokenizerMode tokenizerMode,
    String tokenizerModel,

    boolean streaming,
    boolean showTokens,
    int maxTokens,
    double temperature,
    double topP,

    int contextWindow,
    ContextStrategy contextStrategy,
    int maxHistoryMessages,
    double contextCompressionRatio,
    boolean enableSmartTruncation,
    String continuationPrompt,

    String systemPrompt,
    Map<String, String> customCommands,

    boolean enableChainOfThought,
    boolean showThinkingProcess,
    String thinkingColor,

    int connectTimeoutMs,
    int readTimeoutMs,
    int maxRetries,
    boolean enableDebugMode

) {
    public CliConfig {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("serverUrl no puede estar vacío");
        }

        serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;

        if (maxTokens < 1 || maxTokens > 100000) {
            throw new IllegalArgumentException("maxTokens debe estar entre 1 y 100,000");
        }

        if (contextWindow < 512 || contextWindow > 200000) {
            throw new IllegalArgumentException("contextWindow debe estar entre 512 y 200,000");
        }

        if (maxTokens > contextWindow) {
            throw new IllegalArgumentException("maxTokens no puede ser mayor que contextWindow");
        }

        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature debe estar entre 0.0 y 2.0");
        }

        if (topP < 0.0 || topP > 1.0) {
            throw new IllegalArgumentException("topP debe estar entre 0.0 y 1.0");
        }

        if (maxHistoryMessages < 1 || maxHistoryMessages > 1000) {
            throw new IllegalArgumentException("maxHistoryMessages debe estar entre 1 y 1,000");
        }

        if (contextCompressionRatio < 0.1 || contextCompressionRatio > 1.0) {
            throw new IllegalArgumentException("contextCompressionRatio debe estar entre 0.1 y 1.0");
        }

        if (connectTimeoutMs < 1000 || connectTimeoutMs > 60000) {
            throw new IllegalArgumentException("connectTimeoutMs debe estar entre 1,000 y 60,000");
        }

        if (readTimeoutMs < 5000 || readTimeoutMs > 600000) {
            throw new IllegalArgumentException("readTimeoutMs debe estar entre 5,000 y 600,000");
        }

        if (maxRetries < 0 || maxRetries > 10) {
            throw new IllegalArgumentException("maxRetries debe estar entre 0 y 10");
        }

        customCommands = customCommands != null ? Map.copyOf(customCommands) : Map.of();

        tokenizerMode = tokenizerMode != null ? tokenizerMode : TokenizerMode.REMOTE;
        contextStrategy = contextStrategy != null ? contextStrategy : ContextStrategy.SLIDING_WINDOW;
        tokenizerModel = tokenizerModel != null ? tokenizerModel : "Qwen/Qwen2.5-Coder-32B-Instruct";
        thinkingColor = thinkingColor != null ? thinkingColor : "yellow";

        if (systemPrompt == null || systemPrompt.isBlank()) {
            if (enableChainOfThought) {
                systemPrompt = """
                    Eres un asistente experto en:
                    - Desarrollo Java y Spring Boot
                    - Contabilidad y fiscalidad mexicana
                    - Facturación electrónica CFDI 4.0
                    - Cálculos de IVA, ISR, IEPS

                    INSTRUCCIONES DE PENSAMIENTO:
                    Antes de responder, DEBES analizar la pregunta paso a paso dentro de etiquetas <thinking>.
                    Reflexiona sobre posibles errores, arquitectura y mejores prácticas.

                    Ejemplo:
                    <thinking>
                    El usuario pide un controlador.
                    1. Necesito validar inputs con @Valid
                    2. Usaré @RestController en lugar de @Controller
                    3. Debo recordar manejar excepciones
                    4. ResponseEntity para códigos HTTP apropiados
                    </thinking>

                    Aquí tienes el código...

                    Responde con precisión y exactitud técnica.
                    Si necesitas dividir una respuesta larga, indicaré [CONTINUACIÓN] para seguir.
                    """;
            } else {
                systemPrompt = """
                    Eres un asistente experto en:
                    - Desarrollo Java y Spring Boot
                    - Contabilidad y fiscalidad mexicana
                    - Facturación electrónica CFDI 4.0
                    - Cálculos de IVA, ISR, IEPS

                    Responde con precisión y exactitud técnica.
                    Si necesitas dividir una respuesta larga, indicaré [CONTINUACIÓN] para seguir.
                    """;
            }
        }

        if (continuationPrompt == null || continuationPrompt.isBlank()) {
            continuationPrompt = "[CONTINUACIÓN] Por favor continúa exactamente donde terminaste, sin repetir contenido previo.";
        }
    }

    public enum TokenizerMode {
        REMOTE, LOCAL
    }

    public enum ContextStrategy {
        SLIDING_WINDOW,
        COMPRESSION,
        CHUNKED_CONTINUATION,
        HYBRID
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serverUrl = AppDefaults.DEFAULT_SERVER_URL;
        private String apiKey;
        private String modelName = AppDefaults.DEFAULT_MODEL_NAME;
        private TokenizerMode tokenizerMode = TokenizerMode.REMOTE;
        private String tokenizerModel = "Qwen/Qwen2.5-Coder-32B-Instruct";
        private boolean streaming = true;
        private boolean showTokens = false;
        private int maxTokens = AppDefaults.DEFAULT_MAX_TOKENS;
        private double temperature = AppDefaults.DEFAULT_TEMPERATURE;
        private double topP = AppDefaults.DEFAULT_TOP_P;
        private int contextWindow = AppDefaults.DEFAULT_CONTEXT_WINDOW;
        private ContextStrategy contextStrategy = ContextStrategy.SLIDING_WINDOW;
        private int maxHistoryMessages = AppDefaults.DEFAULT_MAX_HISTORY;
        private double contextCompressionRatio = 0.7;
        private boolean enableSmartTruncation = true;
        private String continuationPrompt;
        private String systemPrompt;
        private Map<String, String> customCommands = Map.of();

        private boolean enableChainOfThought = false;
        private boolean showThinkingProcess = true;
        private String thinkingColor = "yellow";

        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 300000;
        private int maxRetries = 3;
        private boolean enableDebugMode = false;

        public Builder serverUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder tokenizerMode(TokenizerMode mode) {
            this.tokenizerMode = mode;
            return this;
        }

        public Builder tokenizerModel(String model) {
            this.tokenizerModel = model;
            return this;
        }

        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder showTokens(boolean showTokens) {
            this.showTokens = showTokens;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder contextWindow(int contextWindow) {
            this.contextWindow = contextWindow;
            return this;
        }

        public Builder contextStrategy(ContextStrategy strategy) {
            this.contextStrategy = strategy;
            return this;
        }

        public Builder maxHistoryMessages(int maxHistoryMessages) {
            this.maxHistoryMessages = maxHistoryMessages;
            return this;
        }

        public Builder contextCompressionRatio(double ratio) {
            this.contextCompressionRatio = ratio;
            return this;
        }

        public Builder enableSmartTruncation(boolean enable) {
            this.enableSmartTruncation = enable;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder customCommands(Map<String, String> commands) {
            this.customCommands = commands;
            return this;
        }

        public Builder connectTimeout(int timeoutMs) {
            this.connectTimeoutMs = timeoutMs;
            return this;
        }

        public Builder readTimeout(int timeoutMs) {
            this.readTimeoutMs = timeoutMs;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder enableDebugMode(boolean enable) {
            this.enableDebugMode = enable;
            return this;
        }

        public Builder enableChainOfThought(boolean enable) {
            this.enableChainOfThought = enable;
            return this;
        }

        public Builder showThinkingProcess(boolean show) {
            this.showThinkingProcess = show;
            return this;
        }

        public Builder thinkingColor(String color) {
            this.thinkingColor = color;
            return this;
        }

        public CliConfig build() {
            return new CliConfig(
                serverUrl, apiKey, modelName, tokenizerMode, tokenizerModel,
                streaming, showTokens, maxTokens, temperature, topP,
                contextWindow, contextStrategy, maxHistoryMessages,
                contextCompressionRatio, enableSmartTruncation, continuationPrompt,
                systemPrompt, customCommands,
                enableChainOfThought, showThinkingProcess, thinkingColor,
                connectTimeoutMs, readTimeoutMs, maxRetries, enableDebugMode
            );
        }
    }

    public boolean isRemoteTokenizer() {
        return tokenizerMode == TokenizerMode.REMOTE;
    }

    public boolean isLocalTokenizer() {
        return tokenizerMode == TokenizerMode.LOCAL;
    }

    public boolean shouldUseCompression() {
        return contextStrategy == ContextStrategy.COMPRESSION ||
               contextStrategy == ContextStrategy.HYBRID;
    }

    public boolean shouldUseChunking() {
        return contextStrategy == ContextStrategy.CHUNKED_CONTINUATION ||
               contextStrategy == ContextStrategy.HYBRID;
    }

    public int getEffectiveContextSize() {
        return Math.max(contextWindow - maxTokens - 100, 512);
    }

    public int getCompressionThreshold() {
        return (int) (getEffectiveContextSize() * contextCompressionRatio);
    }

    public CliConfig withMaxTokens(int newMaxTokens) {
        return new CliConfig(
            serverUrl, apiKey, modelName, tokenizerMode, tokenizerModel,
            streaming, showTokens, newMaxTokens, temperature, topP,
            contextWindow, contextStrategy, maxHistoryMessages,
            contextCompressionRatio, enableSmartTruncation, continuationPrompt,
            systemPrompt, customCommands,
            enableChainOfThought, showThinkingProcess, thinkingColor,
            connectTimeoutMs, readTimeoutMs, maxRetries, enableDebugMode
        );
    }

    public CliConfig withContextWindow(int newContextWindow) {
        return new CliConfig(
            serverUrl, apiKey, modelName, tokenizerMode, tokenizerModel,
            streaming, showTokens, maxTokens, temperature, topP,
            newContextWindow, contextStrategy, maxHistoryMessages,
            contextCompressionRatio, enableSmartTruncation, continuationPrompt,
            systemPrompt, customCommands,
            enableChainOfThought, showThinkingProcess, thinkingColor,
            connectTimeoutMs, readTimeoutMs, maxRetries, enableDebugMode
        );
    }

    public CliConfig withStreaming(boolean newStreaming) {
        return new CliConfig(
            serverUrl, apiKey, modelName, tokenizerMode, tokenizerModel,
            newStreaming, showTokens, maxTokens, temperature, topP,
            contextWindow, contextStrategy, maxHistoryMessages,
            contextCompressionRatio, enableSmartTruncation, continuationPrompt,
            systemPrompt, customCommands,
            enableChainOfThought, showThinkingProcess, thinkingColor,
            connectTimeoutMs, readTimeoutMs, maxRetries, enableDebugMode
        );
    }

    public CliConfig withServerUrl(String newServerUrl) {
        return new CliConfig(
            newServerUrl, apiKey, modelName, tokenizerMode, tokenizerModel,
            streaming, showTokens, maxTokens, temperature, topP,
            contextWindow, contextStrategy, maxHistoryMessages,
            contextCompressionRatio, enableSmartTruncation, continuationPrompt,
            systemPrompt, customCommands,
            enableChainOfThought, showThinkingProcess, thinkingColor,
            connectTimeoutMs, readTimeoutMs, maxRetries, enableDebugMode
        );
    }

    public CliConfig withModelName(String newModelName) {
        return new CliConfig(
            serverUrl, apiKey, newModelName, tokenizerMode, tokenizerModel,
            streaming, showTokens, maxTokens, temperature, topP,
            contextWindow, contextStrategy, maxHistoryMessages,
            contextCompressionRatio, enableSmartTruncation, continuationPrompt,
            systemPrompt, customCommands,
            enableChainOfThought, showThinkingProcess, thinkingColor,
            connectTimeoutMs, readTimeoutMs, maxRetries, enableDebugMode
        );
    }

    public String getConfigSummary() {
        return """
            Servidor: %s
            Modelo: %s
            Tokenizador: %s (%s)
            Ventana de contexto: %d tokens
            Máx. generación: %d tokens
            Estrategia: %s
            Streaming: %s
            """.formatted(
            serverUrl, modelName, tokenizerMode, tokenizerModel,
            contextWindow, maxTokens, contextStrategy,
            streaming ? "SÍ" : "NO"
        );
    }
}
