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
import dev.fararoni.core.core.services.FileContextService;
import dev.fararoni.core.core.services.FileContextService.AddResult;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class AddCommand implements ConsoleCommand {
    private final FileContextService fileService;

    public AddCommand(FileContextService fileService) {
        this.fileService = fileService;
    }

    public AddCommand() {
        this(null);
    }

    @Override
    public String getTrigger() {
        return "/add";
    }

    @Override
    public String getDescription() {
        return "Agrega archivos al contexto del LLM";
    }

    @Override
    public String getUsage() {
        return "/add <archivo|directorio|glob> [...]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONTEXT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/load", "/include" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /add - Agrega archivos al contexto del LLM

            Uso:
              /add <archivo>         Agrega un archivo especifico
              /add <directorio>/     Agrega todos los archivos del directorio
              /add <patron-glob>     Agrega archivos que coincidan con el patron

            Patrones Glob:
              *.java                 Archivos .java en directorio actual
              **/*.java              Archivos .java recursivamente
              src*.ts            Archivos .ts bajo src/
              {*.js,*.ts}            Archivos .js o .ts

            Ejemplos:
              /add src/Main.java
              /add src/main/java/
              /add **/*.java
              /add src/*.java tests/*.java
              /add config.yaml package.json

            Extensiones soportadas:
              - Codigo: .java, .js, .ts, .py, .go, .rs, .cpp, .c, etc.
              - Config: .json, .xml, .yaml, .yml, .toml, .properties
              - Docs: .md, .txt, .rst

            Notas:
              - Archivos ya cargados se omiten (sin duplicados)
              - Limite de 5MB por archivo
              - Directorios de build (target/, node_modules/) se ignoran

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            ctx.printError("Uso: " + getUsage());
            ctx.print("  Ejemplo: /add src/Main.java");
            ctx.print("  Use /help add para mas informacion");
            return;
        }

        FileContextService service = resolveService(ctx);
        if (service == null) {
            ctx.printError("Servicio de archivos no disponible");
            return;
        }

        List<String> patterns = Arrays.asList(args.trim().split("\\s+"));

        ctx.print("Buscando archivos...");

        AddResult result = service.addFiles(patterns);

        if (!result.success() && result.addedCount() == 0) {
            ctx.printError("No se agregaron archivos");
            for (String error : result.errors()) {
                ctx.printError("  - " + error);
            }
            return;
        }

        if (result.addedCount() > 0) {
            ctx.printSuccess(String.format(
                "OK - %d archivo(s) agregado(s) (%,d chars en contexto)",
                result.addedCount(),
                result.totalChars()
            ));

            int shown = 0;
            for (var file : result.addedFiles()) {
                if (shown >= 10) {
                    ctx.print("  ... y " + (result.addedCount() - 10) + " mas");
                    break;
                }
                ctx.print("  + " + file.getFileName());
                shown++;
            }
        }

        if (result.skipped() > 0) {
            ctx.printWarning(result.skipped() + " archivo(s) omitido(s) (ya cargados)");
        }

        if (result.hasErrors()) {
            for (String error : result.errors()) {
                ctx.printWarning(error);
            }
        }

        String formatted = service.formatForContext();
        if (!formatted.isEmpty()) {
            ctx.addToContext(formatted);
        }

        ctx.printDebug("Total archivos en contexto: " + service.getLoadedCount());
    }

    private FileContextService resolveService(ExecutionContext ctx) {
        if (fileService != null) {
            return fileService;
        }

        return new FileContextService(ctx.getWorkingDirectory());
    }
}
