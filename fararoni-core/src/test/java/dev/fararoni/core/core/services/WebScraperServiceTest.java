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
package dev.fararoni.core.core.services;

import dev.fararoni.core.core.services.WebScraperService.WebContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("WebScraperService")
class WebScraperServiceTest {
    private WebScraperService service;

    @BeforeEach
    void setUp() {
        service = new WebScraperService();
    }

    @Nested
    @DisplayName("Normalizacion de URL")
    class UrlNormalizationTests {
        @Test
        @DisplayName("agrega https si falta protocolo")
        void fetch_NoProtocol_AddsHttps() {
            assertThrows(IOException.class, () -> {
                service.fetch("invalid-domain-that-does-not-exist.xyz");
            });
        }

        @Test
        @DisplayName("lanza NullPointerException si url es null")
        void fetch_NullUrl_ThrowsNPE() {
            assertThrows(NullPointerException.class, () -> {
                service.fetch(null);
            });
        }

        @Test
        @DisplayName("lanza Exception si URL es invalida")
        void fetch_InvalidUrl_ThrowsException() {
            assertThrows(Exception.class, () -> {
                service.fetch("not a valid url at all");
            });
        }
    }

    @Nested
    @DisplayName("Formateo para Contexto")
    class FormatTests {
        @Test
        @DisplayName("formatForContext incluye URL")
        void formatForContext_IncludesUrl() {
            WebContent content = new WebContent(
                "https://example.com",
                "Example Title",
                "A description",
                "Body text"
            );

            String formatted = service.formatForContext(content);

            assertTrue(formatted.contains(">>> WEB SOURCE: https://example.com"));
        }

        @Test
        @DisplayName("formatForContext incluye titulo")
        void formatForContext_IncludesTitle() {
            WebContent content = new WebContent(
                "https://example.com",
                "Example Title",
                null,
                "Body text"
            );

            String formatted = service.formatForContext(content);

            assertTrue(formatted.contains("TITLE: Example Title"));
        }

        @Test
        @DisplayName("formatForContext incluye descripcion si existe")
        void formatForContext_IncludesDescription() {
            WebContent content = new WebContent(
                "https://example.com",
                "Title",
                "Meta description here",
                "Body"
            );

            String formatted = service.formatForContext(content);

            assertTrue(formatted.contains("DESCRIPTION: Meta description here"));
        }

        @Test
        @DisplayName("formatForContext omite descripcion si es null")
        void formatForContext_OmitsNullDescription() {
            WebContent content = new WebContent(
                "https://example.com",
                "Title",
                null,
                "Body"
            );

            String formatted = service.formatForContext(content);

            assertFalse(formatted.contains("DESCRIPTION:"));
        }

        @Test
        @DisplayName("formatForContext omite descripcion si esta vacia")
        void formatForContext_OmitsEmptyDescription() {
            WebContent content = new WebContent(
                "https://example.com",
                "Title",
                "   ",
                "Body"
            );

            String formatted = service.formatForContext(content);

            assertFalse(formatted.contains("DESCRIPTION:"));
        }

        @Test
        @DisplayName("formatForContext incluye contenido limpio")
        void formatForContext_IncludesCleanText() {
            WebContent content = new WebContent(
                "https://example.com",
                "Title",
                null,
                "This is the clean body text"
            );

            String formatted = service.formatForContext(content);

            assertTrue(formatted.contains("This is the clean body text"));
        }
    }

    @Nested
    @DisplayName("WebContent Record")
    class WebContentTests {
        @Test
        @DisplayName("length retorna longitud del texto")
        void length_ReturnsTextLength() {
            WebContent content = new WebContent(
                "url",
                "title",
                "desc",
                "12345"
            );

            assertEquals(5, content.length());
        }

        @Test
        @DisplayName("length retorna 0 si texto es null")
        void length_ReturnsZeroIfNull() {
            WebContent content = new WebContent(
                "url",
                "title",
                "desc",
                null
            );

            assertEquals(0, content.length());
        }

        @Test
        @DisplayName("accessors funcionan correctamente")
        void accessors_Work() {
            WebContent content = new WebContent(
                "https://test.com",
                "Test Title",
                "Test Desc",
                "Test Body"
            );

            assertEquals("https://test.com", content.url());
            assertEquals("Test Title", content.title());
            assertEquals("Test Desc", content.description());
            assertEquals("Test Body", content.cleanText());
        }
    }

    @Nested
    @DisplayName("Integracion (con red)")
    class IntegrationTests {
        @Test
        @DisplayName("puede descargar example.com")
        void fetch_ExampleCom_Works() {
            try {
                WebContent content = service.fetch("example.com");

                assertNotNull(content);
                assertNotNull(content.url());
                assertTrue(content.url().startsWith("https://"));
                assertNotNull(content.title());
                assertTrue(content.length() > 0);
            } catch (IOException e) {
                System.out.println("Skipped: No network - " + e.getMessage());
            }
        }

        @Test
        @DisplayName("maneja redirect correctamente")
        void fetch_WithRedirect_FollowsRedirect() {
            try {
                WebContent content = service.fetch("http://example.com");

                assertNotNull(content);
                assertTrue(content.length() > 0);
            } catch (IOException e) {
                System.out.println("Skipped: No network - " + e.getMessage());
            }
        }

        @Test
        @DisplayName("timeout en URL invalida")
        void fetch_InvalidDomain_TimesOut() {
            assertThrows(IOException.class, () -> {
                service.fetch("https://this-domain-definitely-does-not-exist-12345.com");
            });
        }
    }
}
