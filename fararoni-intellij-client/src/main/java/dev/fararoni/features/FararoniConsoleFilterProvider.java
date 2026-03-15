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

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class FararoniConsoleFilterProvider implements ConsoleFilterProvider {

    @Override
    public Filter @NotNull [] getDefaultFilters(@NotNull Project project) {
        return new Filter[]{new FararoniStackTraceFilter(project)};
    }
}

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
class FararoniStackTraceFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(FararoniStackTraceFilter.class.getName());

    private final Project project;

    // Control de rate limiting para no saturar con errores repetidos
    private long lastErrorTime = 0;
    private String lastErrorLine = "";
    private static final long DEBOUNCE_MS = 3000; // 3 segundos entre análisis

    public FararoniStackTraceFilter(Project project) {
        this.project = project;
    }

    @Override
    public Result applyFilter(@NotNull String line, int entireLength) {
        // Detectar líneas con excepciones o errores
        if (containsError(line)) {
            // Debounce: evitar analizar el mismo error repetidamente
            long now = System.currentTimeMillis();
            if (now - lastErrorTime < DEBOUNCE_MS && line.equals(lastErrorLine)) {
                return null;
            }
            lastErrorTime = now;
            lastErrorLine = line;

            // Disparar análisis en segundo plano (no bloqueamos la consola)
            analyzeErrorAsync(line);
        }

        return null; // No modificamos el texto de la consola
    }

    /**
     * Verifica si la línea contiene un error o excepción.
     */
    private boolean containsError(String line) {
        if (line == null || line.isBlank()) return false;

        // Patrones comunes de error en Java/Kotlin
        return line.contains("Exception") ||
               line.contains("Error:") ||
               line.contains("FATAL") ||
               line.contains("Caused by:") ||
               line.contains("at ") && line.contains("(") && line.contains(".java:"); // stacktrace line
    }

    /**
     * Envía el error al Core de Fararoni para análisis.
     */
    private void analyzeErrorAsync(String errorLine) {
        try {
            FararoniBridge bridge = FararoniBridge.getInstance(project);
            if (bridge != null) {
                LOG.info("[FararoniConsoleFilter] Error detectado, solicitando análisis táctico");
                LOG.info("[FararoniConsoleFilter] Línea: " + truncate(errorLine, 100));

                // Construir contexto del error
                String errorContext = "Error detectado en consola de IntelliJ:\n" + errorLine;

                // Usar la API táctica existente
                bridge.requestTacticalFix(
                    errorLine,           // descripción del error
                    errorContext,        // contexto
                    "ConsoleOutput"      // origen
                );
            }
        } catch (Exception e) {
            LOG.warning("[FararoniConsoleFilter] Error al enviar análisis: " + e.getMessage());
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
