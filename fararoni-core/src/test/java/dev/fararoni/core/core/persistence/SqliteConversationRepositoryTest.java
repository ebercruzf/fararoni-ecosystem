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

import dev.fararoni.core.model.Message;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SqliteConversationRepository Tests")
class SqliteConversationRepositoryTest {
    @TempDir
    Path tempDir;

    private SqliteConversationRepository repo;
    private Path dbPath;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("test_conversations.db");
        repo = new SqliteConversationRepository(dbPath);
    }

    @AfterEach
    void tearDown() {
        if (repo != null) {
            repo.close();
        }
    }

    @Nested
    @DisplayName("Inicializacion")
    class InitializationTests {
        @Test
        @DisplayName("Debe crear tabla e indices al inicializar")
        void shouldCreateTableOnInit() throws Exception {
            assertTrue(repo.isAvailable(), "Repositorio debe estar disponible");

            List<Message> history = repo.getHistory("TEST_SESSION", 10);
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("Debe soportar inicializacion con Path")
        void shouldInitWithPath() throws Exception {
            Path otherPath = tempDir.resolve("other_db.db");
            try (SqliteConversationRepository otherRepo = new SqliteConversationRepository(otherPath)) {
                assertTrue(otherRepo.isAvailable());
            }
        }

        @Test
        @DisplayName("Debe crear directorio padre si no existe")
        void shouldCreateParentDirectory() throws Exception {
            Path nestedPath = tempDir.resolve("nested/dir/conversations.db");
            try (SqliteConversationRepository nestedRepo = new SqliteConversationRepository(nestedPath)) {
                assertTrue(nestedRepo.isAvailable());
            }
        }
    }

    @Nested
    @DisplayName("Operaciones CRUD")
    class CrudTests {
        @Test
        @DisplayName("Debe guardar y recuperar mensaje de usuario")
        void shouldSaveAndRetrieveUserMessage() {
            String sessionId = "WHA_521999123456";
            Message userMsg = Message.user("Hola, como estas?");

            repo.saveMessage(sessionId, userMsg);

            List<Message> history = repo.getHistory(sessionId, 10);
            assertEquals(1, history.size());
            assertEquals("user", history.get(0).role());
            assertEquals("Hola, como estas?", history.get(0).content());
        }

        @Test
        @DisplayName("Debe guardar y recuperar mensaje de asistente")
        void shouldSaveAndRetrieveAssistantMessage() {
            String sessionId = "TEL_887273645";
            Message assistantMsg = Message.assistant("Hola! Estoy bien, gracias.");

            repo.saveMessage(sessionId, assistantMsg);

            List<Message> history = repo.getHistory(sessionId, 10);
            assertEquals(1, history.size());
            assertEquals("assistant", history.get(0).role());
        }

        @Test
        @DisplayName("Debe mantener orden cronologico (mas antiguo primero)")
        void shouldMaintainChronologicalOrder() {
            String sessionId = "CLI_DEV";

            repo.saveMessage(sessionId, Message.user("Primero"));
            repo.saveMessage(sessionId, Message.assistant("Segundo"));
            repo.saveMessage(sessionId, Message.user("Tercero"));

            List<Message> history = repo.getHistory(sessionId, 10);
            assertEquals(3, history.size());
            assertEquals("Primero", history.get(0).content());
            assertEquals("Segundo", history.get(1).content());
            assertEquals("Tercero", history.get(2).content());
        }

        @Test
        @DisplayName("Debe limpiar historial de sesion")
        void shouldClearSession() {
            String sessionId = "WHA_TEST";

            repo.saveMessage(sessionId, Message.user("Mensaje 1"));
            repo.saveMessage(sessionId, Message.assistant("Mensaje 2"));

            repo.clear(sessionId);

            List<Message> history = repo.getHistory(sessionId, 10);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("Debe respetar limite de mensajes")
        void shouldRespectLimit() {
            String sessionId = "LIMIT_TEST";

            for (int i = 1; i <= 5; i++) {
                repo.saveMessage(sessionId, Message.user("Mensaje " + i));
            }

            List<Message> limitedHistory = repo.getHistory(sessionId, 3);
            assertEquals(3, limitedHistory.size());
            assertEquals("Mensaje 3", limitedHistory.get(0).content());
            assertEquals("Mensaje 4", limitedHistory.get(1).content());
            assertEquals("Mensaje 5", limitedHistory.get(2).content());
        }
    }

    @Nested
    @DisplayName("Aislamiento Multi-tenant")
    class MultiTenantTests {
        @Test
        @DisplayName("Sesiones diferentes deben tener historiales aislados")
        void shouldIsolateSessions() {
            String session1 = "WHA_USER_A";
            String session2 = "WHA_USER_B";
            String session3 = "TERMINAL_DEV";

            repo.saveMessage(session1, Message.user("Mensaje de Usuario A"));
            repo.saveMessage(session2, Message.user("Mensaje de Usuario B"));
            repo.saveMessage(session3, Message.user("Mensaje de Terminal"));

            List<Message> history1 = repo.getHistory(session1, 10);
            List<Message> history2 = repo.getHistory(session2, 10);
            List<Message> history3 = repo.getHistory(session3, 10);

            assertEquals(1, history1.size());
            assertEquals(1, history2.size());
            assertEquals(1, history3.size());

            assertEquals("Mensaje de Usuario A", history1.get(0).content());
            assertEquals("Mensaje de Usuario B", history2.get(0).content());
            assertEquals("Mensaje de Terminal", history3.get(0).content());
        }

        @Test
        @DisplayName("Limpiar una sesion no debe afectar otras")
        void clearShouldNotAffectOtherSessions() {
            String session1 = "SESSION_A";
            String session2 = "SESSION_B";

            repo.saveMessage(session1, Message.user("Mensaje A"));
            repo.saveMessage(session2, Message.user("Mensaje B"));

            repo.clear(session1);

            assertTrue(repo.getHistory(session1, 10).isEmpty());
            assertEquals(1, repo.getHistory(session2, 10).size());
        }
    }

    @Nested
    @DisplayName("Auto-pruning")
    class PruningTests {
        @Test
        @DisplayName("Debe mantener maximo 10 mensajes por sesion")
        void shouldPruneOldMessages() {
            String sessionId = "PRUNE_TEST";

            for (int i = 1; i <= 15; i++) {
                repo.saveMessage(sessionId, Message.user("Mensaje " + i));
            }

            List<Message> history = repo.getHistory(sessionId, 20);
            assertTrue(history.size() <= 10,
                "Debe haber maximo 10 mensajes, pero hay: " + history.size());

            if (!history.isEmpty()) {
                String lastContent = history.get(history.size() - 1).content();
                assertEquals("Mensaje 15", lastContent);
            }
        }

        @Test
        @DisplayName("trimByTokens debe ejecutar pruning")
        void trimByTokensShouldPrune() {
            String sessionId = "TRIM_TEST";

            for (int i = 1; i <= 12; i++) {
                repo.saveMessage(sessionId, Message.user("Mensaje " + i));
            }

            repo.trimByTokens(sessionId, 1000);

            List<Message> history = repo.getHistory(sessionId, 20);
            assertTrue(history.size() <= 10);
        }
    }

    @Nested
    @DisplayName("Utilidades")
    class UtilityTests {
        @Test
        @DisplayName("countMessages debe retornar cantidad correcta")
        void shouldCountMessages() {
            String sessionId = "COUNT_TEST";

            assertEquals(0, repo.countMessages(sessionId));

            repo.saveMessage(sessionId, Message.user("Uno"));
            assertEquals(1, repo.countMessages(sessionId));

            repo.saveMessage(sessionId, Message.assistant("Dos"));
            assertEquals(2, repo.countMessages(sessionId));
        }

        @Test
        @DisplayName("getStats debe retornar estadisticas")
        void shouldReturnStats() {
            repo.saveMessage("SESSION_1", Message.user("Test 1"));
            repo.saveMessage("SESSION_2", Message.user("Test 2"));

            SqliteConversationRepository.Stats stats = repo.getStats();

            assertEquals(2, stats.totalMessages());
            assertEquals(2, stats.totalSessions());
            assertEquals(10, stats.maxHistoryPerSession());
            assertTrue(stats.available());
        }

        @Test
        @DisplayName("isAvailable debe retornar false despues de cerrar")
        void shouldReturnFalseAfterClose() {
            assertTrue(repo.isAvailable());
            repo.close();
            assertFalse(repo.isAvailable());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        @Test
        @DisplayName("Debe manejar sessionId null gracefully")
        void shouldHandleNullSessionId() {
            assertDoesNotThrow(() -> repo.saveMessage(null, Message.user("Test")));
            assertDoesNotThrow(() -> repo.getHistory(null, 10));
            assertDoesNotThrow(() -> repo.clear(null));
        }

        @Test
        @DisplayName("Debe manejar mensaje null gracefully")
        void shouldHandleNullMessage() {
            assertDoesNotThrow(() -> repo.saveMessage("TEST", null));
        }

        @Test
        @DisplayName("Debe manejar contenido con caracteres especiales")
        void shouldHandleSpecialCharacters() {
            String sessionId = "SPECIAL_TEST";
            String content = "Mensaje con 'comillas', \"dobles\", y emoji: \uD83D\uDE00";

            repo.saveMessage(sessionId, Message.user(content));

            List<Message> history = repo.getHistory(sessionId, 10);
            assertEquals(1, history.size());
            assertEquals(content, history.get(0).content());
        }

        @Test
        @DisplayName("Debe manejar contenido muy largo")
        void shouldHandleLongContent() {
            String sessionId = "LONG_TEST";
            String longContent = "X".repeat(10000);

            repo.saveMessage(sessionId, Message.user(longContent));

            List<Message> history = repo.getHistory(sessionId, 10);
            assertEquals(1, history.size());
            assertEquals(10000, history.get(0).content().length());
        }
    }
}
