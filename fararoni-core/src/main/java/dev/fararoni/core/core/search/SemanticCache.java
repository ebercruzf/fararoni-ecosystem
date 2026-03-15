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
package dev.fararoni.core.core.search;

import dev.fararoni.core.core.search.TheHound.EmbeddingProvider;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SemanticCache {
    private static final Logger LOG = Logger.getLogger(SemanticCache.class.getName());

    public static final double CACHE_HIT_THRESHOLD = 0.95;

    private static final long DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L;

    private static final int MAX_CACHE_SIZE = 1000;

    private static final String DEFAULT_CACHE_DIR = ".fararoni";

    private static final String CACHE_FILE_NAME = "semantic_cache.dat";

    private static final long AUTO_SAVE_INTERVAL_MS = 5 * 60 * 1000L;

    private final Map<String, CacheEntry> cacheStore = new ConcurrentHashMap<>();

    private final EmbeddingProvider embeddingProvider;

    private final long ttlMs;

    private int cacheHits = 0;
    private int cacheMisses = 0;

    private final Path persistencePath;

    private long lastAutoSaveTime = System.currentTimeMillis();

    private boolean dirty = false;

    private final boolean persistenceEnabled;

    public SemanticCache(EmbeddingProvider provider) {
        this(provider, DEFAULT_TTL_MS, false);
    }

    public SemanticCache(EmbeddingProvider provider, long ttlMs) {
        this(provider, ttlMs, false);
    }

    public SemanticCache(EmbeddingProvider provider, long ttlMs, boolean enablePersistence) {
        if (provider == null) {
            throw new IllegalArgumentException("EmbeddingProvider cannot be null");
        }
        this.embeddingProvider = provider;
        this.ttlMs = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
        this.persistenceEnabled = enablePersistence;

        if (enablePersistence) {
            this.persistencePath = resolvePersistencePath();
            loadFromDisk();
        } else {
            this.persistencePath = null;
        }
    }

    public SemanticCache(EmbeddingProvider provider, long ttlMs, Path customPath) {
        if (provider == null) {
            throw new IllegalArgumentException("EmbeddingProvider cannot be null");
        }
        this.embeddingProvider = provider;
        this.ttlMs = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
        this.persistenceEnabled = customPath != null;
        this.persistencePath = customPath;

        if (persistenceEnabled && Files.exists(customPath)) {
            loadFromDisk();
        }
    }

    public String retrieve(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return null;
        }

        float[] queryVector = embeddingProvider.getEmbedding(userQuery);
        if (queryVector == null || queryVector.length == 0) {
            return null;
        }

        long now = System.currentTimeMillis();
        CacheEntry bestMatch = null;
        double bestSimilarity = 0.0;
        String bestKey = null;

        for (Map.Entry<String, CacheEntry> entry : cacheStore.entrySet()) {
            CacheEntry cached = entry.getValue();

            if (now - cached.timestamp > ttlMs) {
                continue;
            }

            double similarity = VectorUtils.cosineSimilarity(queryVector, cached.vector);

            if (similarity >= CACHE_HIT_THRESHOLD && similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = cached;
                bestKey = entry.getKey();
            }
        }

        if (bestMatch != null) {
            cacheHits++;
            LOG.info(String.format("[CACHE] Cache Hit! (Similitud: %.2f, Key: %s)",
                    bestSimilarity, bestKey));
            return bestMatch.response;
        }

        cacheMisses++;
        return null;
    }

    public void store(String userQuery, String llmResponse) {
        if (userQuery == null || userQuery.isBlank() ||
            llmResponse == null || llmResponse.isBlank()) {
            return;
        }

        if (cacheStore.size() >= MAX_CACHE_SIZE) {
            evictOldestEntry();
        }

        float[] vector = embeddingProvider.getEmbedding(userQuery);
        if (vector == null || vector.length == 0) {
            return;
        }

        String id = UUID.randomUUID().toString();
        cacheStore.put(id, new CacheEntry(vector, llmResponse, System.currentTimeMillis()));
        dirty = true;

        LOG.fine(String.format("[CACHE] Stored new entry (Key: %s, Query length: %d)",
                id, userQuery.length()));

        autoSaveIfNeeded();
    }

    private void evictOldestEntry() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry> entry : cacheStore.entrySet()) {
            if (entry.getValue().timestamp < oldestTime) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cacheStore.remove(oldestKey);
            LOG.fine("[CACHE] Evicted oldest entry: " + oldestKey);
        }
    }

    public int cleanExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;

        var iterator = cacheStore.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().timestamp > ttlMs) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            LOG.info(String.format("[CACHE] Cleaned %d expired entries", removed));
        }

        return removed;
    }

    public void clear() {
        cacheStore.clear();
        cacheHits = 0;
        cacheMisses = 0;
        LOG.info("[CACHE] Cache cleared");
    }

    public int getCacheHits() {
        return cacheHits;
    }

    public int getCacheMisses() {
        return cacheMisses;
    }

    public double getHitRatio() {
        int total = cacheHits + cacheMisses;
        return total == 0 ? 0.0 : (double) cacheHits / total;
    }

    public int size() {
        return cacheStore.size();
    }

    public String getStatsReport() {
        return String.format(
                "[CACHE STATS] Size: %d/%d, Hits: %d, Misses: %d, Hit Ratio: %.2f%%",
                size(), MAX_CACHE_SIZE, cacheHits, cacheMisses, getHitRatio() * 100);
    }

    private Path resolvePersistencePath() {
        String userHome = System.getProperty("user.home");
        Path cacheDir = Path.of(userHome, DEFAULT_CACHE_DIR);

        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
                LOG.info("[CACHE] Created persistence directory: " + cacheDir);
            }
        } catch (IOException e) {
            LOG.warning("[CACHE] Failed to create persistence directory: " + e.getMessage());
            return null;
        }

        return cacheDir.resolve(CACHE_FILE_NAME);
    }

    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        if (persistencePath == null || !Files.exists(persistencePath)) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(persistencePath)))) {
            Map<String, SerializableCacheEntry> loaded =
                    (Map<String, SerializableCacheEntry>) ois.readObject();

            long now = System.currentTimeMillis();
            int loadedCount = 0;
            int expiredCount = 0;

            for (Map.Entry<String, SerializableCacheEntry> entry : loaded.entrySet()) {
                SerializableCacheEntry sce = entry.getValue();

                if (now - sce.timestamp > ttlMs) {
                    expiredCount++;
                    continue;
                }

                if (cacheStore.size() >= MAX_CACHE_SIZE) {
                    break;
                }

                cacheStore.put(entry.getKey(),
                        new CacheEntry(sce.vector, sce.response, sce.timestamp));
                loadedCount++;
            }

            LOG.info(String.format("[CACHE] Loaded %d entries from disk (%d expired, skipped)",
                    loadedCount, expiredCount));
        } catch (IOException | ClassNotFoundException e) {
            LOG.warning("[CACHE] Failed to load cache from disk: " + e.getMessage());
        }
    }

    public boolean saveToDisk() {
        if (!persistenceEnabled || persistencePath == null) {
            return false;
        }

        if (cacheStore.isEmpty()) {
            LOG.fine("[CACHE] Cache is empty, skipping save");
            return true;
        }

        Map<String, SerializableCacheEntry> toSave = new ConcurrentHashMap<>();
        for (Map.Entry<String, CacheEntry> entry : cacheStore.entrySet()) {
            CacheEntry ce = entry.getValue();
            toSave.put(entry.getKey(),
                    new SerializableCacheEntry(ce.vector(), ce.response(), ce.timestamp()));
        }

        Path tempFile = persistencePath.resolveSibling(CACHE_FILE_NAME + ".tmp");

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
            oos.writeObject(toSave);
            oos.flush();

            Files.move(tempFile, persistencePath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            dirty = false;
            lastAutoSaveTime = System.currentTimeMillis();

            LOG.info(String.format("[CACHE] Saved %d entries to disk", toSave.size()));
            return true;
        } catch (IOException e) {
            LOG.warning("[CACHE] Failed to save cache to disk: " + e.getMessage());
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    private void autoSaveIfNeeded() {
        if (!persistenceEnabled || !dirty) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
            saveToDisk();
        }
    }

    public void shutdown() {
        if (persistenceEnabled && dirty) {
            LOG.info("[CACHE] Shutdown - saving pending changes...");
            saveToDisk();
        }
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public boolean isDirty() {
        return dirty;
    }

    private record CacheEntry(float[] vector, String response, long timestamp) {}

    private static class SerializableCacheEntry implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        final float[] vector;
        final String response;
        final long timestamp;

        SerializableCacheEntry(float[] vector, String response, long timestamp) {
            this.vector = vector;
            this.response = response;
            this.timestamp = timestamp;
        }
    }
}
