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
package dev.fararoni.core.core.safety.audit;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.mission.events.ArtifactCertifiedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class NotaryAuditListener {
    private static final Logger LOG = Logger.getLogger(NotaryAuditListener.class.getName());

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final Path ledgerPath;

    private final SovereignEventBus bus;

    private volatile boolean running = false;

    public NotaryAuditListener(SovereignEventBus bus, Path ledgerPath) {
        this.bus = bus;
        this.ledgerPath = ledgerPath;
    }

    public NotaryAuditListener(SovereignEventBus bus) {
        this(bus, resolveDefaultLedgerPath());
    }

    public void start() {
        if (running) {
            LOG.warning("[NOTARY] Ya está corriendo, ignorando start()");
            return;
        }

        try {
            Files.createDirectories(ledgerPath.getParent());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "[NOTARY] No se pudo crear directorio de audit", e);
            return;
        }

        bus.subscribe(
            ArtifactCertifiedEvent.TOPIC,
            ArtifactCertifiedEvent.class,
            this::onArtifactCertified
        );

        running = true;
        LOG.info("[NOTARY] Listener iniciado. Ledger: " + ledgerPath);
        System.out.println("[NOTARY] Audit Ledger activo: " + ledgerPath.getFileName());
    }

    private void onArtifactCertified(SovereignEnvelope<ArtifactCertifiedEvent> envelope) {
        if (!running) {
            return;
        }

        ArtifactCertifiedEvent event = envelope.payload();

        LOG.fine(() -> "[NOTARY] Recibido: " + event);

        try {
            writeToLedger(event);
        } catch (Exception e) {
            LOG.warning("[NOTARY] Error escribiendo al ledger: " + e.getMessage());
        }
    }

    private void writeToLedger(ArtifactCertifiedEvent event) throws IOException {
        String entry = String.format("%s,%s,%s,%s,%s,%s,%d%n",
            ISO_FORMAT.format(event.timestamp()),
            event.correlationId(),
            event.missionId() != null ? event.missionId() : "no-mission",
            event.agentId() != null ? event.agentId() : "unknown",
            event.artifactPath().toString(),
            event.sha256Hash(),
            event.bytesWritten()
        );

        Files.writeString(
            ledgerPath,
            entry,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );

        LOG.info(() -> "[NOTARY] ✓ Registrado: " + event.artifactPath().getFileName());
    }

    public void stop() {
        running = false;
        LOG.info("[NOTARY] Listener detenido");
    }

    public boolean isRunning() {
        return running;
    }

    public Path getLedgerPath() {
        return ledgerPath;
    }

    private static Path resolveDefaultLedgerPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".fararoni", "audit", "artifacts.ledger");
    }
}
