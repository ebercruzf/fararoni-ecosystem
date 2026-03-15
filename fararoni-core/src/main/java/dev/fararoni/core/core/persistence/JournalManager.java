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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.core.core.swarm.domain.SwarmMessage;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.zip.CRC32;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class JournalManager {
    private static final Logger LOG = Logger.getLogger(JournalManager.class.getName());

    private final Path journalDir;
    private final ObjectMapper mapper;

    private final Map<String, AtomicLong> sequenceCounters = new ConcurrentHashMap<>();

    private long entriesWritten = 0;
    private long entriesRead = 0;
    private long corruptedEntries = 0;
    private long writeFailures = 0;

    private boolean failOnWriteError = true;

    public JournalManager(String storagePath) {
        this(Paths.get(storagePath));
    }

    public JournalManager(Path journalDir) {
        this.journalDir = journalDir;
        this.mapper = createObjectMapper();

        try {
            Files.createDirectories(journalDir);
            LOG.info(() -> "[JOURNAL] Inicializado en: " + journalDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear directorio de journals: " + journalDir, e);
        }
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    public void append(String missionId, SwarmMessage msg) {
        Path file = getJournalFile(missionId);

        try {
            String jsonPayload = mapper.writeValueAsString(msg);

            long crc = calculateCRC32(jsonPayload);

            long seq = getNextSequence(missionId);

            String line = String.format("%s|%d|%d|%s%n",
                Instant.now().toString(),
                seq,
                crc,
                jsonPayload);

            Files.writeString(file, line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.SYNC);

            entriesWritten++;

            LOG.fine(() -> String.format(
                "[JOURNAL] Escrito seq=%d para misión %s: %s",
                seq, missionId, msg.type()));
        } catch (IOException e) {
            writeFailures++;
            LOG.severe(() -> String.format(
                "[JOURNAL] CRITICAL: Fallo de escritura para misión %s: %s",
                missionId, e.getMessage()));

            if (failOnWriteError) {
                throw new JournalWriteException(
                    "Fallo de persistencia para misión " + missionId, e);
            }
        }
    }

    public void appendBatch(String missionId, List<SwarmMessage> messages) {
        Path file = getJournalFile(missionId);
        StringBuilder batch = new StringBuilder();

        try {
            for (SwarmMessage msg : messages) {
                String jsonPayload = mapper.writeValueAsString(msg);
                long crc = calculateCRC32(jsonPayload);
                long seq = getNextSequence(missionId);

                batch.append(String.format("%s|%d|%d|%s%n",
                    Instant.now().toString(),
                    seq,
                    crc,
                    jsonPayload));
            }

            Files.writeString(file, batch.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.SYNC);

            entriesWritten += messages.size();

            LOG.info(() -> String.format(
                "[JOURNAL] Batch escrito: %d mensajes para misión %s",
                messages.size(), missionId));
        } catch (IOException e) {
            writeFailures++;
            if (failOnWriteError) {
                throw new JournalWriteException(
                    "Fallo de batch para misión " + missionId, e);
            }
        }
    }

    public List<SwarmMessage> replay(String missionId) {
        Path file = getJournalFile(missionId);

        if (!Files.exists(file)) {
            LOG.info(() -> "[JOURNAL] No existe diario para misión: " + missionId);
            return Collections.emptyList();
        }

        List<SwarmMessage> history = new ArrayList<>();

        try (var lines = Files.lines(file)) {
            lines.forEach(line -> {
                try {
                    SwarmMessage msg = parseLine(line, missionId);
                    if (msg != null) {
                        Map<String, Object> updatedMetadata = new java.util.HashMap<>(msg.metadata());
                        updatedMetadata.put("IS_REPLAY", true);
                        SwarmMessage replayMsg = new SwarmMessage(
                            msg.id(),
                            msg.senderId(),
                            msg.receiverId(),
                            msg.type(),
                            msg.content(),
                            updatedMetadata,
                            msg.timestamp(),
                            msg.correlationId()
                        );
                        history.add(replayMsg);
                        entriesRead++;
                    }
                } catch (Exception e) {
                    corruptedEntries++;
                    LOG.warning(() -> String.format(
                        "[JOURNAL] Línea corrupta descartada en misión %s: %s",
                        missionId, e.getMessage()));
                }
            });
        } catch (IOException e) {
            LOG.severe(() -> String.format(
                "[JOURNAL] Error leyendo diario de misión %s: %s",
                missionId, e.getMessage()));
            return Collections.emptyList();
        }

        LOG.info(() -> String.format(
            "[JOURNAL] Replay completado para misión %s: %d mensajes",
            missionId, history.size()));

        return history;
    }

    private SwarmMessage parseLine(String line, String missionId) {
        if (line == null || line.isBlank()) {
            return null;
        }

        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) {
            LOG.warning(() -> "[JOURNAL] Línea malformada (faltan campos): " + truncate(line, 50));
            return null;
        }

        try {
            long recordedCrc = Long.parseLong(parts[2]);
            String jsonPayload = parts[3];

            long calculatedCrc = calculateCRC32(jsonPayload);
            if (calculatedCrc != recordedCrc) {
                LOG.warning(() -> String.format(
                    "[JOURNAL] CRC mismatch en misión %s: esperado=%d, calculado=%d",
                    missionId, recordedCrc, calculatedCrc));
                corruptedEntries++;
                return null;
            }

            SwarmMessage msg = mapper.readValue(jsonPayload, SwarmMessage.class);
            LOG.fine(() -> "[JOURNAL] Mensaje deserializado OK: " + msg.id());
            return msg;
        } catch (Exception e) {
            LOG.warning(() -> String.format(
                "[JOURNAL] Error parseando línea en misión %s: %s - LINE: %s",
                missionId, e.getMessage(), truncate(line, 100)));
            return null;
        }
    }

    private Path getJournalFile(String missionId) {
        String safeName = missionId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return journalDir.resolve("mission_" + safeName + ".jsonl");
    }

    public boolean hasJournal(String missionId) {
        return Files.exists(getJournalFile(missionId));
    }

    public boolean deleteJournal(String missionId) {
        try {
            boolean deleted = Files.deleteIfExists(getJournalFile(missionId));
            if (deleted) {
                sequenceCounters.remove(missionId);
                LOG.info(() -> "[JOURNAL] Diario eliminado: " + missionId);
            }
            return deleted;
        } catch (IOException e) {
            LOG.warning(() -> "[JOURNAL] Error eliminando diario: " + e.getMessage());
            return false;
        }
    }

    public List<String> listPendingMissions() {
        try (var stream = Files.list(journalDir)) {
            return stream
                .filter(p -> p.toString().endsWith(".jsonl"))
                .map(p -> {
                    String name = p.getFileName().toString();
                    return name.substring(8, name.length() - 6);
                })
                .toList();
        } catch (IOException e) {
            LOG.warning(() -> "[JOURNAL] Error listando misiones: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private long calculateCRC32(String input) {
        CRC32 crc = new CRC32();
        crc.update(input.getBytes());
        return crc.getValue();
    }

    private long getNextSequence(String missionId) {
        return sequenceCounters
            .computeIfAbsent(missionId, k -> new AtomicLong(0))
            .incrementAndGet();
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    public void setFailOnWriteError(boolean fail) {
        this.failOnWriteError = fail;
    }

    public Path getJournalDir() {
        return journalDir;
    }

    public JournalMetrics getMetrics() {
        return new JournalMetrics(
            entriesWritten,
            entriesRead,
            corruptedEntries,
            writeFailures,
            sequenceCounters.size()
        );
    }

    public record JournalMetrics(
        long entriesWritten,
        long entriesRead,
        long corruptedEntries,
        long writeFailures,
        int activeMissions
    ) {
        public double corruptionRate() {
            long total = entriesWritten + entriesRead;
            return total > 0 ? (double) corruptedEntries / total : 0.0;
        }
    }

    public static class JournalWriteException extends RuntimeException {
        public JournalWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
