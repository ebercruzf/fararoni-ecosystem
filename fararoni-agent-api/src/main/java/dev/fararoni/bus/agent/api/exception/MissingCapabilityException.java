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
package dev.fararoni.bus.agent.api.exception;

/**
 * Excepcion lanzada cuando se requiere una capacidad no disponible.
 *
 * <p>Esta excepcion indica que una operacion requiere un plugin
 * o extension que no esta instalado. El sistema puede usar esta
 * excepcion para ofrecer al usuario instalar el componente faltante.
 *
 * <h2>Casos de Uso:</h2>
 * <ul>
 *   <li>Analisis de imagen sin plugin Vision instalado</li>
 *   <li>Transcripcion de audio sin plugin Voice instalado</li>
 *   <li>Funcionalidad Enterprise sin licencia activa</li>
 * </ul>
 *
 * <h2>Ejemplo de Uso:</h2>
 * <pre>{@code
 * public void analyzeImage(Path imagePath) {
 *     if (!visionPlugin.isInstalled()) {
 *         throw new MissingCapabilityException(
 *             "vision",
 *             "Analisis de imagenes requiere el plugin Vision"
 *         );
 *     }
 *     // ... procesar imagen
 * }
 * }</pre>
 *
 * <h2>Manejo en InteractiveShell:</h2>
 * <pre>{@code
 * try {
 *     command.execute(args, ctx);
 * } catch (MissingCapabilityException e) {
 *     if (e.isInstallable()) {
 *         ctx.print("Esta funcion requiere: " + e.getCapabilityName());
 *         ctx.print("Instalar con: /plugins install " + e.getPluginId());
 *     }
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class MissingCapabilityException extends RuntimeException {

    /** ID del plugin o capacidad faltante */
    private final String capabilityId;

    /** Nombre legible de la capacidad */
    private final String capabilityName;

    /** Si la capacidad puede instalarse via PluginManager */
    private final boolean installable;

    /**
     * Crea una excepcion para capacidad faltante.
     *
     * @param capabilityId ID del plugin (ej: "vision", "voice")
     * @param message mensaje descriptivo
     */
    public MissingCapabilityException(String capabilityId, String message) {
        this(capabilityId, capabilityId, message, true);
    }

    /**
     * Crea una excepcion con nombre personalizado.
     *
     * @param capabilityId ID del plugin
     * @param capabilityName nombre para mostrar
     * @param message mensaje descriptivo
     */
    public MissingCapabilityException(String capabilityId, String capabilityName, String message) {
        this(capabilityId, capabilityName, message, true);
    }

    /**
     * Constructor completo.
     *
     * @param capabilityId ID del plugin
     * @param capabilityName nombre para mostrar
     * @param message mensaje descriptivo
     * @param installable si puede instalarse automaticamente
     */
    public MissingCapabilityException(String capabilityId, String capabilityName,
            String message, boolean installable) {
        super(message);
        this.capabilityId = capabilityId;
        this.capabilityName = capabilityName;
        this.installable = installable;
    }

    /**
     * Constructor con causa.
     *
     * @param capabilityId ID del plugin
     * @param message mensaje descriptivo
     * @param cause causa original
     */
    public MissingCapabilityException(String capabilityId, String message, Throwable cause) {
        super(message, cause);
        this.capabilityId = capabilityId;
        this.capabilityName = capabilityId;
        this.installable = true;
    }

    /**
     * Retorna el ID del plugin o capacidad faltante.
     *
     * @return ID usado para instalacion (ej: "vision")
     */
    public String getCapabilityId() {
        return capabilityId;
    }

    /**
     * Retorna el nombre legible de la capacidad.
     *
     * @return nombre para mostrar al usuario
     */
    public String getCapabilityName() {
        return capabilityName;
    }

    /**
     * Indica si la capacidad puede instalarse automaticamente.
     *
     * @return true si se puede instalar via /plugins install
     */
    public boolean isInstallable() {
        return installable;
    }

    /**
     * Genera mensaje de ayuda para instalacion.
     *
     * @return texto con instrucciones de instalacion
     */
    public String getInstallationHint() {
        if (installable) {
            return String.format(
                "Para habilitar esta funcionalidad, instale el plugin:\n" +
                "  /plugins install %s",
                capabilityId
            );
        } else {
            return "Esta funcionalidad no esta disponible en su configuracion actual.";
        }
    }

    @Override
    public String toString() {
        return String.format("MissingCapabilityException[%s]: %s",
            capabilityId, getMessage());
    }
}
