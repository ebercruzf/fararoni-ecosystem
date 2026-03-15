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

import dev.fararoni.core.core.indexing.model.LineRange;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record EditBlock(
    String id,
    String search,
    String replace,
    int estimatedLine,
    int startIndex,
    int endIndex
) {
    public EditBlock {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser null o vacio");
        }
        if (search == null || search.isEmpty()) {
            throw new IllegalArgumentException("search no puede ser null o vacio");
        }
        if (replace == null) {
            throw new IllegalArgumentException("replace no puede ser null");
        }
        if (estimatedLine < 0) {
            throw new IllegalArgumentException("estimatedLine no puede ser negativo");
        }
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex no puede ser negativo");
        }
        if (endIndex < 0) {
            throw new IllegalArgumentException("endIndex no puede ser negativo");
        }
    }

    public static EditBlock of(String id, String search, String replace, int estimatedLine) {
        return new EditBlock(id, search, replace, estimatedLine, 0, 0);
    }

    public EditBlock withOffsets(int startIndex, int endIndex) {
        return new EditBlock(this.id, this.search, this.replace, this.estimatedLine, startIndex, endIndex);
    }

    public int sizeDelta() {
        return replace.length() - search.length();
    }

    public boolean isDeletion() {
        return replace.isEmpty();
    }

    public boolean isInsertion() {
        return search.length() <= 1 && replace.length() > search.length();
    }

    public int lineCount() {
        return (int) search.chars().filter(ch -> ch == '\n').count() + 1;
    }
}
