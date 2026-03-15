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
package dev.fararoni.core.core.skills;

import dev.fararoni.bus.agent.api.AgentAction;
import dev.fararoni.bus.agent.api.ToolSkill;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("DateTimeSkill Tests")
class DateTimeSkillTest {
    private DateTimeSkill skill;

    @BeforeEach
    void setUp() {
        skill = new DateTimeSkill();
    }

    @Nested
    @DisplayName("ToolSkill Interface")
    class ToolSkillInterfaceTests {
        @Test
        @DisplayName("Should implement ToolSkill")
        void shouldImplementToolSkill() {
            assertThat(skill).isInstanceOf(ToolSkill.class);
        }

        @Test
        @DisplayName("Should return DATETIME as skill name")
        void shouldReturnDatetimeAsSkillName() {
            assertThat(skill.getSkillName()).isEqualTo("DATETIME");
        }

        @Test
        @DisplayName("Should have description")
        void shouldHaveDescription() {
            assertThat(skill.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("Should have at least 6 actions")
        void shouldHaveAtLeast6Actions() {
            long actionCount = Arrays.stream(DateTimeSkill.class.getMethods())
                .filter(m -> m.isAnnotationPresent(AgentAction.class))
                .count();

            assertThat(actionCount).isGreaterThanOrEqualTo(6);
        }
    }

    @Nested
    @DisplayName("Actions")
    class ActionTests {
        @Test
        @DisplayName("now() should return ISO datetime")
        void nowShouldReturnIsoDatetime() {
            String result = skill.now();

            assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        }

        @Test
        @DisplayName("today() should return ISO date")
        void todayShouldReturnIsoDate() {
            String result = skill.today();

            assertThat(result).isEqualTo(LocalDate.now().toString());
        }

        @Test
        @DisplayName("time() should return HH:mm:ss")
        void timeShouldReturnFormattedTime() {
            String result = skill.time();

            assertThat(result).matches("\\d{2}:\\d{2}:\\d{2}");
        }

        @Test
        @DisplayName("timestamp() should return Unix timestamp")
        void timestampShouldReturnUnixTimestamp() {
            long result = skill.timestamp();
            long expected = System.currentTimeMillis() / 1000;

            assertThat(result).isBetween(expected - 2, expected + 2);
        }

        @Test
        @DisplayName("format() should use custom pattern")
        void formatShouldUseCustomPattern() {
            String result = skill.format("yyyy-MM-dd");

            assertThat(result).isEqualTo(LocalDate.now().toString());
        }

        @Test
        @DisplayName("format() should handle invalid pattern")
        void formatShouldHandleInvalidPattern() {
            String result = skill.format("invalid{{{");

            assertThat(result).containsIgnoringCase("error");
        }

        @Test
        @DisplayName("timezone() should return datetime in timezone")
        void timezoneShouldReturnDatetimeInTimezone() {
            String result = skill.timezone("UTC");

            assertThat(result).contains("UTC");
        }

        @Test
        @DisplayName("dayOfWeek() should return day name")
        void dayOfWeekShouldReturnDayName() {
            String result = skill.dayOfWeek();

            assertThat(result).isIn("MONDAY", "TUESDAY", "WEDNESDAY",
                "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        }

        @Test
        @DisplayName("weekNumber() should return valid week")
        void weekNumberShouldReturnValidWeek() {
            int result = skill.weekNumber();

            assertThat(result).isBetween(1, 53);
        }
    }
}
