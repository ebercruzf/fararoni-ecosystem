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
import dev.fararoni.bus.agent.api.ToolSkill;

/**
 * Interface for skills that support Saga compensation (undo).
 *
 * <p>Implement this interface when your skill modifies state (files, database,
 * git) and needs to support automatic rollback if a later step fails. This is
 * the key differentiator vs MCP which has no rollback capability.</p>
 *
 * <h2>Saga Pattern</h2>
 * <p>Unlike traditional 2PC (Two-Phase Commit) which blocks resources,
 * Saga executes operations immediately and stores compensation instructions.
 * If a later step fails, compensations are executed in reverse order.</p>
 *
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * public class FileSkillImpl implements FileSystemSkill, SagaCapableSkill {
 *
 *     @Override
 *     public FNLResult<Void> compensate(CompensationInstruction instruction) {
 *         return switch (instruction.method()) {
 *             case "delete" -> {
 *                 String path = instruction.getParam("path");
 *                 Files.deleteIfExists(Path.of(path));
 *                 yield FNLResult.success(null);
 *             }
 *             case "restore" -> {
 *                 String backup = instruction.getParam("backup");
 *                 String target = instruction.getParam("target");
 *                 Files.move(Path.of(backup), Path.of(target), REPLACE_EXISTING);
 *                 yield FNLResult.success(null);
 *             }
 *             default -> FNLResult.failure("Unknown compensation: " + instruction.method());
 *         };
 *     }
 *
 *     @Override
 *     @AgentAction(name = "write", description = "Writes a file")
 *     public FNLResult<String> writeFile(String path, String content) {
 *         // Backup if exists
 *         String backup = createBackupIfExists(path);
 *
 *         // Write file
 *         Files.writeString(Path.of(path), content);
 *
 *         // Return with compensation instruction
 *         if (backup != null) {
 *             return FNLResult.successWithSaga("Written",
 *                 CompensationInstruction.of("FileSkill", "restore",
 *                     Map.of("backup", backup, "target", path)));
 *         } else {
 *             return FNLResult.successWithSaga("Created",
 *                 CompensationInstruction.of("FileSkill", "delete",
 *                     Map.of("path", path)));
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Compensation Rules</h2>
 * <ul>
 *   <li><b>Idempotent:</b> Calling compensate() 3 times has same effect as 1</li>
 *   <li><b>Infallible:</b> Must not throw exceptions, retry internally</li>
 *   <li><b>Fast:</b> Should complete quickly, no long-running operations</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see CompensationInstruction
 * @see FNLResult#successWithSaga
 */
public interface SagaCapableSkill extends ToolSkill {

    /**
     * Executes a compensation action to undo a previous operation.
     *
     * <p>This method is called by the Saga Orchestrator when a later step
     * in the saga fails. It should undo the effect of a previous successful
     * operation.</p>
     *
     * <p><strong>Implementation Requirements:</strong></p>
     * <ul>
     *   <li>Must be <b>idempotent</b> - safe to call multiple times</li>
     *   <li>Must be <b>infallible</b> - should not throw, return failure result</li>
     *   <li>Should be <b>fast</b> - avoid long-running operations</li>
     *   <li>Should <b>log</b> the compensation for audit trail</li>
     * </ul>
     *
     * @param instruction the compensation instruction from a previous operation
     * @return result indicating success or failure of compensation
     */
    FNLResult<Void> compensate(CompensationInstruction instruction);

    /**
     * Checks if this skill can handle a specific compensation method.
     *
     * <p>Default implementation returns true. Override to restrict
     * which compensation methods are supported.</p>
     *
     * @param method the compensation method name
     * @return true if this skill can handle the method
     */
    default boolean canCompensate(String method) {
        return true;
    }

    /**
     * Gets the list of compensation methods this skill supports.
     *
     * <p>Used for documentation and validation.</p>
     *
     * @return array of supported compensation method names
     */
    default String[] getSupportedCompensations() {
        return new String[0];
    }
}
