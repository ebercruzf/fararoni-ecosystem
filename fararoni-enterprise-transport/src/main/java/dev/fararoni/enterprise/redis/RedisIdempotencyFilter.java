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
package dev.fararoni.enterprise.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class RedisIdempotencyFilter implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RedisIdempotencyFilter.class);

    /** Redis key prefix for idempotency keys */
    private static final String KEY_PREFIX = "fararoni:idem:";

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final Duration ttl;

    /**
     * Creates a Redis idempotency filter.
     *
     * @param redisUrl Redis connection URL (e.g., redis://localhost:6379)
     * @param ttl Time-to-live for idempotency keys
     */
    public RedisIdempotencyFilter(String redisUrl, Duration ttl) {
        this.client = RedisClient.create(redisUrl);
        this.connection = client.connect();
        this.commands = connection.sync();
        this.ttl = ttl;

        LOG.info("[REDIS-IDEM] Connected to: {}, TTL: {}", redisUrl, ttl);
    }

    /**
     * Attempts to process a message with the given idempotency key.
     *
     * <p>Uses Redis SETNX (SET if Not eXists) for atomic check-and-set.</p>
     *
     * @param idempotencyKey Unique message identifier
     * @return true if this is the first time processing, false if duplicate
     */
    public boolean tryProcess(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true; // No key = no deduplication
        }

        String redisKey = KEY_PREFIX + idempotencyKey;

        try {
            // SETNX with TTL - atomic operation
            String result = commands.set(
                redisKey,
                "1",
                SetArgs.Builder.nx().ex(ttl)
            );

            boolean isNew = "OK".equals(result);

            if (!isNew) {
                LOG.debug("[REDIS-IDEM] Duplicate detected: {}", idempotencyKey);
            }

            return isNew;

        } catch (Exception e) {
            LOG.warn("[REDIS-IDEM] Error checking key {}: {}. Allowing message.",
                idempotencyKey, e.getMessage());
            // On error, allow processing (fail-open)
            return true;
        }
    }

    /**
     * Checks if a key exists without marking it as processed.
     *
     * @param idempotencyKey Unique message identifier
     * @return true if key exists (already processed)
     */
    public boolean exists(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }

        String redisKey = KEY_PREFIX + idempotencyKey;

        try {
            return commands.exists(redisKey) > 0;
        } catch (Exception e) {
            LOG.warn("[REDIS-IDEM] Error checking existence: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Manually marks a key as processed.
     *
     * @param idempotencyKey Unique message identifier
     */
    public void markProcessed(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String redisKey = KEY_PREFIX + idempotencyKey;

        try {
            commands.setex(redisKey, ttl.toSeconds(), "1");
        } catch (Exception e) {
            LOG.warn("[REDIS-IDEM] Error marking processed: {}", e.getMessage());
        }
    }

    /**
     * Removes an idempotency key (for testing or manual recovery).
     *
     * @param idempotencyKey Unique message identifier
     */
    public void remove(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String redisKey = KEY_PREFIX + idempotencyKey;

        try {
            commands.del(redisKey);
        } catch (Exception e) {
            LOG.warn("[REDIS-IDEM] Error removing key: {}", e.getMessage());
        }
    }

    /**
     * Gets the count of idempotency keys (approximate).
     *
     * @return Number of keys with the idempotency prefix
     */
    public long getKeyCount() {
        try {
            // Note: KEYS is not recommended for production, use SCAN for large datasets
            return commands.keys(KEY_PREFIX + "*").size();
        } catch (Exception e) {
            LOG.warn("[REDIS-IDEM] Error counting keys: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
            client.shutdown();
            LOG.info("[REDIS-IDEM] Connection closed");
        } catch (Exception e) {
            LOG.warn("[REDIS-IDEM] Error closing connection: {}", e.getMessage());
        }
    }
}
