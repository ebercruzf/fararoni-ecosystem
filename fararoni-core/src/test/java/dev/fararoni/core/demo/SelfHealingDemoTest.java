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
package dev.fararoni.core.demo;

import dev.fararoni.bus.agent.api.ToolRegistry;
import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.ToolMetadata;
import dev.fararoni.core.agent.ActionParser;
import dev.fararoni.core.core.hooks.PostWriteHook;
import dev.fararoni.core.core.saga.SagaOrchestrator;
import dev.fararoni.core.service.FilesystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("🎯 DEMO: Self-Healing con LLM Local")
class SelfHealingDemoTest {
    @TempDir
    Path tempDir;

    private List<String> consoleOutput;
    private FilesystemService filesystemService;
    private SagaOrchestrator sagaOrchestrator;

    @BeforeEach
    void setUp() {
        consoleOutput = new ArrayList<>();
        filesystemService = new FilesystemService(tempDir);
        sagaOrchestrator = new SagaOrchestrator(new DemoToolRegistry());
    }

    @Test
    @DisplayName("✅ Escenario 1: LLM genera codigo CORRECTO → Tests pasan → Archivo guardado")
    void scenarioSuccess() throws Exception {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🎯 ESCENARIO 1: Codigo Correcto - Tests Pasan");
        System.out.println("=".repeat(70));

        PostWriteHook testsPassHook = new PostWriteHook() {
            @Override
            public HookResult onFileWritten(Path file, String sagaId) {
                System.out.println("   🧪 [RegressionGuard] Ejecutando tests...");
                System.out.println("   ✅ Tests PASSED en 150ms");
                return HookResult.ok();
            }

            @Override
            public String getName() {
                return "RegressionGuard";
            }
        };

        ActionParser parser = new ActionParser(
            filesystemService,
            msg -> {
                consoleOutput.add(msg);
                System.out.println("   📝 " + msg);
            },
            null,
            sagaOrchestrator,
            List.of(testsPassHook)
        );

        System.out.println("\n   🤖 LLM Output:");
        System.out.println("   ───────────────────────────────────");

        String[] llmOutput = {
            ">>>FILE: Calculator.java",
            "public class Calculator {",
            "    public int add(int a, int b) {",
            "        return a + b;",
            "    }",
            "}",
            "<<<END_FILE"
        };

        for (String line : llmOutput) {
            System.out.println("   " + line);
            parser.processLine(line);
        }

        System.out.println("   ───────────────────────────────────");

        Path savedFile = tempDir.resolve("Calculator.java");
        boolean fileExists = Files.exists(savedFile);

        System.out.println("\n   📊 RESULTADO:");
        System.out.println("   • Archivo guardado: " + (fileExists ? "✅ SI" : "❌ NO"));
        System.out.println("   • Sagas activas: " + sagaOrchestrator.getActiveSagaCount());

        assert fileExists : "El archivo debería existir";
        System.out.println("\n   ✅ ESCENARIO EXITOSO: Archivo guardado correctamente\n");
    }

    @Test
    @DisplayName("🔄 Escenario 2: LLM genera codigo con BUG → Tests fallan → ROLLBACK automatico")
    void scenarioRollback() throws Exception {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🎯 ESCENARIO 2: Codigo con Bug - Rollback Automatico");
        System.out.println("=".repeat(70));

        PostWriteHook testsFailHook = new PostWriteHook() {
            @Override
            public HookResult onFileWritten(Path file, String sagaId) {
                System.out.println("   🧪 [RegressionGuard] Ejecutando tests...");
                System.out.println("   ❌ Tests FAILED: AssertionError en CalculatorTest.testAdd()");
                System.out.println("   ⚠️  Regression detectada - Iniciando rollback...");
                return HookResult.rollback("AssertionError: expected 5 but was 4");
            }

            @Override
            public String getName() {
                return "RegressionGuard";
            }
        };

        ActionParser parser = new ActionParser(
            filesystemService,
            msg -> {
                consoleOutput.add(msg);
                System.out.println("   📝 " + msg);
            },
            null,
            sagaOrchestrator,
            List.of(testsFailHook)
        );

        System.out.println("\n   🤖 LLM Output (con bug):");
        System.out.println("   ───────────────────────────────────");

        String[] llmOutput = {
            ">>>FILE: Calculator.java",
            "public class Calculator {",
            "    public int add(int a, int b) {",
            "        return a + b - 1; // BUG: off-by-one error",
            "    }",
            "}",
            "<<<END_FILE"
        };

        for (String line : llmOutput) {
            System.out.println("   " + line);
            parser.processLine(line);
        }

        System.out.println("   ───────────────────────────────────");

        var stats = sagaOrchestrator.getStatistics();

        System.out.println("\n   📊 RESULTADO:");
        System.out.println("   • Sagas totales: " + stats.get("totalSagas"));
        System.out.println("   • Sagas compensadas: " + stats.get("compensatedSagas"));
        System.out.println("   • Rollback ejecutado: " + (((int)stats.get("compensatedSagas")) > 0 ? "✅ SI" : "❌ NO"));

        assert ((int)stats.get("compensatedSagas")) > 0 : "Debería haber ejecutado rollback";
        System.out.println("\n   ✅ ESCENARIO EXITOSO: Self-Healing funcionó correctamente\n");
    }

