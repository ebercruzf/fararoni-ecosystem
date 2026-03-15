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
package dev.fararoni.core.core.security;

import dev.fararoni.core.core.persistence.ChannelContactStore;
import dev.fararoni.core.core.persistence.ChannelContactStore.ChannelPairingRequest;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ChannelAccessGuard {
    private static final Logger LOG = Logger.getLogger(ChannelAccessGuard.class.getName());

    public static final int MAX_PENDING_PAIRINGS = 3;

    public static final int PAIRING_EXPIRY_HOURS = 1;

    private static final int CODE_LENGTH = 6;

    private final ChannelContactStore identityStore;
    private final SecureRandom secureRandom;

    public ChannelAccessGuard(ChannelContactStore channelContactStore) {
        this.identityStore = channelContactStore;
        this.secureRandom = new SecureRandom();
    }

    public ChannelAccessResult checkAccess(String senderId, boolean isGroup, String groupId) {
        if (identityStore.isOwner(senderId)) {
            LOG.fine(() -> "[Guard] OWNER autorizado: " + senderId);
            return ChannelAccessResult.allowed(senderId, "OWNER");
        }

        if (isGroup && groupId != null) {
            if (!identityStore.isGroupAllowed(groupId)) {
                LOG.info(() -> "[Guard] Grupo no autorizado: " + groupId);
                return ChannelAccessResult.ignoredGroup(groupId);
            }
        }

        if (identityStore.isAllowed(senderId)) {
            LOG.fine(() -> "[Guard] Contacto autorizado: " + senderId);
            return ChannelAccessResult.allowed(senderId, "ALLOWLIST");
        }

        Optional<ChannelPairingRequest> existingPairing = identityStore.getChannelPairingRequestBySender(senderId);
        if (existingPairing.isPresent()) {
            ChannelPairingRequest pr = existingPairing.get();
            if (!pr.isExpired()) {
                LOG.info(() -> "[Guard] Pairing pendiente para: " + senderId);
                return ChannelAccessResult.needsPairing(senderId, pr.code());
            }
        }

        LOG.info(() -> "[Guard] Acceso denegado: " + senderId);
        return ChannelAccessResult.deniedBlock(senderId);
    }

    public ChannelPairingResult initiatePairing(String senderId, String senderName, String protocol) {
        identityStore.cleanupExpiredPairings();

        List<ChannelPairingRequest> pending = identityStore.getPendingChannelPairingRequests();
        if (pending.size() >= MAX_PENDING_PAIRINGS) {
            LOG.warning(() -> "[Guard] Limite de pairings alcanzado: " + pending.size());
            return ChannelPairingResult.limitReached(MAX_PENDING_PAIRINGS);
        }

        Optional<ChannelPairingRequest> existing = identityStore.getChannelPairingRequestBySender(senderId);
        if (existing.isPresent() && !existing.get().isExpired()) {
            return ChannelPairingResult.success(existing.get());
        }

        String code = generateUniqueCode();

        ChannelPairingRequest request = ChannelPairingRequest.create(
            senderId,
            senderName,
            code,
            protocol,
            PAIRING_EXPIRY_HOURS
        );

        identityStore.saveChannelPairingRequest(request);

        LOG.info(() -> String.format(
            "[Guard] Pairing iniciado: %s -> %s (expira en %dh)",
            senderId, code, PAIRING_EXPIRY_HOURS
        ));

        return ChannelPairingResult.success(request);
    }

    public ChannelApprovalResult approvePairing(String code, String note) {
        Optional<ChannelPairingRequest> request = identityStore.getChannelPairingRequestByCode(code);

        if (request.isEmpty()) {
            return ChannelApprovalResult.notFound(code);
        }

        ChannelPairingRequest pr = request.get();

        if (pr.isExpired()) {
            identityStore.deleteChannelPairingRequest(code);
            return ChannelApprovalResult.expired(code);
        }

        String finalNote = note != null ? note :
            String.format("Aprobado via %s (%s)", pr.protocol(), pr.senderName());

        identityStore.addToAllowList(pr.senderId(), finalNote);
        identityStore.deleteChannelPairingRequest(code);

        LOG.info(() -> String.format(
            "[Guard] Pairing aprobado: %s (%s)",
            pr.senderId(), pr.senderName()
        ));

        return ChannelApprovalResult.approved(pr);
    }

    public ChannelApprovalResult denyPairing(String code) {
        Optional<ChannelPairingRequest> request = identityStore.getChannelPairingRequestByCode(code);

        if (request.isEmpty()) {
            return ChannelApprovalResult.notFound(code);
        }

        ChannelPairingRequest pr = request.get();
        identityStore.deleteChannelPairingRequest(code);

        LOG.info(() -> String.format(
            "[Guard] Pairing denegado: %s (%s)",
            pr.senderId(), pr.senderName()
        ));

        return ChannelApprovalResult.denied(pr);
    }

    public List<ChannelPairingRequest> listPendingPairings() {
        identityStore.cleanupExpiredPairings();
        return identityStore.getPendingChannelPairingRequests();
    }

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = String.format("%06d", secureRandom.nextInt(1000000));
            attempts++;
        } while (identityStore.getChannelPairingRequestByCode(code).isPresent() && attempts < 10);

        return code;
    }

    public record ChannelAccessResult(
        Status status,
        String senderId,
        String reason,
        String pairingCode
    ) {
        public enum Status {
            ALLOWED,
            DENIED_BLOCK,
            DENIED_NEEDS_PAIRING,
            IGNORED_GROUP
        }

        public boolean isAllowed() {
            return status == Status.ALLOWED;
        }

        public static ChannelAccessResult allowed(String senderId, String reason) {
            return new ChannelAccessResult(Status.ALLOWED, senderId, reason, null);
        }

        public static ChannelAccessResult deniedBlock(String senderId) {
            return new ChannelAccessResult(Status.DENIED_BLOCK, senderId, "Not in allowlist", null);
        }

        public static ChannelAccessResult needsPairing(String senderId, String code) {
            return new ChannelAccessResult(Status.DENIED_NEEDS_PAIRING, senderId, "Pairing required", code);
        }

        public static ChannelAccessResult ignoredGroup(String groupId) {
            return new ChannelAccessResult(Status.IGNORED_GROUP, groupId, "Group not authorized", null);
        }
    }

    public record ChannelPairingResult(
        boolean success,
        ChannelPairingRequest request,
        String errorMessage
    ) {
        public static ChannelPairingResult success(ChannelPairingRequest request) {
            return new ChannelPairingResult(true, request, null);
        }

        public static ChannelPairingResult limitReached(int limit) {
            return new ChannelPairingResult(false, null,
                "Limite de " + limit + " solicitudes pendientes alcanzado");
        }
    }

    public record ChannelApprovalResult(
        Status status,
        ChannelPairingRequest request,
        String message
    ) {
        public enum Status {
            APPROVED,
            DENIED,
            NOT_FOUND,
            EXPIRED
        }

        public boolean isSuccess() {
            return status == Status.APPROVED || status == Status.DENIED;
        }

        public static ChannelApprovalResult approved(ChannelPairingRequest request) {
            return new ChannelApprovalResult(Status.APPROVED, request,
                "Contacto agregado a allowlist: " + request.senderId());
        }

        public static ChannelApprovalResult denied(ChannelPairingRequest request) {
            return new ChannelApprovalResult(Status.DENIED, request,
                "Solicitud denegada para: " + request.senderId());
        }

        public static ChannelApprovalResult notFound(String code) {
            return new ChannelApprovalResult(Status.NOT_FOUND, null,
                "Codigo no encontrado: " + code);
        }

        public static ChannelApprovalResult expired(String code) {
            return new ChannelApprovalResult(Status.EXPIRED, null,
                "Codigo expirado: " + code);
        }
    }
}
