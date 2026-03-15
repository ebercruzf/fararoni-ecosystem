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

import dev.fararoni.core.core.utils.TokenUtils;

import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ContextStrategySelector {
    private static final ContextStrategySelector INSTANCE = new ContextStrategySelector();

    private ContextStrategySelector() {
    }

    public static ContextStrategySelector getInstance() {
        return INSTANCE;
    }

    public PruningStrategy selectStrategy(UserIntent intent, String fullContext, String targetMethod) {
        Objects.requireNonNull(intent, "intent no puede ser null");
        Objects.requireNonNull(fullContext, "fullContext no puede ser null");

        int contextSize = fullContext.length();

        if (contextSize <= TokenUtils.RABBIT_MAX_CHARS) {
            return PruningStrategy.FULL_FIDELITY;
        }

        return switch (intent) {
            case EXPLAIN, AUDIT, DOCUMENTATION -> selectSkeletonStrategy(fullContext);

            case REFACTOR, DEBUG, WRITE_TESTS -> selectFocalStrategy(fullContext, targetMethod);

            case CODE_GEN -> selectInterfaceStrategy(fullContext);

            case GENERAL -> PruningStrategy.ABORT;
        };
    }

    private PruningStrategy selectSkeletonStrategy(String fullContext) {
        IntentAwarePruner pruner = IntentAwarePruner.getInstance();

        try {
            int skelSize = pruner.estimateSize(fullContext, PruningStrategy.SKELETON_ONLY, null);

            if (skelSize > 0 && skelSize <= TokenUtils.RABBIT_MAX_CHARS) {
                return PruningStrategy.SKELETON_ONLY;
            }
        } catch (Exception e) {
        }

        return PruningStrategy.ABORT;
    }

    private PruningStrategy selectFocalStrategy(String fullContext, String targetMethod) {
        if (targetMethod == null || targetMethod.isBlank()) {
            return PruningStrategy.ABORT;
        }

        IntentAwarePruner pruner = IntentAwarePruner.getInstance();

        try {
            int focalSize = pruner.estimateSize(fullContext, PruningStrategy.FOCAL_POINT, targetMethod);

            if (focalSize > 0 && focalSize <= TokenUtils.RABBIT_MAX_CHARS) {
                return PruningStrategy.FOCAL_POINT;
            }
        } catch (Exception e) {
        }

        return PruningStrategy.ABORT;
    }

    private PruningStrategy selectInterfaceStrategy(String fullContext) {
        IntentAwarePruner pruner = IntentAwarePruner.getInstance();

        try {
            int interfaceSize = pruner.estimateSize(fullContext, PruningStrategy.INTERFACE_MODE, null);

            if (interfaceSize > 0 && interfaceSize <= TokenUtils.RABBIT_MAX_CHARS) {
                return PruningStrategy.INTERFACE_MODE;
            }
        } catch (Exception e) {
        }

        return PruningStrategy.ABORT;
    }

    public boolean canRabbitHandle(String fullContext, UserIntent intent) {
        PruningStrategy strategy = selectStrategy(intent, fullContext, null);
        return strategy.canProceed();
    }

    public String debugSelection(UserIntent intent, String fullContext, String targetMethod) {
        int contextSize = fullContext.length();
        PruningStrategy selected = selectStrategy(intent, fullContext, targetMethod);

        return String.format(
            "FSM Decision: intent=%s, contextSize=%d, target=%s → strategy=%s (canProceed=%s)",
            intent,
            contextSize,
            targetMethod != null ? targetMethod : "null",
            selected,
            selected.canProceed()
        );
    }
}
