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
package dev.fararoni.core.core.commands;

import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.core.core.services.WebScraperService;
import dev.fararoni.core.core.services.WebScraperService.WebContent;

import java.io.IOException;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class WebCommand implements ConsoleCommand {
    private final WebScraperService scraperService;

    public WebCommand(WebScraperService scraperService) {
        this.scraperService = scraperService;
    }

    public WebCommand() {
        this(new WebScraperService());
    }

    @Override
    public String getTrigger() {
        return "/web";
    }

    @Override
    public String getDescription() {
        return "Descarga contenido de una URL al contexto del LLM";
    }

    @Override
    public String getUsage() {
        return "/web <url>";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONTEXT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/fetch", "/url" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /web - Descarga contenido web al contexto

            Uso:
              /web <url>              Descarga y agrega al contexto
              /web example.com        Auto-agrega https:
              /web https:

            Ejemplos:
              /web github.com/user/repo
              /web docs.oracle.com/javase/tutorial

            Notas:
              - El contenido se limpia automaticamente (sin scripts, ads, nav)
              - Maximo 100,000 caracteres (trunca si excede)
              - Timeout de 10 segundos
              - Solo HTML estatico (JS no se ejecuta)

            Limitaciones Core vs Enterprise:
              - Core: HTML estatico con Jsoup
              - Enterprise: Renderiza JavaScript con Playwright

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            ctx.printError("Uso: " + getUsage());
            ctx.print("  Ejemplo: /web example.com");
            return;
        }

        String url = args.trim();

        ctx.print("Conectando a " + url + " ...");

        try {
            WebContent content = scraperService.fetch(url);

            String formattedContent = scraperService.formatForContext(content);

            ctx.addToContext(formattedContent);

            ctx.printSuccess(String.format(
                "OK - \"%s\" (%,d chars agregados al contexto)",
                truncateTitle(content.title(), 40),
                content.length()
            ));

            ctx.printDebug("URL: " + content.url());
            if (content.description() != null) {
                ctx.printDebug("Desc: " + truncateTitle(content.description(), 60));
            }
        } catch (IOException e) {
            ctx.printError("Error descargando " + url + ": " + e.getMessage());
            ctx.printDebug("Causa: " + (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
        } catch (Exception e) {
            ctx.printError("Error inesperado: " + e.getMessage());
            ctx.printDebug("Tipo: " + e.getClass().getSimpleName());
        }
    }

    private String truncateTitle(String title, int maxLength) {
        if (title == null || title.length() <= maxLength) {
            return title != null ? title : "(sin titulo)";
        }
        return title.substring(0, maxLength - 3) + "...";
    }
}
