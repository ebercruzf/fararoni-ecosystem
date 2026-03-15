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
package dev.fararoni.core.core.utils;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("CommandSuggester Tests")
class CommandSuggesterTest {
    private static final Set<String> COMMANDS = Set.of(
            "git", "config", "load", "help", "status", "clear", "exit", "history"
    );

    @Nested
    @DisplayName("computeLevenshtein")
    class LevenshteinTests {
        @Test
        @DisplayName("Strings identicos deben tener distancia 0")
        void identicalStrings_ReturnsZero() {
            assertEquals(0, CommandSuggester.computeLevenshtein("git", "git"));
            assertEquals(0, CommandSuggester.computeLevenshtein("", ""));
            assertEquals(0, CommandSuggester.computeLevenshtein("config", "config"));
        }

        @ParameterizedTest(name = "Levenshtein({0}, {1}) = {2}")
        @CsvSource({
            "git, gti, 2",
            "config, confg, 1",
            "status, statsu, 2",
            "help, hepl, 2",
            "load, laod, 2",
            "abc, xyz, 3",
            "a, ab, 1",
            "ab, a, 1",
            "cat, cut, 1",
            "saturday, sunday, 3"
        })
        @DisplayName("computeLevenshtein debe calcular correctamente la distancia")
        void computeLevenshtein_VariousPairs_CalculatesCorrectly(String s1, String s2, int expected) {
            assertEquals(expected, CommandSuggester.computeLevenshtein(s1, s2));
        }

        @Test
        @DisplayName("Distancia debe ser simetrica")
        void levenshtein_ShouldBeSymmetric() {
            assertEquals(
                    CommandSuggester.computeLevenshtein("git", "gti"),
                    CommandSuggester.computeLevenshtein("gti", "git")
            );
            assertEquals(
                    CommandSuggester.computeLevenshtein("config", "confg"),
                    CommandSuggester.computeLevenshtein("confg", "config")
            );
        }

        @Test
        @DisplayName("String vacio vs no vacio debe retornar longitud")
        void levenshtein_EmptyVsNonEmpty_ReturnsLength() {
            assertEquals(3, CommandSuggester.computeLevenshtein("", "git"));
            assertEquals(3, CommandSuggester.computeLevenshtein("git", ""));
            assertEquals(6, CommandSuggester.computeLevenshtein("", "config"));
        }

        @Test
        @DisplayName("computeLevenshtein debe lanzar excepcion para null")
        void computeLevenshtein_Null_ThrowsException() {
            assertThrows(NullPointerException.class,
                    () -> CommandSuggester.computeLevenshtein(null, "git"));
            assertThrows(NullPointerException.class,
                    () -> CommandSuggester.computeLevenshtein("git", null));
        }
    }

    @Nested
    @DisplayName("suggest (sugerencia unica)")
    class SuggestTests {
        @ParameterizedTest(name = "suggest({0}) = {1}")
        @CsvSource({
            "gti, git",
            "confg, config",
            "staus, status",
            "hep, help",
            "cler, clear",
            "laod, load"
        })
        @DisplayName("Typos comunes deben sugerir el comando correcto")
        void suggest_CommonTypos_SuggestsCorrectCommand(String input, String expected) {
            Optional<String> result = CommandSuggester.suggest(input, COMMANDS);
            assertTrue(result.isPresent(), "Deberia haber sugerencia para: " + input);
            assertEquals(expected, result.get());
        }

        @ParameterizedTest(name = "suggest({0}) debe retornar empty")
        @ValueSource(strings = {"xyz", "qwerty", "abcdefg", "123456"})
        @DisplayName("Inputs sin similitud no deben tener sugerencia")
        void suggest_NoSimilarity_ReturnsEmpty(String input) {
            Optional<String> result = CommandSuggester.suggest(input, COMMANDS);
            assertTrue(result.isEmpty(), "No deberia haber sugerencia para: " + input);
        }

