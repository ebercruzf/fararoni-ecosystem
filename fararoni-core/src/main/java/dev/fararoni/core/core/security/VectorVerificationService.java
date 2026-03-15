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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class VectorVerificationService {
    private static final Logger log = LoggerFactory.getLogger(VectorVerificationService.class);

    private static volatile VectorVerificationService instance;
    private static final Object LOCK = new Object();

    private static final double SUSPICIOUS_SIMILARITY_THRESHOLD = 0.999;
    private static final int MAX_DUPLICATE_VECTORS = 5;

    private long verificationsPerformed = 0;
    private long poisoningDetected = 0;
    private long vectorsQuarantined = 0;
    private long vectorsReindexed = 0;

    private VectorVerificationService() {
        log.info("[VectorVerificationService] Initialized - Anti RAG Poisoning defense active");
    }

    public static VectorVerificationService getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new VectorVerificationService();
                }
            }
        }
        return instance;
    }

    public String computeContentHash(String content) {
        if (content == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("SHA-256 not available", e);
        }
    }

    public String computeVectorChecksum(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (float f : vector) {
                int bits = Float.floatToIntBits(f);
                digest.update((byte) (bits >> 24));
                digest.update((byte) (bits >> 16));
                digest.update((byte) (bits >> 8));
                digest.update((byte) bits);
            }
            byte[] hash = digest.digest();
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException("SHA-256 not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public VerificationResult verifyEmbedding(String originalContent, String storedHash,
                                               float[] storedVector, String storedChecksum) {
        verificationsPerformed++;

        List<String> issues = new ArrayList<>();

        if (storedHash != null && originalContent != null) {
            String computedHash = computeContentHash(originalContent);
            if (!storedHash.equals(computedHash)) {
                issues.add("CONTENT_TAMPERED: Source content hash mismatch");
                poisoningDetected++;
            }
        }

        if (storedChecksum != null && storedVector != null) {
            String computedChecksum = computeVectorChecksum(storedVector);
            if (!storedChecksum.equals(computedChecksum)) {
                issues.add("VECTOR_MANIPULATED: Embedding vector checksum mismatch");
                poisoningDetected++;
            }
        }

        if (storedVector != null) {
            Optional<String> anomaly = detectVectorAnomaly(storedVector);
            anomaly.ifPresent(issues::add);
        }

        boolean valid = issues.isEmpty();
        if (!valid) {
            log.warn("[VectorVerificationService] Integrity issues detected: {}", issues);
        }

        return new VerificationResult(valid, issues);
    }

    private Optional<String> detectVectorAnomaly(float[] vector) {
        if (vector.length == 0) {
            return Optional.of("EMPTY_VECTOR: Vector has no dimensions");
        }

        boolean allZeros = true;
        for (float v : vector) {
            if (v != 0.0f) {
                allZeros = false;
                break;
            }
        }
        if (allZeros) {
            poisoningDetected++;
            return Optional.of("ZERO_VECTOR: All dimensions are zero (invalid embedding)");
        }

        for (float v : vector) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                poisoningDetected++;
                return Optional.of("INVALID_VALUES: Vector contains NaN or Infinite values");
            }
            if (Math.abs(v) > 100) {
                poisoningDetected++;
                return Optional.of("EXTREME_VALUES: Vector contains suspiciously large values");
            }
        }

        double magnitude = 0;
        for (float v : vector) {
            magnitude += v * v;
        }
        magnitude = Math.sqrt(magnitude);

        if (magnitude < 0.1) {
            return Optional.of("LOW_MAGNITUDE: Vector magnitude too low (" + magnitude + ")");
        }
        if (magnitude > 10) {
            return Optional.of("HIGH_MAGNITUDE: Vector magnitude too high (" + magnitude + ")");
        }

        return Optional.empty();
    }

    public AuditResult auditSemanticCache(Connection connection) {
        log.info("[VectorVerificationService] Starting semantic cache audit...");

        List<PoisonedEntry> poisonedEntries = new ArrayList<>();
        int totalScanned = 0;
        int validEntries = 0;

        String sql = """
            SELECT id, prompt_text, prompt_hash, embedding_json, source_content_hash
            FROM semantic_cache
            """;

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                totalScanned++;

                long id = rs.getLong("id");
                String promptText = rs.getString("prompt_text");
                String promptHash = rs.getString("prompt_hash");
                String embeddingJson = rs.getString("embedding_json");
                String sourceHash = rs.getString("source_content_hash");

                if (sourceHash != null) {
                    String computedHash = computeContentHash(promptText);
                    if (!sourceHash.equals(computedHash)) {
                        poisonedEntries.add(new PoisonedEntry(
                                id, "semantic_cache", "CONTENT_TAMPERED",
                                "Prompt text was modified after embedding generation",
                                sourceHash, computedHash
                        ));
                        continue;
                    }
                }

                if (embeddingJson != null) {
                    try {
                        float[] vector = parseVectorJson(embeddingJson);
                        Optional<String> anomaly = detectVectorAnomaly(vector);
                        if (anomaly.isPresent()) {
                            poisonedEntries.add(new PoisonedEntry(
                                    id, "semantic_cache", "VECTOR_ANOMALY",
                                    anomaly.get(), null, null
                            ));
                            continue;
                        }
                    } catch (Exception e) {
                        poisonedEntries.add(new PoisonedEntry(
                                id, "semantic_cache", "MALFORMED_VECTOR",
                                "Cannot parse embedding JSON: " + e.getMessage(),
                                null, null
                        ));
                        continue;
                    }
                }

                validEntries++;
            }
        } catch (SQLException e) {
            log.error("[VectorVerificationService] Audit failed: {}", e.getMessage());
            return new AuditResult(totalScanned, validEntries, poisonedEntries,
                    "Audit failed: " + e.getMessage());
        }

        log.info("[VectorVerificationService] Audit complete: {}/{} valid, {} poisoned",
                validEntries, totalScanned, poisonedEntries.size());

        return new AuditResult(totalScanned, validEntries, poisonedEntries, null);
    }

    private float[] parseVectorJson(String json) {
        String cleaned = json.trim();
        if (!cleaned.startsWith("[") || !cleaned.endsWith("]")) {
            throw new IllegalArgumentException("Invalid vector JSON format");
        }

        cleaned = cleaned.substring(1, cleaned.length() - 1);
        if (cleaned.isEmpty()) {
            return new float[0];
        }

        String[] parts = cleaned.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }

        return vector;
    }

    public boolean quarantineEntry(Connection connection, PoisonedEntry poisonedEntry,
                                    String originalContent) {
        ensureQuarantineTableExists(connection);

        String sql = """
            INSERT INTO quarantined_messages
            (original_id, table_source, quarantine_reason, original_content,
             expected_hash, actual_hash, quarantine_timestamp, reviewed)
            VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, String.valueOf(poisonedEntry.id()));
            pstmt.setString(2, poisonedEntry.tableSource());
            pstmt.setString(3, poisonedEntry.reason());
            pstmt.setString(4, originalContent);
            pstmt.setString(5, poisonedEntry.expectedHash());
            pstmt.setString(6, poisonedEntry.actualHash());
            pstmt.setLong(7, Instant.now().getEpochSecond());

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                vectorsQuarantined++;
                log.info("[VectorVerificationService] Quarantined entry {} from {}",
                        poisonedEntry.id(), poisonedEntry.tableSource());
                return true;
            }
        } catch (SQLException e) {
            log.error("[VectorVerificationService] Failed to quarantine entry: {}", e.getMessage());
        }

        return false;
    }

    private void ensureQuarantineTableExists(Connection connection) {
        String sql = """
            CREATE TABLE IF NOT EXISTS quarantined_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                original_id TEXT NOT NULL,
                table_source TEXT NOT NULL,
                quarantine_reason TEXT NOT NULL,
                original_content TEXT NOT NULL,
                expected_hash TEXT,
                actual_hash TEXT,
                quarantine_timestamp INTEGER NOT NULL,
                reviewed BOOLEAN DEFAULT FALSE,
                reviewer_notes TEXT
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_quarantine_timestamp ON quarantined_messages(quarantine_timestamp)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_reviewed ON quarantined_messages(reviewed)");
        } catch (SQLException e) {
            log.error("[VectorVerificationService] Failed to create quarantine table: {}", e.getMessage());
        }
    }

    public boolean removeFromSource(Connection connection, String tableSource, long id) {
        String sql = "DELETE FROM " + tableSource + " WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                log.info("[VectorVerificationService] Removed poisoned entry {} from {}", id, tableSource);
                return true;
            }
        } catch (SQLException e) {
            log.error("[VectorVerificationService] Failed to remove entry: {}", e.getMessage());
        }

        return false;
    }

    public boolean markForReindexing(Connection connection, String tableSource, long id) {
        String sql = "UPDATE " + tableSource + " SET embedding_json = NULL, source_content_hash = NULL WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                vectorsReindexed++;
                log.info("[VectorVerificationService] Marked entry {} for re-indexing", id);
                return true;
            }
        } catch (SQLException e) {
            log.error("[VectorVerificationService] Failed to mark for reindexing: {}", e.getMessage());
        }

        return false;
    }

    public long getVerificationsPerformed() {
        return verificationsPerformed;
    }

    public long getPoisoningDetected() {
        return poisoningDetected;
    }

    public long getVectorsQuarantined() {
        return vectorsQuarantined;
    }

    public long getVectorsReindexed() {
        return vectorsReindexed;
    }

    public String getStatsSummary() {
        return String.format(
                "[VectorVerificationService] Stats: verifications=%d, poisoning_detected=%d, quarantined=%d, reindexed=%d",
                verificationsPerformed, poisoningDetected, vectorsQuarantined, vectorsReindexed
        );
    }

    static void resetForTesting() {
        synchronized (LOCK) {
            instance = null;
        }
    }

    void resetStats() {
        verificationsPerformed = 0;
        poisoningDetected = 0;
        vectorsQuarantined = 0;
        vectorsReindexed = 0;
    }

    public record VerificationResult(
            boolean valid,
            List<String> issues
    ) {
        public String getSummary() {
            if (valid) {
                return "Embedding verified: No integrity issues detected";
            }
            return "Embedding COMPROMISED: " + String.join(", ", issues);
        }
    }

    public record PoisonedEntry(
            long id,
            String tableSource,
            String reason,
            String description,
            String expectedHash,
            String actualHash
    ) {}

    public record AuditResult(
            int totalScanned,
            int validEntries,
            List<PoisonedEntry> poisonedEntries,
            String error
    ) {
        public boolean hasErrors() {
            return error != null;
        }

        public boolean hasPoisonedEntries() {
            return !poisonedEntries.isEmpty();
        }

        public double getIntegrityRate() {
            if (totalScanned == 0) return 100.0;
            return (validEntries * 100.0) / totalScanned;
        }

        public String getSummary() {
            if (hasErrors()) {
                return "Audit failed: " + error;
            }
            return String.format("Audit complete: %d/%d valid (%.1f%%), %d poisoned",
                    validEntries, totalScanned, getIntegrityRate(), poisonedEntries.size());
        }
    }
}
