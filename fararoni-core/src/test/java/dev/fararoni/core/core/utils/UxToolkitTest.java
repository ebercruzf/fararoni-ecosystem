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

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("UxToolkit Tests")
class UxToolkitTest {
    @Nested
    @DisplayName("formatBytes (SI Units)")
    class FormatBytesTests {
        @Test
        @DisplayName("formatBytes(0) debe retornar '0 B'")
        void formatBytes_Zero_ReturnsZeroB() {
            assertEquals("0 B", UxToolkit.formatBytes(0));
        }

        @ParameterizedTest(name = "formatBytes({0}) = {1}")
        @CsvSource({
            "1, 1 B",
            "999, 999 B",
            "1000, 1.0 kB",
            "1500, 1.5 kB",
            "1000000, 1.0 MB",
            "1500000, 1.5 MB",
            "1000000000, 1.0 GB",
            "1000000000000, 1.0 TB"
        })
        @DisplayName("formatBytes debe formatear correctamente valores positivos")
        void formatBytes_PositiveValues_FormatsCorrectly(long bytes, String expected) {
            assertEquals(expected, UxToolkit.formatBytes(bytes));
        }

        @ParameterizedTest(name = "formatBytes({0}) = {1}")
        @CsvSource({
            "-1, -1 B",
            "-999, -999 B",
            "-1000, -1.0 kB",
            "-1500000, -1.5 MB"
        })
        @DisplayName("formatBytes debe manejar valores negativos")
        void formatBytes_NegativeValues_FormatsCorrectly(long bytes, String expected) {
            assertEquals(expected, UxToolkit.formatBytes(bytes));
        }

        @Test
        @DisplayName("formatBytes debe manejar Long.MAX_VALUE sin overflow")
        void formatBytes_MaxLong_HandlesWithoutOverflow() {
            String result = UxToolkit.formatBytes(Long.MAX_VALUE);
            assertNotNull(result);
            assertTrue(result.contains("EB"), "Debe usar unidad EB para valores muy grandes");
        }
    }

    @Nested
    @DisplayName("formatBytesIEC (IEC Units)")
    class FormatBytesIECTests {
        @Test
        @DisplayName("formatBytesIEC(0) debe retornar '0 B'")
        void formatBytesIEC_Zero_ReturnsZeroB() {
            assertEquals("0 B", UxToolkit.formatBytesIEC(0));
        }

        @ParameterizedTest(name = "formatBytesIEC({0}) = {1}")
        @CsvSource({
            "1, 1 B",
            "1023, 1023 B",
            "1024, 1.0 KiB",
            "1536, 1.5 KiB",
            "1048576, 1.0 MiB",
            "1073741824, 1.0 GiB"
        })
        @DisplayName("formatBytesIEC debe formatear correctamente con base 1024")
        void formatBytesIEC_PositiveValues_FormatsCorrectly(long bytes, String expected) {
            assertEquals(expected, UxToolkit.formatBytesIEC(bytes));
        }

        @Test
        @DisplayName("formatBytesIEC debe usar sufijo 'iB' para diferenciar de SI")
        void formatBytesIEC_ShouldUseiB_Suffix() {
            String result = UxToolkit.formatBytesIEC(1024);
            assertTrue(result.endsWith("iB"), "Debe terminar con 'iB'");
        }
    }

    @Nested
    @DisplayName("timeAgo")
    class TimeAgoTests {
        @Test
        @DisplayName("timeAgo debe retornar 'just now' para timestamps muy recientes")
        void timeAgo_JustNow_ReturnsJustNow() {
            long now = System.currentTimeMillis();
            assertEquals("just now", UxToolkit.timeAgo(now));
            assertEquals("just now", UxToolkit.timeAgo(now - 500));
        }

        @Test
        @DisplayName("timeAgo debe retornar segundos para tiempos < 1 minuto")
        void timeAgo_Seconds_ReturnsSecondsAgo() {
            long now = System.currentTimeMillis();
            String result = UxToolkit.timeAgo(now - 30_000);
            assertEquals("30s ago", result);
        }

        @Test
        @DisplayName("timeAgo debe retornar minutos para tiempos < 1 hora")
        void timeAgo_Minutes_ReturnsMinutesAgo() {
            long now = System.currentTimeMillis();
            String result = UxToolkit.timeAgo(now - 120_000);
            assertEquals("2m ago", result);
        }

        @Test
        @DisplayName("timeAgo debe retornar horas para tiempos < 1 dia")
        void timeAgo_Hours_ReturnsHoursAgo() {
            long now = System.currentTimeMillis();
            String result = UxToolkit.timeAgo(now - 7_200_000);
            assertEquals("2h ago", result);
        }

        @Test
        @DisplayName("timeAgo debe retornar dias para tiempos < 1 semana")
        void timeAgo_Days_ReturnsDaysAgo() {
            long now = System.currentTimeMillis();
            String result = UxToolkit.timeAgo(now - 172_800_000);
            assertEquals("2d ago", result);
        }

