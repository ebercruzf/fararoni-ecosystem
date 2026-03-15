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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SovereignSearchSkill implements WebSearchSkill {
    private static final Logger LOG = Logger.getLogger(SovereignSearchSkill.class.getName());

    private static final String DDG_SEARCH_URL = "https://html.duckduckgo.com/html/?q=";

    private static final String USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS = 8_000;

    private static final int MAX_RESULTS = 5;

    private static final int MAX_CONTENT_LENGTH = 50_000;

    @Override
    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "Error: Query de busqueda vacia.";
        }

        LOG.info(() -> "[SOVEREIGN] Buscando en DuckDuckGo: " + query);
        System.out.println("[SOVEREIGN] Buscando en DuckDuckGo (Sin API Key)...");

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = DDG_SEARCH_URL + encodedQuery;

            Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9,es;q=0.8")
                .referrer("https://duckduckgo.com/")
                .get();

            StringBuilder report = new StringBuilder();
            report.append("Resultados de busqueda para: \"").append(query).append("\"\n\n");

            Elements results = doc.select(".result__body");

            if (results.isEmpty()) {
                results = doc.select(".results .result");
            }

            int count = 0;
            for (Element result : results) {
                if (count >= MAX_RESULTS) break;

                Element titleEl = result.selectFirst(".result__a");
                Element snippetEl = result.selectFirst(".result__snippet");

                if (titleEl != null) {
                    String title = titleEl.text();
                    String link = extractCleanUrl(titleEl.attr("href"));
                    String snippet = (snippetEl != null) ? snippetEl.text() : "(Sin descripcion)";

                    count++;
                    report.append(String.format("%d. **%s**\n", count, title));
                    report.append(String.format("   URL: %s\n", link));
                    report.append(String.format("   > %s\n\n", truncateSnippet(snippet, 200)));
                }
            }

            if (count == 0) {
                return "No se encontraron resultados para: \"" + query + "\"\n" +
                       "(Sugerencia: Intenta con terminos mas especificos)";
            }

            report.append("---\n");
            report.append("Fuente: DuckDuckGo | Resultados: ").append(count);

            final int finalCount = count;
            LOG.info(() -> "[SOVEREIGN] Busqueda exitosa: " + finalCount + " resultados");
            return report.toString();
        } catch (IOException e) {
            LOG.warning(() -> "[SOVEREIGN] Error de conexion: " + e.getMessage());
            return "Error de conexion al buscador: " + e.getMessage() +
                   "\n(Sugerencia: Verifica tu conexion a Internet)";
        } catch (Exception e) {
            LOG.severe(() -> "[SOVEREIGN] Error inesperado: " + e.getMessage());
            return "Error inesperado durante la busqueda: " + e.getMessage();
        }
    }

    @Override
    public String scrapeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "Error: URL vacia.";
        }

        LOG.info(() -> "[SOVEREIGN] Leyendo contenido de: " + url);
        System.out.println("[SOVEREIGN] Leyendo sitio: " + url);

        try {
            String normalizedUrl = normalizeUrl(url);

            Document doc = Jsoup.connect(normalizedUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get();

            doc.select("script, style, nav, footer, aside, iframe, noscript, " +
                       ".ads, .advertisement, .cookie-banner, .popup, .modal").remove();

            String rawText = doc.body() != null ? doc.body().text() : "";

            final String text;
            if (rawText.length() > MAX_CONTENT_LENGTH) {
                text = rawText.substring(0, MAX_CONTENT_LENGTH) + "\n\n[... contenido truncado ...]";
            } else {
                text = rawText;
            }

            String title = doc.title();
            StringBuilder result = new StringBuilder();
            result.append("=== ").append(title).append(" ===\n");
            result.append("URL: ").append(normalizedUrl).append("\n\n");
            result.append(text);

            LOG.info(() -> "[SOVEREIGN] Contenido extraido: " + text.length() + " caracteres");
            return result.toString();
        } catch (IOException e) {
            LOG.warning(() -> "[SOVEREIGN] Error leyendo " + url + ": " + e.getMessage());
            return "No se pudo leer el sitio: " + e.getMessage();
        } catch (Exception e) {
            LOG.severe(() -> "[SOVEREIGN] Error inesperado: " + e.getMessage());
            return "Error inesperado: " + e.getMessage();
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "DuckDuckGo (Sovereign)";
    }

    private String extractCleanUrl(String href) {
        if (href == null || href.isBlank()) {
            return "(URL no disponible)";
        }

        if (href.contains("uddg=")) {
            int start = href.indexOf("uddg=") + 5;
            int end = href.indexOf("&", start);
            if (end == -1) end = href.length();
            String encoded = href.substring(start, end);
            try {
                return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return href;
            }
        }

        if (href.startsWith("http")) {
            return href;
        }

        return href;
    }

    private String truncateSnippet(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private String normalizeUrl(String url) {
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }
}
