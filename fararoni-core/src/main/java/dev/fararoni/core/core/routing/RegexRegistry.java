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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class RegexRegistry {
    private static final Set<String> TRIVIAL_GREETINGS = Set.of(
        "hola", "hola!", "buenos dias", "buenas tardes", "buenas noches",
        "buen dia", "que tal", "como estas", "como estas?", "que onda",
        "gracias", "muchas gracias", "vale", "perfecto", "listo",
        "entendido", "claro", "si", "no", "adios", "chao",
        "hasta luego", "nos vemos",
        "test", "prueba", "ping", "status", "estado",
        "version", "info", "ayuda", "salir",
        "hello", "hi", "hey", "thanks", "thank you", "ok", "okay",
        "perfect", "got it", "yes", "bye", "goodbye",
        "help", "exit", "quit", "about"
    );

    private static final List<Pattern> SYSTEM_COMMANDS = List.of(
        Pattern.compile("^git\\s+(status|log|branch|diff|remote|fetch|show|blame|shortlog).*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(date|pwd|whoami|hostname|uname|uptime)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(ls|dir|tree)\\s*.*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(muestra|show|list|listar)\\s+(archivos|files|directorio|directory).*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^(clear|cls|limpiar|limpia)$", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> SIMPLE_CODE_GEN = List.of(
        Pattern.compile(
            "^(crea|genera|haz|create|make)\\s+(una?\\s+)?(clase|class|interfaz|interface|record|enum)\\s+\\w+$",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "^(explica|explicame|que\\s+es|que\\s+hace|what\\s+is|explain)\\s+.{1,50}$",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "^(genera|crea|add|agrega)\\s+(getters?|setters?|getter\\s+y\\s+setter).*$",
            Pattern.CASE_INSENSITIVE
        )
    );

    private static final List<Pattern> SECURITY_PATTERNS = List.of(
        Pattern.compile(
            "(password|contrase[nñ]a|passwd|secret|api[_-]?key|apikey|credential|token|bearer)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(vulnerabilidad|vulnerability|exploit|injection|xss|csrf|sqli|sql\\s+injection)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(autenticacion|authentication|oauth|jwt|session|cookie.*security|authorization)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(encrypt|decrypt|hash|cifrar|descifrar|ssl|tls|certificate|certificado)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(security\\s+audit|audit.*security|pentest|penetration|security\\s+review)",
            Pattern.CASE_INSENSITIVE
        )
    );

    private static final List<Pattern> ARCHITECTURE_PATTERNS = List.of(
        Pattern.compile(
            "(analiza|revisa|examina|analyz|review|examin).*(arquitectura|architecture|proyecto|project|sistema|system|codebase)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(refactoriza|reestructura|redise[nñ]a|refactor|restructur|redesign).*(modulo|module|componente|component|capa|layer|paquete|package)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(documenta|genera\\s+documentacion|document).*(todo|completo|proyecto|entero|full|complete|entire)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(migra|migration|upgrade|actualiza).*(version|framework|database|base\\s+de\\s+datos)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(optimiza|optimize).*(rendimiento|performance|todo|completo|sistema|system)",
            Pattern.CASE_INSENSITIVE
        )
    );

    private static final List<Pattern> DESIGN_PATTERNS = List.of(
        Pattern.compile(
            "(dise[nñ]a|design|crea|crear|implementa|implement|construye|build|desarrolla|develop).*(sistema|system|arquitectura|architecture|plataforma|platform|soluci[oó]n|solution|aplicaci[oó]n|application)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(circuit.?breaker|retry.?pattern|fallback|failover|load.?balancer|event.?sourcing|cqrs|saga|pub.?sub|message.?queue)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(integra|integrar|integration|conecta|connect).*(m[uú]ltiples?|varios|different|distintos).*(proveedor|provider|api|servicio|service|sistema|system)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(stripe|paypal|mercado.?pago|braintree|square|adyen).*(y|and|,).*(stripe|paypal|mercado.?pago|braintree|square|adyen)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(mu[eé]strame|show|dame|give|genera|generate).*(c[oó]digo|code|implementaci[oó]n|implementation).*(complet|full|entero|entire)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(alta.?disponibilidad|high.?availability|redundancia|redundancy|escalabilidad|scalability|resiliencia|resilience)",
            Pattern.CASE_INSENSITIVE
        )
    );

    private static final List<Pattern> ANALYSIS_PATTERNS = List.of(
        Pattern.compile(
            "(resumen|resume|summary|entiende|comprende|understand).*(proyecto|c[oó]digo|code|repositorio|repo|contexto|context|aplicaci[oó]n|app)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(estado|status|estructura|structure|diagrama|diagram|mapa|map).*(proyecto|c[oó]digo|sistema|system|aplicaci[oó]n|clases|classes)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(audita|audit|eval[uú]a|evaluate|diagnostica|diagnose).*(c[oó]digo|code|proyecto|project|calidad|quality)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(analiza|analices|analizar|analyze|analysis).*(c[oó]digo|code|proyecto|project|sistema|system|archivo|file|esto|this)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(explica|explicar|explain|describe).*(c[oó]digo|code|que\\s+hace|what.*does|funciona|works)",
            Pattern.CASE_INSENSITIVE
        )
    );

    private static final List<Pattern> DANGEROUS_OPS_PATTERNS = List.of(
        Pattern.compile(
            "git\\s+(commit|push|pull|merge|rebase|reset|revert|cherry-pick|stash|checkout\\s+-b)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(deploy|despliega|publica|publish|release|lanza|sube|upload).*(produccion|production|server|servidor|cloud|nube)?",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(delete|elimina|borra|remove|drop|truncate|destroy|wipe|purge|clean).*(archivo|file|tabla|table|base|database|registro|record|datos|data)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(drop|truncate|delete\\s+from|alter\\s+table|update\\s+.*\\s+set)",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile(
            "(npm|pip|maven|gradle|cargo|gem)\\s+(install|uninstall|remove|update|upgrade)",
            Pattern.CASE_INSENSITIVE
        )
    );

    private static final int COMPLEX_QUERY_CHAR_THRESHOLD = 120;
    private static final int COMPLEX_QUERY_WORD_THRESHOLD = 18;

    public Optional<RoutingPlan> classify(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String normalized = query.toLowerCase().trim();
        long startTime = System.nanoTime();

        for (Pattern p : SECURITY_PATTERNS) {
            if (p.matcher(query).find()) {
                return Optional.of(buildPlan(
                    RoutingPlan.TargetModel.EXPERT,
                    RoutingPlan.DetectedIntent.SECURITY,
                    0.95,
                    "Security topic detected - requires expert analysis",
                    startTime
                ));
            }
        }

        for (Pattern p : ARCHITECTURE_PATTERNS) {
            if (p.matcher(query).find()) {
                return Optional.of(buildPlan(
                    RoutingPlan.TargetModel.EXPERT,
                    RoutingPlan.DetectedIntent.ARCHITECTURE,
                    0.9,
                    "Architecture/refactor pattern - requires expert",
                    startTime
                ));
            }
        }

        for (Pattern p : DESIGN_PATTERNS) {
            if (p.matcher(query).find()) {
                return Optional.of(buildPlan(
                    RoutingPlan.TargetModel.EXPERT,
                    RoutingPlan.DetectedIntent.ARCHITECTURE,
                    0.85,
                    "Design pattern detected - requires expert",
                    startTime
                ));
            }
        }

        for (Pattern p : ANALYSIS_PATTERNS) {
            if (p.matcher(query).find()) {
                return Optional.of(buildPlan(
                    RoutingPlan.TargetModel.EXPERT,
                    RoutingPlan.DetectedIntent.ARCHITECTURE,
                    0.8,
                    "Deep analysis/comprehension task - requires expert",
                    startTime
                ));
            }
        }

        for (Pattern p : DANGEROUS_OPS_PATTERNS) {
            if (p.matcher(query).find()) {
                return Optional.of(buildPlan(
                    RoutingPlan.TargetModel.EXPERT,
                    RoutingPlan.DetectedIntent.CODE_GEN,
                    0.9,
                    "Dangerous operation detected - requires expert precision",
                    startTime
                ));
            }
        }

        int wordCount = normalized.split("\\s+").length;
        if (normalized.length() > COMPLEX_QUERY_CHAR_THRESHOLD || wordCount > COMPLEX_QUERY_WORD_THRESHOLD) {
            return Optional.of(buildPlan(
                RoutingPlan.TargetModel.EXPERT,
                RoutingPlan.DetectedIntent.UNKNOWN,
                0.7,
                "Complex query (length=" + normalized.length() + " chars, words=" + wordCount + ")",
                startTime
            ));
        }

        if (TRIVIAL_GREETINGS.contains(normalized)) {
            return Optional.of(buildPlan(
                RoutingPlan.TargetModel.LOCAL,
                RoutingPlan.DetectedIntent.GREETING,
                0.05,
                "Trivial greeting - exact match",
                startTime
            ));
        }

        for (Pattern p : SYSTEM_COMMANDS) {
            if (p.matcher(query).matches()) {
                return Optional.of(buildPlan(
                    RoutingPlan.TargetModel.LOCAL,
                    RoutingPlan.DetectedIntent.SYSTEM_CMD,
                    0.1,
                    "System command - regex match",
                    startTime
                ));
            }
        }

        for (Pattern p : SIMPLE_CODE_GEN) {
            if (p.matcher(query).matches()) {
                return Optional.of(buildPlan(
                    RoutingPlan.TargetModel.LOCAL,
                    RoutingPlan.DetectedIntent.CODE_GEN,
                    0.3,
                    "Simple code generation - regex match",
                    startTime
                ));
            }
        }

        return Optional.empty();
    }

    public boolean containsSecurityTopic(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return SECURITY_PATTERNS.stream()
            .anyMatch(p -> p.matcher(query).find());
    }

    public boolean isTrivialGreeting(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return TRIVIAL_GREETINGS.contains(query.toLowerCase().trim());
    }

    public String getStats() {
        return String.format(
            "RegexRegistry [greetings=%d, systemCmds=%d, simpleCode=%d, security=%d, arch=%d, design=%d, analysis=%d, dangerous=%d]",
            TRIVIAL_GREETINGS.size(),
            SYSTEM_COMMANDS.size(),
            SIMPLE_CODE_GEN.size(),
            SECURITY_PATTERNS.size(),
            ARCHITECTURE_PATTERNS.size(),
            DESIGN_PATTERNS.size(),
            ANALYSIS_PATTERNS.size(),
            DANGEROUS_OPS_PATTERNS.size()
        );
    }

    private RoutingPlan buildPlan(
            RoutingPlan.TargetModel target,
            RoutingPlan.DetectedIntent intent,
            double complexity,
            String reasoning,
            long startNano) {
        long elapsedNano = System.nanoTime() - startNano;
        long elapsedMs = elapsedNano / 1_000_000;

        return RoutingPlan.builder()
            .target(target)
            .intent(intent)
            .complexity(complexity)
            .requiresInternet(target.mayRequireCloud())
            .reasoning(reasoning)
            .source(RoutingPlan.DecisionSource.LAYER_0_REFLEX)
            .timeMs(elapsedMs)
            .build();
    }
}
