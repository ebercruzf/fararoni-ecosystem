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
package dev.fararoni.core.core.reflexion.memory;

import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class AttemptMemory {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private static final int MAX_ATTEMPTS_PER_EXERCISE = 10;

    private final Map<String, List<RetryAttempt>> memory;

    private final Duration ttl;

    public AttemptMemory() {
        this(DEFAULT_TTL);
    }

    public AttemptMemory(Duration ttl) {
        this.memory = new ConcurrentHashMap<>();
        this.ttl = ttl != null ? ttl : DEFAULT_TTL;
    }

    public void recordAttempt(String exerciseId, RetryAttempt attempt) {
        Objects.requireNonNull(exerciseId, "exerciseId no puede ser null");
        Objects.requireNonNull(attempt, "attempt no puede ser null");

        memory.compute(exerciseId, (key, attempts) -> {
            if (attempts == null) {
                attempts = new ArrayList<>();
            }

            cleanExpired(attempts);

            attempts.add(attempt);

            while (attempts.size() > MAX_ATTEMPTS_PER_EXERCISE) {
                attempts.remove(0);
            }

            return attempts;
        });
    }

    public void clearExercise(String exerciseId) {
        memory.remove(exerciseId);
    }

    public void clearAll() {
        memory.clear();
    }

    public List<RetryAttempt> getAttempts(String exerciseId) {
        List<RetryAttempt> attempts = memory.get(exerciseId);
        if (attempts == null) {
            return List.of();
        }

        return attempts.stream()
            .filter(a -> !isExpired(a))
            .toList();
    }

    public Optional<RetryAttempt> getLastAttempt(String exerciseId) {
        List<RetryAttempt> attempts = getAttempts(exerciseId);
        if (attempts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(attempts.get(attempts.size() - 1));
    }

    public int getAttemptCount(String exerciseId) {
        return getAttempts(exerciseId).size();
    }

    public boolean hasAttempts(String exerciseId) {
        return !getAttempts(exerciseId).isEmpty();
    }

    public boolean isRepeatingPattern(String exerciseId, FailurePattern pattern) {
        List<RetryAttempt> attempts = getAttempts(exerciseId);
        if (attempts.size() < 2) {
            return false;
        }

        RetryAttempt last = attempts.get(attempts.size() - 1);
        RetryAttempt prev = attempts.get(attempts.size() - 2);

        return last.hasPattern(pattern) && prev.hasPattern(pattern);
    }

    public boolean isRepeatingCode(String exerciseId) {
        List<RetryAttempt> attempts = getAttempts(exerciseId);
        if (attempts.size() < 2) {
            return false;
        }

        RetryAttempt last = attempts.get(attempts.size() - 1);
        RetryAttempt prev = attempts.get(attempts.size() - 2);

        return last.hasSameCode(prev);
    }

    public boolean isRepeatingSameFailingTests(String exerciseId) {
        List<RetryAttempt> attempts = getAttempts(exerciseId);
        if (attempts.size() < 2) {
            return false;
        }

        RetryAttempt last = attempts.get(attempts.size() - 1);
        RetryAttempt prev = attempts.get(attempts.size() - 2);

        return last.sharesSameFailingTests(prev);
    }

    public Set<FailurePattern> getRepeatedPatterns(String exerciseId, int n) {
        List<RetryAttempt> attempts = getAttempts(exerciseId);
        if (attempts.size() < 2) {
            return Set.of();
        }

        int start = Math.max(0, attempts.size() - n);
        List<RetryAttempt> recent = attempts.subList(start, attempts.size());

        Map<FailurePattern, Long> patternCounts = recent.stream()
            .flatMap(a -> a.patterns().stream())
            .collect(Collectors.groupingBy(p -> p, Collectors.counting()));

        return patternCounts.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    public Optional<String> getSuggestionToAvoidRepeat(String exerciseId) {
        List<RetryAttempt> attempts = getAttempts(exerciseId);
        if (attempts.size() < 2) {
            return Optional.empty();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Advertencia: Errores Repetidos Detectados\n\n");

        boolean hasRepetition = false;

        if (isRepeatingCode(exerciseId)) {
            sb.append("**ALERTA:** El codigo generado es IDENTICO al intento anterior.\n");
            sb.append("- Debes cambiar tu enfoque, no repetir el mismo codigo.\n\n");
            hasRepetition = true;
        }

        Set<FailurePattern> repeated = getRepeatedPatterns(exerciseId, 3);
        if (!repeated.isEmpty()) {
            sb.append("**Patrones que se repiten:**\n");
            for (FailurePattern pattern : repeated) {
                sb.append(String.format("- **%s**: %s\n", pattern.name(), pattern.getSuggestion()));
            }
            sb.append("\n");
            hasRepetition = true;
        }

        if (isRepeatingSameFailingTests(exerciseId)) {
            RetryAttempt last = attempts.get(attempts.size() - 1);
            sb.append("**Tests que siguen fallando:**\n");
            for (String testName : last.failingTestNames()) {
                sb.append(String.format("- `%s`\n", testName));
            }
            sb.append("\n");
            sb.append("Enfocate en estos tests especificos antes de continuar.\n");
            hasRepetition = true;
        }

        if (!hasRepetition) {
            return Optional.empty();
        }

        sb.append("\n---\n");
        sb.append("**Recomendacion:** Cambia tu estrategia de solucion. ");
        sb.append("El enfoque actual no esta funcionando.\n");

        return Optional.of(sb.toString());
    }

    public String getHistorySummary(String exerciseId) {
        List<RetryAttempt> attempts = getAttempts(exerciseId);
        if (attempts.isEmpty()) {
            return "Sin intentos registrados para " + exerciseId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## Historico de %s (%d intentos)\n\n", exerciseId, attempts.size()));

        for (RetryAttempt attempt : attempts) {
            sb.append(attempt.toSummary()).append("\n");
        }

        return sb.toString();
    }

    private boolean isExpired(RetryAttempt attempt) {
        return Duration.between(attempt.timestamp(), Instant.now()).compareTo(ttl) > 0;
    }

    private void cleanExpired(List<RetryAttempt> attempts) {
        attempts.removeIf(this::isExpired);
    }
}
