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
package dev.fararoni.bus.agent.api.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para AgentMessage.
 * Valida: factory methods, type checks, content accessors.
 */
@DisplayName("AgentMessage")
class AgentMessageTest {

    @Nested
    @DisplayName("Constantes de Tipo")
    class ConstantesTipo {

        @Test
        @DisplayName("Constantes tienen valores correctos")
        void constantesTienenValoresCorrectos() {
            assertEquals("TASK_ASSIGNMENT", AgentMessage.TYPE_TASK_ASSIGNMENT);
            assertEquals("CODE_REVIEW_REQ", AgentMessage.TYPE_CODE_REVIEW_REQ);
            assertEquals("KNOWLEDGE_QUERY", AgentMessage.TYPE_KNOWLEDGE_QUERY);
            assertEquals("STATUS_UPDATE", AgentMessage.TYPE_STATUS_UPDATE);
            assertEquals("FATAL_ERROR", AgentMessage.TYPE_FATAL_ERROR);
            assertEquals("ACK", AgentMessage.TYPE_ACK);
            assertEquals("NACK", AgentMessage.TYPE_NACK);
        }
    }

    @Nested
    @DisplayName("Factory: taskAssignment()")
    class TaskAssignment {

        @Test
        @DisplayName("Crea mensaje con tipo TASK_ASSIGNMENT")
        void creaMensajeConTipoCorrecto() {
            var message = AgentMessage.taskAssignment(
                "SUPERVISOR",
                Map.of("priority", "HIGH"),
                "Implementa el login"
            );

            assertEquals(AgentMessage.TYPE_TASK_ASSIGNMENT, message.type());
            assertEquals("SUPERVISOR", message.senderRole());
            assertEquals("Implementa el login", message.naturalLanguageHint());
            assertEquals("HIGH", message.content().get("priority"));
        }

        @Test
        @DisplayName("isTaskAssignment() retorna true")
        void isTaskAssignmentTrue() {
            var message = AgentMessage.taskAssignment("SUPERVISOR", Map.of(), "Task");
            assertTrue(message.isTaskAssignment());
        }
    }

    @Nested
    @DisplayName("Factory: codeReviewRequest()")
    class CodeReviewRequest {

        @Test
        @DisplayName("Crea mensaje con tipo CODE_REVIEW_REQ")
        void creaMensajeConTipoCorrecto() {
            var message = AgentMessage.codeReviewRequest(
                "DEVELOPER",
                "/src/Main.java",
                "Revisa este codigo"
            );

            assertEquals(AgentMessage.TYPE_CODE_REVIEW_REQ, message.type());
            assertEquals("DEVELOPER", message.senderRole());
            assertEquals("/src/Main.java", message.content().get("code_path"));
            assertEquals("Revisa este codigo", message.naturalLanguageHint());
        }
    }

    @Nested
    @DisplayName("Factory: knowledgeQuery()")
    class KnowledgeQuery {

        @Test
        @DisplayName("Crea mensaje con tipo KNOWLEDGE_QUERY")
        void creaMensajeConTipoCorrecto() {
            var message = AgentMessage.knowledgeQuery("ARCHITECT", "Kubernetes");

            assertEquals(AgentMessage.TYPE_KNOWLEDGE_QUERY, message.type());
            assertEquals("ARCHITECT", message.senderRole());
            assertEquals("Kubernetes", message.content().get("topic"));
            assertEquals("Kubernetes", message.naturalLanguageHint());
        }
    }

    @Nested
    @DisplayName("Factory: statusUpdate()")
    class StatusUpdate {

        @Test
        @DisplayName("Crea mensaje con tipo STATUS_UPDATE")
        void creaMensajeConTipoCorrecto() {
            var message = AgentMessage.statusUpdate(
                "DEVELOPER",
                "IN_PROGRESS",
                "Trabajando en feature"
            );

            assertEquals(AgentMessage.TYPE_STATUS_UPDATE, message.type());
            assertEquals("DEVELOPER", message.senderRole());
            assertEquals("IN_PROGRESS", message.content().get("status"));
            assertEquals("Trabajando en feature", message.naturalLanguageHint());
        }
    }

    @Nested
    @DisplayName("Factory: fatalError()")
    class FatalError {

        @Test
        @DisplayName("Crea mensaje con tipo FATAL_ERROR")
        void creaMensajeConTipoCorrecto() {
            var message = AgentMessage.fatalError(
                "QA",
                "ERR_COMPILE",
                "No compila el codigo"
            );

            assertEquals(AgentMessage.TYPE_FATAL_ERROR, message.type());
            assertEquals("QA", message.senderRole());
            assertEquals("ERR_COMPILE", message.content().get("error_code"));
            assertEquals("No compila el codigo", message.content().get("error_message"));
            assertEquals("No compila el codigo", message.naturalLanguageHint());
        }

        @Test
        @DisplayName("isFatalError() retorna true")
        void isFatalErrorTrue() {
            var message = AgentMessage.fatalError("QA", "ERR", "Error");
            assertTrue(message.isFatalError());
        }
    }

    @Nested
    @DisplayName("Factory: ack()")
    class Ack {

        @Test
        @DisplayName("Crea mensaje con tipo ACK")
        void creaMensajeConTipoCorrecto() {
            var message = AgentMessage.ack("DEVELOPER", "msg-123");

            assertEquals(AgentMessage.TYPE_ACK, message.type());
            assertEquals("DEVELOPER", message.senderRole());
            assertEquals("msg-123", message.content().get("original_msg_id"));
            assertNull(message.naturalLanguageHint());
        }

