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
 * Categorias para organizar comandos de consola en el sistema de ayuda.
 *
 * <p>Las categorias permiten agrupar comandos relacionados en /help
 * para mejor navegabilidad y descubrimiento de funcionalidades.
 *
 * <h2>Orden de presentacion:</h2>
 * <ol>
 *   <li>BASIC - Comandos esenciales (/help, /exit)</li>
 *   <li>FILE - Gestion de archivos (/load, /add, /drop)</li>
 *   <li>CONTEXT - Contexto y proyecto (/tree, /context)</li>
 *   <li>GIT - Control de versiones (/commit, /undo, /git)</li>
 *   <li>WEB - Web scraping (/web)</li>
 *   <li>CONFIG - Configuracion (/config, /router, /model)</li>
 *   <li>DEBUG - Desarrollo (/debug, /tokens, /status)</li>
 *   <li>ENTERPRISE - Funciones Enterprise (/rag, /audit, /map)</li>
 * </ol>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public enum CommandCategory {

    /**
     * Comandos basicos esenciales.
     * Incluye: /help, /exit, /clear
     */
    BASIC("Basicos", 1),

    /**
     * Gestion de archivos en contexto.
     * Incluye: /load, /unload, /add, /drop, /list
     */
    FILE("Archivos", 2),

    /**
     * Comandos de contexto y estructura del proyecto.
     * Incluye: /tree, /context, /map (Enterprise)
     */
    CONTEXT("Contexto", 3),

    /**
     * Control de versiones y Git.
     * Incluye: /commit, /undo, /git, /diff
     */
    GIT("Git", 4),

    /**
     * Web scraping y contenido externo.
     * Incluye: /web
     */
    WEB("Web", 5),

    /**
     * Configuracion y ajustes del sistema.
     * Incluye: /config, /router, /model, /mode
     */
    CONFIG("Configuracion", 6),

    /**
     * Herramientas de desarrollo y debug.
     * Incluye: /debug, /tokens, /status, /history, /dryrun
     */
    DEBUG("Debug", 7),

    /**
     * Funciones exclusivas de Enterprise.
     * Incluye: /rag, /audit, /research, /teach, /remember
     */
    ENTERPRISE("Enterprise", 8),

    /**
     * Inteligencia y reconocimiento.
     * Incluye: /web (search), /deep (research), investigacion
     */
    INTELLIGENCE("Inteligencia", 9),

    /**
     * Extensiones modulares.
     * Incluye: /vision, /ocr, /voice, y otros plugins.
     */
    EXTENSIONS("Extensiones", 10);

    private final String displayName;
    private final int order;

    CommandCategory(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }

    /**
     * Nombre para mostrar en la interfaz.
     * @return nombre legible de la categoria
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Orden de presentacion en /help.
     * @return numero de orden (menor = primero)
     */
    public int getOrder() {
        return order;
    }
}
