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
package dev.fararoni.core.core.surgical;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class OverlapConflictException extends SurgicalException {
    private final String type;
    private final EditBlock blockA;
    private final EditBlock blockB;

    public OverlapConflictException(String type, EditBlock a, EditBlock b, String message) {
        super(message);
        this.type = type;
        this.blockA = a;
        this.blockB = b;
    }

    public String getPromptHint() {
        return String.format(
            "ERROR: Tus bloques '%s' y '%s' se solapan. NO envies multiples bloques para la misma zona. " +
            "Fusiona ambos cambios en UN SOLO bloque SEARCH/REPLACE que abarque todo el contexto.",
            blockA.search(), blockB.search()
        );
    }

    public String getType() {
        return type;
    }

    public EditBlock getBlockA() {
        return blockA;
    }

    public EditBlock getBlockB() {
        return blockB;
    }

    public List<EditBlock> getConflictingBlocks() {
        return List.of(blockA, blockB);
    }
}