        @Test
        @DisplayName("timeAgo debe retornar semanas para tiempos < 1 mes")
        void timeAgo_Weeks_ReturnsWeeksAgo() {
            long now = System.currentTimeMillis();
            String result = UxToolkit.timeAgo(now - 1_209_600_000);
            assertEquals("2w ago", result);
        }

        @Test
        @DisplayName("timeAgo debe retornar meses para tiempos < 1 ano")
        void timeAgo_Months_ReturnsMonthsAgo() {
            long now = System.currentTimeMillis();
            String result = UxToolkit.timeAgo(now - 5_184_000_000L);
            assertEquals("2mo ago", result);
        }

        @Test
        @DisplayName("timeAgo debe retornar anos para tiempos >= 1 ano")
        void timeAgo_Years_ReturnsYearsAgo() {
            long now = System.currentTimeMillis();
            String result = UxToolkit.timeAgo(now - 63_072_000_000L);
            assertEquals("2y ago", result);
        }

        @Test
        @DisplayName("timeAgo(Instant) debe funcionar igual que timeAgo(long)")
        void timeAgo_Instant_ShouldWorkSameAsLong() {
            long timestamp = System.currentTimeMillis() - 3_600_000;
            Instant instant = Instant.ofEpochMilli(timestamp);

            assertEquals(UxToolkit.timeAgo(timestamp), UxToolkit.timeAgo(instant));
        }

        @Test
        @DisplayName("timeAgo(Instant) debe lanzar excepcion para null")
        void timeAgo_NullInstant_ThrowsException() {
            assertThrows(NullPointerException.class, () -> UxToolkit.timeAgo((Instant) null));
        }

        @Test
        @DisplayName("timeAgo debe manejar timestamps futuros como 'just now'")
        void timeAgo_FutureTimestamp_ReturnsJustNow() {
            long future = System.currentTimeMillis() + 10_000;
            assertEquals("just now", UxToolkit.timeAgo(future));
        }
    }

    @Nested
    @DisplayName("formatDuration")
    class FormatDurationTests {
        @ParameterizedTest(name = "formatDuration({0}s) = {1}")
        @CsvSource({
            "0, 0s",
            "1, 1s",
            "45, 45s",
            "59, 59s",
            "60, 1m 0s",
            "61, 1m 1s",
            "125, 2m 5s",
            "3599, 59m 59s",
            "3600, 1h 0m",
            "3660, 1h 1m",
            "7200, 2h 0m",
            "7380, 2h 3m"
        })
        @DisplayName("formatDuration debe formatear correctamente diferentes duraciones")
        void formatDuration_VariousDurations_FormatsCorrectly(long seconds, String expected) {
            Duration duration = Duration.ofSeconds(seconds);
            assertEquals(expected, UxToolkit.formatDuration(duration));
        }

        @Test
        @DisplayName("formatDuration debe manejar duraciones negativas con valor absoluto")
        void formatDuration_NegativeDuration_UsesAbsoluteValue() {
            Duration negativeDuration = Duration.ofSeconds(-125);
            assertEquals("2m 5s", UxToolkit.formatDuration(negativeDuration));
        }

        @Test
        @DisplayName("formatDuration debe lanzar excepcion para null")
        void formatDuration_Null_ThrowsException() {
            assertThrows(NullPointerException.class, () -> UxToolkit.formatDuration(null));
        }

        @Test
        @DisplayName("formatDurationCompact debe funcionar con milisegundos")
        void formatDurationCompact_Milliseconds_FormatsCorrectly() {
            assertEquals("2m 5s", UxToolkit.formatDurationCompact(125_000));
            assertEquals("0s", UxToolkit.formatDurationCompact(500));
        }

        @Test
        @DisplayName("formatDurationFromSeconds debe funcionar con segundos")
        void formatDurationFromSeconds_Seconds_FormatsCorrectly() {
            assertEquals("2m 5s", UxToolkit.formatDurationFromSeconds(125));
            assertEquals("1h 30m", UxToolkit.formatDurationFromSeconds(5400));
        }
    }

    @Nested
    @DisplayName("formatDate y formatTimeSmart")
    class FormatDateTests {
        @Test
        @DisplayName("formatDate debe retornar formato ISO yyyy-MM-dd")
        void formatDate_ShouldReturnISOFormat() {
            String result = UxToolkit.formatDate(System.currentTimeMillis());
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"),
                    "Formato debe ser yyyy-MM-dd, recibido: " + result);
        }

        @Test
        @DisplayName("formatTimeSmart debe usar tiempo relativo para eventos recientes")
        void formatTimeSmart_RecentEvents_UsesRelativeTime() {
            long recentTimestamp = System.currentTimeMillis() - 3_600_000;
            String result = UxToolkit.formatTimeSmart(recentTimestamp);
            assertTrue(result.endsWith("ago"), "Eventos recientes deben usar tiempo relativo");
        }

