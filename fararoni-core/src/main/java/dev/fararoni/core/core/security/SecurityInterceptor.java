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
import dev.fararoni.bus.agent.api.security.AuditLog;
import dev.fararoni.bus.agent.api.security.RequiresRole;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SecurityInterceptor {
    private static final Logger LOG = Logger.getLogger(SecurityInterceptor.class.getName());

    private static final ThreadLocal<SecurityContext> CURRENT_CONTEXT = new ThreadLocal<>();

    private final Map<Method, RequiresRole> roleCache = new ConcurrentHashMap<>();
    private final Map<Method, AuditLog> auditCache = new ConcurrentHashMap<>();

    private final List<AuditEntry> auditEntries = Collections.synchronizedList(new ArrayList<>());

    public record SecurityContext(
        String userId,
        String username,
        Set<String> roles,
        Map<String, Object> attributes
    ) {
        public SecurityContext {
            roles = roles != null ? Set.copyOf(roles) : Set.of();
            attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        }

        public static SecurityContext anonymous() {
            return new SecurityContext("anonymous", "anonymous", Set.of(), Map.of());
        }

        public static SecurityContext of(String userId, Set<String> roles) {
            return new SecurityContext(userId, userId, roles, Map.of());
        }
    }

    public record AuditEntry(
        Instant timestamp,
        String userId,
        String skillName,
        String actionName,
        Map<String, Object> parameters,
        boolean success,
        String errorMessage,
        long durationMs,
        String severity,
        String category
    ) {}

    public static void setContext(SecurityContext context) {
        CURRENT_CONTEXT.set(context);
    }

    public static SecurityContext getContext() {
        SecurityContext ctx = CURRENT_CONTEXT.get();
        return ctx != null ? ctx : SecurityContext.anonymous();
    }

    public static void clearContext() {
        CURRENT_CONTEXT.remove();
    }

    public void setCurrentRoles(Set<String> roles) {
        SecurityContext current = getContext();
        setContext(new SecurityContext(
            current.userId(),
            current.username(),
            roles,
            current.attributes()
        ));
    }

    public FNLResult<Void> checkAccess(Method method) {
        RequiresRole annotation = getRequiresRole(method);

        if (annotation == null) {
            return FNLResult.success(null);
        }

        SecurityContext ctx = getContext();
        String[] requiredRoles = annotation.value();
        boolean anyMode = annotation.any();

        boolean hasAccess;
        if (anyMode) {
            hasAccess = Arrays.stream(requiredRoles)
                .anyMatch(role -> ctx.roles().contains(role));
        } else {
            hasAccess = ctx.roles().containsAll(Arrays.asList(requiredRoles));
        }

        if (hasAccess) {
            LOG.fine(() -> String.format("[RBAC] Access granted: %s.%s for user %s with roles %s",
                method.getDeclaringClass().getSimpleName(), method.getName(),
                ctx.userId(), ctx.roles()));
            return FNLResult.success(null);
        } else {
            String message = annotation.message();
            LOG.warning(() -> String.format("[RBAC] Access DENIED: %s.%s for user %s. Required: %s (%s), Has: %s",
                method.getDeclaringClass().getSimpleName(), method.getName(),
                ctx.userId(), Arrays.toString(requiredRoles),
                anyMode ? "ANY" : "ALL", ctx.roles()));
            return FNLResult.failure(message);
        }
    }

    public boolean hasAccess(Method method, Set<String> userRoles) {
        RequiresRole annotation = getRequiresRole(method);

        if (annotation == null) {
            return true;
        }

        String[] requiredRoles = annotation.value();
        boolean anyMode = annotation.any();

        if (anyMode) {
            return Arrays.stream(requiredRoles).anyMatch(userRoles::contains);
        } else {
            return userRoles.containsAll(Arrays.asList(requiredRoles));
        }
    }

    public void recordAudit(Method method, Map<String, Object> parameters,
                           boolean success, String errorMessage, long durationMs) {
        AuditLog annotation = getAuditLog(method);

        if (annotation == null) {
            return;
        }

        SecurityContext ctx = getContext();
        String severity = annotation.severity();
        String category = annotation.category();

        Map<String, Object> filteredParams = filterParameters(parameters, annotation);

        AuditEntry entry = new AuditEntry(
            Instant.now(),
            ctx.userId(),
            method.getDeclaringClass().getSimpleName(),
            method.getName(),
            filteredParams,
            success,
            errorMessage,
            durationMs,
            severity,
            category
        );

        auditEntries.add(entry);

        LOG.info(() -> String.format("[AUDIT] [%s] %s %s.%s by %s (%dms)",
            severity,
            success ? "SUCCESS" : "FAILURE",
            entry.skillName(), entry.actionName(),
            entry.userId(), entry.durationMs()));
    }

    private Map<String, Object> filterParameters(Map<String, Object> params, AuditLog annotation) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }

        if (!annotation.captureInputs()) {
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (String key : params.keySet()) {
                filtered.put(key, "[REDACTED]");
            }
            return filtered;
        }

        return Map.copyOf(params);
    }

    private RequiresRole getRequiresRole(Method method) {
        return roleCache.computeIfAbsent(method,
            m -> m.getAnnotation(RequiresRole.class));
    }

    private AuditLog getAuditLog(Method method) {
        return auditCache.computeIfAbsent(method,
            m -> m.getAnnotation(AuditLog.class));
    }

    public List<AuditEntry> getAuditEntries() {
        return List.copyOf(auditEntries);
    }

    public List<AuditEntry> getAuditEntriesForUser(String userId) {
        return auditEntries.stream()
            .filter(e -> e.userId().equals(userId))
            .toList();
    }

    public void clearAuditEntries() {
        auditEntries.clear();
    }

    public int getAuditEntryCount() {
        return auditEntries.size();
    }
}
