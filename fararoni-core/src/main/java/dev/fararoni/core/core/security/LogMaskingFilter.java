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
package dev.fararoni.core.core.security;

import java.util.regex.Pattern;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class LogMaskingFilter {
    private static final Pattern[] SENSITIVE_PATTERNS = {
        Pattern.compile("sk-[a-zA-Z0-9\\-_]{20,}"),

        Pattern.compile("sk-ant-[a-zA-Z0-9\\-_]{20,}"),

        Pattern.compile("Bearer\\s+[a-zA-Z0-9\\-_\\.]+", Pattern.CASE_INSENSITIVE),

        Pattern.compile("Authorization:\\s*[^\\s,;]+", Pattern.CASE_INSENSITIVE),

        Pattern.compile("eyJ[a-zA-Z0-9\\-_]+\\.[a-zA-Z0-9\\-_]+\\.[a-zA-Z0-9\\-_]+"),

        Pattern.compile("://[^:]+:[^@]+@"),

        Pattern.compile("(api[_-]?key|secret|password|token)\\s*[=:]\\s*[^\\s,;\"']+", Pattern.CASE_INSENSITIVE),

        Pattern.compile("ENC:[a-zA-Z0-9+/=]{32,}")
    };

    private static final String MASK = "***MASKED***";
    private static final String URL_MASK = "://***:***@";

    private LogMaskingFilter() {
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String result = message;

        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.pattern().contains("://")) {
                result = pattern.matcher(result).replaceAll(URL_MASK);
            } else {
                result = pattern.matcher(result).replaceAll(MASK);
            }
        }

        return result;
    }

    public static boolean containsSensitiveData(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(message).find()) {
                return true;
            }
        }

        return false;
    }

    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return MASK;
        }

        String prefix = apiKey.substring(0, 7);
        String suffix = apiKey.substring(apiKey.length() - 4);
        return prefix + "..." + suffix;
    }

    public static String maskBearerToken(String token) {
        if (token == null || token.length() < 8) {
            return MASK;
        }

        return token.substring(0, 8) + "..." + MASK;
    }

    public static String formatAndMask(String format, Object... args) {
        if (format == null) {
            return null;
        }

        Object[] maskedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) {
                maskedArgs[i] = mask((String) args[i]);
            } else if (args[i] != null) {
                maskedArgs[i] = mask(args[i].toString());
            } else {
                maskedArgs[i] = null;
            }
        }

        String result = format;
        for (Object arg : maskedArgs) {
            int idx = result.indexOf("{}");
            if (idx >= 0) {
                result = result.substring(0, idx) + (arg != null ? arg : "null") + result.substring(idx + 2);
            }
        }

        return result;
    }
}
