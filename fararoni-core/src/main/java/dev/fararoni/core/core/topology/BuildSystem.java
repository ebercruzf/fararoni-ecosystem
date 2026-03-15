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
package dev.fararoni.core.core.topology;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public enum BuildSystem {

    MAVEN("Maven", "src/main/java", List.of("pom.xml")),

    GRADLE("Gradle", "src/main/java", List.of("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")),

    NPM("NPM", "src", List.of("package.json")),

    PIP("Python", "src", List.of("requirements.txt", "pyproject.toml", "setup.py", "setup.cfg")),

    CARGO("Cargo", "src", List.of("Cargo.toml")),

    GO("Go", ".", List.of("go.mod")),

    DOTNET("DotNet", ".", List.of("*.csproj", "*.sln", "*.fsproj")),

    UNKNOWN("Unknown", ".", List.of());

    private final String displayName;
    private final String defaultSourceRoot;
    private final List<String> markerFiles;

    BuildSystem(String displayName, String defaultSourceRoot, List<String> markerFiles) {
        this.displayName = displayName;
        this.defaultSourceRoot = defaultSourceRoot;
        this.markerFiles = markerFiles;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultSourceRoot() {
        return defaultSourceRoot;
    }

    public List<String> getMarkerFiles() {
        return markerFiles;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public boolean usesJavaPackages() {
        return this == MAVEN || this == GRADLE;
    }

    public boolean usesPythonModules() {
        return this == PIP;
    }
}
