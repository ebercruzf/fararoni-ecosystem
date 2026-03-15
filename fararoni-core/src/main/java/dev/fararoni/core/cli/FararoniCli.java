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
package dev.fararoni.core.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@Deprecated(since = "0.11.34-RC7.15", forRemoval = false)
public class FararoniCli {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String EXIT_COMMAND = "/exit";
    private static final String QUIT_COMMAND = "/quit";
    private static final String HELP_COMMAND = "/help";

    public static void startRemoteMode(String serverUrl) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        if (!checkServerHealth(client, serverUrl)) {
            System.err.println("[ERROR] No se pudo conectar al servidor en " + serverUrl);
            System.err.println("   Verifica que el servidor esté corriendo.");
            return;
        }

        printRemoteBanner(serverUrl);

        try {
            Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .dumb(false)
                .build();

            Path historyFile = Paths.get(System.getProperty("user.home"), ".fararoni", "remote-history");
            historyFile.getParent().toFile().mkdirs();

            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(new DefaultHistory())
                .variable(LineReader.HISTORY_FILE, historyFile)
                .option(LineReader.Option.HISTORY_BEEP, false)
                .option(LineReader.Option.AUTO_FRESH_LINE, true)
                .build();

            while (true) {
                String input;
                try {
                    input = reader.readLine("\u001B[32m>>> \u001B[0m").trim();
                } catch (UserInterruptException e) {
                    System.out.println("\n(Ctrl+C detectado. Usa /exit para salir)");
                    continue;
                } catch (EndOfFileException e) {
                    System.out.println("\n👋 ¡Hasta pronto!");
                    break;
                }

                if (input.isEmpty()) {
                    continue;
                }

                if (input.equalsIgnoreCase(EXIT_COMMAND) || input.equalsIgnoreCase(QUIT_COMMAND)) {
                    System.out.println("👋 ¡Hasta pronto!");
                    break;
                }

                if (input.equalsIgnoreCase(HELP_COMMAND)) {
                    printHelp();
                    continue;
                }

                try {
                    System.out.println("\u001B[2mProcesando...\u001B[0m");
                    String response = sendToServer(client, serverUrl, input);
                    System.out.print("\u001B[A\u001B[2K");
                    System.out.println();
                    System.out.println(response);
                    System.out.println();
                } catch (Exception e) {
                    System.err.println("[ERROR] Error de comunicacion: " + e.getMessage());
                    System.err.println("   ¿El servidor sigue corriendo?");
                }
            }

            terminal.close();
        } catch (Exception e) {
            System.err.println("[ERROR] Error inicializando terminal: " + e.getMessage());
            System.err.println("   Intentando modo básico...");
            startRemoteModeBasic(serverUrl, client);
        }
    }

    private static void startRemoteModeBasic(String serverUrl, HttpClient client) {
        java.util.Scanner scanner = new java.util.Scanner(System.in);

        while (true) {
            System.out.print(">>> ");
            String input;

            try {
                input = scanner.nextLine().trim();
            } catch (Exception e) {
                break;
            }

            if (input.isEmpty()) continue;

            if (input.equalsIgnoreCase(EXIT_COMMAND) || input.equalsIgnoreCase(QUIT_COMMAND)) {
                System.out.println("👋 ¡Hasta pronto!");
                break;
            }

            if (input.equalsIgnoreCase(HELP_COMMAND)) {
                printHelp();
                continue;
            }

            try {
                String response = sendToServer(client, serverUrl, input);
                System.out.println("\n" + response + "\n");
            } catch (Exception e) {
                System.err.println("[ERROR] " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static boolean checkServerHealth(HttpClient client, String serverUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sendToServer(HttpClient client, String serverUrl, String message) throws Exception {
        String jsonBody = mapper.writeValueAsString(
            mapper.createObjectNode()
                .put("message", message)
                .set("context", mapper.createObjectNode()
                    .put("prompt", message)
                    .put("mode", "remote-cli"))
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(serverUrl + "/api/chat"))
            .header("Content-Type", "application/json")
            .header("X-Client-Type", "FararoniCli")
            .header("X-Client-Version", "1.0.0")
            .timeout(Duration.ofMinutes(5))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode json = mapper.readTree(response.body());
            if (json.has("response")) {
                return json.get("response").asText();
            }
            return response.body();
        } else if (response.statusCode() == 429) {
            return "[WARN] Demasiadas solicitudes. Espera un momento.";
        } else if (response.statusCode() >= 500) {
            return "[ERROR] Error del servidor: " + response.statusCode();
        } else {
            return "[WARN] Respuesta inesperada: " + response.statusCode() + "\n" + response.body();
        }
    }

    private static void printRemoteBanner(String serverUrl) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           FARARONI CLI - Modo Remoto (Cliente HTTP)           ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║  Conectado a: " + padRight(serverUrl, 47) + " ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Comandos locales:                                            ║");
        System.out.println("║    /exit, /quit  - Salir del CLI                              ║");
        System.out.println("║    /help         - Mostrar ayuda                              ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Este CLI NO usa recursos locales (DB, LLM)                   ║");
        System.out.println("║     Todo se procesa en el servidor.                           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    FARARONI CLI - AYUDA                       ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║  MODO REMOTO                                                  ║");
        System.out.println("║  Este CLI está conectado a un servidor Fararoni.              ║");
        System.out.println("║  Todos los comandos se envían al servidor para procesar.      ║");
        System.out.println("║                                                               ║");
        System.out.println("║  COMANDOS LOCALES (no van al servidor):                       ║");
        System.out.println("║    /exit, /quit  - Cerrar este CLI                            ║");
        System.out.println("║    /help         - Mostrar esta ayuda                         ║");
        System.out.println("║                                                               ║");
        System.out.println("║  COMANDOS DEL SERVIDOR (se envían al server):                 ║");
        System.out.println("║    /status       - Estado del servidor                        ║");
        System.out.println("║    /agents       - Lista de agentes activos                   ║");
        System.out.println("║    /task <msg>   - Ejecutar tarea con agentes                 ║");
        System.out.println("║    <cualquier>   - Chat con el LLM                            ║");
        System.out.println("║                                                               ║");
        System.out.println("║  Para iniciar en modo embebido (sin servidor):                ║");
        System.out.println("║    1. Detén el servidor actual                                ║");
        System.out.println("║    2. Ejecuta: java -jar fararoni.jar                         ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) {
            return s.substring(0, n);
        }
        return s + " ".repeat(n - s.length());
    }
}