        @Test
        @DisplayName("isAck() retorna true")
        void isAckTrue() {
            var message = AgentMessage.ack("DEV", "msg-1");
            assertTrue(message.isAck());
        }
    }

    @Nested
    @DisplayName("Factory: nack()")
    class Nack {

        @Test
        @DisplayName("Crea mensaje con tipo NACK")
        void creaMensajeConTipoCorrecto() {
            var message = AgentMessage.nack("QA", "msg-456", "Codigo no compila");

            assertEquals(AgentMessage.TYPE_NACK, message.type());
            assertEquals("QA", message.senderRole());
            assertEquals("msg-456", message.content().get("original_msg_id"));
            assertEquals("Codigo no compila", message.content().get("reason"));
            assertEquals("Codigo no compila", message.naturalLanguageHint());
        }

        @Test
        @DisplayName("isNack() retorna true")
        void isNackTrue() {
            var message = AgentMessage.nack("QA", "msg-1", "reason");
            assertTrue(message.isNack());
        }
    }

    @Nested
    @DisplayName("Type Checks")
    class TypeChecks {

        @Test
        @DisplayName("isTaskAssignment() false para otros tipos")
        void isTaskAssignmentFalseParaOtros() {
            assertFalse(AgentMessage.ack("DEV", "id").isTaskAssignment());
            assertFalse(AgentMessage.nack("QA", "id", "r").isTaskAssignment());
            assertFalse(AgentMessage.fatalError("QA", "E", "m").isTaskAssignment());
        }

        @Test
        @DisplayName("isAck() false para otros tipos")
        void isAckFalseParaOtros() {
            assertFalse(AgentMessage.taskAssignment("S", Map.of(), "t").isAck());
            assertFalse(AgentMessage.nack("Q", "i", "r").isAck());
        }

        @Test
        @DisplayName("isNack() false para otros tipos")
        void isNackFalseParaOtros() {
            assertFalse(AgentMessage.taskAssignment("S", Map.of(), "t").isNack());
            assertFalse(AgentMessage.ack("D", "i").isNack());
        }

        @Test
        @DisplayName("isFatalError() false para otros tipos")
        void isFatalErrorFalseParaOtros() {
            assertFalse(AgentMessage.ack("D", "i").isFatalError());
            assertFalse(AgentMessage.statusUpdate("D", "OK", "m").isFatalError());
        }
    }

    @Nested
    @DisplayName("getContent()")
    class GetContent {

        @Test
        @DisplayName("Retorna valor correcto")
        void retornaValorCorrecto() {
            var message = AgentMessage.taskAssignment(
                "SUPERVISOR",
                Map.of("priority", "HIGH", "count", 5),
                "Task"
            );

            assertEquals("HIGH", message.<String>getContent("priority"));
            assertEquals(5, message.<Integer>getContent("count"));
        }

        @Test
        @DisplayName("Retorna null para clave inexistente")
        void retornaNullParaClaveInexistente() {
            var message = AgentMessage.ack("DEV", "id");
            assertNull(message.getContent("no_existe"));
        }

        @Test
        @DisplayName("Retorna default para clave inexistente")
        void retornaDefaultParaClaveInexistente() {
            var message = AgentMessage.ack("DEV", "id");
            assertEquals("default", message.getContent("no_existe", "default"));
            assertEquals(42, message.getContent("no_existe", 42));
        }

        @Test
        @DisplayName("Retorna valor cuando existe (no default)")
        void retornaValorCuandoExiste() {
            var message = AgentMessage.taskAssignment(
                "SUPERVISOR",
                Map.of("priority", "HIGH"),
                "Task"
            );

            assertEquals("HIGH", message.getContent("priority", "LOW"));
        }
    }

    @Nested
    @DisplayName("Record Accessors")
    class RecordAccessors {

        @Test
        @DisplayName("Accessors basicos funcionan")
        void accessorsBasicos() {
            var content = Map.<String, Object>of("key", "value");
            var message = new AgentMessage("CUSTOM", "TESTER", content, "Hint");

            assertEquals("CUSTOM", message.type());
            assertEquals("TESTER", message.senderRole());
            assertEquals(content, message.content());
            assertEquals("Hint", message.naturalLanguageHint());
        }
    }

    @Nested
    @DisplayName("Escenarios de Uso")
    class EscenariosUso {

        @Test
        @DisplayName("Flujo completo: Supervisor -> Developer -> QA")
        void flujoCompleto() {
            // 1. Supervisor asigna tarea
            var assignment = AgentMessage.taskAssignment(
                "SUPERVISOR",
                Map.of("ticket", "JIRA-123", "priority", "HIGH"),
                "Implementar login"
            );
            assertTrue(assignment.isTaskAssignment());

            // 2. Developer confirma recepcion
            var ack = AgentMessage.ack("DEVELOPER", "msg-001");
            assertTrue(ack.isAck());

            // 3. Developer pasa a QA
            var review = AgentMessage.codeReviewRequest(
                "DEVELOPER",
                "/src/Login.java",
                "Revisar implementacion"
            );
            assertEquals("DEVELOPER", review.senderRole());

            // 4. QA rechaza
            var nack = AgentMessage.nack("QA", "msg-002", "Falta validacion");
            assertTrue(nack.isNack());
            assertEquals("Falta validacion", nack.getContent("reason"));
        }
    }
}
