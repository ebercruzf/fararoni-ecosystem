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
package dev.fararoni.bus.gateway.rest.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class RateLimiter {

    private static final Logger LOG = Logger.getLogger(RateLimiter.class.getName());

    /** Default bucket capacity (burst size) */
    public static final int DEFAULT_CAPACITY = 100;

    /** Default refill rate (tokens per second) */
    public static final int DEFAULT_REFILL_RATE = 10;

    /** Cleanup interval for idle buckets (5 minutes) */
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;

    /** Max idle time before bucket is removed (10 minutes) */
    private static final long MAX_IDLE_MS = 10 * 60 * 1000;

    private final int capacity;
    private final int refillRate;
    private final Map<String, TokenBucket> buckets;
    private final AtomicLong lastCleanup;

    // Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong throttledRequests = new AtomicLong(0);

    /**
     * Creates a rate limiter with default settings.
     * Capacity: 100, Refill Rate: 10/sec
     */
    public RateLimiter() {
        this(DEFAULT_CAPACITY, DEFAULT_REFILL_RATE);
    }

    /**
     * Creates a rate limiter with custom settings.
     *
     * @param capacity   maximum burst size (tokens)
     * @param refillRate tokens added per second
     */
    public RateLimiter(int capacity, int refillRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillRate <= 0) {
            throw new IllegalArgumentException("refillRate must be positive");
        }
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.buckets = new ConcurrentHashMap<>();
        this.lastCleanup = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Attempts to acquire a token for the given client.
     *
     * <p>This method is thread-safe and non-blocking.</p>
     *
     * @param clientId client identifier (typically IP address)
     * @return true if token acquired, false if rate limited
     */
    public boolean tryAcquire(String clientId) {
        totalRequests.incrementAndGet();

        // Periodic cleanup of idle buckets
        maybeCleanup();

        // Get or create bucket for this client
        TokenBucket bucket = buckets.computeIfAbsent(
            clientId,
            k -> new TokenBucket(capacity, refillRate)
        );

        boolean acquired = bucket.tryAcquire();
        if (!acquired) {
            throttledRequests.incrementAndGet();
            LOG.fine("[RATE-LIMITER] Throttled request from: " + clientId);
        }
        return acquired;
    }

    /**
     * Checks if a client is currently being throttled.
     *
     * @param clientId client identifier
     * @return true if client has no tokens available
     */
    public boolean isThrottling(String clientId) {
        TokenBucket bucket = buckets.get(clientId);
        return bucket != null && bucket.getAvailableTokens() <= 0;
    }

    /**
     * Returns the number of available tokens for a client.
     *
     * @param clientId client identifier
     * @return available tokens (0 if throttled)
     */
    public int getAvailableTokens(String clientId) {
        TokenBucket bucket = buckets.get(clientId);
        return bucket != null ? bucket.getAvailableTokens() : capacity;
    }

    /**
     * Resets the bucket for a specific client.
     *
     * @param clientId client identifier
     */
    public void reset(String clientId) {
        buckets.remove(clientId);
    }

    /**
     * Clears all buckets.
     */
    public void resetAll() {
        buckets.clear();
    }

    /**
     * Returns total requests processed.
     *
     * @return total request count
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * Returns total throttled requests.
     *
     * @return throttled request count
     */
    public long getThrottledRequests() {
        return throttledRequests.get();
    }

    /**
     * Returns the throttle rate (percentage).
     *
     * @return throttle rate (0.0 to 1.0)
     */
    public double getThrottleRate() {
        long total = totalRequests.get();
        return total > 0 ? (double) throttledRequests.get() / total : 0.0;
    }

    /**
     * Returns the number of active client buckets.
     *
     * @return number of tracked clients
     */
    public int getActiveClientCount() {
        return buckets.size();
    }

    /**
     * Periodically removes idle buckets to prevent memory leaks.
     */
    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();

        if (now - last > CLEANUP_INTERVAL_MS) {
            if (lastCleanup.compareAndSet(last, now)) {
                int removed = 0;
                var iterator = buckets.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (now - entry.getValue().getLastAccessTime() > MAX_IDLE_MS) {
                        iterator.remove();
                        removed++;
                    }
                }
                if (removed > 0) {
                    LOG.fine("[RATE-LIMITER] Cleaned up " + removed + " idle buckets");
                }
            }
        }
    }

    /**
     * Token bucket for a single client.
     */
    private static class TokenBucket {
        private final int capacity;
        private final double refillRate; // tokens per millisecond
        private double tokens;
        private long lastRefillTime;
        private long lastAccessTime;

        TokenBucket(int capacity, int refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRate = refillRatePerSecond / 1000.0;
            this.tokens = capacity; // Start full
            this.lastRefillTime = System.currentTimeMillis();
            this.lastAccessTime = this.lastRefillTime;
        }

        synchronized boolean tryAcquire() {
            refill();
            lastAccessTime = System.currentTimeMillis();

            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        synchronized int getAvailableTokens() {
            refill();
            return (int) tokens;
        }

        long getLastAccessTime() {
            return lastAccessTime;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;

            if (elapsed > 0) {
                double newTokens = elapsed * refillRate;
                tokens = Math.min(capacity, tokens + newTokens);
                lastRefillTime = now;
            }
        }
    }
}
