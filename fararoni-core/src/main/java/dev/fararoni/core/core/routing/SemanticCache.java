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
package dev.fararoni.core.core.routing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SemanticCache {
    private static final Logger LOG = Logger.getLogger(SemanticCache.class.getName());

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.85;

    private static final long DEFAULT_TTL_MS = 60 * 60 * 1000;

    private static final int DEFAULT_MAX_ENTRIES = 1000;

    private final double similarityThreshold;
    private final long ttlMs;
    private final int maxEntries;

    private final Map<String, CachedDecision> cache = new ConcurrentHashMap<>();

    private long hits = 0;
    private long misses = 0;

    public SemanticCache() {
        this(DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_TTL_MS, DEFAULT_MAX_ENTRIES);
    }

    public SemanticCache(double similarityThreshold, long ttlMs, int maxEntries) {
        this.similarityThreshold = Math.max(0.0, Math.min(1.0, similarityThreshold));
        this.ttlMs = Math.max(1000, ttlMs);
        this.maxEntries = Math.max(10, maxEntries);
    }

    public Optional<RoutingPlan> findSimilar(String query) {
        if (query == null || query.isBlank()) {
            misses++;
            return Optional.empty();
        }

        long startTime = System.nanoTime();
        String normalized = normalize(query);
        Set<String> queryWords = tokenize(normalized);

        CachedDecision exact = cache.get(normalized);
        if (exact != null && !exact.isExpired(ttlMs)) {
            hits++;
            return Optional.of(rebuildWithCacheSource(exact.plan(), startTime));
        }

        CachedDecision bestMatch = null;
        double bestSimilarity = 0.0;

        for (var entry : cache.entrySet()) {
            CachedDecision cached = entry.getValue();

            if (cached.isExpired(ttlMs)) {
                continue;
            }

            Set<String> cachedWords = tokenize(entry.getKey());
            double similarity = jaccardSimilarity(queryWords, cachedWords);

            if (similarity >= similarityThreshold && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = cached;
            }
        }

        if (bestMatch != null) {
            hits++;
            LOG.fine("[SemanticCache] Hit with similarity=" + String.format("%.2f", bestSimilarity));
            return Optional.of(rebuildWithCacheSource(bestMatch.plan(), startTime));
        }

        misses++;
        return Optional.empty();
    }

    public void store(String query, RoutingPlan plan) {
        if (query == null || query.isBlank() || plan == null) {
            return;
        }

        if (plan.decisionSource() == RoutingPlan.DecisionSource.DEFAULT_FALLBACK) {
            return;
        }

        if (cache.size() >= maxEntries) {
            evictOldest();
        }

        String normalized = normalize(query);
        cache.put(normalized, new CachedDecision(plan, System.currentTimeMillis()));

        LOG.fine("[SemanticCache] Stored: " + normalized.substring(0, Math.min(50, normalized.length())));
    }

    public int cleanup() {
        int before = cache.size();
        cache.entrySet().removeIf(e -> e.getValue().isExpired(ttlMs));
        int removed = before - cache.size();

        if (removed > 0) {
            LOG.fine("[SemanticCache] Cleanup: removed " + removed + " expired entries");
        }

        return removed;
    }

    public void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
        LOG.info("[SemanticCache] Cache cleared");
    }

    public int size() {
        return cache.size();
    }

    public CacheStats getStats() {
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        return new CacheStats(cache.size(), hits, misses, hitRate, similarityThreshold);
    }

    public String getStatsString() {
        CacheStats stats = getStats();
        return String.format("SemanticCache [size=%d, hits=%d, misses=%d, rate=%.1f%%, threshold=%.2f]",
            stats.size(), stats.hits(), stats.misses(), stats.hitRate() * 100, stats.threshold());
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }

    private String normalize(String query) {
        return query.toLowerCase()
            .replaceAll("[^a-záéíóúüñ0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private Set<String> tokenize(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(normalized.split("\\s+")));
    }

    private RoutingPlan rebuildWithCacheSource(RoutingPlan original, long startNano) {
        long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;

        return RoutingPlan.builder()
            .target(original.target())
            .intent(original.detectedIntent())
            .complexity(original.complexityScore())
            .requiresInternet(original.requiresInternet())
            .reasoning("Cache hit: " + original.reasoning())
            .source(RoutingPlan.DecisionSource.LAYER_1_SEMANTIC)
            .timeMs(elapsedMs)
            .build();
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (var entry : cache.entrySet()) {
            if (entry.getValue().timestamp() < oldestTime) {
                oldestTime = entry.getValue().timestamp();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
            LOG.fine("[SemanticCache] Evicted oldest entry");
        }
    }

    private record CachedDecision(RoutingPlan plan, long timestamp) {
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    public record CacheStats(
        int size,
        long hits,
        long misses,
        double hitRate,
        double threshold
    ) {}
}
