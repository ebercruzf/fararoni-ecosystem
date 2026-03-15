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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record RoutingPlan(
    TargetModel target,
    DetectedIntent detectedIntent,
    double complexityScore,
    boolean requiresInternet,
    String reasoning,
    DecisionSource decisionSource,
    long decisionTimeMs
) {
    public enum TargetModel {
        LOCAL(0, "Qwen 1.5B (El Capataz)", false),

        EXPERT(1, "Qwen 32B / Cloud (El Experto)", true);

        private final int tier;
        private final String description;
        private final boolean mayRequireCloud;

        TargetModel(int tier, String description, boolean mayRequireCloud) {
            this.tier = tier;
            this.description = description;
            this.mayRequireCloud = mayRequireCloud;
        }

        public int tier() { return tier; }

        public String description() { return description; }

        public boolean mayRequireCloud() { return mayRequireCloud; }
    }

    public enum DetectedIntent {
        GREETING("Saludo"),

        SYSTEM_CMD("Comando de Sistema"),

        CONFIG("Configuracion"),

        CODE_GEN("Generacion de Codigo"),

        CODE_READ("Lectura de Codigo"),

        DEBUG("Debugging"),

        REFACTOR("Refactorizacion"),

        ARCHITECTURE("Arquitectura"),

        SECURITY("Seguridad"),

        DOCUMENTATION("Documentacion"),

        HEAVY_PAYLOAD("Carga Masiva"),

        UNKNOWN("Desconocido");

        private final String displayName;

        DetectedIntent(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum DecisionSource {
        TRAFFIC_BYPASS("Capa -1 - Traffic Controller (Bypass)"),

        LAYER_0_REFLEX("Capa 0 - Reflejos (Regex)"),

        LAYER_1_SEMANTIC("Capa 1 - Memoria Semantica (Cache)"),

        LAYER_2_COGNITIVE("Capa 2 - Juicio Cognitivo (LLM+GBNF)"),

        DEFAULT_FALLBACK("Fallback por Defecto");

        private final String description;

        DecisionSource(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TargetModel target = TargetModel.LOCAL;
        private DetectedIntent intent = DetectedIntent.UNKNOWN;
        private double complexity = 0.5;
        private boolean requiresInternet = false;
        private String reasoning = "";
        private DecisionSource source = DecisionSource.DEFAULT_FALLBACK;
        private long timeMs = 0;

        public Builder target(TargetModel t) {
            this.target = t;
            return this;
        }

        public Builder intent(DetectedIntent i) {
            this.intent = i;
            return this;
        }

        public Builder complexity(double c) {
            this.complexity = Math.max(0.0, Math.min(1.0, c));
            return this;
        }

        public Builder requiresInternet(boolean r) {
            this.requiresInternet = r;
            return this;
        }

        public Builder reasoning(String r) {
            this.reasoning = r != null ? r : "";
            return this;
        }

        public Builder source(DecisionSource s) {
            this.source = s;
            return this;
        }

        public Builder timeMs(long t) {
            this.timeMs = Math.max(0, t);
            return this;
        }

        public RoutingPlan build() {
            return new RoutingPlan(
                target,
                intent,
                complexity,
                requiresInternet,
                reasoning,
                source,
                timeMs
            );
        }
    }

    public String toAuditString() {
        return String.format("[%s] %s -> %s (%.2f) in %dms | %s",
            decisionSource.name(),
            detectedIntent.name(),
            target.name(),
            complexityScore,
            decisionTimeMs,
            reasoning
        );
    }

    public String toDisplayString() {
        return String.format("%s: %s (complejidad=%.0f%%) via %s",
            target.description(),
            detectedIntent.getDisplayName(),
            complexityScore * 100,
            decisionSource.getDescription()
        );
    }

    public boolean wasAiDecision() {
        return decisionSource == DecisionSource.LAYER_2_COGNITIVE;
    }

    public boolean wasInstantDecision() {
        return decisionSource == DecisionSource.LAYER_0_REFLEX;
    }

    public boolean wasCachedDecision() {
        return decisionSource == DecisionSource.LAYER_1_SEMANTIC;
    }
}
