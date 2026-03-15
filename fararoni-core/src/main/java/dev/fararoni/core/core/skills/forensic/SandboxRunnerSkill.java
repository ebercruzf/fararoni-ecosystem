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
package dev.fararoni.core.core.skills.forensic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SandboxRunnerSkill {
    private static final Logger LOG = Logger.getLogger(SandboxRunnerSkill.class.getName());

    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    public String runEphemeralJava(String javaCode) {
        return runEphemeralJava(javaCode, DEFAULT_TIMEOUT_SECONDS);
    }

    public String runEphemeralJava(String javaCode, int timeoutSeconds) {
        try {
            Path tempDir = Files.createTempDirectory("fararoni_sre_lab");
            Path sourceFile = tempDir.resolve("ReproCase.java");

            LOG.fine(() -> "[SandboxRunner] Creando laboratorio en: " + tempDir);

            Files.writeString(sourceFile, javaCode);

            ProcessBuilder pb = new ProcessBuilder(
                "java", "--enable-preview", sourceFile.toString()
            );
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                p.destroyForcibly();
                LOG.warning(() -> "[SandboxRunner] TIMEOUT: Script abortado después de " + timeoutSeconds + "s");
                return "TIMEOUT: La prueba tardó demasiado y fue abortada.";
            }

            String output = new String(p.getInputStream().readAllBytes());

            try {
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                LOG.fine(() -> "[SandboxRunner] No se pudo limpiar temp: " + e.getMessage());
            }

            LOG.info(() -> "[SandboxRunner] Ejecución completada. Exit code: " + p.exitValue());
            return output;
        } catch (Exception e) {
            LOG.severe(() -> "[SandboxRunner] ERROR: " + e.getMessage());
            return "ERROR DE EJECUCIÓN: " + e.getMessage();
        }
    }

    public String runEphemeralPython(String pythonCode) {
        try {
            Path tempDir = Files.createTempDirectory("fararoni_sre_lab");
            Path sourceFile = tempDir.resolve("repro_case.py");

            Files.writeString(sourceFile, pythonCode);

            ProcessBuilder pb = new ProcessBuilder("python3", sourceFile.toString());
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            boolean finished = p.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                p.destroyForcibly();
                return "TIMEOUT: Script Python abortado.";
            }

            String output = new String(p.getInputStream().readAllBytes());

            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(tempDir);

            return output;
        } catch (Exception e) {
            return "ERROR DE EJECUCIÓN PYTHON: " + e.getMessage();
        }
    }

    public String runShellCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            boolean finished = p.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                p.destroyForcibly();
                return "TIMEOUT: Comando shell abortado.";
            }

            return new String(p.getInputStream().readAllBytes());
        } catch (Exception e) {
            return "ERROR SHELL: " + e.getMessage();
        }
    }
}
