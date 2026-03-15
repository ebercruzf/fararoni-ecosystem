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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class RunCommand implements ConsoleCommand {
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_LINES = 200;

    @Override
    public String getTrigger() {
        return "/run";
    }

    @Override
    public String getDescription() {
        return "Ejecuta un comando de shell";
    }

    @Override
    public String getUsage() {
        return "/run <comando> [--timeout=N]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.DEBUG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/exec", "/shell" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /run - Ejecuta un comando de shell

            Uso:
              /run <comando>             Ejecuta el comando
              /run <comando> --timeout=N Timeout en segundos (default: 60)

            Ejemplos:
              /run ls -la
              /run mvn clean compile
              /run npm install --timeout=120
              /run python script.py

            Comandos comunes:
              /run mvn test              Ejecutar tests Maven
              /run npm run build         Build de proyecto Node
              /run cargo build           Build de proyecto Rust
              /run go test ./...         Tests de proyecto Go

            Notas:
              - El comando se ejecuta en el directorio de trabajo actual
              - El output se captura y se muestra en consola
              - Timeout por defecto: 60 segundos
              - El output se trunca si excede 200 lineas
              - Use con cuidado: ejecuta comandos reales

            Advertencia:
              Este comando ejecuta comandos reales del sistema.
              Use con responsabilidad.

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            ctx.printError("Uso: " + getUsage());
            ctx.print("  Ejemplo: /run ls -la");
            return;
        }

        Path workDir = ctx.getWorkingDirectory();
        String command = args.trim();
        int timeout = DEFAULT_TIMEOUT_SECONDS;

        if (command.contains("--timeout=")) {
            int idx = command.indexOf("--timeout=");
            String timeoutPart = command.substring(idx);
            command = command.substring(0, idx).trim();

            try {
                String timeoutValue = timeoutPart.split("=")[1].split("\\s")[0];
                timeout = Integer.parseInt(timeoutValue);
                if (timeout < 1 || timeout > 600) {
                    ctx.printWarning("Timeout debe ser entre 1 y 600 segundos, usando default");
                    timeout = DEFAULT_TIMEOUT_SECONDS;
                }
            } catch (Exception e) {
                ctx.printWarning("Timeout invalido, usando default: " + DEFAULT_TIMEOUT_SECONDS + "s");
            }
        }

        ctx.print("Ejecutando: " + command);
        ctx.print("Timeout: " + timeout + " segundos");
        ctx.print("");

        long startTime = System.currentTimeMillis();

        try {
            String[] shellCommand;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                shellCommand = new String[] { "cmd.exe", "/c", command };
            } else {
                shellCommand = new String[] { "/bin/sh", "-c", command };
            }

            ProcessBuilder pb = new ProcessBuilder(shellCommand);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            int lineCount = 0;
            boolean truncated = false;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (lineCount < MAX_OUTPUT_LINES) {
                        output.append(line).append("\n");
                        ctx.print("  " + line);
                    } else if (!truncated) {
                        truncated = true;
                    }
                    lineCount++;
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                ctx.printError("Timeout: el comando excedio " + timeout + " segundos");
                return;
            }

            int exitCode = process.exitValue();

            ctx.print("");

            if (truncated) {
                ctx.printWarning("Output truncado (" + (lineCount - MAX_OUTPUT_LINES) + " lineas omitidas)");
            }

            if (exitCode == 0) {
                ctx.printSuccess(String.format("OK - Completado en %.2fs (exit code: 0)", elapsed / 1000.0));
            } else {
                ctx.printError(String.format("FALLO - Exit code: %d (en %.2fs)", exitCode, elapsed / 1000.0));
            }

            ctx.printDebug("Lineas output: " + lineCount);
        } catch (Exception e) {
            ctx.printError("Error ejecutando comando: " + e.getMessage());
            ctx.printDebug("Causa: " + e.getClass().getSimpleName());
        }
    }
}
