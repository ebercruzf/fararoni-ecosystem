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
package dev.fararoni.core.core.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.core.core.constants.AppDefaults;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LlmProviderDiscovery {
    private static final Logger LOG = Logger.getLogger(LlmProviderDiscovery.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int OLLAMA_PORT = 11434;

    private static final int VLLM_PORT = 8000;

    private static final int LM_STUDIO_PORT = 1234;

    private static final int SCAN_TIMEOUT_SECONDS = 2;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(SCAN_TIMEOUT_SECONDS))
        .build();

    public record DiscoveryResult(
        boolean success,
        String serverUrl,
        String modelName,
        String message,
        List<String> availableModels
    ) {
        public static DiscoveryResult success(String url, String model, String message, List<String> models) {
            return new DiscoveryResult(true, url, model, message, models != null ? models : List.of());
        }

        public static DiscoveryResult success(String url, String model, String message) {
            return new DiscoveryResult(true, url, model, message, List.of());
        }

        public static DiscoveryResult failure(String message) {
            return new DiscoveryResult(false, null, null, message, List.of());
        }
    }

    public DiscoveryResult discoverBestProvider(String configUrl, String configModel, String apiKey) {
        LOG.info("[DISCOVERY] Iniciando protocolo de autodescubrimiento...");

        String cleanConfigModel = sanitizeModelName(configModel);

        if (configUrl != null && !configUrl.isBlank() && checkConnection(configUrl)) {
            LOG.info("[DISCOVERY] Enlace primario estable: " + configUrl);
            return DiscoveryResult.success(configUrl, cleanConfigModel, "Enlace primario estable.");
        }

        if (configUrl != null && !configUrl.isBlank()) {
            LOG.warning("[DISCOVERY] Enlace primario (" + configUrl + ") caido. Iniciando escaneo de frecuencias...");
            System.out.println("[AVISO] Enlace primario caido (" + configUrl + "). Iniciando escaneo de frecuencias...");
        }

        System.out.println("[SCAN] Escaneando proveedores LLM locales...");

        String ollamaUrl = "http://localhost:" + OLLAMA_PORT;
        if (checkConnection(ollamaUrl)) {
            LOG.info("[DISCOVERY] Senal de Ollama detectada. Interrogando modelos...");
            System.out.println("[OK] Senal de Ollama detectada en puerto " + OLLAMA_PORT);

            OllamaModelsResult ollamaModels = findBestOllamaModel(ollamaUrl);
            if (ollamaModels != null && ollamaModels.bestModel() != null) {
                System.out.println("[SEL] Modelo seleccionado: " + ollamaModels.bestModel());
                return DiscoveryResult.success(ollamaUrl, ollamaModels.bestModel(),
                    "Re-enrutado a Ollama (Modelo: " + ollamaModels.bestModel() + ")",
                    ollamaModels.allModels());
            }
        }

        if (configUrl == null || !configUrl.contains(":" + VLLM_PORT)) {
            String vllmUrl = "http://localhost:" + VLLM_PORT;
            if (checkConnection(vllmUrl)) {
                LOG.info("[DISCOVERY] Senal de vLLM detectada.");
                System.out.println("[OK] Senal de vLLM detectada en puerto " + VLLM_PORT);

                List<String> models = listOpenAIModels(vllmUrl);
                if (!models.isEmpty()) {
                    String bestModel = selectBestModel(models);
                    System.out.println("[SEL] Modelo seleccionado: " + bestModel);
                    return DiscoveryResult.success(vllmUrl, bestModel,
                        "Re-enrutado a vLLM (Modelo: " + bestModel + ")", models);
                }
            }
        }

        String lmStudioUrl = "http://localhost:" + LM_STUDIO_PORT + "/v1";
        if (checkConnection(lmStudioUrl.replace("/v1", ""))) {
            LOG.info("[DISCOVERY] Senal de LM Studio detectada.");
            System.out.println("[OK] Senal de LM Studio detectada en puerto " + LM_STUDIO_PORT);
            return DiscoveryResult.success(lmStudioUrl, "local-model",
                "Re-enrutado a LM Studio/LocalAI");
        }

        LOG.severe("[DISCOVERY] No se detectaron proveedores LLM activos.");
        System.out.println("[ERROR] No se detectaron proveedores LLM activos.");
        System.out.println("\n[TIP] Sugerencias:");
        System.out.println("   - Instala Ollama: brew install ollama && ollama pull qwen2.5-coder:7b");
        System.out.println("   - O configura manualmente: fararoni config set server-url <URL>");

        return DiscoveryResult.failure("No se detectaron proveedores activos.");
    }

    private boolean checkConnection(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return false;

        try {
            String target;
            if (baseUrl.contains(":" + OLLAMA_PORT)) {
                target = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "") + "/api/tags";
            } else if (baseUrl.contains(":" + VLLM_PORT)) {
                target = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "") + "/health";
            } else {
                target = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "") + "/v1/models";
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(target))
                .timeout(Duration.ofSeconds(SCAN_TIMEOUT_SECONDS))
                .GET()
                .build();

            HttpResponse<Void> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (Exception e) {
            LOG.fine("[DISCOVERY] Conexión fallida a " + baseUrl + ": " + e.getMessage());
            return false;
        }
    }

    private record OllamaModelsResult(String bestModel, List<String> allModels) {}

    private OllamaModelsResult findBestOllamaModel(String baseUrl) {
        try {
            String apiUrl = baseUrl.replaceAll("/$", "") + "/api/tags";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(SCAN_TIMEOUT_SECONDS))
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode modelsNode = root.get("models");
            if (modelsNode == null || !modelsNode.isArray()) return null;

            List<String> models = new ArrayList<>();
            for (JsonNode model : modelsNode) {
                JsonNode nameNode = model.get("name");
                if (nameNode != null) {
                    String cleanName = sanitizeModelName(nameNode.asText());
                    if (cleanName != null) {
                        models.add(cleanName);
                    }
                }
            }

            if (models.isEmpty()) return null;

            System.out.println("[SCAN] Modelos encontrados en Ollama: " + models);
            return new OllamaModelsResult(selectBestModel(models), models);
        } catch (Exception e) {
            LOG.warning("[DISCOVERY] Error listando modelos Ollama: " + e.getMessage());
            return null;
        }
    }

    private List<String> listOpenAIModels(String baseUrl) {
        List<String> models = new ArrayList<>();
        try {
            String apiUrl = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "") + "/v1/models";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(SCAN_TIMEOUT_SECONDS))
                .GET()
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return models;

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) return models;

            for (JsonNode model : dataNode) {
                JsonNode idNode = model.get("id");
                if (idNode != null) {
                    models.add(idNode.asText());
                }
            }
        } catch (Exception e) {
            LOG.fine("[DISCOVERY] Error listando modelos OpenAI: " + e.getMessage());
        }
        return models;
    }

    private String sanitizeModelName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            rawName = System.getenv(AppDefaults.ENV_MODEL_NAME);

            if (rawName == null || rawName.isBlank()) {
                rawName = AppDefaults.DEFAULT_MODEL_NAME;
            }
        }

        String clean = rawName.trim()
                             .replaceAll("~$", "")
                             .replaceAll("\\s+", "-")
                             .replaceAll("[^a-zA-Z0-9._:-]", "");

        if (!clean.equals(rawName)) {
            LOG.warning("[DISCOVERY] Nombre de modelo sanitizado: '" + rawName + "' -> '" + clean + "'");
        }

        return clean.isEmpty() ? AppDefaults.DEFAULT_MODEL_NAME : clean;
    }

    private String selectBestModel(List<String> models) {
        if (models.isEmpty()) {
            return AppDefaults.DEFAULT_MODEL_NAME;
        }

        String[] priorities = {
            "qwen2.5-coder",
            "qwen-coder",
            "qwen2.5",
            "qwen",
            "codellama",
            "deepseek-coder",
            "llama3",
            "llama2",
            "mistral",
            "mixtral"
        };

        for (String priority : priorities) {
            for (String model : models) {
                if (model.toLowerCase().contains(priority)) {
                    return model;
                }
            }
        }

        return models.get(0);
    }
}
