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
package dev.fararoni.core.core.skills.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fararoni.core.core.services.WebScraperService;
import dev.fararoni.core.core.services.WebScraperService.WebContent;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.core.skills.impl.SovereignSearchSkill;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ToolExecWebHandlers {
    private static final Logger logger = Logger.getLogger(ToolExecWebHandlers.class.getName());
    private final ObjectMapper mapper;
    private final WebScraperService webScraper;
    private final SovereignSearchSkill searchSkill;

    public ToolExecWebHandlers(ObjectMapper mapper, WebScraperService webScraper, SovereignSearchSkill searchSkill) {
        this.mapper = mapper;
        this.webScraper = webScraper;
        this.searchSkill = searchSkill;
    }

    public ToolExecutionResult handleWebFetch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("url")) {
            return new ToolExecutionResult(false,
                "Error: web_fetch requiere parametro 'url'",
                Optional.empty(), Optional.empty());
        }

        String url = args.get("url").asText();

        if (url == null || url.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: url no puede estar vacio",
                Optional.empty(), Optional.empty());
        }

        logger.info("[WEB_FETCH] Descargando: " + url);

        try {
            WebContent content = webScraper.fetch(url);
            String cleanText = webScraper.formatForContext(content);

            logger.info("[WEB_FETCH] OK - " + content.title() + " (" + cleanText.length() + " chars)");

            String result = String.format(
                "=== CONTENIDO WEB DESCARGADO ===\n" +
                "URL: %s\n" +
                "Titulo: %s\n" +
                "Descripcion: %s\n" +
                "Longitud: %d caracteres\n\n" +
                "=== CONTENIDO ===\n%s",
                content.url(),
                content.title() != null ? content.title() : "(sin titulo)",
                content.description() != null ? content.description() : "(sin descripcion)",
                cleanText.length(),
                cleanText
            );

            return new ToolExecutionResult(true, result, Optional.of(cleanText), Optional.of(url));
        } catch (java.io.IOException e) {
            logger.warning("[WEB_FETCH] Error de red: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error descargando URL '" + url + "': " + e.getMessage() +
                ". Verifica que la URL sea correcta y accesible.",
                Optional.empty(), Optional.of(url));
        }
    }

    public ToolExecutionResult handleWebSearch(String jsonArgs) throws Exception {
        JsonNode args = mapper.readTree(jsonArgs);

        if (!args.has("query")) {
            return new ToolExecutionResult(false,
                "Error: web_search requiere parametro 'query'",
                Optional.empty(), Optional.empty());
        }

        String query = args.get("query").asText();

        if (query == null || query.isBlank()) {
            return new ToolExecutionResult(false,
                "Error: query no puede estar vacio",
                Optional.empty(), Optional.empty());
        }

        logger.info("[WEB_SEARCH] Buscando: " + query);

        try {
            if (!searchSkill.isAvailable()) {
                return new ToolExecutionResult(false,
                    "Error: El servicio de busqueda web no esta disponible.",
                    Optional.empty(), Optional.of(query));
            }

            String results = searchSkill.search(query);

            if (results == null || results.isBlank() || results.contains("No se encontraron")) {
                return new ToolExecutionResult(true,
                    "No se encontraron resultados para: '" + query + "'. " +
                    "Intenta con terminos mas especificos o diferentes.",
                    Optional.empty(), Optional.of(query));
            }

            String formattedResult = String.format(
                "=== RESULTADOS DE BUSQUEDA WEB ===\n" +
                "Proveedor: %s\n\n%s\n\n" +
                "NOTA: Para leer el contenido completo de una pagina, usa web_fetch con la URL.",
                searchSkill.getProviderName(),
                results
            );

            logger.info("[WEB_SEARCH] OK - Resultados obtenidos para: " + query);

            return new ToolExecutionResult(true, formattedResult, Optional.of(results), Optional.of(query));
        } catch (Exception e) {
            logger.warning("[WEB_SEARCH] Error: " + e.getMessage());
            return new ToolExecutionResult(false,
                "Error buscando '" + query + "': " + e.getMessage() +
                ". La busqueda en internet puede no estar disponible temporalmente.",
                Optional.empty(), Optional.of(query));
        }
    }
}
