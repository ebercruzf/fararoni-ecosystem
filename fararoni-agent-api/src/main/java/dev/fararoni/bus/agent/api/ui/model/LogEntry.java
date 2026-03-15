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

import java.time.Instant;
import java.util.Objects;

/**
 * Representa una entrada de log para mostrar en el dashboard del agente.
 *
 * <h2>Proposito</h2>
 * <p>Encapsula la informacion de un evento de log que puede ser mostrado
 * en la interfaz de usuario. El Core decidira como renderizarlo (colores,
 * iconos, formato de timestamp, etc.).</p>
 *
 * <h2>Niveles de Log</h2>
 * <ul>
 *   <li><b>INFO:</b> Informacion general (azul/cyan)</li>
 *   <li><b>WARN:</b> Advertencia (amarillo)</li>
 *   <li><b>ERROR:</b> Error (rojo)</li>
 *   <li><b>DEBUG:</b> Debug (gris, solo si debug mode activo)</li>
 *   <li><b>SUCCESS:</b> Exito (verde)</li>
 * </ul>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * var log = new LogEntry(
 *     Instant.now(),
 *     "INFO",
 *     "Conectado a base de datos Oracle"
 * );
 *
 * // O usando el factory method
 * var errorLog = LogEntry.error("Fallo conexion a BD");
 * }</pre>
 *
 * <h2>Inmutabilidad</h2>
 * <p>Este record es inmutable. Una vez creado, sus valores no pueden
 * cambiar, lo que lo hace seguro para uso concurrente.</p>
 *
 * @param timestamp momento en que ocurrio el evento
 * @param level     nivel del log (INFO, WARN, ERROR, DEBUG, SUCCESS)
 * @param message   mensaje descriptivo del evento
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record LogEntry(
    Instant timestamp,
    String level,
    String message
) {
    // ========================================================================
    // VALIDACION EN CONSTRUCTOR CANONICO
    // ========================================================================

    /**
     * Constructor canonico con validacion.
     *
     * @param timestamp momento del evento (no puede ser null)
     * @param level     nivel del log (no puede ser null ni vacio)
     * @param message   mensaje del evento (no puede ser null)
     * @throws NullPointerException si algun parametro es null
     * @throws IllegalArgumentException si level esta vacio
     */
    public LogEntry {
        Objects.requireNonNull(timestamp, "timestamp no puede ser null");
        Objects.requireNonNull(level, "level no puede ser null");
        Objects.requireNonNull(message, "message no puede ser null");

        if (level.isBlank()) {
            throw new IllegalArgumentException("level no puede estar vacio");
        }
    }

    // ========================================================================
    // FACTORY METHODS - Para facilitar la creacion
    // ========================================================================

    /**
     * Crea una entrada de log de nivel INFO.
     *
     * @param message mensaje informativo
     * @return nueva entrada de log
     */
    public static LogEntry info(String message) {
        return new LogEntry(Instant.now(), "INFO", message);
    }

    /**
     * Crea una entrada de log de nivel WARN.
     *
     * @param message mensaje de advertencia
     * @return nueva entrada de log
     */
    public static LogEntry warn(String message) {
        return new LogEntry(Instant.now(), "WARN", message);
    }

    /**
     * Crea una entrada de log de nivel ERROR.
     *
     * @param message mensaje de error
     * @return nueva entrada de log
     */
    public static LogEntry error(String message) {
        return new LogEntry(Instant.now(), "ERROR", message);
    }

    /**
     * Crea una entrada de log de nivel DEBUG.
     *
     * @param message mensaje de debug
     * @return nueva entrada de log
     */
    public static LogEntry debug(String message) {
        return new LogEntry(Instant.now(), "DEBUG", message);
    }

    /**
     * Crea una entrada de log de nivel SUCCESS.
     *
     * @param message mensaje de exito
     * @return nueva entrada de log
     */
    public static LogEntry success(String message) {
        return new LogEntry(Instant.now(), "SUCCESS", message);
    }

    // ========================================================================
    // METODOS DE UTILIDAD
    // ========================================================================

    /**
     * Verifica si este log es de nivel ERROR.
     *
     * @return true si el nivel es ERROR
     */
    public boolean isError() {
        return "ERROR".equalsIgnoreCase(level);
    }

    /**
     * Verifica si este log es de nivel WARN.
     *
     * @return true si el nivel es WARN
     */
    public boolean isWarning() {
        return "WARN".equalsIgnoreCase(level);
    }

    /**
     * Verifica si este log es de nivel DEBUG.
     *
     * @return true si el nivel es DEBUG
     */
    public boolean isDebug() {
        return "DEBUG".equalsIgnoreCase(level);
    }
}
