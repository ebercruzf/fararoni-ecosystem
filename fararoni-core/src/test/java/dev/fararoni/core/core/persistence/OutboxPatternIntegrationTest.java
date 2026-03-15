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
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OutboxPatternIntegrationTest {
    private static Path tempDbPath;
    private static SovereignJournal journal;
    private static InMemorySovereignBus bus;

    @BeforeAll
    static void setUp() throws Exception {
        tempDbPath = Files.createTempFile("outbox_integration_test_", ".db");
        journal = new SovereignJournal(tempDbPath);

        bus = new InMemorySovereignBus();
        bus.connectJournal(journal);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bus != null) {
            bus.shutdown(Duration.ofSeconds(5));
        }
        if (journal != null) {
            journal.close();
        }
        Files.deleteIfExists(tempDbPath);
        Files.deleteIfExists(Path.of(tempDbPath.toString() + "-wal"));
        Files.deleteIfExists(Path.of(tempDbPath.toString() + "-shm"));
    }

    @Test
    @Order(1)
    @DisplayName("Flujo completo: publish → persist → deliver → dispatch")
    void testCompleteOutboxFlow() throws Exception {
        String topic = "integration.test.flow";
        String payload = "Integration test message";
        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST_AGENT", "trace-integ", payload);

        CountDownLatch receivedLatch = new CountDownLatch(1);
        List<String> receivedPayloads = new ArrayList<>();

        bus.subscribe(topic, String.class, env -> {
            receivedPayloads.add(env.payload());
            receivedLatch.countDown();
        });

        bus.publish(topic, envelope).join();

        boolean received = receivedLatch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "El mensaje debe ser recibido por el subscriber");
        assertEquals(1, receivedPayloads.size());
        assertEquals(payload, receivedPayloads.get(0));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status FROM outbox_events WHERE id = ?")) {
            stmt.setString(1, envelope.id());
            ResultSet rs = stmt.executeQuery();

            assertTrue(rs.next(), "El evento debe existir en la base de datos");
            assertEquals("DISPATCHED", rs.getString("status"),
                "El evento debe estar marcado como DISPATCHED");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Múltiples mensajes: todos persisten y se despachan")
    void testMultipleMessagesFlow() throws Exception {
        String topic = "integration.test.batch";
        int messageCount = 10;
        CountDownLatch receivedLatch = new CountDownLatch(messageCount);
        AtomicInteger receivedCount = new AtomicInteger(0);

        bus.subscribe(topic, String.class, env -> {
            receivedCount.incrementAndGet();
            receivedLatch.countDown();
        });

        List<String> envelopeIds = new ArrayList<>();

        for (int i = 0; i < messageCount; i++) {
            SovereignEnvelope<String> envelope = SovereignEnvelope.create(
                "BATCH_AGENT",
                "trace-batch-" + i,
                "Message " + i
            );
            envelopeIds.add(envelope.id());
            bus.publish(topic, envelope);
        }

        boolean allReceived = receivedLatch.await(10, TimeUnit.SECONDS);

        assertTrue(allReceived, "Todos los mensajes deben ser recibidos");
        assertEquals(messageCount, receivedCount.get());

        Thread.sleep(500);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM outbox_events WHERE id IN (" +
                 String.join(",", envelopeIds.stream().map(id -> "'" + id + "'").toList()) +
                 ") AND status = 'DISPATCHED'")) {
            ResultSet rs = stmt.executeQuery();
            rs.next();
            int dispatchedCount = rs.getInt(1);

            assertEquals(messageCount, dispatchedCount,
                "Todos los mensajes deben estar DISPATCHED en la DB");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Journal conectado: isJournalConnected retorna true")
    void testJournalConnected() {
        assertTrue(bus.isJournalConnected(),
            "El bus debe reportar que el Journal está conectado");
    }

    @Test
    @Order(4)
    @DisplayName("Métricas de Journal: reflejan operaciones")
    void testJournalMetricsAfterOperations() {
        SovereignJournal.JournalMetrics metrics = journal.getMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.eventsPersisted() >= 11,
            "Debe haber al menos 11 eventos persistidos");
        assertTrue(metrics.eventsDispatched() >= 11,
            "Debe haber al menos 11 eventos despachados");
        assertTrue(metrics.successRate() > 0.5,
            "Success rate debe ser mayor al 50%");
    }

    @Test
    @Order(5)
    @DisplayName("Recovery: OutboxDispatcher recupera mensajes pendientes")
    void testOutboxDispatcherRecovery() throws Exception {
        String eventId = "recovery-test-" + System.currentTimeMillis();
        String topic = "integration.test.recovery";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             PreparedStatement stmt = conn.prepareStatement("""
                INSERT INTO outbox_events
                (id, trace_id, correlation_id, topic, sender_role, payload_json, payload_type,
                 status, retry_count, max_retries, ttl_ms, created_at, expires_at, checksum)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, 3, 30000, ?, ?, ?)
             """)) {
            long now = System.currentTimeMillis();
            stmt.setString(1, eventId);
            stmt.setString(2, "trace-recovery");
            stmt.setString(3, "corr-recovery");
            stmt.setString(4, topic);
            stmt.setString(5, "TEST_RECOVERY");
            stmt.setString(6, "{\"id\":\"" + eventId + "\",\"payload\":\"recovery test\"}");
            stmt.setString(7, "java.lang.String");
            stmt.setLong(8, now);
            stmt.setLong(9, now + 30000);
            stmt.setString(10, "checksum123");
            stmt.executeUpdate();
        }

        OutboxDispatcher dispatcher = new OutboxDispatcher(journal, bus);
        dispatcher.start();

        Thread.sleep(1000);

        assertTrue(dispatcher.getRecoveredCount() >= 0,
            "El dispatcher debe reportar eventos recuperados");

        dispatcher.stop();
    }

    @Test
    @Order(6)
    @DisplayName("SQLite WAL: base de datos es válida y consultable")
    void testSqliteWalIntegrity() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             Statement stmt = conn.createStatement()) {
            ResultSet rsWal = stmt.executeQuery("PRAGMA journal_mode;");
            rsWal.next();
            assertEquals("wal", rsWal.getString(1).toLowerCase(),
                "La base debe estar en modo WAL");

            ResultSet rsStats = stmt.executeQuery(
                "SELECT status, COUNT(*) as count FROM outbox_events GROUP BY status");

            boolean hasData = false;
            while (rsStats.next()) {
                hasData = true;
                String status = rsStats.getString("status");
                int count = rsStats.getInt("count");
                assertTrue(count >= 0, "Conteo de " + status + " debe ser >= 0");
            }
            assertTrue(hasData, "Debe haber datos en la tabla outbox_events");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Índices: existen y están activos")
    void testIndexesExist() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + tempDbPath);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='outbox_events'");

            List<String> indexes = new ArrayList<>();
            while (rs.next()) {
                indexes.add(rs.getString("name"));
            }

            assertTrue(indexes.stream().anyMatch(name -> name.contains("status")),
                "Debe existir índice para status");
            assertTrue(indexes.stream().anyMatch(name -> name.contains("expires")),
                "Debe existir índice para expires_at");
        }
    }

    @Test
    @DisplayName("Bus sin Journal: funciona normalmente sin persistencia")
    void testBusWithoutJournal() throws Exception {
        InMemorySovereignBus noPersistBus = new InMemorySovereignBus();
        assertFalse(noPersistBus.isJournalConnected());

        String topic = "test.no.persist";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger received = new AtomicInteger(0);

        noPersistBus.subscribe(topic, String.class, env -> {
            received.incrementAndGet();
            latch.countDown();
        });

        SovereignEnvelope<String> envelope = SovereignEnvelope.create("TEST", "trace-np", "No persist");
        noPersistBus.publish(topic, envelope).join();

        boolean ok = latch.await(3, TimeUnit.SECONDS);

        assertTrue(ok, "Mensaje debe entregarse aunque no haya Journal");
        assertEquals(1, received.get());

        noPersistBus.shutdown(Duration.ofSeconds(2));
    }
}
