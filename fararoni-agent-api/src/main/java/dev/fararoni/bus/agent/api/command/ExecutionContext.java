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

import dev.fararoni.bus.agent.api.interaction.AgentUserInterface;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Contexto de ejecucion para comandos de consola.
 *
 * <p>Proporciona acceso a los servicios y estado necesarios para que
 * los comandos puedan ejecutarse sin acoplamiento directo con InteractiveShell.
 *
 * <h2>Principios de diseno:</h2>
 * <ul>
 *   <li>Inmutabilidad: El contexto no debe modificarse despues de crearse</li>
 *   <li>Minimalismo: Solo expone lo necesario para los comandos</li>
 *   <li>Desacoplamiento: Los comandos no conocen la implementacion del shell</li>
 * </ul>
 *
 * <h2>Ejemplo de uso:</h2>
 * <pre>{@code
 * public class WebCommand implements ConsoleCommand {
 *     public void execute(String args, ExecutionContext ctx) {
 *         ctx.print("Descargando: " + args);
 *         String content = webScraper.fetch(args);
 *         ctx.addToContext("WEB: " + content);
 *         ctx.printSuccess("OK (" + content.length() + " chars)");
 *     }
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ExecutionContext {

    // ================== OUTPUT METHODS ==================

    /**
     * Imprime un mensaje normal al usuario.
     * @param message mensaje a imprimir
     */
    void print(String message);

    /**
     * Imprime un mensaje de exito (verde).
     * @param message mensaje de exito
     */
    void printSuccess(String message);

    /**
     * Imprime un mensaje de advertencia (amarillo).
     * @param message mensaje de advertencia
     */
    void printWarning(String message);

    /**
     * Imprime un mensaje de error (rojo).
     * @param message mensaje de error
     */
    void printError(String message);

    /**
     * Imprime mensaje solo si modo debug esta activo.
     * @param message mensaje de debug
     */
    void printDebug(String message);

    // ================== CONTEXT METHODS ==================

    /**
     * Agrega contenido al contexto del LLM (memoria).
     *
     * <p>El contenido agregado sera visible para el LLM en futuras
     * interacciones, permitiendo que "recuerde" informacion.
     *
     * @param content contenido a agregar al contexto
     */
    void addToContext(String content);

    /**
     * Agrega contenido al contexto del sistema (system message).
     *
     * <p>A diferencia de addToContext, este se agrega como mensaje
     * de sistema, util para instrucciones o contexto estructural.
     *
     * @param content contenido para system message
     */
    void addToSystemContext(String content);

    /**
     * Obtiene el directorio de trabajo actual.
     * @return path del directorio de trabajo
     */
    Path getWorkingDirectory();

    /**
     * Obtiene el directorio raiz del proyecto (si es detectable).
     * @return Optional con el path del proyecto, vacio si no es proyecto
     */
    Optional<Path> getProjectRoot();

    // ================== STATE METHODS ==================

    /**
     * Verifica si el modo debug esta activo.
     * @return true si debug mode esta habilitado
     */
    boolean isDebugMode();

    /**
     * Verifica si estamos en un repositorio Git.
     * @return true si es repo git
     */
    boolean isGitRepository();

    /**
     * Obtiene la rama Git actual.
     * @return Optional con nombre de la rama, vacio si no es repo git
     */
    Optional<String> getCurrentBranch();

    // ================== SERVICE ACCESS ==================

    /**
     * Ejecuta una operacion con el servicio de archivos.
     *
     * <p>Proporciona acceso indirecto al FileManager para operaciones
     * como leer, escribir, o listar archivos.
     *
     * @param <T> tipo de retorno
     * @param operation operacion a ejecutar
     * @return resultado de la operacion
     */
    <T> T withFileService(java.util.function.Function<FileServiceAccessor, T> operation);

    /**
     * Interfaz para operaciones de archivo.
     */
    interface FileServiceAccessor {
        /**
         * Lee contenido de un archivo.
         * @param path ruta del archivo
         * @return contenido del archivo
         */
        String readFile(Path path);

        /**
         * Escribe contenido a un archivo.
         * @param path ruta del archivo
         * @param content contenido a escribir
         */
        void writeFile(Path path, String content);

        /**
         * Verifica si un archivo existe.
         * @param path ruta del archivo
         * @return true si existe
         */
        boolean exists(Path path);
    }

    // ================== AUDIT ==================

    /**
     * Registra una accion en el log de auditoria.
     * @param action descripcion de la accion
     * @param details detalles adicionales (puede ser null)
     */
    void logAudit(String action, String details);

    // ================== USER INTERFACE ==================

    /**
     * Obtiene la interfaz de usuario para interaccion rica.
     *
     * <p>Esta interfaz permite al codigo Enterprise comunicarse con el usuario
     * de forma desacoplada, sin conocer la implementacion especifica de UI
     * (JLine, REST, etc.).</p>
     *
     * <p><strong>Patron Render Server:</strong></p>
     * <p>El modulo Enterprise usa esta interfaz para:</p>
     * <ul>
     *   <li>Solicitar input del usuario (prompts, confirmaciones, menus)</li>
     *   <li>Mostrar output rico (dashboards, status bars, progreso)</li>
     *   <li>Controlar la pantalla (limpiar, obtener dimensiones)</li>
     * </ul>
     *
     * <p><strong>Ejemplo de Uso:</strong></p>
     * <pre>{@code
     * // En un Skill de Enterprise
     * public FNLResult execute(ExecutionContext ctx) {
     *     // Pedir confirmacion sin saber de JLine
     *     if (ctx.getUI().confirm("Deseas continuar?")) {
     *         ctx.getUI().setLoading(true, "Procesando...");
     *         // ... trabajo ...
     *         ctx.getUI().success("Completado!");
     *     }
     * }
     * }</pre>
     *
     * <p><strong>Implementacion Default:</strong></p>
     * <p>Si no hay UI disponible, retorna {@link AgentUserInterface#NO_OP}
     * que no hace nada pero evita NullPointerExceptions.</p>
     *
     * @return interfaz de usuario, nunca null
     * @see AgentUserInterface
     * @since 1.0.0
     */
    default AgentUserInterface getUI() {
        return AgentUserInterface.NO_OP;
    }

    // ================== INTERACTIVE INPUT ==================

    /**
     * Verifica si el contexto soporta entrada interactiva del usuario.
     *
     * <p>Retorna true si el shell puede solicitar input del usuario
     * via {@link #readLine()}. Puede ser false si se ejecuta en modo
     * batch, CI/CD, o sin terminal.</p>
     *
     * @return true si soporta input interactivo
     * @since 1.0.0
     */
    default boolean supportsInteractiveInput() {
        return false; // Default: no soporta (seguro para entornos no interactivos)
    }

    /**
     * Lee una linea de entrada del usuario.
     *
     * <p>Solo usar si {@link #supportsInteractiveInput()} retorna true.
     * Puede bloquear hasta que el usuario ingrese texto y presione Enter.</p>
     *
     * @return linea ingresada por el usuario, o null si no hay input disponible
     * @since 1.0.0
     */
    default String readLine() {
        return null; // Default: no input disponible
    }

    // ================== CORE SERVICE ACCESS ==================

    /**
     * [FASE 14.23] Obtiene el nucleo de Fararoni para operaciones avanzadas.
     *
     * <p>Usado por comandos que necesitan acceso directo al motor LLM,
     * como hot-swap de modelos, diagnostico profundo, etc.</p>
     *
     * <p><strong>Nota de Arquitectura:</strong></p>
     * <p>Este metodo retorna Object para evitar dependencia circular entre
     * agent-api y fararoni-core. El comando debe hacer cast al tipo correcto.</p>
     *
     * <p><strong>Ejemplo:</strong></p>
     * <pre>{@code
     * FararoniCore core = (FararoniCore) ctx.getCore();
     * if (core != null) {
     *     core.hotSwapRabbitClient(url, model);
     * }
     * }</pre>
     *
     * @return instancia de FararoniCore como Object, o null si no disponible
     * @since 1.0.0
     */
    default Object getCore() {
        return null; // Default: no disponible (ej. en tests)
    }
}
