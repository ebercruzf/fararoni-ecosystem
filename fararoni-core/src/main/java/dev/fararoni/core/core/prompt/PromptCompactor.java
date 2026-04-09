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
package dev.fararoni.core.core.prompt;

import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class PromptCompactor {

    private static final Logger LOG = Logger.getLogger(PromptCompactor.class.getName());

    private static final Pattern MODEL_SIZE_PATTERN = Pattern.compile(":(\\d+)b");

    private static final String[] CLOUD_MODELS = {
            "opus", "sonnet", "haiku", "gpt-4", "gpt-4o",
            "deepseek-v3", "deepseek-chat", "deepseek-reasoner"
    };

    private static final String[] LOCAL_MODELS = {
            "qwen", "deepseek-coder", "codestral", "mistral",
            "llama", "phi", "gemma", "starcoder"
    };

    private static final String KALIMAN_COMPACT = """
            {
              "agent": "KALIMAN",
              "role": "SOVEREIGN_ARCHITECT",
              "domain": "Java 25 + Sovereign AI + High-Performance Systems",

              "!!RIGOR!!": {
                "r1": "PROHIBIDO SUPONER — Si no leíste el archivo en esta sesión, NO EXISTE",
                "r2": "CORROBORACIÓN OBLIGATORIA — Cross-check antes de proponer cambios",
                "r3": "Si no puedes verificar, DILO explícitamente — no asumas que funciona"
              },

              "protocolo_RVB": {
                "R": "LEER módulo afectado + dependencias directas. Si no leíste suficiente, PARA y pide más.",
                "V": "VERIFICAR: schema actual si hay DB, topología si hay red. NO asumir configuraciones.",
                "B": "DISEÑAR solo DESPUÉS de R+V completados. Pseudocódigo, no producción."
              },

              "principios": [
                "SOLID + GRASP: desacoplado, interfaces, Single Responsibility",
                "Hexagonal (Ports & Adapters) o DAG para escalabilidad",
                "Java 25: Records, Sealed Types, Virtual Threads, Panama FFM, Pattern Matching",
                "PROHIBIDO: Lombok, FQN inline, LangChain, placeholders",
                "Seguridad: Default DENY, explicit ALLOW. IroncladGuard valida todo flujo nuevo.",
                "Rendimiento: Optimizar para microsegundos. Off-heap con MemorySegment donde aplique."
              ],

              "checks_obligatorios": [
                "ANTI-DUPLICACIÓN: Auditar 37 tools existentes ANTES de crear uno nuevo",
                "TRADE-OFFS: Cada decisión arquitectónica con pros/contras explícitos",
                "DIAGRAMA MERMAID: Obligatorio si el cambio afecta >2 módulos"
              ],

              "contexto_cfararoni": {
                "modulos": 18,
                "core": "fararoni-core: CLI kernel, 22+ commands, 37 tools, Self-Healing, Swarm agents",
                "audio": "fararoni-audio-core: Qwen3-TTS + Whisper STT + Panama FFM (833ns abort)",
                "bus": "Sovereign Bus: NATS JetStream + Chronicle Queue + InMemory (3-tier)",
                "seguridad": "Zero-Trust I/O: IroncladGuard, Kill Switches, TOTP 2FA",
                "package": "dev.fararoni.core (NO com.cfararoni)"
              },

              "output_requerido": [
                "1. Validación R.V.B.: qué leíste, qué verificaste",
                "2. Trade-offs explícitos de la propuesta",
                "3. Check seguridad IroncladGuard",
                "4. Reporte anti-duplicación",
                "5. Diagrama Mermaid.js (si >2 módulos)",
                "6. Diseño en pseudocódigo (NO código producción)",
                "7. Validación SOLID de los 5 principios"
              ],

              "presupuesto": "Max ~2500 tokens. Pseudocódigo e interfaces, no implementación completa.",

              "!!RIGOR_CIERRE!!": "Si no lo verificaste, NO lo afirmes. Suponer desperdicia más tiempo que verificar."
            }
            """;

    private static final String SOLIN_COMPACT = """
            {
              "agent": "SOLIN",
              "role": "SOVEREIGN_ENGINEER",
              "domain": "Java 25 Implementation + High-Concurrency + Zero-Boilerplate",

              "!!RIGOR!!": {
                "r1": "ANTI-SUPOSICIÓN: Si no conoces la implementación actual, PIDE el código PRIMERO",
                "r2": "VALIDACIÓN DE CODEBASE: Analizar código existente ANTES de implementar",
                "r3": "CORROBORACIÓN: Si el Architect sugiere algo que ya existe en el Core, REUSAR no recrear"
              },

              "auditoria_critica": {
                "veto": "Si el diseño tiene acoplamiento fuerte, memory leaks, o viola SOLID → DETENTE y objeta",
                "preguntas": "Si no es óptimo para microsegundos → pide clarificación ANTES de codear"
              },

              "SIP_obligatorio": {
                "header": "MISSION + MÓDULOS_AFECTADOS + ARCHIVOS_ESTIMADOS + NIVEL_RIESGO",
                "checklist": [
                  "READ: Listar archivos leídos. Si no leíste suficiente, PARA.",
                  "MATCH: Confirmar que tu código sigue patrones existentes. Citar archivo referencia.",
                  "DEPS: Listar dependencias nuevas. Justificar cada una. Preferir cero.",
                  "DEUDA: Reportar code smells encontrados durante READ."
                ],
                "plan_por_archivo": "FILE + ACTION(CREATE|MODIFY|DELETE) + QUÉ + POR_QUÉ + DEPS",
                "gate": "Presentar SIP → ESPERAR aprobación → Solo entonces codear"
              },

              "reglas_codigo": [
                "Java 25: Records, Sealed, Pattern Matching, Virtual Threads, Panama FFM",
                "PROHIBIDO: Lombok, FQN inline, variables genéricas (data/info/list)",
                "Inmutabilidad: final > volatile. Scoped Values > ThreadLocal.",
                "SRP estricto. Métodos <20 líneas. Un nivel de indentación max.",
                "Boy Scout Rule: Dejar el workspace más limpio de como lo encontraste.",
                "NO COMMENTS: El código se explica solo. Si necesitas comentar QUÉ hace, refactoriza el nombre."
              ],

              "output_por_batch": {
                "max_archivos": 2,
                "prioridad": "sealed interfaces → records → implementations → tests",
                "cierre_batch": "Batch X/Y complete. Procede para el siguiente?"
              },

              "presupuesto": "Max ~2500 tokens por batch. 2 archivos máximo por respuesta.",

              "!!RIGOR_CIERRE!!": "SIP VALIDADO: Seguí el plan sin desviaciones. Si no lo verifiqué, no lo afirmo."
            }
            """;

    private static final Map<String, String> COMPACT_TEMPLATES = Map.of(
            "kaliman", KALIMAN_COMPACT,
            "solin", SOLIN_COMPACT
    );

    private static final java.util.Set<String> COMPACTION_EXCLUDED_AGENTS = java.util.Set.of(
            "blueprint", "builder", "solin"
    );

    public String compact(String originalPrompt, String modelId, String agentId) {
        if (!requiresCompaction(modelId)) {
            return originalPrompt;
        }

        if (agentId != null && COMPACTION_EXCLUDED_AGENTS.contains(agentId.toLowerCase())) {
            LOG.info("[PROMPT-COMPACTOR] Agente " + agentId
                    + " excluido de compactación (genera código con >>>FILE:)");
            return originalPrompt;
        }

        if (agentId != null) {
            String template = COMPACT_TEMPLATES.get(agentId.toLowerCase());
            if (template != null) {
                LOG.info("[PROMPT-COMPACTOR] Usando template JSON para agente: " + agentId
                        + " (modelo: " + modelId + ")");
                return template;
            }
        }

        LOG.info("[PROMPT-COMPACTOR] Compactación genérica para agente: " + agentId
                + " (modelo: " + modelId + ")");
        return compactGeneric(originalPrompt, agentId);
    }

    public boolean requiresCompaction(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }

        String lower = modelId.toLowerCase();

        for (String cloud : CLOUD_MODELS) {
            if (lower.contains(cloud)) return false;
        }

        for (String local : LOCAL_MODELS) {
            if (lower.contains(local)) return true;
        }

        var matcher = MODEL_SIZE_PATTERN.matcher(lower);
        if (matcher.find()) {
            int sizeB = Integer.parseInt(matcher.group(1));
            return sizeB <= 35;
        }

        return false;
    }

    public boolean isEdgeModel(String modelId) {
        if (modelId == null) return false;
        var matcher = MODEL_SIZE_PATTERN.matcher(modelId.toLowerCase());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1)) <= 7;
        }
        String lower = modelId.toLowerCase();
        return lower.contains("rabbit") || lower.contains(":1b")
                || lower.contains("tiny") || lower.contains("mini");
    }

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "^\\s*(?:" +
            "\\[([A-ZÁÉÍÓÚ][A-ZÁÉÍÓÚ0-9 _\\-:]+)]" +
            "|═{3,}" +
            "|-{3,}" +
            "|#{1,3}\\s+(.+)" +
            "|([A-ZÁÉÍÓÚ][A-ZÁÉÍÓÚ ]+:)\\s*$" +
            ")\\s*$"
    );

    private static final Pattern RULE_LINE = Pattern.compile(
            "^\\s*(?:" +
            "\\d+\\.\\s+.+" +
            "|- .+" +
            "|\\* .+" +
            "|[✓✗☐⚠🚫✅❌]\\s*.+" +
            ")$"
    );

    private static final Pattern KEYWORD_LINE = Pattern.compile(
            "(?i).*(PROHIBIDO|OBLIGATORIO|NUNCA|SIEMPRE|DEBE|CRÍTICO|CRITICAL" +
            "|MANDATORY|FORBIDDEN|REQUIRED|IMPORTANT).*"
    );

    private String compactGeneric(String originalPrompt, String agentId) {
        var sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"agent\": \"").append(agentId != null ? agentId.toUpperCase() : "AGENT").append("\",\n\n");

        sb.append("  \"!!RIGOR!!\": {\n");
        sb.append("    \"r1\": \"PROHIBIDO SUPONER — Si no lo verificaste, no lo afirmes\",\n");
        sb.append("    \"r2\": \"Antes de proponer: ¿leíste el código actual o estás adivinando?\",\n");
        sb.append("    \"r3\": \"Si no puedes verificar, DILO explícitamente\"\n");
        sb.append("  },\n\n");

        String[] lines = originalPrompt.split("\n");
        String currentSection = "general";
        var sectionRules = new java.util.LinkedHashMap<String, java.util.List<String>>();
        sectionRules.put(currentSection, new java.util.ArrayList<>());

        String identity = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (identity == null && !trimmed.startsWith("[") && !trimmed.startsWith("═")
                    && !trimmed.startsWith("-") && trimmed.length() > 10) {
                identity = escapeJson(trimmed);
                continue;
            }

            var headerMatcher = SECTION_HEADER.matcher(trimmed);
            if (headerMatcher.matches()) {
                String sectionName = headerMatcher.group(1);
                if (sectionName == null) sectionName = headerMatcher.group(2);
                if (sectionName == null) sectionName = headerMatcher.group(3);
                if (sectionName == null) continue;

                currentSection = sectionName.trim()
                        .toLowerCase()
                        .replaceAll("[^a-záéíóúñ0-9]+", "_")
                        .replaceAll("^_|_$", "");

                if (!currentSection.isEmpty()) {
                    sectionRules.putIfAbsent(currentSection, new java.util.ArrayList<>());
                }
                continue;
            }

            boolean isRule = RULE_LINE.matcher(trimmed).matches();
            boolean isKeyword = KEYWORD_LINE.matcher(trimmed).matches();

            if (isRule || isKeyword) {
                String clean = escapeJson(trimmed);
                if (clean.length() > 150) clean = clean.substring(0, 150) + "...";
                sectionRules.computeIfAbsent(currentSection, k -> new java.util.ArrayList<>())
                        .add(clean);
            }
        }

        if (identity != null) {
            sb.append("  \"identidad\": \"").append(identity).append("\",\n\n");
        }

        int sectionCount = 0;
        for (var entry : sectionRules.entrySet()) {
            var rules = entry.getValue();
            if (rules.isEmpty()) continue;
            if (sectionCount >= 10) break;

            sb.append("  \"").append(entry.getKey()).append("\": [\n");
            int ruleLimit = Math.min(rules.size(), 8);
            for (int i = 0; i < ruleLimit; i++) {
                sb.append("    \"").append(rules.get(i)).append("\"");
                if (i < ruleLimit - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n\n");
            sectionCount++;
        }

        sb.append("  \"!!RIGOR_CIERRE!!\": \"Si no lo verificaste, NO lo afirmes. Suponer desperdicia más tiempo que verificar.\"\n");
        sb.append("}");

        return sb.toString();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "'")
                .replace("\t", " ")
                .replace("\r", "");
    }
}
