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
package dev.fararoni.core.combat.security;

import dev.fararoni.core.core.persistence.ChannelContactStore;
import dev.fararoni.core.core.persistence.ChannelContactStore.ChannelPairingRequest;
import dev.fararoni.core.core.security.ChannelAccessGuard;
import dev.fararoni.core.core.security.ChannelAccessGuard.ChannelAccessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("SEC-04: Zero Trust Validation")
class ZeroTrustValidationTest {
    private ChannelAccessGuard guard;
    private InMemoryChannelContactStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryChannelContactStore();
        guard = new ChannelAccessGuard(store);
    }

    @Test
    @DisplayName("Desconocido es bloqueado por defecto (deny by default)")
    void unknown_isBlocked_byDefault() {
        String unknownSender = "unknown-sender-12345";

        ChannelAccessResult result = guard.checkAccess(unknownSender, false, null);

        assertFalse(result.isAllowed(),
            "Desconocido debe ser bloqueado por defecto");
        assertEquals(ChannelAccessResult.Status.DENIED_BLOCK, result.status(),
            "Status debe ser DENIED_BLOCK");
    }

    @Test
    @DisplayName("OWNER tiene acceso total sin verificación adicional")
    void owner_hasFullAccess_withoutVerification() {
        String ownerId = "owner-phone-12345";
        store.setOwner(ownerId);

        ChannelAccessResult result = guard.checkAccess(ownerId, false, null);

        assertTrue(result.isAllowed(), "OWNER debe tener acceso");
        assertEquals("OWNER", result.reason(), "Razón debe ser OWNER");
    }

    @Test
    @DisplayName("Contacto en AllowList tiene acceso")
    void allowListContact_hasAccess() {
        String allowedContact = "allowed-contact-67890";
        store.addToAllowList(allowedContact, "VIP Customer");

        ChannelAccessResult result = guard.checkAccess(allowedContact, false, null);

        assertTrue(result.isAllowed(), "Contacto en allowlist debe tener acceso");
        assertEquals("ALLOWLIST", result.reason(), "Razón debe ser ALLOWLIST");
    }

    @Test
    @DisplayName("Grupo no autorizado es ignorado silenciosamente")
    void unauthorizedGroup_isIgnored_silently() {
        String unknownGroupId = "group-unknown-abc";
        String senderInGroup = "sender-in-group";

        ChannelAccessResult result = guard.checkAccess(senderInGroup, true, unknownGroupId);

        assertEquals(ChannelAccessResult.Status.IGNORED_GROUP, result.status(),
            "Grupo no autorizado debe ser IGNORED_GROUP");
        assertFalse(result.isAllowed());
    }

    @Test
    @DisplayName("Grupo autorizado permite acceso a miembros")
    void authorizedGroup_allowsMembers() {
        String groupId = "group-authorized-xyz";
        store.addGroupToAllowList(groupId, "Support Team");

        String memberSender = "team-member-123";
        store.addToAllowList(memberSender, "Team Member");

        ChannelAccessResult result = guard.checkAccess(memberSender, true, groupId);

        assertTrue(result.isAllowed(),
            "Miembro de grupo autorizado debe tener acceso");
    }

    @Test
    @DisplayName("Grupo autorizado pero sender no autorizado es bloqueado")
    void authorizedGroup_unauthorizedSender_isBlocked() {
        String groupId = "group-authorized-abc";
        store.addGroupToAllowList(groupId, "Company Group");

        String unknownSender = "random-person";

        ChannelAccessResult result = guard.checkAccess(unknownSender, true, groupId);

        assertFalse(result.isAllowed(),
            "Sender no autorizado debe ser bloqueado aunque el grupo esté autorizado");
        assertEquals(ChannelAccessResult.Status.DENIED_BLOCK, result.status());
    }

    @Test
    @DisplayName("Pairing pendiente retorna código existente")
    void pendingPairing_returnsExistingCode() {
        String sender = "pending-sender";
        guard.initiatePairing(sender, "Pending User", "WHATSAPP");
        List<ChannelPairingRequest> pending = guard.listPendingPairings();
        String code = pending.get(0).code();

        ChannelAccessResult result = guard.checkAccess(sender, false, null);

        assertEquals(ChannelAccessResult.Status.DENIED_NEEDS_PAIRING, result.status(),
            "Debe indicar que necesita completar pairing");
        assertEquals(code, result.pairingCode(),
            "Debe incluir el código de pairing existente");
    }

    @Test
    @DisplayName("Cada solicitud es verificada independientemente")
    void eachRequest_isVerified_independently() {
        String contact = "temporary-contact";
        store.addToAllowList(contact, "Temporary");

        ChannelAccessResult firstResult = guard.checkAccess(contact, false, null);
        assertTrue(firstResult.isAllowed(), "Primera verificación debe tener acceso");

        store.removeFromAllowList(contact);

        ChannelAccessResult secondResult = guard.checkAccess(contact, false, null);
        assertFalse(secondResult.isAllowed(),
            "Segunda verificación debe ser bloqueada (acceso revocado)");
    }

    @Test
    @DisplayName("OWNER no necesita estar en AllowList")
    void owner_doesNotNeed_allowList() {
        String ownerId = "owner-not-in-list";
        store.setOwner(ownerId);

        ChannelAccessResult result = guard.checkAccess(ownerId, false, null);

        assertTrue(result.isAllowed(),
            "OWNER debe tener acceso sin estar en allowlist");
        assertFalse(store.isAllowed(ownerId),
            "OWNER no debe estar en allowlist");
    }

    @Test
    @DisplayName("Verificación es case-sensitive")
    void verification_isCaseSensitive() {
        String contact = "User123";
        store.addToAllowList(contact, "User");

        ChannelAccessResult exactResult = guard.checkAccess("User123", false, null);
        ChannelAccessResult lowerResult = guard.checkAccess("user123", false, null);
        ChannelAccessResult upperResult = guard.checkAccess("USER123", false, null);

        assertTrue(exactResult.isAllowed(), "Case exacto debe tener acceso");
        assertFalse(lowerResult.isAllowed(), "Lowercase debe ser bloqueado");
        assertFalse(upperResult.isAllowed(), "Uppercase debe ser bloqueado");
    }

    @Test
    @DisplayName("Revocar OWNER no afecta su acceso si sigue siendo OWNER")
    void revokeFromAllowList_doesNotAffectOwner() {
        String ownerId = "dual-role-owner";
        store.setOwner(ownerId);
        store.addToAllowList(ownerId, "Also in allowlist");

        assertTrue(guard.checkAccess(ownerId, false, null).isAllowed());

        store.removeFromAllowList(ownerId);

        ChannelAccessResult result = guard.checkAccess(ownerId, false, null);
        assertTrue(result.isAllowed(), "OWNER debe mantener acceso");
        assertEquals("OWNER", result.reason(), "Razón debe ser OWNER, no ALLOWLIST");
    }

    @Test
    @DisplayName("Múltiples canales del mismo sender son verificados individualmente")
    void multipleChannels_sameSender_verifiedIndividually() {
        String sender = "multi-channel-user";
        store.addToAllowList(sender, "Multi-channel");

        ChannelAccessResult directResult = guard.checkAccess(sender, false, null);
        ChannelAccessResult groupResult = guard.checkAccess(sender, true, "unknown-group");

        assertTrue(directResult.isAllowed(), "Acceso directo debe funcionar");
        assertEquals(ChannelAccessResult.Status.IGNORED_GROUP, groupResult.status(),
            "Acceso desde grupo desconocido debe ser ignorado");
    }

    private static class InMemoryChannelContactStore implements ChannelContactStore {
        private String owner;
        private final Map<String, ChannelContact> allowList = new ConcurrentHashMap<>();
        private final Map<String, ChannelContact> groups = new ConcurrentHashMap<>();
        private final Map<String, ChannelPairingRequest> pairingsByCode = new ConcurrentHashMap<>();
        private final Map<String, ChannelPairingRequest> pairingsBySender = new ConcurrentHashMap<>();

        @Override
        public Optional<String> getOwner() {
            return Optional.ofNullable(owner);
        }

        @Override
        public void setOwner(String ownerId) {
            this.owner = ownerId;
        }

        @Override
        public boolean isAllowed(String senderId) {
            return allowList.containsKey(senderId);
        }

        @Override
        public void addToAllowList(String senderId, String note) {
            allowList.put(senderId, new ChannelContact(
                senderId,
                ChannelContact.EntryType.CONTACT,
                note,
                Instant.now()
            ));
        }

        @Override
        public boolean removeFromAllowList(String senderId) {
            return allowList.remove(senderId) != null;
        }

        @Override
        public List<ChannelContact> getAllowList() {
            return new ArrayList<>(allowList.values());
        }

        @Override
        public boolean isGroupAllowed(String groupId) {
            return groups.containsKey(groupId);
        }

        @Override
        public void addGroupToAllowList(String groupId, String note) {
            groups.put(groupId, new ChannelContact(
                groupId,
                ChannelContact.EntryType.GROUP,
                note,
                Instant.now()
            ));
        }

        @Override
        public boolean removeGroupFromAllowList(String groupId) {
            return groups.remove(groupId) != null;
        }

        @Override
        public List<ChannelContact> getAllowedGroups() {
            return new ArrayList<>(groups.values());
        }

        @Override
        public void saveChannelPairingRequest(ChannelPairingRequest request) {
            pairingsByCode.put(request.code(), request);
            pairingsBySender.put(request.senderId(), request);
        }

        @Override
        public Optional<ChannelPairingRequest> getChannelPairingRequestByCode(String code) {
            return Optional.ofNullable(pairingsByCode.get(code));
        }

        @Override
        public Optional<ChannelPairingRequest> getChannelPairingRequestBySender(String senderId) {
            return Optional.ofNullable(pairingsBySender.get(senderId));
        }

        @Override
        public boolean deleteChannelPairingRequest(String code) {
            ChannelPairingRequest removed = pairingsByCode.remove(code);
            if (removed != null) {
                pairingsBySender.remove(removed.senderId());
                return true;
            }
            return false;
        }

        @Override
        public List<ChannelPairingRequest> getPendingChannelPairingRequests() {
            return pairingsByCode.values().stream()
                .filter(r -> !r.isExpired())
                .toList();
        }

        @Override
        public int cleanupExpiredPairings() {
            List<String> expiredCodes = pairingsByCode.values().stream()
                .filter(ChannelPairingRequest::isExpired)
                .map(ChannelPairingRequest::code)
                .toList();

            expiredCodes.forEach(this::deleteChannelPairingRequest);
            return expiredCodes.size();
        }

        @Override
        public void close() {
        }
    }
}
