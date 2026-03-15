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
package dev.fararoni.core.core.hybrid;

import java.util.Objects;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DraftResult {
    private final boolean success;
    private final String draft;
    private final String skipReason;
    private final PruningStrategy strategyUsed;
    private final int contextSizeChars;

    private DraftResult(boolean success, String draft, String skipReason,
                        PruningStrategy strategyUsed, int contextSizeChars) {
        this.success = success;
        this.draft = draft;
        this.skipReason = skipReason;
        this.strategyUsed = strategyUsed;
        this.contextSizeChars = contextSizeChars;
    }

    public static DraftResult success(String draft, PruningStrategy strategyUsed, int contextSizeChars) {
        Objects.requireNonNull(draft, "draft no puede ser null");
        return new DraftResult(true, draft, null, strategyUsed, contextSizeChars);
    }

    public static DraftResult success(String draft) {
        return success(draft, PruningStrategy.FULL_FIDELITY, -1);
    }

    public static DraftResult skipped(String reason) {
        Objects.requireNonNull(reason, "reason no puede ser null");
        return new DraftResult(false, null, reason, PruningStrategy.ABORT, -1);
    }

    public static DraftResult skipped(String reason, int contextSizeChars) {
        Objects.requireNonNull(reason, "reason no puede ser null");
        return new DraftResult(false, null, reason, PruningStrategy.ABORT, contextSizeChars);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isSkipped() {
        return !success;
    }

    public Optional<String> getDraft() {
        return Optional.ofNullable(draft);
    }

    public Optional<String> getSkipReason() {
        return Optional.ofNullable(skipReason);
    }

    public PruningStrategy getStrategyUsed() {
        return strategyUsed;
    }

    public int getContextSizeChars() {
        return contextSizeChars;
    }

    public String getDraftOrThrow() {
        if (!success || draft == null) {
            throw new IllegalStateException("No draft available: " + skipReason);
        }
        return draft;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("DraftResult[SUCCESS, strategy=%s, contextSize=%d, draftLength=%d]",
                strategyUsed, contextSizeChars, draft != null ? draft.length() : 0);
        } else {
            return String.format("DraftResult[SKIPPED, reason='%s', contextSize=%d]",
                skipReason, contextSizeChars);
        }
    }
}
