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
package dev.fararoni.core.core.kernel;

import dev.fararoni.core.core.persona.Persona;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SecurityGuard {
    private static final Logger LOG = Logger.getLogger(SecurityGuard.class.getName());

    private static final Set<String> GLOBAL_BLACKLIST = Set.of(
        "system_shutdown",
        "db_drop",
        "fs_delete_recursive",
        "env_clear",
        "credential_dump",
        "process_kill_all",
        "git_reset_hard",
        "git_clean"
    );

    private static final Set<String> SENSITIVE_TOOLS = Set.of(
        "fs_delete",
        "db_truncate",
        "shell_execute",
        "git_push_force",
        "deploy_production"
    );

    private final ConcurrentHashMap<String, AccessLog> accessLogs = new ConcurrentHashMap<>();

    private boolean rateLimitingEnabled = false;
    private int maxAccessesPerMinute = 100;

    public boolean validateToolAccess(Persona persona, String toolName) {
        logAccessAttempt(persona.id(), toolName);

        if (GLOBAL_BLACKLIST.contains(toolName)) {
            LOG.warning(() -> String.format(
                "[SecurityGuard] BLOCKED: %s attempted to use globally blacklisted tool: %s",
                persona.id(), toolName));
            return false;
        }

        if (!persona.canUseTool(toolName)) {
            LOG.info(() -> String.format(
                "[SecurityGuard] DENIED: %s does not have permission for: %s",
                persona.id(), toolName));
            return false;
        }

        if (SENSITIVE_TOOLS.contains(toolName)) {
            LOG.info(() -> String.format(
                "[SecurityGuard] SENSITIVE: %s accessing sensitive tool: %s",
                persona.id(), toolName));
        }

        if (rateLimitingEnabled && isRateLimited(persona.id())) {
            LOG.warning(() -> String.format(
                "[SecurityGuard] RATE LIMITED: %s exceeded access rate",
                persona.id()));
            return false;
        }

        return true;
    }

    public boolean isBlacklisted(String toolName) {
        return GLOBAL_BLACKLIST.contains(toolName);
    }

    public boolean isSensitive(String toolName) {
        return SENSITIVE_TOOLS.contains(toolName);
    }

    public AccessLog getAccessLog(String personaId) {
        return accessLogs.get(personaId);
    }

    public SecurityStats getStats() {
        long totalAttempts = accessLogs.values().stream()
            .mapToLong(log -> log.totalAttempts)
            .sum();
        long totalBlocks = accessLogs.values().stream()
            .mapToLong(log -> log.blockedAttempts)
            .sum();

        return new SecurityStats(
            totalAttempts,
            totalBlocks,
            accessLogs.size(),
            GLOBAL_BLACKLIST.size(),
            SENSITIVE_TOOLS.size()
        );
    }

    public void setRateLimiting(boolean enabled, int maxPerMinute) {
        this.rateLimitingEnabled = enabled;
        this.maxAccessesPerMinute = maxPerMinute;
    }

    public void clearLogs() {
        accessLogs.clear();
    }

    private void logAccessAttempt(String personaId, String toolName) {
        accessLogs.compute(personaId, (id, log) -> {
            if (log == null) {
                log = new AccessLog(personaId);
            }
            log.recordAttempt(toolName);
            return log;
        });
    }

    private boolean isRateLimited(String personaId) {
        AccessLog log = accessLogs.get(personaId);
        if (log == null) return false;

        long currentMinute = System.currentTimeMillis() / 60000;
        if (log.lastMinute != currentMinute) {
            log.accessesThisMinute = 0;
            log.lastMinute = currentMinute;
        }

        return log.accessesThisMinute >= maxAccessesPerMinute;
    }

    public static class AccessLog {
        public final String personaId;
        public long totalAttempts = 0;
        public long blockedAttempts = 0;
        public long lastMinute = 0;
        public int accessesThisMinute = 0;
        public final ConcurrentHashMap<String, Long> toolAccessCounts = new ConcurrentHashMap<>();

        public AccessLog(String personaId) {
            this.personaId = personaId;
        }

        public void recordAttempt(String toolName) {
            totalAttempts++;
            accessesThisMinute++;
            toolAccessCounts.merge(toolName, 1L, Long::sum);
        }

        public void recordBlock() {
            blockedAttempts++;
        }
    }

    public record SecurityStats(
        long totalAttempts,
        long totalBlocks,
        int uniquePersonas,
        int blacklistSize,
        int sensitiveToolsCount
    ) {
        public double blockRate() {
            return totalAttempts > 0 ? (double) totalBlocks / totalAttempts : 0.0;
        }
    }
}
