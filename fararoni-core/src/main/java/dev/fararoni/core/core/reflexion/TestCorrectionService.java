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
package dev.fararoni.core.core.reflexion;

import dev.fararoni.core.core.clients.AgentClient;
import dev.fararoni.core.core.clients.AgentClient.AgentResponse;
import dev.fararoni.core.core.clients.AgentClient.ToolCall;
import dev.fararoni.core.core.clients.OpenAICompatibleClient;

import dev.fararoni.core.core.config.AgentConfig;
import dev.fararoni.core.core.managers.BiblioCognitiveTriadManager;
import dev.fararoni.core.core.memory.Wisdom;
import dev.fararoni.core.core.persona.Personas;
import dev.fararoni.core.core.prompt.PromptBuilder;
import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import dev.fararoni.core.core.skills.AgenticStrategies;
import dev.fararoni.core.core.skills.ReflexionStrategies;
import dev.fararoni.core.core.skills.ToolExecutionResult;
import dev.fararoni.core.core.skills.ToolExecutor;

import java.util.*;
import java.util.logging.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * @author Eber Cruz
 * @version 3.0.0
 */
public final class TestCorrectionService {
    private static final Logger LOG = Logger.getLogger(TestCorrectionService.class.getName());

    private final ReflexionEngine engine;
    private final FeedbackFormatter formatter;
    private String currentExerciseId;

    private final BiblioCognitiveTriadManager brain;

    private final ReflexionStrategies reflexionStrategies;
    private final AgenticStrategies agenticStrategies;

    private final ToolExecutor toolExecutor;

    private final AgentClient agentClient;

    public static int getMaxAttempts() {
        return AgentConfig.load().maxAttempts();
    }

    private TestCorrectionService(ReflexionEngine engine,
                                  FeedbackFormatter formatter,
                                  BiblioCognitiveTriadManager brain) {
        this.engine = engine;
        this.formatter = formatter;
        this.brain = brain;

        this.reflexionStrategies = new ReflexionStrategies();

        this.toolExecutor = new ToolExecutor();
        this.agenticStrategies = new AgenticStrategies();

        AgentConfig config = AgentConfig.load();

        this.agentClient = new OpenAICompatibleClient(
                config.getAgenticUrl(),
                config.getAgenticApiKey(),
                config.getAgenticModel()
        );
    }

    public static TestCorrectionService create() {
        BiblioCognitiveTriadManager brainManager = new BiblioCognitiveTriadManager();
        try {
            brainManager.loadMemoryBank();
        } catch (Exception e) {
            LOG.severe("Error critico cargando Cognitive Memory: " + e.getMessage());
        }

        return new TestCorrectionService(
                ReflexionEngine.forTestCorrection(),
                new FeedbackFormatter(),
                brainManager
        );
    }

    public static TestCorrectionService minimal() {
        return new TestCorrectionService(
                ReflexionEngine.minimal(),
                new FeedbackFormatter(),
                new BiblioCognitiveTriadManager()
        );
    }

    public String generateCorrectionFeedback(String code, String testOutput, int attemptNumber) {
        return generateCorrectionFeedback(code, testOutput, attemptNumber, null);
    }

