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
package dev.fararoni.core.core.react;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BatchRepairDirective implements IRepairDirective {
    private static final Pattern[] FILE_ERROR_PATTERNS = {
        Pattern.compile("\\[ERROR]\\s+(/\\S+\\.\\w+):\\["),
        Pattern.compile("(/\\S+\\.\\w+):\\d+:"),
        Pattern.compile("(/\\S+\\.\\w+):\\d+:\\d+:.*(?:error|warning)"),
        Pattern.compile("(\\S+\\.tsx?)\\(\\d+,\\d+\\):"),
        Pattern.compile("-->\\s+(\\S+\\.rs):\\d+:\\d+"),
        Pattern.compile("(\\.?/\\S+\\.go):\\d+:\\d+:"),
        Pattern.compile("File \"(\\S+\\.py)\",\\s+line \\d+"),
    };

    private static final java.util.logging.Logger LOG =
        java.util.logging.Logger.getLogger(BatchRepairDirective.class.getName());

    @Override
    public boolean applies(String buildOutput) {
        if (buildOutput == null) return false;
        int count = countDistinctErrorFiles(buildOutput);
        LOG.info("[BATCH-DETECT] distinctErrorFiles=" + count + " applies=" + (count > 1));
        return count > 1;
    }

    private int countDistinctErrorFiles(String buildOutput) {
        Set<String> files = new LinkedHashSet<>();
        for (Pattern pattern : FILE_ERROR_PATTERNS) {
            Matcher m = pattern.matcher(buildOutput);
            while (m.find()) {
                files.add(m.group(1));
            }
        }
        if (!files.isEmpty()) {
            LOG.info("[BATCH-DETECT] files found: " + files);
        }
        return files.size();
    }

    @Override
    public String initialProtocol() {
        return "[PROTOCOLO DE REPARACIÓN EN LOTE — OBLIGATORIO]\n"
            + "1. IDENTIFICACIÓN: Analiza la salida del error. Múltiples archivos fallaron — modo BATCH activo.\n"
            + "2. LECTURA EN LOTE: Usa 'fs_read' para leer TODOS los archivos afectados, uno por uno, ANTES de intentar corregirlos.\n"
            + "3. PARCHEO EN LOTE: Usa 'fs_patch' para aplicar TODAS las correcciones necesarias en TODOS los archivos afectados.\n"
            + "4. VERIFICACIÓN: SOLO cuando TODOS los archivos estén parcheados, usa 'ShellCommand' para re-ejecutar el comando de ejecución/construcción original.\n"
            + "PROHIBIDO: NO ejecutes el comando de validación después de parchear un solo archivo si hay más errores pendientes. "
            + "Lee TODO, parchea TODO, verifica UNA vez.";
    }

    @Override
    public String afterShellFailure() {
        return "[PROTOCOLO] Fallo detectado. RECUERDA EL MODO BATCH: Lee TODOS los archivos con errores (fs_read) "
            + "antes de empezar a corregirlos (fs_patch).";
    }

    @Override
    public String afterRead() {
        return "[DIRECTIVA] Archivo leído. Si hay MÁS archivos con errores, usa fs_read para leerlos ahora. "
            + "Si ya leíste todos, usa fs_patch para comenzar a corregirlos.";
    }

    @Override
    public String afterPatch() {
        return "[DIRECTIVA] Parche aplicado. Si hay OTROS archivos con errores pendientes, usa fs_patch en ellos ahora. "
            + "SOLO si ya corregiste TODOS los archivos, usa ShellCommand para ejecutar el comando original y verificar.";
    }

    @Override
    public String certitudeAfterRead() {
        return "PROTOCOL-MANDATE: You have read a file. "
            + "If there are MORE files with errors, read them first. "
            + "Once ALL error files are read, apply the fixes using 'fs_patch'.";
    }

    @Override
    public String certitudeAfterPatch() {
        return "PROTOCOL-MANDATE: Your patch has been applied to disk successfully. "
            + "If there are MORE files with errors, use fs_patch on them NOW. "
            + "If you have fixed ALL errors in ALL files, verify by running the ORIGINAL command via ShellCommand. "
            + "Do NOT run the command if there are still known unpatched files.";
    }
}
