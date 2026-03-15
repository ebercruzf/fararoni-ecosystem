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
package dev.fararoni.core.core.safety.mission;

import dev.fararoni.core.core.runtime.sandbox.DockerSandbox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class MissionPostChecker {
    private static final Logger LOG = Logger.getLogger(MissionPostChecker.class.getName());

    public static final String ENV_VALIDATION_MODE = "FARARONI_VALIDATION_MODE";

    private static final int DEFAULT_TIMEOUT_MINUTES = 5;

    private final int timeoutMinutes;

    public enum ValidationMode {
        LSP_ONLY,

        HYBRID
    }

    public MissionPostChecker() {
        this(DEFAULT_TIMEOUT_MINUTES);
    }

    public MissionPostChecker(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : DEFAULT_TIMEOUT_MINUTES;
    }

    public MissionReport verifyFullProject(Path projectPath, String techStack) {
        if (projectPath == null || !Files.isDirectory(projectPath)) {
            return MissionReport.infrastructureError("Ruta de proyecto inválida: " + projectPath);
        }

        long startTime = System.currentTimeMillis();
        ValidationMode mode = getValidationMode();

        LOG.info("[POST-CHECK] Verificando proyecto: " + projectPath);
        LOG.info("[POST-CHECK] Modo de validación: " + mode);

        ProjectType detectedType = detectProjectType(projectPath);
        if (detectedType == ProjectType.UNKNOWN) {
            LOG.info("[POST-CHECK] Tipo de proyecto no reconocido, omitiendo verificación");
            return MissionReport.skipped();
        }

        String actualTechStack = detectedType.name().toLowerCase();
        LOG.info("[POST-CHECK] Proyecto detectado: " + actualTechStack);

        MissionReport localResult = executeBuild(projectPath, detectedType, startTime);

        if (localResult.isSuccess()) {
            LOG.info(localResult.toLogString());
            return localResult;
        }

        if (mode == ValidationMode.HYBRID && DockerSandbox.isDockerAvailable()) {
            LOG.info("[POST-CHECK] Modo HYBRID: Intentando validación con Docker...");
            MissionReport dockerResult = executeDockerBuild(projectPath, detectedType, startTime);

            if (dockerResult.isSuccess()) {
                LOG.info("[POST-CHECK] Docker validación exitosa (local falló pero Docker OK)");
                return dockerResult;
            }

            LOG.warning("[POST-CHECK] Docker también falló");
            return dockerResult;
        }

        LOG.warning(localResult.toLogString());
        return localResult;
    }

    public MissionReport verifyFullProject(String projectPath, String techStack) {
        return verifyFullProject(Path.of(projectPath), techStack);
    }

    public enum ProjectType {
        MAVEN("pom.xml", "./mvnw", "compile", "-q", "-DskipTests"),
        MAVEN_NO_WRAPPER("pom.xml", "mvn", "compile", "-q", "-DskipTests"),
        GRADLE("build.gradle", "./gradlew", "compileJava", "-q"),
        NPM("package.json", "npm", "run", "build"),
        UNKNOWN(null);

        private final String marker;
        private final String[] command;

        ProjectType(String marker, String... command) {
            this.marker = marker;
            this.command = command;
        }

        public String getMarker() { return marker; }
        public String[] getCommand() { return command; }
    }

    public ProjectType detectProjectType(Path projectPath) {
        if (Files.exists(projectPath.resolve("pom.xml"))) {
            if (!Files.exists(projectPath.resolve("mvnw"))) {
                return ProjectType.MAVEN_NO_WRAPPER;
            }
            return ProjectType.MAVEN;
        }
        if (Files.exists(projectPath.resolve("build.gradle"))) {
            return ProjectType.GRADLE;
        }
        if (Files.exists(projectPath.resolve("package.json"))) {
            return ProjectType.NPM;
        }
        return ProjectType.UNKNOWN;
    }

    private MissionReport executeBuild(Path projectPath, ProjectType type, long startTime) {
        String techStack = type.name().toLowerCase();

        try {
            ProcessBuilder pb = new ProcessBuilder(type.getCommand());
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);

            LOG.info("[POST-CHECK] Ejecutando: " + String.join(" ", type.getCommand()));
            System.out.println("[BUILD] Compilando proyecto " + techStack + "...");

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > 50000) {
                        output.append("... [truncado]");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                System.out.println("[BUILD] [TIMEOUT] Timeout después de " + timeoutMinutes + " minutos");
                return MissionReport.timeout(techStack, timeoutMinutes);
            }

            if (process.exitValue() == 0) {
                System.out.println("[BUILD] [OK] Compilación exitosa (" + duration + "ms)");
                return MissionReport.success(techStack, duration);
            }

            System.out.println("[BUILD] [ERROR] Errores de compilación detectados");
            return MissionReport.failure(output.toString(), techStack, duration);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[POST-CHECK] Error ejecutando build", e);
            return MissionReport.infrastructureError(e.getMessage());
        }
    }

    private MissionReport executeDockerBuild(Path projectPath, ProjectType type, long startTime) {
        String techStack = type.name().toLowerCase();

        try {
            String image = switch (type) {
                case MAVEN, GRADLE -> DockerSandbox.IMAGE_JAVA_21;
                case NPM -> DockerSandbox.IMAGE_NODE_20;
                default -> "alpine:latest";
            };

            DockerSandbox sandbox = new DockerSandbox(image);
            sandbox.startSandbox(projectPath);

            DockerSandbox.SandboxResult result = sandbox.execute(
                (int) TimeUnit.MINUTES.toSeconds(timeoutMinutes),
                type.getCommand()
            );

            sandbox.destroySandbox();

            long duration = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                return MissionReport.success(techStack + " (docker)", duration);
            }

            return MissionReport.failure(
                result.getCombinedOutput(),
                techStack + " (docker)",
                duration
            );
        } catch (DockerSandbox.SandboxException e) {
            LOG.warning("[POST-CHECK] Error en Docker: " + e.getMessage());
            return MissionReport.infrastructureError("Docker: " + e.getMessage());
        }
    }

    public static ValidationMode getValidationMode() {
        String envMode = System.getenv(ENV_VALIDATION_MODE);
        if (envMode != null && !envMode.isBlank()) {
            if ("HYBRID".equalsIgnoreCase(envMode.trim())) {
                return ValidationMode.HYBRID;
            }
            return ValidationMode.LSP_ONLY;
        }

        String propMode = System.getProperty("fararoni.validation.mode");
        if (propMode != null && !propMode.isBlank()) {
            if ("HYBRID".equalsIgnoreCase(propMode.trim())) {
                return ValidationMode.HYBRID;
            }
            return ValidationMode.LSP_ONLY;
        }

        return ValidationMode.LSP_ONLY;
    }

    public static boolean isHybridModeEnabled() {
        return getValidationMode() == ValidationMode.HYBRID;
    }

    public static void printModeInfo() {
        ValidationMode mode = getValidationMode();
        boolean dockerAvailable = DockerSandbox.isDockerAvailable();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║            FARARONI - MODO DE VALIDACIÓN DE CÓDIGO                       ║");
        System.out.println("║                                                                          ║");
        System.out.println("║  ¿QUÉ ES ESTO?                                                           ║");
        System.out.println("║  Fararoni verifica que el código generado por la IA compile              ║");
        System.out.println("║  correctamente ANTES de darlo por bueno. Hay dos estrategias:            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                          ║");
        System.out.println("║  MODO ACTUAL: " + padRight(mode.name(), 58) + "                       ║");
        System.out.println("║                                                                          ║");

        if (mode == ValidationMode.LSP_ONLY) {
            System.out.println("║  ESTRATEGIA: Compilación Local Únicamente                               ║");
            System.out.println("║  ─────────────────────────────────────────────────────────────────────  ║");
            System.out.println("║  • Ejecuta: mvn compile / gradle build / npm build                      ║");
            System.out.println("║  • Usa el compilador instalado en tu máquina (Java, Node, etc.)         ║");
            System.out.println("║  • Docker NO se usa nunca (más rápido, menos recursos)                  ║");
            System.out.println("║  • Ideal para: desarrollo local, máquinas con pocos recursos            ║");
        } else {
            System.out.println("║  ESTRATEGIA: Híbrida (Local + Docker Fallback)                          ║");
            System.out.println("║  ─────────────────────────────────────────────────────────────────────  ║");
            System.out.println("║  • PRIMERO intenta compilación local (mvn compile)                      ║");
            System.out.println("║  • SI FALLA y Docker está disponible → reintenta en contenedor          ║");
            System.out.println("║  • El contenedor tiene entorno aislado y controlado                     ║");
            System.out.println("║  • Ideal para: CI/CD, entornos enterprise, diagnóstico profundo         ║");
            System.out.println("║                                                                         ║");
            System.out.println("║  Docker disponible: " + padRight(dockerAvailable ? "SI [OK]" : "NO [FAIL] (fallback desactivado)", 50) + "║");
        }

        System.out.println("║                                                                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  ¿CÓMO CAMBIAR EL MODO?                                                  ║");
        System.out.println("║                                                                          ║");
        System.out.println("║  # Opción 1: Solo compilación local (default, recomendado)               ║");
        System.out.println("║  export FARARONI_VALIDATION_MODE=LSP_ONLY                                ║");
        System.out.println("║                                                                          ║");
        System.out.println("║  # Opción 2: Híbrido con Docker como respaldo                            ║");
        System.out.println("║  export FARARONI_VALIDATION_MODE=HYBRID                                  ║");
        System.out.println("║                                                                          ║");
        System.out.println("║  # Para que sea permanente, agrégalo a tu ~/.bashrc o ~/.zshrc           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
