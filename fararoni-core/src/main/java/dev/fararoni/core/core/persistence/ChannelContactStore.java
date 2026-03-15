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
package dev.fararoni.core.core.persistence;

import dev.fararoni.core.core.security.ChannelAccessGuard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface ChannelContactStore extends AutoCloseable {
    Optional<String> getOwner();

    void setOwner(String ownerId);

    default boolean isOwner(String senderId) {
        return getOwner().map(o -> o.equals(senderId)).orElse(false);
    }

    boolean isAllowed(String senderId);

    void addToAllowList(String senderId, String note);

    boolean removeFromAllowList(String senderId);

    List<ChannelContact> getAllowList();

    boolean isGroupAllowed(String groupId);

    void addGroupToAllowList(String groupId, String note);

    boolean removeGroupFromAllowList(String groupId);

    List<ChannelContact> getAllowedGroups();

    void saveChannelPairingRequest(ChannelPairingRequest request);

    Optional<ChannelPairingRequest> getChannelPairingRequestByCode(String code);

    Optional<ChannelPairingRequest> getChannelPairingRequestBySender(String senderId);

    boolean deleteChannelPairingRequest(String code);

    List<ChannelPairingRequest> getPendingChannelPairingRequests();

    int cleanupExpiredPairings();

    record ChannelContact(
        String id,
        EntryType type,
        String note,
        Instant createdAt
    ) {
        public enum EntryType {
            CONTACT,
            GROUP,
            OWNER
        }
    }

    record ChannelPairingRequest(
        String senderId,
        String senderName,
        String code,
        String protocol,
        Instant expiresAt,
        Instant createdAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public static ChannelPairingRequest create(
                String senderId,
                String senderName,
                String code,
                String protocol,
                int expiryHours) {
            Instant now = Instant.now();
            return new ChannelPairingRequest(
                senderId,
                senderName,
                code,
                protocol,
                now.plusSeconds(expiryHours * 3600L),
                now
            );
        }
    }
}
