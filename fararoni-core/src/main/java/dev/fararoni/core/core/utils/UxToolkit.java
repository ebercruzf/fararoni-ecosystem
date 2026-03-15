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

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class UxToolkit {
    private static final long MS_PER_SECOND = 1000L;

    private static final long MS_PER_MINUTE = 60 * MS_PER_SECOND;

    private static final long MS_PER_HOUR = 60 * MS_PER_MINUTE;

    private static final long MS_PER_DAY = 24 * MS_PER_HOUR;

    private static final long MS_PER_WEEK = 7 * MS_PER_DAY;

    private static final long MS_PER_MONTH = 30 * MS_PER_DAY;

    private static final long MS_PER_YEAR = 365 * MS_PER_DAY;

    private static final long SI_THRESHOLD = 1000L;

    private static final long IEC_THRESHOLD = 1024L;

    private static final String SI_UNITS = "kMGTPE";

    private static final String IEC_UNITS = "KMGTPE";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private UxToolkit() {
        throw new AssertionError("UxToolkit es una clase de utilidades estaticas");
    }

    public static String formatBytes(long bytes) {
        if (-SI_THRESHOLD < bytes && bytes < SI_THRESHOLD) {
            return bytes + " B";
        }

        CharacterIterator ci = new StringCharacterIterator(SI_UNITS);
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= SI_THRESHOLD;
            ci.next();
        }

        return String.format("%.1f %cB", bytes / (double) SI_THRESHOLD, ci.current());
    }

    public static String formatBytesIEC(long bytes) {
        if (-IEC_THRESHOLD < bytes && bytes < IEC_THRESHOLD) {
            return bytes + " B";
        }

        CharacterIterator ci = new StringCharacterIterator(IEC_UNITS);
        while (bytes <= -1_048_527 || bytes >= 1_048_527) {
            bytes /= IEC_THRESHOLD;
            ci.next();
        }

        return String.format("%.1f %ciB", bytes / (double) IEC_THRESHOLD, ci.current());
    }

    public static String timeAgo(long timestampMillis) {
        long now = System.currentTimeMillis();
        long diff = now - timestampMillis;

        if (diff < MS_PER_SECOND) {
            return "just now";
        }

        if (diff < MS_PER_MINUTE) {
            return (diff / MS_PER_SECOND) + "s ago";
        }

        if (diff < MS_PER_HOUR) {
            return (diff / MS_PER_MINUTE) + "m ago";
        }

        if (diff < MS_PER_DAY) {
            return (diff / MS_PER_HOUR) + "h ago";
        }

        if (diff < MS_PER_WEEK) {
            return (diff / MS_PER_DAY) + "d ago";
        }

        if (diff < MS_PER_MONTH) {
            return (diff / MS_PER_WEEK) + "w ago";
        }

        if (diff < MS_PER_YEAR) {
            return (diff / MS_PER_MONTH) + "mo ago";
        }

        return (diff / MS_PER_YEAR) + "y ago";
    }

    public static String timeAgo(Instant instant) {
        Objects.requireNonNull(instant, "instant no puede ser null");
        return timeAgo(instant.toEpochMilli());
    }

    public static String formatDate(long timestampMillis) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampMillis),
                ZoneId.systemDefault()
        );
        return DATE_FORMATTER.format(dateTime);
    }

    public static String formatTimeSmart(long timestampMillis) {
        long diff = System.currentTimeMillis() - timestampMillis;

        if (diff < MS_PER_WEEK) {
            return timeAgo(timestampMillis);
        }

        return formatDate(timestampMillis);
    }

    public static String formatDuration(Duration duration) {
        Objects.requireNonNull(duration, "duration no puede ser null");

        long totalSeconds = Math.abs(duration.getSeconds());

        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes < 60) {
            return String.format("%dm %ds", minutes, seconds);
        }

        long hours = minutes / 60;
        minutes = minutes % 60;
        return String.format("%dh %dm", hours, minutes);
    }

    public static String formatDurationCompact(long millis) {
        return formatDuration(Duration.ofMillis(Math.abs(millis)));
    }

    public static String formatDurationFromSeconds(long seconds) {
        return formatDuration(Duration.ofSeconds(Math.abs(seconds)));
    }

    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    public static String formatTokens(long tokens) {
        if (tokens < 1000) {
            return String.valueOf(tokens);
        }
        return String.format("%.1fk", tokens / 1000.0);
    }

    public static String truncate(String text, int maxLength) {
        if (maxLength < 4) {
            throw new IllegalArgumentException("maxLength debe ser al menos 4 para acomodar '...'");
        }

        if (text == null || text.isEmpty()) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 3) + "...";
    }

    public static String progressBar(double progress, int width) {
        if (progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("progress debe estar entre 0.0 y 1.0");
        }
        if (width < 1) {
            throw new IllegalArgumentException("width debe ser al menos 1");
        }

        int filled = (int) (progress * width);
        int empty = width - filled;

        return "█".repeat(filled) + "░".repeat(empty);
    }
}
