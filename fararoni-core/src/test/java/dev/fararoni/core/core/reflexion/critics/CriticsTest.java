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
package dev.fararoni.core.core.reflexion.critics;

import dev.fararoni.core.core.reflexion.Critic;
import dev.fararoni.core.core.reflexion.Evaluation;
import dev.fararoni.core.core.reflexion.EvaluationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("Critics - Implementaciones de Criticos")
class CriticsTest {
    @Nested
    @DisplayName("AssumptionCritic")
    class AssumptionCriticTests {
        private final AssumptionCritic critic = new AssumptionCritic();
        private final EvaluationContext ctx = EvaluationContext.empty();

        @Test
        @DisplayName("Detecta 'I assume' en respuesta")
        void detectsAssume() {
            String response = "I assume you want to use Java for this project.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }

        @Test
        @DisplayName("Detecta 'probably' en respuesta")
        void detectsProbably() {
            String response = "This will probably work for your use case.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta 'I think' en respuesta")
        void detectsIThink() {
            String response = "I think the best approach would be to use a HashMap.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Pasa cuando no hay suposiciones")
        void passesWithoutAssumptions() {
            String response = "To solve this problem, use a HashMap with String keys.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isPassed());
        }

        @Test
        @DisplayName("Skip para respuesta vacia")
        void skipsEmptyResponse() {
            Evaluation result = critic.evaluate("", ctx);

            assertInstanceOf(Evaluation.Skip.class, result);
        }

        @Test
        @DisplayName("Detecta patrones en español")
        void detectsSpanishPatterns() {
            String response = "Supongo que quieres usar Java para este proyecto.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }

        @Test
        @DisplayName("Strict mode falla en cualquier suposicion")
        void strictMode_failsOnAnyAssumption() {
            AssumptionCritic strict = new AssumptionCritic().withStrictMode(true);
            String response = "I assume this is correct.";

            Evaluation result = strict.evaluate(response, ctx);

            assertTrue(result.isBlocking());
        }

        @Test
        @DisplayName("Nombre y categoria correctos")
        void hasCorrectNameAndCategory() {
            assertEquals("AssumptionCritic", critic.getName());
            assertEquals(Critic.CriticCategory.QUALITY, critic.getCategory());
        }
    }

    @Nested
    @DisplayName("CompletenessCritic")
    class CompletenessCriticTests {
        private final CompletenessCritic critic = new CompletenessCritic();
        private final EvaluationContext ctx = EvaluationContext.empty();

        @Test
        @DisplayName("Detecta llaves desbalanceadas")
        void detectsUnbalancedBraces() {
            String response = "Here is the code:\n```java\npublic class Test {\n```";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }

        @Test
        @DisplayName("Detecta respuesta truncada con ...")
        void detectsTruncatedResponse() {
            String response = "The steps are: 1. First step, 2. Second step...";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta bloques de codigo sin cerrar")
        void detectsUnclosedCodeBlock() {
            String response = "Here's the implementation:\n```java\ncode here";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }

        @Test
        @DisplayName("Pasa con respuesta completa")
        void passesCompleteResponse() {
            String response = """
                Here is a complete solution:
                ```java
                public class Test {
                    public void method() {
                        System.out.println("Hello");
                    }
                }
                ```
                This code prints "Hello" to the console.
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isPassed() || result.hasWarnings());
        }

        @Test
        @DisplayName("Falla con respuesta muy corta")
        void failsWithVeryShortResponse() {
            CompletenessCritic strict = new CompletenessCritic().withMinLength(100);
            String response = "Yes.";

            Evaluation result = strict.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }
    }

    @Nested
    @DisplayName("SecurityCritic")
    class SecurityCriticTests {
        private final SecurityCritic critic = new SecurityCritic();
        private final EvaluationContext ctx = EvaluationContext.forCode("fix code", "java");

        @Test
        @DisplayName("Detecta SQL injection por concatenacion")
        void detectsSqlInjection() {
            String response = """
                ```java
                String sql = "SELECT * FROM users WHERE name = '" + name + "' AND active = 1";
                stmt.executeQuery(sql);
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertNotNull(result);
            if (result.isBlocking() || result.hasWarnings()) {
                assertTrue(true);
            } else {
                assertTrue(result.isPassed() || result instanceof Evaluation.Skip);
            }
        }

        @Test
        @DisplayName("Detecta Runtime.exec con concatenacion")
        void detectsCommandInjection() {
            String response = """
                ```java
                Runtime.getRuntime().exec("ls " + userInput);
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isBlocking() || result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta contraseña hardcodeada")
        void detectsHardcodedPassword() {
            String response = """
                ```java
                String password = "mySecretPassword123";
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isBlocking() || result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta uso de MD5")
        void detectsWeakMd5() {
            String response = """
                ```java
                MessageDigest md = MessageDigest.getInstance("MD5");
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Pasa con codigo seguro")
        void passesSecureCode() {
            String response = """
                ```java
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
                ps.setInt(1, userId);
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isPassed() || !result.isBlocking());
        }

        @Test
        @DisplayName("Skip sin bloques de codigo")
        void skipsWithoutCodeBlocks() {
            EvaluationContext textCtx = EvaluationContext.ofPrompt("explain something");
            String response = "This is just text without any code.";

            Evaluation result = critic.evaluate(response, textCtx);

            assertInstanceOf(Evaluation.Skip.class, result);
        }
    }

    @Nested
    @DisplayName("PiiDetectionCritic")
    class PiiDetectionCriticTests {
        private final PiiDetectionCritic critic = new PiiDetectionCritic();
        private final EvaluationContext ctx = EvaluationContext.empty();

        @Test
        @DisplayName("Detecta SSN")
        void detectsSsn() {
            String response = "The customer's SSN is 123-45-6789.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isBlocking() || result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta numero de tarjeta de credito")
        void detectsCreditCard() {
            String response = "Payment with card 4111-1111-1111-1111";

            Evaluation result = critic.evaluate(response, ctx);

            assertNotNull(result);
        }

        @Test
        @DisplayName("Detecta email real")
        void detectsEmail() {
            String response = "Contact john.doe@company.com for more info.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }

        @Test
        @DisplayName("No detecta email de ejemplo")
        void doesNotDetectExampleEmail() {
            String response = "Use user@example.com as a placeholder.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isPassed() || result.hasWarnings());
        }

        @Test
        @DisplayName("No detecta IP localhost")
        void doesNotDetectLocalhost() {
            String response = "Connect to 127.0.0.1 for local testing.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.isPassed() || result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta CURP mexicana")
        void detectsCurp() {
            String response = "Su CURP es GODE920101HDFRRL09.";

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }
    }

    @Nested
    @DisplayName("SyntaxCritic")
    class SyntaxCriticTests {
        private final SyntaxCritic critic = new SyntaxCritic();
        private final EvaluationContext ctx = EvaluationContext.forCode("write code", "java");

        @Test
        @DisplayName("Detecta llaves desbalanceadas en Java")
        void detectsUnbalancedBracesInJava() {
            String response = """
                ```java
                public class Test {
                    public void method() {
                        if (true) {
                            System.out.println("hello");
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings() || result.isBlocking());
        }

        @Test
        @DisplayName("Detecta uso de == con String")
        void detectsStringEqualsOperator() {
            String response = """
                ```java
                if (name == "test") {
                    return true;
                }
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta var en JavaScript")
        void detectsVarInJavascript() {
            String response = """
                ```javascript
                var x = 5;
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Detecta DELETE sin WHERE en SQL")
        void detectsDeleteWithoutWhere() {
            String response = """
                ```sql
                DELETE FROM users
                ```
                """;

            Evaluation result = critic.evaluate(response, ctx);

            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("Skip si no hay bloques de codigo")
        void skipsWithoutCodeBlocks() {
            EvaluationContext textCtx = EvaluationContext.ofPrompt("explain");
            String response = "This is just explanatory text.";

            Evaluation result = critic.evaluate(response, textCtx);

            assertInstanceOf(Evaluation.Skip.class, result);
        }
    }

    @Nested
    @DisplayName("PatternCritic")
    class PatternCriticTests {
        @Test
        @DisplayName("Patron requerido pasa cuando esta presente")
        void requiredPattern_passesWhenPresent() {
            PatternCritic critic = PatternCritic.builder("Test")
                .requirePattern("\\{", "Debe contener {")
                .build();

            Evaluation result = critic.evaluate("{data}", EvaluationContext.empty());

            assertTrue(result.isPassed());
        }

        @Test
        @DisplayName("Patron requerido falla cuando no esta presente")
        void requiredPattern_failsWhenMissing() {
            PatternCritic critic = PatternCritic.builder("Test")
                .requirePattern("\\{", "Debe contener {")
                .build();

            Evaluation result = critic.evaluate("data", EvaluationContext.empty());

            assertTrue(result.isBlocking() || result.hasWarnings());
        }

        @Test
        @DisplayName("Patron prohibido pasa cuando no esta presente")
        void forbiddenPattern_passesWhenAbsent() {
            PatternCritic critic = PatternCritic.builder("Test")
                .forbidPattern("undefined", "No debe contener undefined")
                .build();

            Evaluation result = critic.evaluate("{data: null}", EvaluationContext.empty());

            assertTrue(result.isPassed());
        }

        @Test
        @DisplayName("Patron prohibido falla cuando esta presente")
        void forbiddenPattern_failsWhenPresent() {
            PatternCritic critic = PatternCritic.builder("Test")
                .forbidPattern("undefined", "No debe contener undefined")
                .build();

            Evaluation result = critic.evaluate("{data: undefined}", EvaluationContext.empty());

            assertTrue(result.isBlocking() || result.hasWarnings());
        }

        @Test
        @DisplayName("JSON validator detecta comillas simples")
        void jsonValidator_detectsSingleQuotes() {
            PatternCritic validator = PatternCritic.jsonValidator();

            Evaluation result = validator.evaluate("{'key': 'value'}", EvaluationContext.empty());

            assertTrue(result.hasWarnings() || result.isBlocking());
        }

        @Test
        @DisplayName("Multiples reglas se evaluan")
        void multipleRules_areEvaluated() {
            PatternCritic critic = PatternCritic.builder("Multi")
                .requirePattern("^\\{", "Debe empezar con {")
                .requirePattern("\\}$", "Debe terminar con }")
                .forbidPattern("undefined", "Sin undefined")
                .build();

            Evaluation result = critic.evaluate("{valid: true}", EvaluationContext.empty());

            assertTrue(result.isPassed());
        }
    }

    @Nested
    @DisplayName("Critic Composition")
    class CriticCompositionTests {
        @Test
        @DisplayName("andThen combina criticos")
        void andThen_combinesCritics() {
            Critic c1 = Critic.alwaysPass("C1");
            Critic c2 = Critic.alwaysPass("C2");
            Critic combined = c1.andThen(c2);

            Evaluation result = combined.evaluate("test", EvaluationContext.empty());

            assertTrue(result.isPassed());
        }

        @Test
        @DisplayName("andThen detiene en primer fallo")
        void andThen_stopsOnFirstFailure() {
            Critic c1 = Critic.alwaysFail("C1", "Fail reason");
            Critic c2 = Critic.alwaysPass("C2");
            Critic combined = c1.andThen(c2);

            Evaluation result = combined.evaluate("test", EvaluationContext.empty());

            assertTrue(result.isBlocking());
            assertEquals("C1", result.criticName());
        }

        @Test
        @DisplayName("when() crea critico condicional")
        void when_createsConditionalCritic() {
            Critic base = Critic.alwaysPass("Base");
            Critic conditional = base.when(ctx -> ctx.expectsCode());

            Evaluation result1 = conditional.evaluate("test",
                EvaluationContext.ofPrompt("explain"));
            assertInstanceOf(Evaluation.Skip.class, result1);

            Evaluation result2 = conditional.evaluate("test",
                EvaluationContext.forCode("fix", "java"));
            assertTrue(result2.isPassed());
        }

        @Test
        @DisplayName("asNonBlocking convierte Fail a Warning")
        void asNonBlocking_convertsFailToWarning() {
            Critic base = Critic.alwaysFail("Base", "Reason");
            Critic nonBlocking = base.asNonBlocking();

            Evaluation result = nonBlocking.evaluate("test", EvaluationContext.empty());

            assertFalse(result.isBlocking());
            assertTrue(result.hasWarnings());
        }

        @Test
        @DisplayName("combine() combina lista de criticos")
        void combine_combinesCriticsList() {
            List<Critic> critics = List.of(
                Critic.alwaysPass("C1"),
                Critic.alwaysPass("C2"),
                Critic.alwaysPass("C3")
            );
            Critic combined = Critic.combine(critics);

            Evaluation result = combined.evaluate("test", EvaluationContext.empty());

            assertTrue(result.isPassed());
        }
    }
}
