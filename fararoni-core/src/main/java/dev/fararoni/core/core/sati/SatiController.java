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
package dev.fararoni.core.core.sati;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;

/**
 * @since 1.0.0
 */
public class SatiController {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SatiController.class);

    private static final String PANIC_TOPIC = "fararoni.sati.control.panic";
    private static final String COMMAND_HARD_REBOOT = "HARD_REBOOT";
    private static final String COMMAND_SOFT_RESTART = "SOFT_RESTART";
    private static final String COMMAND_STATUS_REPORT = "STATUS_REPORT";

    private final SovereignEventBus bus;

    public SatiController(SovereignEventBus bus) {
        this.bus = bus;
        LOG.info("[SATI-CTRL] Controlador inicializado. Topic de panico: '{}'", PANIC_TOPIC);
    }

    public void broadcastPanic() {
        LOG.warn("[SATI-CTRL] [PANIC] Enviando senal de HARD_REBOOT a todos los Sidecars...");
        System.out.println();
        System.out.println("[!] ENVIANDO SENAL DE PANICO A TODOS LOS SIDECARS...");

        bus.publish(PANIC_TOPIC, SovereignEnvelope.create("KERNEL", COMMAND_HARD_REBOOT));

        System.out.println("[OK] Orden de reinicio masivo distribuida.");
        LOG.info("[SATI-CTRL] [PANIC] Orden HARD_REBOOT enviada exitosamente");
    }

    public void broadcastSoftRestart() {
        LOG.info("[SATI-CTRL] Enviando senal de SOFT_RESTART a todos los Sidecars...");
        System.out.println();
        System.out.println("[!] ENVIANDO SENAL DE REINICIO SUAVE...");

        bus.publish(PANIC_TOPIC, SovereignEnvelope.create("KERNEL", COMMAND_SOFT_RESTART));

        System.out.println("[OK] Orden de reinicio suave distribuida.");
        LOG.info("[SATI-CTRL] Orden SOFT_RESTART enviada exitosamente");
    }

    public void requestStatusReport() {
        LOG.info("[SATI-CTRL] Solicitando reporte de estado a todos los Sidecars...");
        bus.publish(PANIC_TOPIC, SovereignEnvelope.create("KERNEL", COMMAND_STATUS_REPORT));
        LOG.info("[SATI-CTRL] Solicitud de reporte enviada");
    }

    public void restartSidecar(String sidecarId) {
        String targetTopic = PANIC_TOPIC + "." + sidecarId;
        LOG.info("[SATI-CTRL] Enviando reinicio a Sidecar: {}", sidecarId);
        bus.publish(targetTopic, SovereignEnvelope.create("KERNEL", COMMAND_HARD_REBOOT));
    }

    public void broadcastCommand(String command) {
        LOG.info("[SATI-CTRL] Enviando comando personalizado: {}", command);
        bus.publish(PANIC_TOPIC, SovereignEnvelope.create("KERNEL", command));
    }

    public boolean processCliCommand(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String cmd = input.trim().toLowerCase();

        if ("panic".equals(cmd) || "hard-reboot".equals(cmd)) {
            broadcastPanic();
            return true;
        }

        if ("soft-restart".equals(cmd)) {
            broadcastSoftRestart();
            return true;
        }

        if ("status-report".equals(cmd)) {
            requestStatusReport();
            return true;
        }

        if (cmd.startsWith("restart ")) {
            String sidecarId = cmd.substring(8).trim();
            if (!sidecarId.isEmpty()) {
                restartSidecar(sidecarId);
                return true;
            }
        }

        return false;
    }

    public void printHelp() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  SATI CONTROLLER - Comandos");
        System.out.println("========================================");
        System.out.println("  panic          - Hard reboot de TODOS los Sidecars");
        System.out.println("  soft-restart   - Reinicio suave (espera peticiones)");
        System.out.println("  status-report  - Solicita heartbeat inmediato");
        System.out.println("  restart <id>   - Reinicia un Sidecar especifico");
        System.out.println("========================================");
    }
}