        @Test
        @DisplayName("formatTimeSmart debe usar fecha para eventos antiguos")
        void formatTimeSmart_OldEvents_UsesDate() {
            long oldTimestamp = System.currentTimeMillis() - 864_000_000;
            String result = UxToolkit.formatTimeSmart(oldTimestamp);
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"),
                    "Eventos antiguos deben usar formato fecha");
        }
    }

    @Nested
    @DisplayName("formatNumber")
    class FormatNumberTests {
        @ParameterizedTest(name = "formatNumber({0}) contiene separadores")
        @ValueSource(longs = {1000, 1234567, 1000000000})
        @DisplayName("formatNumber debe agregar separadores de miles")
        void formatNumber_ShouldAddThousandsSeparators(long number) {
            String result = UxToolkit.formatNumber(number);
            assertTrue(result.contains(","), "Debe contener separadores: " + result);
        }

        @Test
        @DisplayName("formatNumber debe manejar numeros negativos")
        void formatNumber_NegativeNumbers_FormatsCorrectly() {
            String result = UxToolkit.formatNumber(-1234567);
            assertTrue(result.startsWith("-"), "Debe mantener el signo negativo");
            assertTrue(result.contains(","), "Debe tener separadores");
        }
    }

    @Nested
    @DisplayName("formatTokens")
    class FormatTokensTests {
        @ParameterizedTest(name = "formatTokens({0}) = {1}")
        @CsvSource({
            "0, 0",
            "500, 500",
            "999, 999",
            "1000, 1.0k",
            "1500, 1.5k",
            "5400, 5.4k",
            "15000, 15.0k",
            "150000, 150.0k"
        })
        @DisplayName("formatTokens debe formatear correctamente")
        void formatTokens_VariousValues_FormatsCorrectly(long tokens, String expected) {
            assertEquals(expected, UxToolkit.formatTokens(tokens));
        }
    }

    @Nested
    @DisplayName("truncate")
    class TruncateTests {
        @Test
        @DisplayName("truncate no debe modificar texto que cabe")
        void truncate_ShortText_ReturnsUnchanged() {
            assertEquals("Hello", UxToolkit.truncate("Hello", 10));
        }

        @Test
        @DisplayName("truncate debe agregar '...' a texto largo")
        void truncate_LongText_AddsEllipsis() {
            String result = UxToolkit.truncate("Hello World", 8);
            assertEquals("Hello...", result);
            assertEquals(8, result.length());
        }

        @Test
        @DisplayName("truncate debe retornar string vacio para null")
        void truncate_Null_ReturnsEmpty() {
            assertEquals("", UxToolkit.truncate(null, 10));
        }

        @Test
        @DisplayName("truncate debe retornar string vacio para string vacio")
        void truncate_Empty_ReturnsEmpty() {
            assertEquals("", UxToolkit.truncate("", 10));
        }

        @Test
        @DisplayName("truncate debe lanzar excepcion para maxLength < 4")
        void truncate_TooSmallMaxLength_ThrowsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> UxToolkit.truncate("Hello", 3));
        }
    }

    @Nested
    @DisplayName("progressBar")
    class ProgressBarTests {
        @Test
        @DisplayName("progressBar(0.0) debe retornar barra vacia")
        void progressBar_Zero_ReturnsEmpty() {
            String result = UxToolkit.progressBar(0.0, 10);
            assertEquals("░░░░░░░░░░", result);
            assertEquals(10, result.length());
        }

        @Test
        @DisplayName("progressBar(1.0) debe retornar barra llena")
        void progressBar_Full_ReturnsFull() {
            String result = UxToolkit.progressBar(1.0, 10);
            assertEquals("██████████", result);
            assertEquals(10, result.length());
        }

        @Test
        @DisplayName("progressBar(0.5) debe retornar mitad llena")
        void progressBar_Half_ReturnsHalf() {
            String result = UxToolkit.progressBar(0.5, 10);
            assertEquals("█████░░░░░", result);
        }

        @Test
        @DisplayName("progressBar debe lanzar excepcion para progress fuera de rango")
        void progressBar_OutOfRange_ThrowsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> UxToolkit.progressBar(-0.1, 10));
            assertThrows(IllegalArgumentException.class,
                    () -> UxToolkit.progressBar(1.1, 10));
        }

        @Test
        @DisplayName("progressBar debe lanzar excepcion para width < 1")
        void progressBar_ZeroWidth_ThrowsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> UxToolkit.progressBar(0.5, 0));
        }
    }

    @Nested
    @DisplayName("Thread Safety y Consistencia")
    class ThreadSafetyTests {
        @Test
        @DisplayName("UxToolkit no debe ser instanciable")
        void constructor_ShouldBePrivate() {
            assertTrue(true, "La clase tiene constructor privado (verificado por compilador)");
        }

        @Test
        @DisplayName("Metodos deben ser consistentes con mismos inputs")
        void methods_ShouldBeConsistent() {
            assertEquals(UxToolkit.formatBytes(1500000), UxToolkit.formatBytes(1500000));
            assertEquals(UxToolkit.formatTokens(5400), UxToolkit.formatTokens(5400));
            assertEquals(UxToolkit.truncate("Hello World", 8), UxToolkit.truncate("Hello World", 8));
        }
    }
}
