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

import dev.fararoni.bus.agent.api.ToolRegistry;
import dev.fararoni.bus.agent.api.ToolRequest;
import dev.fararoni.bus.agent.api.ToolResponse;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.core.client.LlmClient;
import dev.fararoni.core.core.engine.ReflexionGuard;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import dev.fararoni.core.core.persona.CognitiveEngine;
import dev.fararoni.core.core.persona.Persona;
import dev.fararoni.core.model.GenerationRequest;
import dev.fararoni.core.model.GenerationResponse;
import dev.fararoni.core.model.Message;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ReActEngine {
    private static final Logger LOG = Logger.getLogger(ReActEngine.class.getName());

    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
        "(?:Thought|Pensamiento|Razonamiento):\\s*(.+?)(?=(?:Action|Accion|Final|$))",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "(?:Action|Accion):\\s*(\\w+)\\.(\\w+)\\s*(?:\\{([^}]*)\\}|\\(([^)]*)\\))?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile(
        "(?:Final Answer|Respuesta Final|Answer):\\s*(.+)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern JSON_ACTION_PATTERN = Pattern.compile(
        "\\{\\s*\"tool\"\\s*:\\s*\"(\\w+)\"\\s*,\\s*\"action\"\\s*:\\s*\"(\\w+)\"\\s*(?:,\\s*\"params\"\\s*:\\s*(\\{[^}]*\\}))?\\s*\\}",
        Pattern.CASE_INSENSITIVE
    );

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ReflexionGuard reflexionGuard;
    private final CognitiveEngine cognitiveEngine;
    private final int maxTurns;
    private final int maxTokens;
    private final double temperature;
    private final boolean enableReflexion;
    private final boolean enablePersonas;

    private final Map<String, Object> sessionContext = new ConcurrentHashMap<>();
    private Consumer<ReActStep> stepListener;

    private ReActEngine(Builder builder) {
        this.llmClient = Objects.requireNonNull(builder.llmClient, "llmClient required");
        this.toolRegistry = builder.toolRegistry;
        this.reflexionGuard = builder.reflexionGuard;
        this.cognitiveEngine = builder.cognitiveEngine;
        this.maxTurns = builder.maxTurns > 0 ? builder.maxTurns : 5;
        this.maxTokens = builder.maxTokens > 0 ? builder.maxTokens : 2048;
        this.temperature = builder.temperature;
        this.enableReflexion = builder.enableReflexion;
        this.enablePersonas = builder.enablePersonas && cognitiveEngine != null;
    }

    public ReActResult execute(String task) {
        return execute(task, List.of());
    }

    public ReActResult execute(String task, List<Message> history) {
        Objects.requireNonNull(task, "task must not be null");

        LOG.info(() -> "[ReAct] Starting execution: " + truncate(task, 60));
        Instant startTime = Instant.now();

        List<ReActStep> steps = new ArrayList<>();
        List<Message> messages = new ArrayList<>(history);

        Persona persona = null;
        if (enablePersonas) {
            persona = cognitiveEngine.selectPersonaFor(task);
            final Persona selectedPersona = persona;
            LOG.fine(() -> "[ReAct] Selected persona: " + selectedPersona.id());
        }

        String systemPrompt = buildSystemPrompt(persona);
        messages.add(0, Message.system(systemPrompt));

        messages.add(Message.user(task));

        int turn = 0;
        ReActStep lastStep = null;
        String finalAnswer = null;

        while (turn < maxTurns) {
            turn++;
            final int currentTurn = turn;
            LOG.fine(() -> "[ReAct] Turn " + currentTurn + "/" + maxTurns);

            try {
                Instant stepStart = Instant.now();
                String llmOutput = callLlm(messages);
                Duration llmDuration = Duration.between(stepStart, Instant.now());

                LOG.fine(() -> "[ReAct] LLM output: " + truncate(llmOutput, 200));

                ParsedOutput parsed = parseOutput(llmOutput);

                if (parsed.isFinalAnswer()) {
                    finalAnswer = parsed.finalAnswer;
                    lastStep = ReActStep.finalAnswer(turn, finalAnswer, llmOutput);
                    steps.add(lastStep);
                    notifyStepListener(lastStep);
                    break;
                }

                if (parsed.hasAction()) {
                    stepStart = Instant.now();
                    ToolResponse observation = executeAction(parsed.action);
                    Duration actionDuration = Duration.between(stepStart, Instant.now());

                    lastStep = ReActStep.toolCall(
                        turn,
                        parsed.thought,
                        parsed.action,
                        observation,
                        llmOutput,
                        actionDuration
                    );
                    steps.add(lastStep);
                    notifyStepListener(lastStep);

                    messages.add(Message.assistant(llmOutput));
                    messages.add(Message.user("Observation: " + observation.getMessage()));

                    if (enableReflexion && reflexionGuard != null && !observation.success()) {
                        var guardResult = reflexionGuard.validate(
                            observation.getMessage(),
                            EvaluationContext.empty()
                        );
                        if (!guardResult.isAccepted()) {
                            String feedback = guardResult.getFeedback();
                            if (feedback != null && !feedback.isBlank()) {
                                messages.add(Message.system("Feedback: " + feedback));
                            }
                        }
                    }
                } else {
                    lastStep = ReActStep.thinking(turn, parsed.thought, llmOutput);
                    steps.add(lastStep);
                    notifyStepListener(lastStep);

                    messages.add(Message.assistant(llmOutput));
                    messages.add(Message.user(
                        "Please provide an Action or Final Answer. " +
                        "Use format: Action: TOOL.action {params} or Final Answer: your response"
                    ));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[ReAct] Error in turn " + turn, e);
                lastStep = ReActStep.error(turn, e.getMessage(), null);
                steps.add(lastStep);
                notifyStepListener(lastStep);
                break;
            }
        }

        if (finalAnswer == null && (lastStep == null || !lastStep.isTerminal())) {
            LOG.warning("[ReAct] Max turns reached without final answer");

            messages.add(Message.user(
                "Maximum iterations reached. Please provide your Final Answer now based on what you have learned."
            ));

            try {
                String forcedOutput = callLlm(messages);
                ParsedOutput parsed = parseOutput(forcedOutput);
                finalAnswer = parsed.finalAnswer != null ? parsed.finalAnswer : forcedOutput;
                lastStep = ReActStep.finalAnswer(turn + 1, finalAnswer, forcedOutput);
                steps.add(lastStep);
                notifyStepListener(lastStep);
            } catch (Exception e) {
                LOG.warning("[ReAct] Failed to get forced final answer: " + e.getMessage());
            }
        }

        Duration totalDuration = Duration.between(startTime, Instant.now());
        LOG.info(() -> String.format("[ReAct] Completed in %d steps, %dms",
            steps.size(), totalDuration.toMillis()));

        return new ReActResult(
            steps,
            finalAnswer,
            persona,
            totalDuration,
            turn <= maxTurns
        );
    }

    public ToolResponse executeTool(ToolRequest request) {
        return executeAction(request);
    }

    public void setStepListener(Consumer<ReActStep> listener) {
        this.stepListener = listener;
    }

    public void setSessionContext(String key, Object value) {
        sessionContext.put(key, value);
    }

    public Object getSessionContext(String key) {
        return sessionContext.get(key);
    }

    public void clearSessionContext() {
        sessionContext.clear();
    }

    private String buildSystemPrompt(Persona persona) {
        StringBuilder sb = new StringBuilder();

        if (persona != null) {
            sb.append(persona.generateSystemPrompt()).append("\n\n");
        }

        sb.append("""
            You are an AI assistant that uses the ReAct (Reasoning and Acting) pattern.

            For each step, you should:
            1. **Thought**: Reason about what you need to do next
            2. **Action**: Execute a tool if needed, OR
            3. **Final Answer**: Provide the final response if the task is complete

            Format your responses as:
            ```
            Thought: [Your reasoning about the current situation and next step]
            Action: TOOL.action {param1: value1, param2: value2}
            ```

            OR when you have the final answer:
            ```
            Thought: [Your reasoning]
            Final Answer: [Your complete response to the user]
            ```
            """);

        if (toolRegistry != null) {
            sb.append("\n## Available Tools\n");
            sb.append(toolRegistry.generateToolsSummary());
        }

        return sb.toString();
    }

    private String callLlm(List<Message> messages) {
        GenerationRequest request = GenerationRequest.builder()
            .messages(messages)
            .maxTokens(maxTokens)
            .temperature(temperature)
            .build();

        GenerationResponse response = llmClient.generate(request);
        return response.text();
    }

    private ParsedOutput parseOutput(String output) {
        ParsedOutput parsed = new ParsedOutput();
        parsed.rawOutput = output;

        Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(output);
        if (finalMatcher.find()) {
            parsed.finalAnswer = finalMatcher.group(1).trim();
            return parsed;
        }

        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(output);
        if (thoughtMatcher.find()) {
            parsed.thought = thoughtMatcher.group(1).trim();
        }

        Matcher actionMatcher = ACTION_PATTERN.matcher(output);
        if (actionMatcher.find()) {
            String tool = actionMatcher.group(1);
            String action = actionMatcher.group(2);
            String params = actionMatcher.group(3) != null ? actionMatcher.group(3) :
                           (actionMatcher.group(4) != null ? actionMatcher.group(4) : "");

            Map<String, Object> paramMap = parseParams(params);
            parsed.action = ToolRequest.of(tool, action, paramMap);
            return parsed;
        }

        Matcher jsonMatcher = JSON_ACTION_PATTERN.matcher(output);
        if (jsonMatcher.find()) {
            String tool = jsonMatcher.group(1);
            String action = jsonMatcher.group(2);
            String params = jsonMatcher.group(3) != null ? jsonMatcher.group(3) : "{}";

            Map<String, Object> paramMap = parseJsonParams(params);
            parsed.action = ToolRequest.of(tool, action, paramMap);
            return parsed;
        }

        return parsed;
    }

    private Map<String, Object> parseParams(String params) {
        Map<String, Object> result = new HashMap<>();
        if (params == null || params.isBlank()) {
            return result;
        }

        Pattern paramPattern = Pattern.compile("(\\w+)\\s*:\\s*([^,}]+)");
        Matcher matcher = paramPattern.matcher(params);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    private Map<String, Object> parseJsonParams(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isBlank() || json.equals("{}")) {
            return result;
        }

        Pattern pattern = Pattern.compile("\"(\\w+)\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d.]+)|true|false|null)");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String strValue = matcher.group(2);
            String numValue = matcher.group(3);

            if (strValue != null) {
                result.put(key, strValue);
            } else if (numValue != null) {
                if (numValue.contains(".")) {
                    result.put(key, Double.parseDouble(numValue));
                } else {
                    result.put(key, Long.parseLong(numValue));
                }
            }
        }
        return result;
    }

    private ToolResponse executeAction(ToolRequest request) {
        if (toolRegistry == null) {
            return ToolResponse.error("No tool registry configured");
        }

        Instant start = Instant.now();
        String toolName = request.toolName();
        String actionName = request.action();

        LOG.fine(() -> "[ReAct] Executing: " + toolName + "." + actionName);

        try {
            Optional<ToolSkill> skillOpt = toolRegistry.findSkill(toolName);
            if (skillOpt.isEmpty()) {
                return ToolResponse.error("Tool not found: " + toolName, request.requestId(),
                    Duration.between(start, Instant.now()).toMillis());
            }

            ToolSkill skill = skillOpt.get();

            Optional<Method> methodOpt = toolRegistry.findAction(toolName, actionName);
            if (methodOpt.isEmpty()) {
                return ToolResponse.error("Action not found: " + actionName + " in " + toolName,
                    request.requestId(), Duration.between(start, Instant.now()).toMillis());
            }

            Method method = methodOpt.get();

            Object[] args = prepareMethodArgs(method, request.params());

            Object result = method.invoke(skill, args);

            long duration = Duration.between(start, Instant.now()).toMillis();

            if (result instanceof ToolResponse tr) {
                return tr.withRequestId(request.requestId()).withExecutionTime(duration);
            } else if (result instanceof String s) {
                return ToolResponse.success(s, request.requestId(), duration);
            } else if (result != null) {
                return ToolResponse.success(result.toString(), request.requestId(), duration);
            } else {
                return ToolResponse.success("Action completed", request.requestId(), duration);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[ReAct] Tool execution failed", e);
            return ToolResponse.fromException(e, request.requestId(),
                Duration.between(start, Instant.now()).toMillis());
        }
    }

    private Object[] prepareMethodArgs(Method method, Map<String, Object> params) {
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] parameters = method.getParameters();

        if (paramTypes.length == 0) {
            return new Object[0];
        }

        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            Object value = params.get(paramName);

            if (value == null) {
                for (String key : params.keySet()) {
                    if (key.equalsIgnoreCase(paramName)) {
                        value = params.get(key);
                        break;
                    }
                }
            }

            args[i] = convertValue(value, paramTypes[i]);
        }
        return args;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        String strValue = value.toString();

        if (targetType == String.class) {
            return strValue;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(strValue);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(strValue);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(strValue);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(strValue);
        }

        return value;
    }

    private void notifyStepListener(ReActStep step) {
        if (stepListener != null) {
            try {
                stepListener.accept(step);
            } catch (Exception e) {
                LOG.warning("[ReAct] Step listener error: " + e.getMessage());
            }
        }
    }

    private static String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() <= len ? s : s.substring(0, len - 3) + "...";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ReActEngine minimal(LlmClient llmClient) {
        return builder().llmClient(llmClient).build();
    }

    public static ReActEngine withTools(LlmClient llmClient, ToolRegistry toolRegistry) {
        return builder()
            .llmClient(llmClient)
            .toolRegistry(toolRegistry)
            .build();
    }

    public static final class Builder {
        private LlmClient llmClient;
        private ToolRegistry toolRegistry;
        private ReflexionGuard reflexionGuard;
        private CognitiveEngine cognitiveEngine;
        private int maxTurns = 5;
        private int maxTokens = 2048;
        private double temperature = 0.7;
        private boolean enableReflexion = true;
        private boolean enablePersonas = true;

        private Builder() {}

        public Builder llmClient(LlmClient client) {
            this.llmClient = client;
            return this;
        }

        public Builder toolRegistry(ToolRegistry registry) {
            this.toolRegistry = registry;
            return this;
        }

        public Builder reflexionGuard(ReflexionGuard guard) {
            this.reflexionGuard = guard;
            return this;
        }

        public Builder cognitiveEngine(CognitiveEngine engine) {
            this.cognitiveEngine = engine;
            return this;
        }

        public Builder maxTurns(int turns) {
            this.maxTurns = turns;
            return this;
        }

        public Builder maxTokens(int tokens) {
            this.maxTokens = tokens;
            return this;
        }

        public Builder temperature(double temp) {
            this.temperature = temp;
            return this;
        }

        public Builder enableReflexion(boolean enable) {
            this.enableReflexion = enable;
            return this;
        }

        public Builder enablePersonas(boolean enable) {
            this.enablePersonas = enable;
            return this;
        }

        public ReActEngine build() {
            return new ReActEngine(this);
        }
    }

    private static class ParsedOutput {
        String rawOutput;
        String thought;
        ToolRequest action;
        String finalAnswer;

        boolean hasAction() {
            return action != null;
        }

        boolean isFinalAnswer() {
            return finalAnswer != null && !finalAnswer.isBlank();
        }
    }
}
