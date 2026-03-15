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
package dev.fararoni.core.core.persistence;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.core.core.resilience.PoisonPill;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SovereignJournalTest {
    private static Path tempDbPath;
    private static SovereignJournal journal;

    @BeforeAll
    static void setUp() throws Exception {
        tempDbPath = Files.createTempFile("sovereign_journal_test_", ".db");
        journal = new SovereignJournal(tempDbPath);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (journal != null) {
            journal.close();
        }
        Files.deleteIfExists(tempDbPath);
        Files.deleteIfExists(Path.of(tempDbPath.toString() + "-wal"));
        Files.deleteIfExists(Path.of(tempDbPath.toString() + "-shm"));
    }

    @Test
    @Order(1)
    @DisplayName("persistEvent: debe guardar evento en SQLite con status PENDING")
    void testPersistEvent() throws Exception {
        String topic = "test.topic.persist";
        String payload = "Test message payload";
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST_AGENT", "trace-123", payload);

        boolean result = journal.persistEvent(topic, envelope);

        assertTrue(result, "El evento debe persistirse exitosamente");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT id, topic, status, payload_json FROM outbox_events WHERE id = ?")) {
            stmt.setString(1, envelope.id());
            ResultSet rs = stmt.executeQuery();

            assertTrue(rs.next(), "El evento debe existir en la base de datos");
            assertEquals(envelope.id(), rs.getString("id"));
            assertEquals(topic, rs.getString("topic"));
            assertEquals("PENDING", rs.getString("status"));
            assertNotNull(rs.getString("payload_json"));
        }
    }

    @Test
    @Order(2)
    @DisplayName("markDispatched: debe cambiar status a DISPATCHED")
    void testMarkDispatched() throws Exception {
        String topic = "test.topic.dispatch";
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST_AGENT", "trace-456", "Dispatch test");
        journal.persistEvent(topic, envelope);

        journal.markDispatched(envelope.id());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status, dispatched_at FROM outbox_events WHERE id = ?")) {
            stmt.setString(1, envelope.id());
            ResultSet rs = stmt.executeQuery();

            assertTrue(rs.next());
            assertEquals("DISPATCHED", rs.getString("status"));
            assertNotNull(rs.getLong("dispatched_at"), "dispatched_at debe tener valor");
        }
    }

    @Test
    @Order(3)
    @DisplayName("markFailed: debe cambiar status a FAILED con mensaje de error")
    void testMarkFailed() throws Exception {
        String topic = "test.topic.fail";
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST_AGENT", "trace-789", "Fail test");
        journal.persistEvent(topic, envelope);

        String errorMsg = "Test failure reason";
        journal.markFailed(envelope.id(), errorMsg);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status, last_error FROM outbox_events WHERE id = ?")) {
            stmt.setString(1, envelope.id());
            ResultSet rs = stmt.executeQuery();

            assertTrue(rs.next());
            assertEquals("FAILED", rs.getString("status"));
            assertEquals(errorMsg, rs.getString("last_error"));
        }
    }

    @Test
    @Order(4)
    @DisplayName("recoverPending: debe retornar solo eventos PENDING")
    void testRecoverPending() throws Exception {
        String topic = "test.topic.recover";
        SovereignEnvelope<String> pendingEnvelope = SovereignEnvelope.create("TEST_AGENT", "trace-recover", "Pending for recovery");
        journal.persistEvent(topic, pendingEnvelope);

        List<OutboxEvent> pending = journal.recoverPending();

        assertFalse(pending.isEmpty(), "Debe haber eventos pendientes");

        for (OutboxEvent event : pending) {
            assertEquals(OutboxEvent.OutboxStatus.PENDING, event.status(),
                "Solo deben recuperarse eventos con status PENDING");
        }

        boolean found = pending.stream().anyMatch(e -> e.id().equals(pendingEnvelope.id()));
        assertTrue(found, "El evento pendiente debe estar en la lista de recovery");
    }

    @Test
    @Order(5)
    @DisplayName("savePoisonPill: debe persistir PoisonPill en tabla poison_pills")
    void testSavePoisonPill() throws Exception {
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("FAILED_AGENT", "trace-dlq", "Failed message");
        PoisonPill pill = PoisonPill.create(
            "test.dlq.topic",
            new RuntimeException("Test error"),
            null,
            envelope
        );

        journal.savePoisonPill(pill);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT original_topic, failure_reason, status FROM poison_pills WHERE original_topic = ?")) {
            stmt.setString(1, "test.dlq.topic");
            ResultSet rs = stmt.executeQuery();

            assertTrue(rs.next(), "PoisonPill debe existir en la base de datos");
            assertEquals("test.dlq.topic", rs.getString("original_topic"));
            assertNotNull(rs.getString("failure_reason"));
            assertEquals("PENDING", rs.getString("status"));
        }
    }

    @Test
    @Order(6)
    @DisplayName("markResurrected: debe marcar PoisonPill como RESURRECTED")
    void testMarkResurrected() throws Exception {
        var pills = journal.getPendingPoisonPills(1);
        assertFalse(pills.isEmpty(), "Debe haber al menos un PoisonPill pendiente");
        String pillId = pills.get(0).id();

        journal.markResurrected(pillId);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status FROM poison_pills WHERE id = ?")) {
            stmt.setString(1, pillId);
            ResultSet rs = stmt.executeQuery();

            assertTrue(rs.next());
            assertEquals("RESURRECTED", rs.getString("status"));
        }
    }

    @Test
    @Order(7)
    @DisplayName("getMetrics: debe retornar métricas correctas")
    void testGetMetrics() {
        SovereignJournal.JournalMetrics metrics = journal.getMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.eventsPersisted() > 0, "Debe haber eventos persistidos");
        assertTrue(metrics.eventsDispatched() > 0, "Debe haber eventos despachados");
        assertTrue(metrics.poisonPillsSaved() > 0, "Debe haber poison pills guardados");
        assertTrue(metrics.successRate() >= 0.0 && metrics.successRate() <= 1.0,
            "Success rate debe estar entre 0 y 1");
    }

    @Test
    @DisplayName("OutboxEvent.fromEnvelope: debe crear evento con checksum válido")
    void testOutboxEventFromEnvelope() {
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST", "trace-1", "payload");
        String payloadJson = "{\"test\": \"data\"}";

        OutboxEvent event = OutboxEvent.fromEnvelope("test.topic", envelope, payloadJson);

        assertEquals(envelope.id(), event.id());
        assertEquals("test.topic", event.topic());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, event.status());
        assertEquals(0, event.retryCount());
        assertNotNull(event.checksum());
        assertFalse(event.checksum().isEmpty());
        assertNotNull(event.createdAt());
        assertNotNull(event.expiresAt());
    }

    @Test
    @DisplayName("OutboxEvent.canRetry: debe retornar false si excede max retries")
    void testOutboxEventCanRetry() {
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST", "trace-2", "payload");
        OutboxEvent freshEvent = OutboxEvent.fromEnvelope("test.topic", envelope, "{}");

        assertTrue(freshEvent.canRetry(), "Evento fresco debe poder reintentarse");

        OutboxEvent exhaustedEvent = new OutboxEvent(
            UUID.randomUUID().toString(),
            "trace", "corr", "topic", "sender", null,
            "{}", "String",
            OutboxEvent.OutboxStatus.PENDING,
            5,
            3,
            30000L,
            java.time.Instant.now(),
            null,
            java.time.Instant.now().plusSeconds(3600),
            null,
            "checksum"
        );

        assertFalse(exhaustedEvent.canRetry(), "Evento con retries agotados no debe poder reintentarse");
    }

    @Test
    @DisplayName("OutboxEvent.isExpired: debe detectar eventos expirados")
    void testOutboxEventIsExpired() {
        OutboxEvent expiredEvent = new OutboxEvent(
            UUID.randomUUID().toString(),
            "trace", "corr", "topic", "sender", null,
            "{}", "String",
            OutboxEvent.OutboxStatus.PENDING,
            0, 3, 30000L,
            java.time.Instant.now().minusSeconds(100),
            null,
            java.time.Instant.now().minusSeconds(50),
            null,
            "checksum"
        );

        assertTrue(expiredEvent.isExpired(), "Evento con expiresAt en el pasado debe estar expirado");

        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST", "trace-3", "payload");
        OutboxEvent freshEvent = OutboxEvent.fromEnvelope("test.topic", envelope, "{}");

        assertFalse(freshEvent.isExpired(), "Evento fresco no debe estar expirado");
    }

    @Test
    @DisplayName("persistEvent: debe ser idempotente (no duplicar)")
    void testPersistEventIdempotent() throws Exception {
        String topic = "test.topic.idempotent";
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST_AGENT", "trace-idem", "Idempotent test");

        boolean first = journal.persistEvent(topic, envelope);
        boolean second = journal.persistEvent(topic, envelope);

        assertTrue(first, "Primera persistencia debe ser exitosa");
        assertFalse(second, "Segunda persistencia debe retornar false (ya existe)");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM outbox_events WHERE id = ?")) {
            stmt.setString(1, envelope.id());
            ResultSet rs = stmt.executeQuery();
            rs.next();
            assertEquals(1, rs.getInt(1), "Solo debe existir un registro");
        }
    }
}
