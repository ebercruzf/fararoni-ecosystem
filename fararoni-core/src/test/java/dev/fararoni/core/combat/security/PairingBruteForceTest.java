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
import dev.fararoni.core.core.security.ChannelAccessGuard.ChannelApprovalResult;
import dev.fararoni.core.core.security.ChannelAccessGuard.ChannelPairingResult;
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
@DisplayName("SEC-03: Pairing Code Brute Force")
class PairingBruteForceTest {
    private ChannelAccessGuard guard;
    private InMemoryChannelContactStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryChannelContactStore();
        guard = new ChannelAccessGuard(store);
    }

    @Test
    @DisplayName("Límite de 3 solicitudes pendientes previene spam")
    void maxPendingPairings_preventsSpam() {
        for (int i = 0; i < ChannelAccessGuard.MAX_PENDING_PAIRINGS; i++) {
            ChannelPairingResult result = guard.initiatePairing(
                "attacker-" + i,
                "Attacker " + i,
                "WHATSAPP"
            );
            assertTrue(result.success(),
                "Primeras " + ChannelAccessGuard.MAX_PENDING_PAIRINGS + " solicitudes deben tener éxito");
        }

        ChannelPairingResult blockedResult = guard.initiatePairing(
            "attacker-extra",
            "Extra Attacker",
            "WHATSAPP"
        );

        assertFalse(blockedResult.success(),
            "Solicitud adicional debe ser bloqueada");
        assertTrue(blockedResult.errorMessage().contains("Limite"),
            "Mensaje debe indicar que se alcanzó el límite");
    }

    @Test
    @DisplayName("Códigos inválidos retornan NOT_FOUND sin revelar información")
    void invalidCode_returnsNotFound_noInfoLeak() {
        guard.initiatePairing("victim", "Victim", "WHATSAPP");
        List<ChannelPairingRequest> pending = guard.listPendingPairings();
        assertFalse(pending.isEmpty());
        String validCode = pending.get(0).code();

        ChannelApprovalResult invalidResult = guard.approvePairing("000000", null);

        assertEquals(ChannelApprovalResult.Status.NOT_FOUND, invalidResult.status(),
            "Código inválido debe retornar NOT_FOUND");

        assertFalse(invalidResult.message().contains(validCode),
            "Mensaje de error no debe revelar código válido");
    }

    @Test
    @DisplayName("Brute force de códigos es impracticable (1M combinaciones)")
    void bruteForce_isImpractical() {
        guard.initiatePairing("target", "Target", "WHATSAPP");
        List<ChannelPairingRequest> pending = guard.listPendingPairings();
        String validCode = pending.get(0).code();

        assertEquals(6, validCode.length(),
            "Código debe tener 6 dígitos");
        assertTrue(validCode.matches("\\d{6}"),
            "Código debe ser solo dígitos");

        int totalCombinations = 1_000_000;
        int expirySeconds = ChannelAccessGuard.PAIRING_EXPIRY_HOURS * 3600;
        int attemptsPerSecond = 100;

        int maxAttemptsInWindow = attemptsPerSecond * expirySeconds;
        double probabilityOfGuess = (double) maxAttemptsInWindow / totalCombinations;

        assertTrue(probabilityOfGuess < 0.5,
            "Probabilidad de adivinar debe ser < 50% incluso con 100 intentos/segundo");
    }

    @Test
    @DisplayName("Códigos expirados son rechazados automáticamente")
    void expiredCodes_areRejected() {
        Instant pastExpiry = Instant.now().minusSeconds(3600);

        ChannelPairingRequest expiredRequest = new ChannelPairingRequest(
            "expired-sender",
            "Expired User",
            "123456",
            "WHATSAPP",
            pastExpiry,
            pastExpiry.minusSeconds(7200)
        );
        store.saveChannelPairingRequest(expiredRequest);

        ChannelApprovalResult result = guard.approvePairing("123456", null);

        assertEquals(ChannelApprovalResult.Status.EXPIRED, result.status(),
            "Código expirado debe retornar EXPIRED");
    }

    @Test
    @DisplayName("Mismo sender no puede tener múltiples códigos activos")
    void sameSender_cannotHaveMultipleCodes() {
        ChannelPairingResult first = guard.initiatePairing("repeat-sender", "Repeat", "WHATSAPP");
        assertTrue(first.success());
        String firstCode = first.request().code();

        ChannelPairingResult second = guard.initiatePairing("repeat-sender", "Repeat", "WHATSAPP");

        assertTrue(second.success());
        assertEquals(firstCode, second.request().code(),
            "Debe retornar el código existente, no generar uno nuevo");
    }

    @Test
    @DisplayName("Códigos son únicos entre solicitudes")
    void codes_areUnique_betweenRequests() {
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < ChannelAccessGuard.MAX_PENDING_PAIRINGS; i++) {
            ChannelPairingResult result = guard.initiatePairing(
                "unique-sender-" + i,
                "User " + i,
                "WHATSAPP"
            );
            assertTrue(result.success());
            codes.add(result.request().code());
        }

        assertEquals(ChannelAccessGuard.MAX_PENDING_PAIRINGS, codes.size(),
            "Todos los códigos deben ser únicos");
    }

    @Test
    @DisplayName("Aprobación exitosa elimina el código (no reusable)")
    void approvedCode_isDeleted_notReusable() {
        guard.initiatePairing("approved-sender", "Approved", "WHATSAPP");
        List<ChannelPairingRequest> pending = guard.listPendingPairings();
        String code = pending.get(0).code();

        ChannelApprovalResult approved = guard.approvePairing(code, "Test approval");
        assertEquals(ChannelApprovalResult.Status.APPROVED, approved.status());

        ChannelApprovalResult reuse = guard.approvePairing(code, null);

        assertEquals(ChannelApprovalResult.Status.NOT_FOUND, reuse.status(),
            "Código aprobado no debe ser reusable");
    }

    @Test
    @DisplayName("Denegación elimina el código")
    void deniedCode_isDeleted() {
        guard.initiatePairing("denied-sender", "Denied", "WHATSAPP");
        List<ChannelPairingRequest> pending = guard.listPendingPairings();
        String code = pending.get(0).code();

        ChannelApprovalResult denied = guard.denyPairing(code);
        assertEquals(ChannelApprovalResult.Status.DENIED, denied.status());

        ChannelApprovalResult check = guard.approvePairing(code, null);
        assertEquals(ChannelApprovalResult.Status.NOT_FOUND, check.status(),
            "Código denegado debe ser eliminado");
    }

    @Test
    @DisplayName("Cleanup de expirados libera slots para nuevos pairings")
    void expiredCleanup_freesSlots() {
        Instant pastExpiry = Instant.now().minusSeconds(3600);

        for (int i = 0; i < ChannelAccessGuard.MAX_PENDING_PAIRINGS; i++) {
            store.saveChannelPairingRequest(new ChannelPairingRequest(
                "expired-" + i,
                "Expired " + i,
                String.format("%06d", i),
                "WHATSAPP",
                pastExpiry,
                pastExpiry.minusSeconds(7200)
            ));
        }

        List<ChannelPairingRequest> pendingBeforeCleanup = store.getPendingChannelPairingRequests();
        assertEquals(0, pendingBeforeCleanup.size(),
            "Solicitudes expiradas no deben contar como pendientes");

        ChannelPairingResult newPairing = guard.initiatePairing("new-sender", "New", "WHATSAPP");

        assertTrue(newPairing.success(),
            "Nuevo pairing debe tener éxito porque expirados no ocupan slots");
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
