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

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Persona System - Sistema de Personas Cognitivas")
class PersonaTest {
    @Nested
    @DisplayName("Persona Record")
    class PersonaRecordTests {
        @Test
        @DisplayName("Builder crea persona correctamente")
        void builder_createsPersona() {
            Persona persona = Persona.builder("test-persona")
                .name("Test Persona")
                .description("A test persona")
                .expertise("java", "testing")
                .style(Persona.CommunicationStyle.TECHNICAL)
                .priorityCritics(Critic.CriticCategory.CODE)
                .build();

            assertEquals("test-persona", persona.id());
            assertEquals("Test Persona", persona.name());
            assertEquals("A test persona", persona.description());
            assertTrue(persona.expertise().contains("java"));
            assertEquals(Persona.CommunicationStyle.TECHNICAL, persona.style());
            assertTrue(persona.prioritizes(Critic.CriticCategory.CODE));
        }

        @Test
        @DisplayName("hasExpertise() es case-insensitive")
        void hasExpertise_isCaseInsensitive() {
            Persona persona = Persona.builder("test")
                .expertise("Java", "Testing")
                .build();

            assertTrue(persona.hasExpertise("java"));
            assertTrue(persona.hasExpertise("JAVA"));
            assertTrue(persona.hasExpertise("testing"));
            assertFalse(persona.hasExpertise("python"));
        }

        @Test
        @DisplayName("prioritizes() verifica categorias correctamente")
        void prioritizes_checksCategories() {
            Persona persona = Persona.builder("test")
                .priorityCritics(Critic.CriticCategory.SECURITY, Critic.CriticCategory.CODE)
                .build();

            assertTrue(persona.prioritizes(Critic.CriticCategory.SECURITY));
            assertTrue(persona.prioritizes(Critic.CriticCategory.CODE));
            assertFalse(persona.prioritizes(Critic.CriticCategory.QUALITY));
        }

        @Test
        @DisplayName("generateSystemPrompt() genera prompt")
        void generateSystemPrompt_generatesPrompt() {
            Persona persona = Persona.builder("test")
                .name("Test Expert")
                .description("An expert in testing")
                .expertise("testing", "qa")
                .style(Persona.CommunicationStyle.DETAILED)
                .build();

            String prompt = persona.generateSystemPrompt();

            assertTrue(prompt.contains("Test Expert"));
            assertTrue(prompt.contains("testing"));
            assertTrue(prompt.contains("qa"));
        }

        @Test
        @DisplayName("generateSystemPrompt() usa systemPrompt custom si existe")
        void generateSystemPrompt_usesCustomIfProvided() {
            Persona persona = Persona.builder("test")
                .systemPrompt("Custom system prompt here")
                .build();

            assertEquals("Custom system prompt here", persona.generateSystemPrompt());
        }

        @Test
        @DisplayName("withMetadata() crea copia con metadata")
        void withMetadata_createsCopyWithMetadata() {
            Persona original = Persona.builder("test").build();

            Persona withMeta = original.withMetadata("key", "value");

            assertNotSame(original, withMeta);
            assertEquals("value", withMeta.getMetadata("key"));
            assertNull(original.getMetadata("key"));
        }

        @Test
        @DisplayName("Constructor valida id no null")
        void constructor_validatesIdNotNull() {
            assertThrows(NullPointerException.class, () ->
                Persona.builder(null).build());
        }

