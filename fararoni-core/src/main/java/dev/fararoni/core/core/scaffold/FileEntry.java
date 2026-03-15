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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record FileEntry(
    String path,
    String content,
    boolean executable
) {

    public FileEntry {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path no puede ser null o vacio");
        }
        if (content == null) {
            content = "";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
    }

    public static FileEntry of(String path, String content) {
        return new FileEntry(path, content, false);
    }

    public static FileEntry executable(String path, String content) {
        return new FileEntry(path, content, true);
    }

    public static FileEntry empty(String path) {
        return new FileEntry(path, "", false);
    }

    public String getExtension() {
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(dot) : "";
    }

    public String getFileName() {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public String getDirectory() {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash > 0 ? path.substring(0, slash) : "";
    }

    public boolean isConfigFile() {
        String name = getFileName().toLowerCase();
        return name.equals("package.json") ||
               name.equals("pom.xml") ||
               name.equals("build.gradle") ||
               name.equals("build.gradle.kts") ||
               name.equals("cargo.toml") ||
               name.equals("go.mod") ||
               name.equals("requirements.txt") ||
               name.endsWith(".toml") ||
               name.endsWith(".yaml") ||
               name.endsWith(".yml");
    }

    public boolean isSourceFile() {
        String ext = getExtension().toLowerCase();
        return ext.equals(".java") ||
               ext.equals(".kt") ||
               ext.equals(".py") ||
               ext.equals(".js") ||
               ext.equals(".ts") ||
               ext.equals(".go") ||
               ext.equals(".rs") ||
               ext.equals(".c") ||
               ext.equals(".cpp") ||
               ext.equals(".cs");
    }
}
