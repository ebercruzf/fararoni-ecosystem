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
package dev.fararoni.core.core.resilience;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class IdempotencyFilter {

    private static final Logger LOG = Logger.getLogger(IdempotencyFilter.class.getName());

    private final Cache<String, Boolean> processedIds;

    private final Duration ttl;

    private final int maxSize;

    private final AtomicLong duplicatesBlocked = new AtomicLong(0);
    private final AtomicLong uniqueProcessed = new AtomicLong(0);

    public IdempotencyFilter(Duration ttl, int maxSize) {
        this.ttl = ttl;
        this.maxSize = maxSize;
        this.processedIds = Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(maxSize)
            .recordStats()
            .build();

        LOG.info(() -> String.format(
            "[IDEMPOTENCY] Filtro creado: TTL=%s, maxSize=%d",
            ttl, maxSize
        ));
    }

    public IdempotencyFilter() {
        this(Duration.ofMinutes(10), 10_000);
    }

    public boolean isDuplicate(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }

        Boolean existing = processedIds.getIfPresent(messageId);
        if (existing != null) {
            duplicatesBlocked.incrementAndGet();
            LOG.fine(() -> "[IDEMPOTENCY] Duplicado bloqueado: " + messageId);
            return true;
        }

        return false;
    }

    public void markProcessed(String messageId) {
        if (messageId != null && !messageId.isBlank()) {
            processedIds.put(messageId, Boolean.TRUE);
            uniqueProcessed.incrementAndGet();
        }
    }

    public boolean tryProcess(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return true;
        }

        if (processedIds.getIfPresent(messageId) != null) {
            duplicatesBlocked.incrementAndGet();
            LOG.fine(() -> "[IDEMPOTENCY] Duplicado bloqueado: " + messageId);
            return false;
        }

        processedIds.put(messageId, Boolean.TRUE);
        uniqueProcessed.incrementAndGet();
        return true;
    }

    public void clear() {
        processedIds.invalidateAll();
        LOG.info("[IDEMPOTENCY] Cache limpiada");
    }

    public void remove(String messageId) {
        if (messageId != null) {
            processedIds.invalidate(messageId);
        }
    }

    public long size() {
        return processedIds.estimatedSize();
    }

    public IdempotencyStats getStats() {
        var cacheStats = processedIds.stats();
        return new IdempotencyStats(
            uniqueProcessed.get(),
            duplicatesBlocked.get(),
            processedIds.estimatedSize(),
            cacheStats.hitRate(),
            cacheStats.evictionCount(),
            ttl,
            maxSize
        );
    }

    public record IdempotencyStats(
        long uniqueProcessed,
        long duplicatesBlocked,
        long cacheSize,
        double hitRate,
        long evictions,
        Duration ttl,
        int maxSize
    ) {
        public double deduplicationRate() {
            long total = uniqueProcessed + duplicatesBlocked;
            return total > 0 ? (double) duplicatesBlocked / total * 100 : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "IdempotencyStats[unique=%d, duplicates=%d, rate=%.1f%%, cache=%d/%d, hitRate=%.2f]",
                uniqueProcessed, duplicatesBlocked, deduplicationRate(),
                cacheSize, maxSize, hitRate * 100
            );
        }
    }
}
