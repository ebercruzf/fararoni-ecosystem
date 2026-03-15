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

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.security.AgentRole;
import dev.fararoni.bus.agent.api.security.CapabilityMetadata;
import dev.fararoni.bus.agent.api.security.SecurityInspector;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class InMemorySecurityInspector implements SecurityInspector {
    private static final Logger LOG = Logger.getLogger(InMemorySecurityInspector.class.getName());

    private final Map<String, CapabilityMetadata> policies;

    private final Map<String, AgentRole> userDirectory;

    private final AgentRole defaultRole;

    public InMemorySecurityInspector() {
        this(null);
    }

    public InMemorySecurityInspector(AgentRole defaultRole) {
        this.policies = new ConcurrentHashMap<>();
        this.userDirectory = new ConcurrentHashMap<>();
        this.defaultRole = defaultRole;
    }

    public void registerUser(String userId, AgentRole role) {
        if (userId == null || role == null) {
            throw new IllegalArgumentException("userId y role no pueden ser null");
        }
        userDirectory.put(userId, role);
        LOG.fine("[RBAC] Usuario registrado: " + userId + " -> " + role);
    }

    public boolean removeUser(String userId) {
        AgentRole removed = userDirectory.remove(userId);
        if (removed != null) {
            LOG.fine("[RBAC] Usuario eliminado: " + userId);
            return true;
        }
        return false;
    }

    public AgentRole getUserRole(String userId) {
        return userDirectory.getOrDefault(userId, defaultRole);
    }

    public boolean isUserRegistered(String userId) {
        return userDirectory.containsKey(userId);
    }

    @Override
    public void definePolicy(CapabilityMetadata metadata) {
        if (metadata == null || metadata.name() == null) {
            throw new IllegalArgumentException("metadata y su nombre no pueden ser null");
        }
        policies.put(metadata.name(), metadata);
        LOG.fine("[RBAC] Policy definida: " + metadata.name() +
                 " (rol requerido: " + metadata.requiredRole() + ")");
    }

    public boolean removePolicy(String topic) {
        CapabilityMetadata removed = policies.remove(topic);
        if (removed != null) {
            LOG.fine("[RBAC] Policy eliminada: " + topic);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasPolicy(String topic) {
        return policies.containsKey(topic);
    }

    @Override
    public CapabilityMetadata getPolicy(String topic) {
        return policies.get(topic);
    }

    public int getPolicyCount() {
        return policies.size();
    }

    @Override
    public CompletableFuture<Void> inspect(String topic, SovereignEnvelope<?> envelope) {
        return CompletableFuture.supplyAsync(() -> {
            String userId = envelope != null ? envelope.userId() : null;
            if (userId == null || userId.isBlank()) {
                logDenial(topic, "unknown", "userId no proporcionado");
                throw new SecurityException(
                    "Acceso denegado a '" + topic + "': userId no proporcionado"
                );
            }

            AgentRole userRole = getUserRole(userId);
            if (userRole == null) {
                logDenial(topic, userId, "usuario no registrado (Deny-by-Default)");
                throw new SecurityException(
                    "Acceso denegado a '" + topic + "': usuario '" + userId + "' no registrado"
                );
            }

            CapabilityMetadata policy = policies.get(topic);
            if (policy == null) {
                logDenial(topic, userId, "sin policy definida (Deny-by-Default)");
                throw new SecurityException(
                    "Acceso denegado a '" + topic + "': sin policy definida"
                );
            }

            AgentRole requiredRole = policy.requiredRole();
            if (!userRole.canAccess(requiredRole)) {
                logDenial(topic, userId,
                    "rol insuficiente (tiene: " + userRole + ", requiere: " + requiredRole + ")");
                throw new SecurityException(
                    "Acceso denegado a '" + topic + "': rol insuficiente. " +
                    "Usuario '" + userId + "' tiene " + userRole +
                    ", se requiere " + requiredRole
                );
            }

            if (policy.requiresAudit()) {
                LOG.info("[RBAC-AUDIT] Acceso permitido a operacion critica: " +
                         topic + " por " + userId + " (" + userRole + ")");
            }

            LOG.fine("[RBAC] Acceso permitido: " + userId + " -> " + topic);
            return null;
        });
    }

    private void logDenial(String topic, String userId, String reason) {
        LOG.warning("[RBAC-DENY] " + topic + " | usuario: " + userId + " | razon: " + reason);
    }

    public void definePolicies(Iterable<CapabilityMetadata> metadataList) {
        for (CapabilityMetadata metadata : metadataList) {
            definePolicy(metadata);
        }
    }

    public void registerUsers(Map<String, AgentRole> users) {
        for (Map.Entry<String, AgentRole> entry : users.entrySet()) {
            registerUser(entry.getKey(), entry.getValue());
        }
    }

    public void clearAll() {
        policies.clear();
        userDirectory.clear();
        LOG.warning("[RBAC] Todas las policies y usuarios eliminados - DENY-ALL activo");
    }

    public Stats getStats() {
        return new Stats(
            policies.size(),
            userDirectory.size(),
            defaultRole
        );
    }

    public record Stats(
        int policyCount,
        int userCount,
        AgentRole defaultRole
    ) {
        public boolean isDenyByDefault() {
            return defaultRole == null;
        }
    }

    @Override
    public String toString() {
        return String.format(
            "InMemorySecurityInspector[policies=%d, users=%d, defaultRole=%s]",
            policies.size(),
            userDirectory.size(),
            defaultRole != null ? defaultRole : "DENY"
        );
    }
}
