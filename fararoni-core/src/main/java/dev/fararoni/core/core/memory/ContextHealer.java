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
package dev.fararoni.core.core.memory;

import dev.fararoni.core.core.routing.RoutingPlan;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.1.0 (Phoenix 2.1 - Sticky Session Routing)
 * @since 1.0.0
 */
public class ContextHealer {
    private static final Logger LOG = Logger.getLogger(ContextHealer.class.getName());

    public record LimboState(
        String prompt,
        RoutingPlan.TargetModel model,
        long timestamp
    ) {}

    private LimboState limbo = null;

    private static final long LIMBO_TTL_MS = 5 * 60 * 1000;

    public void moveToLimbo(String prompt, RoutingPlan.TargetModel model) {
        this.limbo = new LimboState(prompt, model, System.currentTimeMillis());
        LOG.info(() -> "[PHOENIX] Prompt guardado en Limbo. Modelo: " + model);
    }

    public void moveToLimbo(String prompt) {
        moveToLimbo(prompt, RoutingPlan.TargetModel.EXPERT);
    }

    public boolean hasPendingContext() {
        if (limbo == null) {
            return false;
        }
        if (System.currentTimeMillis() - limbo.timestamp() > LIMBO_TTL_MS) {
            LOG.info("[PHOENIX] Limbo expirado (TTL > 5min). Limpiando.");
            limbo = null;
            return false;
        }
        return true;
    }

    public Optional<String> consumeLimbo() {
        if (limbo == null) {
            return Optional.empty();
        }
        String prompt = limbo.prompt();
        limbo = null;
        return Optional.of(prompt);
    }

    public String peekLimbo() {
        return hasPendingContext() ? limbo.prompt() : null;
    }

    public RoutingPlan.TargetModel getLimboModel() {
        return hasPendingContext() ? limbo.model() : null;
    }

    public void clearLimbo() {
        this.limbo = null;
        LOG.info("[PHOENIX] Limbo limpiado manualmente.");
    }

    public RoutingPlan.TargetModel resolveTargetModel(
            RoutingPlan.TargetModel currentDecision,
            String newPrompt) {
        if (limbo == null) {
            return currentDecision;
        }

        if (limbo.model() == RoutingPlan.TargetModel.EXPERT) {
            if (newPrompt != null && newPrompt.length() < 50) {
                LOG.info("[PHOENIX] Prompt corto (<50 chars). Forzando TURTLE.");
                return RoutingPlan.TargetModel.EXPERT;
            }

            if (isContinuation(newPrompt)) {
                LOG.info("[PHOENIX] Continuacion detectada. Forzando TURTLE.");
                return RoutingPlan.TargetModel.EXPERT;
            }

            if (currentDecision == RoutingPlan.TargetModel.LOCAL) {
                LOG.info("[PHOENIX] Router dijo LOCAL pero tenemos contexto EXPERT. Forzando TURTLE.");
                return RoutingPlan.TargetModel.EXPERT;
            }
        }

        if (currentDecision == RoutingPlan.TargetModel.EXPERT) {
            LOG.info("[PHOENIX] Escalada detectada. Respetando TURTLE.");
            return RoutingPlan.TargetModel.EXPERT;
        }

        return currentDecision;
    }

    private boolean isContinuation(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        String s = text.toLowerCase().trim();

        boolean startsWithConnector =
            s.startsWith("y ") || s.startsWith("pero") || s.startsWith("mas") ||
            s.startsWith("entonces") || s.startsWith("ademas") || s.startsWith("tambien") ||
            s.startsWith("aunque") || s.startsWith("con ") || s.startsWith("sin ") ||
            s.startsWith("que") || s.startsWith("como") || s.startsWith("donde") ||
            s.startsWith("si") || s.startsWith("es ") || s.startsWith("esta") ||
            s.startsWith("dime") || s.startsWith("explica") || s.startsWith("muestra") ||
            s.startsWith("and ") || s.startsWith("but ") || s.startsWith("also ") ||
            s.startsWith("however ") || s.startsWith("what") || s.startsWith("how") ||
            s.startsWith("is it") || s.startsWith("is the");

        boolean hasAnaphoricReference =
            s.contains("eso") || s.contains("esto") || s.contains("el codigo") ||
            s.contains("el proyecto") || s.contains("la arquitectura") ||
            s.contains("this") || s.contains("that") || s.contains("the code");

        if (startsWithConnector || hasAnaphoricReference) {
            LOG.fine(() -> "[PHOENIX] isContinuation=true para: " + truncate(s, 30));
            return true;
        }

        return false;
    }

    public String healContext(String newPrompt) {
        if (!hasPendingContext()) {
            return newPrompt;
        }

        String previousPrompt = limbo.prompt();

        return String.format(
            """
            [SYSTEM_RECOVERY: INTERRUPTED_CONTEXT_MERGE]
            The user previously interrupted this heavy request:
            "%s"

            Immediately after, they asked:
            "%s"

            INSTRUCTION:
            1. The user is likely asking about the interrupted topic.
            2. Combine the intent. Treat "%s" as applying to the context of "%s".
            3. Answer appropriately assuming the full context is active.
            4. Do NOT ask for clarification - assume continuity.

            %s""",
            truncate(previousPrompt, 300),
            truncate(newPrompt, 200),
            truncate(newPrompt, 100),
            truncate(previousPrompt, 100),
            newPrompt
        );
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
