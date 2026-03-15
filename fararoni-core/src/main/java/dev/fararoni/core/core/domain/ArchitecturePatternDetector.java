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
package dev.fararoni.core.core.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public final class ArchitecturePatternDetector {

    private ArchitecturePatternDetector() {}

    public enum LayerType {
        MODEL("model"),
        DOMAIN("domain"),
        ENTITY("entity"),
        CORE("core");

        final String dirName;

        LayerType(String n) {
            this.dirName = n;
        }

        public String getDirName() {
            return dirName;
        }
    }

    public static Optional<String> suggestLayer(Path rootDir) {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return Optional.empty();
        }

        try (Stream<Path> walk = Files.walk(rootDir, 3)) {
            List<String> existingDirs = walk
                .filter(Files::isDirectory)
                .map(p -> p.getFileName().toString().toLowerCase())
                .toList();

            if (existingDirs.contains("domain")) return Optional.of("domain");
            if (existingDirs.contains("model")) return Optional.of("model");
            if (existingDirs.contains("entity")) return Optional.of("entity");
            if (existingDirs.contains("core")) return Optional.of("core");

        } catch (Exception e) {
        }

        return Optional.empty();
    }
}
