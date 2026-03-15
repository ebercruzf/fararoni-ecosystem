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
package dev.fararoni.core.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class FilesystemService {
    private static final Set<Character> DANGEROUS_CHARS = Set.of(
        ';', '|', '&', '$', '`', '>', '<', '!', '\n', '\r', '\0'
    );

    private static final List<String> BLOCKED_PREFIXES = List.of(
        "/etc", "/usr", "/bin", "/sbin", "/dev",
        "/proc", "/sys", "/boot", "/root", "/lib", "/lib64",
        "C:\\Windows", "C:\\Program Files"
    );

    private final Path workingDirectory;

    public FilesystemService(Path workingDirectory) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public WriteResult writeFile(String relativePath, String content) {
        if (content == null) {
            content = "";
        }

        Path targetPath = resolveSafePath(relativePath);
        if (targetPath == null) {
            return WriteResult.error("Ruta no permitida: " + relativePath);
        }

        try {
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            if (parent != null && !Files.isWritable(parent)) {
                return WriteResult.error("Sin permisos de escritura en: " + parent);
            }

            Files.writeString(targetPath, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return WriteResult.success(targetPath, content.length());
        } catch (IOException e) {
            return WriteResult.error("Error escribiendo archivo: " + e.getMessage());
        }
    }

    public WriteResult appendFile(String relativePath, String content) {
        Path targetPath = resolveSafePath(relativePath);
        if (targetPath == null) {
            return WriteResult.error("Ruta no permitida: " + relativePath);
        }

        try {
            Files.writeString(targetPath, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            return WriteResult.success(targetPath, content.length());
        } catch (IOException e) {
            return WriteResult.error("Error agregando a archivo: " + e.getMessage());
        }
    }

    public WriteResult createDirectory(String relativePath) {
        Path targetPath = resolveSafePath(relativePath);
        if (targetPath == null) {
            return WriteResult.error("Ruta no permitida: " + relativePath);
        }

        try {
            Files.createDirectories(targetPath);
            return WriteResult.success(targetPath, 0);
        } catch (IOException e) {
            return WriteResult.error("Error creando directorio: " + e.getMessage());
        }
    }

    private Path resolveSafePath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            return null;
        }

        for (char c : inputPath.toCharArray()) {
            if (DANGEROUS_CHARS.contains(c)) {
                return null;
            }
        }

        if (inputPath.contains("..")) {
            return null;
        }

        Path resolved;
        try {
            Path inputAsPath = Path.of(inputPath);
            if (inputAsPath.isAbsolute()) {
                resolved = inputAsPath.normalize();
            } else {
                resolved = workingDirectory.resolve(inputPath).normalize();
            }
        } catch (Exception e) {
            return null;
        }

        String pathStr = resolved.toString();
        for (String blocked : BLOCKED_PREFIXES) {
            if (pathStr.startsWith(blocked)) {
                return null;
            }
        }

        return resolved;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public boolean isPathSafe(String relativePath) {
        return resolveSafePath(relativePath) != null;
    }

    public boolean fileExists(String relativePath) {
        Path targetPath = resolveSafePath(relativePath);
        return targetPath != null && Files.exists(targetPath);
    }
}
