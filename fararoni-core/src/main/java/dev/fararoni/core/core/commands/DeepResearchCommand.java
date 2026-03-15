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
import dev.fararoni.core.core.skills.WebSearchSkill;
import dev.fararoni.core.core.skills.impl.SovereignSearchSkill;
import dev.fararoni.core.core.swarm.roles.DeepResearchAgent;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class DeepResearchCommand implements ConsoleCommand {
    private final WebSearchSkill searchSkill;

    public DeepResearchCommand(WebSearchSkill searchSkill) {
        this.searchSkill = searchSkill;
    }

    public DeepResearchCommand() {
        this(new SovereignSearchSkill());
    }

    @Override
    public String getTrigger() {
        return "/deep";
    }

    @Override
    public String getDescription() {
        return "Inicia investigacion profunda sobre un tema (genera reporte)";
    }

    @Override
    public String getUsage() {
        return "/deep <tema de investigacion>";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.INTELLIGENCE;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/research", "/investigate" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /deep - Investigacion Profunda (Deep Research)

            Uso:
              /deep <tema>              Inicia investigacion exhaustiva

            Ejemplos:
              /deep futuro de la IA en medicina
              /deep comparativa frameworks frontend 2026
              /deep impacto de blockchain en finanzas

            Proceso:
              1. Planificacion - Genera vectores de investigacion
              2. Ejecucion - Multiples busquedas en Internet
              3. Analisis - Lectura profunda de fuentes
              4. Sintesis - Genera reporte ejecutivo Markdown

            Output:
              - Archivo .md en el directorio 'reports/'
              - Incluye: Resumen, Hallazgos, Analisis, Conclusiones

            Notas:
              - Operacion ESTRATEGICA (puede tomar varios segundos)
              - Usa el modelo Turtle (32B) para analisis profundo
              - Requiere conexion a Internet

            Diferencia con /web:
              - /web: Rapido, respuesta corta, tactico
              - /deep: Lento, reporte completo, estrategico

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        if (args == null || args.isBlank()) {
            ctx.printError("Uso: " + getUsage());
            ctx.print("  Ejemplo: /deep futuro de la inteligencia artificial");
            return;
        }

        String topic = args.trim();

        ctx.print("===============================================");
        ctx.print("  PROTOCOLO DE INVESTIGACION PROFUNDA");
        ctx.print("===============================================");
        ctx.print("Tema: " + topic);
        ctx.print("");
        ctx.print("Este proceso puede tomar varios segundos...");
        ctx.print("");

        try {
            DeepResearchAgent researcher = new DeepResearchAgent(searchSkill);

            String result = researcher.researchDirect(topic);

            ctx.print("");
            ctx.printSuccess("Investigacion completada!");
            ctx.print(result);
            ctx.print("");
            ctx.print("Revisa el directorio 'reports/' para ver el reporte completo.");
        } catch (Exception e) {
            ctx.printError("Error durante la investigacion: " + e.getMessage());
            ctx.printDebug("Causa: " + (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
        }
    }
}
