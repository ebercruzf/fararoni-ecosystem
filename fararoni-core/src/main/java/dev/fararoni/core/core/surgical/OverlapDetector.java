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

import java.util.*;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.2.0
 * @since 1.0.0
 */
public class OverlapDetector {
    private static final Logger logger = Logger.getLogger(OverlapDetector.class.getName());

    public void validate(List<EditBlock> edits) throws OverlapConflictException {
        List<EditBlock> sortedEdits = new ArrayList<>(edits);
        sortedEdits.sort(Comparator.comparingInt(EditBlock::estimatedLine));

        for (int i = 0; i < sortedEdits.size() - 1; i++) {
            EditBlock current = sortedEdits.get(i);
            EditBlock next = sortedEdits.get(i + 1);

            if (hasOverlap(current, next)) {
                if (isSubset(current, next)) {
                    throw new OverlapConflictException(
                        "REDUNDANT", current, next,
                        "El bloque " + next.id() + " es redundante dentro de " + current.id()
                    );
                }

                throw new OverlapConflictException(
                    "COLLISION", current, next,
                    String.format("Conflicto de escritura en lineas %d-%d. Fusion requerida.",
                    current.estimatedLine(), next.estimatedLine())
                );
            }
        }
    }

    private boolean hasOverlap(EditBlock a, EditBlock b) {
        int aStart = a.estimatedLine();
        int aEnd = aStart + a.lineCount();
        int bStart = b.estimatedLine();

        return bStart < aEnd;
    }

    private boolean isSubset(EditBlock a, EditBlock b) {
        return a.search().contains(b.search()) || b.search().contains(a.search());
    }

    public List<EditBlock> optimizeRedundancies(List<EditBlock> edits) {
        if (edits == null || edits.size() < 2) {
            return edits == null ? List.of() : new ArrayList<>(edits);
        }

        List<EditBlock> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(e -> -e.search().length()));

        List<EditBlock> accepted = new ArrayList<>();

        for (EditBlock candidate : sorted) {
            EditBlock parentBlock = null;
            for (EditBlock existing : accepted) {
                if (existing.search().contains(candidate.search())) {
                    parentBlock = existing;
                    break;
                }
            }

            if (parentBlock != null) {
                logger.warning(String.format(
                    "AUTO-FIX: Eliminando bloque redundante '%s' contenido en '%s'",
                    candidate.id(), parentBlock.id()));
            } else {
                accepted.add(candidate);
            }
        }

        return accepted;
    }

    public void validateCollisions(List<EditBlock> edits) throws OverlapConflictException {
        if (edits == null || edits.size() < 2) {
            return;
        }

        List<EditBlock> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(EditBlock::estimatedLine));

        for (int i = 0; i < sorted.size() - 1; i++) {
            EditBlock current = sorted.get(i);
            EditBlock next = sorted.get(i + 1);

            if (hasOverlap(current, next) && !isSubset(current, next)) {
                throw new OverlapConflictException(
                    "COLLISION", current, next,
                    String.format("Conflicto de escritura en lineas %d-%d. Fusion requerida.",
                    current.estimatedLine(), next.estimatedLine())
                );
            }
        }
    }
}
