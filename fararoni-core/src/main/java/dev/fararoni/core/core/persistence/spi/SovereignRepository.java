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
package dev.fararoni.core.core.persistence.spi;

import com.fasterxml.jackson.databind.JsonNode;
import dev.fararoni.core.core.persistence.PersistenceFactory;

import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface SovereignRepository extends AutoCloseable {
    void initialize() throws RepositoryException;

    boolean isAvailable();

    List<ChannelRecord> findActiveChannels();

    Optional<ChannelRecord> findById(String channelId);

    List<ChannelRecord> findByType(String type);

    void save(ChannelRecord record) throws RepositoryException;

    void updateStatus(String channelId, String status) throws RepositoryException;

    void delete(String channelId) throws RepositoryException;

    void saveSecret(String channelId, byte[] encryptedSecret, byte[] iv, byte[] authTag, String keyId)
        throws RepositoryException;

    Optional<EncryptedSecretRecord> getSecret(String channelId);

    void deleteSecret(String channelId);

    void logAudit(String channelId, String action, String changedBy);

    int countChannels();

    void runScript(String sqlScript) throws RepositoryException;

    record ChannelRecord(
        String id,
        String type,
        String name,
        JsonNode config,
        String status,
        long createdAt,
        long updatedAt
    ) {}

    record EncryptedSecretRecord(
        String channelId,
        byte[] encryptedSecret,
        byte[] iv,
        byte[] authTag,
        int encryptionVersion,
        String keyId
    ) {}

    class RepositoryException extends Exception {
        public RepositoryException(String message) {
            super(message);
        }

        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
