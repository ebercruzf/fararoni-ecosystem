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
package dev.fararoni.core.core.clients;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DeepSeekClient extends OpenAICompatibleClient {

    private static final Logger logger = Logger.getLogger(DeepSeekClient.class.getName());

    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    public static final String ENV_DEEPSEEK_API_KEY = "DEEPSEEK_API_KEY";

    public static final String MODEL_CHAT = "deepseek-chat";

    public static final String MODEL_REASONER = "deepseek-reasoner";

    public DeepSeekClient(String apiKey) {
        this(apiKey, MODEL_CHAT);
    }

    public DeepSeekClient(String apiKey, String modelName) {
        this(apiKey, modelName, 180);
    }

    public DeepSeekClient(String apiKey, String modelName, int timeoutSeconds) {
        super(DEEPSEEK_BASE_URL, apiKey, modelName, timeoutSeconds);
        logger.info(() -> String.format("[DeepSeek] Client initialized (model=%s, url=%s)", modelName, DEEPSEEK_BASE_URL));
    }

    public static DeepSeekClient fromEnvironment() {
        return fromEnvironment(MODEL_CHAT);
    }

    public static DeepSeekClient fromEnvironment(String modelName) {
        String apiKey = System.getenv(ENV_DEEPSEEK_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            logger.warning("[DeepSeek] API key not found. Set " + ENV_DEEPSEEK_API_KEY + " environment variable.");
            return null;
        }
        return new DeepSeekClient(apiKey, modelName);
    }
}
