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
package dev.fararoni.core.core.middleware;

import dev.fararoni.core.core.config.HardwareTier;
import dev.fararoni.core.core.skills.AlgorithmPatterns;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class PromptInjector {

    private static final Logger LOG = Logger.getLogger(PromptInjector.class.getName());

    private static final String PREFIX_LOW_RESOURCE =
            "═══════════════════════════════════════════════════════════════\n" +
                    "INSTRUCCION ESTRICTA (PROTOCOLO MILITAR):\n" +
                    "Sigue estos pasos EXACTOS sin desviarte ni intentar optimizar.\n" +
                    "═══════════════════════════════════════════════════════════════\n\n";

    private static final String PREFIX_MEDIUM =
            "───────────────────────────────────────────────────────────────\n" +
                    "CONTEXTO TÉCNICO: Considera el siguiente patrón estándar.\n" +
                    "───────────────────────────────────────────────────────────────\n\n";

    private static final String PREFIX_HIGH_PERFORMANCE =
            "┌─────────────────────────────────────────────────────────────┐\n" +
                    "│ TIP DE ARQUITECTURA: Enfoque sugerido para optimización.   │\n" +
                    "└─────────────────────────────────────────────────────────────┘\n\n";

    private static final String SEPARATOR =
            "\n───────────────────────────────────────────────────────────────\n" +
                    "PROBLEMA A RESOLVER:\n" +
                    "───────────────────────────────────────────────────────────────\n\n";

    private final AlgorithmPatterns algorithmPatterns;

    public PromptInjector() {
        this(new AlgorithmPatterns());
    }

    public PromptInjector(AlgorithmPatterns algorithmPatterns) {
        this.algorithmPatterns = algorithmPatterns;
    }

    public String inject(String basePrompt, String pattern, HardwareTier tier) {
        if (pattern == null || pattern.isBlank()) {
            return basePrompt != null ? basePrompt : "";
        }

        HardwareTier effectiveTier = tier != null ? tier : HardwareTier.MEDIUM;
        String prefix = getPrefixForTier(effectiveTier);

        return prefix + pattern + SEPARATOR + (basePrompt != null ? basePrompt : "");
    }

    public String injectWithAutoDetection(String basePrompt, String problemDescription, String modelName) {
        var detection = algorithmPatterns.detectBestPatternDetailed(problemDescription);

        if (detection.isEmpty() || !detection.get().isConfident()) {
            LOG.fine("[PromptInjector] No pattern detected, returning base prompt");
            return basePrompt != null ? basePrompt : "";
        }

        AlgorithmPatterns.DetectionResult result = detection.get();
        AlgorithmPatterns.PatternType patternType = result.type();

        HardwareTier nativeTier = HardwareTier.fromModelName(modelName);
        HardwareTier effectiveTier = nativeTier;

        if (result.complexity() == AlgorithmPatterns.Complexity.CRITICAL) {
            if (nativeTier != HardwareTier.LOW_RESOURCE) {
                LOG.info(String.format(
                        "[PromptInjector] [ALERT] CRITICAL PATTERN DETECTED (%s). Overriding tier %s -> LOW_RESOURCE for strict compliance.",
                        patternType, nativeTier));
            }
            effectiveTier = HardwareTier.LOW_RESOURCE;
        } else {
            LOG.info(String.format(
                    "[PromptInjector] Standard pattern (%s). Using native tier: %s",
                    patternType, nativeTier));
        }

        return inject(basePrompt, result.template(), effectiveTier);
    }

    private String getPrefixForTier(HardwareTier tier) {
        return switch (tier) {
            case LOW_RESOURCE -> PREFIX_LOW_RESOURCE;
            case HIGH_PERFORMANCE -> PREFIX_HIGH_PERFORMANCE;
            default -> PREFIX_MEDIUM;
        };
    }
}
