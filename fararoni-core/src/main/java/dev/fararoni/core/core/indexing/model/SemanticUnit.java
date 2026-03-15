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

import dev.fararoni.core.core.indexing.SemanticChunker;
import dev.fararoni.core.core.indexing.SentinelVisitor;

import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record SemanticUnit(
    String type,
    String signature,
    String content,
    int startLine,
    int endLine,
    Set<String> usedFields,
    boolean isOverride
) {
    public static final String TYPE_IMPORT = "IMPORT";

    public static final String TYPE_CLASS = "CLASS";

    public static final String TYPE_FIELD = "FIELD";

    public static final String TYPE_CONSTRUCTOR = "CONSTRUCTOR";

    public static final String TYPE_METHOD = "METHOD";

    public static final String TYPE_LAMBDA = "LAMBDA";

    public int tokenEstimate() {
        return content != null ? content.length() / 4 : 0;
    }

    public int lineCount() {
        return Math.max(0, endLine - startLine + 1);
    }

    public boolean hasDependencies() {
        return usedFields != null && !usedFields.isEmpty();
    }

    public boolean isMethod() {
        return TYPE_METHOD.equals(type);
    }

    public boolean isField() {
        return TYPE_FIELD.equals(type);
    }

    public boolean isClass() {
        return TYPE_CLASS.equals(type);
    }

    public boolean isOverrideMethod() {
        return isMethod() && isOverride;
    }

    public static SemanticUnit of(
            String type,
            String signature,
            String content,
            int startLine,
            int endLine,
            Set<String> usedFields) {
        return new SemanticUnit(type, signature, content, startLine, endLine, usedFields, false);
    }
}
