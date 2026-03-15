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
import dev.fararoni.core.core.services.StructureScannerService;
import dev.fararoni.core.core.services.StructureScannerService.DirectoryScanResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TreeCommand implements ConsoleCommand {
    private final StructureScannerService scannerService;

    public TreeCommand(StructureScannerService scannerService) {
        this.scannerService = scannerService;
    }

    public TreeCommand() {
        this(new StructureScannerService());
    }

    @Override
    public String getTrigger() {
        return "/tree";
    }

    @Override
    public String getDescription() {
        return "Genera un mapa esqueletico (skeleton) del proyecto al contexto del LLM";
    }

    @Override
    public String getUsage() {
        return "/tree [directorio]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONTEXT;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/skeleton", "/map-lite" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /tree - Genera Skeleton Map del proyecto

            Uso:
              /tree                  Escanea directorio de trabajo actual
              /tree src/             Escanea directorio especifico
              /tree src/main/java    Escanea subdirectorio

            Lenguajes soportados:
              - Java (.java)
              - Python (.py)
              - JavaScript/TypeScript (.js, .jsx, .ts, .tsx)
              - Go (.go)
              - Rust (.rs)
              - Kotlin (.kt, .kts)
              - C/C++ (.c, .h, .cpp, .hpp)
              - C# (.cs)
              - Ruby (.rb)
              - PHP (.php)

            Ejemplos:
              /tree
              /tree src/main/java/com/myapp
              /tree frontend/src/components

            Notas:
              - Profundidad maxima: 5 niveles
              - Limite: ~50,000 tokens (trunca si excede)
              - Ignora: target/, node_modules/, .git/, build/
              - Solo extrae firmas (class, function, method)
              - Util para dar contexto estructural al LLM

            Core vs Enterprise:
              - Core (/tree): Regex scanner (rapido, polyglot)
              - Enterprise (/map): AST real (preciso, grafo de llamadas)

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        Path targetDir = resolveTargetDirectory(args, ctx);

        if (targetDir == null) {
            return;
        }

        if (!Files.exists(targetDir)) {
            ctx.printError("Directorio no existe: " + targetDir);
            return;
        }

        if (!Files.isDirectory(targetDir)) {
            ctx.printError("No es un directorio: " + targetDir);
            return;
        }

        ctx.print("Escaneando " + targetDir + " ...");

        try {
            DirectoryScanResult result = scannerService.scanDirectory(targetDir);

            if (result.fileCount() == 0) {
                ctx.printWarning("No se encontraron archivos de codigo en: " + targetDir);
                ctx.print("Extensiones soportadas: " + String.join(", ", scannerService.getSupportedExtensions()));
                return;
            }

            String formattedContent = scannerService.formatForContext(result);

            ctx.addToSystemContext(formattedContent);

            String statusIcon = result.truncated() ? "[TRUNCATED] " : "";
            ctx.printSuccess(String.format(
                "%sOK - %d archivos, %d firmas (%,d chars agregados al contexto)",
                statusIcon,
                result.fileCount(),
                result.totalSignatures(),
                result.totalChars()
            ));

            ctx.printDebug("Root: " + result.rootPath());
            ctx.printDebug("Truncated: " + result.truncated());
        } catch (IOException e) {
            ctx.printError("Error escaneando directorio: " + e.getMessage());
            ctx.printDebug("Causa: " + (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
        } catch (Exception e) {
            ctx.printError("Error inesperado: " + e.getMessage());
            ctx.printDebug("Tipo: " + e.getClass().getSimpleName());
        }
    }

    private Path resolveTargetDirectory(String args, ExecutionContext ctx) {
        Path workingDir = ctx.getWorkingDirectory();

        if (args == null || args.isBlank()) {
            return workingDir;
        }

        String dirArg = args.trim();

        if (dirArg.startsWith("/") || dirArg.matches("^[A-Za-z]:.*")) {
            return Path.of(dirArg);
        }

        return workingDir.resolve(dirArg).normalize();
    }
}
