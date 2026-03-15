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
package dev.fararoni.core.core.indexing.model;

import dev.fararoni.core.core.indexing.SentinelJavaParser;
import dev.fararoni.core.core.surgical.SurgicalEditor;

/**
 * @author Eber Cruz
 * @version 1.0.0 (Ciclo 7 - Fase 0.1: Agregados startOffset/endOffset)
 * @since 1.0.0
 */
public record LineRange(int startLine, int endLine, int startColumn, int endColumn, int startOffset, int endOffset) {
    public boolean contains(int line) {
        return line >= startLine && line <= endLine;
    }

    public boolean overlaps(LineRange other) {
        if (other == null) {
            return false;
        }
        return this.startLine <= other.endLine && this.endLine >= other.startLine;
    }

    public int lineCount() {
        return Math.max(0, endLine - startLine + 1);
    }

    public boolean isContainedIn(LineRange outer) {
        if (outer == null) {
            return false;
        }
        return this.startLine >= outer.startLine && this.endLine <= outer.endLine;
    }
}
