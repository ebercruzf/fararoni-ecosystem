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

import dev.fararoni.core.core.commands.WebCommand;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Objects;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class WebScraperService {
    private static final String USER_AGENT =
        "Mozilla/5.0 (compatible; FararoniBot/1.0; +https://fararoni.ai/bot)";

    private static final int TIMEOUT_MS = 10_000;

    private static final String NOISE_SELECTORS =
        "script, style, nav, footer, aside, iframe, noscript, " +
        ".ads, .ad, .advertisement, .cookie-banner, .cookie-consent, " +
        ".popup, .modal, .sidebar, .menu, .navigation, " +
        "[role=navigation], [role=banner], [role=complementary]";

    private static final String BLOCK_SELECTORS = "h1, h2, h3, h4, h5, h6, p, div, li, tr, blockquote, pre";

    private static final int MAX_CONTENT_LENGTH = 100_000;

    public WebContent fetch(String url) throws IOException {
        Objects.requireNonNull(url, "url no puede ser null");

        String normalizedUrl = normalizeUrl(url);

        Document doc = Jsoup.connect(normalizedUrl)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS)
            .followRedirects(true)
            .get();

        String title = doc.title();
        String description = extractMetaDescription(doc);

        removeNoiseElements(doc);
        preserveBlockFormatting(doc);

        String cleanText = doc.body() != null ? doc.body().text() : "";

        if (cleanText.length() > MAX_CONTENT_LENGTH) {
            cleanText = cleanText.substring(0, MAX_CONTENT_LENGTH) + "\n[... truncado ...]";
        }

        return new WebContent(normalizedUrl, title, description, cleanText);
    }

    public String formatForContext(WebContent content) {
        StringBuilder sb = new StringBuilder();
        sb.append(">>> WEB SOURCE: ").append(content.url()).append("\n");
        sb.append("TITLE: ").append(content.title()).append("\n");

        if (content.description() != null && !content.description().isBlank()) {
            sb.append("DESCRIPTION: ").append(content.description()).append("\n");
        }

        sb.append("\n").append(content.cleanText());
        return sb.toString();
    }

    private String normalizeUrl(String url) {
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private String extractMetaDescription(Document doc) {
        Element metaDesc = doc.selectFirst("meta[name=description]");
        if (metaDesc != null) {
            return metaDesc.attr("content");
        }

        Element ogDesc = doc.selectFirst("meta[property=og:description]");
        if (ogDesc != null) {
            return ogDesc.attr("content");
        }

        return null;
    }

    private void removeNoiseElements(Document doc) {
        Elements noiseElements = doc.select(NOISE_SELECTORS);
        noiseElements.remove();
    }

    private void preserveBlockFormatting(Document doc) {
        Elements blockElements = doc.select(BLOCK_SELECTORS);
        for (Element el : blockElements) {
            el.prepend("\n");
        }
    }

    public record WebContent(
        String url,
        String title,
        String description,
        String cleanText
    ) {
        public int length() {
            return cleanText != null ? cleanText.length() : 0;
        }
    }
}
