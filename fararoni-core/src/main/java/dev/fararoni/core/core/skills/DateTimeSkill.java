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
import dev.fararoni.bus.agent.api.ToolParameter;
import dev.fararoni.bus.agent.api.ToolSkill;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class DateTimeSkill implements ToolSkill {
    private static final String SKILL_NAME = "DATETIME";

    @Override
    public String getSkillName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return "Date and time operations - get current date, time, timestamps";
    }

    @AgentAction(
        name = "now",
        description = "Get the current date and time"
    )
    public String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @AgentAction(
        name = "today",
        description = "Get today's date"
    )
    public String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @AgentAction(
        name = "time",
        description = "Get the current time"
    )
    public String time() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    @AgentAction(
        name = "timestamp",
        description = "Get the current Unix timestamp (seconds since epoch)"
    )
    public long timestamp() {
        return System.currentTimeMillis() / 1000;
    }

    @AgentAction(
        name = "format",
        description = "Format the current date/time with a custom pattern"
    )
    public String format(
        @ToolParameter(
            name = "pattern",
            description = "Date/time format pattern (e.g., 'yyyy-MM-dd', 'HH:mm:ss', 'EEEE, MMMM d')"
        ) String pattern
    ) {
        if (pattern == null || pattern.isBlank()) {
            return "Error: Pattern is required";
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.now().format(formatter);
        } catch (IllegalArgumentException e) {
            return "Error: Invalid pattern - " + e.getMessage();
        }
    }

    @AgentAction(
        name = "timezone",
        description = "Get the current date/time in a specific timezone"
    )
    public String timezone(
        @ToolParameter(
            name = "timezone",
            description = "Timezone ID (e.g., 'America/New_York', 'Europe/London', 'Asia/Tokyo')"
        ) String timezone
    ) {
        if (timezone == null || timezone.isBlank()) {
            return "Error: Timezone is required";
        }
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime zdt = ZonedDateTime.now(zoneId);
            return zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        } catch (Exception e) {
            return "Error: Invalid timezone - " + e.getMessage();
        }
    }

    @AgentAction(
        name = "day_of_week",
        description = "Get the current day of the week"
    )
    public String dayOfWeek() {
        return LocalDate.now().getDayOfWeek().toString();
    }

    @AgentAction(
        name = "week_number",
        description = "Get the current week number of the year"
    )
    public int weekNumber() {
        return LocalDate.now().get(java.time.temporal.WeekFields.ISO.weekOfYear());
    }
}
