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
package dev.fararoni.core.core.hybrid;

import dev.fararoni.core.core.utils.TokenUtils;
import dev.fararoni.core.core.config.AgentConfig;

import java.util.Objects;
import java.util.function.Function;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DraftGenerator {
    private final UserIntentRouter router;
    private final ContextStrategySelector strategySelector;
    private final IntentAwarePruner pruner;
    private final Function<String, String> rabbitGenerator;

    public DraftGenerator(Function<String, String> rabbitGenerator) {
        this.router = UserIntentRouter.getInstance();
        this.strategySelector = ContextStrategySelector.getInstance();
        this.pruner = IntentAwarePruner.getInstance();
        this.rabbitGenerator = Objects.requireNonNull(rabbitGenerator, "rabbitGenerator no puede ser null");
    }

    DraftGenerator(UserIntentRouter router, ContextStrategySelector selector,
                   IntentAwarePruner pruner, Function<String, String> generator) {
        this.router = router;
        this.strategySelector = selector;
        this.pruner = pruner;
        this.rabbitGenerator = generator;
    }

    public DraftResult generatePlanSafely(String userQuery, String fullContext) {
        Objects.requireNonNull(userQuery, "userQuery no puede ser null");
        Objects.requireNonNull(fullContext, "fullContext no puede ser null");

        try {
            UserIntentRouter.DetectionResult detection = router.detect(userQuery);
            UserIntent intent = detection.intent();
            String targetMethod = detection.targetMethod();

            PruningStrategy strategy = strategySelector.selectStrategy(intent, fullContext, targetMethod);

            if (!strategy.canProceed()) {
                return DraftResult.skipped(
                    "Strategy exhausted: " + intent + " → ABORT",
                    fullContext.length()
                );
            }

            String prunedContext;
            try {
                prunedContext = pruner.applyStrategy(fullContext, strategy, targetMethod);
            } catch (ContextPruningException e) {
                return DraftResult.skipped("Pruning failed: " + e.getMessage(), fullContext.length());
            }

            if (prunedContext.length() > TokenUtils.RABBIT_MAX_CHARS) {
                return DraftResult.skipped(
                    String.format("Context overflow after pruning: %d > %d chars",
                        prunedContext.length(), TokenUtils.RABBIT_MAX_CHARS),
                    prunedContext.length()
                );
            }

            String prompt = buildPrompt(userQuery, prunedContext, intent);
            String draft = rabbitGenerator.apply(prompt);

            return DraftResult.success(draft, strategy, prunedContext.length());
        } catch (Exception e) {
            return DraftResult.skipped("Unexpected error: " + e.getMessage(), fullContext.length());
        }
    }

    public DraftResult generatePlanSafely(String userQuery, String fullContext, String targetMethod) {
        Objects.requireNonNull(userQuery, "userQuery no puede ser null");
        Objects.requireNonNull(fullContext, "fullContext no puede ser null");

        try {
            UserIntent intent = router.detectIntent(userQuery);

            PruningStrategy strategy = strategySelector.selectStrategy(intent, fullContext, targetMethod);

            if (!strategy.canProceed()) {
                return DraftResult.skipped("Strategy ABORT for intent: " + intent, fullContext.length());
            }

            String prunedContext = pruner.applyStrategy(fullContext, strategy, targetMethod);

            if (prunedContext.length() > TokenUtils.RABBIT_MAX_CHARS) {
                return DraftResult.skipped("Context overflow: " + prunedContext.length(), prunedContext.length());
            }

            String prompt = buildPrompt(userQuery, prunedContext, intent);
            String draft = rabbitGenerator.apply(prompt);

            return DraftResult.success(draft, strategy, prunedContext.length());
        } catch (Exception e) {
            return DraftResult.skipped("Error: " + e.getMessage(), fullContext.length());
        }
    }

    private String buildPrompt(String userQuery, String context, UserIntent intent) {
        return String.format("""
            ## Tarea: %s

            ## Intención Detectada: %s

            ## Contexto (código relevante):
            ```java
            %s
            ```

            ## Instrucciones para el Plan:
            1. Analiza cuidadosamente el contexto proporcionado
            2. Genera un plan paso a paso para completar la tarea
            3. Sé específico sobre qué cambios hacer y dónde
            4. No generes código completo, solo el plan estructurado

            ## Plan:
            """,
            userQuery,
            intent.getDescription(),
            context
        );
    }

    public boolean canHandle(String fullContext, String userQuery) {
        UserIntent intent = router.detectIntent(userQuery);
        return strategySelector.canRabbitHandle(fullContext, intent);
    }
}
