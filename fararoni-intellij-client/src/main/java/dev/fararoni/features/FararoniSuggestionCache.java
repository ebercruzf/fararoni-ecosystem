/*
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---------------------------------------------------------------------------
 *
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licenciado bajo la Licencia Apache, Version 2.0 (la "Licencia");
 * no puede usar este archivo excepto en cumplimiento con la Licencia.
 * Puede obtener una copia de la Licencia en
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * A menos que lo exija la ley aplicable o se acuerde por escrito, el software
 * distribuido bajo la Licencia se distribuye "TAL CUAL", SIN GARANTIAS NI
 * CONDICIONES DE NINGUN TIPO, ya sean expresas o implicitas.
 * Consulte la Licencia para conocer el lenguaje especifico que rige los
 * permisos y las limitaciones de la misma.
 */
package dev.fararoni.features;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class FararoniSuggestionCache {

    private static final Logger LOG = Logger.getLogger(FararoniSuggestionCache.class.getName());

    /** Tiempo de vida de una sugerencia en milisegundos (5 minutos) */
    public static final long TTL_MS = 5 * 60 * 1000;

    /** Máximo de sugerencias en cache */
    public static final int MAX_CACHE_SIZE = 50;

    private final Project project;
    private final Map<String, CachedSuggestion> cache = new ConcurrentHashMap<>();

    /**
     * Crea un nuevo cache para el proyecto dado.
     *
     * @param project el proyecto de IntelliJ
     */
    public FararoniSuggestionCache(Project project) {
        this.project = project;
        LOG.info("[SuggestionCache] Initialized for project: " + project.getName());
    }

    /**
     * Almacena una sugerencia en el cache.
     *
     * <p>Si ya existe una sugerencia para el archivo, la reemplaza.</p>
     *
     * @param filePath   ruta completa del archivo
     * @param suggestion la sugerencia a almacenar
     */
    public void put(String filePath, CachedSuggestion suggestion) {
        // Limpiar expiradas primero si el cache está lleno
        if (cache.size() >= MAX_CACHE_SIZE) {
            cleanExpired();
        }

        cache.put(filePath, suggestion);
        LOG.info("[SuggestionCache] Cached suggestion for " + extractFileName(filePath) +
                 " (intent: " + suggestion.intent() + ")");
    }

    /**
     * Almacena una sugerencia con parámetros individuales.
     *
     * @param filePath ruta completa del archivo
     * @param content  código sugerido
     * @param intent   tipo de sugerencia
     * @param traceId  ID para tracking
     */
    public void put(String filePath, String content, String intent, String traceId) {
        put(filePath, new CachedSuggestion(content, intent, traceId, Instant.now().toEpochMilli()));
    }

    /**
     * Obtiene una sugerencia del cache.
     *
     * <p>Retorna null si no existe o si expiró.</p>
     *
     * @param filePath ruta completa del archivo
     * @return la sugerencia o null
     */
    public CachedSuggestion get(String filePath) {
        CachedSuggestion suggestion = cache.get(filePath);

        if (suggestion == null) {
            return null;
        }

        // Verificar si expiró
        if (isExpired(suggestion)) {
            cache.remove(filePath);
            LOG.fine("[SuggestionCache] Expired suggestion removed for " + extractFileName(filePath));
            return null;
        }

        return suggestion;
    }

    /**
     * Verifica si hay una sugerencia disponible para un archivo.
     *
     * @param filePath ruta completa del archivo
     * @return true si hay sugerencia válida (no expirada)
     */
    public boolean has(String filePath) {
        return get(filePath) != null;
    }

    /**
     * Elimina una sugerencia del cache.
     *
     * <p>Llamar después de que el usuario aplique o rechace la sugerencia.</p>
     *
     * @param filePath ruta completa del archivo
     * @return la sugerencia eliminada o null
     */
    public CachedSuggestion remove(String filePath) {
        CachedSuggestion removed = cache.remove(filePath);
        if (removed != null) {
            LOG.info("[SuggestionCache] Removed suggestion for " + extractFileName(filePath));
        }
        return removed;
    }

    /**
     * Limpia todas las sugerencias expiradas.
     *
     * @return número de sugerencias eliminadas
     */
    public int cleanExpired() {
        int removed = 0;
        long now = Instant.now().toEpochMilli();

        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue().timestamp() > TTL_MS) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            LOG.info("[SuggestionCache] Cleaned " + removed + " expired suggestions");
        }

        return removed;
    }

    /**
     * Limpia todo el cache.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        LOG.info("[SuggestionCache] Cleared " + size + " suggestions");
    }

    /**
     * Retorna el número de sugerencias en cache.
     *
     * @return tamaño del cache
     */
    public int size() {
        return cache.size();
    }

    /**
     * Verifica si una sugerencia expiró.
     */
    private boolean isExpired(CachedSuggestion suggestion) {
        return Instant.now().toEpochMilli() - suggestion.timestamp() > TTL_MS;
    }

    /**
     * Extrae el nombre del archivo de una ruta completa.
     */
    private String extractFileName(String filePath) {
        if (filePath == null) return "unknown";
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    /**
     * Obtiene instancia del servicio para un proyecto.
     *
     * @param project el proyecto
     * @return instancia del FararoniSuggestionCache
     */
    public static FararoniSuggestionCache getInstance(Project project) {
        return project.getService(FararoniSuggestionCache.class);
    }

    /**
     * Representa una sugerencia almacenada en cache.
     *
     * @param content   el código sugerido por el LLM
     * @param intent    tipo de sugerencia (QUICK_FIX, SURGICAL_FIX, etc.)
     * @param traceId   ID para tracking y debugging
     * @param timestamp momento en que se recibió (epoch millis)
     */
    public record CachedSuggestion(
        String content,
        String intent,
        String traceId,
        long timestamp
    ) {
        /**
         * Calcula la edad de la sugerencia en segundos.
         *
         * @return edad en segundos
         */
        public long ageSeconds() {
            return (Instant.now().toEpochMilli() - timestamp) / 1000;
        }

        /**
         * Verifica si es una sugerencia de fix quirúrgico.
         *
         * @return true si es SURGICAL_FIX
         */
        public boolean isSurgicalFix() {
            return "SURGICAL_FIX".equalsIgnoreCase(intent);
        }

        /**
         * Verifica si es un quick fix.
         *
         * @return true si es QUICK_FIX
         */
        public boolean isQuickFix() {
            return "QUICK_FIX".equalsIgnoreCase(intent);
        }
    }
}
