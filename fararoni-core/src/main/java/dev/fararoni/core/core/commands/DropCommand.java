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
import dev.fararoni.core.core.services.FileContextService.DropResult;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DropCommand implements ConsoleCommand {
    private final FileContextService fileService;

    public DropCommand(FileContextService fileService) {
        this.fileService = fileService;
    }

    public DropCommand() {
        this(null);
    }

    @Override
    public String getTrigger() {
        return "/drop";
    }

    @Override
    public String getDescription() {
        return "Remueve archivos del contexto del LLM";
    }

    @Override
    public String getUsage() {
        return "/drop <archivo|patron|all> [...]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONTEXT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/unload", "/remove" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /drop - Remueve archivos del contexto del LLM

            Uso:
              /drop <archivo>        Remueve un archivo especifico
              /drop <patron>         Remueve archivos que coincidan
              /drop all              Remueve todos los archivos

            Patrones:
              Main.java              Coincidencia exacta por nombre
              *.java                 Todos los .java cargados
              src/                   Archivos bajo src/

            Ejemplos:
              /drop Main.java
              /drop *.test.js
              /drop all

            Notas:
              - Solo afecta archivos cargados en el contexto
              - Use /list para ver archivos cargados
              - 'all' es palabra reservada para limpiar todo

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            ctx.printError("Uso: " + getUsage());
            ctx.print("  Ejemplo: /drop Main.java");
            ctx.print("  Use /drop all para remover todo");
            return;
        }

        FileContextService service = resolveService(ctx);
        if (service == null) {
            ctx.printError("Servicio de archivos no disponible");
            return;
        }

        if (service.getLoadedCount() == 0) {
            ctx.printWarning("No hay archivos cargados en el contexto");
            return;
        }

        String trimmedArgs = args.trim();

        if (trimmedArgs.equalsIgnoreCase("all")) {
            int count = service.getLoadedCount();
            DropResult result = service.dropAll();
            ctx.printSuccess("OK - " + count + " archivo(s) removido(s) del contexto");
            ctx.print("Contexto de archivos limpio");
            return;
        }

        List<String> patterns = Arrays.asList(trimmedArgs.split("\\s+"));

        DropResult result = service.dropFiles(patterns);

        if (!result.success() && result.droppedCount() == 0) {
            ctx.printError("No se removieron archivos");
            for (String error : result.errors()) {
                ctx.printError("  - " + error);
            }
            return;
        }

        if (result.droppedCount() > 0) {
            ctx.printSuccess(String.format(
                "OK - %d archivo(s) removido(s) (%,d chars restantes)",
                result.droppedCount(),
                result.remainingChars()
            ));

            int shown = 0;
            for (var file : result.droppedFiles()) {
                if (shown >= 10) {
                    ctx.print("  ... y " + (result.droppedCount() - 10) + " mas");
                    break;
                }
                ctx.print("  - " + file.getFileName());
                shown++;
            }
        }

        if (result.hasErrors()) {
            for (String error : result.errors()) {
                ctx.printWarning(error);
            }
        }

        ctx.printDebug("Archivos restantes: " + service.getLoadedCount());
    }

    private FileContextService resolveService(ExecutionContext ctx) {
        if (fileService != null) {
            return fileService;
        }
        return new FileContextService(ctx.getWorkingDirectory());
    }
}
