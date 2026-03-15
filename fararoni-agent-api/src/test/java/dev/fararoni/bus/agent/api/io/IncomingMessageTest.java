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
package dev.fararoni.bus.agent.api.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para IncomingMessage record (Track A - Fase A2).
 *
 * Verifica:
 * - Construcción y validación
 * - Acceso a metadata
 * - Métodos de tipo (isEmail, isSlack, etc.)
 * - Métodos de utilidad (contentPreview, uniqueId)
 */
@DisplayName("IncomingMessage - DTO Agnostico de Mensajes")
class IncomingMessageTest {

    @Nested
    @DisplayName("Construcción y Validación")
    class ConstructionTests {

        @Test
        @DisplayName("Constructor completo crea mensaje correctamente")
        void fullConstructor_createsMessageCorrectly() {
            Instant now = Instant.now();
            Map<String, String> metadata = Map.of("key", "value");

            IncomingMessage msg = new IncomingMessage(
                "email",
                "imap-soporte",
                "user@test.com",
                "Test content",
                metadata,
                now
            );

            assertEquals("email", msg.channelType());
            assertEquals("imap-soporte", msg.channelName());
            assertEquals("user@test.com", msg.sourceId());
            assertEquals("Test content", msg.content());
            assertEquals(now, msg.receivedAt());
            assertEquals("value", msg.metadata().get("key"));
        }

        @Test
        @DisplayName("Constructor simplificado usa defaults")
        void simpleConstructor_usesDefaults() {
            IncomingMessage msg = new IncomingMessage("slack", "U123", "Hello");

            assertEquals("slack", msg.channelType());
            assertEquals("slack", msg.channelName()); // Default: channelType
            assertEquals("U123", msg.sourceId());
            assertEquals("Hello", msg.content());
            assertNotNull(msg.receivedAt());
            assertTrue(msg.metadata().isEmpty());
        }

        @Test
        @DisplayName("Constructor con channelName null usa channelType como default")
        void constructor_withNullChannelName_usesChannelTypeAsDefault() {
            IncomingMessage msg = new IncomingMessage(
                "jira", null, "PROJ-123", "Content", Map.of(), Instant.now()
            );

            assertEquals("jira", msg.channelName());
        }

        @Test
        @DisplayName("Constructor con channelName vacío usa channelType como default")
        void constructor_withEmptyChannelName_usesChannelTypeAsDefault() {
            IncomingMessage msg = new IncomingMessage(
                "webhook", "  ", "src", "Content", Map.of(), Instant.now()
            );

            assertEquals("webhook", msg.channelName());
        }

        @Test
        @DisplayName("Constructor con metadata null usa Map vacío")
        void constructor_withNullMetadata_usesEmptyMap() {
            IncomingMessage msg = new IncomingMessage(
                "email", "ch", "src", "Content", null, Instant.now()
            );

            assertNotNull(msg.metadata());
            assertTrue(msg.metadata().isEmpty());
        }

        @Test
        @DisplayName("Constructor con receivedAt null usa Instant.now()")
        void constructor_withNullReceivedAt_usesNow() {
            Instant before = Instant.now();

            IncomingMessage msg = new IncomingMessage(
                "email", "ch", "src", "Content", Map.of(), null
            );

            Instant after = Instant.now();
            assertNotNull(msg.receivedAt());
            assertTrue(msg.receivedAt().compareTo(before) >= 0);
            assertTrue(msg.receivedAt().compareTo(after) <= 0);
        }

        @Test
        @DisplayName("Constructor lanza NullPointerException para channelType null")
        void constructor_withNullChannelType_throwsNPE() {
            assertThrows(NullPointerException.class, () ->
                new IncomingMessage(null, "src", "Content")
            );
        }

        @Test
        @DisplayName("Constructor lanza NullPointerException para sourceId null")
        void constructor_withNullSourceId_throwsNPE() {
            assertThrows(NullPointerException.class, () ->
                new IncomingMessage("email", null, "Content")
            );
        }

        @Test
        @DisplayName("Constructor lanza NullPointerException para content null")
        void constructor_withNullContent_throwsNPE() {
            assertThrows(NullPointerException.class, () ->
                new IncomingMessage("email", "src", null)
            );
        }

        @Test
        @DisplayName("Metadata es inmutable")
        void metadata_isImmutable() {
            IncomingMessage msg = new IncomingMessage(
                "email", "ch", "src", "Content", Map.of("key", "value"), Instant.now()
            );

            assertThrows(UnsupportedOperationException.class, () ->
                msg.metadata().put("new", "value")
            );
        }
    }

    @Nested
    @DisplayName("Acceso a Metadata")
    class MetadataAccessTests {

        private final IncomingMessage msg = new IncomingMessage(
            "email", "imap", "user@test.com", "Content",
            Map.of("subject", "Test Subject", "priority", "high"),
            Instant.now()
        );

        @Test
        @DisplayName("getMetadata() retorna valor existente")
        void getMetadata_withExistingKey_returnsValue() {
            var result = msg.getMetadata("subject");

            assertTrue(result.isPresent());
            assertEquals("Test Subject", result.get());
        }