        @Test
        @DisplayName("Constructor valida id no blank")
        void constructor_validatesIdNotBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                Persona.builder("  ").build());
        }
    }

    @Nested
    @DisplayName("Personas Predefinidas")
    class PredefinedPersonasTests {
        @Test
        @DisplayName("Todas las personas predefinidas estan disponibles")
        void allPredefinedPersonas_areAvailable() {
            List<Persona> all = Personas.all();

            assertEquals(7, all.size());
            assertNotNull(Personas.ANALYST);
            assertNotNull(Personas.ARCHITECT);
            assertNotNull(Personas.DEVELOPER);
            assertNotNull(Personas.SECURITY);
            assertNotNull(Personas.REVIEWER);
            assertNotNull(Personas.DEVOPS);
            assertNotNull(Personas.TESTER);
        }

        @Test
        @DisplayName("byId() encuentra persona por ID")
        void byId_findsPersonaById() {
            assertEquals(Personas.DEVELOPER, Personas.byId("developer"));
            assertEquals(Personas.SECURITY, Personas.byId("security"));
            assertNull(Personas.byId("nonexistent"));
        }

        @Test
        @DisplayName("byId() es case-insensitive")
        void byId_isCaseInsensitive() {
            assertEquals(Personas.DEVELOPER, Personas.byId("DEVELOPER"));
            assertEquals(Personas.SECURITY, Personas.byId("Security"));
        }

        @Test
        @DisplayName("defaultPersona() retorna DEVELOPER")
        void defaultPersona_returnsDeveloper() {
            assertEquals(Personas.DEVELOPER, Personas.defaultPersona());
        }

        @Test
        @DisplayName("withExpertise() filtra correctamente")
        void withExpertise_filtersCorrectly() {
            List<Persona> securityExperts = Personas.withExpertise("security");

            assertTrue(securityExperts.contains(Personas.SECURITY));
        }

        @Test
        @DisplayName("prioritizing() filtra por categoria")
        void prioritizing_filtersByCategory() {
            List<Persona> securityPriority = Personas.prioritizing(Critic.CriticCategory.SECURITY);

            assertTrue(securityPriority.contains(Personas.SECURITY));
        }

        @Test
        @DisplayName("Cada persona tiene system prompt valido")
        void eachPersona_hasValidSystemPrompt() {
            for (Persona persona : Personas.all()) {
                String prompt = persona.generateSystemPrompt();
                assertNotNull(prompt);
                assertFalse(prompt.isBlank());
            }
        }
    }

    @Nested
    @DisplayName("PersonaRegistry")
    class PersonaRegistryTests {
        @Test
        @DisplayName("getInstance() retorna singleton")
        void getInstance_returnsSingleton() {
            PersonaRegistry r1 = PersonaRegistry.getInstance();
            PersonaRegistry r2 = PersonaRegistry.getInstance();

            assertSame(r1, r2);
        }

        @Test
        @DisplayName("newInstance() crea nueva instancia")
        void newInstance_createsNewInstance() {
            PersonaRegistry r1 = PersonaRegistry.newInstance();
            PersonaRegistry r2 = PersonaRegistry.newInstance();

            assertNotSame(r1, r2);
        }

        @Test
        @DisplayName("Registry tiene personas predefinidas")
        void registry_hasPredefinedPersonas() {
            PersonaRegistry registry = PersonaRegistry.newInstance();

            assertTrue(registry.contains("developer"));
            assertTrue(registry.contains("security"));
            assertTrue(registry.contains("architect"));
            assertEquals(7, registry.size());
        }

        @Test
        @DisplayName("register() agrega nueva persona")
        void register_addsNewPersona() {
            PersonaRegistry registry = PersonaRegistry.newInstance();
            Persona custom = Persona.builder("custom").name("Custom").build();

            registry.register(custom);

            assertTrue(registry.contains("custom"));
            assertEquals(8, registry.size());
        }

        @Test
        @DisplayName("register() lanza excepcion si ya existe")
        void register_throwsIfExists() {
            PersonaRegistry registry = PersonaRegistry.newInstance();
            Persona dup = Persona.builder("developer").name("Dup").build();

            assertThrows(IllegalArgumentException.class, () ->
                registry.register(dup));
        }

        @Test
        @DisplayName("registerOrUpdate() actualiza si existe")
        void registerOrUpdate_updatesIfExists() {
            PersonaRegistry registry = PersonaRegistry.newInstance();
            Persona updated = Persona.builder("developer").name("Updated Dev").build();

            registry.registerOrUpdate(updated);

            assertEquals("Updated Dev", registry.get("developer").get().name());
        }

        @Test
        @DisplayName("unregister() remueve persona")
        void unregister_removesPersona() {
            PersonaRegistry registry = PersonaRegistry.newInstance();

            assertTrue(registry.unregister("tester"));
            assertFalse(registry.contains("tester"));
        }

        @Test
        @DisplayName("selectFor() selecciona persona correcta")
        void selectFor_selectsCorrectPersona() {
            PersonaRegistry registry = PersonaRegistry.newInstance();

            assertEquals("security", registry.selectFor("review security vulnerabilities").id());
            assertEquals("architect", registry.selectFor("design the architecture").id());
            assertEquals("developer", registry.selectFor("implement the feature").id());
            assertEquals("tester", registry.selectFor("write unit tests").id());
            assertEquals("reviewer", registry.selectFor("review this PR").id());
            assertEquals("devops", registry.selectFor("setup CI/CD pipeline").id());
        }

        @Test
        @DisplayName("selectFor() retorna default para tarea generica")
        void selectFor_returnsDefaultForGenericTask() {
            PersonaRegistry registry = PersonaRegistry.newInstance();

            Persona selected = registry.selectFor("do something");

            assertEquals(registry.getDefault(), selected);
        }

        @Test
        @DisplayName("reset() restaura estado inicial")
        void reset_restoresInitialState() {
            PersonaRegistry registry = PersonaRegistry.newInstance();
            registry.register(Persona.builder("custom").build());
            registry.unregister("tester");

            registry.reset();

            assertFalse(registry.contains("custom"));
            assertTrue(registry.contains("tester"));
            assertEquals(7, registry.size());
        }
    }

    @Nested
    @DisplayName("CognitiveEngine")
    class CognitiveEngineTests {
        @Test
        @DisplayName("standard() crea engine con auto-seleccion")
        void standard_createsEngineWithAutoSelection() {
            CognitiveEngine engine = CognitiveEngine.standard();

            assertNotNull(engine);
            assertNotNull(engine.getCurrentPersona());
        }

        @Test
        @DisplayName("withPersona() crea engine con persona fija")
        void withPersona_createsEngineWithFixedPersona() {
            CognitiveEngine engine = CognitiveEngine.withPersona(Personas.SECURITY);

            assertEquals(Personas.SECURITY, engine.getCurrentPersona());
        }

        @Test
        @DisplayName("process() selecciona persona automaticamente")
        void process_selectsPersonaAutomatically() {
            CognitiveEngine engine = CognitiveEngine.standard();
            EvaluationContext ctx = EvaluationContext.empty();

            CognitiveEngine.CognitiveResult result = engine.process(
                "review security vulnerabilities",
                "The code looks secure.",
                ctx
            );

            assertEquals("security", result.persona().id());
        }

        @Test
        @DisplayName("processAs() usa persona especificada")
        void processAs_usesSpecifiedPersona() {
            CognitiveEngine engine = CognitiveEngine.standard();
            EvaluationContext ctx = EvaluationContext.empty();

            CognitiveEngine.CognitiveResult result = engine.processAs(
                Personas.ARCHITECT,
                "The architecture should use microservices.",
                ctx
            );

            assertEquals("architect", result.persona().id());
        }

        @Test
        @DisplayName("generateSystemPrompt() genera prompt para persona actual")
        void generateSystemPrompt_generatesForCurrentPersona() {
            CognitiveEngine engine = CognitiveEngine.withPersona(Personas.DEVELOPER);

            String prompt = engine.generateSystemPrompt();

            assertTrue(prompt.contains("Software Developer"));
        }

        @Test
        @DisplayName("buildFullPrompt() combina system prompt con user prompt")
        void buildFullPrompt_combinesPrompts() {
            CognitiveEngine engine = CognitiveEngine.withPersona(Personas.DEVELOPER);

            String fullPrompt = engine.buildFullPrompt("Fix this bug");

            assertTrue(fullPrompt.contains("Software Developer"));
            assertTrue(fullPrompt.contains("Fix this bug"));
        }

        @Test
        @DisplayName("setCurrentPersonaById() cambia persona")
        void setCurrentPersonaById_changesPersona() {
            CognitiveEngine engine = CognitiveEngine.standard();

            assertTrue(engine.setCurrentPersonaById("security"));
            assertEquals("security", engine.getCurrentPersona().id());
        }

        @Test
        @DisplayName("selectPersonaFor() delega a registry")
        void selectPersonaFor_delegatesToRegistry() {
            CognitiveEngine engine = CognitiveEngine.standard();

            Persona selected = engine.selectPersonaFor("check for SQL injection");

            assertEquals("security", selected.id());
        }
    }

    @Nested
    @DisplayName("CognitiveResult")
    class CognitiveResultTests {
        @Test
        @DisplayName("toSummary() genera resumen correcto")
        void toSummary_generatesCorrectSummary() {
            CognitiveEngine engine = CognitiveEngine.standard();
            EvaluationContext ctx = EvaluationContext.empty();

            var result = engine.processAs(Personas.DEVELOPER, "Valid response here.", ctx);
            String summary = result.toSummary();

            assertTrue(summary.contains("developer"));
            assertTrue(summary.contains("accepted="));
        }

        @Test
        @DisplayName("improvedPrompt() agrega feedback si hay errores")
        void improvedPrompt_addsFeedbackIfErrors() {
            CognitiveEngine engine = CognitiveEngine.standard();
            EvaluationContext ctx = EvaluationContext.empty();

            var result = engine.processAs(Personas.DEVELOPER, "x", ctx);

            if (!result.isAccepted()) {
                String improved = result.improvedPrompt("Original prompt");
                assertTrue(improved.length() > "Original prompt".length());
            }
        }
    }

    @Nested
    @DisplayName("CommunicationStyle")
    class CommunicationStyleTests {
        @Test
        @DisplayName("Todos los estilos tienen descripcion")
        void allStyles_haveDescription() {
            for (Persona.CommunicationStyle style : Persona.CommunicationStyle.values()) {
                assertNotNull(style.getDescription());
                assertFalse(style.getDescription().isBlank());
            }
        }
    }
}
