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
package dev.fararoni.bus.agent.api.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionHandle record.
 */
@DisplayName("SessionHandle")
class SessionHandleTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("create() generates random session ID")
        void createGeneratesRandomSessionId() {
            SessionHandle session1 = SessionHandle.create();
            SessionHandle session2 = SessionHandle.create();

            assertNotNull(session1.sessionId());
            assertNotNull(session2.sessionId());
            assertNotEquals(session1.sessionId(), session2.sessionId());
            assertFalse(session1.sessionId().isBlank());
        }

        @Test
        @DisplayName("withId() creates session with specific ID")
        void withIdCreatesSessionWithSpecificId() {
            SessionHandle session = SessionHandle.withId("my-session-123");

            assertEquals("my-session-123", session.sessionId());
            assertNotNull(session.createdAt());
            assertNotNull(session.lastActiveAt());
        }

        @Test
        @DisplayName("withExpiry() creates session with expiration")
        void withExpiryCreatesSessionWithExpiration() {
            Instant expiry = Instant.now().plus(1, ChronoUnit.HOURS);
            SessionHandle session = SessionHandle.withExpiry("session-1", expiry);

            assertEquals("session-1", session.sessionId());
            assertEquals(expiry, session.expiresAt());
        }

        @Test
        @DisplayName("withMetadata() creates session with metadata")
        void withMetadataCreatesSessionWithMetadata() {
            Map<String, Object> metadata = Map.of("user", "alice", "role", "admin");
            SessionHandle session = SessionHandle.withMetadata("session-1", metadata);

            assertEquals("alice", session.getMeta("user"));
            assertEquals("admin", session.getMeta("role"));
        }
    }

    @Nested
    @DisplayName("Compact Constructor Validation")
    class CompactConstructorValidation {

        @Test
        @DisplayName("generates ID when null")
        void generatesIdWhenNull() {
            SessionHandle session = new SessionHandle(null, null, null, null, null);

            assertNotNull(session.sessionId());
            assertFalse(session.sessionId().isBlank());
        }

        @Test
        @DisplayName("generates ID when blank")
        void generatesIdWhenBlank() {
            SessionHandle session = new SessionHandle("  ", null, null, null, null);

            assertNotNull(session.sessionId());
            assertFalse(session.sessionId().isBlank());
        }

        @Test
        @DisplayName("sets createdAt when null")
        void setsCreatedAtWhenNull() {
            SessionHandle session = new SessionHandle("test", null, null, null, null);

            assertNotNull(session.createdAt());
        }

        @Test
        @DisplayName("sets lastActiveAt to createdAt when null")
        void setsLastActiveAtToCreatedAtWhenNull() {
            SessionHandle session = new SessionHandle("test", Instant.now(), null, null, null);

            assertNotNull(session.lastActiveAt());
            assertEquals(session.createdAt(), session.lastActiveAt());
        }

        @Test
        @DisplayName("creates empty metadata when null")
        void createsEmptyMetadataWhenNull() {
            SessionHandle session = new SessionHandle("test", null, null, null, null);

            assertNotNull(session.metadata());
            assertTrue(session.metadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("Expiration")
    class Expiration {

        @Test
        @DisplayName("isExpired() returns false when no expiry set")
        void isExpiredReturnsFalseWhenNoExpiry() {
            SessionHandle session = SessionHandle.create();

            assertFalse(session.isExpired());
            assertTrue(session.isValid());
        }

        @Test
        @DisplayName("isExpired() returns false for future expiry")
        void isExpiredReturnsFalseForFutureExpiry() {
            Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
            SessionHandle session = SessionHandle.withExpiry("session-1", future);

            assertFalse(session.isExpired());
            assertTrue(session.isValid());
        }

        @Test
        @DisplayName("isExpired() returns true for past expiry")
        void isExpiredReturnsTrueForPastExpiry() {
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
            SessionHandle session = SessionHandle.withExpiry("session-1", past);

            assertTrue(session.isExpired());
            assertFalse(session.isValid());
        }
    }

    @Nested
    @DisplayName("Timing Methods")
    class TimingMethods {

        @Test
        @DisplayName("getAgeMs() returns positive value")
        void getAgeMsReturnsPositiveValue() throws InterruptedException {
            SessionHandle session = SessionHandle.create();
            Thread.sleep(10); // Wait a bit

            assertTrue(session.getAgeMs() >= 0);
        }

        @Test
        @DisplayName("getIdleTimeMs() returns positive value")
        void getIdleTimeMsReturnsPositiveValue() throws InterruptedException {
            SessionHandle session = SessionHandle.create();
            Thread.sleep(10); // Wait a bit

            assertTrue(session.getIdleTimeMs() >= 0);
        }

        @Test
        @DisplayName("touch() updates lastActiveAt")
        void touchUpdatesLastActiveAt() throws InterruptedException {
            SessionHandle original = SessionHandle.create();
            Thread.sleep(10);

            SessionHandle touched = original.touch();

            assertTrue(touched.lastActiveAt().isAfter(original.lastActiveAt()));
            assertEquals(original.sessionId(), touched.sessionId());
            assertEquals(original.createdAt(), touched.createdAt());
        }
    }

    @Nested
    @DisplayName("Metadata Access")
    class MetadataAccess {

        @Test
        @DisplayName("getMeta() returns correct value")
        void getMetaReturnsCorrectValue() {
            SessionHandle session = SessionHandle.withMetadata("s1", Map.of(
                "user", "alice",
                "count", 42
            ));

            assertEquals("alice", session.<String>getMeta("user"));
            assertEquals(42, session.<Integer>getMeta("count"));
        }

        @Test
        @DisplayName("getMeta() returns null for missing key")
        void getMetaReturnsNullForMissingKey() {
            SessionHandle session = SessionHandle.create();

            assertNull(session.getMeta("nonexistent"));
        }

        @Test
        @DisplayName("getMeta() with default returns value when present")
        void getMetaWithDefaultReturnsValueWhenPresent() {
            SessionHandle session = SessionHandle.withMetadata("s1", Map.of("key", "value"));

            assertEquals("value", session.getMeta("key", "default"));
        }

        @Test
        @DisplayName("getMeta() with default returns default when missing")
        void getMetaWithDefaultReturnsDefaultWhenMissing() {
            SessionHandle session = SessionHandle.create();

            assertEquals("default", session.getMeta("key", "default"));
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("metadata map is immutable")
        void metadataMapIsImmutable() {
            SessionHandle session = SessionHandle.withMetadata("s1", Map.of("key", "value"));

            assertThrows(UnsupportedOperationException.class, () ->
                session.metadata().put("new", "value")
            );
        }

        @Test
        @DisplayName("touch() returns new instance")
        void touchReturnsNewInstance() {
            SessionHandle original = SessionHandle.create();
            SessionHandle touched = original.touch();

            assertNotSame(original, touched);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("sessions with same data are equal")
        void sessionsWithSameDataAreEqual() {
            Instant now = Instant.now();
            SessionHandle a = new SessionHandle("s1", now, now, null, Map.of());
            SessionHandle b = new SessionHandle("s1", now, now, null, Map.of());

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("sessions with different IDs are not equal")
        void sessionsWithDifferentIdsAreNotEqual() {
            SessionHandle a = SessionHandle.withId("session-1");
            SessionHandle b = SessionHandle.withId("session-2");

            assertNotEquals(a, b);
        }
    }
}
