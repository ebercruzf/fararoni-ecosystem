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
import dev.fararoni.core.core.agent.dynamic.DynamicSwarmAgent;

import java.util.UUID;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class TaskCommand implements ConsoleCommand {
    @Override
    public String getTrigger() {
        return "/task";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/assign", "/orden" };
    }

    @Override
    public String getDescription() {
        return "Envia una orden directa a un agente dinamico especifico";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ENTERPRISE;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || !args.contains(" ")) {
            printUsage();
            return;
        }

        String[] parts = args.split(" ", 2);
        String targetAgentId = parts[0].trim().toUpperCase();
        String payload = parts[1].trim();

        Object coreObj = ctx.getCore();
        if (coreObj == null) {
            System.out.println("[ERROR] Core no disponible.");
            return;
        }

        FararoniCore core = (FararoniCore) coreObj;
        SovereignEventBus bus = core.getSovereignBus();

        if (bus == null) {
            System.out.println("[ERROR] Bus Soberano no inicializado.");
            return;
        }

        DynamicSwarmAgent agent = core.getDynamicAgent(targetAgentId);

        if (agent == null) {
            System.out.println("[ERROR] Agente no encontrado: " + targetAgentId);
            System.out.println();
            listAvailableAgents(core);
            return;
        }

        String targetTopic = agent.getPrimaryInputTopic();
        if (targetTopic == null) {
            System.out.println("[ERROR] Agente sin topicos de entrada: " + targetAgentId);
            return;
        }

        System.out.println(String.format(
            "[TASK] Asignando a [%s] via [%s]...", targetAgentId, targetTopic
        ));

        var envelope = SovereignEnvelope.create(
            "USER_COMMANDER",
            "OPERATOR",
            UUID.randomUUID().toString(),
            payload
        )
        .withHeader("priority", "URGENT")
        .withHeader("direct_target", targetAgentId)
        .withHeader("source", "TaskCommand");

        bus.publish(targetTopic, envelope)
           .exceptionally(ex -> {
               System.out.println("[ERROR] Fallo al despachar: " + ex.getMessage());
               return null;
           });
    }

    private void listAvailableAgents(FararoniCore core) {
        var agents = core.getAllDynamicAgents();
        if (agents.isEmpty()) {
            System.out.println("No hay agentes dinamicos activos.");
            System.out.println("Usa '/wizard create-agent' para crear uno.");
        } else {
            System.out.println("Agentes disponibles:");
            agents.forEach((id, agent) -> {
                System.out.println(String.format("  - %s [%s]",
                    id, agent.getTemplate().roleName()));
            });
        }
    }

    private void printUsage() {
        System.out.println("""

            Uso: /task <agent_id> <instruccion>

            Aliases: /assign, /orden

            Envia una orden directa a un agente dinamico especifico.
            El agente debe estar activo y tener topicos configurados.

            Ejemplos:
              /task ANALISTA_LATAM Analiza el reporte Q4
              /assign REPORTER Genera resumen ejecutivo
              /orden ECO_AGENT Prueba de eco
            """);
    }
}
