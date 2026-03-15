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

import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.persistence.JournalManager;
import dev.fararoni.core.core.swarm.HiveMind;
import dev.fararoni.core.core.swarm.base.SwarmAgent;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;
import dev.fararoni.core.core.swarm.infra.MessageBus;
import dev.fararoni.core.server.SessionManager;
import dev.fararoni.core.core.persona.Persona;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class ChaosTest {
    @TempDir
    Path tempDir;

    private JournalManager journalManager;

    @BeforeEach
    void setUp() {
        journalManager = new JournalManager(tempDir);
    }

    @AfterEach
    void tearDown() {
        journalManager = null;
    }

    @Test
    @DisplayName("🔥 Test de Resurrección: Recuperación tras Crash y validación de Idempotencia")
    void testResurrectionAndIdempotency() throws InterruptedException {
        String missionId = "M-CRASH-TEST-001";

        MessageBus originalBus = new MessageBus(journalManager);

        SwarmMessage msg1 = SwarmMessage.builder()
            .from("PM")
            .to("DEV")
            .type("BLUEPRINT")
            .content("Spec v1")
            .build();

        SwarmMessage msg2 = SwarmMessage.builder()
            .from("DEV")
            .to("QA")
            .type("CODE_DRAFT")
            .content("Class A")
            .build();

        journalManager.append(missionId, msg1);
        journalManager.append(missionId, msg2);

        System.out.println("💀 [CHAOS] Simulando muerte súbita del proceso...");
        originalBus = null;

        System.out.println("✨ [CHAOS] Iniciando secuencia de arranque en nuevo proceso...");
        JournalManager newJournal = new JournalManager(tempDir);

        Thread.sleep(200);

        List<SwarmMessage> history = newJournal.replay(missionId);

        assertEquals(2, history.size(), "Debe recuperar exactamente 2 mensajes de la tumba");

        SwarmMessage recoveredMsg1 = history.get(0);
        assertEquals("BLUEPRINT", recoveredMsg1.type(), "El contenido debe estar intacto");

        assertTrue(recoveredMsg1.metadata().containsKey("IS_REPLAY"),
            "CRÍTICO: El mensaje recuperado DEBE estar marcado como IS_REPLAY para evitar 'Déjà Vu'");

        System.out.println("✅ Test de Resurrección APROBADO.");
    }

    @Test
    @DisplayName("🔒 Test de Aislamiento: Garantía de Silos Multi-Tenant")
    void testTenantIsolation() throws InterruptedException {
        HyperNativeKernel mockKernel = null;
        SessionManager sessionManager = new SessionManager(mockKernel, journalManager);

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
            scope.fork(() -> {
                var siloA = sessionManager.getOrCreateSession("USER_A");
                siloA.bus().send(SwarmMessage.builder()
                    .from("A")
                    .to("A")
                    .type("TEST")
                    .content("Secret A")
                    .build());
                return siloA;
            });

            scope.fork(() -> {
                var siloB = sessionManager.getOrCreateSession("USER_B");
                siloB.bus().send(SwarmMessage.builder()
                    .from("B")
                    .to("B")
                    .type("TEST")
                    .content("Secret B")
                    .build());
                return siloB;
            });

            scope.join();
        } catch (Exception e) {
            fail("Error en concurrencia estructurada: " + e.getMessage());
        }

        var siloA = sessionManager.getOrCreateSession("USER_A");
        var siloB = sessionManager.getOrCreateSession("USER_B");

        assertNotSame(siloA, siloB, "Los silos deben ser objetos diferentes");
        assertNotSame(siloA.bus(), siloB.bus(), "CRÍTICO: Los buses de mensajes deben estar aislados en memoria");
        assertNotEquals(siloA.userId(), siloB.userId());

        System.out.println("✅ Test de Aislamiento APROBADO.");

        sessionManager.shutdown();
    }

    @Test
    @DisplayName("🛡️ Test de Integridad: Detección de Corrupción (Bit Rot)")
    void testCorruptionDetection() throws IOException {
        String missionId = "M-CORRUPT-TEST";
        SwarmMessage validMsg = SwarmMessage.builder()
            .from("SYS")
            .to("LOG")
            .type("INFO")
            .content("Payload Limpio")
            .build();

        journalManager.append(missionId, validMsg);

        Path journalFile = tempDir.resolve("mission_" + missionId + ".jsonl");
        assertTrue(Files.exists(journalFile), "El archivo de journal debe existir");

        List<String> lines = Files.readAllLines(journalFile);
        assertFalse(lines.isEmpty(), "Debe haber al menos una línea en el journal");

        String validLine = lines.get(0);

        String[] parts = validLine.split("\\|", 4);
        assertEquals(4, parts.length, "Formato de línea debe tener 4 partes separadas por |");

        String corruptedJson = parts[3].replace("Limpio", "Sucio");
        String corruptedLine = parts[0] + "|" + parts[1] + "|" + parts[2] + "|" + corruptedJson;

        Files.writeString(journalFile, corruptedLine);
        System.out.println("🔨 [CHAOS] Archivo saboteado manualmente. CRC original vs Payload alterado.");

        List<SwarmMessage> recovered = journalManager.replay(missionId);

        assertTrue(recovered.isEmpty(),
            "El sistema debió descartar la línea corrupta silenciosamente debido al fallo de Checksum CRC32");

        System.out.println("✅ Test de Integridad APROBADO.");
    }

    @Test
    @DisplayName("⚡ Test de Fail-Fast: Colapso Controlado del Enjambre")
    void testHiveMindFailFast() {
        MessageBus bus = new MessageBus(journalManager);

        HiveMind testHive = new HiveMind(null, bus) {
            @Override
            protected List<SwarmAgent> createAgents() {
                SwarmAgent suicideAgent = new SwarmAgent("KAMIKAZE", Persona.createDeveloper()) {
                    @Override
                    protected void processMessage(SwarmMessage msg) {
                    }

                    @Override
                    public Void call() throws Exception {
                        throw new RuntimeException("💥 Simulacro de Fallo Crítico en Agente");
                    }
                };
                return List.of(suicideAgent);
            }
        };

        System.out.println("⏱️ [CHAOS] Iniciando misión con agente defectuoso...");
        long start = System.currentTimeMillis();

        var result = testHive.executeMission("Doomed Mission");

        long duration = System.currentTimeMillis() - start;
        System.out.println("⏱️ [CHAOS] Tiempo de colapso: " + duration + "ms");

        assertEquals(HiveMind.MissionStatus.FAILED, result.status(), "La misión debe terminar en FAILED");
        assertNotNull(result.errorMessage(), "Debe haber un mensaje de error");
        assertTrue(result.errorMessage().contains("Simulacro de Fallo") ||
                   result.errorMessage().contains("Fallo Crítico"),
            "El error debe propagarse hacia arriba");
        assertTrue(duration < 2000, "El sistema debe ser Fail-Fast (<2000ms), no esperar timeouts de hilos zombies");

        System.out.println("✅ Test de Fail-Fast APROBADO.");
    }
}