    public String generateCorrectionFeedback(String code, String testOutput,
                                             int attemptNumber, String exerciseId) {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(testOutput, "testOutput must not be null");

        if (exerciseId != null) {
            this.currentExerciseId = exerciseId;
        }
        String safeId = (exerciseId != null) ? exerciseId : "unknown";

        AgentConfig config = AgentConfig.load();

        if (true) {
            try {
                var agentStrategyConfig = agenticStrategies.prepareStrategy(testOutput, safeId);

                if (agentStrategyConfig.tools() != null && !agentStrategyConfig.tools().isEmpty()) {
                    LOG.info("[ORCHESTRATOR] Activando Via Agentica (Function Calling) para: " + safeId);

                    AgentResponse response = agentClient.generateWithTools(
                            agentStrategyConfig.systemPrompt(),
                            "The tests failed. Analyze the error and fix the code:\n" + testOutput,
                            agentStrategyConfig.tools()
                    );

                    if (response.isToolCall()) {
                        String targetPath = "src/main/exercises/" + safeId.replace("-", "_") + ".py";

                        String goldenCode = handleToolExecution(response.toolCall(), targetPath);

                        if (goldenCode != null) {
                            return goldenCode;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warning("[ORCHESTRATOR] Fallo en Via Agentica: " + e.getMessage());
            }
        }

        LOG.info("[ORCHESTRATOR] Ejecutando Flujo Legacy (Prompt Engineering) para: " + safeId);

        List<Wisdom> wisdom = brain.retrieveWisdomObjectsByTag(safeId);

        String sniperAnalysis = reflexionStrategies.determineStrategy(testOutput, safeId, wisdom);

        EvaluationContext.Builder contextBuilder = EvaluationContext.builder()
                .userPrompt(safeId)
                .responseType(EvaluationContext.ResponseType.CODE)
                .metadata(EvaluationContext.KEY_TEST_OUTPUT, testOutput)
                .metadata(EvaluationContext.KEY_ATTEMPT_NUMBER, attemptNumber);

        if (exerciseId != null) {
            contextBuilder.metadata("exerciseId", exerciseId);
        }

        EvaluationContext context = contextBuilder.build();

        ReflexionEngine.ReflexionResult result = engine.reflect(code, context);
        String technicalFeedback = result.getFormattedFeedback();

        return String.format("%s\n\n%s", sniperAnalysis, technicalFeedback);
    }

    public String handleToolExecution(ToolCall toolCall, String targetFilePath) {
        LOG.info("[ORCHESTRATOR] El Agente solicita ejecutar herramienta: " + toolCall.functionName());

        ToolExecutionResult result = toolExecutor.executeTool(toolCall);

        if (result.success() && result.payload().isPresent()) {
            LOG.info("[ORCHESTRATOR] Persistiendo cambios en disco local (Backup)...");
            try {
                java.nio.file.Path path = Paths.get(targetFilePath);
                if (path.getParent() != null) Files.createDirectories(path.getParent());

                String codeContent = result.payload().get();

                Files.writeString(path, codeContent,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                LOG.info("[ORCHESTRATOR] Archivo escrito localmente. Retornando codigo a STDOUT.");

                return codeContent;
            } catch (IOException e) {
                LOG.severe("[ORCHESTRATOR] Error IO: " + e.getMessage());
                return null;
            }
        } else {
            LOG.warning("[ORCHESTRATOR] La herramienta fallo: " + result.message());
            return null;
        }
    }

    public String buildRetryPrompt(String originalInstructions, String code,
                                   String testOutput, int attemptNumber) {
        return buildRetryPrompt(originalInstructions, code, testOutput, "", attemptNumber, this.currentExerciseId);
    }

    public String buildRetryPrompt(String originalInstructions, String code,
                                   String testOutput, String stdErr,
                                   int attemptNumber) {
        return buildRetryPrompt(originalInstructions, code, testOutput, stdErr, attemptNumber, this.currentExerciseId);
    }

    public String buildRetryPrompt(String originalInstructions, String code,
                                   String testOutput, String stdErr,
                                   int attemptNumber,
                                   String exerciseId) {
        String safeExerciseId = exerciseId != null ? exerciseId : "unknown";
        List<Wisdom> surgicalWisdom = brain.retrieveWisdomObjectsByTag(safeExerciseId);
        String fullForensicReport = "=== TEST REPORT (STDOUT) ===\n" + testOutput + "\n\n=== SYSTEM LOGS (STDERR) ===\n" + stdErr;

        String sniperIntervention = reflexionStrategies.determineStrategy(fullForensicReport, safeExerciseId, surgicalWisdom);

        return PromptBuilder.create()
                .systemPrompt("Actúa como un Ingeniero de Software Senior.")
                .withPersona(Personas.SENIOR_ENGINEER)
                .addContext(surgicalWisdom)
                .userQuery(String.format("%s\n\nAttempt %d\nReport:\n%s\nCode:\n%s\nInstructions:\n%s",
                        sniperIntervention, attemptNumber, fullForensicReport, code, originalInstructions))
                .build();
    }

    public boolean needsCorrection(String code, String testOutput) {
        EvaluationContext context = EvaluationContext.forTestRetry("analysis", testOutput, 1);
        return engine.reflect(code, context).needsCorrection();
    }

    public Set<FailurePattern> detectPatterns(String testOutput) {
        EvaluationContext context = EvaluationContext.forTestRetry("pattern-detection", testOutput, 1);
        return new HashSet<>();
    }

    public FailurePattern getDominantPattern(String testOutput) {
        return FailurePattern.UNKNOWN;
    }

    public String getSummary(String testOutput) {
        return "Summary generation not active in Bypass mode.";
    }

    public void clearMemory() {
        engine.clearMemory();
        currentExerciseId = null;
    }

    public void startExercise(String exerciseId) {
        clearMemory();
        this.currentExerciseId = exerciseId;
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("currentExerciseId", currentExerciseId);
        return metrics;
    }

    public String generateDiagnosticReport(String errorText, String exerciseId) {
        LOG.info("[DIAGNOSTIC] Iniciando diagnostico para ejercicio: " + (exerciseId != null ? exerciseId : "unknown"));

        return generateCorrectionFeedback("", errorText, 1, exerciseId);
    }
}
