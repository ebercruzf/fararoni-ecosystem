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

import java.util.Objects;

/**
 * Representa un item individual dentro de un menu interactivo.
 *
 * <h2>Proposito</h2>
 * <p>Encapsula los datos de una opcion de menu que el usuario puede seleccionar.
 * El Core renderizara este item segun su estado (habilitado/deshabilitado,
 * seleccionado, etc.).</p>
 *
 * <h2>Componentes</h2>
 * <ul>
 *   <li><b>id:</b> Identificador unico para programaticamente saber que se selecciono</li>
 *   <li><b>label:</b> Texto visible para el usuario</li>
 *   <li><b>shortcut:</b> Atajo de teclado opcional (ej: "1", "a", "Enter")</li>
 *   <li><b>enabled:</b> Si el item puede ser seleccionado</li>
 * </ul>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // Item habilitado con atajo
 * var item1 = new MenuItem("save", "Guardar cambios", "s", true);
 *
 * // Item deshabilitado
 * var item2 = new MenuItem("delete", "Eliminar archivo", "d", false);
 *
 * // Usando builder pattern
 * var item3 = MenuItem.of("cancel", "Cancelar");
 * }</pre>
 *
 * <h2>Renderizado Esperado</h2>
 * <p>El Core podria renderizar esto como:</p>
 * <pre>
 *   [s] Guardar cambios
 *   [d] Eliminar archivo (deshabilitado)
 *   [c] Cancelar
 * </pre>
 *
 * @param id       identificador unico del item (para logica de seleccion)
 * @param label    texto visible para el usuario
 * @param shortcut atajo de teclado (puede ser null si no tiene)
 * @param enabled  true si el item puede ser seleccionado
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see MenuModel
 */
public record MenuItem(
    String id,
    String label,
    String shortcut,
    boolean enabled
) {
    // ========================================================================
    // VALIDACION EN CONSTRUCTOR CANONICO
    // ========================================================================

    /**
     * Constructor canonico con validacion.
     *
     * @param id       identificador unico (no puede ser null ni vacio)
     * @param label    texto visible (no puede ser null ni vacio)
     * @param shortcut atajo de teclado (puede ser null)
     * @param enabled  estado de habilitacion
     * @throws NullPointerException si id o label son null
     * @throws IllegalArgumentException si id o label estan vacios
     */
    public MenuItem {
        Objects.requireNonNull(id, "id no puede ser null");
        Objects.requireNonNull(label, "label no puede ser null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id no puede estar vacio");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label no puede estar vacio");
        }
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Crea un item de menu habilitado sin atajo.
     *
     * <p>Metodo de conveniencia para crear items simples.</p>
     *
     * @param id    identificador unico
     * @param label texto visible
     * @return nuevo MenuItem habilitado sin shortcut
     */
    public static MenuItem of(String id, String label) {
        return new MenuItem(id, label, null, true);
    }

    /**
     * Crea un item de menu habilitado con atajo.
     *
     * @param id       identificador unico
     * @param label    texto visible
     * @param shortcut atajo de teclado
     * @return nuevo MenuItem habilitado con shortcut
     */
    public static MenuItem of(String id, String label, String shortcut) {
        return new MenuItem(id, label, shortcut, true);
    }

    /**
     * Crea un item de menu deshabilitado.
     *
     * <p>Los items deshabilitados se muestran pero no pueden seleccionarse.</p>
     *
     * @param id    identificador unico
     * @param label texto visible
     * @return nuevo MenuItem deshabilitado
     */
    public static MenuItem disabled(String id, String label) {
        return new MenuItem(id, label, null, false);
    }

    // ========================================================================
    // METODOS DE UTILIDAD
    // ========================================================================

    /**
     * Verifica si este item tiene un atajo de teclado definido.
     *
     * @return true si shortcut no es null ni vacio
     */
    public boolean hasShortcut() {
        return shortcut != null && !shortcut.isBlank();
    }

    /**
     * Crea una copia de este item pero deshabilitado.
     *
     * @return nuevo MenuItem con los mismos datos pero enabled=false
     */
    public MenuItem asDisabled() {
        return new MenuItem(id, label, shortcut, false);
    }

    /**
     * Crea una copia de este item pero habilitado.
     *
     * @return nuevo MenuItem con los mismos datos pero enabled=true
     */
    public MenuItem asEnabled() {
        return new MenuItem(id, label, shortcut, true);
    }
}
