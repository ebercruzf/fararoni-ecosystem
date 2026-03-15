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
package dev.fararoni.core.core.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class HtmlScriptExtractor {
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "<script([^>]*)>([\\s\\S]*?)</script>",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TYPE_PATTERN = Pattern.compile(
        "type\\s*=\\s*[\"']([^\"']*)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LANG_PATTERN = Pattern.compile(
        "lang\\s*=\\s*[\"']([^\"']*)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SRC_PATTERN = Pattern.compile(
        "src\\s*=\\s*[\"']([^\"']*)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    public List<ScriptBlock> extract(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return List.of();
        }

        List<ScriptBlock> scripts = new ArrayList<>();
        Matcher matcher = SCRIPT_PATTERN.matcher(htmlContent);

        while (matcher.find()) {
            String attributes = matcher.group(1);
            String content = matcher.group(2);
            int startOffset = matcher.start();

            if (hasExternalSource(attributes)) {
                continue;
            }

            if (content == null || content.isBlank()) {
                continue;
            }

            ScriptType type = determineScriptType(attributes);

            int startLine = countLines(htmlContent, 0, startOffset) + 1;

            scripts.add(new ScriptBlock(
                content.trim(),
                type,
                startLine,
                startOffset,
                attributes
            ));
        }

        return scripts;
    }

    public boolean hasScripts(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return SCRIPT_PATTERN.matcher(content).find();
    }

    public boolean isHtmlLikeExtension(String extension) {
        if (extension == null) {
            return false;
        }
        String ext = extension.toLowerCase();
        return "html".equals(ext) || "htm".equals(ext) ||
               "vue".equals(ext) || "svelte".equals(ext);
    }

    private boolean hasExternalSource(String attributes) {
        if (attributes == null || attributes.isBlank()) {
            return false;
        }
        return SRC_PATTERN.matcher(attributes).find();
    }

    private ScriptType determineScriptType(String attributes) {
        if (attributes == null || attributes.isBlank()) {
            return ScriptType.JAVASCRIPT;
        }

        Matcher langMatcher = LANG_PATTERN.matcher(attributes);
        if (langMatcher.find()) {
            String lang = langMatcher.group(1).toLowerCase();
            if ("ts".equals(lang) || "typescript".equals(lang)) {
                return ScriptType.TYPESCRIPT;
            }
            if ("tsx".equals(lang)) {
                return ScriptType.TSX;
            }
            if ("jsx".equals(lang)) {
                return ScriptType.JSX;
            }
        }

        Matcher typeMatcher = TYPE_PATTERN.matcher(attributes);
        if (typeMatcher.find()) {
            String type = typeMatcher.group(1).toLowerCase();
            if (type.contains("typescript")) {
                return ScriptType.TYPESCRIPT;
            }
            if ("module".equals(type)) {
                return ScriptType.MODULE;
            }
        }

        return ScriptType.JAVASCRIPT;
    }

    private int countLines(String content, int start, int end) {
        int lines = 0;
        for (int i = start; i < end && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    public enum ScriptType {
        JAVASCRIPT("js"),
        TYPESCRIPT("ts"),
        JSX("jsx"),
        TSX("tsx"),
        MODULE("js");

        private final String extension;

        ScriptType(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }

        public boolean isTypeScript() {
            return this == TYPESCRIPT || this == TSX;
        }
    }

    public record ScriptBlock(
        String content,
        ScriptType type,
        int startLine,
        int startOffset,
        String attributes
    ) {
        public String getExtension() {
            return type.getExtension();
        }

        public boolean isTypeScript() {
            return type.isTypeScript();
        }

        public int tokenEstimate() {
            return content != null ? content.length() / 4 : 0;
        }
    }
}