        @Test
        @DisplayName("getMetadata() retorna empty para clave inexistente")
        void getMetadata_withMissingKey_returnsEmpty() {
            var result = msg.getMetadata("nonexistent");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("getMetadataOrDefault() retorna valor existente")
        void getMetadataOrDefault_withExistingKey_returnsValue() {
            assertEquals("high", msg.getMetadataOrDefault("priority", "normal"));
        }

        @Test
        @DisplayName("getMetadataOrDefault() retorna default para clave inexistente")
        void getMetadataOrDefault_withMissingKey_returnsDefault() {
            assertEquals("normal", msg.getMetadataOrDefault("status", "normal"));
        }

        @Test
        @DisplayName("hasMetadata() retorna true para clave existente")
        void hasMetadata_withExistingKey_returnsTrue() {
            assertTrue(msg.hasMetadata("subject"));
        }

        @Test
        @DisplayName("hasMetadata() retorna false para clave inexistente")
        void hasMetadata_withMissingKey_returnsFalse() {
            assertFalse(msg.hasMetadata("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Métodos de Tipo")
    class TypeCheckTests {

        @Test
        @DisplayName("isEmail() detecta mensajes de email")
        void isEmail_withEmailType_returnsTrue() {
            IncomingMessage msg = new IncomingMessage("email", "src", "Content");
            assertTrue(msg.isEmail());
            assertFalse(msg.isSlack());
            assertFalse(msg.isJira());
            assertFalse(msg.isWebhook());
        }

        @Test
        @DisplayName("isEmail() es case-insensitive")
        void isEmail_isCaseInsensitive() {
            assertTrue(new IncomingMessage("EMAIL", "src", "Content").isEmail());
            assertTrue(new IncomingMessage("Email", "src", "Content").isEmail());
        }

        @Test
        @DisplayName("isSlack() detecta mensajes de Slack")
        void isSlack_withSlackType_returnsTrue() {
            IncomingMessage msg = new IncomingMessage("slack", "src", "Content");
            assertTrue(msg.isSlack());
            assertFalse(msg.isEmail());
        }

        @Test
        @DisplayName("isJira() detecta mensajes de Jira")
        void isJira_withJiraType_returnsTrue() {
            IncomingMessage msg = new IncomingMessage("jira", "src", "Content");
            assertTrue(msg.isJira());
            assertFalse(msg.isEmail());
        }

        @Test
        @DisplayName("isWebhook() detecta webhooks")
        void isWebhook_withWebhookType_returnsTrue() {
            IncomingMessage msg = new IncomingMessage("webhook", "src", "Content");
            assertTrue(msg.isWebhook());
            assertFalse(msg.isEmail());
        }
    }

    @Nested
    @DisplayName("Métodos de Utilidad")
    class UtilityMethodsTests {

        @Test
        @DisplayName("contentPreview() trunca contenido largo")
        void contentPreview_withLongContent_truncates() {
            IncomingMessage msg = new IncomingMessage(
                "email", "src", "This is a very long content that should be truncated"
            );

            String preview = msg.contentPreview(20);

            assertEquals(20, preview.length());
            assertTrue(preview.endsWith("..."));
            assertEquals("This is a very lo...", preview);
        }

        @Test
        @DisplayName("contentPreview() no trunca contenido corto")
        void contentPreview_withShortContent_doesNotTruncate() {
            IncomingMessage msg = new IncomingMessage("email", "src", "Short");

            String preview = msg.contentPreview(20);

            assertEquals("Short", preview);
        }

        @Test
        @DisplayName("uniqueId() genera ID único")
        void uniqueId_generatesUniqueId() {
            Instant time = Instant.parse("2026-01-16T00:00:00Z");
            IncomingMessage msg = new IncomingMessage(
                "email", "ch", "user@test.com", "Content", Map.of(), time
            );

            String id = msg.uniqueId();

            assertTrue(id.startsWith("email:user@test.com:"));
            assertTrue(id.contains(String.valueOf(time.toEpochMilli())));
        }

        @Test
        @DisplayName("uniqueId() es diferente para mensajes diferentes")
        void uniqueId_isDifferentForDifferentMessages() {
            IncomingMessage msg1 = new IncomingMessage("email", "src1", "Content1");
            IncomingMessage msg2 = new IncomingMessage("email", "src2", "Content2");

            assertNotEquals(msg1.uniqueId(), msg2.uniqueId());
        }

        @Test
        @DisplayName("toLogString() genera string para logging")
        void toLogString_generatesLogString() {
            IncomingMessage msg = new IncomingMessage(
                "email", "imap-soporte", "user@test.com", "Hello World",
                Map.of(), Instant.now()
            );

            String log = msg.toLogString();

            assertTrue(log.contains("email"));
            assertTrue(log.contains("imap-soporte"));
            assertTrue(log.contains("user@test.com"));
            assertTrue(log.contains("contentLen=11"));
            assertFalse(log.contains("Hello World")); // No debe contener el contenido real
        }
    }

    @Nested
    @DisplayName("Inmutabilidad")
    class ImmutabilityTests {

        @Test
        @DisplayName("Record es inmutable")
        void record_isImmutable() {
            Instant time = Instant.now();
            IncomingMessage msg = new IncomingMessage(
                "email", "ch", "src", "Content", Map.of("key", "value"), time
            );

            // Verificar que los valores no cambian
            assertEquals("email", msg.channelType());
            assertEquals("ch", msg.channelName());
            assertEquals("src", msg.sourceId());
            assertEquals("Content", msg.content());
            assertEquals(time, msg.receivedAt());
        }
    }
}
