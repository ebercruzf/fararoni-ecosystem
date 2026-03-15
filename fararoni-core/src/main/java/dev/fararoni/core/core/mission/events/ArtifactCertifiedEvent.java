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
package dev.fararoni.core.core.mission.events;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record ArtifactCertifiedEvent(
    String correlationId,
    String missionId,
    String agentId,
    Path artifactPath,
    String sha256Hash,
    long bytesWritten,
    Instant timestamp
) {
    public static final String TOPIC = "audit.artifact.certified";

    public ArtifactCertifiedEvent {
        Objects.requireNonNull(correlationId, "correlationId no puede ser null");
        Objects.requireNonNull(artifactPath, "artifactPath no puede ser null");
        Objects.requireNonNull(sha256Hash, "sha256Hash no puede ser null");
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");

        if (sha256Hash.length() != 64 || !sha256Hash.matches("[a-fA-F0-9]+")) {
            throw new IllegalArgumentException(
                "sha256Hash debe ser un hash de 64 caracteres hex, recibido: " + sha256Hash);
        }
    }

    public static ArtifactCertifiedEvent from(
            FileWriteResultEvent resultEvent,
            String sha256Hash) {
        return new ArtifactCertifiedEvent(
            resultEvent.originalEventId(),
            resultEvent.missionId(),
            resultEvent.agentId(),
            resultEvent.writtenPath(),
            sha256Hash,
            resultEvent.bytesWritten(),
            Instant.now()
        );
    }

    public static ArtifactCertifiedEvent create(
            String correlationId,
            String missionId,
            String agentId,
            Path path,
            String hash,
            long bytes) {
        return new ArtifactCertifiedEvent(
            correlationId,
            missionId,
            agentId,
            path,
            hash,
            bytes,
            Instant.now()
        );
    }

    @Override
    public String toString() {
        return String.format(
            "ArtifactCertified[%s, %s, %s...%s, %d bytes]",
            artifactPath.getFileName(),
            missionId != null ? missionId : "no-mission",
            sha256Hash.substring(0, 8),
            sha256Hash.substring(56),
            bytesWritten
        );
    }
}
