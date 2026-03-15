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
package dev.fararoni.core.cli;

import dev.fararoni.core.core.reflexion.TestCorrectionService;
import dev.fararoni.core.core.reflexion.testoutput.FailurePattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
    name = "reflexion",
    mixinStandardHelpOptions = true,
    version = "ReflexionEngine v2.0.0",
    description = """
        [PROBE] Generador de Feedback para Self-Correction

        Analiza test failures y genera feedback estructurado
        para ayudar al LLM a corregir código.

        Ejemplos:
          reflexion --code "def f(): pass" --test-output "FAILED"
          reflexion --test-output-file pytest.txt --attempt 2
          cat code.py | reflexion --test-output "FAILED"
        """
)
public class ReflexionCli implements Callable<Integer> {
    @Option(names = {"-c", "--code"},
            description = "Código que falló los tests")
    private String code;

    @Option(names = {"-t", "--test-output"},
            description = "Output de pytest/tests")
    private String testOutput;

    @Option(names = {"-f", "--test-output-file"},
            description = "Archivo con output de pytest")
    private File testOutputFile;

    @Option(names = {"-a", "--attempt"},
            description = "Número de intento (1, 2, 3...)",
            defaultValue = "1")
    private int attemptNumber;

    @Option(names = {"-e", "--exercise-id"},
            description = "ID del ejercicio (para tracking)")
    private String exerciseId;

    @Option(names = {"--minimal"},
            description = "Usar engine minimalista (más rápido, menos detalle)")
    private boolean minimal;

    @Option(names = {"--patterns-only"},
            description = "Solo mostrar patrones detectados")
    private boolean patternsOnly;

    @Option(names = {"--summary"},
            description = "Solo mostrar resumen")
    private boolean summaryOnly;

    @Option(names = {"--json"},
            description = "Output en formato JSON")
    private boolean jsonOutput;

    @Option(names = {"--quiet", "-q"},
            description = "Sin output extra, solo feedback")
    private boolean quiet;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ReflexionCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        String codeInput = getCode();
        if (codeInput == null) {
            codeInput = "";
        }

        String testOutputInput = getTestOutput();
        if (testOutputInput == null || testOutputInput.isBlank()) {
            if (!quiet) {
                System.err.println("ERROR: Se requiere --test-output o --test-output-file");
            }
            return 1;
        }

        TestCorrectionService service = minimal
            ? TestCorrectionService.minimal()
            : TestCorrectionService.create();

        if (exerciseId != null) {
            service.startExercise(exerciseId);
        }

        if (patternsOnly) {
            outputPatterns(service, testOutputInput);
        } else if (summaryOnly) {
            outputSummary(service, testOutputInput);
        } else {
            outputFeedback(service, codeInput, testOutputInput);
        }

        return 0;
    }

    private String getCode() throws IOException {
        if (code != null) {
            return code;
        }

        if (System.console() == null && System.in.available() > 0) {
            return readStdin();
        }

        return null;
    }

    private String getTestOutput() throws IOException {
        if (testOutput != null) {
            return testOutput;
        }

        if (testOutputFile != null && testOutputFile.exists()) {
            return java.nio.file.Files.readString(testOutputFile.toPath());
        }

        return null;
    }

    private String readStdin() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private void outputFeedback(TestCorrectionService service, String code, String testOutput) {
        String feedback = service.generateCorrectionFeedback(
            code, testOutput, attemptNumber, exerciseId
        );

        if (jsonOutput) {
            outputJson("feedback", feedback);
        } else {
            System.out.println(feedback);
        }
    }

    private void outputPatterns(TestCorrectionService service, String testOutput) {
        Set<FailurePattern> patterns = service.detectPatterns(testOutput);

        if (jsonOutput) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (FailurePattern p : patterns) {
                if (!first) json.append(",");
                json.append("\"").append(p.name()).append("\"");
                first = false;
            }
            json.append("]");
            System.out.println(json);
        } else {
            System.out.println("Patrones detectados:");
            for (FailurePattern p : patterns) {
                System.out.println("  - " + p.name());
            }
            FailurePattern dominant = service.getDominantPattern(testOutput);
            System.out.println("\nPatrón dominante: " + dominant.name());
        }
    }

    private void outputSummary(TestCorrectionService service, String testOutput) {
        String summary = service.getSummary(testOutput);

        if (jsonOutput) {
            outputJson("summary", summary);
        } else {
            System.out.println(summary);
        }
    }

    private void outputJson(String key, String value) {
        String escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");

        System.out.printf("{\"%s\":\"%s\"}\n", key, escaped);
    }
}
