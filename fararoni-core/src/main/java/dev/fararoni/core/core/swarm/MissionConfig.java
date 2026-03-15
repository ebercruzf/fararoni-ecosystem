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
package dev.fararoni.core.core.swarm;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record MissionConfig(
    DefconLevel level,
    boolean enableAnalyst,
    boolean enableArchitect,
    boolean enableQA
) {
    public enum DefconLevel {
        DEFCON_5("[+] RUTINA"),
        DEFCON_3("[!] ESTANDAR"),
        DEFCON_1("[!!] CRITICO");

        private final String display;

        DefconLevel(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    public static MissionConfig fastMode() {
        return new MissionConfig(DefconLevel.DEFCON_5, false, false, false);
    }

    public static MissionConfig standardMode() {
        return new MissionConfig(DefconLevel.DEFCON_3, true, false, true);
    }

    public static MissionConfig fullSquad() {
        return new MissionConfig(DefconLevel.DEFCON_1, true, true, true);
    }

    public String toSimpleFormat() {
        return String.format("DEFCON=%s|ANALYST=%b|ARCHITECT=%b|QA=%b",
            level.name(), enableAnalyst, enableArchitect, enableQA);
    }

    public static MissionConfig fromSimpleFormat(String data) {
        if (data == null || data.isBlank()) {
            return fastMode();
        }

        try {
            DefconLevel level = DefconLevel.DEFCON_5;
            boolean analyst = false;
            boolean architect = false;
            boolean qa = false;

            for (String part : data.split("\\|")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    switch (kv[0]) {
                        case "DEFCON" -> level = DefconLevel.valueOf(kv[1]);
                        case "ANALYST" -> analyst = Boolean.parseBoolean(kv[1]);
                        case "ARCHITECT" -> architect = Boolean.parseBoolean(kv[1]);
                        case "QA" -> qa = Boolean.parseBoolean(kv[1]);
                    }
                }
            }
            return new MissionConfig(level, analyst, architect, qa);
        } catch (Exception e) {
            return fastMode();
        }
    }

    public static MissionConfig fromAssessment(String assessment) {
        if (assessment == null) {
            return fastMode();
        }

        String upper = assessment.toUpperCase();
        if (upper.contains("HIGH") || upper.contains("CRÍTICO") || upper.contains("CRITICAL")) {
            return fullSquad();
        } else if (upper.contains("MEDIUM") || upper.contains("MEDIO") || upper.contains("STANDARD")) {
            return standardMode();
        } else {
            return fastMode();
        }
    }

    public String getNextAgent(String currentAgent) {
        return switch (currentAgent.toUpperCase()) {
            case "COMMANDER" -> enableAnalyst ? "INTEL" : "BLUEPRINT";

            case "INTEL" -> enableArchitect ? "STRATEGIST" : "BLUEPRINT";

            case "STRATEGIST" -> "BLUEPRINT";

            case "BLUEPRINT" -> "BUILDER";

            case "BUILDER" -> enableQA ? "SENTINEL" : "OPERATOR";

            case "SENTINEL" -> "OPERATOR";

            case "OPERATOR" -> "COMMANDER";

            case "PM" -> enableAnalyst ? "INTEL" : "BLUEPRINT";
            case "ANALYST" -> enableArchitect ? "STRATEGIST" : "BLUEPRINT";
            case "ARCH" -> "BLUEPRINT";
            case "TL" -> "BUILDER";
            case "DEV" -> enableQA ? "SENTINEL" : "OPERATOR";
            case "QA" -> "OPERATOR";
            case "SRE" -> "COMMANDER";

            default -> "COMMANDER";
        };
    }

    public boolean isAgentActive(String agentId) {
        return switch (agentId.toUpperCase()) {
            case "COMMANDER", "BLUEPRINT", "BUILDER", "OPERATOR" -> true;
            case "INTEL" -> enableAnalyst;
            case "STRATEGIST" -> enableArchitect;
            case "SENTINEL" -> enableQA;

            case "PM", "TL", "DEV", "SRE" -> true;
            case "ANALYST" -> enableAnalyst;
            case "ARCH" -> enableArchitect;
            case "QA" -> enableQA;

            default -> false;
        };
    }

    public String getAgentChain() {
        StringBuilder chain = new StringBuilder("COMMANDER");
        String current = "COMMANDER";

        for (int i = 0; i < 10; i++) {
            String next = getNextAgent(current);
            if (next.equals("COMMANDER")) break;
            chain.append(" → ").append(next);
            current = next;
        }

        return chain.toString();
    }

    public int activeAgentCount() {
        int count = 4;
        if (enableAnalyst) count++;
        if (enableArchitect) count++;
        if (enableQA) count++;
        return count;
    }

    public String describe() {
        return String.format("%s - %d agentes activos [ANALYST=%b, ARCH=%b, QA=%b]",
            level.getDisplay(), activeAgentCount(), enableAnalyst, enableArchitect, enableQA);
    }

    @Override
    public String toString() {
        return describe();
    }
}
