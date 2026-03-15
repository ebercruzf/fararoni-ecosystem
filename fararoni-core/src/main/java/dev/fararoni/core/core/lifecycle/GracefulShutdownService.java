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
package dev.fararoni.core.core.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class GracefulShutdownService {
    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownService.class);

    private static volatile GracefulShutdownService instance;
    private static final Object LOCK = new Object();

    private final Deque<RegisteredResource> resources = new ConcurrentLinkedDeque<>();

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private final AtomicBoolean shutdownCompleted = new AtomicBoolean(false);

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    private static final long RESOURCE_TIMEOUT_MS = 5000;

    private record RegisteredResource(String name, AutoCloseable resource, long registeredAt) {}

    private GracefulShutdownService() {
        registerShutdownHook();
        log.info("[GracefulShutdownService] Initialized - JVM shutdown hook registered");
    }

    public static GracefulShutdownService getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new GracefulShutdownService();
                }
            }
        }
        return instance;
    }

    public void register(String name, AutoCloseable resource) {
        if (resource == null) {
            log.warn("[GracefulShutdownService] Attempted to register null resource: {}", name);
            return;
        }

        if (shutdownStarted.get()) {
            log.warn("[GracefulShutdownService] Cannot register '{}' - shutdown already started", name);
            throw new IllegalStateException("Cannot register resources after shutdown started");
        }

        resources.addFirst(new RegisteredResource(name, resource, System.currentTimeMillis()));
        log.debug("[GracefulShutdownService] Registered: {} (total: {})", name, resources.size());
    }

    public void register(String name, Closeable resource) {
        register(name, (AutoCloseable) resource);
    }

    public boolean unregister(AutoCloseable resource) {
        boolean removed = resources.removeIf(r -> r.resource == resource);
        if (removed) {
            log.debug("[GracefulShutdownService] Unregistered resource (remaining: {})", resources.size());
        }
        return removed;
    }

    public ShutdownResult shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            log.warn("[GracefulShutdownService] Shutdown already in progress or completed");
            return new ShutdownResult(0, 0, 0, true);
        }

        long startTime = System.currentTimeMillis();
        int totalResources = resources.size();

        log.info("[GracefulShutdownService] Starting graceful shutdown of {} resources...", totalResources);

        RegisteredResource resource;
        while ((resource = resources.pollFirst()) != null) {
            closeResource(resource);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        shutdownCompleted.set(true);

        int success = successCount.get();
        int failures = failureCount.get();

        String status = failures == 0 ? "SUCCESS" : "PARTIAL";
        log.info("[GracefulShutdownService] Shutdown {} - {} resources closed in {}ms ({} success, {} failed)",
                status, totalResources, elapsed, success, failures);

        System.out.println("\n[Shutdown] Cierre completado: " + success + "/" + totalResources +
                " recursos en " + elapsed + "ms");

        return new ShutdownResult(success, failures, elapsed, false);
    }

    private void closeResource(RegisteredResource registered) {
        String name = registered.name;
        AutoCloseable resource = registered.resource;

        log.debug("[GracefulShutdownService] Closing: {}", name);
        long start = System.currentTimeMillis();

        try {
            resource.close();
            long elapsed = System.currentTimeMillis() - start;
            successCount.incrementAndGet();
            log.info("[GracefulShutdownService] Closed: {} ({}ms)", name, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            failureCount.incrementAndGet();
            log.error("[GracefulShutdownService] Failed to close '{}' after {}ms: {}",
                    name, elapsed, e.getMessage(), e);
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[GracefulShutdownService] JVM shutdown signal received");
            shutdown();
        }, "GracefulShutdown-Hook"));
    }

    public int getRegisteredCount() {
        return resources.size();
    }

    public boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    public boolean isShutdownCompleted() {
        return shutdownCompleted.get();
    }

    void resetForTesting() {
        resources.clear();
        shutdownStarted.set(false);
        shutdownCompleted.set(false);
        successCount.set(0);
        failureCount.set(0);
    }

    public record ShutdownResult(
            int successCount,
            int failureCount,
            long elapsedMs,
            boolean wasAlreadyRunning
    ) {
        public boolean isSuccess() {
            return failureCount == 0 && !wasAlreadyRunning;
        }

        public int totalCount() {
            return successCount + failureCount;
        }
    }
}
