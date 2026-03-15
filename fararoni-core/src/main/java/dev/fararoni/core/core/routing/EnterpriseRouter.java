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

import dev.fararoni.core.core.constants.AppDefaults;
import dev.fararoni.core.core.constants.AppDefaults.RabbitPower;
import dev.fararoni.core.core.gateway.CircuitBreaker;
import dev.fararoni.core.core.index.ProjectKnowledgeBase.ContextProfile;
import dev.fararoni.core.core.llm.LocalLlmService;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class EnterpriseRouter implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(EnterpriseRouter.class.getName());

    public static final int ROUTER_BYPASS_THRESHOLD = 800;

    private final RegexRegistry regexRegistry;
    private final SemanticCache semanticCache;
    private final CognitiveAnalyzer cognitiveAnalyzer;

    private final CircuitBreaker cognitiveCB = CircuitBreaker.Factory.standard();

    private RabbitPower currentRabbitPower = RabbitPower.WEAK_1B;

    private long totalRoutes = 0;
    private long bypassHits = 0;
    private long layer0Hits = 0;
    private long layer1Hits = 0;
    private long layer2Hits = 0;

    public EnterpriseRouter(LocalLlmService localLlm) {
        this.regexRegistry = new RegexRegistry();
        this.semanticCache = new SemanticCache();

        if (localLlm != null && localLlm.isGrammarGenerationAvailable()) {
            this.cognitiveAnalyzer = new CognitiveAnalyzer(localLlm);
            LOG.info("[EnterpriseRouter] Initialized with all 3 layers (LLM available)");
        } else {
            this.cognitiveAnalyzer = null;
            LOG.info("[EnterpriseRouter] Initialized with 2 layers (LLM not available)");
        }
    }

    EnterpriseRouter(RegexRegistry regex, SemanticCache cache, CognitiveAnalyzer cognitive) {
        this.regexRegistry = regex;
        this.semanticCache = cache;
        this.cognitiveAnalyzer = cognitive;
    }

    public RoutingPlan route(String query) {
        if (query == null || query.isBlank()) {
            return buildDefaultPlan("Empty query");
        }

        totalRoutes++;
        long startTime = System.nanoTime();

        if (query.length() > ROUTER_BYPASS_THRESHOLD) {
            if (isHighEntropy(query)) {
                bypassHits++;
                RoutingPlan plan = RoutingPlan.builder()
                    .target(RoutingPlan.TargetModel.EXPERT)
                    .intent(RoutingPlan.DetectedIntent.HEAVY_PAYLOAD)
                    .complexity(1.0)
                    .requiresInternet(false)
                    .reasoning("Heavy payload bypass (" + query.length() + " chars > " +
                               ROUTER_BYPASS_THRESHOLD + " threshold) - Entropía verificada")
                    .source(RoutingPlan.DecisionSource.TRAFFIC_BYPASS)
                    .timeMs(0)
                    .build();
                LOG.fine("[EnterpriseRouter] Bypass hit (heavy payload): " + query.length() + " chars");
                return plan;
            } else {
                LOG.warning("[MILITARY-GRADE] Payload grande rechazado por baja entropía: " +
                           query.length() + " chars");
                return buildDefaultPlan("Heavy payload rejected - low entropy (possible DoS)");
            }
        }

        Optional<RoutingPlan> layer0Result = regexRegistry.classify(query);
        if (layer0Result.isPresent()) {
            layer0Hits++;
            RoutingPlan plan = layer0Result.get();
            LOG.fine("[EnterpriseRouter] Layer 0 hit: " + plan.toAuditString());
            return plan;
        }

        Optional<RoutingPlan> layer1Result = semanticCache.findSimilar(query);
        if (layer1Result.isPresent()) {
            layer1Hits++;
            RoutingPlan plan = layer1Result.get();
            LOG.fine("[EnterpriseRouter] Layer 1 hit: " + plan.toAuditString());
            return plan;
        }

        if (canUseCognitiveLayer()) {
            try {
                layer2Hits++;
                long startL2 = System.currentTimeMillis();
                RoutingPlan plan = cognitiveAnalyzer.analyze(query);
                long elapsedL2 = System.currentTimeMillis() - startL2;

                if (elapsedL2 > 500) {
                    LOG.warning("[EnterpriseRouter] Capa 2 lenta: " + elapsedL2 + "ms (umbral 500ms)");
                }

                cognitiveCB.recordSuccess();
                LOG.fine("[EnterpriseRouter] Layer 2 analysis: " + plan.toAuditString());

                if (plan.decisionSource() != RoutingPlan.DecisionSource.DEFAULT_FALLBACK) {
                    semanticCache.store(query, plan);
                }

                return plan;
            } catch (Exception e) {
                cognitiveCB.recordFailure(e.getMessage());
                LOG.warning("[MILITARY-GRADE] Fallo en Capa 2: " + e.getMessage() +
                           " | Estado CB: " + cognitiveCB.getState());

                if (cognitiveCB.isOpen()) {
                    LOG.severe("[SYSTEM] Router entrando en MODO DEGRADADO (Solo Capa 0 y 1)");
                }
            }
        }

        LOG.fine("[EnterpriseRouter] No LLM available, using default");
        return buildDefaultPlan("LLM not available - defaulting to LOCAL");
    }

    public RoutingPlan route(String query, String context) {
        return route(query);
    }

    public RoutingPlan route(String query, ContextProfile profile) {
        if (query == null || query.isBlank()) {
            return buildDefaultPlan("Empty query");
        }

        RoutingPlan basePlan = route(query);

        if (basePlan.target() == RoutingPlan.TargetModel.EXPERT) {
            return basePlan;
        }

        int estimatedTokens = estimateTokens(profile);
        boolean hasMemorySpace = estimatedTokens < currentRabbitPower.maxSafeContextTokens;

        if (!hasMemorySpace) {
            LOG.info("[EnterpriseRouter] Rabbit " + currentRabbitPower.displayName +
                     " sin memoria para " + profile + " (" + estimatedTokens +
                     " > " + currentRabbitPower.maxSafeContextTokens + ") -> Tortuga");

            return RoutingPlan.builder()
                .target(RoutingPlan.TargetModel.EXPERT)
                .intent(basePlan.detectedIntent())
                .complexity(basePlan.complexityScore())
                .requiresInternet(false)
                .reasoning("Rabbit " + currentRabbitPower.displayName +
                           " sin memoria para contexto " + profile +
                           " (" + estimatedTokens + " tokens > " +
                           currentRabbitPower.maxSafeContextTokens + " límite)")
                .source(RoutingPlan.DecisionSource.TRAFFIC_BYPASS)
                .timeMs(basePlan.decisionTimeMs())
                .build();
        }

        LOG.fine("[EnterpriseRouter] Rabbit " + currentRabbitPower.displayName +
                 " asignado. Contexto " + profile + " (" + estimatedTokens + " tokens)");
        return basePlan;
    }

    public void setRabbitPower(RabbitPower power) {
        RabbitPower previous = this.currentRabbitPower;
        this.currentRabbitPower = power != null ? power : RabbitPower.WEAK_1B;
        LOG.info("[EnterpriseRouter] Potencia de Rabbit: " + previous + " -> " + this.currentRabbitPower);
    }

    public void setRabbitPowerFromModel(String modelName) {
        setRabbitPower(RabbitPower.fromModelName(modelName));
    }

    public RabbitPower getCurrentRabbitPower() {
        return currentRabbitPower;
    }

    private int estimateTokens(ContextProfile profile) {
        if (profile == null) return AppDefaults.TOKENS_TACTICAL;
        return switch (profile) {
            case SKELETAL -> AppDefaults.TOKENS_SKELETAL;
            case TACTICAL -> AppDefaults.TOKENS_TACTICAL;
            case STRATEGIC -> AppDefaults.TOKENS_STRATEGIC;
        };
    }

    public void clearCache() {
        semanticCache.clear();
        LOG.info("[EnterpriseRouter] Semantic cache cleared");
    }

    public int cleanupCache() {
        return semanticCache.cleanup();
    }

    public boolean isFullyOperational() {
        return cognitiveAnalyzer != null && cognitiveAnalyzer.isReady();
    }

    public boolean isOperational() {
        return true;
    }

    public RouterStats getStats() {
        double bypassRate = totalRoutes > 0 ? (double) bypassHits / totalRoutes : 0.0;
        double layer0Rate = totalRoutes > 0 ? (double) layer0Hits / totalRoutes : 0.0;
        double layer1Rate = totalRoutes > 0 ? (double) layer1Hits / totalRoutes : 0.0;
        double layer2Rate = totalRoutes > 0 ? (double) layer2Hits / totalRoutes : 0.0;

        return new RouterStats(
            totalRoutes,
            bypassHits,
            layer0Hits,
            layer1Hits,
            layer2Hits,
            bypassRate,
            layer0Rate,
            layer1Rate,
            layer2Rate,
            semanticCache.size(),
            isFullyOperational()
        );
    }

    public String getStatsString() {
        RouterStats stats = getStats();
        return String.format(
            "EnterpriseRouter [total=%d, Bypass=%.1f%%, L0=%.1f%%, L1=%.1f%%, L2=%.1f%%, cache=%d, full=%s]",
            stats.totalRoutes(),
            stats.bypassRate() * 100,
            stats.layer0Rate() * 100,
            stats.layer1Rate() * 100,
            stats.layer2Rate() * 100,
            stats.cacheSize(),
            stats.fullyOperational()
        );
    }

    public String getDetailedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== EnterpriseRouter Stats ===\n");
        sb.append(getStatsString()).append("\n");
        sb.append(regexRegistry.getStats()).append("\n");
        sb.append(semanticCache.getStatsString()).append("\n");
        if (cognitiveAnalyzer != null) {
            sb.append(cognitiveAnalyzer.getStats()).append("\n");
        } else {
            sb.append("CognitiveAnalyzer [not available]\n");
        }
        CircuitBreaker.Stats cbStats = cognitiveCB.getStats();
        sb.append(String.format("CircuitBreaker [state=%s, failures=%d/%d, healthy=%s]\n",
            cbStats.state(), cbStats.failureCount(), cbStats.failureThreshold(), cbStats.isHealthy()));
        return sb.toString();
    }

    public CircuitBreaker.Stats getCircuitBreakerStats() {
        return cognitiveCB.getStats();
    }

    public void resetCircuitBreaker() {
        cognitiveCB.reset();
        LOG.info("[EnterpriseRouter] Circuit Breaker reiniciado manualmente");
    }

    public boolean isDegradedMode() {
        return cognitiveCB.isOpen();
    }

    @Override
    public void close() {
        LOG.info("[EnterpriseRouter] Closing - " + getStatsString());
    }

    private boolean canUseCognitiveLayer() {
        if (cognitiveAnalyzer == null || !cognitiveAnalyzer.isReady()) {
            return false;
        }
        if (cognitiveCB.isOpen()) {
            LOG.fine("[EnterpriseRouter] Capa 2 bloqueada por Circuit Breaker");
            return false;
        }
        return true;
    }

    private boolean isHighEntropy(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        String[] words = content.trim().split("\\s+");
        if (words.length < 3) {
            return false;
        }

        long uniqueChars = content.chars().distinct().count();
        double uniqueRatio = (double) uniqueChars / content.length();

        if (uniqueRatio < 0.05) {
            LOG.warning("[EnterpriseRouter] Baja entropía detectada: " +
                       String.format("%.1f%% caracteres únicos", uniqueRatio * 100));
            return false;
        }

        boolean hasLetter = content.chars().anyMatch(Character::isLetter);
        if (!hasLetter) {
            return false;
        }

        return true;
    }

    private RoutingPlan buildDefaultPlan(String reason) {
        return RoutingPlan.builder()
            .target(RoutingPlan.TargetModel.LOCAL)
            .intent(RoutingPlan.DetectedIntent.UNKNOWN)
            .complexity(0.5)
            .requiresInternet(false)
            .reasoning("Default - " + reason)
            .source(RoutingPlan.DecisionSource.DEFAULT_FALLBACK)
            .timeMs(0)
            .build();
    }

    public record RouterStats(
        long totalRoutes,
        long bypassHits,
        long layer0Hits,
        long layer1Hits,
        long layer2Hits,
        double bypassRate,
        double layer0Rate,
        double layer1Rate,
        double layer2Rate,
        int cacheSize,
        boolean fullyOperational
    ) {}
}
