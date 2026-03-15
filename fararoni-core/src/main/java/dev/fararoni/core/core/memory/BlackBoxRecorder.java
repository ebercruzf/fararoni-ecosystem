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
package dev.fararoni.core.core.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.fararoni.core.core.persistence.JournalManager;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class BlackBoxRecorder {
    private static final Logger LOG = Logger.getLogger(BlackBoxRecorder.class.getName());

    private static final int MAX_SAMPLE_SIZE = 50_000;
    private static final String TRAINING_FILE = "training_data.jsonl";
    private static final String REGRESSION_DIR = "auto_generated_tests";

    private static final Pattern[] PII_PATTERNS = {
        Pattern.compile("sk-[a-zA-Z0-9]{32,}"),
        Pattern.compile("sk-ant-[a-zA-Z0-9-]{32,}"),
        Pattern.compile("AKIA[0-9A-Z]{16}"),
        Pattern.compile("ghp_[a-zA-Z0-9]{36}"),
        Pattern.compile("glpat-[a-zA-Z0-9-]{20}"),
        Pattern.compile("password\\s*[=:]\\s*[\"']?[^\"'\\s]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("api[_-]?key\\s*[=:]\\s*[\"']?[a-zA-Z0-9]{20,}", Pattern.CASE_INSENSITIVE)
    };

    private final Path trainingPath;
    private final Path regressionDir;
    private final ObjectMapper mapper;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicLong sampleCount = new AtomicLong(0);
    private final AtomicLong testCount = new AtomicLong(0);
    private final AtomicLong piiBlockedCount = new AtomicLong(0);

    public BlackBoxRecorder() {
        this(
            Path.of(System.getProperty("user.home"), ".fararoni", "memory"),
            Path.of(System.getProperty("user.dir"), "gym", REGRESSION_DIR)
        );
    }

    public BlackBoxRecorder(Path trainingDir, Path regressionDir) {
        this.trainingPath = trainingDir.resolve(TRAINING_FILE);
        this.regressionDir = regressionDir;
        this.mapper = createMapper();
        ensureDirectories();
        LOG.info("[REC] BlackBoxRecorder inicializado. Training: " + trainingPath);
    }

    private ObjectMapper createMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(trainingPath.getParent());
            Files.createDirectories(regressionDir);
        } catch (IOException e) {
            LOG.warning("[REC] No se pudieron crear directorios: " + e.getMessage());
        }
    }

    public boolean recordTrainingSample(String input, String output) {
        if (input == null || input.isBlank() || output == null || output.isBlank()) {
            LOG.fine("[REC] Muestra ignorada: input u output vacio");
            return false;
        }

        if (input.length() + output.length() > MAX_SAMPLE_SIZE) {
            LOG.warning("[REC] Muestra ignorada: excede " + MAX_SAMPLE_SIZE + " bytes");
            return false;
        }

        if (containsPII(input) || containsPII(output)) {
            piiBlockedCount.incrementAndGet();
            LOG.warning("[REC] PII detectado. Grabacion abortada por seguridad.");
            return false;
        }

        ObjectNode record = mapper.createObjectNode();
        record.put("timestamp", Instant.now().toString());
        record.put("type", "training_sample");
        record.put("input", input);
        record.put("output", output);

        return writeSecurely(trainingPath, record, true);
    }

    public boolean recordTrainingSample(String input, String output, String language, String taskType) {
        if (input == null || input.isBlank() || output == null || output.isBlank()) {
            return false;
        }

        if (input.length() + output.length() > MAX_SAMPLE_SIZE) {
            return false;
        }

        if (containsPII(input) || containsPII(output)) {
            piiBlockedCount.incrementAndGet();
            LOG.warning("[REC] PII detectado. Grabacion abortada.");
            return false;
        }

        ObjectNode record = mapper.createObjectNode();
        record.put("timestamp", Instant.now().toString());
        record.put("type", "training_sample");
        record.put("input", input);
        record.put("output", output);
        record.put("language", language != null ? language : "unknown");
        record.put("task_type", taskType != null ? taskType : "unknown");

        return writeSecurely(trainingPath, record, true);
    }

    public Path generateRegressionTest(String targetFile, String brokenCode, String expectedPattern) {
        if (targetFile == null || targetFile.isBlank()) {
            LOG.warning("[QA] Test ignorado: targetFile vacio");
            return null;
        }

        if (brokenCode == null || brokenCode.isBlank()) {
            LOG.warning("[QA] Test ignorado: brokenCode vacio");
            return null;
        }

        if (containsPII(brokenCode)) {
            piiBlockedCount.incrementAndGet();
            LOG.warning("[QA] PII detectado en test. Generacion abortada.");
            return null;
        }

        long testId = System.currentTimeMillis();
        String testFileName = "test_" + testId + ".json";
        Path testPath = regressionDir.resolve(testFileName);

        ObjectNode testVector = mapper.createObjectNode();
        testVector.put("id", "auto_test_" + testId);
        testVector.put("created_at", Instant.now().toString());
        testVector.put("target_file", targetFile);
        testVector.put("initial_content", brokenCode);
        testVector.put("expected_pattern", expectedPattern != null ? expectedPattern : "SUCCESS");
        testVector.put("generated_by", "BlackBoxRecorder");

        if (writeSecurely(testPath, testVector, false)) {
            testCount.incrementAndGet();
            LOG.info("[QA] Nuevo test de regresion generado: " + testFileName);
            return testPath;
        }

        return null;
    }

    boolean containsPII(String text) {
        if (text == null) return false;

        for (Pattern pattern : PII_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean writeSecurely(Path path, ObjectNode node, boolean append) {
        writeLock.lock();
        try {
            java.io.File file = path.toFile();
            long freeSpace = file.getParentFile() != null ? file.getParentFile().getFreeSpace() : 0;
            if (freeSpace > 0 && freeSpace < 1024 * 1024) {
                LOG.severe("[REC] Espacio en disco critico. Grabacion abortada.");
                return false;
            }

            String json = mapper.writeValueAsString(node);

            if (append) {
                Files.writeString(
                    path,
                    json + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
                );
                sampleCount.incrementAndGet();
            } else {
                Files.writeString(
                    path,
                    json,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                );
            }
            return true;
        } catch (IOException e) {
            LOG.severe("[REC] Error escribiendo: " + e.getMessage());
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    public RecorderStats getStats() {
        return new RecorderStats(
            sampleCount.get(),
            testCount.get(),
            piiBlockedCount.get(),
            trainingPath,
            regressionDir
        );
    }

    public record RecorderStats(
        long trainingSamples,
        long regressionTests,
        long piiBlocked,
        Path trainingPath,
        Path regressionDir
    ) {
        @Override
        public String toString() {
            return String.format(
                "RecorderStats[samples=%d, tests=%d, piiBlocked=%d]",
                trainingSamples, regressionTests, piiBlocked
            );
        }
    }
}