    @Test
    @DisplayName("⚠️ Escenario 3: LLM genera codigo con warnings → Tests pasan con advertencias")
    void scenarioWarning() throws Exception {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🎯 ESCENARIO 3: Codigo con Warnings - Archivo guardado con advertencia");
        System.out.println("=".repeat(70));

        PostWriteHook warningHook = new PostWriteHook() {
            @Override
            public HookResult onFileWritten(Path file, String sagaId) {
                System.out.println("   🧪 [RegressionGuard] Ejecutando tests...");
                System.out.println("   ✅ Tests PASSED en 200ms");
                System.out.println("   ⚠️  Checkstyle: 3 warnings detectados");
                return HookResult.warning("Checkstyle: Missing Javadoc on public method");
            }

            @Override
            public String getName() {
                return "RegressionGuard";
            }
        };

        ActionParser parser = new ActionParser(
            filesystemService,
            msg -> {
                consoleOutput.add(msg);
                System.out.println("   📝 " + msg);
            },
            null,
            sagaOrchestrator,
            List.of(warningHook)
        );

        System.out.println("\n   🤖 LLM Output:");
        System.out.println("   ───────────────────────────────────");

        String[] llmOutput = {
            ">>>FILE: Utils.java",
            "public class Utils {",
            "    public static String format(String s) {",
            "        return s.trim().toUpperCase();",
            "    }",
            "}",
            "<<<END_FILE"
        };

        for (String line : llmOutput) {
            System.out.println("   " + line);
            parser.processLine(line);
        }

        System.out.println("   ───────────────────────────────────");

        Path savedFile = tempDir.resolve("Utils.java");
        boolean fileExists = Files.exists(savedFile);

        System.out.println("\n   📊 RESULTADO:");
        System.out.println("   • Archivo guardado: " + (fileExists ? "✅ SI" : "❌ NO"));
        System.out.println("   • Warning mostrado: ✅ SI");

        assert fileExists : "El archivo debería existir (warnings no bloquean)";
        System.out.println("\n   ✅ ESCENARIO EXITOSO: Archivo guardado con advertencia\n");
    }

    @Test
    @DisplayName("🔗 Escenario 4: Flujo completo - Multiples archivos con rollback parcial")
    void scenarioMultipleFiles() throws Exception {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🎯 ESCENARIO 4: Multiples Archivos - Rollback Parcial");
        System.out.println("=".repeat(70));

        final int[] fileCount = {0};

        PostWriteHook selectiveHook = new PostWriteHook() {
            @Override
            public HookResult onFileWritten(Path file, String sagaId) {
                fileCount[0]++;
                String fileName = file.getFileName().toString();
                System.out.println("   🧪 [RegressionGuard] Validando " + fileName + "...");

                if (fileName.equals("Broken.java")) {
                    System.out.println("   ❌ Tests FAILED para " + fileName);
                    return HookResult.rollback("Compilation error in Broken.java");
                }

                System.out.println("   ✅ Tests PASSED para " + fileName);
                return HookResult.ok();
            }

            @Override
            public String getName() {
                return "RegressionGuard";
            }
        };

        ActionParser parser = new ActionParser(
            filesystemService,
            msg -> {
                consoleOutput.add(msg);
                System.out.println("   📝 " + msg);
            },
            null,
            sagaOrchestrator,
            List.of(selectiveHook)
        );

        System.out.println("\n   🤖 LLM Output (3 archivos):");
        System.out.println("   ───────────────────────────────────");

        String[] file1 = {">>>FILE: Good1.java", "public class Good1 {}", "<<<END_FILE"};
        for (String line : file1) {
            System.out.println("   " + line);
            parser.processLine(line);
        }

        String[] file2 = {">>>FILE: Broken.java", "public class Broken { syntax error", "<<<END_FILE"};
        for (String line : file2) {
            System.out.println("   " + line);
            parser.processLine(line);
        }

        String[] file3 = {">>>FILE: Good2.java", "public class Good2 {}", "<<<END_FILE"};
        for (String line : file3) {
            System.out.println("   " + line);
            parser.processLine(line);
        }

        System.out.println("   ───────────────────────────────────");

        var stats = sagaOrchestrator.getStatistics();

        System.out.println("\n   📊 RESULTADO:");
        System.out.println("   • Archivos procesados: " + fileCount[0]);
        System.out.println("   • Good1.java: " + (Files.exists(tempDir.resolve("Good1.java")) ? "✅ Guardado" : "❌ No existe"));
        System.out.println("   • Broken.java: " + (Files.exists(tempDir.resolve("Broken.java")) ? "⚠️ Existe (rollback falló)" : "✅ Rollback exitoso"));
        System.out.println("   • Good2.java: " + (Files.exists(tempDir.resolve("Good2.java")) ? "✅ Guardado" : "❌ No existe"));
        System.out.println("   • Compensaciones: " + stats.get("compensatedSagas"));

        System.out.println("\n   ✅ ESCENARIO COMPLETADO\n");
    }

    static class DemoToolRegistry implements ToolRegistry {
        @Override public void register(ToolSkill skill) {}
        @Override public boolean unregister(String skillName) { return false; }
        @Override public Optional<ToolSkill> findSkill(String name) { return Optional.empty(); }
        @Override public Optional<Method> findAction(String skillName, String actionName) { return Optional.empty(); }
        @Override public List<ToolSkill> getAllSkills() { return List.of(); }
        @Override public List<String> getAllSkillNames() { return List.of(); }
        @Override public List<ToolMetadata> getAllActionMetadata() { return List.of(); }
        @Override public String generateToolsJson() { return "{}"; }
        @Override public String generateToolsSummary() { return ""; }
        @Override public int size() { return 0; }
        @Override public boolean hasSkill(String skillName) { return false; }
        @Override public void shutdownAll() {}
        @Override public void clear() {}
    }
}
