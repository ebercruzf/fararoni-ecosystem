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
package dev.fararoni.core.core.agents;

import dev.fararoni.core.core.surgical.EditBlock;
import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.llm.ChatSession;
import dev.fararoni.core.core.llm.InferenceParameters;
import dev.fararoni.core.core.llm.LLMProvider;
import dev.fararoni.core.core.llm.ResponseParser;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class RabbitAgent {
    private ChatSession currentSession;
    private final LLMProvider llmProvider;
    private final ResponseParser parser;

    public RabbitAgent(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
        this.parser = new ResponseParser();
    }

    public List<EditBlock> planSurgery(AnalysisContext ctx, String instruction, String method) {
        String systemPrompt = buildSystemPrompt(ctx);
        this.currentSession = new ChatSession(llmProvider, systemPrompt, 0.2);

        String initialPrompt = String.format(
            "Target Method: %s\nInstruction: %s\nOutput JSON only.",
            method, instruction
        );

        String rawResponse = this.currentSession.sendUserMessage(initialPrompt);
        return parser.parseJson(rawResponse);
    }

    public List<EditBlock> askForCorrection(AnalysisContext ctx, String errorPrompt, String method) {
        if (this.currentSession == null) {
            throw new IllegalStateException("No hay sesion activa. Llama a planSurgery primero.");
        }

        String strictCorrectionPrompt = String.format(
            "ERROR EN TU RESPUESTA ANTERIOR:\n%s\n" +
            "--------------------------------------------------\n" +
            "INSTRUCCION DE RECUPERACION:\n" +
            "1. Analiza el error reportado arriba.\n" +
            "2. Genera un NUEVO plan JSON que solucione el conflicto.\n" +
            "3. NO expliques nada. NO pidas perdon. SOLO devuelve el JSON corregido.",
            errorPrompt
        );

        String newRawResponse = this.currentSession.sendUserMessage(strictCorrectionPrompt);

        return parser.parseJson(newRawResponse);
    }

    private String buildSystemPrompt(AnalysisContext ctx) {
        return String.format(
            "Eres Rabbit, un cirujano de codigo Java experto en ediciones quirurgicas.\n" +
            "Tu trabajo es generar planes de edicion SEARCH/REPLACE precisos.\n" +
            "Archivo actual: %s\n" +
            "REGLAS:\n" +
            "- Responde SOLO con JSON valido, sin explicaciones.\n" +
            "- Cada bloque debe tener: id, search, replace, estimatedLine.\n" +
            "- NO generes bloques que se solapen en las mismas lineas.\n" +
            "- Si necesitas modificar codigo adyacente, FUSIONA en un solo bloque.",
            ctx != null ? ctx.filename() : "desconocido"
        );
    }

    public ChatSession getCurrentSession() {
        return currentSession;
    }

    public String generateRaw(String prompt, String contextSnippet, InferenceParameters params) {
        String fullPrompt = String.format(
            "%s\n\n--- SOURCE CONTEXT ---\n%s\n--- END CONTEXT ---",
            prompt, contextSnippet
        );

        return llmProvider.generateWithParams(fullPrompt, params);
    }
}
