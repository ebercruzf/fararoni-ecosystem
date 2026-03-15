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
package dev.fararoni.bus.agent.api.interaction;

import dev.fararoni.bus.agent.api.ui.model.*;
import dev.fararoni.bus.agent.api.ui.model.TaskTreeModel;

/**
 * Interfaz para interaccion con el usuario desde el modulo Enterprise.
 *
 * <h2>Proposito Arquitectonico</h2>
 * <p>Esta interfaz es el <b>contrato</b> que permite al modulo Enterprise
 * comunicarse con el usuario sin conocer la implementacion especifica de UI.
 * El Core implementa esta interfaz usando JLine (u otra tecnologia).</p>
 *
 * <h2>Patron Render Server</h2>
 * <ul>
 *   <li><b>Enterprise (Backend):</b> Construye modelos de datos, llama estos metodos</li>
 *   <li><b>Core (Frontend):</b> Implementa esta interfaz, renderiza en terminal</li>
 * </ul>
 *
 * <h2>Regla de Oro</h2>
 * <blockquote>
 * <b>Ninguna clase que implemente esta interfaz debe estar en Enterprise.</b>
 * <br>
 * La implementacion (JLineAgentUI) vive exclusivamente en Core.
 * </blockquote>
 *
 * <h2>Categorias de Metodos</h2>
 * <table border="1">
 *   <caption>Method categories in AgentUserInterface</caption>
 *   <tr><th>Categoria</th><th>Metodos</th><th>Descripcion</th></tr>
 *   <tr><td>Input Simple</td><td>prompt, promptSecret, confirm</td><td>Obtener texto del usuario</td></tr>
 *   <tr><td>Input Complejo</td><td>selectFromMenu</td><td>Seleccion de opciones</td></tr>
 *   <tr><td>Output Simple</td><td>info, success, warn, error</td><td>Mensajes de una linea</td></tr>
 *   <tr><td>Output Rico</td><td>renderDashboard, updateStatusBar</td><td>UI compleja</td></tr>
 *   <tr><td>Live Progress</td><td>updateProgress, onProcessComplete</td><td>Arbol de progreso animado</td></tr>
 *   <tr><td>Control</td><td>setLoading, clearScreen, getTerminal*</td><td>Control de pantalla</td></tr>
 * </table>
 *
 * <h2>Ejemplo de Uso desde Enterprise</h2>
 * <pre>{@code
 * public class DatabaseSkill implements Skill {
 *
 *     @ToolAction(name = "db_connect")
 *     public FNLResult connect(ExecutionContext ctx, String connStr) {
 *         // INPUT: Pedir password (Enterprise no sabe de JLine)
 *         String password = ctx.getUI().promptSecret("Password de Oracle");
 *
 *         // OUTPUT: Mostrar progreso
 *         ctx.getUI().setLoading(true, "Conectando...");
 *
 *         try {
 *             // Conectar a BD...
 *             ctx.getUI().success("Conectado exitosamente");
 *             return FNLResult.success("Conectado");
 *         } catch (Exception e) {
 *             ctx.getUI().error("Fallo: " + e.getMessage());
 *             return FNLResult.failure(e.getMessage());
 *         } finally {
 *             ctx.getUI().setLoading(false, null);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Implementaciones</h2>
 * <ul>
 *   <li><b>JLineAgentUI (Core):</b> Implementacion principal usando JLine 3</li>
 *   <li><b>NoOpAgentUI (API):</b> Implementacion nula para contextos sin UI</li>
 *   <li><b>TestAgentUI (Test):</b> Mock para tests unitarios</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Las implementaciones deben ser thread-safe para uso concurrente,
 * ya que multiples skills pueden intentar escribir a la UI simultaneamente.</p>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see DashboardModel
 * @see MenuModel
 * @see StatusBarModel
 * @see TaskTreeModel
 */
public interface AgentUserInterface {

    // ════════════════════════════════════════════════════════════════════════
    // INPUT SIMPLE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Solicita texto al usuario.
     *
     * <p>Muestra un mensaje y espera que el usuario ingrese texto.
     * El texto ingresado es visible mientras se escribe.</p>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * Ingresa tu nombre: [cursor parpadeando]
     * </pre>
     *
     * @param message mensaje a mostrar antes del prompt
     * @return texto ingresado por el usuario (puede estar vacio)
     * @throws UserCancelledException si el usuario cancela (Ctrl+C)
     */
    String prompt(String message);

    /**
     * Solicita texto secreto al usuario (password, API key, etc.).
     *
     * <p>Igual que {@link #prompt(String)} pero el texto no se muestra
     * mientras se escribe. Tipicamente se muestran asteriscos.</p>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * Password de Oracle: ********
     * </pre>
     *
     * @param message mensaje a mostrar antes del prompt
     * @return texto secreto ingresado (puede estar vacio)
     * @throws UserCancelledException si el usuario cancela (Ctrl+C)
     */
    String promptSecret(String message);

