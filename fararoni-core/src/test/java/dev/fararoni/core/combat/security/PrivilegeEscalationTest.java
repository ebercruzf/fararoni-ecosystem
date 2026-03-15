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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SEC-02: Escalación de Privilegios")
class PrivilegeEscalationTest {
    @Test
    @DisplayName("ChannelTrustLevel es un enum inmutable")
    void channelTrustLevel_isImmutableEnum() {
        assertTrue(ChannelTrustLevel.class.isEnum(),
            "ChannelTrustLevel debe ser un enum para garantizar inmutabilidad");

        ChannelTrustLevel[] values = ChannelTrustLevel.values();
        assertEquals(3, values.length,
            "Deben existir exactamente 3 niveles de confianza");

        assertNotNull(ChannelTrustLevel.valueOf("SECURE_ENCRYPTED"));
        assertNotNull(ChannelTrustLevel.valueOf("TRUSTED_DEVICE"));
        assertNotNull(ChannelTrustLevel.valueOf("UNTRUSTED_EXTERNAL"));
    }

    @Test
    @DisplayName("Trust score no puede ser modificado en runtime")
    void trustScore_cannotBeModifiedAtRuntime() {
        int originalScore = ChannelTrustLevel.UNTRUSTED_EXTERNAL.getTrustScore();

        int scoreAfterMultipleCalls = ChannelTrustLevel.UNTRUSTED_EXTERNAL.getTrustScore();

        assertEquals(originalScore, scoreAfterMultipleCalls,
            "Trust score debe ser consistente entre llamadas");

        assertEquals(100, ChannelTrustLevel.SECURE_ENCRYPTED.getTrustScore());
        assertEquals(75, ChannelTrustLevel.TRUSTED_DEVICE.getTrustScore());
        assertEquals(25, ChannelTrustLevel.UNTRUSTED_EXTERNAL.getTrustScore());
    }

    @Test
    @DisplayName("AllowedCommands retorna copia inmutable")
    void allowedCommands_returnsImmutableCopy() {
        Set<String> commands = ChannelTrustLevel.UNTRUSTED_EXTERNAL.getAllowedCommands();

        assertThrows(UnsupportedOperationException.class, () -> {
            commands.add("/malicious");
        }, "AllowedCommands debe retornar un Set inmutable");

        assertThrows(UnsupportedOperationException.class, () -> {
            commands.remove("/task");
        }, "No debe permitir remover comandos del Set");

        assertThrows(UnsupportedOperationException.class, () -> {
            commands.clear();
        }, "No debe permitir limpiar el Set");
    }

    @Test
    @DisplayName("No se puede suplantar nivel SECURE desde UNTRUSTED")
    void cannotSpoof_secureLevel_fromUntrusted() {
        ChannelTrustLevel untrusted = ChannelTrustLevel.UNTRUSTED_EXTERNAL;
        ChannelTrustLevel secure = ChannelTrustLevel.SECURE_ENCRYPTED;

        assertFalse(untrusted.isAtLeast(secure),
            "UNTRUSTED nunca debe pasar como SECURE");

        assertTrue(secure.canExecuteCommand("/shutdown"),
            "SECURE real puede ejecutar /shutdown");
        assertFalse(untrusted.canExecuteCommand("/shutdown"),
            "UNTRUSTED no puede ejecutar /shutdown sin importar lo que diga");
    }

    @Test
    @DisplayName("Comparación de niveles es determinística")
    void levelComparison_isDeterministic() {
        for (int i = 0; i < 100; i++) {
            assertTrue(ChannelTrustLevel.SECURE_ENCRYPTED.isAtLeast(ChannelTrustLevel.UNTRUSTED_EXTERNAL));

            assertFalse(ChannelTrustLevel.UNTRUSTED_EXTERNAL.isAtLeast(ChannelTrustLevel.SECURE_ENCRYPTED));
        }
    }

