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
package dev.fararoni.core.core.ninja;

import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SpeculativeCache {
    private static final Logger LOG = Logger.getLogger(SpeculativeCache.class.getName());

    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final int maxSize;
    private final Duration defaultTtl;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private long hits = 0;
    private long misses = 0;
    private long evictions = 0;

    private SpeculativeCache(Builder builder) {
        this.cache = new ConcurrentHashMap<>();
        this.maxSize = builder.maxSize;
        this.defaultTtl = builder.defaultTtl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SpeculativeCache createDefault() {
        return builder().build();
    }

    public void put(ToolRequest request, ToolResponse response, Duration executionTime) {
        if (request == null || response == null) {
            return;
        }

        String key = computeKey(request);
        CacheEntry entry = new CacheEntry(
            response,
            Instant.now(),
            Instant.now(),
            executionTime,
            0,
            false
        );

        lock.writeLock().lock();
        try {
            if (cache.size() >= maxSize) {
                evictLRU();
            }

            cache.put(key, entry);
            LOG.fine(() -> "[Cache] Stored: " + key.substring(0, Math.min(20, key.length())));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(ToolRequest request, ToolResponse response, Duration executionTime, Duration ttl) {
        put(request, response, executionTime);
    }

    public Optional<ToolResponse> get(ToolRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        String key = computeKey(request);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            misses++;
            return Optional.empty();
        }

        if (isExpired(entry)) {
            cache.remove(key);
            misses++;
            evictions++;
            return Optional.empty();
        }

        hits++;
        CacheEntry updated = entry.withAccess();
        cache.put(key, updated);

        return Optional.of(entry.response());
    }

    public boolean contains(ToolRequest request) {
        if (request == null) {
            return false;
        }

        String key = computeKey(request);
        CacheEntry entry = cache.get(key);

        if (entry == null) {
            return false;
        }

        if (isExpired(entry)) {
            cache.remove(key);
            return false;
        }

        return true;
    }

    public void invalidate(ToolRequest request) {
        if (request == null) {
            return;
        }
        cache.remove(computeKey(request));
    }

    public void invalidateSkill(String skillName) {
        cache.keySet().removeIf(key -> key.startsWith(skillName + ":"));
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            hits = 0;
            misses = 0;
            evictions = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void putSpeculative(ToolRequest request, ToolResponse response) {
        if (request == null || response == null) {
            return;
        }

        String key = computeKey(request);
        CacheEntry entry = new CacheEntry(
            response,
            Instant.now(),
            Instant.now(),
            Duration.ZERO,
            0,
            true
        );

        lock.writeLock().lock();
        try {
            if (cache.size() >= maxSize) {
                evictLRU();
            }
            cache.putIfAbsent(key, entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void warmUp(Map<ToolRequest, ToolResponse> predictions) {
        for (Map.Entry<ToolRequest, ToolResponse> entry : predictions.entrySet()) {
            putSpeculative(entry.getKey(), entry.getValue());
        }
        LOG.info(() -> "[Cache] Warmed up with " + predictions.size() + " predictions");
    }

    public int size() {
        return cache.size();
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public long getEvictions() {
        return evictions;
    }

    public double getHitRate() {
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("size", cache.size());
        metrics.put("maxSize", maxSize);
        metrics.put("hits", hits);
        metrics.put("misses", misses);
        metrics.put("evictions", evictions);
        metrics.put("hitRate", getHitRate());

        long speculativeCount = cache.values().stream()
            .filter(CacheEntry::isSpeculative)
            .count();
        metrics.put("speculativeEntries", speculativeCount);

        return metrics;
    }

    private String computeKey(ToolRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.toolName()).append(":");
        sb.append(request.action()).append(":");
        if (request.params() != null) {
            request.params().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append("=").append(e.getValue()).append(";"));
        }
        return sb.toString();
    }

    private boolean isExpired(CacheEntry entry) {
        Duration age = Duration.between(entry.createdAt(), Instant.now());
        return age.compareTo(defaultTtl) > 0;
    }

    private void evictLRU() {
        String toEvict = null;
        double lowestScore = Double.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            double score = entry.getValue().evictionScore();
            if (score < lowestScore) {
                lowestScore = score;
                toEvict = entry.getKey();
            }
        }

        if (toEvict != null) {
            cache.remove(toEvict);
            evictions++;
            final double finalScore = lowestScore;
            LOG.fine(() -> "[Cache] Evicted entry with score: " + finalScore);
        }
    }

    private record CacheEntry(
        ToolResponse response,
        Instant createdAt,
        Instant lastAccess,
        Duration executionTime,
        int hitCount,
        boolean isSpeculative
    ) {
        CacheEntry withAccess() {
            return new CacheEntry(response, createdAt, Instant.now(), executionTime,
                hitCount + 1, isSpeculative);
        }

        double evictionScore() {
            long timeSinceAccess = Duration.between(lastAccess, Instant.now()).toSeconds();
            double recency = 1.0 / (1.0 + timeSinceAccess);
            double speculativePenalty = isSpeculative ? 0.5 : 1.0;
            return recency * (1 + hitCount) * speculativePenalty;
        }
    }

    public static final class Builder {
        private int maxSize = 1000;
        private Duration defaultTtl = Duration.ofMinutes(5);

        private Builder() {}

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.defaultTtl = ttl;
            return this;
        }

        public SpeculativeCache build() {
            return new SpeculativeCache(this);
        }
    }
}
