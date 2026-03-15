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

import dev.fararoni.bus.agent.api.ToolRegistry;
import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.core.core.dispatcher.AgentDispatcher;
import dev.fararoni.core.core.dispatcher.ReflectionToolInvoker;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class NinjaDispatcher extends AgentDispatcher {
    private static final Logger LOG = Logger.getLogger(NinjaDispatcher.class.getName());

    private final SpeculativeCache speculativeCache;
    private final PriorityBlockingQueue<PrioritizedRequest> priorityQueue;
    private final Map<String, Integer> toolPriorities;
    private final int batchThreshold;
    private final Duration batchWindow;
    private final boolean speculativeEnabled;

    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long totalExecutions = 0;
    private long speculativeHits = 0;

    private NinjaDispatcher(Builder builder) {
        super(builder.registry, builder.invoker, builder.maxConcurrency, builder.defaultTimeoutMs);
        this.speculativeCache = builder.speculativeCache;
        this.priorityQueue = new PriorityBlockingQueue<>();
        this.toolPriorities = new HashMap<>(builder.toolPriorities);
        this.batchThreshold = builder.batchThreshold;
        this.batchWindow = builder.batchWindow;
        this.speculativeEnabled = builder.speculativeEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ToolResponse executeSingle(ToolRequest request) {
        totalExecutions++;

        if (speculativeCache != null && speculativeEnabled) {
            Optional<ToolResponse> cached = speculativeCache.get(request);
            if (cached.isPresent()) {
                cacheHits++;
                LOG.fine(() -> "[Ninja] Cache hit for: " + request.toolName() + "." + request.action());
                return cached.get();
            }
            cacheMisses++;
        }

        Instant start = Instant.now();
        ToolResponse response = super.executeSingle(request);
        Duration duration = Duration.between(start, Instant.now());

        if (speculativeCache != null && response.success()) {
            speculativeCache.put(request, response, duration);
        }

        return response;
    }

    public List<ToolResponse> executeBatchOptimized(List<ToolRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<ToolRequest> uncached = new ArrayList<>();
        Map<Integer, ToolResponse> cachedResponses = new HashMap<>();

        for (int i = 0; i < requests.size(); i++) {
            ToolRequest req = requests.get(i);
            if (speculativeCache != null && speculativeEnabled) {
                Optional<ToolResponse> cached = speculativeCache.get(req);
                if (cached.isPresent()) {
                    cachedResponses.put(i, cached.get());
                    cacheHits++;
                    continue;
                }
            }
            cacheMisses++;
            uncached.add(req);
        }

        List<ToolResponse> executedResponses = super.executeBatch(uncached);

        if (speculativeCache != null) {
            for (int i = 0; i < executedResponses.size(); i++) {
                ToolResponse resp = executedResponses.get(i);
                if (resp.success()) {
                    speculativeCache.put(uncached.get(i), resp, Duration.ZERO);
                }
            }
        }

        List<ToolResponse> results = new ArrayList<>(requests.size());
        int executedIdx = 0;
        for (int i = 0; i < requests.size(); i++) {
            if (cachedResponses.containsKey(i)) {
                results.add(cachedResponses.get(i));
            } else {
                results.add(executedResponses.get(executedIdx++));
            }
        }

        totalExecutions += requests.size();
        return results;
    }

    public CompletableFuture<ToolResponse> executeWithPriority(ToolRequest request, int priority) {
        return CompletableFuture.supplyAsync(() -> {
            PrioritizedRequest pr = new PrioritizedRequest(request, priority, Instant.now());
            priorityQueue.add(pr);
            return executeSingle(request);
        });
    }

    public void speculateExecution(List<ToolRequest> predictions) {
        if (!speculativeEnabled || predictions == null) {
            return;
        }

        for (ToolRequest prediction : predictions) {
            CompletableFuture.runAsync(() -> {
                try {
                    ToolResponse response = super.executeSingle(prediction);
                    if (response.success() && speculativeCache != null) {
                        speculativeCache.put(prediction, response, Duration.ZERO);
                        speculativeHits++;
                        LOG.fine(() -> "[Ninja] Speculative execution cached: " +
                            prediction.toolName() + "." + prediction.action());
                    }
                } catch (Exception e) {
                    LOG.fine(() -> "[Ninja] Speculative execution failed: " + e.getMessage());
                }
            });
        }
    }

    public void warmup(List<String> toolPatterns) {
        LOG.info(() -> "[Ninja] Warming up cache for " + toolPatterns.size() + " patterns");
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalExecutions", totalExecutions);
        metrics.put("cacheHits", cacheHits);
        metrics.put("cacheMisses", cacheMisses);
        metrics.put("speculativeHits", speculativeHits);
        metrics.put("cacheHitRate", totalExecutions > 0 ?
            (double) cacheHits / totalExecutions : 0.0);
        metrics.put("queueSize", priorityQueue.size());
        if (speculativeCache != null) {
            metrics.put("cacheSize", speculativeCache.size());
        }
        return metrics;
    }

    public void resetMetrics() {
        cacheHits = 0;
        cacheMisses = 0;
        totalExecutions = 0;
        speculativeHits = 0;
    }

    private record PrioritizedRequest(
        ToolRequest request,
        int priority,
        Instant queuedAt
    ) implements Comparable<PrioritizedRequest> {
        @Override
        public int compareTo(PrioritizedRequest other) {
            int cmp = Integer.compare(other.priority, this.priority);
            if (cmp == 0) {
                return this.queuedAt.compareTo(other.queuedAt);
            }
            return cmp;
        }
    }

    public static final class Builder {
        private ToolRegistry registry;
        private ReflectionToolInvoker invoker = new ReflectionToolInvoker();
        private int maxConcurrency = 200;
        private long defaultTimeoutMs = 30_000;
        private SpeculativeCache speculativeCache;
        private Map<String, Integer> toolPriorities = new HashMap<>();
        private int batchThreshold = 5;
        private Duration batchWindow = Duration.ofMillis(50);
        private boolean speculativeEnabled = true;

        private Builder() {}

        public Builder registry(ToolRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder invoker(ReflectionToolInvoker invoker) {
            this.invoker = invoker;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder defaultTimeoutMs(long timeoutMs) {
            this.defaultTimeoutMs = timeoutMs;
            return this;
        }

        public Builder speculativeCache(SpeculativeCache cache) {
            this.speculativeCache = cache;
            return this;
        }

        public Builder toolPriority(String skillAction, int priority) {
            this.toolPriorities.put(skillAction, priority);
            return this;
        }

        public Builder batchThreshold(int threshold) {
            this.batchThreshold = threshold;
            return this;
        }

        public Builder batchWindow(Duration window) {
            this.batchWindow = window;
            return this;
        }

        public Builder speculativeEnabled(boolean enabled) {
            this.speculativeEnabled = enabled;
            return this;
        }

        public NinjaDispatcher build() {
            Objects.requireNonNull(registry, "registry must not be null");
            return new NinjaDispatcher(this);
        }
    }
}
