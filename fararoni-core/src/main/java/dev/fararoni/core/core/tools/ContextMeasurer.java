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
package dev.fararoni.core.core.tools;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Herramienta de medicion de volumen de contexto para C-Fararoni.
 *
 * <p>Estima tokens basado en la arquitectura de DeepSeek/OpenAI.
 * Aproximacion estadistica: 1 token ≈ 4 caracteres para codigo fuente Java.</p>
 *
 * <p>Uso:</p>
 * <pre>{@code
 * var measurer = new ContextMeasurer(systemPrompt + userMessage);
 * measurer.printReport();
 * // Si excede limite, segmentar antes de enviar al LLM.
 * if (measurer.estimatedTokens() > 120_000) {
 *     // Truncar o segmentar
 * }
 * }</pre>
 *
 * @author Eber Cruz
 * @version 1.2.0
 * @since 1.2.0
 */
public record ContextMeasurer(String content) {

    private static final int AVG_CHARS_PER_TOKEN = 4;

    public int estimatedTokens() {
        return content != null ? content.length() / AVG_CHARS_PER_TOKEN : 0;
    }

    public double sizeKB() {
        return content != null ? content.getBytes(StandardCharsets.UTF_8).length / 1024.0 : 0.0;
    }

    public String status() {
        int tokens = estimatedTokens();
        if (tokens > 120_000) return "CRITICAL (Near 128K Limit)";
        if (tokens > 80_000)  return "WARNING (High Density)";
        if (tokens > 4_000)   return "OPTIMAL (Input Window)";
        return "LIGHT (Output Safe)";
    }

    public boolean exceedsOutputLimit() {
        return estimatedTokens() > 4_000;
    }

    public boolean exceedsInputLimit() {
        return estimatedTokens() > 120_000;
    }

    public void printReport() {
        byte[] bytes = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
        int tokens = estimatedTokens();
        NumberFormat nf = NumberFormat.getInstance(Locale.US);

        System.out.println("""
            +-------------------------------------------------------+
            | C-FARARONI CONTEXT AUDIT                              |
            +-------------------------------------------------------+
            | Tamano en Bruto : %s bytes
            | Volumen en KB   : %.2f KB
            | Tokens Est.     : ~%s
            | Estado Ventana  : %s
            +-------------------------------------------------------+
            """.formatted(
                nf.format(bytes.length),
                bytes.length / 1024.0,
                nf.format(tokens),
                status()
        ));
    }
}
