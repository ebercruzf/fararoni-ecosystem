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
package dev.fararoni.core.core.surgical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("BitapMatcher Tests")
class BitapMatcherTest {
    private BitapMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new BitapMatcher();
    }

    @Nested
    @DisplayName("Exact Match Tests")
    class ExactMatchTests {
        @Test
        @DisplayName("Encuentra coincidencia exacta al inicio")
        void testExactMatchAtStart() {
            Optional<Integer> result = matcher.findExact("hello world", "hello");
            assertTrue(result.isPresent());
            assertEquals(0, result.get());
        }

        @Test
        @DisplayName("Encuentra coincidencia exacta en medio")
        void testExactMatchInMiddle() {
            Optional<Integer> result = matcher.findExact("hello world", "lo wo");
            assertTrue(result.isPresent());
            assertEquals(3, result.get());
        }

        @Test
        @DisplayName("Encuentra coincidencia exacta al final")
        void testExactMatchAtEnd() {
            Optional<Integer> result = matcher.findExact("hello world", "world");
            assertTrue(result.isPresent());
            assertEquals(6, result.get());
        }

        @Test
        @DisplayName("No encuentra cuando no hay match")
        void testNoMatch() {
            Optional<Integer> result = matcher.findExact("hello world", "xyz");
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Maneja null text")
        void testNullText() {
            Optional<Integer> result = matcher.findExact(null, "pattern");
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Maneja null pattern")
        void testNullPattern() {
            Optional<Integer> result = matcher.findExact("text", null);
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Maneja pattern vacio")
        void testEmptyPattern() {
            Optional<Integer> result = matcher.findExact("text", "");
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Fuzzy Match Tests")
    class FuzzyMatchTests {
        @Test
        @DisplayName("Encuentra match exacto con 0 errores al inicio")
        void testFuzzyExactMatchAtStart() {
            BitapMatcher.MatchResult result = matcher.findFuzzy("hello world", "hello", 1);
            assertTrue(result.found());
            assertEquals(0, result.position());
            assertEquals(0, result.errors());
        }

        @Test
        @DisplayName("Encuentra match con 1 error de sustitucion")
        void testFuzzyOneSubstitution() {
            BitapMatcher.MatchResult result = matcher.findFuzzy("hello world", "hxllo", 1);
            assertTrue(result.found());
            assertEquals(0, result.position());
            assertTrue(result.errors() <= 1);
        }

        @Test
        @DisplayName("Fuzzy match encuentra patron similar")
        void testFuzzyFindsSimilar() {
            BitapMatcher.MatchResult result = matcher.findFuzzy("the quick brown fox", "quik", 1);
            assertTrue(result.found());
            assertTrue(result.errors() <= 1);
        }

        @Test
        @DisplayName("Encuentra match con multiples errores permitidos")
        void testFuzzyMultipleErrors() {
            BitapMatcher.MatchResult result = matcher.findFuzzy("hello world", "hxllx", 2);
            assertTrue(result.found());
            assertEquals(0, result.position());
            assertTrue(result.errors() <= 2);
        }

        @Test
        @DisplayName("Maneja patron largo con fallback a exacto")
        void testLongPatternFallback() {
            String longPattern = "a".repeat(70);
            String text = "prefix" + longPattern + "suffix";

            BitapMatcher.MatchResult result = matcher.findFuzzy(text, longPattern, 1);
            assertTrue(result.found());
            assertEquals(6, result.position());
            assertEquals(0, result.errors());
        }
    }

    @Nested
    @DisplayName("Levenshtein Distance Tests")
    class LevenshteinTests {
        @Test
        @DisplayName("Distancia 0 para strings identicos")
        void testIdenticalStrings() {
            assertEquals(0, matcher.levenshteinDistance("hello", "hello"));
        }

        @Test
        @DisplayName("Distancia 1 para una sustitucion")
        void testOneSubstitution() {
            assertEquals(1, matcher.levenshteinDistance("hello", "hallo"));
        }

        @Test
        @DisplayName("Distancia 1 para una insercion")
        void testOneInsertion() {
            assertEquals(1, matcher.levenshteinDistance("hello", "helloo"));
        }

        @Test
        @DisplayName("Distancia 1 para una eliminacion")
        void testOneDeletion() {
            assertEquals(1, matcher.levenshteinDistance("hello", "helo"));
        }

        @Test
        @DisplayName("Distancia igual a longitud para string vacio")
        void testEmptyString() {
            assertEquals(5, matcher.levenshteinDistance("hello", ""));
            assertEquals(5, matcher.levenshteinDistance("", "world"));
        }

        @Test
        @DisplayName("Distancia maxima para strings completamente diferentes")
        void testCompletelyDifferent() {
            assertEquals(5, matcher.levenshteinDistance("aaaaa", "bbbbb"));
        }

        @Test
        @DisplayName("Maneja null strings")
        void testNullStrings() {
            assertEquals(0, matcher.levenshteinDistance(null, null));
            assertEquals(Integer.MAX_VALUE, matcher.levenshteinDistance("text", null));
            assertEquals(Integer.MAX_VALUE, matcher.levenshteinDistance(null, "text"));
        }
    }

    @Nested
    @DisplayName("MatchResult Tests")
    class MatchResultTests {
        @Test
        @DisplayName("found() retorna true para match valido")
        void testFoundTrue() {
            BitapMatcher.MatchResult result = new BitapMatcher.MatchResult(5, 1, 10);
            assertTrue(result.found());
        }

        @Test
        @DisplayName("found() retorna false para noMatch")
        void testFoundFalse() {
            BitapMatcher.MatchResult result = BitapMatcher.MatchResult.noMatch();
            assertFalse(result.found());
            assertEquals(-1, result.position());
        }
    }
}
