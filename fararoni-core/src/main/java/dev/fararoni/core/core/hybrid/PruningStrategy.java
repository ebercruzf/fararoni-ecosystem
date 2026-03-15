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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public enum PruningStrategy {
    FULL_FIDELITY("Contexto completo sin poda", true),

    SKELETON_ONLY("Solo firmas y estructura", true),

    FOCAL_POINT("Ventana focal AST + campos de clase", true),

    INTERFACE_MODE("Solo interfaces y DTOs", true),

    ABORT("Delegar a Tortuga (32B)", false);

    private final String description;
    private final boolean canProceed;

    PruningStrategy(String description, boolean canProceed) {
        this.description = description;
        this.canProceed = canProceed;
    }

    public String getDescription() {
        return description;
    }

    public boolean canProceed() {
        return canProceed;
    }

    public boolean requiresTarget() {
        return this == FOCAL_POINT;
    }

    public boolean usesAst() {
        return this == FOCAL_POINT || this == INTERFACE_MODE;
    }
}
