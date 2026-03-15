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
package dev.fararoni.bus.agent.api.bus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para SovereignEnvelope.
 * Valida: creacion, inmutabilidad, TTL, reintentos, wither methods.
 */
@DisplayName("SovereignEnvelope")
class SovereignEnvelopeTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("create(userId, payload) genera UUID e inicializa campos")
        void createBasico() {
            var envelope = SovereignEnvelope.create("user-123", "Hello");

            assertNotNull(envelope.id(), "ID debe generarse");
            assertNotNull(envelope.traceId(), "TraceId debe generarse");
            assertEquals("user-123", envelope.userId());
            assertEquals("Hello", envelope.payload());
            assertNull(envelope.correlationId());
            assertNull(envelope.replyTo());
            assertNull(envelope.senderRole());
            assertEquals(SovereignEnvelope.DEFAULT_TTL_MS, envelope.ttlMs());
            assertEquals(0, envelope.retryCount());
            assertNotNull(envelope.timestamp());
            assertTrue(envelope.headers().isEmpty());
        }

        @Test
        @DisplayName("create(userId, traceId, payload) respeta traceId proporcionado")
        void createConTraceId() {
            var envelope = SovereignEnvelope.create("user-123", "trace-abc", "Payload");

            assertEquals("trace-abc", envelope.traceId());
            assertEquals("user-123", envelope.userId());
        }

        @Test
        @DisplayName("create(userId, senderRole, traceId, payload) incluye senderRole")
        void createCompleto() {
            var envelope = SovereignEnvelope.create("user-123", "SUPERVISOR", "trace-xyz", "Task");

            assertEquals("SUPERVISOR", envelope.senderRole());
            assertEquals("trace-xyz", envelope.traceId());
            assertEquals("user-123", envelope.userId());
            assertEquals("Task", envelope.payload());
        }

        @Test
        @DisplayName("createWithTtl() permite TTL personalizado")
        void createConTtlPersonalizado() {
            var envelope = SovereignEnvelope.createWithTtl(
                "user-123", "DEVELOPER", "trace-1", 60_000, "Data"
            );

            assertEquals(60_000, envelope.ttlMs());
            assertEquals("DEVELOPER", envelope.senderRole());
        }

        @Test
        @DisplayName("create con traceId null genera uno nuevo")
        void createTraceIdNullGeneraNuevo() {
            var envelope = SovereignEnvelope.create("user", null, "payload");

            assertNotNull(envelope.traceId());
            assertFalse(envelope.traceId().isBlank());
        }
    }

    @Nested
    @DisplayName("Wither Methods (Inmutabilidad)")
    class WitherMethods {

        @Test
        @DisplayName("withReplyTo() crea nueva instancia con replyTo")
        void withReplyTo() {
            var original = SovereignEnvelope.create("user", "data");
            var modified = original.withReplyTo("reply.topic");

            assertNotSame(original, modified);
            assertNull(original.replyTo());
            assertEquals("reply.topic", modified.replyTo());
            assertEquals(original.id(), modified.id()); // ID se mantiene
        }

        @Test
        @DisplayName("withCorrelation() crea nueva instancia con correlationId")
        void withCorrelation() {
            var original = SovereignEnvelope.create("user", "data");
            var modified = original.withCorrelation("corr-123");

            assertNotSame(original, modified);
            assertNull(original.correlationId());
            assertEquals("corr-123", modified.correlationId());
        }

        @Test
        @DisplayName("withSenderRole() crea nueva instancia con senderRole")
        void withSenderRole() {
            var original = SovereignEnvelope.create("user", "data");
            var modified = original.withSenderRole("ARCHITECT");

            assertNotSame(original, modified);
            assertNull(original.senderRole());
            assertEquals("ARCHITECT", modified.senderRole());
        }

        @Test
        @DisplayName("withHeader() agrega header sin modificar original")
        void withHeader() {
            var original = SovereignEnvelope.create("user", "data");
            var modified = original.withHeader("key", "value");

            assertNotSame(original, modified);
            assertTrue(original.headers().isEmpty());
            assertEquals("value", modified.headers().get("key"));
        }

        @Test
        @DisplayName("withHeaders() agrega multiples headers")
        void withHeaders() {
            var original = SovereignEnvelope.create("user", "data");
            var modified = original.withHeaders(Map.of("a", "1", "b", "2"));

            assertEquals(2, modified.headers().size());
            assertEquals("1", modified.headers().get("a"));
            assertEquals("2", modified.headers().get("b"));
        }

        @Test
        @DisplayName("Encadenamiento de withers funciona correctamente")
        void encadenamientoWithers() {
            var envelope = SovereignEnvelope.create("user", "data")
                .withReplyTo("reply.topic")
                .withCorrelation("corr-1")
                .withSenderRole("QA")
                .withHeader("priority", "HIGH");

            assertEquals("reply.topic", envelope.replyTo());
            assertEquals("corr-1", envelope.correlationId());
            assertEquals("QA", envelope.senderRole());
            assertEquals("HIGH", envelope.headers().get("priority"));
        }
    }

    @Nested
    @DisplayName("Ciclo de Vida (TTL y Reintentos)")
    class CicloDeVida {

        @Test
        @DisplayName("incrementRetry() crea nueva instancia con retryCount + 1")
        void incrementRetry() {
            var original = SovereignEnvelope.create("user", "data");
            assertEquals(0, original.retryCount());

            var retry1 = original.incrementRetry();
            assertEquals(1, retry1.retryCount());
            assertEquals(0, original.retryCount()); // Original no cambia

            var retry2 = retry1.incrementRetry();
            assertEquals(2, retry2.retryCount());

            var retry3 = retry2.incrementRetry();
            assertEquals(3, retry3.retryCount());
        }

        @Test
        @DisplayName("isMaxRetriesExceeded() true cuando retryCount >= MAX")
        void isMaxRetriesExceeded() {
            var envelope = SovereignEnvelope.create("user", "data");
            assertFalse(envelope.isMaxRetriesExceeded());

            var retry1 = envelope.incrementRetry();
            assertFalse(retry1.isMaxRetriesExceeded());

            var retry2 = retry1.incrementRetry();
            assertFalse(retry2.isMaxRetriesExceeded());

            var retry3 = retry2.incrementRetry();
            assertTrue(retry3.isMaxRetriesExceeded()); // 3 >= MAX_RETRY_COUNT
        }

        @Test
        @DisplayName("isExpired() false para mensaje recien creado")
        void isExpiredFalseParaNuevo() {
            var envelope = SovereignEnvelope.create("user", "data");
            assertFalse(envelope.isExpired());
        }

        @Test
        @DisplayName("isExpired() true cuando pasa el TTL")
        void isExpiredTrueCuandoPasaTtl() {
            // Crear envelope con TTL de 1ms y timestamp antiguo
            // Record V3: id, idempotencyKey, schemaVersion, traceId, corrId, replyTo,
            //            senderRole, userId, signature, timestamp, ttlMs, retryCount,
            //            hopCount, headers, payload
            var envelope = new SovereignEnvelope<>(
                "id-1",
                "test-idem-key",  // [V3] idempotencyKey
                "3.0",           // [V3] schemaVersion
                "trace-1",
                null,       // correlationId
                null,       // replyTo
                null,       // senderRole
                "user",     // userId
                null,       // signature [FASE 40.1]
                Instant.now().minusMillis(100), // 100ms en el pasado
                50,         // TTL de 50ms
                0,          // retryCount
                0,          // hopCount [FASE 40.1]
                Map.of(),
                "data"
            );

            assertTrue(envelope.isExpired());
        }

        @Test
        @DisplayName("ageMs() calcula edad correctamente")
        void ageMs() throws InterruptedException {
            var envelope = SovereignEnvelope.create("user", "data");
            Thread.sleep(10);
            assertTrue(envelope.ageMs() >= 10);
        }

        @Test
        @DisplayName("remainingTtlMs() calcula tiempo restante")
        void remainingTtlMs() {
            var envelope = SovereignEnvelope.create("user", "data");
            long remaining = envelope.remainingTtlMs();

            assertTrue(remaining > 0);
            assertTrue(remaining <= SovereignEnvelope.DEFAULT_TTL_MS);
        }

        @Test
        @DisplayName("remainingTtlMs() negativo para mensaje expirado")
        void remainingTtlMsNegativo() {
            // Record V3: id, idempotencyKey, schemaVersion, traceId, corrId, replyTo,
            //            senderRole, userId, signature, timestamp, ttlMs, retryCount,
            //            hopCount, headers, payload
            var envelope = new SovereignEnvelope<>(
                "id-1", "test-idem-key", "3.0", "trace-1", null, null, null, "user",
                null,       // signature [FASE 40.1]
                Instant.now().minusMillis(100),
                50,         // TTL 50ms, edad 100ms
                0,          // retryCount
                0,          // hopCount [FASE 40.1]
                Map.of(), "data"
            );

            assertTrue(envelope.remainingTtlMs() < 0);
        }
    }

    @Nested
    @DisplayName("Metodos de Consulta")
    class MetodosConsulta {

        @Test
        @DisplayName("expectsReply() true si replyTo esta configurado")
        void expectsReplyTrue() {
            var envelope = SovereignEnvelope.create("user", "data")
                .withReplyTo("reply.topic");

            assertTrue(envelope.expectsReply());
        }

        @Test
        @DisplayName("expectsReply() false si replyTo es null o vacio")
        void expectsReplyFalse() {
            var envelope1 = SovereignEnvelope.create("user", "data");
            assertFalse(envelope1.expectsReply());

            var envelope2 = envelope1.withReplyTo("");
            assertFalse(envelope2.expectsReply());

            var envelope3 = envelope1.withReplyTo("   ");
            assertFalse(envelope3.expectsReply());
        }
    }

    @Nested
    @DisplayName("Constantes")
    class Constantes {

        @Test
        @DisplayName("DEFAULT_TTL_MS es 30 segundos")
        void defaultTtl() {
            assertEquals(30_000, SovereignEnvelope.DEFAULT_TTL_MS);
        }

        @Test
        @DisplayName("MAX_RETRY_COUNT es 3")
        void maxRetryCount() {
            assertEquals(3, SovereignEnvelope.MAX_RETRY_COUNT);
        }
    }

    @Nested
    @DisplayName("Tipos Genericos")
    class TiposGenericos {

        @Test
        @DisplayName("Soporta payload de cualquier tipo")
        void soportaTiposGenericos() {
            record CustomPayload(String name, int value) {}

            var envelope = SovereignEnvelope.create("user", new CustomPayload("test", 42));

            assertInstanceOf(CustomPayload.class, envelope.payload());
            assertEquals("test", envelope.payload().name());
            assertEquals(42, envelope.payload().value());
        }

        @Test
        @DisplayName("Soporta payload null")
        void soportaPayloadNull() {
            var envelope = SovereignEnvelope.create("user", null);
            assertNull(envelope.payload());
        }
    }

    // =========================================================================
    // Tests de Seguridad (Signature + HopCount)
    // =========================================================================

    @Nested
    @DisplayName("Seguridad - Signature")
    class SecuritySignature {

        @Test
        @DisplayName("Envelope nuevo no tiene firma")
        void envelopeNuevoSinFirma() {
            var envelope = SovereignEnvelope.create("user", "data");

            assertNull(envelope.signature());
            assertFalse(envelope.hasSignature());
        }

        @Test
        @DisplayName("withSignature() agrega firma al envelope")
        void withSignatureAgregaFirma() {
            var original = SovereignEnvelope.create("user", "data");
            var signed = original.withSignature("ABC123signature");

            assertNotSame(original, signed);
            assertNull(original.signature());
            assertEquals("ABC123signature", signed.signature());
            assertTrue(signed.hasSignature());
        }

        @Test
        @DisplayName("withSignature() preserva otros campos")
        void withSignaturePreservaCampos() {
            var original = SovereignEnvelope.create("user", "ROLE", "trace-1", "payload")
                .withReplyTo("reply.topic")
                .withHeader("key", "value");

            var signed = original.withSignature("sig123");

            assertEquals(original.id(), signed.id());
            assertEquals(original.userId(), signed.userId());
            assertEquals(original.senderRole(), signed.senderRole());
            assertEquals(original.traceId(), signed.traceId());
            assertEquals(original.replyTo(), signed.replyTo());
            assertEquals(original.headers(), signed.headers());
            assertEquals(original.payload(), signed.payload());
        }

        @Test
        @DisplayName("hasSignature() false para firma null")
        void hasSignatureFalseParaNull() {
            var envelope = SovereignEnvelope.create("user", "data");
            assertFalse(envelope.hasSignature());
        }

        @Test
        @DisplayName("hasSignature() false para firma vacia")
        void hasSignatureFalseParaVacia() {
            var envelope = SovereignEnvelope.create("user", "data")
                .withSignature("");
            assertFalse(envelope.hasSignature());
        }

        @Test
        @DisplayName("hasSignature() false para firma solo espacios")
        void hasSignatureFalseParaEspacios() {
            var envelope = SovereignEnvelope.create("user", "data")
                .withSignature("   ");
            assertFalse(envelope.hasSignature());
        }
    }

    @Nested
    @DisplayName("Seguridad - HopCount (Anti-Loop)")
    class SecurityHopCount {

        @Test
        @DisplayName("Envelope nuevo tiene hopCount 0")
        void envelopeNuevoHopCountCero() {
            var envelope = SovereignEnvelope.create("user", "data");

            assertEquals(0, envelope.hopCount());
            assertFalse(envelope.isMaxHopsExceeded());
        }

        @Test
        @DisplayName("incrementHop() aumenta hopCount en 1")
        void incrementHopAumentaEnUno() {
            var original = SovereignEnvelope.create("user", "data");
            var hop1 = original.incrementHop();
            var hop2 = hop1.incrementHop();
            var hop3 = hop2.incrementHop();

            assertEquals(0, original.hopCount());
            assertEquals(1, hop1.hopCount());
            assertEquals(2, hop2.hopCount());
            assertEquals(3, hop3.hopCount());
        }

        @Test
        @DisplayName("incrementHop() no modifica original (inmutabilidad)")
        void incrementHopInmutable() {
            var original = SovereignEnvelope.create("user", "data");
            var hopped = original.incrementHop();

            assertNotSame(original, hopped);
            assertEquals(0, original.hopCount());
            assertEquals(1, hopped.hopCount());
        }

        @Test
        @DisplayName("incrementHop() preserva otros campos")
        void incrementHopPreservaCampos() {
            var original = SovereignEnvelope.create("user", "ROLE", "trace-1", "payload")
                .withSignature("sig123")
                .withReplyTo("reply.topic");

            var hopped = original.incrementHop();

            assertEquals(original.id(), hopped.id());
            assertEquals(original.signature(), hopped.signature());
            assertEquals(original.userId(), hopped.userId());
            assertEquals(original.replyTo(), hopped.replyTo());
        }

        @Test
        @DisplayName("isMaxHopsExceeded() true cuando hopCount >= MAX_HOP_COUNT")
        void isMaxHopsExceededTrue() {
            var envelope = SovereignEnvelope.create("user", "data");

            // Incrementar hasta el limite
            for (int i = 0; i < SovereignEnvelope.MAX_HOP_COUNT - 1; i++) {
                envelope = envelope.incrementHop();
                assertFalse(envelope.isMaxHopsExceeded(),
                    "hopCount=" + envelope.hopCount() + " no deberia exceder max");
            }

            // Uno mas para llegar al limite
            envelope = envelope.incrementHop();
            assertTrue(envelope.isMaxHopsExceeded(),
                "hopCount=" + envelope.hopCount() + " deberia exceder max");
        }

        @Test
        @DisplayName("MAX_HOP_COUNT es 50")
        void maxHopCountEs50() {
            assertEquals(50, SovereignEnvelope.MAX_HOP_COUNT);
        }

        @Test
        @DisplayName("Simular bucle infinito: hopCount crece con cada hop")
        void simularBucleInfinito() {
            var envelope = SovereignEnvelope.create("user", "data");

            int hops = 0;
            while (!envelope.isMaxHopsExceeded() && hops < 100) {
                envelope = envelope.incrementHop();
                hops++;
            }

            // Deberia detenerse en MAX_HOP_COUNT
            assertEquals(SovereignEnvelope.MAX_HOP_COUNT, hops);
            assertTrue(envelope.isMaxHopsExceeded());
        }
    }

    @Nested
    @DisplayName("Constantes de Seguridad")
    class ConstantesSeguridad {

        @Test
        @DisplayName("MAX_HOP_COUNT definida correctamente")
        void maxHopCountDefinida() {
            assertTrue(SovereignEnvelope.MAX_HOP_COUNT > 0);
            assertEquals(50, SovereignEnvelope.MAX_HOP_COUNT);
        }
    }
}
