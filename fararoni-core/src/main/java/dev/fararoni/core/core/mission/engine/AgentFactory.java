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
package dev.fararoni.core.core.mission.engine;

import dev.fararoni.bus.agent.api.bus.SovereignEnvelope;
import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.core.agent.dynamic.LlmInferenceProvider;
import dev.fararoni.core.core.agent.dynamic.StreamingLlmInferenceProvider;
import dev.fararoni.core.core.agents.ReactiveSwarmAgent;
import dev.fararoni.core.core.llm.StreamingLlmCallback;
import dev.fararoni.core.core.mission.events.FileWriteIntentEvent;
import dev.fararoni.core.core.mission.model.AgentTemplate;
import dev.fararoni.core.core.utils.MultiFileParser;
import dev.fararoni.core.core.utils.ProjectContextExtractor;
import dev.fararoni.core.core.runtime.sandbox.DockerSandbox;
import dev.fararoni.core.core.safety.mission.MissionPostChecker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class AgentFactory {
    private static final Logger LOG = Logger.getLogger(AgentFactory.class.getName());

    private static final Map<String, BiFunction<AgentTemplate, SovereignEventBus, ReactiveSwarmAgent>>
        REGISTERED_CONSTRUCTORS = new ConcurrentHashMap<>();

    private AgentFactory() {
    }

    public static void register(
            String role,
            BiFunction<AgentTemplate, SovereignEventBus, ReactiveSwarmAgent> constructor) {
        REGISTERED_CONSTRUCTORS.put(role, constructor);
        LOG.info("AgentFactory: Registered constructor for role '" + role + "'");
    }

    public static boolean hasRegistered(String role) {
        return REGISTERED_CONSTRUCTORS.containsKey(role);
    }

    public static ReactiveSwarmAgent createFromTemplate(
            AgentTemplate template,
            SovereignEventBus bus,
            LlmInferenceProvider llm) {
        String role = template.role();

        BiFunction<AgentTemplate, SovereignEventBus, ReactiveSwarmAgent> constructor =
            REGISTERED_CONSTRUCTORS.get(role);

        if (constructor != null) {
            LOG.info("Creating agent '" + template.id() +
                     "' using registered constructor for role '" + role + "'");
            return constructor.apply(template, bus);
        }

        LOG.info("Creating generic agent '" + template.id() +
                 "' for role '" + role + "' (LLM-enabled)");
        return new GenericReactiveAgent(template, bus, llm);
    }

    public static class GenericReactiveAgent extends ReactiveSwarmAgent {
        private final LlmInferenceProvider llm;
        private final SovereignEventBus eventBus;

        public GenericReactiveAgent(AgentTemplate template, SovereignEventBus bus, LlmInferenceProvider llm) {
            super(template, bus);
            this.llm = llm;
            this.eventBus = bus;
        }

        @Override
        protected AgentResult processTask(SovereignEnvelope<?> envelope) {
            logThinking("Processing task with capabilities: " + getCapabilities());

            Object payload = envelope.payload();
            String taskDescription = extractTaskContext(payload);

            if (MultiFileParser.isMultiFile(taskDescription)) {
                LOG.info("BYPASS COGNITIVO: Input ya contiene >>>FILE:");
                System.out.println("[BUILDER] [START] BYPASS COGNITIVO: Codigo ya generado por Blueprint");
                System.out.println("[BUILDER] Saltando inferencia LLM -> Escritura directa");

                return processFileWriteIntents(taskDescription, envelope.correlationId());
            }

            if (llm == null) {
                LOG.warning("GenericAgent '" + getAgentId() + "' has no LLM provider");
                return AgentResult.success("Task acknowledged (no LLM available)");
            }

            if (requiresStrongModel() && isWeakModel()) {
                LOG.severe("[SECURITY-GUARD] Intento de escritura con modelo débil bloqueado.");
                System.out.println("[CIRCUIT-BREAKER] [WARN] CIRCUIT BREAKER COGNITIVO ACTIVADO");
                System.out.println("[CIRCUIT-BREAKER]    Agente: " + getAgentId());
                System.out.println("[CIRCUIT-BREAKER]    Capabilities: " + getCapabilities());
                System.out.println("[CIRCUIT-BREAKER]    Modelo 1.5B NO puede generar código/rutas.");
                throw new CognitiveSafetyException(
                    "Capacidad de escritura denegada: Modelo débil (1.5B) " +
                    "no apto para generación de rutas o lógica de archivos. " +
                    "Agente '" + getAgentId() + "' requiere modelo cognitivo fuerte (7B+)."
                );
            }

            try {
                String systemPrompt = getSystemPrompt();
                String response;

                boolean canUseStreaming = llm instanceof StreamingLlmInferenceProvider;
                boolean needsCodeGeneration = requiresStrongModel();

                if (canUseStreaming && needsCodeGeneration) {
                    try {
                        LOG.info("Usando STREAMING PARALELO para: " + getAgentId());
                        System.out.println("[STREAMING-PARALLEL] [START] Modo paralelo activado para: " + getAgentId());

                        StreamingLlmInferenceProvider streamingLlm = (StreamingLlmInferenceProvider) llm;

                        Path outputDir = ProjectContextExtractor.extractProjectRoot(
                            Set.of(), Paths.get(System.getProperty("user.dir")));

                        final String agentId = getAgentId();
                        response = streamingLlm.inferStreamingParallel(
                            systemPrompt,
                            taskDescription,
                            outputDir,
                            new StreamingLlmCallback() {
                                @Override
                                public void onToken(String token) {
                                }

                                @Override
                                public void onFileDetected(String path, String content) {
                                    LOG.info("[STREAMING] Archivo detectado: " + path);
                                    System.out.println("[STREAMING-WRITE] " + path);
                                }

                                @Override
                                public void onMetrics(double tps, int totalTokens) {
                                    LOG.fine(() -> String.format(
                                        "[STREAMING] Agent %s: %.1f tok/s | %d tokens",
                                        agentId, tps, totalTokens));
                                }

                                @Override
                                public void onComplete(String fullResponse) {
                                    LOG.info("[STREAMING] Completado para: " + agentId);
                                    System.out.println("[STREAMING-PARALLEL] [OK] " + agentId + " completado");
                                }

                                @Override
                                public void onError(Throwable error) {
                                    LOG.severe("[STREAMING] Error en " + agentId + ": " + error.getMessage());
                                    System.out.println("[STREAMING-PARALLEL] [ERROR] " + error.getMessage());
                                }
                            }
                        );
                    } catch (Exception streamingException) {
                        LOG.log(Level.WARNING,
                            "[FALLBACK] Error en Pipeline Paralelo para " + getAgentId() +
                            ". Intentando recuperación en modo BATCH...", streamingException);

                        System.out.println("[GRACEFUL-DEGRADATION] [WARN] Streaming fallo para: " + getAgentId());
                        System.out.println("[GRACEFUL-DEGRADATION] Degradando a modo BATCH...");
                        System.out.println("[GRACEFUL-DEGRADATION]    Causa: " + streamingException.getMessage());

                        response = llm.infer(systemPrompt, taskDescription);

                        System.out.println("[GRACEFUL-DEGRADATION] [OK] Recuperacion BATCH completada");
                        LOG.info("[FALLBACK] Recuperación exitosa en modo BATCH para: " + getAgentId());
                    }
                } else {
                    LOG.info("Camino tradicional (BATCH): Invocando LLM para generacion");
                    response = llm.infer(systemPrompt, taskDescription);
                }

                logAction("LLM inference completed for agent '" + getAgentId() + "'");

                boolean needsStrong = requiresStrongModel();
                boolean isWeakResponse = isWeakModelFallbackResponse(response);

                LOG.info("Post-inference check: needsStrong=" + needsStrong +
                         ", isWeakResponse=" + isWeakResponse + ", agent=" + getAgentId());

                if (needsStrong && isWeakResponse) {
                    LOG.severe("[SECURITY-GUARD] Fallback a modelo débil detectado post-inferencia.");
                    System.out.println("[CIRCUIT-BREAKER] [WARN] CIRCUIT BREAKER POST-INFERENCIA");
                    System.out.println("[CIRCUIT-BREAKER]    Agente: " + getAgentId());
                    System.out.println("[CIRCUIT-BREAKER]    Turtle (32B) no respondió, se usó Rabbit (1.5B)");
                    System.out.println("[CIRCUIT-BREAKER]    Código generado NO confiable. Abortando.");
                    throw new CognitiveSafetyException(
                        "Fallback a modelo débil detectado. El código generado por Rabbit (1.5B) " +
                        "NO es confiable para agente '" + getAgentId() + "'. " +
                        "Verifica disponibilidad de Turtle (32B/7B)."
                    );
                }

                if (MultiFileParser.isMultiFile(response)) {
                    List<String> allowed = template != null ? template.allowedTools() : null;
                    boolean hasWritePermission = allowed != null && allowed.contains("fs_write");

                    if (!hasWritePermission) {
                        LOG.warning(() -> String.format(
                            "[SECURITY-GATEKEEPER] Agente '%s' intentó bypass de escritura sin 'fs_write'. " +
                            "Delegando código al siguiente paso (Builder).", getAgentId()));
                        System.out.println("[SECURITY] Agente '" + getAgentId() +
                            "' sin permiso fs_write - Codigo delegado al Builder");

                        return AgentResult.success(response);
                    }

                    LOG.info("[SECURITY-GATEKEEPER] Agente '" + getAgentId() +
                             "' con fs_write autorizado - Procediendo a escritura");
                    return processFileWriteIntents(response, envelope.correlationId());
                }

                return AgentResult.success(response);
            } catch (CognitiveSafetyException cse) {
                LOG.severe("CognitiveSafetyException propagandose: " + cse.getMessage());
                System.out.println("[CIRCUIT-BREAKER] [ERROR] CIRCUIT BREAKER ACTIVADO - Abortando agente");
                return AgentResult.failure("CIRCUIT_BREAKER: " + cse.getMessage());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "LLM inference failed", e);
                return AgentResult.failure("LLM inference failed: " + e.getMessage());
            }
        }

        private AgentResult processFileWriteIntents(String llmResponse, String correlationId) {
            logAction("Builder mode: Detectado >>>FILE: - publicando eventos...");

            Map<String, String> files = MultiFileParser.parse(llmResponse);

            if (files.isEmpty()) {
                LOG.warning("No files extracted from LLM response");
                return AgentResult.success("Respuesta procesada pero no se detectaron archivos");
            }

            int publishedCount = 0;
            StringBuilder report = new StringBuilder();
            report.append("Archivos encolados para validación y escritura:\n");

            for (Map.Entry<String, String> entry : files.entrySet()) {
                String path = entry.getKey();
                String content = entry.getValue();

                logAction("Publishing FileWriteIntentEvent for: " + path);

                FileWriteIntentEvent intent = FileWriteIntentEvent.create(
                    getAgentId(),
                    correlationId,
                    path,
                    content
                );

                try {
                    eventBus.publish(
                        FileWriteIntentEvent.TOPIC,
                        SovereignEnvelope.create("agent", intent)
                    );
                    publishedCount++;
                    report.append("  - ").append(path).append("\n");
                    LOG.info("FileWriteIntentEvent published: " + path);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to publish event for: " + path, e);
                    report.append("  [ERROR] ").append(path).append(" (error: ").append(e.getMessage()).append(")\n");
                }
            }

            String summary = String.format(
                "Builder: %d archivos encolados para escritura segura\n%s",
                publishedCount, report
            );

            System.out.println("[BUILDER] " + publishedCount + " archivos enviados a FileSystemIntentListener");

            Path workingDir = Paths.get(System.getProperty("user.dir"));
            Path projectRoot = ProjectContextExtractor.extractProjectRoot(files.keySet(), workingDir);

            LOG.info("Proyecto detectado para validacion: " + projectRoot);
            System.out.println("[BUILDER] Proyecto: " + projectRoot.getFileName());

            String compileResult = validateWithDocker(projectRoot);
            if (compileResult != null) {
                summary += "\n\n" + compileResult;
            }

            return AgentResult.success(summary);
        }

        @Override
        protected AgentResult compensateTask(SovereignEnvelope<?> envelope) {
            logAction("Compensation requested for generic agent (no-op)");
            return AgentResult.success("No compensation needed for generic agent");
        }

        private boolean requiresStrongModel() {
            List<String> caps = getCapabilities();
            String role = getAgentId().toLowerCase();

            return caps.contains("code_generation")
                || caps.contains("java_refactoring")
                || caps.contains("technical_design")
                || caps.contains("architecture_design")
                || role.contains("builder")
                || role.contains("constructor")
                || role.contains("blueprint");
        }

        private boolean isWeakModelFallbackResponse(String response) {
            if (response == null) return false;

            boolean usedRabbit = response.contains("Usando Rabbit");

            boolean fallbackHeader = response.contains("[SISTEMA: Experto no disponible");

            if (usedRabbit) {
                LOG.warning("Detectado fallback a Rabbit (1.5B) - Codigo NO confiable");
                return true;
            }

            return false;
        }

        private boolean isWeakModel() {
            if (llm == null) return false;

            String modelInfo = llm.toString().toLowerCase();

            boolean isWeak = modelInfo.contains("1.5b")
                          || modelInfo.contains("0.5b")
                          || modelInfo.contains(":1b")
                          || modelInfo.contains("tiny");

            if (isWeak) {
                LOG.info("Modelo DEBIL detectado (< 7B): " + modelInfo);
                return true;
            }

            String envModel = System.getenv("FARARONI_RABBIT_MODEL");
            if (envModel != null) {
                String lower = envModel.toLowerCase();
                if (lower.contains("1.5b") || lower.contains("0.5b") || lower.contains(":1b")) {
                    LOG.info("Modelo DEBIL en env RABBIT: " + envModel);
                    return true;
                }
            }

            String sysProp = System.getProperty("fararoni.rabbit.model");
            if (sysProp != null && sysProp.toLowerCase().contains("1.5b")) {
                LOG.info("Modelo DEBIL en sysprop: " + sysProp);
                return true;
            }

            LOG.fine("Modelo FUERTE detectado (>= 7B): " + modelInfo);
            return false;
        }

        private String validateWithDocker(Path projectRoot) {
            if (projectRoot == null) {
                LOG.fine("projectRoot es null, saltando validacion");
                return null;
            }

            if (!MissionPostChecker.isHybridModeEnabled()) {
                LOG.info("[SECURITY] Política LSP_ONLY activa. Docker omitido por configuración de seguridad.");
                return null;
            }

            if (!DockerSandbox.isDockerAvailable()) {
                LOG.warning("[INFRA] Modo Híbrido solicitado pero Docker no está disponible o no responde.");
                LOG.warning("[INFRA] Verifique: 1) Docker instalado, 2) Daemon corriendo, 3) Permisos de usuario");
                return null;
            }

            LOG.info("[INFRA] Validación Docker habilitada y disponible. Procediendo con compilación en contenedor.");

            String image;
            String[] compileCmd;

            if (projectRoot.resolve("pom.xml").toFile().exists()) {
                image = DockerSandbox.IMAGE_JAVA_21;
                compileCmd = new String[]{"mvn", "compile", "-q", "-DskipTests"};
            } else if (projectRoot.resolve("build.gradle").toFile().exists()) {
                image = DockerSandbox.IMAGE_JAVA_21;
                compileCmd = new String[]{"gradle", "compileJava", "-q"};
            } else if (projectRoot.resolve("package.json").toFile().exists()) {
                image = DockerSandbox.IMAGE_NODE_20;
                compileCmd = new String[]{"npm", "run", "build"};
            } else if (projectRoot.resolve("requirements.txt").toFile().exists()) {
                LOG.info("Proyecto Python detectado (sin compilacion Docker)");
                return "Python: Validacion de sintaxis pendiente";
            } else if (projectRoot.resolve("go.mod").toFile().exists()) {
                image = "golang:1.22-alpine";
                compileCmd = new String[]{"go", "build", "./..."};
            } else {
                LOG.fine("Tipo de proyecto no reconocido en: " + projectRoot);
                return null;
            }

            LOG.info("Compilando en: " + projectRoot);
            System.out.println("[DOCKER] Compilando: " + projectRoot.getFileName());

            try {
                DockerSandbox sandbox = new DockerSandbox(image);
                sandbox.startSandbox(projectRoot);

                DockerSandbox.SandboxResult result = sandbox.execute(60, compileCmd);
                sandbox.destroySandbox();

                if (result.isSuccess()) {
                    LOG.info("Compilacion exitosa en " + projectRoot.getFileName());
                    System.out.println("[DOCKER] [OK] Compilacion exitosa (" + result.durationMs() + "ms)");
                    return "Docker: Compilacion exitosa en " + projectRoot.getFileName();
                } else {
                    LOG.warning("Errores en " + projectRoot.getFileName() + ":\n" + result.stderr());
                    System.out.println("[DOCKER] [ERROR] Errores de compilacion en " + projectRoot.getFileName());
                    return "Docker: Errores en " + projectRoot.getFileName() + "\n" + result.stderr();
                }
            } catch (DockerSandbox.SandboxException e) {
                LOG.warning("Error Docker: " + e.getMessage());
                return null;
            }
        }

        private String extractTaskContext(Object payload) {
            if (payload == null) {
                return "No task description";
            }

            if (payload instanceof SovereignMissionEngine.TaskPayload taskPayload) {
                String context = taskPayload.contextJson();
                if (context != null && !context.isBlank()) {
                    LOG.info("Extraido contextJson de TaskPayload (" +
                             context.length() + " chars)");
                    return context;
                }
                String override = taskPayload.systemPromptOverride();
                if (override != null && !override.isBlank()) {
                    return override;
                }
                return "TaskPayload sin contexto";
            }

            if (payload instanceof java.util.Map<?, ?> map) {
                Object contextJson = map.get("contextJson");
                if (contextJson instanceof String ctx && !ctx.isBlank()) {
                    LOG.info("Extraido contextJson de Map (" + ctx.length() + " chars)");
                    return ctx;
                }
                Object override = map.get("systemPromptOverride");
                if (override instanceof String ovr && !ovr.isBlank()) {
                    return ovr;
                }
            }

            String result = payload.toString();
            LOG.fine("Usando payload.toString() (" + result.length() + " chars)");
            return result;
        }
    }
}
