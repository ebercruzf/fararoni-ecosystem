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
package dev.fararoni.core.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ResponseHallucinationGuard {
    private static final Logger LOG = Logger.getLogger(ResponseHallucinationGuard.class.getName());
    private static final DateTimeFormatter AUDIT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int HALLUCINATION_THRESHOLD = 5;

    private static final String FILTERED_NOTICE =
        "[Fararoni: Respuesta filtrada — el modelo genero contenido no relacionado " +
        "con tu solicitud. Reintenta tu pregunta o reformulala.]";

    private static final Path AUDIT_DIR = Path.of(System.getProperty("user.home"), ".fararoni", "security");
    private static final Path AUDIT_LOG = AUDIT_DIR.resolve("audit.log");

    private static final Pattern DATE_RANGE = Pattern.compile(
        "(?i)(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|" +
        "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|" +
        "ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|jun(?:io)?|" +
        "jul(?:io)?|ago(?:sto)?|sep(?:tiembre)?|oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?)" +
        "\\s+\\d{4}\\s*[–\\-]\\s*" +
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|" +
        "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|" +
        "ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|jun(?:io)?|" +
        "jul(?:io)?|ago(?:sto)?|sep(?:tiembre)?|oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?|" +
        "present|actual|actualidad)" +
        "\\s*\\d{0,4}"
    );

    private static final Pattern ROLE_TITLE = Pattern.compile(
        "(?i)\\b(role|position|puesto|cargo|titulo|title)\\s*:\\s*\\w+"
    );

    private static final Pattern CV_BULLET = Pattern.compile(
        "(?i)[•\\-\\*]\\s*(manage[d]?|increase[d]?|decrease[d]?|achieve[d]?|" +
        "responsible|reporting|maintain(ed)?|develop(ed)?|implement(ed)?|coordinate[d]?|" +
        "supervise[d]?|led|directed|oversaw|delivered|designed|created|built|" +
        "administer(ed)?|support(ed)?|operate[d]?|handle[d]?|conduct(ed)?|ensure[d]?|" +
        "establish(ed)?|improve[d]?|optimize[d]?|deploy(ed)?|migrat(ed|ion)|" +
        "administr[oóe]|desarroll[oóe]|implement[oóe]|coordin[oóe]|supervis[oóe]|" +
        "gestion[oóe]|diseñ[oóe]|constru[iyí]|mejor[oóe]|logr[oóe]|cre[oóe])" +
        ".*\\b\\d+[%]?"
    );

    private static final Pattern JOB_TITLE_YEAR = Pattern.compile(
        "(?i)\\b(technician|engineer|manager|analyst|developer|consultant|director|" +
        "coordinator|supervisor|architect|specialist|administrator|operator|intern|" +
        "lead|fullstack|frontend|backend|devops|scrum\\s*master|product\\s*owner|" +
        "ingeniero|tecnico|gerente|analista|desarrollador|consultor|coordinador|" +
        "supervisor|arquitecto|especialista|administrador|operador|practicante|lider)\\b" +
        ".*\\b(19|20)\\d{2}\\b"
    );

    private static final Pattern CORPORATE_LOCATION = Pattern.compile(
        "(?i)[A-Z][A-Za-zÀ-ÿ\\s&.]+,\\s*[A-Z][A-Za-zÀ-ÿ\\s]+,\\s*[A-Z][A-Za-zÀ-ÿ\\s]+\\.?\\s*$",
        Pattern.MULTILINE
    );

    private static final Pattern EDUCATION = Pattern.compile(
        "(?i)\\b(bachelor|master|phd|licenciatura|ingenieria|maestria|doctorado|" +
        "degree|diploma|certification|certificacion|b\\.?s\\.?|m\\.?s\\.?|m\\.?b\\.?a\\.?)\\b" +
        ".*\\b(university|universidad|institute|instituto|college|school|" +
        "tecnologico|politecnico|autonoma|unam|itesm|tec)\\b"
    );

    private static final Pattern USING_TECH_STACK = Pattern.compile(
        "(?i)^\\s*using\\s*:.*(?:,.*){4,}",
        Pattern.MULTILINE
    );

    private static final Pattern CONVERSATIONAL_QUESTION = Pattern.compile(
        "(?i)(a\\s+qu[eé]\\s+te\\s+refieres|qu[eé]\\s+(significa|es|son|quiere\\s+decir)|" +
        "por\\s*qu[eé]|c[oó]mo\\s+(funciona|es|se)|expl[ií]ca|what\\s+(do\\s+you\\s+mean|is|are|does)|" +
        "why\\s+(is|are|did|does|do)|how\\s+(does|do|is|are)|can\\s+you\\s+explain|" +
        "could\\s+you\\s+(explain|clarify)|tell\\s+me\\s+(about|why|how|what))"
    );

    private static final Pattern SYSTEM_OUTPUT_LINE = Pattern.compile(
        "^\\s*\\[(INFO|ERROR|WARNING|WARN)\\]\\s", Pattern.MULTILINE
    );

    private static final String NON_SEQUITUR_NOTICE =
        "[Fararoni: El modelo no respondio tu pregunta y ejecuto un comando no relacionado. " +
        "Reintenta tu pregunta.]";

    private static final Pattern CODE_BLOCK = Pattern.compile("```[\\s\\S]*?```");

    private static final Pattern XML_TAG = Pattern.compile("<[a-zA-Z0-9_]+>[\\s\\S]*?</[a-zA-Z0-9_]+>");

    private ResponseHallucinationGuard() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int score(String response) {
        if (response == null || response.length() < 50) return 0;

        String textOnly = removeCodeBlocks(response);
        if (textOnly.isBlank()) return 0;

        int score = 0;

        score += countMatches(textOnly, DATE_RANGE) * 3;
        score += countMatches(textOnly, ROLE_TITLE) * 3;
        score += countMatches(textOnly, CV_BULLET) * 2;
        score += countMatches(textOnly, JOB_TITLE_YEAR) * 1;
        score += countMatches(textOnly, CORPORATE_LOCATION) * 2;
        score += countMatches(textOnly, EDUCATION) * 2;
        score += countMatches(textOnly, USING_TECH_STACK) * 2;

        return score;
    }

    public static String filter(String userPrompt, String response) {
        if (response == null || response.isBlank()) return response;

        if (isNonSequitur(userPrompt, response)) {
            LOG.warning("[HALLUCINATION-GUARD] Non-sequitur detectado. " +
                "Pregunta conversacional ignorada por el LLM.");
            auditLog(0, "[NON-SEQUITUR] prompt=" + userPrompt + " | response=" + response);
            return NON_SEQUITUR_NOTICE;
        }

        return filter(response);
    }

    public static String filter(String response) {
        if (response == null || response.isBlank()) return response;

        int hallucinationScore = score(response);

        if (hallucinationScore >= HALLUCINATION_THRESHOLD) {
            LOG.warning("[HALLUCINATION-GUARD] Alucinacion detectada (score=" +
                hallucinationScore + "). Ejecutando extraccion quirurgica.");
            auditLog(hallucinationScore, response);

            String usefulPayload = extractUsefulPayload(response);

            if (usefulPayload.isBlank()) {
                return FILTERED_NOTICE;
            }

            return FILTERED_NOTICE + "\n\n" + usefulPayload;
        }

        return response;
    }

    public static boolean isNonSequitur(String userPrompt, String response) {
        if (userPrompt == null || response == null) return false;

        boolean isConversational = CONVERSATIONAL_QUESTION.matcher(userPrompt).find();
        if (!isConversational) return false;

        String textOnly = removeCodeBlocks(response);
        String[] lines = textOnly.split("\n");

        int totalLines = 0;
        int buildLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            totalLines++;
            if (SYSTEM_OUTPUT_LINE.matcher(line).find() ||
                trimmed.startsWith("---") ||
                trimmed.startsWith("BUILD SUCCESS") ||
                trimmed.startsWith("BUILD FAILURE") ||
                trimmed.startsWith("Nivel ") ||
                trimmed.matches("\\[exit_code:.*") ||
                trimmed.matches("(?i).*comando '.*' no esta en la whitelist.*") ||
                trimmed.matches("Error ejecutando .*:.*")) {
                buildLines++;
            }
        }

        if (totalLines == 0) return false;

        double buildRatio = (double) buildLines / totalLines;
        return buildRatio > 0.80;
    }

    public static boolean isHallucination(String response) {
        return score(response) >= HALLUCINATION_THRESHOLD;
    }

    private static String extractUsefulPayload(String text) {
        StringBuilder preserved = new StringBuilder();

        Matcher codeMatcher = CODE_BLOCK.matcher(text);
        while (codeMatcher.find()) {
            preserved.append(codeMatcher.group()).append("\n\n");
        }

        Matcher xmlMatcher = XML_TAG.matcher(text);
        while (xmlMatcher.find()) {
            String match = xmlMatcher.group();
            if (!preserved.toString().contains(match)) {
                preserved.append(match).append("\n\n");
            }
        }

        return preserved.toString().trim();
    }

    private static String removeCodeBlocks(String text) {
        return CODE_BLOCK.matcher(text).replaceAll(" ");
    }

    private static int countMatches(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static void auditLog(int score, String response) {
        try {
            Files.createDirectories(AUDIT_DIR);
            String snippet = response.length() > 300
                ? response.substring(0, 300) + "..."
                : response;
            String entry = String.format("[%s] [HALLUCINATION] score=%d snippet=%s%n",
                LocalDateTime.now().format(AUDIT_FMT), score,
                snippet.replace("\n", "\\n"));
            Files.writeString(AUDIT_LOG, entry,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.warning("[HALLUCINATION-GUARD] Error escribiendo audit log: " + e.getMessage());
        }
    }
}
