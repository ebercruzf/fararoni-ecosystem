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
package dev.fararoni.core.core.resilience;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.agents.AbstractSwarmAgent;
import dev.fararoni.core.core.bus.InMemorySovereignBus;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class GalvanicAgent extends AbstractSwarmAgent {

    private static final Logger LOG = Logger.getLogger(GalvanicAgent.class.getName());

    private static final String DLQ_TOPIC = InMemorySovereignBus.DLQ_TOPIC;

    private static final int MAX_RESURRECTIONS = 3;

    private volatile boolean running = false;

    public GalvanicAgent(SovereignEventBus bus) {
        super("GALVANIC", bus);
    }

    public void start() {
        if (running) {
            LOG.warning("[GALVANIC] Ya está corriendo");
            return;
        }

        running = true;

        logAction("Iniciando vigilancia en " + DLQ_TOPIC);
        LOG.info("[GALVANIC] Iniciando vigilancia en " + DLQ_TOPIC);

        bus.subscribe(DLQ_TOPIC, PoisonPill.class, this::performRitual);

        logIdle("Vigilando DLQ...");
    }

    public void stop() {
        running = false;
        logAction("Detenido");
        LOG.info("[GALVANIC] Detenido");
    }

    public boolean isRunning() {
        return running;
    }

    @SuppressWarnings("unchecked")
    private void performRitual(SovereignEnvelope<PoisonPill> dlqEnvelope) {
        PoisonPill corpse = dlqEnvelope.payload();
        SovereignEnvelope<?> originalSoul = corpse.originalEnvelope();

        if (originalSoul == null) {
            LOG.warning("[GALVANIC] Píldora vacía recibida. Ignorando.");
            return;
        }

        String logPrefix = String.format("[GALVANIC] MsgID: %s | Topic: %s | ",
                originalSoul.id(), corpse.originalTopic());

        logThinking("Analizando cadáver: " + originalSoul.id());

        if (corpse.isTransientError() && originalSoul.retryCount() < MAX_RESURRECTIONS) {
            int attempt = originalSoul.retryCount() + 1;

            logAction("Resucitando mensaje " + originalSoul.id() + " (Intento " + attempt + "/" + MAX_RESURRECTIONS + ")");
            LOG.info(logPrefix + "Intentando resurrección (" + attempt + "/" + MAX_RESURRECTIONS + ")");

            try {
                SovereignEnvelope<?> resurrected = originalSoul.incrementRetry();

                bus.publish(corpse.originalTopic(), (SovereignEnvelope) resurrected).join();

                logSuccess("Resurrección completada para " + originalSoul.id());
                LOG.fine(logPrefix + "Resurrección exitosa. Mensaje reencolado.");

            } catch (Exception ex) {
                logError("Fallo crítico en resurrección: " + ex.getMessage());
                LOG.severe(logPrefix + "Fallo crítico en resurrección: " + ex.getMessage());
            }

        } else {
            logPermanentDeath(logPrefix, corpse, originalSoul);
        }

        logIdle("Vigilando DLQ...");
    }

    private void logPermanentDeath(String prefix, PoisonPill corpse, SovereignEnvelope<?> soul) {
        String reason = corpse.isTransientError()
            ? "Agotó reintentos (" + soul.retryCount() + "/" + MAX_RESURRECTIONS + ")"
            : "Error permanente";

        logError("Muerte definitiva para " + soul.id() + ". Causa: " + reason);

        LOG.severe(prefix + "MUERTE DEFINITIVA. Causa: " + reason);
        LOG.severe(prefix + "Error: " + corpse.failureReason());
        LOG.fine(prefix + "Stacktrace:\n" + corpse.stackTrace());

        notifyHumanOperators(corpse);
    }

    private void notifyHumanOperators(PoisonPill corpse) {
        LOG.warning("[GALVANIC-ALERT] Mensaje irrecuperable: " + corpse.toShortString());
    }
}