    /**
     * Solicita confirmacion Yes/No al usuario.
     *
     * <p>Muestra una pregunta y espera respuesta afirmativa o negativa.
     * Acepta variaciones como "y", "yes", "si", "s", "n", "no".</p>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * Deseas continuar? [y/N]: _
     * </pre>
     *
     * @param message pregunta a mostrar
     * @return true si el usuario confirma, false si niega
     * @throws UserCancelledException si el usuario cancela (Ctrl+C)
     */
    boolean confirm(String message);

    // ════════════════════════════════════════════════════════════════════════
    // INPUT COMPLEJO
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Muestra un menu y espera que el usuario seleccione una opcion.
     *
     * <p>Renderiza un menu interactivo donde el usuario puede navegar
     * con flechas y seleccionar con Enter, o usar atajos de teclado.</p>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * ┌─ Selecciona una accion: ────────────┐
     * │  [1] Guardar y continuar    &lt;--     │
     * │  [2] Descartar cambios              │
     * │  [c] Cancelar                       │
     * └─────────────────────────────────────┘
     * </pre>
     *
     * @param menu modelo del menu con opciones
     * @return ID del item seleccionado (de {@link MenuItem#id()})
     * @throws UserCancelledException si el usuario cancela (Esc o Ctrl+C)
     * @see MenuModel
     */
    String selectFromMenu(MenuModel menu);

    // ════════════════════════════════════════════════════════════════════════
    // OUTPUT SIMPLE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Muestra un mensaje informativo.
     *
     * <p>Tipicamente renderizado en color cyan/azul.</p>
     *
     * @param message mensaje a mostrar
     */
    void info(String message);

    /**
     * Muestra un mensaje de exito.
     *
     * <p>Tipicamente renderizado en color verde con checkmark.</p>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * [OK] Archivo guardado exitosamente
     * </pre>
     *
     * @param message mensaje de exito
     */
    void success(String message);

    /**
     * Muestra un mensaje de advertencia.
     *
     * <p>Tipicamente renderizado en color amarillo.</p>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * [WARN] El archivo es muy grande (>1MB)
     * </pre>
     *
     * @param message mensaje de advertencia
     */
    void warn(String message);

    /**
     * Muestra un mensaje de error.
     *
     * <p>Tipicamente renderizado en color rojo.</p>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * [ERROR] No se pudo conectar a la base de datos
     * </pre>
     *
     * @param message mensaje de error
     */
    void error(String message);

    /**
     * Muestra contenido formateado en Markdown.
     *
     * <p>El contenido Markdown se renderiza con formato apropiado:
     * headers, listas, codigo, negritas, etc.</p>
     *
     * @param markdown contenido en formato Markdown
     */
    void printMarkdown(String markdown);

    // ════════════════════════════════════════════════════════════════════════
    // OUTPUT RICO (Dashboards, Status Bars)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Renderiza el dashboard completo del agente.
     *
     * <p>Muestra una vista completa con estado, tokens, skills, logs, etc.
     * Tipicamente ocupa toda la pantalla o una porcion significativa.</p>
     *
     * @param model modelo con todos los datos del dashboard
     * @see DashboardModel
     */
    void renderDashboard(DashboardModel model);

    /**
     * Actualiza la barra de estado global.
     *
     * <p>La barra de estado es una linea persistente (tipicamente en la
     * parte inferior) que muestra informacion contextual.</p>
     *
     * @param model modelo con datos de la barra de estado
     * @see StatusBarModel
     */
    void updateStatusBar(StatusBarModel model);

    /**
     * Muestra u oculta un indicador de carga (spinner).
     *
     * <p>Util para operaciones que toman tiempo. El spinner es animado
     * y se actualiza automaticamente.</p>
     *
     * @param loading true para mostrar spinner, false para ocultarlo
     * @param message mensaje opcional junto al spinner (puede ser null)
     */
    void setLoading(boolean loading, String message);

    // ════════════════════════════════════════════════════════════════════════
    // LIVE PROGRESS TREE
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Actualiza el arbol de progreso en vivo (Live Execution Tree).
     *
     * <p>Este metodo es llamado por Enterprise cada vez que cambia el estado
     * de las tareas. Core lo renderiza usando JLine Display para evitar parpadeo.</p>
     *
     * <p><strong>Caracteristicas del Renderizado:</strong></p>
     * <ul>
     *   <li>7 estados visuales con iconos y colores distintivos</li>
     *   <li>Guias de arbol (│, ├─, └─) para jerarquia clara</li>
     *   <li>Spinner animado para tareas en ejecucion</li>
     *   <li>Truncado inteligente para textos largos</li>
     * </ul>
     *
     * <p><strong>Ejemplo de Renderizado:</strong></p>
     * <pre>
     * ⠋ Actualizando checklist... (2m 24s · 5.4k tokens)
     * ├─ ✔ Leer archivos
     * ├─ ⠋ Procesando...
     * └─ ◦ Pendiente
     * </pre>
     *
     * <p><strong>Ejemplo de Uso:</strong></p>
     * <pre>{@code
     * var nodes = List.of(
     *     TaskNode.success("1", "Leer archivos"),
     *     TaskNode.running("2", "Procesando"),
     *     TaskNode.pending("3", "Pendiente")
     * );
     * var model = TaskTreeModel.running("Analizando...", "10s", nodes);
     * context.getUI().updateProgress(model);
     * }</pre>
     *
     * @param model arbol de tareas con estados actuales (no null)
     * @see TaskTreeModel
     * @see TaskNode
     * @see TaskState
     */
    void updateProgress(TaskTreeModel model);

    /**
     * Notifica que el proceso termino y debe compactarse la UI.
     *
     * <p>Core limpia el arbol animado y muestra la barra de compactacion:</p>
     * <pre>
     * ────────────── Conversation compacted ──────────────
     * </pre>
     *
     * <p>Este metodo debe llamarse al finalizar una operacion larga para
     * liberar espacio visual y permitir al usuario continuar.</p>
     *
     * <p><strong>Ejemplo de Uso:</strong></p>
     * <pre>{@code
     * // Al terminar la operacion
     * context.getUI().onProcessComplete("Analisis completado: 5 archivos procesados");
     * }</pre>
     *
     * @param summary resumen final del proceso (puede ser null para solo compactar)
     */
    void onProcessComplete(String summary);

    // ════════════════════════════════════════════════════════════════════════
    // CONTROL DE PANTALLA
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Limpia la pantalla de la terminal.
     *
     * <p>Borra todo el contenido visible y posiciona el cursor arriba.</p>
     */
    void clearScreen();

    /**
     * Obtiene el ancho actual de la terminal en columnas.
     *
     * @return numero de columnas (tipicamente 80-200)
     */
    int getTerminalWidth();

    /**
     * Obtiene el alto actual de la terminal en filas.
     *
     * @return numero de filas (tipicamente 24-50)
     */
    int getTerminalHeight();

    // ════════════════════════════════════════════════════════════════════════
    // EXCEPCION PARA CANCELACION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Excepcion lanzada cuando el usuario cancela una operacion de input.
     *
     * <p>Se lanza cuando el usuario presiona Ctrl+C, Esc, o cierra la terminal
     * durante una operacion de input como prompt o selectFromMenu.</p>
     */
    class UserCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Crea una nueva excepcion de cancelacion.
         */
        public UserCancelledException() {
            super("Operacion cancelada por el usuario");
        }

        /**
         * Crea una excepcion con mensaje personalizado.
         *
         * @param message mensaje descriptivo
         */
        public UserCancelledException(String message) {
            super(message);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // IMPLEMENTACION NULA (NO-OP) PARA CONTEXTOS SIN UI
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Implementacion nula que no hace nada.
     *
     * <p>Util para contextos donde no hay UI disponible (tests, CI/CD, etc.)
     * o como fallback cuando no se puede inicializar JLine.</p>
     *
     * <p><strong>Comportamiento:</strong></p>
     * <ul>
     *   <li>Los metodos de output no hacen nada</li>
     *   <li>Los metodos de input retornan valores vacios o defaults</li>
     *   <li>Las dimensiones retornan valores estandar (80x24)</li>
     * </ul>
     */
    AgentUserInterface NO_OP = new AgentUserInterface() {
        @Override public String prompt(String message) { return ""; }
        @Override public String promptSecret(String message) { return ""; }
        @Override public boolean confirm(String message) { return false; }
        @Override public String selectFromMenu(MenuModel menu) { return null; }
        @Override public void info(String message) { }
        @Override public void success(String message) { }
        @Override public void warn(String message) { }
        @Override public void error(String message) { }
        @Override public void printMarkdown(String markdown) { }
        @Override public void renderDashboard(DashboardModel model) { }
        @Override public void updateStatusBar(StatusBarModel model) { }
        @Override public void setLoading(boolean loading, String message) { }
        @Override public void updateProgress(TaskTreeModel model) { }
        @Override public void onProcessComplete(String summary) { }
        @Override public void clearScreen() { }
        @Override public int getTerminalWidth() { return 80; }
        @Override public int getTerminalHeight() { return 24; }
    };
}
