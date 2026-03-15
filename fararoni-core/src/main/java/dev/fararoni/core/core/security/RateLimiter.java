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
package dev.fararoni.core.core.security;

import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.security.RateLimit;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class RateLimiter {
    private static final Logger LOG = Logger.getLogger(RateLimiter.class.getName());

    private final Map<String, RateLimitState> states = new ConcurrentHashMap<>();

    private final Map<Method, RateLimit> annotationCache = new ConcurrentHashMap<>();

    private static class RateLimitState {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final int maxCalls;
        private final long windowMs;
        private final RateLimit.Strategy strategy;
        private final String message;

        RateLimitState(RateLimit annotation) {
            this.maxCalls = annotation.calls();
            this.windowMs = annotation.unit().toMillis(annotation.period());
            this.strategy = annotation.strategy();
            this.message = annotation.message();
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long windowStartTime = windowStart.get();

            if (now - windowStartTime >= windowMs) {
                windowStart.set(now);
                count.set(1);
                return true;
            }

            int currentCount = count.incrementAndGet();
            if (currentCount <= maxCalls) {
                return true;
            }

            count.decrementAndGet();
            return false;
        }

        long getTimeRemainingMs() {
            long elapsed = System.currentTimeMillis() - windowStart.get();
            return Math.max(0, windowMs - elapsed);
        }

        int getCurrentCount() {
            return count.get();
        }
    }

    public record RateLimitResult(
        boolean allowed,
        int currentCount,
        int maxCalls,
        long retryAfterMs,
        String message
    ) {
        public static RateLimitResult allowed(int current, int max) {
            return new RateLimitResult(true, current, max, 0, null);
        }

        public static RateLimitResult denied(int current, int max, long retryAfter, String message) {
            return new RateLimitResult(false, current, max, retryAfter, message);
        }
    }

    public FNLResult<Void> checkLimit(Method method) {
        RateLimit annotation = getRateLimit(method);

        if (annotation == null) {
            return FNLResult.success(null);
        }

        String key = getMethodKey(method);
        RateLimitState state = states.computeIfAbsent(key, k -> new RateLimitState(annotation));

        if (state.strategy == RateLimit.Strategy.THROTTLE) {
            return handleThrottle(method, state);
        }

        if (state.tryAcquire()) {
            LOG.fine(() -> String.format("[RATE_LIMIT] Allowed: %s (%d/%d)",
                method.getName(), state.getCurrentCount(), state.maxCalls));
            return FNLResult.success(null);
        }

        long retryAfter = state.getTimeRemainingMs();
        LOG.warning(() -> String.format("[RATE_LIMIT] DENIED: %s (%d/%d, retry in %dms)",
            method.getName(), state.getCurrentCount(), state.maxCalls, retryAfter));

        return FNLResult.failure(String.format("%s (retry after %d seconds)",
            state.message, retryAfter / 1000 + 1));
    }

    private FNLResult<Void> handleThrottle(Method method, RateLimitState state) {
        if (!state.tryAcquire()) {
            long delay = state.windowMs / state.maxCalls;
            LOG.fine(() -> String.format("[RATE_LIMIT] Throttling: %s (delay %dms)",
                method.getName(), delay));

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FNLResult.failure("Interrupted while throttling");
            }

            state.tryAcquire();
        }
        return FNLResult.success(null);
    }

    public RateLimitResult getStatus(Method method) {
        RateLimit annotation = getRateLimit(method);

        if (annotation == null) {
            return RateLimitResult.allowed(0, Integer.MAX_VALUE);
        }

        String key = getMethodKey(method);
        RateLimitState state = states.get(key);

        if (state == null) {
            return RateLimitResult.allowed(0, annotation.calls());
        }

        int current = state.getCurrentCount();
        int max = state.maxCalls;

        if (current < max) {
            return RateLimitResult.allowed(current, max);
        }

        return RateLimitResult.denied(current, max, state.getTimeRemainingMs(), state.message);
    }

    public void recordCall(Method method) {
        RateLimit annotation = getRateLimit(method);

        if (annotation == null) {
            return;
        }

        String key = getMethodKey(method);
        RateLimitState state = states.computeIfAbsent(key, k -> new RateLimitState(annotation));
        state.tryAcquire();
    }

    public void reset(Method method) {
        String key = getMethodKey(method);
        states.remove(key);
    }

    public void resetAll() {
        states.clear();
    }

    public int getTrackedMethodCount() {
        return states.size();
    }

    private RateLimit getRateLimit(Method method) {
        return annotationCache.computeIfAbsent(method,
            m -> m.getAnnotation(RateLimit.class));
    }

    private String getMethodKey(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    public void setCustomLimit(String methodKey, int maxCalls, long windowMs) {
        states.put(methodKey, new RateLimitState(new RateLimit() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RateLimit.class;
            }

            @Override
            public int calls() {
                return maxCalls;
            }

            @Override
            public long period() {
                return windowMs;
            }

            @Override
            public TimeUnit unit() {
                return TimeUnit.MILLISECONDS;
            }

            @Override
            public Strategy strategy() {
                return Strategy.REJECT;
            }

            @Override
            public String message() {
                return "Rate limit exceeded";
            }
        }));
    }
}