    @Test
    @DisplayName("Wildcard solo existe en SECURE_ENCRYPTED")
    void wildcard_onlyExistsInSecure() {
        assertTrue(ChannelTrustLevel.SECURE_ENCRYPTED.getAllowedCommands().contains("*"),
            "SECURE debe tener wildcard");

        assertFalse(ChannelTrustLevel.TRUSTED_DEVICE.getAllowedCommands().contains("*"),
            "TRUSTED no debe tener wildcard");

        assertFalse(ChannelTrustLevel.UNTRUSTED_EXTERNAL.getAllowedCommands().contains("*"),
            "UNTRUSTED no debe tener wildcard");
    }

    @Test
    @DisplayName("Enum values() retorna siempre el mismo orden")
    void enumValues_alwaysSameOrder() {
        ChannelTrustLevel[] values1 = ChannelTrustLevel.values();
        ChannelTrustLevel[] values2 = ChannelTrustLevel.values();

        assertArrayEquals(values1, values2,
            "values() debe retornar siempre el mismo orden");

        assertEquals(ChannelTrustLevel.SECURE_ENCRYPTED, values1[0]);
        assertEquals(ChannelTrustLevel.TRUSTED_DEVICE, values1[1]);
        assertEquals(ChannelTrustLevel.UNTRUSTED_EXTERNAL, values1[2]);
    }

    @Test
    @DisplayName("Cada nivel tiene descripción y emoji únicos")
    void eachLevel_hasUniqueDescriptionAndEmoji() {
        Set<String> descriptions = Set.of(
            ChannelTrustLevel.SECURE_ENCRYPTED.getDescription(),
            ChannelTrustLevel.TRUSTED_DEVICE.getDescription(),
            ChannelTrustLevel.UNTRUSTED_EXTERNAL.getDescription()
        );

        assertEquals(3, descriptions.size(),
            "Cada nivel debe tener descripción única");

        Set<String> badges = Set.of(
            ChannelTrustLevel.SECURE_ENCRYPTED.getBadge(),
            ChannelTrustLevel.TRUSTED_DEVICE.getBadge(),
            ChannelTrustLevel.UNTRUSTED_EXTERNAL.getBadge()
        );

        assertEquals(3, badges.size(),
            "Cada nivel debe tener badge único");
    }

    @Test
    @DisplayName("toString incluye información de seguridad")
    void toString_includesSecurityInfo() {
        String untrustedStr = ChannelTrustLevel.UNTRUSTED_EXTERNAL.toString();

        assertTrue(untrustedStr.contains("UNTRUSTED_EXTERNAL"),
            "toString debe contener el nombre del nivel");
        assertTrue(untrustedStr.contains("25"),
            "toString debe contener el trust score");
    }

    @Test
    @DisplayName("Trust score forma una jerarquía estricta")
    void trustScore_formsStrictHierarchy() {
        int secureScore = ChannelTrustLevel.SECURE_ENCRYPTED.getTrustScore();
        int trustedScore = ChannelTrustLevel.TRUSTED_DEVICE.getTrustScore();
        int untrustedScore = ChannelTrustLevel.UNTRUSTED_EXTERNAL.getTrustScore();

        assertTrue(secureScore > trustedScore,
            "SECURE debe tener mayor score que TRUSTED");
        assertTrue(trustedScore > untrustedScore,
            "TRUSTED debe tener mayor score que UNTRUSTED");

        assertTrue(untrustedScore > 0,
            "Incluso el menor score debe ser positivo");
    }

    @Test
    @DisplayName("TRUSTED_DEVICE tiene comandos de desarrollo específicos")
    void trustedDevice_hasDevelopmentCommands() {
        Set<String> trustedCommands = ChannelTrustLevel.TRUSTED_DEVICE.getAllowedCommands();

        assertTrue(trustedCommands.contains("/debug"),
            "TRUSTED debe permitir /debug");
        assertTrue(trustedCommands.contains("/logs"),
            "TRUSTED debe permitir /logs");
        assertTrue(trustedCommands.contains("/metrics"),
            "TRUSTED debe permitir /metrics");

        assertFalse(trustedCommands.contains("*"),
            "TRUSTED no debe tener wildcard");

        assertEquals(14, trustedCommands.size(),
            "TRUSTED debe tener exactamente 14 comandos permitidos");
    }
}
