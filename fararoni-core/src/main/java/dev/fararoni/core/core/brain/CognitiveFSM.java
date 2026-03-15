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
package dev.fararoni.core.core.brain;

import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CognitiveFSM {
    private static final Logger LOG = Logger.getLogger(CognitiveFSM.class.getName());

    public enum State {
        IDLE("Esperando"),

        GATHERING("Recolectando"),

        PLANNING("Planificando"),

        CODING("Codificando"),

        VERIFYING("Verificando"),

        COMPLETED("Completado"),

        FAILED("Fallido");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean isTerminal() {
            return this == COMPLETED || this == FAILED;
        }
    }

    private static final java.util.Map<State, Set<State>> VALID_TRANSITIONS = java.util.Map.of(
        State.IDLE, EnumSet.of(State.GATHERING),
        State.GATHERING, EnumSet.of(State.PLANNING, State.FAILED),
        State.PLANNING, EnumSet.of(State.CODING, State.GATHERING, State.FAILED),
        State.CODING, EnumSet.of(State.VERIFYING, State.FAILED),
        State.VERIFYING, EnumSet.of(State.COMPLETED, State.GATHERING, State.FAILED),
        State.COMPLETED, EnumSet.noneOf(State.class),
        State.FAILED, EnumSet.noneOf(State.class)
    );

    private State currentState = State.IDLE;

    private int attemptCount = 0;

    private final int maxAttempts;

    private final java.util.List<State> history = new java.util.ArrayList<>();

    public CognitiveFSM(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
        this.maxAttempts = maxAttempts;
        this.history.add(State.IDLE);
    }

    public CognitiveFSM() {
        this(3);
    }

    public void transition(State newState) {
        if (currentState.isTerminal()) {
            LOG.warning("[BRAIN] Intento de transicion desde estado terminal: " +
                       currentState + " -> " + newState);
            return;
        }

        Set<State> allowed = VALID_TRANSITIONS.get(currentState);
        if (allowed == null || !allowed.contains(newState)) {
            throw new IllegalStateException(
                "Transicion no valida: " + currentState + " -> " + newState +
                ". Transiciones permitidas: " + allowed
            );
        }

        LOG.info("[BRAIN] Transicion: " + currentState.getLabel() +
                " -> " + newState.getLabel());

        this.currentState = newState;
        this.history.add(newState);
    }

    public void recordAttempt() {
        this.attemptCount++;

        LOG.info("[BRAIN] Intento " + attemptCount + "/" + maxAttempts);

        if (this.attemptCount >= maxAttempts) {
            LOG.severe("[BRAIN] Limite de intentos cognitivos excedido (" +
                      maxAttempts + "). Declarando FAILED.");
            this.currentState = State.FAILED;
            this.history.add(State.FAILED);
        }
    }

    public boolean canContinue() {
        return !currentState.isTerminal();
    }

    public State getCurrentState() {
        return currentState;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public java.util.List<State> getHistory() {
        return java.util.Collections.unmodifiableList(history);
    }

    public void reset() {
        this.currentState = State.IDLE;
        this.attemptCount = 0;
        this.history.clear();
        this.history.add(State.IDLE);
        LOG.info("[BRAIN] FSM reiniciada a IDLE");
    }

    public String getStatusReport() {
        return String.format(
            "CognitiveFSM Status:\n" +
            "  - Estado: %s (%s)\n" +
            "  - Intentos: %d/%d\n" +
            "  - Puede continuar: %s\n" +
            "  - Historial: %s",
            currentState,
            currentState.getLabel(),
            attemptCount,
            maxAttempts,
            canContinue() ? "SI" : "NO",
            history.stream()
                   .map(State::name)
                   .collect(java.util.stream.Collectors.joining(" -> "))
        );
    }
}