        @Test
        @DisplayName("Input null debe retornar empty")
        void suggest_NullInput_ReturnsEmpty() {
            Optional<String> result = CommandSuggester.suggest(null, COMMANDS);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Input vacio debe retornar empty")
        void suggest_EmptyInput_ReturnsEmpty() {
            Optional<String> result = CommandSuggester.suggest("", COMMANDS);
            assertTrue(result.isEmpty());

            result = CommandSuggester.suggest("   ", COMMANDS);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("validCommands null debe lanzar excepcion")
        void suggest_NullCommands_ThrowsException() {
            assertThrows(NullPointerException.class,
                    () -> CommandSuggester.suggest("git", null));
        }

        @Test
        @DisplayName("Comandos vacios en la coleccion deben ignorarse")
        void suggest_EmptyCommandsInCollection_ShouldBeIgnored() {
            Set<String> commandsWithEmpty = Set.of("git", "", "config", "   ");
            Optional<String> result = CommandSuggester.suggest("gti", commandsWithEmpty);
            assertTrue(result.isPresent());
            assertEquals("git", result.get());
        }

        @Test
        @DisplayName("Sugerencia debe ser case-insensitive")
        void suggest_ShouldBeCaseInsensitive() {
            Optional<String> result = CommandSuggester.suggest("GTI", COMMANDS);
            assertTrue(result.isPresent());
            assertEquals("git", result.get());

            result = CommandSuggester.suggest("CONFG", COMMANDS);
            assertTrue(result.isPresent());
            assertEquals("config", result.get());
        }

        @Test
        @DisplayName("Comando exacto no debe sugerirse a si mismo")
        void suggest_ExactMatch_ReturnsEmpty() {
            Optional<String> result = CommandSuggester.suggest("git", COMMANDS);
            assertTrue(result.isEmpty(), "Comando exacto no necesita sugerencia");
        }
    }

    @Nested
    @DisplayName("suggestMultiple")
    class SuggestMultipleTests {
        @Test
        @DisplayName("suggestMultiple debe retornar lista ordenada por distancia")
        void suggestMultiple_ShouldReturnOrderedList() {
            Set<String> commands = Set.of("commit", "config", "command", "common");

            List<String> suggestions = CommandSuggester.suggestMultiple("comit", commands, 3);

            assertFalse(suggestions.isEmpty(), "Deberia haber sugerencias");
            assertEquals("commit", suggestions.get(0));
        }

        @Test
        @DisplayName("suggestMultiple debe respetar el limite maximo")
        void suggestMultiple_ShouldRespectLimit() {
            Set<String> commands = Set.of("a", "ab", "abc", "abcd", "abcde");

            List<String> suggestions = CommandSuggester.suggestMultiple("x", commands, 2);

            assertTrue(suggestions.size() <= 2, "No debe exceder el limite");
        }

        @Test
        @DisplayName("suggestMultiple con input null debe retornar lista vacia")
        void suggestMultiple_NullInput_ReturnsEmptyList() {
            List<String> suggestions = CommandSuggester.suggestMultiple(null, COMMANDS, 3);
            assertTrue(suggestions.isEmpty());
        }

        @Test
        @DisplayName("suggestMultiple con maxSuggestions < 1 debe lanzar excepcion")
        void suggestMultiple_InvalidLimit_ThrowsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> CommandSuggester.suggestMultiple("git", COMMANDS, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CommandSuggester.suggestMultiple("git", COMMANDS, -1));
        }
    }

    @Nested
    @DisplayName("similarity")
    class SimilarityTests {
        @Test
        @DisplayName("Strings identicos deben tener similitud 1.0")
        void similarity_IdenticalStrings_ReturnsOne() {
            assertEquals(1.0, CommandSuggester.similarity("git", "git"), 0.001);
            assertEquals(1.0, CommandSuggester.similarity("config", "config"), 0.001);
        }

        @Test
        @DisplayName("Strings completamente diferentes deben tener similitud baja")
        void similarity_DifferentStrings_ReturnsLow() {
            double similarity = CommandSuggester.similarity("abc", "xyz");
            assertEquals(0.0, similarity, 0.001);
        }

