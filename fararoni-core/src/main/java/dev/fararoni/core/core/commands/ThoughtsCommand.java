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
import dev.fararoni.core.FararoniCore;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ThoughtsCommand implements ConsoleCommand {
    public static final String SHOW_REASONING_KEY = "FARARONI_SHOW_REASONING";

    @Override
    public String getTrigger() {
        return "/thoughts";
    }

    @Override
    public String getDescription() {
        return "Activa/desactiva visualización de razonamiento (Qwen3/DeepSeek-R1)";
    }

    @Override
    public String getUsage() {
        return "/thoughts [on|off]";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIG;
    }

    @Override
    public String[] getAliases() {
        return new String[] { "/thinking", "/reasoning" };
    }

    @Override
    public String getExtendedHelp() {
        return """

            /thoughts - Control de visualización de pensamientos del modelo

            Uso:
              /thoughts         Muestra estado actual
              /thoughts on      Activa visualización de reasoning
              /thoughts off     Desactiva visualización de reasoning

            Modelos Soportados:
              • Qwen3 (qwen3:30b, qwen3:14b, etc.)
              • DeepSeek-R1 (deepseek-r1:32b, etc.)
              • OpenAI o1 (o1-mini, o1-preview)

            Cuando está ACTIVO:
              Los tokens de razonamiento se muestran en GRIS ITÁLICO
              antes de la respuesta final, permitiéndote ver cómo
              "piensa" el modelo.

            Cuando está INACTIVO:
              Solo se muestra la respuesta final. El razonamiento
              sigue siendo usado internamente pero no se visualiza.

            Nota:
              Si el modelo actual no soporta reasoning (como Qwen2.5-Coder),
              este comando informará que la opción no está disponible.

            """;
    }

    @Override
    public void execute(String args, ExecutionContext ctx) {
        System.out.println();

        FararoniCore core = getFararoniCore(ctx);
        boolean modelSupportsReasoning = checkModelSupport(core);

        String subcommand = args != null ? args.trim().toLowerCase() : "";

        switch (subcommand) {
            case "on" -> enableThoughts(modelSupportsReasoning);
            case "off" -> disableThoughts();
            default -> showStatus(modelSupportsReasoning, core);
        }

        System.out.println();
    }

    private void enableThoughts(boolean modelSupportsReasoning) {
        if (!modelSupportsReasoning) {
            System.out.println("INFO El modelo actual no soporta cadena de pensamiento.");
            System.out.println("     La opción /thoughts no está disponible para este motor.");
            System.out.println();
            System.out.println("TIP  Usa '/reconfig' para cambiar a un modelo con reasoning:");
            System.out.println("     - qwen3:30b");
            System.out.println("     - deepseek-r1:32b");
            return;
        }

        System.setProperty(SHOW_REASONING_KEY, "true");
        System.out.println("[OK] Visualización de razonamiento: ACTIVADA");
        System.out.println();
        System.out.println("     Los pensamientos del modelo se mostrarán en gris itálico.");
        System.out.println("     Usa '/thoughts off' para desactivar.");
    }

    private void disableThoughts() {
        System.setProperty(SHOW_REASONING_KEY, "false");
        System.out.println("[OK] Visualización de razonamiento: DESACTIVADA");
        System.out.println();
        System.out.println("     Solo se mostrará la respuesta final del modelo.");
    }

    private void showStatus(boolean modelSupportsReasoning, FararoniCore core) {
        boolean isEnabled = Boolean.getBoolean(SHOW_REASONING_KEY);

        System.out.println("ESTADO DE VISUALIZACIÓN DE PENSAMIENTOS");
        System.out.println("────────────────────────────────────────");
        System.out.println();

        String modelName = core != null ? core.getRabbitModelName() : "desconocido";
        System.out.println("  Modelo actual:    " + modelName);
        System.out.println("  Soporta Thinking: " + (modelSupportsReasoning ? "SI" : "NO"));
        System.out.println("  Visualización:    " + (isEnabled ? "ACTIVA" : "INACTIVA"));
        System.out.println();

        if (modelSupportsReasoning) {
            if (isEnabled) {
                System.out.println("  Los pensamientos del modelo se muestran en streaming.");
                System.out.println("  Usa '/thoughts off' para ocultarlos.");
            } else {
                System.out.println("  Los pensamientos están ocultos.");
                System.out.println("  Usa '/thoughts on' para verlos.");
            }
        } else {
            System.out.println("  Este modelo no genera cadena de pensamiento.");
            System.out.println("  La opción /thoughts no afecta su comportamiento.");
        }
    }

    private boolean checkModelSupport(FararoniCore core) {
        if (core == null) {
            return false;
        }

        String modelName = core.getRabbitModelName();
        if (modelName == null) {
            return false;
        }

        String modelLower = modelName.toLowerCase();
        return modelLower.contains("qwen3") ||
               modelLower.contains("deepseek-r1") ||
               modelLower.contains("r1:") ||
               modelLower.contains("o1-") ||
               modelLower.contains("o1:") ||
               modelLower.contains("-thinking");
    }

    private FararoniCore getFararoniCore(ExecutionContext ctx) {
        if (ctx == null) return null;
        Object coreObj = ctx.getCore();
        if (coreObj instanceof FararoniCore) {
            return (FararoniCore) coreObj;
        }
        return null;
    }
}
