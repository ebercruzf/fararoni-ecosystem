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

import dev.fararoni.bus.agent.api.saga.CompensationInstruction;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class CompensationStack {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private final String sagaId;
    private final Deque<CompensationEntry> stack;
    private final long createdAt;

    public record CompensationEntry(
        long sequence,
        long timestamp,
        CompensationInstruction instruction,
        String operationDescription
    ) {
        public CompensationEntry {
            Objects.requireNonNull(instruction, "instruction cannot be null");
        }

        public static CompensationEntry of(CompensationInstruction instruction, String description) {
            return new CompensationEntry(
                ID_GENERATOR.incrementAndGet(),
                System.currentTimeMillis(),
                instruction,
                description
            );
        }
    }

    public CompensationStack(String sagaId) {
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId cannot be null");
        this.stack = new ConcurrentLinkedDeque<>();
        this.createdAt = System.currentTimeMillis();
    }

    public CompensationStack() {
        this("saga-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public CompensationStack push(CompensationInstruction instruction) {
        return push(instruction, null);
    }

    public CompensationStack push(CompensationInstruction instruction, String description) {
        stack.push(CompensationEntry.of(instruction, description));
        return this;
    }

    public CompensationInstruction pop() {
        CompensationEntry entry = stack.poll();
        return entry != null ? entry.instruction() : null;
    }

    public CompensationEntry popEntry() {
        return stack.poll();
    }

    public CompensationInstruction peek() {
        CompensationEntry entry = stack.peek();
        return entry != null ? entry.instruction() : null;
    }

    public CompensationEntry peekEntry() {
        return stack.peek();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int size() {
        return stack.size();
    }

    public List<CompensationEntry> getEntries() {
        return List.copyOf(stack);
    }

    public List<CompensationInstruction> getInstructions() {
        return stack.stream()
            .map(CompensationEntry::instruction)
            .toList();
    }

    public List<CompensationEntry> drain() {
        List<CompensationEntry> result = new ArrayList<>();
        CompensationEntry entry;
        while ((entry = stack.poll()) != null) {
            result.add(entry);
        }
        return result;
    }

    public void clear() {
        stack.clear();
    }

    public String getSagaId() {
        return sagaId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - createdAt;
    }

    public Map<String, Object> toSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sagaId", sagaId);
        snapshot.put("createdAt", createdAt);
        snapshot.put("size", stack.size());
        snapshot.put("entries", stack.stream()
            .map(e -> Map.of(
                "sequence", e.sequence(),
                "timestamp", e.timestamp(),
                "skill", e.instruction().skillName(),
                "method", e.instruction().method(),
                "params", e.instruction().params(),
                "description", e.operationDescription() != null ? e.operationDescription() : ""
            ))
            .toList());
        return snapshot;
    }

    @Override
    public String toString() {
        return String.format("CompensationStack[id=%s, size=%d, age=%dms]",
            sagaId, stack.size(), getAgeMs());
    }
}
