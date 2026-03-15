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
package dev.fararoni.core.core.persona;

import dev.fararoni.core.core.engine.ReflexionEngine;
import dev.fararoni.core.core.engine.ReflexionResult;
import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.reflexion.critics.*;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CognitiveEngine {
    private final PersonaRegistry registry;
    private final boolean autoSelectPersona;
    private final boolean adaptCriticsToPersona;
    private Persona currentPersona;

    private CognitiveEngine(Builder builder) {
        this.registry = builder.registry != null
            ? builder.registry
            : PersonaRegistry.getInstance();
        this.autoSelectPersona = builder.autoSelectPersona;
        this.adaptCriticsToPersona = builder.adaptCriticsToPersona;
        this.currentPersona = builder.defaultPersona != null
            ? builder.defaultPersona
            : Personas.DEVELOPER;
    }

    public CognitiveResult process(String taskDescription, String response, EvaluationContext context) {
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Persona persona = autoSelectPersona && taskDescription != null
            ? registry.selectFor(taskDescription)
            : currentPersona;

        return processAs(persona, response, context);
    }

    public CognitiveResult processAs(Persona persona, String response, EvaluationContext context) {
        Objects.requireNonNull(persona, "persona must not be null");
        Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(context, "context must not be null");

        ReflexionEngine engine = createEngineForPersona(persona);

        EvaluationContext enrichedContext = context.withMetadata("persona", persona.id());

        ReflexionResult reflexionResult = engine.reflect(response, enrichedContext);

        return new CognitiveResult(persona, reflexionResult, response);
    }

    public String generateSystemPrompt() {
        return currentPersona.generateSystemPrompt();
    }

    public String generateSystemPrompt(Persona persona) {
        return persona.generateSystemPrompt();
    }

    public String buildFullPrompt(String userPrompt) {
        return buildFullPrompt(currentPersona, userPrompt);
    }

    public String buildFullPrompt(Persona persona, String userPrompt) {
        String systemPrompt = persona.generateSystemPrompt();
        if (systemPrompt.isBlank()) {
            return userPrompt;
        }
        return systemPrompt + "\n\n---\n\n" + userPrompt;
    }

    public Persona getCurrentPersona() {
        return currentPersona;
    }

    public void setCurrentPersona(Persona persona) {
        this.currentPersona = Objects.requireNonNull(persona, "persona must not be null");
    }

    public boolean setCurrentPersonaById(String personaId) {
        Optional<Persona> persona = registry.get(personaId);
        persona.ifPresent(p -> this.currentPersona = p);
        return persona.isPresent();
    }

    public PersonaRegistry getRegistry() {
        return registry;
    }

    public Persona selectPersonaFor(String taskDescription) {
        return registry.selectFor(taskDescription);
    }

    private ReflexionEngine createEngineForPersona(Persona persona) {
        ReflexionEngine.Builder builder = ReflexionEngine.builder();

        if (adaptCriticsToPersona) {
            for (Critic.CriticCategory category : persona.priorityCritics()) {
                addCriticsForCategory(builder, category);
            }

            if (persona.priorityCritics().isEmpty()) {
                builder.addCritic(new CompletenessCritic());
                builder.addCritic(new AssumptionCritic());
            }
        } else {
            builder.addCritic(new CompletenessCritic());
            builder.addCritic(new AssumptionCritic());
            builder.addCritic(new SecurityCritic());
        }

        return builder.build();
    }

    private void addCriticsForCategory(ReflexionEngine.Builder builder, Critic.CriticCategory category) {
        switch (category) {
            case QUALITY -> {
                builder.addCritic(new AssumptionCritic());
                builder.addCritic(new CompletenessCritic());
                builder.addCritic(new EvidenceCritic());
            }
            case SECURITY -> {
                builder.addCritic(new SecurityCritic());
                builder.addCritic(new PiiDetectionCritic());
            }
            case CODE -> {
                builder.addCritic(new SyntaxCritic());
                builder.addCritic(new CompletenessCritic().withCheckCodeBlocks(true));
            }
            case COMPLIANCE -> {
                builder.addCritic(new PiiDetectionCritic());
            }
            case FORMAT -> {
                builder.addCritic(new CompletenessCritic());
                builder.addCritic(PatternCritic.markdownValidator());
            }
            case GENERAL -> {
                builder.addCritic(new CompletenessCritic());
            }
        }
    }

    public static CognitiveEngine standard() {
        return builder()
            .autoSelectPersona(true)
            .adaptCriticsToPersona(true)
            .build();
    }

    public static CognitiveEngine withPersona(Persona persona) {
        return builder()
            .defaultPersona(persona)
            .autoSelectPersona(false)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private PersonaRegistry registry;
        private Persona defaultPersona;
        private boolean autoSelectPersona = true;
        private boolean adaptCriticsToPersona = true;

        private Builder() {}

        public Builder registry(PersonaRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder defaultPersona(Persona persona) {
            this.defaultPersona = persona;
            return this;
        }

        public Builder autoSelectPersona(boolean auto) {
            this.autoSelectPersona = auto;
            return this;
        }

        public Builder adaptCriticsToPersona(boolean adapt) {
            this.adaptCriticsToPersona = adapt;
            return this;
        }

        public CognitiveEngine build() {
            return new CognitiveEngine(this);
        }
    }

    public record CognitiveResult(
        Persona persona,
        ReflexionResult reflexionResult,
        String originalResponse
    ) {
        public boolean isAccepted() {
            return !reflexionResult.requiresRegeneration();
        }

        public boolean isPerfect() {
            return reflexionResult.isClean();
        }

        public String getFeedback() {
            if (isAccepted()) {
                return "";
            }
            return reflexionResult.getFeedbackForLlm();
        }

        public String improvedPrompt(String originalPrompt) {
            String feedback = getFeedback();
            if (feedback.isBlank()) {
                return originalPrompt;
            }
            return originalPrompt + "\n\n" + feedback;
        }

        public String toSummary() {
            return String.format(
                "CognitiveResult[persona=%s, accepted=%s, warnings=%d, failures=%d]",
                persona.id(),
                isAccepted(),
                reflexionResult.warnings().size(),
                reflexionResult.failures().size()
            );
        }
    }
}
