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

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.bus.agent.api.command.CommandCategory;
import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.bus.agent.api.command.ExecutionContext;
import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.core.agents.QuartermasterAgent;

import java.util.UUID;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class AskCommand implements ConsoleCommand {
    public AskCommand() {
    }

    @Override
    public String getTrigger() {
        return "/ask";
    }

    @Override
    public String[] getAliases() {
        return new String[] {
            "/qm",
            "/ayuda",
            "/quartermaster"
        };
    }

    @Override
    public String getDescription() {
        return "Solicita asistencia tecnica o tactica al Quartermaster";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.BASIC;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            printUsage();
            return;
        }

        Object coreObj = ctx.getCore();
        if (coreObj == null) {
            System.out.println("[ERROR] Core no disponible.");
            System.out.println("Tip: El Quartermaster requiere el core activo.");
            return;
        }

        FararoniCore core = (FararoniCore) coreObj;
        SovereignEventBus bus = core.getSovereignBus();

        if (bus == null) {
            System.out.println("[ERROR] Bus Soberano no inicializado.");
            System.out.println("Tip: El sistema de mensajeria no esta activo.");
            return;
        }

        System.out.println("[QUARTERMASTER] Contactando servicio de asistencia...");

        var envelope = SovereignEnvelope.create(
            "USER_CONSOLE",
            "OPERATOR",
            UUID.randomUUID().toString(),
            args
        )
        .withHeader("target_agent", QuartermasterAgent.ID)
        .withHeader("intent", "USER_HELP_REQUEST")
        .withHeader("source", "AskCommand");

        bus.publish(QuartermasterAgent.REQUESTS_TOPIC, envelope)
           .exceptionally(ex -> {
               System.out.println("[ERROR] Fallo al despachar: " + ex.getMessage());
               return null;
           });
    }

    private void printUsage() {
        System.out.println("""

            Uso: /ask <tu pregunta>

            Aliases: /qm, /ayuda, /quartermaster

            El Quartermaster es tu oficial de enlace. Te ayuda a:
            - Encontrar el comando correcto para cada tarea
            - Diagnosticar errores y problemas
            - Entender como usar el sistema

            Ejemplos:
              /ask Como creo un agente nuevo?
              /qm Que comando uso para ver el estado?
              /ayuda Me dio error al conectar, que hago?
            """);
    }
}
