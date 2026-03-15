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
package dev.fararoni.core.combat.security;

import dev.fararoni.core.core.gateway.security.ChannelTrustLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SEC-01: Inyección de Comandos")
class CommandInjectionTest {
    @Test
    @DisplayName("UNTRUSTED_EXTERNAL rechaza comandos administrativos")
    void untrustedChannel_rejectsAdminCommands() {
        ChannelTrustLevel untrusted = ChannelTrustLevel.UNTRUSTED_EXTERNAL;

        assertFalse(untrusted.canExecuteCommand("/shutdown"),
            "/shutdown debe ser rechazado en canal no confiable");
        assertFalse(untrusted.canExecuteCommand("/system shutdown"),
            "/system shutdown debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("/config set admin.password=123"),
            "/config debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("/debug enable"),
            "/debug debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("/logs tail"),
            "/logs debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("/metrics expose"),
            "/metrics debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("/security disable"),
            "/security debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("/reconfig all"),
            "/reconfig debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("/export database"),
            "/export debe ser rechazado");
    }

    @Test
    @DisplayName("UNTRUSTED_EXTERNAL permite solo comandos básicos")
    void untrustedChannel_allowsOnlyBasicCommands() {
        ChannelTrustLevel untrusted = ChannelTrustLevel.UNTRUSTED_EXTERNAL;

        assertTrue(untrusted.canExecuteCommand("/task create nueva tarea"),
            "/task debe ser permitido");
        assertTrue(untrusted.canExecuteCommand("/ask question"),
            "/ask debe ser permitido");
        assertTrue(untrusted.canExecuteCommand("/status"),
            "/status debe ser permitido");
        assertTrue(untrusted.canExecuteCommand("/help"),
            "/help debe ser permitido");
    }

    @Test
    @DisplayName("SECURE_ENCRYPTED permite todos los comandos")
    void secureChannel_allowsAllCommands() {
        ChannelTrustLevel secure = ChannelTrustLevel.SECURE_ENCRYPTED;

        assertTrue(secure.canExecuteCommand("/shutdown"),
            "SECURE debe permitir /shutdown");
        assertTrue(secure.canExecuteCommand("/system reboot"),
            "SECURE debe permitir /system");
        assertTrue(secure.canExecuteCommand("/config set anything"),
            "SECURE debe permitir /config");
        assertTrue(secure.canExecuteCommand("/nuclear launch"),
            "SECURE debe permitir cualquier comando con wildcard");
    }

    @Test
    @DisplayName("TRUSTED_DEVICE permite comandos de desarrollo pero no todos")
    void trustedDevice_allowsDevCommands() {
        ChannelTrustLevel trusted = ChannelTrustLevel.TRUSTED_DEVICE;

        assertTrue(trusted.canExecuteCommand("/task"),
            "TRUSTED debe permitir /task");
        assertTrue(trusted.canExecuteCommand("/ask"),
            "TRUSTED debe permitir /ask");
        assertTrue(trusted.canExecuteCommand("/wizard"),
            "TRUSTED debe permitir /wizard");
        assertTrue(trusted.canExecuteCommand("/config"),
            "TRUSTED debe permitir /config");
        assertTrue(trusted.canExecuteCommand("/debug"),
            "TRUSTED debe permitir /debug");
        assertTrue(trusted.canExecuteCommand("/logs"),
            "TRUSTED debe permitir /logs");
        assertTrue(trusted.canExecuteCommand("/metrics"),
            "TRUSTED debe permitir /metrics");
        assertTrue(trusted.canExecuteCommand("/swarm"),
            "TRUSTED debe permitir /swarm");

        assertFalse(trusted.canExecuteCommand("/shutdown"),
            "TRUSTED NO debe permitir /shutdown");
        assertFalse(trusted.canExecuteCommand("/system"),
            "TRUSTED NO debe permitir /system");
    }

