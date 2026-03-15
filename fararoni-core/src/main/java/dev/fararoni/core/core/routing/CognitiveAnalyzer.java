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
package dev.fararoni.core.core.routing;

import dev.fararoni.core.config.ServiceRegistry;
import dev.fararoni.core.core.llm.LocalLlmService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CognitiveAnalyzer {
    private static final Logger LOG = Logger.getLogger(CognitiveAnalyzer.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final double COMPLEXITY_THRESHOLD = 0.6;

    private static final int MAX_TOKENS = 50;

    private final LocalLlmService localLlm;

    public CognitiveAnalyzer(LocalLlmService localLlm) {
        if (localLlm == null) {
            throw new IllegalArgumentException("LocalLlmService cannot be null");
        }
        this.localLlm = localLlm;
    }

    public RoutingPlan analyze(String query) {
        if (query == null || query.isBlank()) {
            return buildDefaultPlan(0, "Empty query");
        }

        long startTime = System.nanoTime();

        try {
            if (!localLlm.isGrammarGenerationAvailable()) {
                LOG.warning("[CognitiveAnalyzer] Grammar generation not available, using default");
                return buildDefaultPlan(startTime, "LLM not available");
            }

            String prompt = buildAnalysisPrompt(query);

            String jsonResponse = localLlm.generateWithGrammar(
                prompt,
                RouterGrammars.ROUTING_DECISION_GRAMMAR,
                MAX_TOKENS
            );

            LOG.fine("[CognitiveAnalyzer] Raw response: " + jsonResponse);

            return parseResponse(jsonResponse, startTime);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CognitiveAnalyzer] Analysis failed: " + e.getMessage(), e);
            return buildDefaultPlan(startTime, "Analysis failed: " + e.getMessage());
        }
    }

    public boolean isReady() {
        return localLlm != null && localLlm.isGrammarGenerationAvailable();
    }

    public String getStats() {
        return String.format("CognitiveAnalyzer [ready=%s, threshold=%.2f]",
            isReady(), COMPLEXITY_THRESHOLD);
    }

    private String buildAnalysisPrompt(String query) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Eres el Gatekeeper del sistema Fararoni. ");
        prompt.append("Analiza el costo computacional de esta tarea.\n\n");

        String sessionContext = getSessionContext();
        if (sessionContext != null && !sessionContext.isBlank()) {
            prompt.append("=== ESTADO DEL SISTEMA ===\n");
            prompt.append(sessionContext);
            prompt.append("\n\n");
        }

        prompt.append("=== QUERY DEL USUARIO ===\n");
        prompt.append("\"").append(query).append("\"\n\n");

        prompt.append("=== REGLAS DE CLASIFICACION ===\n");
        prompt.append("- complexity < 0.3: Saludos, Git basico, Configs simples\n");
        prompt.append("- complexity 0.3 - 0.5: Crear clases simples, leer archivos\n");
        prompt.append("- complexity 0.5 - 0.7: Debugging, funciones complejas\n");
        prompt.append("- complexity 0.7 - 0.9: Refactoring, analisis de modulos\n");
        prompt.append("- complexity > 0.9: Seguridad, arquitectura completa\n\n");

        prompt.append("IMPORTANTE:\n");
        prompt.append("- Si hay un error critico en el contexto, AUMENTA la complejidad.\n");
        prompt.append("- Si el usuario menciona seguridad/passwords, complejidad >= 0.9\n");
        prompt.append("- Si pide analisis de arquitectura, complejidad >= 0.8\n\n");

        prompt.append("Responde SOLO con JSON:\n");

        return prompt.toString();
    }

    private String getSessionContext() {
        try {
            return ServiceRegistry.getSessionContextForPrompt();
        } catch (Exception e) {
            LOG.fine("[CognitiveAnalyzer] Could not get session context: " + e.getMessage());
            return null;
        }
    }

    private RoutingPlan parseResponse(String json, long startNano) {
        try {
            JsonNode node = MAPPER.readTree(json);

            double complexity = 0.5;
            if (node.has("complexity")) {
                complexity = node.get("complexity").asDouble(0.5);
                complexity = Math.max(0.0, Math.min(1.0, complexity));
            }

            RoutingPlan.DetectedIntent intent = RoutingPlan.DetectedIntent.UNKNOWN;
            if (node.has("intent")) {
                String intentStr = node.get("intent").asText("UNKNOWN");
                try {
                    intent = RoutingPlan.DetectedIntent.valueOf(intentStr);
                } catch (IllegalArgumentException e) {
                    LOG.fine("[CognitiveAnalyzer] Unknown intent: " + intentStr);
                }
            }

            RoutingPlan.TargetModel target;
            String reasoning;

            if (complexity < COMPLEXITY_THRESHOLD) {
                target = RoutingPlan.TargetModel.LOCAL;
                reasoning = String.format("LLM analysis: complexity=%.2f < %.2f (LOCAL)",
                    complexity, COMPLEXITY_THRESHOLD);
            } else {
                target = RoutingPlan.TargetModel.EXPERT;
                reasoning = String.format("LLM analysis: complexity=%.2f >= %.2f (EXPERT)",
                    complexity, COMPLEXITY_THRESHOLD);
            }

            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;

            return RoutingPlan.builder()
                .target(target)
                .intent(intent)
                .complexity(complexity)
                .requiresInternet(target.mayRequireCloud())
                .reasoning(reasoning)
                .source(RoutingPlan.DecisionSource.LAYER_2_COGNITIVE)
                .timeMs(elapsedMs)
                .build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CognitiveAnalyzer] Failed to parse JSON: " + json, e);
            return buildDefaultPlan(startNano, "Parse failed: " + e.getMessage());
        }
    }

    private RoutingPlan buildDefaultPlan(long startNano, String reason) {
        long elapsedMs = startNano > 0 ? (System.nanoTime() - startNano) / 1_000_000 : 0;

        return RoutingPlan.builder()
            .target(RoutingPlan.TargetModel.LOCAL)
            .intent(RoutingPlan.DetectedIntent.UNKNOWN)
            .complexity(0.5)
            .requiresInternet(false)
            .reasoning("Default fallback - " + reason)
            .source(RoutingPlan.DecisionSource.DEFAULT_FALLBACK)
            .timeMs(elapsedMs)
            .build();
    }
}
