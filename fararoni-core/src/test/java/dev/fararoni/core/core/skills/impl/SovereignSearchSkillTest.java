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
package dev.fararoni.core.core.skills.impl;

import dev.fararoni.core.core.skills.WebSearchSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("SovereignSearchSkill Tests")
class SovereignSearchSkillTest {
    private WebSearchSkill skill;

    @BeforeEach
    void setUp() {
        skill = new SovereignSearchSkill();
    }

    @Nested
    @DisplayName("Unit Tests (No Network)")
    class UnitTests {
        @Test
        @DisplayName("isAvailable should return true")
        void isAvailableShouldReturnTrue() {
            assertTrue(skill.isAvailable());
        }

        @Test
        @DisplayName("getProviderName should return DuckDuckGo")
        void getProviderNameShouldReturnDuckDuckGo() {
            assertEquals("DuckDuckGo (Sovereign)", skill.getProviderName());
        }

        @Test
        @DisplayName("search with null query should return error")
        void searchWithNullQueryShouldReturnError() {
            String result = skill.search(null);
            assertTrue(result.contains("Error"));
        }

        @Test
        @DisplayName("search with blank query should return error")
        void searchWithBlankQueryShouldReturnError() {
            String result = skill.search("   ");
            assertTrue(result.contains("Error"));
        }

        @Test
        @DisplayName("scrapeUrl with null should return error")
        void scrapeUrlWithNullShouldReturnError() {
            String result = skill.scrapeUrl(null);
            assertTrue(result.contains("Error"));
        }

        @Test
        @DisplayName("scrapeUrl with blank should return error")
        void scrapeUrlWithBlankShouldReturnError() {
            String result = skill.scrapeUrl("");
            assertTrue(result.contains("Error"));
        }
    }

    @Nested
    @DisplayName("Integration Tests (Require Network)")
    @EnabledIfEnvironmentVariable(named = "FARARONI_TEST_NETWORK", matches = "true")
    class IntegrationTests {
        @Test
        @DisplayName("search should return results for valid query")
        void searchShouldReturnResultsForValidQuery() {
            String result = skill.search("Java 25 features");

            assertNotNull(result);
            assertFalse(result.contains("Error"));
            assertTrue(result.contains("Resultados"));
        }

        @Test
        @DisplayName("scrapeUrl should return content for valid URL")
        void scrapeUrlShouldReturnContentForValidUrl() {
            String result = skill.scrapeUrl("https://example.com");

            assertNotNull(result);
            assertFalse(result.contains("Error"));
            assertTrue(result.length() > 100);
        }
    }
}