    @ParameterizedTest
    @DisplayName("Comandos con argumentos son validados por comando base")
    @ValueSource(strings = {
        "/task create with multiple arguments",
        "/status --verbose --json",
        "/help subcommand",
        "/ask   multiple   spaces   question"
    })
    void commandsWithArguments_validatedByBaseCommand(String fullCommand) {
        ChannelTrustLevel untrusted = ChannelTrustLevel.UNTRUSTED_EXTERNAL;

        assertTrue(untrusted.canExecuteCommand(fullCommand),
            "Comando con argumentos debe validar por base: " + fullCommand);
    }

    @ParameterizedTest
    @DisplayName("Intentos de bypass con espacios/caracteres son rechazados")
    @ValueSource(strings = {
        "  /shutdown  ",
        "/SHUTDOWN",
        "/ShUtDoWn",
        "/shutdown\nmalicious",
        "/shutdown; rm -rf /",
        "/shutdown && evil",
        "/shutdown | grep"
    })
    void bypassAttempts_areRejected(String maliciousCommand) {
        ChannelTrustLevel untrusted = ChannelTrustLevel.UNTRUSTED_EXTERNAL;

        assertFalse(untrusted.canExecuteCommand(maliciousCommand),
            "Intento de bypass debe ser rechazado: " + maliciousCommand);
    }

    @Test
    @DisplayName("Comando null o vacío es rechazado")
    void nullOrEmptyCommand_isRejected() {
        ChannelTrustLevel untrusted = ChannelTrustLevel.UNTRUSTED_EXTERNAL;

        assertFalse(untrusted.canExecuteCommand(null),
            "Comando null debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand(""),
            "Comando vacío debe ser rechazado");
        assertFalse(untrusted.canExecuteCommand("   "),
            "Comando con solo espacios debe ser rechazado");
    }

    @Test
    @DisplayName("Trust score refleja nivel de confianza")
    void trustScore_reflectsTrustLevel() {
        assertEquals(100, ChannelTrustLevel.SECURE_ENCRYPTED.getTrustScore(),
            "SECURE debe tener trust score 100");

        assertEquals(75, ChannelTrustLevel.TRUSTED_DEVICE.getTrustScore(),
            "TRUSTED debe tener trust score 75");

        assertEquals(25, ChannelTrustLevel.UNTRUSTED_EXTERNAL.getTrustScore(),
            "UNTRUSTED debe tener trust score 25");
    }

    @Test
    @DisplayName("isAtLeast verifica jerarquía de confianza correctamente")
    void isAtLeast_verifiesHierarchy() {
        ChannelTrustLevel secure = ChannelTrustLevel.SECURE_ENCRYPTED;
        ChannelTrustLevel trusted = ChannelTrustLevel.TRUSTED_DEVICE;
        ChannelTrustLevel untrusted = ChannelTrustLevel.UNTRUSTED_EXTERNAL;

        assertTrue(secure.isAtLeast(secure));
        assertTrue(secure.isAtLeast(trusted));
        assertTrue(secure.isAtLeast(untrusted));

        assertFalse(trusted.isAtLeast(secure),
            "TRUSTED no debe ser >= SECURE");
        assertTrue(trusted.isAtLeast(trusted));
        assertTrue(trusted.isAtLeast(untrusted));

        assertFalse(untrusted.isAtLeast(secure));
        assertFalse(untrusted.isAtLeast(trusted));
        assertTrue(untrusted.isAtLeast(untrusted));
    }

    @Test
    @DisplayName("Whitelist de UNTRUSTED es mínima y explícita")
    void untrustedWhitelist_isMinimalAndExplicit() {
        var allowed = ChannelTrustLevel.UNTRUSTED_EXTERNAL.getAllowedCommands();

        assertEquals(4, allowed.size(),
            "UNTRUSTED debe tener exactamente 4 comandos permitidos");

        assertTrue(allowed.contains("/task"));
        assertTrue(allowed.contains("/ask"));
        assertTrue(allowed.contains("/status"));
        assertTrue(allowed.contains("/help"));

        assertFalse(allowed.contains("*"),
            "UNTRUSTED NO debe tener wildcard");
    }
}
