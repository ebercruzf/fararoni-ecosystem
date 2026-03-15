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
package dev.fararoni.core.core.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class RegexRegistryTest {
    private RegexRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RegexRegistry();
    }

    @Nested
    @DisplayName("Triviales -> LOCAL")
    class TrivialTests {
        @Test
        @DisplayName("Saludos basicos van a LOCAL")
        void saludosBasicos() {
            assertLocal("hola");
            assertLocal("hello");
            assertLocal("hi");
            assertLocal("gracias");
            assertLocal("ok");
        }

        @Test
        @DisplayName("Ping/Status van a LOCAL (NUEVO)")
        void pingStatus() {
            assertLocal("ping");
            assertLocal("test");
            assertLocal("prueba");
            assertLocal("status");
            assertLocal("version");
        }

        @Test
        @DisplayName("Ayuda/Exit van a LOCAL (NUEVO)")
        void ayudaExit() {
            assertLocal("ayuda");
            assertLocal("help");
            assertLocal("exit");
            assertLocal("salir");
            assertLocal("quit");
        }
    }

    @Nested
    @DisplayName("Git Lectura -> LOCAL")
    class GitLecturaTests {
        @Test
        @DisplayName("git status va a LOCAL")
        void gitStatus() {
            assertLocal("git status");
        }

        @Test
        @DisplayName("git log va a LOCAL")
        void gitLog() {
            assertLocal("git log");
            assertLocal("git log --oneline");
        }

        @Test
        @DisplayName("git diff va a LOCAL")
        void gitDiff() {
            assertLocal("git diff");
            assertLocal("git diff HEAD~1");
        }

        @Test
        @DisplayName("git branch va a LOCAL")
        void gitBranch() {
            assertLocal("git branch");
            assertLocal("git branch -a");
        }
    }

    @Nested
    @DisplayName("Git Escritura -> EXPERT (NUEVO)")
    class GitEscrituraTests {
        @Test
        @DisplayName("git commit va a EXPERT")
        void gitCommit() {
            assertExpert("git commit -m 'mensaje'");
            assertExpert("git commit --amend");
        }

        @Test
        @DisplayName("git push va a EXPERT")
        void gitPush() {
            assertExpert("git push");
            assertExpert("git push origin main");
        }

        @Test
        @DisplayName("git merge va a EXPERT")
        void gitMerge() {
            assertExpert("git merge feature-branch");
        }

        @Test
        @DisplayName("git rebase va a EXPERT")
        void gitRebase() {
            assertExpert("git rebase main");
            assertExpert("git rebase -i HEAD~3");
        }

        @Test
        @DisplayName("git reset va a EXPERT")
        void gitReset() {
            assertExpert("git reset --hard HEAD~1");
        }
    }

    @Nested
    @DisplayName("Operaciones Peligrosas -> EXPERT (NUEVO)")
    class DangerousOpsTests {
        @Test
        @DisplayName("Deploy va a EXPERT")
        void deploy() {
            assertExpert("deploy a producción");
            assertExpert("despliega el servicio");
            assertExpert("publica la app");
        }

        @Test
        @DisplayName("Delete/elimina va a EXPERT")
        void delete() {
            assertExpert("elimina la tabla users");
            assertExpert("borra el archivo config");
            assertExpert("delete from users");
        }

        @Test
        @DisplayName("SQL destructivo va a EXPERT")
        void sqlDestructivo() {
            assertExpert("DROP TABLE users");
            assertExpert("TRUNCATE TABLE logs");
        }

        @Test
        @DisplayName("Package managers va a EXPERT")
        void packageManagers() {
            assertExpert("npm install lodash");
            assertExpert("pip install requests");
            assertExpert("maven install");
        }
    }

    @Nested
    @DisplayName("Analisis/Comprension -> EXPERT (NUEVO)")
    class AnalysisTests {
        @Test
        @DisplayName("Resumen de proyecto va a EXPERT")
        void resumen() {
            assertExpert("resumen del proyecto");
            assertExpert("resume el código");
            assertExpert("summary of the application");
        }

        @Test
        @DisplayName("Estructura/diagrama va a EXPERT")
        void estructura() {
            assertExpert("diagrama del sistema");
            assertExpert("estructura del proyecto");
            assertExpert("mapa de clases");
        }

        @Test
        @DisplayName("Auditoria va a EXPERT")
        void auditoria() {
            assertExpert("audita el código");
            assertExpert("evalua la calidad del proyecto");
        }

        @Test
        @DisplayName("FIX 9.4: Analiza/analices con codigo va a EXPERT")
        void analizaAnalices() {
            assertExpert("Hola, necesito que analices este codigo complejo");
            assertExpert("analiza el codigo del proyecto");
            assertExpert("analyze this code please");
            assertExpert("explica que hace este codigo");
            assertExpert("explain how this code works");
        }
    }

    @Nested
    @DisplayName("Seguridad -> EXPERT")
    class SecurityTests {
        @Test
        @DisplayName("Password/token va a EXPERT")
        void credenciales() {
            assertExpert("como manejo el password");
            assertExpert("genera un api_key");
            assertExpert("donde guardo el token");
        }

        @Test
        @DisplayName("Vulnerabilidades va a EXPERT")
        void vulnerabilidades() {
            assertExpert("hay vulnerabilidades en el código");
            assertExpert("revisa por sql injection");
        }
    }

    @Nested
    @DisplayName("Diseño/Arquitectura -> EXPERT")
    class DesignTests {
        @Test
        @DisplayName("Diseña sistema va a EXPERT")
        void disenaSistema() {
            assertExpert("diseña un sistema de pagos");
            assertExpert("crea una arquitectura microservicios");
        }

        @Test
        @DisplayName("Patrones avanzados va a EXPERT")
        void patronesAvanzados() {
            assertExpert("implementa circuit-breaker");
            assertExpert("usa event-sourcing");
            assertExpert("aplica cqrs");
        }

        @Test
        @DisplayName("Multi-proveedor va a EXPERT")
        void multiProveedor() {
            assertExpert("integra Stripe y PayPal");
            assertExpert("conecta con Mercado Pago y Stripe");
        }
    }

    @Nested
    @DisplayName("Longitud > 120 chars -> EXPERT")
    class LengthTests {
        @Test
        @DisplayName("Query larga va a EXPERT")
        void queryLarga() {
            String longQuery = "Necesito que me ayudes a crear una aplicación completa con autenticación, " +
                    "base de datos, API REST y frontend en React con todas las mejores prácticas";
            assertTrue(longQuery.length() > 120, "Query debe tener >120 chars");
            assertExpert(longQuery);
        }

        @Test
        @DisplayName("Query corta no aplica longitud")
        void queryCorta() {
            assertLocal("hola");
        }
    }

    @Nested
    @DisplayName("Código Simple -> LOCAL")
    class SimpleCodeTests {
        @Test
        @DisplayName("Crea clase simple va a LOCAL")
        void creaClase() {
            assertLocal("crea una clase Usuario");
            assertLocal("genera una interfaz Service");
        }
    }

    private void assertLocal(String query) {
        Optional<RoutingPlan> result = registry.classify(query);
        assertTrue(result.isPresent(), "Debe tener resultado para: " + query);
        assertEquals(RoutingPlan.TargetModel.LOCAL, result.get().target(),
                "'" + query + "' debería ir a LOCAL, pero fue: " + result.get().reasoning());
    }

    private void assertExpert(String query) {
        Optional<RoutingPlan> result = registry.classify(query);
        assertTrue(result.isPresent(), "Debe tener resultado para: " + query);
        assertEquals(RoutingPlan.TargetModel.EXPERT, result.get().target(),
                "'" + query + "' debería ir a EXPERT, pero fue: " + result.get().reasoning());
    }

    @Test
    @DisplayName("Stats muestra todos los contadores")
    void stats() {
        String stats = registry.getStats();
        System.out.println("RegexRegistry Stats: " + stats);
        assertTrue(stats.contains("greetings="));
        assertTrue(stats.contains("analysis="));
        assertTrue(stats.contains("dangerous="));
    }
}
