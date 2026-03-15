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
package dev.fararoni.core.core.scaffold;

import dev.fararoni.core.core.topology.BuildSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ProjectBlueprint(
    String name,
    String description,
    BuildSystem buildSystem,
    List<FileEntry> files,
    boolean initGit
) {

    public ProjectBlueprint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name no puede ser null o vacio");
        }
        if (description == null) {
            description = "";
        }
        if (buildSystem == null) {
            buildSystem = BuildSystem.UNKNOWN;
        }
        if (files == null) {
            files = List.of();
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static ProjectBlueprint of(String name, List<FileEntry> files) {
        return new ProjectBlueprint(name, "", BuildSystem.UNKNOWN, files, false);
    }

    public static ProjectBlueprint withGit(String name, List<FileEntry> files) {
        return new ProjectBlueprint(name, "", BuildSystem.UNKNOWN, files, true);
    }

    public int fileCount() {
        return files.size();
    }

    public int sourceFileCount() {
        return (int) files.stream()
            .filter(FileEntry::isSourceFile)
            .count();
    }

    public int configFileCount() {
        return (int) files.stream()
            .filter(FileEntry::isConfigFile)
            .count();
    }

    public List<String> getDirectories() {
        return files.stream()
            .map(FileEntry::getDirectory)
            .filter(dir -> !dir.isEmpty())
            .distinct()
            .sorted()
            .toList();
    }

    public boolean hasFileType(String extension) {
        return files.stream()
            .anyMatch(f -> f.getExtension().equalsIgnoreCase(extension));
    }

    public static class Builder {
        private final String name;
        private String description = "";
        private BuildSystem buildSystem = BuildSystem.UNKNOWN;
        private final List<FileEntry> files = new ArrayList<>();
        private boolean initGit = false;

        private Builder(String name) {
            this.name = name;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder buildSystem(BuildSystem buildSystem) {
            this.buildSystem = buildSystem;
            return this;
        }

        public Builder addFile(String path, String content) {
            files.add(FileEntry.of(path, content));
            return this;
        }

        public Builder addFile(FileEntry entry) {
            files.add(entry);
            return this;
        }

        public Builder addExecutable(String path, String content) {
            files.add(FileEntry.executable(path, content));
            return this;
        }

        public Builder addFiles(List<FileEntry> entries) {
            files.addAll(entries);
            return this;
        }

        public Builder withGit() {
            this.initGit = true;
            return this;
        }

        public Builder withGit(boolean init) {
            this.initGit = init;
            return this;
        }

        public ProjectBlueprint build() {
            return new ProjectBlueprint(name, description, buildSystem, List.copyOf(files), initGit);
        }
    }
}
