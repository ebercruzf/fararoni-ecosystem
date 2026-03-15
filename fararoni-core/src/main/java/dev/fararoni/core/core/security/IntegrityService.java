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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class IntegrityService {
    private static final Logger log = LoggerFactory.getLogger(IntegrityService.class);

    private static volatile IntegrityService instance;
    private static final Object LOCK = new Object();

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final String FIELD_SEPARATOR = "\u0000|\u0000";

    private static final String SIGNATURE_VERSION = "v1";

    private final byte[] hmacKey;

    private long signaturesGenerated = 0;
    private long verificationsPerformed = 0;
    private long verificationsFailed = 0;

    private IntegrityService() {
        this(deriveHmacKey());
    }

    IntegrityService(byte[] hmacKey) {
        this.hmacKey = hmacKey;
        log.info("[IntegrityService] Initialized with HMAC-SHA256");
    }

    public static IntegrityService getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new IntegrityService();
                }
            }
        }
        return instance;
    }

    public String signInteraction(String id, String prompt, String response, long timestamp) {
        Objects.requireNonNull(id, "ID cannot be null");
        Objects.requireNonNull(prompt, "Prompt cannot be null");
        Objects.requireNonNull(response, "Response cannot be null");

        String dataToSign = buildSignatureData(id, prompt, response, timestamp);
        String signature = computeHmac(dataToSign);

        signaturesGenerated++;
        log.debug("[IntegrityService] Generated signature for ID: {}", id);

        return signature;
    }

    public String sign(String... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("At least one field is required");
        }

        StringBuilder sb = new StringBuilder(SIGNATURE_VERSION);
        for (String field : fields) {
            sb.append(FIELD_SEPARATOR);
            sb.append(field != null ? field : "");
        }

        String signature = computeHmac(sb.toString());
        signaturesGenerated++;

        return signature;
    }

    public boolean verifyInteraction(String id, String prompt, String response,
                                      long timestamp, String expectedSignature) {
        verificationsPerformed++;

        if (expectedSignature == null || expectedSignature.isEmpty()) {
            log.warn("[IntegrityService] No signature found for ID: {}", id);
            verificationsFailed++;
            return false;
        }

        String actualSignature = signInteraction(id, prompt, response, timestamp);
        signaturesGenerated--;

        boolean valid = constantTimeEquals(actualSignature, expectedSignature);

        if (!valid) {
            verificationsFailed++;
            log.warn("[IntegrityService] INTEGRITY VIOLATION: Record {} has invalid signature!", id);
        } else {
            log.debug("[IntegrityService] Signature valid for ID: {}", id);
        }

        return valid;
    }

    public boolean verify(String expectedSignature, String... fields) {
        verificationsPerformed++;

        if (expectedSignature == null || expectedSignature.isEmpty()) {
            verificationsFailed++;
            return false;
        }

        String actualSignature = sign(fields);
        signaturesGenerated--;

        boolean valid = constantTimeEquals(actualSignature, expectedSignature);

        if (!valid) {
            verificationsFailed++;
        }

        return valid;
    }

    public record VerificationResult(
            String recordId,
            boolean valid,
            String message,
            String expectedSignature,
            String actualSignature
    ) {
        public static VerificationResult success(String recordId) {
            return new VerificationResult(recordId, true, "Signature valid", null, null);
        }

        public static VerificationResult failure(String recordId, String expected, String actual) {
            return new VerificationResult(
                    recordId,
                    false,
                    "INTEGRITY VIOLATION: Signature mismatch",
                    maskSignature(expected),
                    maskSignature(actual)
            );
        }

        public static VerificationResult missingSignature(String recordId) {
            return new VerificationResult(
                    recordId,
                    false,
                    "No signature found (legacy record or migration needed)",
                    null,
                    null
            );
        }

        private static String maskSignature(String sig) {
            if (sig == null || sig.length() < 16) return "***";
            return sig.substring(0, 8) + "..." + sig.substring(sig.length() - 8);
        }
    }

    public VerificationResult verifyWithDetails(String id, String prompt, String response,
                                                 long timestamp, String expectedSignature) {
        verificationsPerformed++;

        if (expectedSignature == null || expectedSignature.isEmpty()) {
            verificationsFailed++;
            return VerificationResult.missingSignature(id);
        }

        String actualSignature = signInteraction(id, prompt, response, timestamp);
        signaturesGenerated--;

        if (constantTimeEquals(actualSignature, expectedSignature)) {
            return VerificationResult.success(id);
        } else {
            verificationsFailed++;
            return VerificationResult.failure(id, expectedSignature, actualSignature);
        }
    }

    private String buildSignatureData(String id, String prompt, String response, long timestamp) {
        return SIGNATURE_VERSION +
                FIELD_SEPARATOR + id +
                FIELD_SEPARATOR + prompt +
                FIELD_SEPARATOR + response +
                FIELD_SEPARATOR + timestamp;
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(hmacKey, HMAC_ALGORITHM);
            mac.init(keySpec);

            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new SecurityException("HMAC computation failed", e);
        }
    }

    private static byte[] deriveHmacKey() {
        String hardwareId = HardwareIdGenerator.generateHardwareId();
        return hardwareId.substring(0, 32).getBytes(StandardCharsets.UTF_8);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }

    public long getSignaturesGenerated() {
        return signaturesGenerated;
    }

    public long getVerificationsPerformed() {
        return verificationsPerformed;
    }

    public long getVerificationsFailed() {
        return verificationsFailed;
    }

    public double getIntegrityRate() {
        if (verificationsPerformed == 0) {
            return 100.0;
        }
        long successful = verificationsPerformed - verificationsFailed;
        return (successful * 100.0) / verificationsPerformed;
    }

    public String getStatsSummary() {
        return String.format(
                "[IntegrityService] Stats: signatures=%d, verifications=%d, failures=%d, integrity=%.1f%%",
                signaturesGenerated, verificationsPerformed, verificationsFailed, getIntegrityRate()
        );
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    void resetStats() {
        signaturesGenerated = 0;
        verificationsPerformed = 0;
        verificationsFailed = 0;
    }
}