        @Test
        @DisplayName("Strings con un error deben tener similitud alta")
        void similarity_OneError_ReturnsHigh() {
            double similarity = CommandSuggester.similarity("config", "confg");
            assertTrue(similarity > 0.8, "Similitud debe ser > 0.8");
        }

        @Test
        @DisplayName("Similitud con null debe retornar 0.0")
        void similarity_Null_ReturnsZero() {
            assertEquals(0.0, CommandSuggester.similarity(null, "git"), 0.001);
            assertEquals(0.0, CommandSuggester.similarity("git", null), 0.001);
        }

        @Test
        @DisplayName("Dos strings vacios deben tener similitud 1.0")
        void similarity_EmptyStrings_ReturnsOne() {
            assertEquals(1.0, CommandSuggester.similarity("", ""), 0.001);
        }
    }

    @Nested
    @DisplayName("Threshold y Limites")
    class ThresholdTests {
        @Test
        @DisplayName("Comandos cortos permiten hasta 2 errores")
        void suggest_ShortCommands_AllowsTwoErrors() {
            Set<String> commands = Set.of("git");
            Optional<String> result = CommandSuggester.suggest("gti", commands);
            assertTrue(result.isPresent(), "Comandos cortos deben permitir 2 errores");
        }

        @Test
        @DisplayName("Comandos cortos con 3+ errores no deben sugerirse")
        void suggest_ShortCommandsTooManyErrors_ReturnsEmpty() {
            Set<String> commands = Set.of("abc");
            Optional<String> result = CommandSuggester.suggest("xyz", commands);
            assertTrue(result.isEmpty(), "3 errores en comando de 3 letras es demasiado");
        }

        @Test
        @DisplayName("Mas de 3 errores absolutos no deben sugerirse")
        void suggest_AbsoluteErrorsExceeded_ReturnsEmpty() {
            Set<String> commands = Set.of("configuration");
            Optional<String> result = CommandSuggester.suggest("zzzzzzzzzzzzz", commands);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Comandos largos con 50% de error deben sugerirse")
        void suggest_LongCommandAtThreshold_ShouldSuggest() {
            Set<String> commands = Set.of("config");
            Optional<String> result = CommandSuggester.suggest("conxxx", commands);
            assertTrue(result.isPresent(), "Deberia sugerir en el limite del threshold");
        }

        @Test
        @DisplayName("Comandos largos con > 50% de error no deben sugerirse")
        void suggest_LongCommandExceedsThreshold_ReturnsEmpty() {
            Set<String> commands = Set.of("config");
            Optional<String> result = CommandSuggester.suggest("zzzzzz", commands);
            assertTrue(result.isEmpty(), "No debe sugerir si excede 50% de error");
        }
    }

    @Nested
    @DisplayName("Rendimiento")
    class PerformanceTests {
        @Test
        @DisplayName("Levenshtein debe ser eficiente para strings largos")
        void computeLevenshtein_LongStrings_ShouldBeEfficient() {
            String s1 = "a".repeat(100);
            String s2 = "b".repeat(100);

            long start = System.nanoTime();
            int distance = CommandSuggester.computeLevenshtein(s1, s2);
            long elapsed = System.nanoTime() - start;

            assertEquals(100, distance);
            assertTrue(elapsed < 100_000_000, "Deberia completarse en < 100ms");
        }

        @Test
        @DisplayName("suggest con muchos comandos debe ser razonable")
        void suggest_ManyCommands_ShouldBeReasonable() {
            Set<String> manyCommands = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                manyCommands.add("command" + i);
            }

            long start = System.nanoTime();
            Optional<String> result = CommandSuggester.suggest("comand1", manyCommands);
            long elapsed = System.nanoTime() - start;

            assertTrue(result.isPresent());
            assertTrue(elapsed < 500_000_000, "Deberia completarse en < 500ms");
        }
    }
}
