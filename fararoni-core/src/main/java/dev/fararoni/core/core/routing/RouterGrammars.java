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

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class RouterGrammars {
    private RouterGrammars() {
        throw new AssertionError("RouterGrammars is a utility class");
    }

    public static final String ROUTING_DECISION_GRAMMAR = """
        root ::= "{" ws complexity-pair "," ws intent-pair "}" ws
        complexity-pair ::= "\\"complexity\\"" ws ":" ws complexity-value
        intent-pair ::= "\\"intent\\"" ws ":" ws intent-value
        complexity-value ::= "0" "." digit digit?
        intent-value ::= "\\"GREETING\\"" | "\\"SYSTEM_CMD\\"" | "\\"CONFIG\\"" | "\\"CODE_GEN\\"" | "\\"CODE_READ\\"" | "\\"DEBUG\\"" | "\\"REFACTOR\\"" | "\\"ARCHITECTURE\\"" | "\\"SECURITY\\"" | "\\"DOCUMENTATION\\"" | "\\"UNKNOWN\\""
        digit ::= [0-9]
        ws ::= [ \\t\\n]*
        """;

    public static final String COMPLEXITY_ONLY_GRAMMAR = """
        root ::= "0" "." digit digit?
        digit ::= [0-9]
        """;

    public static final String BOOLEAN_GRAMMAR = """
        root ::= "true" | "false"
        """;

    public static final String YES_NO_GRAMMAR = """
        root ::= "\\"si\\"" | "\\"no\\""
        """;

    public static final String ROUTING_WITH_REASONING_GRAMMAR = """
        root ::= "{" ws complexity-pair "," ws intent-pair "," ws reasoning-pair "}" ws
        complexity-pair ::= "\\"complexity\\"" ws ":" ws complexity-value
        intent-pair ::= "\\"intent\\"" ws ":" ws intent-value
        reasoning-pair ::= "\\"reasoning\\"" ws ":" ws "\\"" reasoning-text "\\""
        complexity-value ::= "0" "." digit digit?
        intent-value ::= "\\"GREETING\\"" | "\\"SYSTEM_CMD\\"" | "\\"CONFIG\\"" | "\\"CODE_GEN\\"" | "\\"CODE_READ\\"" | "\\"DEBUG\\"" | "\\"REFACTOR\\"" | "\\"ARCHITECTURE\\"" | "\\"SECURITY\\"" | "\\"DOCUMENTATION\\"" | "\\"UNKNOWN\\""
        reasoning-text ::= [a-zA-Z0-9 .,!?-]{1,100}
        digit ::= [0-9]
        ws ::= [ \\t\\n]*
        """;

    public static boolean isValidBasicSyntax(String grammar) {
        if (grammar == null || grammar.isBlank()) {
            return false;
        }

        if (!grammar.contains("root ::=")) {
            return false;
        }

        return grammar.contains("::=");
    }

    public static String getRoutingGrammar(boolean includeReasoning) {
        return includeReasoning
            ? ROUTING_WITH_REASONING_GRAMMAR
            : ROUTING_DECISION_GRAMMAR;
    }
}
