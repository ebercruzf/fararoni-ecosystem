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

import dev.fararoni.core.core.safety.AtomicFileWriter;
import dev.fararoni.core.core.topology.ProjectTopologyScanner;
import dev.fararoni.core.enterprise.git.GitService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class StructureMaterializer {

    private final AtomicFileWriter atomicWriter;
    private final ProjectTopologyScanner topologyScanner;

    public StructureMaterializer() {
        this.atomicWriter = new AtomicFileWriter();
        this.topologyScanner = new ProjectTopologyScanner();
    }

    public StructureMaterializer(AtomicFileWriter atomicWriter,
                                  ProjectTopologyScanner topologyScanner) {
        this.atomicWriter = Objects.requireNonNull(atomicWriter);
        this.topologyScanner = Objects.requireNonNull(topologyScanner);
    }

    public MaterializeResult materialize(Path root, ProjectBlueprint blueprint) {
        Objects.requireNonNull(root, "root no puede ser null");
        Objects.requireNonNull(blueprint, "blueprint no puede ser null");

        Path absoluteRoot = root.toAbsolutePath().normalize();

        if (Files.exists(absoluteRoot) && !topologyScanner.isEmpty(absoluteRoot)) {
            return MaterializeResult.failure(absoluteRoot,
                "El directorio no esta vacio. Usa un directorio vacio o nuevo.");
        }

        List<String> createdFiles = new ArrayList<>();
        Set<Path> createdDirs = new HashSet<>();
        int filesCreated = 0;
        int dirsCreated = 0;

        try {
            if (!Files.exists(absoluteRoot)) {
                Files.createDirectories(absoluteRoot);
                createdDirs.add(absoluteRoot);
                dirsCreated++;
            }

            for (FileEntry entry : blueprint.files()) {
                Path filePath = absoluteRoot.resolve(entry.path());

                Path parent = filePath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                    Path relative = absoluteRoot.relativize(parent);
                    for (int i = 0; i < relative.getNameCount(); i++) {
                        Path subPath = absoluteRoot.resolve(relative.subpath(0, i + 1));
                        if (createdDirs.add(subPath)) {
                            dirsCreated++;
                        }
                    }
                }

                atomicWriter.writeAtomic(filePath, entry.content());
                createdFiles.add(entry.path());
                filesCreated++;

                if (entry.executable()) {
                    makeExecutable(filePath);
                }
            }

            Optional<String> commitHash = Optional.empty();
            if (blueprint.initGit()) {
                commitHash = initializeGit(absoluteRoot, blueprint.name());
            }

            return new MaterializeResult(
                true,
                absoluteRoot,
                filesCreated,
                dirsCreated,
                createdFiles,
                commitHash,
                Optional.empty()
            );

        } catch (Exception e) {
            rollbackCreation(absoluteRoot, createdFiles, createdDirs);

            return MaterializeResult.failure(absoluteRoot,
                "Error materializando proyecto: " + e.getMessage());
        }
    }

    public MaterializeResult materializeInSubdir(Path parentDir, ProjectBlueprint blueprint) {
        Path projectDir = parentDir.resolve(blueprint.name());
        return materialize(projectDir, blueprint);
    }

    public boolean canMaterialize(Path root, ProjectBlueprint blueprint) {
        if (Files.exists(root) && !topologyScanner.isEmpty(root)) {
            return false;
        }

        for (FileEntry entry : blueprint.files()) {
            Path filePath = root.resolve(entry.path());
            if (Files.exists(filePath)) {
                return false;
            }
        }

        return true;
    }

    private void rollbackCreation(Path root, List<String> files, Set<Path> dirs) {
        for (String file : files) {
            try {
                Files.deleteIfExists(root.resolve(file));
            } catch (IOException ignored) {
            }
        }

        List<Path> sortedDirs = new ArrayList<>(dirs);
        sortedDirs.sort(Comparator.comparingInt(Path::getNameCount).reversed());

        for (Path dir : sortedDirs) {
            try {
                if (isDirectoryEmpty(dir)) {
                    Files.deleteIfExists(dir);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void makeExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException e) {
        }
    }

    private Optional<String> initializeGit(Path root, String projectName) {
        try {
            GitService git = new GitService(root);

            ProcessBuilder pb = new ProcessBuilder("git", "init");
            pb.directory(root.toFile());
            Process p = pb.start();
            int exitCode = p.waitFor();

            if (exitCode != 0) {
                return Optional.empty();
            }

            GitService.CommitResult result = git.stageAndCommit(
                List.of("."),
                "feat(init): " + projectName + " - initial scaffold"
            );

            if (result.success()) {
                return Optional.ofNullable(result.shortHash());
            }

            return Optional.empty();

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean isDirectoryEmpty(Path dir) {
        if (!Files.isDirectory(dir)) {
            return true;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }
}
