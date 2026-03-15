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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("FuzzyMatcher")
class FuzzyMatcherTest {
    @Nested
    @DisplayName("matches")
    class MatchesTests {
        @Test
        @DisplayName("coincide con letras en orden")
        void matches_LettersInOrder_ReturnsTrue() {
            assertTrue(FuzzyMatcher.matches("uac", "UserAuthenticationController"));
            assertTrue(FuzzyMatcher.matches("abc", "AnyBigCat"));
            assertTrue(FuzzyMatcher.matches("main", "MainApplication"));
        }

        @Test
        @DisplayName("no coincide si letras no estan en orden")
        void matches_LettersNotInOrder_ReturnsFalse() {
            assertFalse(FuzzyMatcher.matches("cau", "UserAuthenticationController"));
            assertFalse(FuzzyMatcher.matches("zyx", "AnyBigCat"));
        }

        @Test
        @DisplayName("case insensitive")
        void matches_CaseInsensitive() {
            assertTrue(FuzzyMatcher.matches("UAC", "userauthcontroller"));
            assertTrue(FuzzyMatcher.matches("main", "MAIN_APPLICATION"));
        }

        @Test
        @DisplayName("patron vacio no coincide")
        void matches_EmptyPattern_ReturnsFalse() {
            assertFalse(FuzzyMatcher.matches("", "SomeText"));
        }

        @Test
        @DisplayName("null values retornan false")
        void matches_NullValues_ReturnsFalse() {
            assertFalse(FuzzyMatcher.matches(null, "text"));
            assertFalse(FuzzyMatcher.matches("pattern", null));
            assertFalse(FuzzyMatcher.matches(null, null));
        }

        @Test
        @DisplayName("coincide con substring exacto")
        void matches_ExactSubstring_ReturnsTrue() {
            assertTrue(FuzzyMatcher.matches("auth", "UserAuthController"));
            assertTrue(FuzzyMatcher.matches("test", "MyTestClass"));
        }
    }

    @Nested
    @DisplayName("score")
    class ScoreTests {
        @Test
        @DisplayName("score mayor para coincidencia al inicio")
        void score_StartMatch_HigherScore() {
            int startScore = FuzzyMatcher.score("user", "UserController");
            int middleScore = FuzzyMatcher.score("user", "MyUserController");

            assertTrue(startScore > middleScore,
                "Score al inicio (" + startScore + ") debe ser > score en medio (" + middleScore + ")");
        }

        @Test
        @DisplayName("score mayor para CamelCase match")
        void score_CamelCaseMatch_HigherScore() {
            int camelScore = FuzzyMatcher.score("uac", "UserAuthController");
            int regularScore = FuzzyMatcher.score("uac", "SomeUserAccountClass");

            assertTrue(camelScore > regularScore,
                "CamelCase score (" + camelScore + ") debe ser > regular (" + regularScore + ")");
        }

        @Test
        @DisplayName("score mayor para letras consecutivas")
        void score_ConsecutiveLetters_HigherScore() {
            int consecutiveScore = FuzzyMatcher.score("auth", "UserAuthController");
            int dispersedScore = FuzzyMatcher.score("auth", "AnyUserTypeHandler");

            assertTrue(consecutiveScore > dispersedScore,
                "Consecutivo (" + consecutiveScore + ") debe ser > disperso (" + dispersedScore + ")");
        }

        @Test
        @DisplayName("score cero si no hay coincidencia")
        void score_NoMatch_ReturnsZero() {
            assertEquals(0, FuzzyMatcher.score("xyz", "UserController"));
            assertEquals(0, FuzzyMatcher.score("zzz", "NoMatch"));
        }

        @Test
        @DisplayName("score con substring exacto es alto")
        void score_ExactSubstring_HighScore() {
            int exactScore = FuzzyMatcher.score("Controller", "UserController");
            int fuzzyScore = FuzzyMatcher.score("ctrl", "UserController");

            assertTrue(exactScore > fuzzyScore);
        }

        @Test
        @DisplayName("score mayor para patron mas largo que coincide")
        void score_LongerPatternMatch_ReasonableScore() {
            int shortScore = FuzzyMatcher.score("u", "UserController");
            int longScore = FuzzyMatcher.score("user", "UserController");

            assertTrue(longScore > shortScore);
        }
    }

