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
package dev.fararoni.core.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
class IdempotencyFilterTest {
    private IdempotencyFilter filter;

    @BeforeEach
    void setup() {
        filter = new IdempotencyFilter(Duration.ofMinutes(1), 100);
    }

    @Test
    @DisplayName("Primer mensaje debe ser procesado")
    void firstMessage_ShouldBeProcessed() {
        assertTrue(filter.tryProcess("msg-001"));
    }

    @Test
    @DisplayName("Mensaje duplicado debe ser bloqueado")
    void duplicateMessage_ShouldBeBlocked() {
        assertTrue(filter.tryProcess("msg-002"));
        assertFalse(filter.tryProcess("msg-002"));
        assertFalse(filter.tryProcess("msg-002"));
    }

    @Test
    @DisplayName("Diferentes mensajes deben ser procesados")
    void differentMessages_ShouldBeProcessed() {
        assertTrue(filter.tryProcess("msg-A"));
        assertTrue(filter.tryProcess("msg-B"));
        assertTrue(filter.tryProcess("msg-C"));
    }

    @Test
    @DisplayName("Métricas deben reflejar duplicados bloqueados")
    void stats_ShouldReflectDuplicates() {
        filter.tryProcess("msg-X");
        filter.tryProcess("msg-X");
        filter.tryProcess("msg-X");

        var stats = filter.getStats();
        assertEquals(1, stats.uniqueProcessed());
        assertEquals(2, stats.duplicatesBlocked());
    }

    @Test
    @DisplayName("Clear debe permitir reprocesar")
    void clear_ShouldAllowReprocessing() {
        filter.tryProcess("msg-Y");
        assertFalse(filter.tryProcess("msg-Y"));

        filter.clear();

        assertTrue(filter.tryProcess("msg-Y"));
    }

    @Test
    @DisplayName("Remove debe permitir reprocesar ID específico")
    void remove_ShouldAllowReprocessingSpecificId() {
        filter.tryProcess("msg-Z");
        assertFalse(filter.tryProcess("msg-Z"));

        filter.remove("msg-Z");

        assertTrue(filter.tryProcess("msg-Z"));
    }

    @Test
    @DisplayName("isDuplicate debe detectar duplicados")
    void isDuplicate_ShouldDetect() {
        assertFalse(filter.isDuplicate("new-msg"));
        filter.markProcessed("new-msg");
        assertTrue(filter.isDuplicate("new-msg"));
    }

    @Test
    @DisplayName("Null y blank IDs no deben bloquear")
    void nullAndBlankIds_ShouldNotBlock() {
        assertTrue(filter.tryProcess(null));
        assertTrue(filter.tryProcess(""));
        assertTrue(filter.tryProcess("   "));
    }

    @Test
    @DisplayName("Deduplication rate debe calcularse correctamente")
    void deduplicationRate_ShouldCalculate() {
        filter.tryProcess("id-1");
        filter.tryProcess("id-1");
        filter.tryProcess("id-2");
        filter.tryProcess("id-1");
        filter.tryProcess("id-2");

        var stats = filter.getStats();
        assertEquals(2, stats.uniqueProcessed());
        assertEquals(3, stats.duplicatesBlocked());
        assertEquals(60.0, stats.deduplicationRate(), 0.01);
    }

    @Test
    @DisplayName("Size debe reflejar IDs en cache")
    void size_ShouldReflectCacheSize() {
        assertEquals(0, filter.size());

        filter.tryProcess("a");
        filter.tryProcess("b");
        filter.tryProcess("c");

        assertEquals(3, filter.size());
    }

    @Test
    @DisplayName("Constructor por defecto debe usar valores correctos")
    void defaultConstructor_ShouldUseDefaults() {
        IdempotencyFilter defaultFilter = new IdempotencyFilter();
        var stats = defaultFilter.getStats();

        assertEquals(Duration.ofMinutes(10), stats.ttl());
        assertEquals(10_000, stats.maxSize());
    }

    @Test
    @DisplayName("Stats toString debe ser legible")
    void statsToString_ShouldBeReadable() {
        filter.tryProcess("test");
        filter.tryProcess("test");

        String str = filter.getStats().toString();

        assertTrue(str.contains("unique=1"));
        assertTrue(str.contains("duplicates=1"));
        assertTrue(str.contains("rate=50.0%"));
    }
}
