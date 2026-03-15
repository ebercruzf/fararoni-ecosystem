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

import java.util.List;
import java.util.Objects;

/**
 * Modelo de datos para un menu interactivo.
 *
 * <h2>Proposito</h2>
 * <p>Representa un menu con multiples opciones que el usuario puede navegar
 * y seleccionar. El modulo Enterprise construye este modelo con los datos,
 * y el Core lo renderiza usando JLine (o cualquier otra tecnologia de UI).</p>
 *
 * <h2>Componentes</h2>
 * <ul>
 *   <li><b>title:</b> Titulo del menu mostrado en la parte superior</li>
 *   <li><b>items:</b> Lista de opciones disponibles</li>
 *   <li><b>selectedIndex:</b> Indice del item actualmente seleccionado (para navegacion)</li>
 * </ul>
 *
 * <h2>Ejemplo de Uso</h2>
 * <pre>{@code
 * // Crear menu con opciones
 * var menu = new MenuModel(
 *     "Seleccione una opcion:",
 *     List.of(
 *         MenuItem.of("option1", "Guardar y continuar", "1"),
 *         MenuItem.of("option2", "Descartar cambios", "2"),
 *         MenuItem.of("option3", "Cancelar", "c")
 *     ),
 *     0  // Primera opcion seleccionada
 * );
 *
 * // Mostrar menu y obtener seleccion
 * String selected = context.getUI().selectFromMenu(menu);
 * if ("option1".equals(selected)) {
 *     // Usuario selecciono guardar
 * }
 * }</pre>
 *
 * <h2>Renderizado Esperado</h2>
 * <pre>
 * ┌─ Seleccione una opcion: ─────────────┐
 * │  [1] Guardar y continuar    &lt;--      │
 * │  [2] Descartar cambios               │
 * │  [c] Cancelar                        │
 * └──────────────────────────────────────┘
 * </pre>
 *
 * <h2>Flujo de Interaccion</h2>
 * <ol>
 *   <li>Enterprise crea MenuModel con opciones</li>
 *   <li>Enterprise llama context.getUI().selectFromMenu(menu)</li>
 *   <li>Core renderiza el menu usando JLine</li>
 *   <li>Usuario navega con flechas y selecciona con Enter</li>
 *   <li>Core retorna el ID del item seleccionado</li>
 *   <li>Enterprise recibe el ID y actua segun la seleccion</li>
 * </ol>
 *
 * @param title         titulo del menu
 * @param items         lista de items (opciones)
 * @param selectedIndex indice del item actualmente seleccionado (0-based)
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 * @see MenuItem
 * @see AgentUserInterface#selectFromMenu(MenuModel)
 */
public record MenuModel(
    String title,
    List<MenuItem> items,
    int selectedIndex
) {
    // ========================================================================
    // VALIDACION EN CONSTRUCTOR CANONICO
    // ========================================================================

    /**
     * Constructor canonico con validacion.
     *
     * @param title         titulo del menu (no puede ser null)
     * @param items         lista de items (no puede ser null ni vacia)
     * @param selectedIndex indice seleccionado (debe estar en rango valido)
     * @throws NullPointerException si title o items son null
     * @throws IllegalArgumentException si items esta vacio o selectedIndex fuera de rango
     */
    public MenuModel {
        Objects.requireNonNull(title, "title no puede ser null");
        Objects.requireNonNull(items, "items no puede ser null");

        if (items.isEmpty()) {
            throw new IllegalArgumentException("items no puede estar vacio");
        }

        // Crear copia defensiva inmutable
        items = List.copyOf(items);

        if (selectedIndex < 0 || selectedIndex >= items.size()) {
            throw new IllegalArgumentException(
                "selectedIndex debe estar entre 0 y " + (items.size() - 1) +
                ", pero fue: " + selectedIndex
            );
        }
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Crea un menu con el primer item seleccionado.
     *
     * @param title titulo del menu
     * @param items lista de items
     * @return nuevo MenuModel con selectedIndex=0
     */
    public static MenuModel of(String title, List<MenuItem> items) {
        return new MenuModel(title, items, 0);
    }

    /**
     * Crea un menu con el primer item seleccionado usando varargs.
     *
     * @param title titulo del menu
     * @param items items del menu
     * @return nuevo MenuModel con selectedIndex=0
     */
    public static MenuModel of(String title, MenuItem... items) {
        return new MenuModel(title, List.of(items), 0);
    }

    // ========================================================================
    // METODOS DE NAVEGACION
    // ========================================================================

    /**
     * Obtiene el item actualmente seleccionado.
     *
     * @return MenuItem en la posicion selectedIndex
     */
    public MenuItem getSelectedItem() {
        return items.get(selectedIndex);
    }

    /**
     * Crea una copia del menu con el siguiente item seleccionado.
     *
     * <p>Si ya esta en el ultimo item, permanece ahi (no hace wrap).</p>
     *
     * @return nuevo MenuModel con selectedIndex incrementado
     */
    public MenuModel selectNext() {
        int newIndex = Math.min(selectedIndex + 1, items.size() - 1);
        return new MenuModel(title, items, newIndex);
    }

    /**
     * Crea una copia del menu con el item anterior seleccionado.
     *
     * <p>Si ya esta en el primer item, permanece ahi (no hace wrap).</p>
     *
     * @return nuevo MenuModel con selectedIndex decrementado
     */
    public MenuModel selectPrevious() {
        int newIndex = Math.max(selectedIndex - 1, 0);
        return new MenuModel(title, items, newIndex);
    }

    /**
     * Crea una copia del menu con un item especifico seleccionado.
     *
     * @param index nuevo indice a seleccionar
     * @return nuevo MenuModel con el indice especificado
     * @throws IllegalArgumentException si index esta fuera de rango
     */
    public MenuModel selectIndex(int index) {
        return new MenuModel(title, items, index);
    }

    // ========================================================================
    // METODOS DE UTILIDAD
    // ========================================================================

    /**
     * Obtiene el numero de items en el menu.
     *
     * @return cantidad de items
     */
    public int size() {
        return items.size();
    }

    /**
     * Verifica si hay items habilitados en el menu.
     *
     * @return true si al menos un item esta habilitado
     */
    public boolean hasEnabledItems() {
        return items.stream().anyMatch(MenuItem::enabled);
    }

    /**
     * Busca un item por su atajo de teclado.
     *
     * @param shortcut atajo a buscar
     * @return indice del item con ese shortcut, o -1 si no existe
     */
    public int findByShortcut(String shortcut) {
        if (shortcut == null) return -1;

        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            if (shortcut.equalsIgnoreCase(item.shortcut()) && item.enabled()) {
                return i;
            }
        }
        return -1;
    }
}
