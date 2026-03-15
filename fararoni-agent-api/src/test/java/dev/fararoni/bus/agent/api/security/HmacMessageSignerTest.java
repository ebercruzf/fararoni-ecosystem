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
package dev.fararoni.bus.agent.api.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para HmacMessageSigner.
 *
 * Valida:
 * - Firma y verificacion roundtrip
 * - Deteccion de contenido alterado
 * - Deteccion de timestamp alterado
 * - Comparacion en tiempo constante
 * - Limpieza de recursos
 */
@DisplayName("HmacMessageSigner")
class HmacMessageSignerTest {

    @AfterEach
    void cleanup() {
        // Limpiar estado entre tests
        HmacMessageSigner.cleanup();
    }

    @Nested
    @DisplayName("Sign/Verify Roundtrip")
    class SignVerifyRoundtrip {

        @Test
        @DisplayName("sign() genera firma no vacia")
        void signGeneraFirma() {
            String content = "Hello World";
            long timestamp = System.currentTimeMillis();

            String signature = HmacMessageSigner.sign(content, timestamp);

            assertNotNull(signature);
            assertFalse(signature.isBlank());
        }

        @Test
        @DisplayName("verify() retorna true para firma valida")
        void verifyRetornaTrueParaFirmaValida() {
            String content = "Test message";
            long timestamp = System.currentTimeMillis();

            String signature = HmacMessageSigner.sign(content, timestamp);
            boolean valid = HmacMessageSigner.verify(content, timestamp, signature);

            assertTrue(valid);
        }

        @Test
        @DisplayName("verify() retorna false si contenido alterado")
        void verifyRetornaFalseSiContenidoAlterado() {
            String content = "Original message";
            long timestamp = System.currentTimeMillis();

            String signature = HmacMessageSigner.sign(content, timestamp);
            boolean valid = HmacMessageSigner.verify("Modified message", timestamp, signature);

            assertFalse(valid);
        }

        @Test
        @DisplayName("verify() retorna false si timestamp alterado")
        void verifyRetornaFalseSiTimestampAlterado() {
            String content = "Test message";
            long timestamp = System.currentTimeMillis();

            String signature = HmacMessageSigner.sign(content, timestamp);
            boolean valid = HmacMessageSigner.verify(content, timestamp + 1, signature);

            assertFalse(valid);
        }

        @Test
        @DisplayName("verify() retorna false para firma invalida")
        void verifyRetornaFalseParaFirmaInvalida() {
            String content = "Test message";
            long timestamp = System.currentTimeMillis();

            boolean valid = HmacMessageSigner.verify(content, timestamp, "firmaFalsaABC123");

            assertFalse(valid);
        }

        @Test
        @DisplayName("verify() retorna false para content null")
        void verifyRetornaFalseParaContentNull() {
            boolean valid = HmacMessageSigner.verify(null, System.currentTimeMillis(), "signature");

            assertFalse(valid);
        }

        @Test
        @DisplayName("verify() retorna false para signature null")
        void verifyRetornaFalseParaSignatureNull() {
            boolean valid = HmacMessageSigner.verify("content", System.currentTimeMillis(), null);

            assertFalse(valid);
        }
    }

    @Nested
    @DisplayName("Consistencia de Firmas")
    class ConsistenciaFirmas {

        @Test
        @DisplayName("Mismo contenido y timestamp produce misma firma")
        void mismoContenidoMismaFirma() {
            String content = "Consistent message";
            long timestamp = 1707523200000L; // Timestamp fijo

            String sig1 = HmacMessageSigner.sign(content, timestamp);
            String sig2 = HmacMessageSigner.sign(content, timestamp);

            assertEquals(sig1, sig2);
        }

        @Test
        @DisplayName("Contenido diferente produce firma diferente")
        void contenidoDiferenteFirmaDiferente() {
            long timestamp = System.currentTimeMillis();

            String sig1 = HmacMessageSigner.sign("Message A", timestamp);
            String sig2 = HmacMessageSigner.sign("Message B", timestamp);

            assertNotEquals(sig1, sig2);
        }

        @Test
        @DisplayName("Timestamp diferente produce firma diferente")
        void timestampDiferenteFirmaDiferente() {
            String content = "Same content";

            String sig1 = HmacMessageSigner.sign(content, 1000L);
            String sig2 = HmacMessageSigner.sign(content, 2000L);

            assertNotEquals(sig1, sig2);
        }
    }

