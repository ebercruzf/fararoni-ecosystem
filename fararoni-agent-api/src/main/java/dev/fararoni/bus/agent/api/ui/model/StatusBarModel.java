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
package dev.fararoni.bus.agent.api.ui.model;

import dev.fararoni.bus.agent.api.interaction.AgentUserInterface;

/**
 * Modelo de datos para la barra de estado global del agente.
 *
 * <h2>Proposito</h2>
 * <p>Representa el estado de la barra inferior/superior que muestra
 * informacion contextual durante la ejecucion del agente. Tipicamente
 * incluye estado actual, progreso, y metricas.</p>
 *
 * <h2>Layout Tipico</h2>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────┐
 * │ [leftText]          [centerText]              [rightText]     │
 * │ THINKING...         [████████░░] 80%          4500/8000 tokens│
 * └────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Componentes</h2>
 * <ul>
 *   <li><b>leftText:</b> Texto alineado a la izquierda (estado, accion actual)</li>
 *   <li><b>centerText:</b> Texto centrado (opcional, puede ser barra de progreso)</li>
 *   <li><b>rightText:</b> Texto alineado a la derecha (metricas, tokens)</li>
 *   <li><b>isLoading:</b> Si mostrar spinner/indicador de carga</li>
 *   <li><b>progressPercent:</b> Porcentaje de progreso (0-100), -1 si no aplica</li>
 * </ul>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // Status bar simple
 * var status = new StatusBarModel(
 *     "Procesando...",
 *     null,
 *     "4500 tokens",
 *     true,  // mostrar spinner
 *     -1     // sin barra de progreso
 * );
 *
 * // Con barra de progreso
 * var statusWithProgress = StatusBarModel.withProgress(
 *     "Descargando",
 *     75,
 *     "3 de 4 archivos"
 * );
 *
 * context.getUI().updateStatusBar(status);
 * }</pre>
 *
 * <h2>Estados Comunes</h2>
 * <ul>
 *   <li><b>IDLE:</b> Sin spinner, sin progreso</li>
 *   <li><b>THINKING:</b> Con spinner, sin progreso</li>
 *   <li><b>PROCESSING:</b> Con progreso definido</li>
 *   <li><b>ERROR:</b> Sin spinner, texto rojo (el Core decide el color)</li>
 * </ul>
 *
 * @param leftText        texto para el lado izquierdo (puede ser null)
 * @param centerText      texto para el centro (puede ser null)
 * @param rightText       texto para el lado derecho (puede ser null)
 * @param isLoading       true para mostrar spinner de carga
 * @param progressPercent porcentaje 0-100, o -1 si no hay progreso
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see AgentUserInterface#updateStatusBar(StatusBarModel)
 */
public record StatusBarModel(
    String leftText,
    String centerText,
    String rightText,
    boolean isLoading,
    int progressPercent
) {
    // ========================================================================
    // CONSTANTES
    // ========================================================================

    /**
     * Valor que indica que no hay progreso definido.
     */
    public static final int NO_PROGRESS = -1;

    // ========================================================================
    // VALIDACION EN CONSTRUCTOR CANONICO
    // ========================================================================

    /**
     * Constructor canonico con validacion.
     *
     * @param leftText        texto izquierdo (puede ser null)
     * @param centerText      texto central (puede ser null)
     * @param rightText       texto derecho (puede ser null)
     * @param isLoading       indicador de carga
     * @param progressPercent porcentaje de progreso
     * @throws IllegalArgumentException si progressPercent esta fuera de rango valido
     */
    public StatusBarModel {
        if (progressPercent != NO_PROGRESS && (progressPercent < 0 || progressPercent > 100)) {
            throw new IllegalArgumentException(
                "progressPercent debe ser -1 (sin progreso) o entre 0 y 100, " +
                "pero fue: " + progressPercent
            );
        }
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Crea una barra de estado simple solo con texto izquierdo.
     *
     * @param text texto a mostrar
     * @return StatusBarModel sin loading ni progreso
     */
    public static StatusBarModel simple(String text) {
        return new StatusBarModel(text, null, null, false, NO_PROGRESS);
    }

    /**
     * Crea una barra de estado con indicador de carga.
     *
     * @param text texto a mostrar junto al spinner
     * @return StatusBarModel con loading activo
     */
    public static StatusBarModel loading(String text) {
        return new StatusBarModel(text, null, null, true, NO_PROGRESS);
    }

    /**
     * Crea una barra de estado con barra de progreso.
     *
     * @param leftText  texto izquierdo
     * @param percent   porcentaje de progreso (0-100)
     * @param rightText texto derecho (opcional)
     * @return StatusBarModel con progreso
     */
    public static StatusBarModel withProgress(String leftText, int percent, String rightText) {
        return new StatusBarModel(leftText, null, rightText, false, percent);
    }

    /**
     * Crea una barra de estado vacia (para limpiar).
     *
     * @return StatusBarModel completamente vacio
     */
    public static StatusBarModel empty() {
        return new StatusBarModel(null, null, null, false, NO_PROGRESS);
    }

    /**
     * Crea una barra de estado para mostrar tokens usados.
     *
     * @param status      estado actual ("THINKING", "IDLE", etc.)
     * @param tokensUsed  tokens consumidos
     * @param tokensLimit limite de tokens
     * @return StatusBarModel formateado para tokens
     */
    public static StatusBarModel forTokens(String status, int tokensUsed, int tokensLimit) {
        String rightText = String.format("%,d / %,d tokens", tokensUsed, tokensLimit);
        boolean loading = "THINKING".equalsIgnoreCase(status) ||
                         "PROCESSING".equalsIgnoreCase(status);
        return new StatusBarModel(status, null, rightText, loading, NO_PROGRESS);
    }

    // ========================================================================
    // METODOS DE UTILIDAD
    // ========================================================================

    /**
     * Verifica si hay una barra de progreso definida.
     *
     * @return true si progressPercent esta entre 0 y 100
     */
    public boolean hasProgress() {
        return progressPercent >= 0 && progressPercent <= 100;
    }

    /**
     * Verifica si la barra de estado esta vacia (sin contenido).
     *
     * @return true si no hay texto ni loading ni progreso
     */
    public boolean isEmpty() {
        return !isLoading &&
               progressPercent == NO_PROGRESS &&
               (leftText == null || leftText.isBlank()) &&
               (centerText == null || centerText.isBlank()) &&
               (rightText == null || rightText.isBlank());
    }

    /**
     * Crea una copia con el progreso actualizado.
     *
     * @param newPercent nuevo porcentaje
     * @return nueva instancia con progreso actualizado
     */
    public StatusBarModel withProgressUpdated(int newPercent) {
        return new StatusBarModel(leftText, centerText, rightText, isLoading, newPercent);
    }

    /**
     * Crea una copia con el texto izquierdo actualizado.
     *
     * @param newLeftText nuevo texto izquierdo
     * @return nueva instancia con texto actualizado
     */
    public StatusBarModel withLeftText(String newLeftText) {
        return new StatusBarModel(newLeftText, centerText, rightText, isLoading, progressPercent);
    }
}
