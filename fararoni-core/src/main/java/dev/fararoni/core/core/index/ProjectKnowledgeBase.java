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
package dev.fararoni.core.core.index;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface ProjectKnowledgeBase extends AutoCloseable {
    @Override
    void close() throws Exception;

    enum ContextProfile {
        SKELETAL(2, 25),

        TACTICAL(4, 80),

        STRATEGIC(10, 300);

        public final int maxDepth;

        public final int maxLines;

        ContextProfile(int maxDepth, int maxLines) {
            this.maxDepth = maxDepth;
            this.maxLines = maxLines;
        }
    }

    boolean isAvailable();

    void refresh();

    String generateTreeView(String rootPath);

    String generateHighLevelMap();

    String generateMap(ContextProfile profile);

    void registerFile(java.nio.file.Path absolutePath);

    default String generateTreeView() {
        return generateTreeView(".");
    }

    default boolean isUnavailable() {
        return !isAvailable();
    }
}
