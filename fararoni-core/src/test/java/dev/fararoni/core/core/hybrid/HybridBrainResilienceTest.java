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

import dev.fararoni.core.core.agents.RabbitAgent;
import dev.fararoni.core.core.analysis.AnalysisContext;
import dev.fararoni.core.core.indexing.model.LineRange;
import dev.fararoni.core.core.indexing.SentinelJavaParser;
import dev.fararoni.core.core.surgical.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HybridBrainService - Pruebas de Resiliencia (War Room)")
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class HybridBrainResilienceTest {
    @Mock
    RabbitAgent rabbit;

    @Mock
    TurtleAgent turtle;

    @Mock
    SurgeryConflictResolver conflictResolver;

    @Mock
    SentinelJavaParser parser;

    @Mock
    SurgicalEditor editor;

    OverlapDetector overlapDetector;
    HybridBrainService brain;

    @BeforeEach
    void setup() {
        overlapDetector = new OverlapDetector();
        brain = new HybridBrainService(rabbit, turtle, editor, conflictResolver, parser, overlapDetector);
    }

    private LineRange createLineRange(int startLine, int endLine) {
        return new LineRange(startLine, endLine, 0, 80, startLine * 50, endLine * 50 + 50);
    }

    @Nested
    @DisplayName("Escenario 1: Rabbit Conflict - Resolved Successfully")
    class Scenario1_HappyConflict {
        @Test
        @DisplayName("Rabbit genera conflicto, Resolver lo arregla exitosamente")
        void testScenario1_RabbitConflict_ResolvedSuccessfully() {
            String source = """
                public class Test {
                    public void process() {
                        int x = 10;
                        log("start");
                        save();
                    }
                }
                """;

            List<EditBlock> conflictPlan = List.of(
                new EditBlock("B1", "int x = 10;", "int x = 20;", 3, 0, 0),
                new EditBlock("B2", "int x = 10;\n        log(\"start\");",
                    "var x = 10;\n        logger.info(\"start\");", 3, 0, 0)
            );

            List<EditBlock> unifiedPlan = List.of(
                new EditBlock("UNIFIED",
                    "int x = 10;\n        log(\"start\");",
                    "int x = 20;\n        logger.info(\"start\");",
                    3, 0, 0)
            );

            AnalysisContext mockContext = mock(AnalysisContext.class);
            when(parser.createContext(anyString(), anyString())).thenReturn(mockContext);

            when(rabbit.planSurgery(any(), any(), any())).thenReturn(conflictPlan);

            when(conflictResolver.resolve(any(), anyString())).thenReturn(unifiedPlan);

            String expectedResult = source.replace("int x = 10;\n        log(\"start\");",
                "int x = 20;\n        logger.info(\"start\");");
            SurgeryReport mockReport = new SurgeryReport(expectedResult, List.of(), 0);
            when(editor.executeSurgery(anyString(), anyList(), anyString())).thenReturn(mockReport);

            String result = brain.executeModification("Test.java", source, "Fix var and log", "process");

            assertNotNull(result);
            verify(rabbit, atLeastOnce()).planSurgery(any(), any(), any());
            verify(turtle, never()).generateReplacer(any(), any());
        }
    }

    @Nested
    @DisplayName("Escenario 2: Rabbit Fails - Escalation to Turtle")
    class Scenario2_EscalationToTurtle {
        @Test
        @DisplayName("Rabbit falla, Resolver falla, Tortuga salva el dia")
        void testScenario2_RabbitFailsResolution_EscalateToTurtle() {
            String source = """
                public class Calc {
                    public int calculate() {
                        return 1 + 1;
                    }
                }
                """;

            AnalysisContext mockContext = mock(AnalysisContext.class);
            when(parser.createContext(anyString(), anyString())).thenReturn(mockContext);

            List<EditBlock> badPlan = List.of(
                new EditBlock("Bad1", "1", "2", 3, 0, 0),
                new EditBlock("Bad2", "1", "3", 3, 0, 0)
            );
            when(rabbit.planSurgery(any(), any(), any())).thenReturn(badPlan);

            EditBlock block1 = new EditBlock("Bad1", "1", "2", 3, 0, 0);
            when(editor.executeSurgery(anyString(), anyList(), anyString()))
                .thenThrow(new OverlapConflictException("COLLISION", block1, block1, "Conflicto simulado"));

            when(rabbit.askForCorrection(any(), anyString(), anyString()))
                .thenThrow(new SurgicalException("Rabbit agoto reintentos"));

            when(parser.extractMethodSource(any(), eq("calculate")))
                .thenReturn(Optional.of("public int calculate() { return 1 + 1; }"));
            when(parser.validateMethodSignatureMatch(eq("calculate"), anyString(), any()))
                .thenReturn(true);
            when(mockContext.getMethodRange(eq("calculate")))
                .thenReturn(createLineRange(2, 4));

            String turtleCode = "public int calculate() { return 5; }";
            when(turtle.generateReplacer(anyString(), anyString())).thenReturn(turtleCode);

            when(editor.replaceRange(anyString(), any(LineRange.class), anyString()))
                .thenReturn(source.replace("return 1 + 1;", "return 5;"));

            String result = brain.executeModification("Calc.java", source, "Make it 5", "calculate");

            assertNotNull(result);
            verify(turtle, atLeastOnce()).generateReplacer(anyString(), anyString());
        }

        @Test
        @DisplayName("Rabbit lanza SurgicalException directa, escala a Tortuga")
        void testScenario2_RabbitThrowsException_EscalateToTurtle() {
            String source = """
                public class Val {
                    public int getValue() {
                        return 0;
                    }
                }
                """;

            AnalysisContext mockContext = mock(AnalysisContext.class);
            when(parser.createContext(anyString(), anyString())).thenReturn(mockContext);

            when(rabbit.planSurgery(any(), any(), any()))
                .thenThrow(new SurgicalException("Random fail"));

            when(parser.extractMethodSource(any(), eq("getValue")))
                .thenReturn(Optional.of("public int getValue() { return 0; }"));
            when(parser.validateMethodSignatureMatch(eq("getValue"), anyString(), any()))
                .thenReturn(true);
            when(mockContext.getMethodRange(eq("getValue")))
                .thenReturn(createLineRange(2, 4));

            String turtleCode = "public int getValue() { return 42; }";
            when(turtle.generateReplacer(any(), any())).thenReturn(turtleCode);

            when(editor.replaceRange(anyString(), any(LineRange.class), anyString()))
                .thenReturn(source.replace("return 0;", "return 42;"));

            String result = brain.executeModification("Val.java", source, "Return 42", "getValue");

            assertNotNull(result);
            verify(turtle, times(1)).generateReplacer(any(), any());
        }
    }

    @Nested
    @DisplayName("Escenario 3: Turtle Hallucinates - Fail-Safe")
    class Scenario3_TurtleFailSafe {
        @Test
        @DisplayName("Tortuga devuelve codigo con firma diferente, sistema lanza excepcion")
        void testScenario3_TurtleReturnsInvalidSignature_Throws() {
            String source = """
                public class Val {
                    public int getValue() {
                        return 0;
                    }
                }
                """;

            AnalysisContext mockContext = mock(AnalysisContext.class);
            when(parser.createContext(anyString(), anyString())).thenReturn(mockContext);

            when(rabbit.planSurgery(any(), any(), any()))
                .thenThrow(new SurgicalException("Random fail"));

            when(parser.extractMethodSource(any(), eq("getValue")))
                .thenReturn(Optional.of("public int getValue() { return 0; }"));

            when(turtle.generateReplacer(any(), any())).thenReturn("public void getValue() {}");

            when(parser.validateMethodSignatureMatch(eq("getValue"), anyString(), any()))
                .thenReturn(false);

            assertThrows(SurgicalException.class,
                () -> brain.executeModification("Val.java", source, "Fix", "getValue"),
                "Debe lanzar SurgicalException si la firma no coincide");
        }

        @Test
        @DisplayName("Tortuga lanza excepcion, sistema propaga error")
        void testScenario3_TurtleThrows_PropagatesError() {
            String source = """
                public class Val {
                    public int getValue() {
                        return 0;
                    }
                }
                """;

            AnalysisContext mockContext = mock(AnalysisContext.class);
            when(parser.createContext(anyString(), anyString())).thenReturn(mockContext);

            when(rabbit.planSurgery(any(), any(), any()))
                .thenThrow(new SurgicalException("Rabbit fail"));

            when(parser.extractMethodSource(any(), eq("getValue")))
                .thenReturn(Optional.of("public int getValue() { return 0; }"));

            when(turtle.generateReplacer(any(), any()))
                .thenThrow(new SurgicalException("Turtle fail"));

            assertThrows(SurgicalException.class,
                () -> brain.executeModification("Val.java", source, "Fix", "getValue"),
                "Debe lanzar SurgicalException si Tortuga falla");
        }
    }

    @Nested
    @DisplayName("Escenario Adicional: Ping-Pong de Errores")
    class ScenarioAdditional_PingPong {
        @Test
        @DisplayName("Rabbit falla multiples veces, sistema escala a Tortuga sin bucle infinito")
        void testPingPong_EventuallyEscalates() {
            String source = """
                public class Loop {
                    public void process() {
                        doSomething();
                    }
                }
                """;

            AnalysisContext mockContext = mock(AnalysisContext.class);
            when(parser.createContext(anyString(), anyString())).thenReturn(mockContext);

            List<EditBlock> badPlan = List.of(
                new EditBlock("Bad1", "doSomething();", "doA();", 3, 0, 0),
                new EditBlock("Bad2", "doSomething();", "doB();", 3, 0, 0)
            );
            when(rabbit.planSurgery(any(), any(), any())).thenReturn(badPlan);

            EditBlock block1 = new EditBlock("Bad1", "doSomething();", "doA();", 3, 0, 0);
            when(editor.executeSurgery(anyString(), anyList(), anyString()))
                .thenThrow(new OverlapConflictException("COLLISION", block1, block1, "Conflicto simulado"));

            when(rabbit.askForCorrection(any(), anyString(), anyString()))
                .thenReturn(badPlan);

            when(parser.extractMethodSource(any(), eq("process")))
                .thenReturn(Optional.of("public void process() { doSomething(); }"));
            when(parser.validateMethodSignatureMatch(eq("process"), anyString(), any()))
                .thenReturn(true);
            when(mockContext.getMethodRange(eq("process")))
                .thenReturn(createLineRange(2, 4));

            String turtleCode = "public void process() { doCorrectThing(); }";
            when(turtle.generateReplacer(anyString(), anyString())).thenReturn(turtleCode);

            when(editor.replaceRange(anyString(), any(LineRange.class), anyString()))
                .thenReturn(source.replace("doSomething();", "doCorrectThing();"));

            String result = brain.executeModification("Loop.java", source, "Fix it", "process");

            assertNotNull(result);
            verify(turtle, atLeastOnce()).generateReplacer(anyString(), anyString());

            verify(rabbit, atMost(5)).askForCorrection(any(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Escenario: Auto-Fix en Flujo Completo")
    class ScenarioAutoFix {
        @Test
        @DisplayName("Plan con redundancia es auto-saneado sin escalacion")
        void testAutoFix_RedundancySanitized_NoEscalation() {
            String source = """
                public class Clean {
                    public void process() {
                        line1();
                        line2();
                        line3();
                    }
                }
                """;

            AnalysisContext mockContext = mock(AnalysisContext.class);
            when(parser.createContext(anyString(), anyString())).thenReturn(mockContext);

            List<EditBlock> redundantPlan = List.of(
                new EditBlock("Big", "line1();\n        line2();\n        line3();",
                    "newLine1();\n        newLine2();\n        newLine3();", 3, 0, 0),
                new EditBlock("Small", "line2();", "newLine2();", 4, 0, 0)
            );
            when(rabbit.planSurgery(any(), any(), any())).thenReturn(redundantPlan);

            String expectedResult = source
                .replace("line1();", "newLine1();")
                .replace("line2();", "newLine2();")
                .replace("line3();", "newLine3();");
            SurgeryReport mockReport = new SurgeryReport(expectedResult, List.of(), 0);
            when(editor.executeSurgery(anyString(), anyList(), anyString())).thenReturn(mockReport);

            String result = brain.executeModification("Clean.java", source, "Replace lines", "process");

            assertNotNull(result);
            verify(turtle, never()).generateReplacer(any(), any());
        }
    }
}
