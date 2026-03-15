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
package dev.fararoni.core.core.mission.engine;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class FileGenerationRetryQueue {
    private static final Logger LOG = Logger.getLogger(FileGenerationRetryQueue.class.getName());

    private final int maxRetries;
    private final long baseDelayMs;
    private final double backoffMultiplier;

    private final Queue<RetryEntry> pendingRetries = new ConcurrentLinkedQueue<>();

    private final Map<String, AtomicInteger> retryCountByFile = new ConcurrentHashMap<>();

    private final Set<String> permanentlyFailed = ConcurrentHashMap.newKeySet();

    private final Set<String> successfulAfterRetry = ConcurrentHashMap.newKeySet();

    public FileGenerationRetryQueue() {
        this(2, 1000, 2.0);
    }

    public FileGenerationRetryQueue(int maxRetries, long baseDelayMs, double backoffMultiplier) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.backoffMultiplier = backoffMultiplier;

        LOG.info("RetryQueue initialized: maxRetries=" + maxRetries +
                ", baseDelay=" + baseDelayMs + "ms, backoff=" + backoffMultiplier + "x");
    }

    public boolean enqueue(String filePath, String description, String errorReason, int priority) {
        int currentCount = retryCountByFile
            .computeIfAbsent(filePath, k -> new AtomicInteger(0))
            .incrementAndGet();

        if (currentCount > maxRetries) {
            LOG.warning("Max retries exceeded for: " + filePath +
                       " (" + currentCount + "/" + maxRetries + ")");
            permanentlyFailed.add(filePath);
            return false;
        }

        long delay = (long) (baseDelayMs * Math.pow(backoffMultiplier, currentCount - 1));

        RetryEntry entry = new RetryEntry(
            filePath,
            description,
            errorReason,
            currentCount,
            priority,
            Instant.now().plusMillis(delay)
        );

        pendingRetries.add(entry);

        LOG.info("Enqueued for retry: " + filePath +
                " (attempt " + currentCount + "/" + maxRetries +
                ", delay=" + delay + "ms)");

        return true;
    }

    public boolean enqueue(String filePath, String description, String errorReason) {
        return enqueue(filePath, description, errorReason, 0);
    }

    public Optional<RetryEntry> dequeue() {
        Instant now = Instant.now();

        return pendingRetries.stream()
            .filter(e -> e.readyAt().isBefore(now) || e.readyAt().equals(now))
            .max(Comparator.comparingInt(RetryEntry::priority))
            .map(entry -> {
                pendingRetries.remove(entry);
                return entry;
            });
    }

    public List<RetryEntry> dequeueAll() {
        Instant now = Instant.now();
        List<RetryEntry> ready = new ArrayList<>();

        pendingRetries.removeIf(entry -> {
            if (entry.readyAt().isBefore(now) || entry.readyAt().equals(now)) {
                ready.add(entry);
                return true;
            }
            return false;
        });

        ready.sort(Comparator.comparingInt(RetryEntry::priority).reversed());

        return ready;
    }

    public void notifySuccess(String filePath) {
        successfulAfterRetry.add(filePath);
        LOG.info("Retry SUCCESS: " + filePath);
    }

    public void notifyFailure(String filePath, String description, String newError) {
        int count = retryCountByFile.getOrDefault(filePath, new AtomicInteger(0)).get();
        if (count < maxRetries) {
            enqueue(filePath, description, newError);
        } else {
            permanentlyFailed.add(filePath);
            LOG.warning("Retry FAILED PERMANENTLY: " + filePath);
        }
    }

    public boolean hasPendingRetries() {
        return !pendingRetries.isEmpty();
    }

    public int getPendingCount() {
        return pendingRetries.size();
    }

    public Set<String> getPermanentlyFailed() {
        return Set.copyOf(permanentlyFailed);
    }

    public Set<String> getSuccessfulAfterRetry() {
        return Set.copyOf(successfulAfterRetry);
    }

    public int getRetryCount(String filePath) {
        AtomicInteger count = retryCountByFile.get(filePath);
        return count != null ? count.get() : 0;
    }

    public boolean isPermanentlyFailed(String filePath) {
        return permanentlyFailed.contains(filePath);
    }

    public void clear() {
        pendingRetries.clear();
        retryCountByFile.clear();
        permanentlyFailed.clear();
        successfulAfterRetry.clear();
        LOG.info("RetryQueue cleared");
    }

    public RetryMetrics getMetrics() {
        int totalRetried = retryCountByFile.size();
        int successful = successfulAfterRetry.size();
        int failed = permanentlyFailed.size();
        int pending = pendingRetries.size();

        return new RetryMetrics(totalRetried, successful, failed, pending);
    }

    @Override
    public String toString() {
        RetryMetrics m = getMetrics();
        return String.format(
            "RetryQueue{pending=%d, retried=%d, success=%d, failed=%d}",
            m.pending(), m.totalRetried(), m.successful(), m.failed()
        );
    }

    public record RetryEntry(
        String filePath,
        String description,
        String errorReason,
        int attemptNumber,
        int priority,
        Instant readyAt
    ) {
        public boolean isReady() {
            return Instant.now().isAfter(readyAt) || Instant.now().equals(readyAt);
        }
    }

    public record RetryMetrics(
        int totalRetried,
        int successful,
        int failed,
        int pending
    ) {
        public double successRate() {
            int completed = successful + failed;
            if (completed == 0) return 1.0;
            return (double) successful / completed;
        }
    }
}
