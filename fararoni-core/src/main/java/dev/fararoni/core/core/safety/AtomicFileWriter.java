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
package dev.fararoni.core.core.safety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class AtomicFileWriter {
    private static final String TEMP_SUFFIX = ".fararoni-tmp";

    public void writeAtomic(Path target, String content) {
        Objects.requireNonNull(target, "target no puede ser null");
        Objects.requireNonNull(content, "content no puede ser null");

        Path tempFile = createTempPath(target);

        try {
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(tempFile, content, StandardCharsets.UTF_8);

            Files.move(tempFile, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            cleanupTemp(tempFile);
            throw new SafetyException(
                "Error en escritura atomica: " + target,
                SafetyException.SafetyErrorCode.ATOMIC_WRITE_FAILED,
                e
            );
        }
    }

    public void writeAtomicBytes(Path target, byte[] bytes) {
        Objects.requireNonNull(target, "target no puede ser null");
        Objects.requireNonNull(bytes, "bytes no puede ser null");

        Path tempFile = createTempPath(target);

        try {
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.write(tempFile, bytes);

            Files.move(tempFile, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            cleanupTemp(tempFile);
            throw new SafetyException(
                "Error en escritura atomica de bytes: " + target,
                SafetyException.SafetyErrorCode.ATOMIC_WRITE_FAILED,
                e
            );
        }
    }

    private Path createTempPath(Path target) {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String tempName = target.getFileName().toString() + "." + uniqueId + TEMP_SUFFIX;
        Path parent = target.getParent();
        return parent != null ? parent.resolve(tempName) : Path.of(tempName);
    }

    private void cleanupTemp(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
    }
}
