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
package dev.fararoni.core.core.hybrid;

import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.indexing.SentinelJavaParser;
import dev.fararoni.core.core.indexing.model.LineRange;
import dev.fararoni.core.core.surgical.EditBlock;
import dev.fararoni.core.core.surgical.OverlapConflictException;
import dev.fararoni.core.core.surgical.OverlapDetector;
import dev.fararoni.core.core.surgical.SurgeryConflictResolver;
import dev.fararoni.core.core.surgical.SurgeryReport;
import dev.fararoni.core.core.surgical.SurgicalEditor;
import dev.fararoni.core.core.surgical.SurgicalException;
import dev.fararoni.core.core.agents.RabbitAgent;

import java.util.List;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public class HybridBrainService {
    private static final int MAX_SURGICAL_RETRIES = 2;

    private static final Logger logger = Logger.getLogger(HybridBrainService.class.getName());

    private final RabbitAgent rabbit;
    private final TurtleAgent turtle;
    private final SurgicalEditor editor;
    private final SurgeryConflictResolver conflictResolver;
    private final SentinelJavaParser parser;
    private final OverlapDetector overlapDetector;

    public HybridBrainService(
            RabbitAgent rabbit,
            TurtleAgent turtle,
            SurgicalEditor editor,
            SurgeryConflictResolver conflictResolver,
            SentinelJavaParser parser) {
        this.rabbit = rabbit;
        this.turtle = turtle;
        this.editor = editor;
        this.conflictResolver = conflictResolver;
        this.parser = parser;
        this.overlapDetector = new OverlapDetector();
    }

    public HybridBrainService(
            RabbitAgent rabbit,
            TurtleAgent turtle,
            SurgicalEditor editor,
            SurgeryConflictResolver conflictResolver,
            SentinelJavaParser parser,
            OverlapDetector overlapDetector) {
        this.rabbit = rabbit;
        this.turtle = turtle;
        this.editor = editor;
        this.conflictResolver = conflictResolver;
        this.parser = parser;
        this.overlapDetector = overlapDetector;
    }

    public String executeModification(String filename, String originalSource, String userInstruction, String targetMethod) {
        AnalysisContext context = parser.createContext(filename, originalSource);

        try {
            logger.info("Fase 1: Planificando cirugia inicial...");
            List<EditBlock> initialPlan = rabbit.planSurgery(context, userInstruction, targetMethod);

            if (initialPlan.isEmpty()) {
                logger.warning("Rabbit no genero cambios. Abortando.");
                return originalSource;
            }

            List<EditBlock> sanitizedPlan = overlapDetector.optimizeRedundancies(initialPlan);

            return executeSurgeryWithRecursiveRetry(
                originalSource,
                sanitizedPlan,
                targetMethod,
                context,
                userInstruction,
                0
            );
        } catch (SurgicalException e) {
            return engageTurtleProtocol(originalSource, context, userInstruction, targetMethod, e);
        }
    }

    private String executeSurgeryWithRecursiveRetry(
            String source,
            List<EditBlock> currentPlan,
            String targetMethod,
            AnalysisContext context,
            String originalInstruction,
            int retryCount) {
        try {
            overlapDetector.validateCollisions(currentPlan);

            SurgeryReport report = editor.executeSurgery(source, currentPlan, targetMethod);
            return report.content();
        } catch (OverlapConflictException e) {
            if (retryCount >= MAX_SURGICAL_RETRIES) {
                logger.warning(String.format("Rabbit agoto sus reintentos (%d/%d). Activando Fallback.",
                    retryCount, MAX_SURGICAL_RETRIES));

                return engageTurtleProtocol(source, context, originalInstruction, targetMethod, e);
            }

            logger.info(String.format("Conflicto detectado. Reintento %d/%d. Instruyendo al Rabbit...",
                retryCount + 1, MAX_SURGICAL_RETRIES));

            String fixPrompt = e.getPromptHint();

            List<EditBlock> fixedPlan = rabbit.askForCorrection(context, fixPrompt, targetMethod);

            List<EditBlock> sanitizedFixedPlan = overlapDetector.optimizeRedundancies(fixedPlan);

            return executeSurgeryWithRecursiveRetry(
                source,
                sanitizedFixedPlan,
                targetMethod,
                context,
                originalInstruction,
                retryCount + 1
            );
        }
    }

    public String applyChangesWithSafetyNet(
            String source,
            List<EditBlock> rabbitPlan,
            String targetMethod,
            AnalysisContext context,
            String instruction) {
        try {
            SurgeryReport report = editor.executeSurgery(source, rabbitPlan, targetMethod);
            return report.content();
        } catch (OverlapConflictException e) {
            logger.warning("[WARN] Conflicto detectado. Activando SurgeryConflictResolver...");

            try {
                List<EditBlock> unifiedPlan = conflictResolver.resolve(e.getConflictingBlocks(), source);
                SurgeryReport report = editor.executeSurgery(source, unifiedPlan, targetMethod);
                return report.content();
            } catch (Exception rabbitFail) {
                logger.warning("Rabbit fallo la unificacion. Escalando a la Tortuga...");
                return engageTurtleProtocol(source, context, instruction, targetMethod, e);
            }
        }
    }

    private String handleConflictProtocol(
            String originalSource,
            AnalysisContext context,
            String instruction,
            String targetMethod,
            OverlapConflictException conflict) {
        try {
            List<EditBlock> unifiedPlan = conflictResolver.resolve(conflict.getConflictingBlocks(), originalSource);

            logger.info("Re-intento de cirugia con bloques unificados...");
            SurgeryReport report = editor.executeSurgery(originalSource, unifiedPlan, targetMethod);
            return report.content();
        } catch (Exception e) {
            logger.severe("[ERROR] Rabbit fallo la resolucion del conflicto. La complejidad excede capacidad 1.5B.");
            return engageTurtleProtocol(originalSource, context, instruction, targetMethod, e);
        }
    }

    private String engageTurtleProtocol(
            String originalSource,
            AnalysisContext context,
            String instruction,
            String targetMethod,
            Exception cause) {
        logger.info("ACTIVANDO PROTOCOLO TORTUGA (Deep Thought)...");

        java.util.Optional<String> methodBodyOpt = parser.extractMethodSource(context, targetMethod);

        if (methodBodyOpt.isEmpty()) {
            throw new SurgicalException(
                String.format("FATAL: El metodo '%s' no se encuentra en el archivo original. Imposible escalar.", targetMethod)
            );
        }
        String originalMethodBody = methodBodyOpt.get();

        String rescuePrompt = String.format(
            "CRITICAL TASK: Complete rewrite of method '%s'.\n" +
            "CONTEXT: Previous surgical patches failed due to: %s.\n" +
            "INSTRUCTION: Re-implement the method correctly to satisfy: '%s'.\n" +
            "CONSTRAINT 1: Output ONLY the Java code for the method.\n" +
            "CONSTRAINT 2: Do NOT change the method signature (name/params/return type).\n" +
            "CONSTRAINT 3: Keep existing imports and dependencies valid.",
            targetMethod, cause.getMessage(), instruction
        );

        String newMethodCode = turtle.generateReplacer(rescuePrompt, originalMethodBody);

        if (!parser.validateMethodSignatureMatch(targetMethod, newMethodCode, context)) {
            logger.severe("La Tortuga rompio el contrato de la interfaz. Rollback ejecutado.");
            throw new SurgicalException("La reescritura de la Tortuga es invalida: La firma del metodo no coincide.");
        }

        LineRange methodRange = context.getMethodRange(targetMethod);
        if (methodRange == null) {
            return editor.replaceMethodComplete(originalSource, targetMethod, newMethodCode);
        }

        return editor.replaceRange(originalSource, methodRange, newMethodCode);
    }
}
