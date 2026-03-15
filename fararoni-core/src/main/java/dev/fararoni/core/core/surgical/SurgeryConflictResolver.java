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
package dev.fararoni.core.core.surgical;

import dev.fararoni.core.core.agents.RabbitAgent;
import dev.fararoni.core.core.llm.InferenceParameters;
import dev.fararoni.core.core.parser.SurgicalBlockParser;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class SurgeryConflictResolver {
    private static final Logger logger = Logger.getLogger(SurgeryConflictResolver.class.getName());

    private final RabbitAgent rabbitAgent;
    private final SurgicalBlockParser blockParser;

    public SurgeryConflictResolver(RabbitAgent rabbitAgent) {
        this.rabbitAgent = rabbitAgent;
        this.blockParser = new SurgicalBlockParser();
    }

    public List<EditBlock> resolve(List<EditBlock> conflictingBlocks, String fullSourceCode) {
        if (conflictingBlocks == null || conflictingBlocks.isEmpty()) {
            return Collections.emptyList();
        }

        int minLine = conflictingBlocks.stream().mapToInt(EditBlock::estimatedLine).min().orElse(0);
        int maxLine = conflictingBlocks.stream().mapToInt(EditBlock::estimatedLine).max().orElse(0);

        String contextSnippet = extractContext(fullSourceCode, minLine, maxLine, 5);

        String changesDescription = conflictingBlocks.stream()
            .map(b -> String.format("- Block %s wants to change: '%s' TO '%s'",
                b.id(), b.search(), b.replace()))
            .collect(Collectors.joining("\n"));

        String prompt = String.format(
            "CRITICAL CONFLICT RESOLUTION REQUIRED.\n" +
            "Scenario: Multiple surgical blocks overlap in lines %d-%d.\n" +
            "Conflicting Intentions:\n%s\n" +
            "TASK: Unify ALL intentions into a SINGLE '<<<<<<< SEARCH ... ======= ... >>>>>>> REPLACE' block.\n" +
            "CONSTRAINT: Use the provided context accurately. Output ONLY the surgical block.",
            minLine, maxLine, changesDescription
        );

        InferenceParameters params = new InferenceParameters()
            .setTemperature(0.1)
            .setGrammar(UnifiedBlockGrammar.GBNF_GRAMMAR)
            .setMaxTokens(1024);

        try {
            logger.info(String.format("Intentando unificacion de %d bloques con Rabbit...", conflictingBlocks.size()));

            String unifiedRawOutput = rabbitAgent.generateRaw(prompt, contextSnippet, params);

            List<EditBlock> unifiedPlan = blockParser.parse(unifiedRawOutput);

            if (unifiedPlan.isEmpty()) {
                throw new SurgicalException("Rabbit genero una respuesta vacia o invalida bajo presion.");
            }

            return unifiedPlan;
        } catch (Exception e) {
            throw new SurgicalException("Fallo en resolucion de conflictos: " + e.getMessage(), e);
        }
    }

    private String extractContext(String source, int startLine, int endLine, int buffer) {
        String[] lines = source.split("\n");
        int start = Math.max(0, startLine - buffer - 1);
        int end = Math.min(lines.length, endLine + buffer);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }
}
