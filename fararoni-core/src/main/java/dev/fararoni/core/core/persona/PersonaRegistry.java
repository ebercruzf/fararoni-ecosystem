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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PersonaRegistry {
    private static volatile PersonaRegistry instance;
    private static final Object LOCK = new Object();

    private final Map<String, Persona> personas;
    private Persona defaultPersona;

    private PersonaRegistry() {
        this.personas = new ConcurrentHashMap<>();
        this.defaultPersona = Personas.DEVELOPER;

        loadPredefined();
    }

    public static PersonaRegistry getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new PersonaRegistry();
                }
            }
        }
        return instance;
    }

    public static PersonaRegistry newInstance() {
        return new PersonaRegistry();
    }

    public PersonaRegistry register(Persona persona) {
        Objects.requireNonNull(persona, "persona must not be null");
        if (personas.containsKey(persona.id())) {
            throw new IllegalArgumentException("Persona already registered: " + persona.id());
        }
        personas.put(persona.id(), persona);
        return this;
    }

    public PersonaRegistry registerOrUpdate(Persona persona) {
        Objects.requireNonNull(persona, "persona must not be null");
        personas.put(persona.id(), persona);
        return this;
    }

    public boolean unregister(String id) {
        return personas.remove(id) != null;
    }

    private void loadPredefined() {
        for (Persona persona : Personas.all()) {
            personas.put(persona.id(), persona);
        }
    }

    public Optional<Persona> get(String id) {
        return Optional.ofNullable(personas.get(id));
    }

    public Persona getOrDefault(String id) {
        return personas.getOrDefault(id, defaultPersona);
    }

    public boolean contains(String id) {
        return personas.containsKey(id);
    }

    public Collection<Persona> getAll() {
        return Collections.unmodifiableCollection(personas.values());
    }

    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(personas.keySet());
    }

    public int size() {
        return personas.size();
    }

    public List<Persona> findByExpertise(String expertise) {
        return personas.values().stream()
            .filter(p -> p.hasExpertise(expertise))
            .toList();
    }

    public List<Persona> findByPriority(Critic.CriticCategory category) {
        return personas.values().stream()
            .filter(p -> p.prioritizes(category))
            .toList();
    }

    public List<Persona> findByStyle(Persona.CommunicationStyle style) {
        return personas.values().stream()
            .filter(p -> p.style() == style)
            .toList();
    }

    public List<Persona> findBy(Predicate<Persona> predicate) {
        return personas.values().stream()
            .filter(predicate)
            .toList();
    }

    public Persona selectFor(String taskDescription) {
        if (taskDescription == null || taskDescription.isBlank()) {
            return defaultPersona;
        }

        String lower = taskDescription.toLowerCase();

        if (containsAny(lower, "security", "vulnerability", "owasp", "injection",
            "xss", "csrf", "authentication", "authorization", "encrypt")) {
            return personas.getOrDefault("security", defaultPersona);
        }

        if (containsAny(lower, "review", "code review", "pr review", "pull request",
            "feedback", "improve")) {
            return personas.getOrDefault("reviewer", defaultPersona);
        }

        if (containsAny(lower, "architecture", "design", "pattern", "scalab",
            "microservice", "api design", "system design")) {
            return personas.getOrDefault("architect", defaultPersona);
        }

        if (containsAny(lower, "test", "testing", "unit test", "integration test",
            "e2e", "qa", "quality")) {
            return personas.getOrDefault("tester", defaultPersona);
        }

        if (containsAny(lower, "deploy", "ci/cd", "pipeline", "docker",
            "kubernetes", "terraform", "infrastructure", "devops")) {
            return personas.getOrDefault("devops", defaultPersona);
        }

        if (containsAny(lower, "requirement", "analyze", "understand", "clarify",
            "user story", "acceptance")) {
            return personas.getOrDefault("analyst", defaultPersona);
        }

        if (containsAny(lower, "implement", "code", "fix", "bug", "function",
            "class", "method", "refactor")) {
            return personas.getOrDefault("developer", defaultPersona);
        }

        return defaultPersona;
    }

    public Persona selectForCategory(Critic.CriticCategory category) {
        return personas.values().stream()
            .filter(p -> p.prioritizes(category))
            .findFirst()
            .orElse(defaultPersona);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public Persona getDefault() {
        return defaultPersona;
    }

    public void setDefault(Persona persona) {
        this.defaultPersona = Objects.requireNonNull(persona, "persona must not be null");
    }

    public void setDefaultById(String id) {
        Persona persona = personas.get(id);
        if (persona == null) {
            throw new IllegalArgumentException("Persona not found: " + id);
        }
        this.defaultPersona = persona;
    }

    public void reset() {
        personas.clear();
        loadPredefined();
        defaultPersona = Personas.DEVELOPER;
    }
}
