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

import dev.fararoni.core.core.command.CommandRegistry;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class CommandCompleter implements Completer {
    private static final List<String> LEGACY_COMMANDS = List.of(
        "/help", "/h",
        "/exit", "/quit", "/q",
        "/clear", "/cls",
        "/load",
        "/unload",
        "/list", "/ls",
        "/tokens",
        "/debug",
        "/history",
        "/status",
        "/config",
        "/router",
        "/context",
        "/git"
    );

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word();
        String buffer = line.line();

        if (line.wordIndex() == 0 || buffer.trim().startsWith("/")) {
            for (String cmd : LEGACY_COMMANDS) {
                if (matchesPrefix(cmd, word)) {
                    candidates.add(createCandidate(cmd, getDescription(cmd)));
                }
            }

            try {
                CommandRegistry.getInstance().getAllCommands().forEach(command -> {
                    String trigger = command.getTrigger();
                    if (matchesPrefix(trigger, word)) {
                        candidates.add(createCandidate(trigger, command.getDescription()));
                    }

                    for (String alias : command.getAliases()) {
                        if (matchesPrefix(alias, word)) {
                            candidates.add(createCandidate(alias, command.getDescription()));
                        }
                    }
                });
            } catch (Exception e) {
            }
        }
    }

    private boolean matchesPrefix(String command, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        return command.toLowerCase().startsWith(prefix.toLowerCase());
    }

    private Candidate createCandidate(String value, String description) {
        return new Candidate(
            value,
            value,
            null,
            description,
            null,
            null,
            true
        );
    }

    private String getDescription(String command) {
        return switch (command) {
            case "/help", "/h" -> "Muestra ayuda";
            case "/exit", "/quit", "/q" -> "Sale del shell";
            case "/clear", "/cls" -> "Limpia historial";
            case "/load" -> "Carga archivos al contexto";
            case "/unload" -> "Descarga contexto";
            case "/list", "/ls" -> "Lista archivos cargados";
            case "/tokens" -> "Toggle conteo de tokens";
            case "/debug" -> "Toggle modo debug";
            case "/history" -> "Muestra historial de conversacion";
            case "/status" -> "Muestra estado del sistema";
            case "/config" -> "Gestion de configuracion";
            case "/router" -> "Control del router semantico";
            case "/context" -> "Ver contexto del proyecto";
            case "/git" -> "Operaciones Git";
            default -> "";
        };
    }
}