    @Nested
    @DisplayName("rank")
    class RankTests {
        private final List<String> testCandidates = List.of(
            "UserAuthenticationController.java",
            "UserAccountConfig.java",
            "UnrelatedFile.java",
            "MainApplication.java",
            "UtilsAndConstants.java",
            "AuthService.java"
        );

        @Test
        @DisplayName("rankea por relevancia descendente")
        void rank_OrdersByScoreDescending() {
            List<String> results = FuzzyMatcher.rank("auth", testCandidates, 3);

            assertFalse(results.isEmpty());
            assertTrue(results.get(0).toLowerCase().contains("auth"));
        }

        @Test
        @DisplayName("respeta maxResults")
        void rank_RespectsMaxResults() {
            List<String> results = FuzzyMatcher.rank("a", testCandidates, 2);

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("retorna lista vacia para patron vacio")
        void rank_EmptyPattern_ReturnsEmpty() {
            List<String> results = FuzzyMatcher.rank("", testCandidates, 10);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("retorna lista vacia para maxResults cero")
        void rank_ZeroMaxResults_ReturnsEmpty() {
            List<String> results = FuzzyMatcher.rank("auth", testCandidates, 0);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("filtra candidatos que no coinciden")
        void rank_FiltersNonMatching() {
            List<String> results = FuzzyMatcher.rank("xyz", testCandidates, 10);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("rank sin limite retorna todos los matches")
        void rank_NoLimit_ReturnsAllMatches() {
            List<String> results = FuzzyMatcher.rank("a", testCandidates);

            assertTrue(results.size() > 1);
        }

        @Test
        @DisplayName("caso de uso real: buscar archivos Java")
        void rank_RealUseCase_JavaFiles() {
            List<String> files = List.of(
                "src/main/java/UserService.java",
                "src/main/java/UserController.java",
                "src/test/java/UserServiceTest.java",
                "src/main/java/ProductService.java",
                "src/main/java/OrderController.java"
            );

            List<String> results = FuzzyMatcher.rank("usc", files, 3);

            assertFalse(results.isEmpty());
            assertTrue(results.get(0).contains("User"));
        }
    }

    @Nested
    @DisplayName("findBest")
    class FindBestTests {
        @Test
        @DisplayName("retorna el mejor match")
        void findBest_ReturnsBestMatch() {
            List<String> candidates = List.of(
                "UserController.java",
                "AdminController.java",
                "UserService.java"
            );

            String best = FuzzyMatcher.findBest("user", candidates);

            assertNotNull(best);
            assertTrue(best.contains("User"));
        }

        @Test
        @DisplayName("retorna null si no hay match")
        void findBest_NoMatch_ReturnsNull() {
            List<String> candidates = List.of("Abc.java", "Def.java");

            String best = FuzzyMatcher.findBest("xyz", candidates);

            assertNull(best);
        }
    }

    @Nested
    @DisplayName("filter")
    class FilterTests {
        @Test
        @DisplayName("filtra candidatos que coinciden")
        void filter_ReturnsMatchingCandidates() {
            List<String> candidates = List.of(
                "UserController",
                "AdminController",
                "ProductService",
                "UserService"
            );

            List<String> filtered = FuzzyMatcher.filter("user", candidates);

            assertTrue(filtered.size() >= 2);
            assertTrue(filtered.contains("UserController"));
            assertTrue(filtered.contains("UserService"));
        }

        @Test
        @DisplayName("retorna lista vacia para patron null")
        void filter_NullPattern_ReturnsEmpty() {
            List<String> filtered = FuzzyMatcher.filter(null, List.of("a", "b"));

            assertTrue(filtered.isEmpty());
        }

        @Test
        @DisplayName("retorna lista vacia para candidates null")
        void filter_NullCandidates_ReturnsEmpty() {
            List<String> filtered = FuzzyMatcher.filter("test", null);

            assertTrue(filtered.isEmpty());
        }
    }

    @Nested
    @DisplayName("Word Start Detection")
    class WordStartTests {
        @Test
        @DisplayName("detecta CamelCase")
        void wordStart_CamelCase_Detected() {
            int score = FuzzyMatcher.score("uac", "UserAuthController");
            assertTrue(score > 0);

            int plainScore = FuzzyMatcher.score("uac", "userauthcontroller");

            assertTrue(score >= plainScore);
        }

        @Test
        @DisplayName("detecta separador underscore")
        void wordStart_Underscore_Detected() {
            int score = FuzzyMatcher.score("uac", "user_auth_controller");
            assertTrue(score > 0);
        }

        @Test
        @DisplayName("detecta separador guion")
        void wordStart_Dash_Detected() {
            int score = FuzzyMatcher.score("uac", "user-auth-controller");
            assertTrue(score > 0);
        }

        @Test
        @DisplayName("detecta separador punto")
        void wordStart_Dot_Detected() {
            int score = FuzzyMatcher.score("uc", "user.controller");
            assertTrue(score > 0);
        }

        @Test
        @DisplayName("detecta separador slash")
        void wordStart_Slash_Detected() {
            int score = FuzzyMatcher.score("uc", "src/user/controller");
            assertTrue(score > 0);
        }
    }

    @Nested
    @DisplayName("Validacion")
    class ValidationTests {
        @Test
        @DisplayName("rank lanza NPE si pattern es null")
        void rank_NullPattern_ThrowsNPE() {
            assertThrows(NullPointerException.class, () -> {
                FuzzyMatcher.rank(null, List.of("a"), 10);
            });
        }

        @Test
        @DisplayName("rank lanza NPE si candidates es null")
        void rank_NullCandidates_ThrowsNPE() {
            assertThrows(NullPointerException.class, () -> {
                FuzzyMatcher.rank("test", null, 10);
            });
        }

        @Test
        @DisplayName("maneja candidatos con null")
        void filter_CandidatesWithNull_SkipsNull() {
            List<String> candidates = new java.util.ArrayList<>();
            candidates.add("UserController");
            candidates.add(null);
            candidates.add("AdminController");

            List<String> filtered = FuzzyMatcher.filter("ctrl", candidates);

            assertFalse(filtered.contains(null));
        }
    }

    @Nested
    @DisplayName("Integracion")
    class IntegrationTests {
        @Test
        @DisplayName("escenario: buscar archivos en proyecto")
        void integration_ProjectFileSearch() {
            List<String> projectFiles = List.of(
                "src/main/java/com/example/UserController.java",
                "src/main/java/com/example/UserService.java",
                "src/main/java/com/example/UserRepository.java",
                "src/main/java/com/example/ProductController.java",
                "src/main/java/com/example/ProductService.java",
                "src/test/java/com/example/UserControllerTest.java",
                "src/test/java/com/example/UserServiceTest.java",
                "pom.xml",
                "README.md"
            );

            List<String> results = FuzzyMatcher.rank("usvc", projectFiles, 3);

            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(f -> f.contains("UserService")));
        }

        @Test
        @DisplayName("escenario: autocompletado de comandos")
        void integration_CommandAutocomplete() {
            List<String> commands = List.of(
                "git status",
                "git commit",
                "git push",
                "git pull",
                "git checkout",
                "gradle build",
                "gradle test",
                "mvn clean install"
            );

            List<String> results = FuzzyMatcher.rank("gco", commands, 3);

            assertFalse(results.isEmpty());
            assertTrue(results.stream().allMatch(r -> r.startsWith("git")));
            assertTrue(results.stream().anyMatch(r -> r.contains("checkout")));
        }

        @Test
        @DisplayName("escenario: ejemplo del Javadoc")
        void integration_JavadocExample() {
            List<String> files = List.of(
                "UserAuthenticationController.java",
                "UserAccountConfig.java",
                "UnrelatedFile.java",
                "MainApplication.java"
            );

            List<String> matches = FuzzyMatcher.rank("uac", files, 3);

            assertFalse(matches.isEmpty());

            assertTrue(matches.stream().anyMatch(f -> f.contains("UserAuth")));
            assertTrue(matches.stream().anyMatch(f -> f.contains("UserAccount")));

            assertFalse(matches.contains("MainApplication.java"));
        }
    }
}
