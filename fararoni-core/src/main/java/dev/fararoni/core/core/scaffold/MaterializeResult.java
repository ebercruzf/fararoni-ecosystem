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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record MaterializeResult(
    boolean success,
    Path rootPath,
    int filesCreated,
    int directoriesCreated,
    List<String> createdFiles,
    Optional<String> gitCommitHash,
    Optional<String> error
) {

    public MaterializeResult {
        if (createdFiles == null) {
            createdFiles = List.of();
        }
        if (gitCommitHash == null) {
            gitCommitHash = Optional.empty();
        }
        if (error == null) {
            error = Optional.empty();
        }
    }

    public static MaterializeResult success(Path rootPath, int filesCreated,
                                            int directoriesCreated, List<String> createdFiles) {
        return new MaterializeResult(
            true,
            rootPath,
            filesCreated,
            directoriesCreated,
            createdFiles,
            Optional.empty(),
            Optional.empty()
        );
    }

    public static MaterializeResult successWithGit(Path rootPath, int filesCreated,
                                                   int directoriesCreated, List<String> createdFiles,
                                                   String commitHash) {
        return new MaterializeResult(
            true,
            rootPath,
            filesCreated,
            directoriesCreated,
            createdFiles,
            Optional.ofNullable(commitHash),
            Optional.empty()
        );
    }

    public static MaterializeResult failure(Path rootPath, String errorMessage) {
        return new MaterializeResult(
            false,
            rootPath,
            0,
            0,
            List.of(),
            Optional.empty(),
            Optional.of(errorMessage)
        );
    }

    public String toSummary() {
        StringBuilder sb = new StringBuilder();

        if (success) {
            sb.append("Proyecto creado exitosamente en: ").append(rootPath).append("\n");
            sb.append("  - Directorios: ").append(directoriesCreated).append("\n");
            sb.append("  - Archivos: ").append(filesCreated).append("\n");

            gitCommitHash.ifPresent(hash ->
                sb.append("  - Git commit: ").append(hash).append("\n")
            );

            if (!createdFiles.isEmpty() && createdFiles.size() <= 10) {
                sb.append("\nArchivos creados:\n");
                createdFiles.forEach(f -> sb.append("  ").append(f).append("\n"));
            }
        } else {
            sb.append("Error creando proyecto: ");
            error.ifPresent(sb::append);
        }

        return sb.toString();
    }

    public boolean hasGit() {
        return gitCommitHash.isPresent();
    }

    public int totalCreated() {
        return filesCreated + directoriesCreated;
    }
}
