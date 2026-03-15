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
package dev.fararoni.core.core.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
@DisplayName("CodeAnalysisService - Bisturi vs Machete")
class CodeAnalysisServiceTest {
    private CodeAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new CodeAnalysisService();
    }

    @Nested
    @DisplayName("Estrategia de Decision")
    class StrategyDecision {
        @Test
        @DisplayName("Debe usar El Ojo (AST) para archivos .java")
        void shouldUseAstForJavaFiles() {
            String javaCode = """
                public class Test {
                    public void method() {}
                }
                """;

            CodeAnalysisService.AnalysisDiagnostic diag =
                service.diagnose("Test.java", javaCode, 10);

            assertThat(diag.isJavaFile()).isTrue();
        }

        @Test
        @DisplayName("Debe usar Machete para archivos no-Java")
        void shouldUseMacheteForNonJavaFiles() {
            String pythonCode = "def hello(): print('world')";

            CodeAnalysisService.AnalysisDiagnostic diag =
                service.diagnose("test.py", pythonCode, 10);

            assertThat(diag.isJavaFile()).isFalse();
            if (diag.needsChunking()) {
                assertThat(diag.strategyUsed()).isEqualTo("REGEX_MACHETE");
            }
        }
    }

    @Nested
    @DisplayName("Division Inteligente")
    class IntelligentSplit {
        @Test
        @DisplayName("Debe retornar lista vacia para contenido null")
        void shouldReturnEmptyForNull() {
            List<String> chunks = service.intelligentSplit("test.java", null, 1000);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("Debe retornar lista vacia para contenido en blanco")
        void shouldReturnEmptyForBlank() {
            List<String> chunks = service.intelligentSplit("test.java", "  ", 1000);
            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("Debe retornar contenido completo si no necesita chunking")
        void shouldReturnCompleteIfNoChunkingNeeded() {
            String smallCode = "public class A {}";

            List<String> chunks = service.intelligentSplit("A.java", smallCode, 10000);

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).isEqualTo(smallCode);
        }

        @Test
        @DisplayName("Debe dividir codigo Java grande usando AST")
        void shouldSplitLargeJavaUsingAst() {
            StringBuilder code = new StringBuilder("public class Big {\n");
            for (int i = 0; i < 50; i++) {
                code.append("    public void method").append(i).append("() {\n");
                code.append("        // Some code\n".repeat(10));
                code.append("    }\n");
            }
            code.append("}");

            List<String> chunks = service.intelligentSplit("Big.java", code.toString(), 200);

            assertThat(chunks.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("Debe dividir codigo no-Java usando lineas")
        void shouldSplitNonJavaByLines() {
            String pythonCode = "def method():\n    pass\n".repeat(100);

            List<String> chunks = service.intelligentSplit("script.py", pythonCode, 50);

            assertThat(chunks.size()).isGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("Deteccion de Necesidad de Chunking")
    class NeedsChunking {
        @Test
        @DisplayName("Debe detectar que codigo grande necesita chunking")
        void shouldDetectLargeCodeNeedsChunking() {
            String largeCode = "x".repeat(20000);

            assertThat(service.needsChunking(largeCode, 1000)).isTrue();
        }

        @Test
        @DisplayName("Debe detectar que codigo pequeno no necesita chunking")
        void shouldDetectSmallCodeDoesNotNeedChunking() {
            String smallCode = "x".repeat(100);

            assertThat(service.needsChunking(smallCode, 1000)).isFalse();
        }

        @Test
        @DisplayName("Debe retornar false para null")
        void shouldReturnFalseForNull() {
            assertThat(service.needsChunking(null, 1000)).isFalse();
        }
    }

    @Nested
    @DisplayName("Diagnostico de Analisis")
    class AnalysisDiagnostic {
        @Test
        @DisplayName("Diagnostico debe reportar estrategia correcta para Java")
        void diagnosticShouldReportCorrectStrategyForJava() {
            String javaCode = """
                public class Test {
                    public void m1() {  }
                    public void m2() {  }
                }
                """.repeat(50);

            CodeAnalysisService.AnalysisDiagnostic diag =
                service.diagnose("Test.java", javaCode, 100);

            assertThat(diag.isJavaFile()).isTrue();
            assertThat(diag.needsChunking()).isTrue();
            assertThat(diag.strategyUsed()).isIn("AST_OJO", "REGEX_MACHETE");
        }

        @Test
        @DisplayName("Diagnostico debe reportar NONE si no necesita chunking")
        void diagnosticShouldReportNoneIfNoChunking() {
            String smallCode = "public class A {}";

            CodeAnalysisService.AnalysisDiagnostic diag =
                service.diagnose("A.java", smallCode, 10000);

            assertThat(diag.needsChunking()).isFalse();
            assertThat(diag.strategyUsed()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("Diagnostico debe estimar tokens correctamente")
        void diagnosticShouldEstimateTokens() {
            String code = "x".repeat(400);

            CodeAnalysisService.AnalysisDiagnostic diag =
                service.diagnose("test.txt", code, 1000);

            assertThat(diag.estimatedTokens()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Accessors")
    class Accessors {
        @Test
        @DisplayName("Debe exponer parser y chunker")
        void shouldExposeParserAndChunker() {
            assertThat(service.getSentinelParser()).isNotNull();
            assertThat(service.getSemanticChunker()).isNotNull();
            assertThat(service.getLegacyMachete()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Degradacion Graciosa")
    class GracefulDegradation {
        @Test
        @DisplayName("Debe degradar a Machete si Java tiene sintaxis invalida")
        void shouldDegradeToMacheteForInvalidJava() {
            String invalidJava = "public class { broken syntax }}} " + "x".repeat(1000);

            List<String> chunks = service.intelligentSplit("Broken.java", invalidJava, 100);

            assertThat(chunks).isNotEmpty();
        }
    }
}
