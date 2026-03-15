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
package dev.fararoni.bus.agent.api.saga;

import dev.fararoni.bus.agent.api.FNLResult;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an "Insurance Policy" for Saga compensation.
 *
 * <p>When a tool executes an action that modifies state (file, database, git),
 * it returns this instruction along with the result. If a later step in the
 * Saga fails, the orchestrator uses these instructions to undo the changes
 * in reverse order.</p>
 *
 * <h2>How Saga Works</h2>
 * <pre>
 * 1. Agent wants to: Create file → Commit → Push to DB
 * 2. FileSkill.write() succeeds → Returns CompensationInstruction("delete", path)
 * 3. GitSkill.commit() succeeds → Returns CompensationInstruction("reset", hash)
 * 4. DbSkill.insert() FAILS!
 * 5. Orchestrator runs compensations in reverse:
 *    - GitSkill.compensate("reset", hash) → Undoes commit
 *    - FileSkill.compensate("delete", path) → Deletes file
 * 6. System is back to clean state!
 * </pre>
 *
 * <h2>Usage in Skill</h2>
 * <pre>{@code
 * @AgentAction(name = "write", description = "Writes a file")
 * public FNLResult<String> writeFile(String path, String content) {
 *     // 1. Backup existing file if it exists
 *     String backupPath = backup(path);
 *
 *     // 2. Write the new content
 *     Files.writeString(Path.of(path), content);
 *
 *     // 3. Return success with compensation instruction
 *     if (backupPath != null) {
 *         // If file existed, compensation = restore from backup
 *         return FNLResult.successWithSaga(
 *             "File written: " + path,
 *             CompensationInstruction.of("FileSkill", "restore",
 *                 Map.of("backup", backupPath, "target", path))
 *         );
 *     } else {
 *         // If new file, compensation = delete it
 *         return FNLResult.successWithSaga(
 *             "File created: " + path,
 *             CompensationInstruction.of("FileSkill", "delete",
 *                 Map.of("path", path))
 *         );
 *     }
 * }
 * }</pre>
 *
 * <h2>Enterprise Value vs MCP</h2>
 * <ul>
 *   <li>MCP has no concept of rollback - broken state remains</li>
 *   <li>FNL Saga provides automatic cleanup on failure</li>
 *   <li>Serializable for persistence across server restarts</li>
 *   <li>Industry-standard pattern (used by Uber, )</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @param skillName The skill that should execute the compensation
 * @param method The method to call for compensation (e.g., "delete", "restore", "reset")
 * @param params Parameters needed for the compensation action
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see SagaCapableSkill
 * @see FNLResult
 */
public record CompensationInstruction(
    String skillName,
    String method,
    Map<String, Object> params
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Compact constructor with validation.
     */
    public CompensationInstruction {
        Objects.requireNonNull(skillName, "skillName cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
        if (skillName.isBlank()) {
            throw new IllegalArgumentException("skillName cannot be blank");
        }
        if (method.isBlank()) {
            throw new IllegalArgumentException("method cannot be blank");
        }
        // Make params immutable
        params = params != null ? Map.copyOf(params) : Collections.emptyMap();
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a compensation instruction with parameters.
     *
     * @param skillName the skill to execute compensation
     * @param method the compensation method
     * @param params the parameters for compensation
     * @return new CompensationInstruction
     */
    public static CompensationInstruction of(String skillName, String method, Map<String, Object> params) {
        return new CompensationInstruction(skillName, method, params);
    }

    /**
     * Creates a compensation instruction without parameters.
     *
     * @param skillName the skill to execute compensation
     * @param method the compensation method
     * @return new CompensationInstruction
     */
    public static CompensationInstruction of(String skillName, String method) {
        return new CompensationInstruction(skillName, method, Collections.emptyMap());
    }

    // ==================== Helper Methods ====================

    /**
     * Gets a parameter value by key.
     *
     * @param key the parameter key
     * @param <T> expected type
     * @return the parameter value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key) {
        return (T) params.get(key);
    }

    /**
     * Gets a parameter value with default.
     *
     * @param key the parameter key
     * @param defaultValue value to return if not found
     * @param <T> expected type
     * @return the parameter value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, T defaultValue) {
        Object value = params.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Checks if a parameter exists.
     *
     * @param key the parameter key
     * @return true if parameter exists
     */
    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    /**
     * Returns a human-readable description of this instruction.
     *
     * @return description string
     */
    public String describe() {
        return String.format("%s.%s(%s)", skillName, method, params);
    }
}
