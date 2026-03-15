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
package dev.fararoni.core.core.saga;

import dev.fararoni.bus.agent.api.FNLResult;
import dev.fararoni.bus.agent.api.ToolRegistry;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.saga.CompensationInstruction;
import dev.fararoni.bus.agent.api.saga.SagaCapableSkill;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class SagaOrchestrator {
    private static final Logger LOG = Logger.getLogger(SagaOrchestrator.class.getName());

    private final ToolRegistry toolRegistry;
    private final Map<String, CompensationStack> activeSagas;
    private final AtomicInteger totalSagas;
    private final AtomicInteger successfulSagas;
    private final AtomicInteger compensatedSagas;

    public record CompensationResult(
        String sagaId,
        int totalCompensations,
        int successfulCompensations,
        int failedCompensations,
        List<String> errors,
        long durationMs
    ) {
        public boolean isFullyCompensated() {
            return failedCompensations == 0;
        }

        public boolean isPartiallyCompensated() {
            return successfulCompensations > 0 && failedCompensations > 0;
        }
    }

    public SagaOrchestrator(ToolRegistry toolRegistry) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry cannot be null");
        this.activeSagas = new ConcurrentHashMap<>();
        this.totalSagas = new AtomicInteger(0);
        this.successfulSagas = new AtomicInteger(0);
        this.compensatedSagas = new AtomicInteger(0);
    }

    public String beginSaga() {
        return beginSaga(null);
    }

    public String beginSaga(String customId) {
        String sagaId = customId != null ? customId :
            "saga-" + UUID.randomUUID().toString().substring(0, 8);

        CompensationStack stack = new CompensationStack(sagaId);
        activeSagas.put(sagaId, stack);
        totalSagas.incrementAndGet();

        LOG.fine(() -> String.format("[SAGA_BEGIN] %s", sagaId));
        return sagaId;
    }

    public boolean commitSaga(String sagaId) {
        CompensationStack stack = activeSagas.remove(sagaId);
        if (stack != null) {
            int compensations = stack.size();
            stack.clear();
            successfulSagas.incrementAndGet();
            LOG.fine(() -> String.format("[SAGA_COMMIT] %s (cleared %d compensations)", sagaId, compensations));
            return true;
        }
        return false;
    }

    public boolean isActive(String sagaId) {
        return activeSagas.containsKey(sagaId);
    }

    public <T> boolean registerCompensation(String sagaId, FNLResult<T> result) {
        return registerCompensation(sagaId, result, null);
    }

    public <T> boolean registerCompensation(String sagaId, FNLResult<T> result, String description) {
        if (result == null || !result.hasSagaCompensation()) {
            return false;
        }

        CompensationStack stack = activeSagas.get(sagaId);
        if (stack == null) {
            LOG.warning(() -> String.format("[SAGA_WARN] Attempted to register compensation for unknown saga: %s", sagaId));
            return false;
        }

        CompensationInstruction instruction = result.undoInstruction();
        stack.push(instruction, description);

        LOG.fine(() -> String.format("[SAGA_REG] %s <- %s.%s",
            sagaId, instruction.skillName(), instruction.method()));
        return true;
    }

    public boolean registerCompensation(String sagaId, CompensationInstruction instruction) {
        return registerCompensation(sagaId, instruction, null);
    }

    public boolean registerCompensation(String sagaId, CompensationInstruction instruction, String description) {
        if (instruction == null) {
            return false;
        }

        CompensationStack stack = activeSagas.get(sagaId);
        if (stack == null) {
            return false;
        }

        stack.push(instruction, description);
        return true;
    }

    public CompensationResult compensate(String sagaId) {
        long startTime = System.currentTimeMillis();
        CompensationStack stack = activeSagas.remove(sagaId);

        if (stack == null) {
            LOG.warning(() -> String.format("[SAGA_WARN] No active saga found: %s", sagaId));
            return new CompensationResult(sagaId, 0, 0, 0, List.of("Saga not found"), 0);
        }

        List<CompensationStack.CompensationEntry> entries = stack.drain();
        int total = entries.size();
        int successful = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        LOG.info(() -> String.format("[SAGA_COMPENSATE] %s - Starting %d compensations", sagaId, total));

        for (CompensationStack.CompensationEntry entry : entries) {
            CompensationInstruction instruction = entry.instruction();
            try {
                FNLResult<Void> result = executeCompensation(instruction);
                if (result.success()) {
                    successful++;
                    LOG.fine(() -> String.format("[SAGA_UNDO] %s.%s - SUCCESS",
                        instruction.skillName(), instruction.method()));
                } else {
                    failed++;
                    String error = String.format("%s.%s failed: %s",
                        instruction.skillName(), instruction.method(), result.error());
                    errors.add(error);
                    LOG.warning(() -> "[SAGA_UNDO] " + error);
                }
            } catch (Exception e) {
                failed++;
                String error = String.format("%s.%s threw: %s",
                    instruction.skillName(), instruction.method(), e.getMessage());
                errors.add(error);
                LOG.log(Level.WARNING, "[SAGA_UNDO] " + error, e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        compensatedSagas.incrementAndGet();

        CompensationResult result = new CompensationResult(
            sagaId, total, successful, failed, List.copyOf(errors), duration
        );

        LOG.info(() -> String.format("[SAGA_COMPLETE] %s - %d/%d compensations in %dms",
            sagaId, result.successfulCompensations(), result.totalCompensations(), duration));

        return result;
    }

    private FNLResult<Void> executeCompensation(CompensationInstruction instruction) {
        String skillName = instruction.skillName();

        Optional<ToolSkill> skillOpt = toolRegistry.findSkill(skillName);
        if (skillOpt.isEmpty()) {
            return FNLResult.failure("Skill not found: " + skillName);
        }

        ToolSkill skill = skillOpt.get();

        if (!(skill instanceof SagaCapableSkill sagaSkill)) {
            return FNLResult.failure("Skill does not support Saga: " + skillName);
        }

        if (!sagaSkill.canCompensate(instruction.method())) {
            return FNLResult.failure("Skill cannot compensate method: " + instruction.method());
        }

        return sagaSkill.compensate(instruction);
    }

    public Optional<CompensationStack> getStack(String sagaId) {
        return Optional.ofNullable(activeSagas.get(sagaId));
    }

    public int getCompensationCount(String sagaId) {
        CompensationStack stack = activeSagas.get(sagaId);
        return stack != null ? stack.size() : 0;
    }

    public Set<String> getActiveSagaIds() {
        return Set.copyOf(activeSagas.keySet());
    }

    public int getActiveSagaCount() {
        return activeSagas.size();
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSagas", totalSagas.get());
        stats.put("successfulSagas", successfulSagas.get());
        stats.put("compensatedSagas", compensatedSagas.get());
        stats.put("activeSagas", activeSagas.size());
        stats.put("activeSagaIds", getActiveSagaIds());
        return stats;
    }

    public void resetStatistics() {
        totalSagas.set(0);
        successfulSagas.set(0);
        compensatedSagas.set(0);
    }

    public boolean cancelSaga(String sagaId) {
        CompensationStack stack = activeSagas.remove(sagaId);
        if (stack != null) {
            LOG.warning(() -> String.format("[SAGA_CANCEL] %s (discarded %d compensations)",
                sagaId, stack.size()));
            return true;
        }
        return false;
    }

    public boolean markAsCompensated(String sagaId) {
        CompensationStack stack = activeSagas.remove(sagaId);
        if (stack != null) {
            int compensations = stack.size();
            stack.clear();
            compensatedSagas.incrementAndGet();
            LOG.info(() -> String.format("[SAGA_MARK_COMPENSATED] %s (manual rollback, %d compensations cleared)",
                sagaId, compensations));
            return true;
        }
        return false;
    }

    public int clearAll() {
        int count = activeSagas.size();
        activeSagas.clear();
        LOG.warning(() -> String.format("[SAGA_CLEAR_ALL] Cleared %d sagas", count));
        return count;
    }

    @Override
    public String toString() {
        return String.format("SagaOrchestrator[active=%d, total=%d, successful=%d, compensated=%d]",
            activeSagas.size(), totalSagas.get(), successfulSagas.get(), compensatedSagas.get());
    }
}
