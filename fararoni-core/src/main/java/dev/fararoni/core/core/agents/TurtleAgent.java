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

import dev.fararoni.core.core.config.AgentConfig;
import dev.fararoni.core.core.llm.LLMProvider;
import dev.fararoni.core.core.llm.InferenceParameters;
import dev.fararoni.core.core.surgical.SurgicalException;

import java.time.Duration;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class TurtleAgent implements dev.fararoni.core.core.hybrid.TurtleAgent {
    private static final Logger logger = Logger.getLogger(TurtleAgent.class.getName());

    private static final String SYSTEM_PERSONA = """
        You are an elite Java Software Architect.
        Your task is to REWRITE complete Java methods to fix bugs or implement features.

        RULES:
        1. OUTPUT ONLY JAVA CODE. No explanations, no markdown intro/outro.
        2. DO NOT hallucinate imports. Use only standard Java or provided context.
        3. MAINTAIN the exact method signature (name, return type, params).
        4. IMPLEMENT the full logic. Do not use comments like '// ... rest of code'.
        """;

    private final LLMProvider llmProvider;
    private final AgentConfig config;

    private final Pattern codeFencePattern = Pattern.compile("```(?:java)?\\s*(.*?)```", Pattern.DOTALL);

    public TurtleAgent(LLMProvider llmProvider, AgentConfig config) {
        this.llmProvider = llmProvider;
        this.config = config;
    }

    @Override
    public String generateReplacer(String prompt, String methodBody) {
        return generate(prompt, methodBody);
    }

    @Override
    public String generate(String instruction, String methodBody) {
        long startTime = System.currentTimeMillis();
        String modelName = config.getTurtleModelName();

        InferenceParameters params = new InferenceParameters()
            .setModel(modelName)
            .setTemperature(0.1)
            .setTopP(0.9)
            .setRepeatPenalty(1.15)
            .setNumCtx(8192)
            .setMaxTokens(4096)
            .setTimeout(Duration.ofSeconds(config.getTurtleTimeoutSeconds()));

        String fullPrompt = String.format("""
            %s

            <EXISTING_CODE>
            %s
            </EXISTING_CODE>

            <INSTRUCTION>
            %s
            </INSTRUCTION>

            Generate the replacement method now:
            """, SYSTEM_PERSONA, methodBody, instruction);

        try {
            logger.info(String.format("Turtle (Qwen 32B) pensando... [Timeout: %ds]", params.getTimeout().getSeconds()));

            String rawResponse = llmProvider.generateRaw(fullPrompt, params);

            String cleanCode = sanitizeResponse(rawResponse);

            validateOutput(cleanCode);

            long duration = System.currentTimeMillis() - startTime;
            logger.info(String.format("Turtle completo la tarea en %dms. (Output: %d chars)", duration, cleanCode.length()));

            return cleanCode;
        } catch (Exception e) {
            logger.severe(String.format("Fallo critico en TurtleAgent (Qwen Local): %s", e.getMessage()));
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                throw new SurgicalException("Turtle Timeout: Qwen 32B tardo demasiado. Reduce la carga del sistema.", e);
            }
            throw new SurgicalException("Turtle Generation Failed: " + e.getMessage(), e);
        }
    }

    private String sanitizeResponse(String raw) {
        if (raw == null || raw.isBlank()) return "";

        String clean = raw.trim();

        Matcher matcher = codeFencePattern.matcher(clean);
        if (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (candidate.contains("public") || candidate.contains("private") || candidate.contains("protected")) {
                return candidate;
            }
        }

        int methodStart = -1;
        String[] modifiers = {"public ", "private ", "protected ", "static "};

        for (String mod : modifiers) {
            int idx = clean.indexOf(mod);
            if (idx != -1 && (methodStart == -1 || idx < methodStart)) {
                methodStart = idx;
            }
        }

        if (methodStart != -1) {
            clean = clean.substring(methodStart);
        }

        int lastBrace = clean.lastIndexOf("}");
        if (lastBrace != -1 && lastBrace < clean.length() - 1) {
            clean = clean.substring(0, lastBrace + 1);
        }

        return clean;
    }

    private void validateOutput(String code) {
        if (code.isEmpty()) {
            throw new SurgicalException("Qwen 32B devolvio una respuesta vacia.");
        }
        if (!code.contains("{") || !code.contains("}")) {
            throw new SurgicalException("Qwen 32B genero texto, pero no parece un metodo Java valido.");
        }
    }
}
