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
package dev.fararoni.core.core.safety.listeners;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.bus.InMemorySovereignBus;
import dev.fararoni.core.core.mission.events.FileWriteErrorEvent;
import dev.fararoni.core.core.mission.events.FileWriteIntentEvent;
import dev.fararoni.core.core.mission.events.FileWriteResultEvent;
import dev.fararoni.core.core.safety.IroncladGuard;
import dev.fararoni.core.core.safety.SafetyLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("FileSystemIntentListener - Pruebas de Concurrencia TOCTOU")
class FileSystemIntentListenerConcurrencyTest {
    private static final int THREAD_COUNT = 50;

    private SovereignEventBus bus;
    private FileSystemIntentListener listener;
    private Path tempDir;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("fararoni_test_");

        bus = new InMemorySovereignBus();

        IroncladGuard guard = new IroncladGuard();
        SafetyLayer safetyLayer = new SafetyLayer(tempDir);

        listener = new FileSystemIntentListener(
            bus,
            guard,
            safetyLayer,
            tempDir
        );

        bus.subscribe(FileWriteResultEvent.TOPIC, FileWriteResultEvent.class, env -> {
            successCount.incrementAndGet();
        });

        bus.subscribe(FileWriteErrorEvent.TOPIC, FileWriteErrorEvent.class, env -> {
            errorCount.incrementAndGet();
        });

        listener.start();

        successCount.set(0);
        errorCount.set(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {}
                });
        }
    }

    @Test
    @DisplayName("Escenario 1: Ataque Distribuido (50 hilos → 50 archivos diferentes)")
    void testConcurrentWritesToDifferentFiles() throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int agentId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    FileWriteIntentEvent intent = FileWriteIntentEvent.create(
                        "Agent-" + agentId,
                        "mission-test",
                        "test_file_" + agentId + ".java",
                        "class Test" + agentId + " { }"
                    );

                    bus.publish(
                        FileWriteIntentEvent.TOPIC,
                        SovereignEnvelope.create("test", intent)
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        endLatch.await();

        Thread.sleep(2000);

        System.out.println("[TEST-1] Éxitos: " + successCount.get() + ", Errores: " + errorCount.get());

        int minExpected = (int) (THREAD_COUNT * 0.7);
        assertTrue(successCount.get() >= minExpected,
            "Al menos " + minExpected + " hilos deberían escribir exitosamente. Actual: " + successCount.get());
        assertEquals(0, errorCount.get(),
            "No debería haber errores cuando cada hilo escribe a un archivo diferente");

        executor.shutdown();
    }

    @Test
    @DisplayName("Escenario 2: Serialización de escrituras (50 hilos → 1 mismo archivo)")
    void testConcurrentWritesToSameFile() throws Exception {
        Path sharedFile = tempDir.resolve("shared_file.java");
        String initialContent = "class Shared { int version = 0; }";
        Files.writeString(sharedFile, initialContent);
        listener.registerReadHash(sharedFile.toString(), initialContent);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int agentId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    FileWriteIntentEvent intent = FileWriteIntentEvent.create(
                        "Agent-" + agentId,
                        "mission-test",
                        "shared_file.java",
                        "class Shared { int version = " + agentId + "; }"
                    );

                    bus.publish(
                        FileWriteIntentEvent.TOPIC,
                        SovereignEnvelope.create("test", intent)
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        Thread.sleep(3000);

        System.out.println("[TEST-2] Éxitos: " + successCount.get() + ", Errores: " + errorCount.get());

        int totalProcessed = successCount.get() + errorCount.get();
        int minExpected = (int) (THREAD_COUNT * 0.3);
        assertTrue(totalProcessed >= minExpected,
            "Al menos " + minExpected + " escrituras deberían procesarse. Actual: " + totalProcessed);

        String finalContent = Files.readString(sharedFile);
        assertTrue(finalContent.contains("class Shared"),
            "El archivo final no debería estar corrupto");

        executor.shutdown();
    }

    @Test
    @Disabled("FASE 55.4.3: Java usa validacion minima (Patron Cursor/Aider). " +
              "Balanceo de llaves delegado a Maven/Gradle.")
    @DisplayName("Escenario 3: Validación de InMemoryJavaValidator [DISABLED]")
    void testJavaValidatorBlocksSyntaxErrors() throws Exception {
        FileWriteIntentEvent badIntent = FileWriteIntentEvent.create(
            "Agent-BadCode",
            "mission-test",
            "BrokenClass.java",
            "class BrokenClass { // falta cerrar llave"
        );

        bus.publish(
            FileWriteIntentEvent.TOPIC,
            SovereignEnvelope.create("test", badIntent)
        );

        Thread.sleep(1000);

        System.out.println("[TEST-3] Éxitos: " + successCount.get() + ", Errores: " + errorCount.get());

        assertEquals(0, successCount.get(),
            "Código con sintaxis inválida no debería escribirse");
        assertEquals(1, errorCount.get(),
            "Debería haber 1 error por sintaxis inválida");
    }
}
