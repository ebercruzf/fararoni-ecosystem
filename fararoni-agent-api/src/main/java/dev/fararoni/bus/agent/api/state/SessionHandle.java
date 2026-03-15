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

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Token representing a persistent session (connection).
 *
 * <p>FNL supports stateful connections (database pools, SSH sessions, etc.)
 * unlike MCP which is stateless and reconnects on every call. This enables
 * massive performance gains for multi-step operations.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Open a database connection
 * FNLResult<SessionHandle> result = dbSkill.connect("jdbc:postgresql://...");
 *
 * if (result.success()) {
 *     SessionHandle session = result.data();
 *
 *     // Execute 100 queries using the same connection (fast!)
 *     for (String query : queries) {
 *         dbSkill.query(session.sessionId(), query);
 *     }
 *
 *     // Keep alive if long operation
 *     dbSkill.ping(session.sessionId());
 *
 *     // Close when done
 *     dbSkill.disconnect(session.sessionId());
 * }
 * }</pre>
 *
 * <h2>Performance Comparison</h2>
 * <table border="1">
 *   <caption>MCP vs FNL session performance</caption>
 *   <tr><th>Operation</th><th>MCP (Stateless)</th><th>FNL (Stateful)</th></tr>
 *   <tr><td>100 DB queries</td><td>100 connections (200s)</td><td>1 connection (2s)</td></tr>
 *   <tr><td>SSH commands</td><td>Reconnect each time</td><td>Persistent session</td></tr>
 *   <tr><td>API calls</td><td>New auth each time</td><td>Token reuse</td></tr>
 * </table>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @param sessionId Unique identifier for this session
 * @param createdAt When the session was created
 * @param lastActiveAt When the session was last used
 * @param expiresAt When the session will expire (null = no expiry)
 * @param metadata Additional session metadata (connection URL, user, etc.)
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see StatefulSkill
 */
public record SessionHandle(
    String sessionId,
    Instant createdAt,
    Instant lastActiveAt,
    Instant expiresAt,
    Map<String, Object> metadata
) {

    /**
     * Compact constructor with validation and defaults.
     */
    public SessionHandle {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastActiveAt == null) {
            lastActiveAt = createdAt;
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a new session handle with a random ID.
     *
     * @return new session handle
     */
    public static SessionHandle create() {
        return new SessionHandle(
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now(),
            null,
            Collections.emptyMap()
        );
    }

    /**
     * Creates a session handle with specific ID.
     *
     * @param sessionId the session identifier
     * @return new session handle
     */
    public static SessionHandle withId(String sessionId) {
        return new SessionHandle(
            sessionId,
            Instant.now(),
            Instant.now(),
            null,
            Collections.emptyMap()
        );
    }

    /**
     * Creates a session handle with expiry.
     *
     * @param sessionId the session identifier
     * @param expiresAt when the session expires
     * @return new session handle
     */
    public static SessionHandle withExpiry(String sessionId, Instant expiresAt) {
        return new SessionHandle(
            sessionId,
            Instant.now(),
            Instant.now(),
            expiresAt,
            Collections.emptyMap()
        );
    }

    /**
     * Creates a session handle with metadata.
     *
     * @param sessionId the session identifier
     * @param metadata session metadata
     * @return new session handle
     */
    public static SessionHandle withMetadata(String sessionId, Map<String, Object> metadata) {
        return new SessionHandle(
            sessionId,
            Instant.now(),
            Instant.now(),
            null,
            metadata
        );
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if this session has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this session is still valid.
     *
     * @return true if valid (not expired)
     */
    public boolean isValid() {
        return !isExpired();
    }

    /**
     * Gets the session age in milliseconds.
     *
     * @return age in ms
     */
    public long getAgeMs() {
        return Instant.now().toEpochMilli() - createdAt.toEpochMilli();
    }

    /**
     * Gets the time since last activity in milliseconds.
     *
     * @return idle time in ms
     */
    public long getIdleTimeMs() {
        return Instant.now().toEpochMilli() - lastActiveAt.toEpochMilli();
    }

    /**
     * Creates a new handle with updated lastActiveAt.
     *
     * @return updated handle
     */
    public SessionHandle touch() {
        return new SessionHandle(sessionId, createdAt, Instant.now(), expiresAt, metadata);
    }

    /**
     * Gets a metadata value.
     *
     * @param key the metadata key
     * @param <T> expected type
     * @return the value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key) {
        return (T) metadata.get(key);
    }

    /**
     * Gets a metadata value with default.
     *
     * @param key the metadata key
     * @param defaultValue value to return if not found
     * @param <T> expected type
     * @return the value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getMeta(String key, T defaultValue) {
        Object value = metadata.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
