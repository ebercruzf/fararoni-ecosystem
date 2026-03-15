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
package dev.fararoni.core.core.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ProjectContextExtractor {
    private static final Logger LOG = Logger.getLogger(ProjectContextExtractor.class.getName());

    private ProjectContextExtractor() {
    }

    public static Path extractProjectRoot(Set<String> filePaths, Path workingDir) {
        if (filePaths == null || filePaths.isEmpty()) {
            LOG.fine("No hay rutas de archivos, usando workingDir");
            return workingDir;
        }

        if (isValidProjectRoot(workingDir)) {
            LOG.info("DENTRO de proyecto existente: " + workingDir);
            return workingDir;
        }

        String firstPath = filePaths.iterator().next();
        Path path = Paths.get(firstPath);

        if (path.getNameCount() > 0) {
            String projectName = path.getName(0).toString();

            Path projectRoot = workingDir.resolve(projectName)
                                         .toAbsolutePath()
                                         .normalize();

            LOG.info("NUEVO proyecto: " + projectRoot +
                     " (existe=" + Files.isDirectory(projectRoot) + ")");

            return projectRoot;
        }

        LOG.fine("Sin segmentos, fallback a workingDir: " + workingDir);
        return workingDir;
    }

    public static boolean isValidProjectRoot(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }

        return Files.exists(dir.resolve("pom.xml"))
            || Files.exists(dir.resolve("build.gradle"))
            || Files.exists(dir.resolve("build.gradle.kts"))
            || Files.exists(dir.resolve("package.json"))
            || Files.exists(dir.resolve("requirements.txt"))
            || Files.exists(dir.resolve("pyproject.toml"))
            || Files.exists(dir.resolve("setup.py"))
            || Files.exists(dir.resolve("go.mod"))
            || Files.exists(dir.resolve("Cargo.toml"))
            || Files.exists(dir.resolve("Makefile"))
            || Files.exists(dir.resolve("CMakeLists.txt"));
    }

    public static Path extractProjectRoot(Set<String> filePaths) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        return extractProjectRoot(filePaths, workingDir);
    }
}