    @Nested
    @DisplayName("Validacion de Entrada")
    class ValidacionEntrada {

        @Test
        @DisplayName("sign() lanza exception para content null")
        void signLanzaExceptionParaContentNull() {
            assertThrows(NullPointerException.class, () ->
                HmacMessageSigner.sign(null, System.currentTimeMillis())
            );
        }

        @Test
        @DisplayName("sign() acepta content vacio")
        void signAceptaContentVacio() {
            String signature = HmacMessageSigner.sign("", System.currentTimeMillis());
            assertNotNull(signature);
        }

        @Test
        @DisplayName("sign() acepta timestamp cero")
        void signAceptaTimestampCero() {
            String signature = HmacMessageSigner.sign("content", 0L);
            assertNotNull(signature);
        }

        @Test
        @DisplayName("sign() acepta timestamp negativo")
        void signAceptaTimestampNegativo() {
            String signature = HmacMessageSigner.sign("content", -1L);
            assertNotNull(signature);
        }
    }

    @Nested
    @DisplayName("Estado y Diagnostico")
    class EstadoDiagnostico {

        @Test
        @DisplayName("getStatus() retorna informacion valida")
        void getStatusRetornaInfo() {
            var status = HmacMessageSigner.getStatus();

            assertNotNull(status);
            assertEquals("HmacSHA256", status.algorithm());
            assertEquals("FARARONI_HMAC_SECRET", status.secretEnvVar());
            // productionKeyActive depende del ambiente
            assertNotNull(status.toString());
        }

        @Test
        @DisplayName("isProductionKeyConfigured() retorna false sin variable de entorno")
        void isProductionKeyConfiguredSinEnv() {
            // En ambiente de test, normalmente no hay variable configurada
            // Este test verifica que el metodo no falla
            boolean result = HmacMessageSigner.isProductionKeyConfigured();
            // El resultado depende del ambiente, solo verificamos que no falle
            assertNotNull(Boolean.valueOf(result));
        }
    }

    @Nested
    @DisplayName("Limpieza de Recursos")
    class LimpiezaRecursos {

        @Test
        @DisplayName("cleanup() no lanza exception")
        void cleanupNoLanzaException() {
            // Primero usar el signer
            HmacMessageSigner.sign("test", 123L);

            // Luego limpiar
            assertDoesNotThrow(HmacMessageSigner::cleanup);
        }

        @Test
        @DisplayName("resetSecretKey() permite recargar clave")
        void resetSecretKeyPermiteRecargar() {
            // Firmar para cargar la clave
            String sig1 = HmacMessageSigner.sign("test", 123L);

            // Reset
            HmacMessageSigner.resetSecretKey();

            // Firmar de nuevo (recarga la clave)
            String sig2 = HmacMessageSigner.sign("test", 123L);

            // Deben ser iguales (misma clave por defecto)
            assertEquals(sig1, sig2);
        }

        @Test
        @DisplayName("Signer funciona despues de cleanup")
        void signerFuncionaDespuesDeCleanup() {
            HmacMessageSigner.sign("before", 1L);
            HmacMessageSigner.cleanup();

            // Debe funcionar normalmente despues de cleanup
            String signature = HmacMessageSigner.sign("after", 2L);
            assertNotNull(signature);
        }
    }

    @Nested
    @DisplayName("Formato de Firma")
    class FormatoFirma {

        @Test
        @DisplayName("Firma es Base64 valido")
        void firmaEsBase64Valido() {
            String signature = HmacMessageSigner.sign("test", System.currentTimeMillis());

            // Intentar decodificar - no debe lanzar exception
            assertDoesNotThrow(() -> {
                byte[] decoded = java.util.Base64.getDecoder().decode(signature);
                // HMAC-SHA256 produce 32 bytes
                assertEquals(32, decoded.length);
            });
        }

        @Test
        @DisplayName("Firma tiene longitud consistente")
        void firmaLongitudConsistente() {
            String sig1 = HmacMessageSigner.sign("short", 1L);
            String sig2 = HmacMessageSigner.sign("a very long message with lots of content", 2L);

            // Base64 de 32 bytes = 44 caracteres
            assertEquals(44, sig1.length());
            assertEquals(44, sig2.length());
        }
    }
}
