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
package dev.fararoni.core.core.parser;

import dev.fararoni.core.core.surgical.EditBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SurgicalBlockParser {
    private static final Logger logger = Logger.getLogger(SurgicalBlockParser.class.getName());

    private static final Pattern GIT_STYLE_PATTERN = Pattern.compile(
        "<<<<<<< SEARCH\\s*\\n(.*?)=======\\s*\\n(.*?)>>>>>>> REPLACE",
        Pattern.DOTALL
    );

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
        "\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*" +
        "\"search\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*" +
        "\"replace\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*" +
        "(?:,\\s*\"estimatedLine\"\\s*:\\s*(\\d+))?\\s*\\}",
        Pattern.DOTALL
    );

    private int blockCounter = 0;

    public List<EditBlock> parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return List.of();
        }

        List<EditBlock> gitStyleBlocks = parseGitStyle(rawOutput);
        if (!gitStyleBlocks.isEmpty()) {
            logger.info("Parseados " + gitStyleBlocks.size() + " bloques en formato Git-style");
            return gitStyleBlocks;
        }

        List<EditBlock> jsonBlocks = parseJsonFormat(rawOutput);
        if (!jsonBlocks.isEmpty()) {
            logger.info("Parseados " + jsonBlocks.size() + " bloques en formato JSON");
            return jsonBlocks;
        }

        logger.warning("No se encontraron bloques validos en la salida del LLM");
        return List.of();
    }

    private List<EditBlock> parseGitStyle(String input) {
        List<EditBlock> blocks = new ArrayList<>();
        Matcher matcher = GIT_STYLE_PATTERN.matcher(input);

        while (matcher.find()) {
            String search = matcher.group(1).trim();
            String replace = matcher.group(2).trim();

            if (!search.isEmpty()) {
                blocks.add(new EditBlock(
                    "unified-" + (++blockCounter),
                    search,
                    replace,
                    0,
                    -1,
                    -1
                ));
            }
        }

        return blocks;
    }

    private List<EditBlock> parseJsonFormat(String input) {
        List<EditBlock> blocks = new ArrayList<>();
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(input);

        while (matcher.find()) {
            String id = matcher.group(1);
            String search = unescapeJson(matcher.group(2));
            String replace = unescapeJson(matcher.group(3));
            int estimatedLine = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;

            if (!search.isEmpty()) {
                blocks.add(new EditBlock(
                    id,
                    search,
                    replace,
                    estimatedLine,
                    -1,
                    -1
                ));
            }
        }

        return blocks;
    }

    private String unescapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    public void resetCounter() {
        this.blockCounter = 0;
    }
}
