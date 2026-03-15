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
package dev.fararoni.core.core.dispatcher;

import dev.fararoni.bus.agent.api.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class AgentDispatcher {

    private final ToolRegistry registry;
    private final ReflectionToolInvoker invoker;
    private final Semaphore concurrencyLimiter;
    private final long defaultTimeoutMs;

    public static final int DEFAULT_MAX_CONCURRENCY = 100;

    public static final long DEFAULT_TIMEOUT_MS = 30_000;

    public AgentDispatcher(ToolRegistry registry) {
        this(registry, new ReflectionToolInvoker(), DEFAULT_MAX_CONCURRENCY, DEFAULT_TIMEOUT_MS);
    }

    public AgentDispatcher(ToolRegistry registry, ReflectionToolInvoker invoker) {
        this(registry, invoker, DEFAULT_MAX_CONCURRENCY, DEFAULT_TIMEOUT_MS);
    }

    public AgentDispatcher(ToolRegistry registry, ReflectionToolInvoker invoker,
                          int maxConcurrency, long defaultTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.invoker = Objects.requireNonNull(invoker, "invoker cannot be null");
        this.concurrencyLimiter = new Semaphore(maxConcurrency);
        this.defaultTimeoutMs = defaultTimeout;
    }

    public ToolResponse executeSingle(ToolRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        long startTime = System.currentTimeMillis();
        String requestId = request.requestId();

        try {
            if (!concurrencyLimiter.tryAcquire(defaultTimeoutMs, TimeUnit.MILLISECONDS)) {
                return ToolResponse.error(
                    "Sistema ocupado, demasiadas solicitudes concurrentes",
                    requestId,
                    System.currentTimeMillis() - startTime
                );
            }

            try {
                return doExecute(request, startTime);
            } finally {
                concurrencyLimiter.release();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResponse.error(
                "Ejecucion interrumpida",
                requestId,
                System.currentTimeMillis() - startTime
            );
        }
    }

    public List<ToolResponse> executeBatch(List<ToolRequest> requests) {
        Objects.requireNonNull(requests, "requests cannot be null");

        if (requests.isEmpty()) {
            return List.of();
        }

        if (requests.size() == 1) {
            return List.of(executeSingle(requests.getFirst()));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.printf("[FNL] Ejecutando %d herramientas en paralelo con Virtual Threads...%n",
                requests.size());

            List<Future<ToolResponse>> futures = requests.stream()
                .map(req -> executor.submit(() -> executeSingle(req)))
                .toList();

            return futures.stream()
                .map(this::safeGet)
                .toList();
        }
    }

    public boolean isSkillAvailable(String skillName) {
        return registry.findSkill(skillName)
            .map(ToolSkill::isAvailable)
            .orElse(false);
    }

    public int getActiveExecutions() {
        return DEFAULT_MAX_CONCURRENCY - concurrencyLimiter.availablePermits();
    }

    public ToolRegistry getRegistry() {
        return registry;
    }

    private ToolResponse doExecute(ToolRequest request, long startTime) {
        String requestId = request.requestId();

        try {
            ToolSkill skill = registry.findSkill(request.toolName())
                .orElseThrow(() -> new ToolNotFoundException(
                    request.toolName(),
                    registry.getAllSkillNames()
                ));

            if (!skill.isAvailable()) {
                return ToolResponse.error(
                    "Skill '" + request.toolName() + "' no esta disponible actualmente",
                    requestId,
                    System.currentTimeMillis() - startTime
                );
            }

            Method method = registry.findAction(request.toolName(), request.action())
                .orElseThrow(() -> new ActionNotFoundException(
                    request.toolName(),
                    request.action(),
                    ((ToolRegistryImpl) registry).getActionNames(request.toolName())
                ));

            AgentAction actionAnnotation = method.getAnnotation(AgentAction.class);
            long timeout = actionAnnotation != null ? actionAnnotation.timeoutMs() : defaultTimeoutMs;

            Object result = executeWithTimeout(skill, method, request, timeout);

            long executionTime = System.currentTimeMillis() - startTime;
            String resultStr = result != null ? result.toString() : "OK";

            logSuccess(request, resultStr, executionTime);

            return ToolResponse.success(resultStr, requestId, executionTime);

        } catch (ToolNotFoundException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logError(request, e, executionTime);
            return ToolResponse.error(e.getMessage(), requestId, executionTime);

        } catch (ActionNotFoundException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logError(request, e, executionTime);
            return ToolResponse.error(e.getMessage(), requestId, executionTime);

        } catch (ParameterValidationException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logError(request, e, executionTime);
            return ToolResponse.error(e.getMessage(), requestId, executionTime);

        } catch (TimeoutException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logError(request, e, executionTime);
            return ToolResponse.error(
                "La operacion excedio el tiempo limite",
                requestId,
                executionTime
            );

        } catch (SecurityException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logError(request, e, executionTime);
            return ToolResponse.error(
                "Acceso denegado: " + e.getMessage(),
                requestId,
                executionTime
            );

        } catch (java.lang.reflect.InvocationTargetException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logError(request, new RuntimeException(cause.getMessage()), executionTime);

            String message = cause.getMessage();
            if (message == null || message.isBlank()) {
                message = cause.getClass().getSimpleName();
            }

            return ToolResponse.error(message, requestId, executionTime);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logError(request, e, executionTime);

            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.getClass().getSimpleName();
            }

            return ToolResponse.error(message, requestId, executionTime);
        }
    }

    private Object executeWithTimeout(ToolSkill skill, Method method, ToolRequest request, long timeoutMs)
            throws Exception {

        if (timeoutMs <= 0) {
            return invoker.invoke(skill, method, request.params());
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object> future = executor.submit(() -> invoker.invoke(skill, method, request.params()));

            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                throw new TimeoutException("Timeout after " + timeoutMs + "ms");
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw new RuntimeException(cause);
            }
        }
    }

    private ToolResponse safeGet(Future<ToolResponse> future) {
        try {
            return future.get(defaultTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ToolResponse.error("Timeout esperando resultado");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResponse.error("Ejecucion interrumpida");
        } catch (Exception e) {
            return ToolResponse.error("Error: " + e.getMessage());
        }
    }

    private void logSuccess(ToolRequest request, String result, long timeMs) {
        System.out.printf("[TOOL_EXEC] %s.%s -> %s (SUCCESS) [%dms]%n",
            request.toolName(),
            request.action(),
            truncate(result, 50),
            timeMs
        );
    }

    private void logError(ToolRequest request, Exception e, long timeMs) {
        System.out.printf("[TOOL_EXEC] %s.%s -> %s (ERROR) [%dms]%n",
            request.toolName(),
            request.action(),
            e.getMessage(),
            timeMs
        );
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
}
