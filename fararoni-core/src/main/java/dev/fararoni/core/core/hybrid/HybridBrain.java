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

import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class HybridBrain {
    private static final Logger LOG = Logger.getLogger(HybridBrain.class.getName());

    private final DraftGenerator draftGenerator;
    private final Function<String, String> turtleGenerator;

    public HybridBrain(Function<String, String> rabbitGenerator,
                       Function<String, String> turtleGenerator) {
        this.draftGenerator = new DraftGenerator(rabbitGenerator);
        this.turtleGenerator = Objects.requireNonNull(turtleGenerator, "turtleGenerator no puede ser null");
    }

    HybridBrain(DraftGenerator draftGenerator, Function<String, String> turtleGenerator) {
        this.draftGenerator = Objects.requireNonNull(draftGenerator);
        this.turtleGenerator = Objects.requireNonNull(turtleGenerator);
    }

    public HybridResponse process(String userQuery, String fullContext) {
        Objects.requireNonNull(userQuery, "userQuery no puede ser null");
        Objects.requireNonNull(fullContext, "fullContext no puede ser null");

        DraftResult draftResult = draftGenerator.generatePlanSafely(userQuery, fullContext);

        if (draftResult.isSuccess()) {
            String draft = draftResult.getDraftOrThrow();
            String finalResponse = expandDraft(userQuery, draft, fullContext);

            return HybridResponse.withDraft(
                finalResponse,
                draft,
                draftResult.getStrategyUsed(),
                draftResult.getContextSizeChars()
            );
        } else {
            String skipReason = draftResult.getSkipReason().orElse("Unknown");
            LOG.info("[HybridBrain] Rabbit surrender: " + skipReason);

            String finalResponse = turtleDirect(userQuery, fullContext);

            return HybridResponse.turtleDirect(
                finalResponse,
                skipReason,
                fullContext.length()
            );
        }
    }

    public HybridResponse process(String userQuery, String fullContext, String targetMethod) {
        Objects.requireNonNull(userQuery, "userQuery no puede ser null");
        Objects.requireNonNull(fullContext, "fullContext no puede ser null");

        DraftResult draftResult = draftGenerator.generatePlanSafely(userQuery, fullContext, targetMethod);

        if (draftResult.isSuccess()) {
            String draft = draftResult.getDraftOrThrow();
            String finalResponse = expandDraft(userQuery, draft, fullContext);

            return HybridResponse.withDraft(
                finalResponse,
                draft,
                draftResult.getStrategyUsed(),
                draftResult.getContextSizeChars()
            );
        } else {
            String skipReason = draftResult.getSkipReason().orElse("Unknown");
            String finalResponse = turtleDirect(userQuery, fullContext);

            return HybridResponse.turtleDirect(finalResponse, skipReason, fullContext.length());
        }
    }

    private String expandDraft(String userQuery, String draft, String fullContext) {
        String expansionPrompt = buildExpansionPrompt(userQuery, draft, fullContext);
        return turtleGenerator.apply(expansionPrompt);
    }

    private String turtleDirect(String userQuery, String fullContext) {
        String directPrompt = buildDirectPrompt(userQuery, fullContext);
        return turtleGenerator.apply(directPrompt);
    }

    private String buildExpansionPrompt(String userQuery, String draft, String fullContext) {
        return String.format("""
            ## Tarea Original
            %s

            ## Plan Generado (por modelo de planificación)
            %s

            ## Contexto Completo
            ```
            %s
            ```

            ## Instrucciones
            Ejecuta el plan anterior paso a paso. Genera el código completo y funcional.
            Sigue el plan fielmente pero usa tu conocimiento para implementar los detalles.
            """,
            userQuery,
            draft,
            fullContext.length() > 50000 ? fullContext.substring(0, 50000) + "\n... [truncated]" : fullContext
        );
    }

    private String buildDirectPrompt(String userQuery, String fullContext) {
        return String.format("""
            ## Tarea
            %s

            ## Contexto
            ```
            %s
            ```

            ## Instrucciones
            Completa la tarea solicitada. Genera código funcional y bien estructurado.
            """,
            userQuery,
            fullContext
        );
    }

    public boolean canRabbitHandle(String fullContext, String userQuery) {
        return draftGenerator.canHandle(fullContext, userQuery);
    }

    public record HybridResponse(
        String response,
        boolean usedRabbit,
        String draft,
        PruningStrategy strategyUsed,
        String skipReason,
        int contextSizeProcessed
    ) {
        public static HybridResponse withDraft(String response, String draft,
                                                PruningStrategy strategy, int contextSize) {
            return new HybridResponse(response, true, draft, strategy, null, contextSize);
        }

        public static HybridResponse turtleDirect(String response, String skipReason, int contextSize) {
            return new HybridResponse(response, false, null, PruningStrategy.ABORT, skipReason, contextSize);
        }

        public boolean usedHybridFlow() {
            return usedRabbit && draft != null;
        }
    }
}
