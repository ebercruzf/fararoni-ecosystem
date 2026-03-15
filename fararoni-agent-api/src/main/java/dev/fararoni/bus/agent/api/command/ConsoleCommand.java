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
package dev.fararoni.bus.agent.api.command;

/**
 * Interfaz para comandos de consola dinamicos en FARARONI.
 *
 * <p>Esta interfaz define el contrato para comandos que se ejecutan
 * desde el shell interactivo mediante triggers como /web, /tree, etc.
 *
 * <h2>Diferencia con CommandProvider:</h2>
 * <ul>
 *   <li>{@code CommandProvider} registra clases Picocli (estaticas)</li>
 *   <li>{@code ConsoleCommand} ejecuta directamente en el shell (dinamicas)</li>
 * </ul>
 *
 * <h2>Arquitectura Hibrida:</h2>
 * <p>InteractiveShell maneja comandos en orden:
 * <ol>
 *   <li>Switch legacy (/help, /load, /git, etc.)</li>
 *   <li>CommandRegistry busca ConsoleCommand si legacy no manejo</li>
 * </ol>
 *
 * <h2>Ejemplo de Implementacion:</h2>
 * <pre>{@code
 * public class WebCommand implements ConsoleCommand {
 *
 *     private final WebScraperService scraper;
 *
 *     public WebCommand(WebScraperService scraper) {
 *         this.scraper = scraper;
 *     }
 *
 *     @Override
 *     public String getTrigger() {
 *         return "/web";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "Descarga contenido de una URL al contexto";
 *     }
 *
 *     @Override
 *     public void execute(String args, ExecutionContext ctx) {
 *         if (args.isBlank()) {
 *             ctx.printError("Uso: /web &lt;url&gt;");
 *             return;
 *         }
 *         try {
 *             String content = scraper.fetchCleanContent(args);
 *             ctx.addToContext(">>> WEB SOURCE: " + args + "\n" + content);
 *             ctx.printSuccess("OK (" + content.length() + " chars)");
 *         } catch (Exception e) {
 *             ctx.printError("Error: " + e.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Registro del Comando:</h2>
 * <p>Los comandos se registran via {@code CommandProvider.provideConsoleCommands()}
 * y se descubren automaticamente mediante ServiceLoader.
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see CommandCategory
 * @see ExecutionContext
 */
public interface ConsoleCommand {

    /**
     * Retorna el trigger del comando (ej: "/web", "/tree").
     *
     * <p>El trigger debe:
     * <ul>
     *   <li>Empezar con "/" (slash)</li>
     *   <li>Ser lowercase</li>
     *   <li>No contener espacios</li>
     *   <li>Ser unico en el sistema</li>
     * </ul>
     *
     * @return el trigger del comando, nunca null
     */
    String getTrigger();

    /**
     * Retorna una descripcion corta del comando para /help.
     *
     * <p>Debe ser concisa (max 60 caracteres) y descriptiva.
     * Ejemplo: "Descarga contenido de una URL al contexto"
     *
     * @return descripcion del comando, nunca null
     */
    String getDescription();

    /**
     * Ejecuta el comando con los argumentos proporcionados.
     *
     * <p>Este metodo no debe lanzar excepciones al usuario.
     * Cualquier error debe manejarse internamente y reportarse
     * via {@code ctx.printError()}.
     *
     * <p>El comando tiene acceso completo al contexto de ejecucion
     * para imprimir mensajes, agregar contenido al contexto del LLM,
     * acceder a servicios de archivos, etc.
     *
     * @param args argumentos pasados despues del trigger (puede estar vacio)
     * @param ctx contexto de ejecucion con acceso a servicios
     */
    void execute(String args, ExecutionContext ctx);

    /**
     * Retorna el uso del comando para mostrar en ayuda.
     *
     * <p>Ejemplo: "/web &lt;url&gt;" o "/tree [nivel]"
     *
     * @return formato de uso del comando
     */
    default String getUsage() {
        return getTrigger();
    }

    /**
     * Retorna la categoria del comando para agrupar en /help.
     *
     * @return categoria del comando
     */
    default CommandCategory getCategory() {
        return CommandCategory.DEBUG; // Default: categoria menos visible
    }

    /**
     * Retorna aliases alternativos para el comando.
     *
     * <p>Por ejemplo, /web podria tener alias /fetch.
     *
     * @return array de aliases (puede estar vacio, nunca null)
     */
    default String[] getAliases() {
        return new String[0];
    }

    /**
     * Indica si el comando requiere Enterprise.
     *
     * <p>Si retorna true y Enterprise no esta presente,
     * el comando muestra mensaje de upgrade.
     *
     * @return true si requiere licencia Enterprise
     */
    default boolean requiresEnterprise() {
        return false;
    }

    /**
     * Verifica si el comando esta habilitado.
     *
     * <p>Util para deshabilitar comandos basado en configuracion,
     * feature flags, o estado del sistema.
     *
     * @return true si el comando esta disponible
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Retorna ayuda extendida para el comando.
     *
     * <p>Se muestra cuando el usuario escribe "/help comando"
     * o "comando --help".
     *
     * @return texto de ayuda extendida (puede contener multiples lineas)
     */
    default String getExtendedHelp() {
        return String.format("""

            %s

            Uso: %s

            %s
            """, getTrigger(), getUsage(), getDescription());
    }
}
