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
package dev.fararoni.core.core.skills;

public final class ServerStartupDetector {
    private ServerStartupDetector() {}

    private static final String[] STARTUP_INDICATORS = {
        "Started ",
        "Tomcat started on port",
        "Netty started on port",
        "Tomcat initialized with port",
        "started on port",

        "listening on port",
        "Server running on",
        "ready on http",
        "Listening on",

        "Uvicorn running on",
        "Starting development server",
        "Booting worker",

        "Application started",
        "Server started",
    };

    public static boolean isServerStarted(String partialOutput) {
        if (partialOutput == null || partialOutput.isEmpty()) {
            return false;
        }
        for (String indicator : STARTUP_INDICATORS) {
            if (partialOutput.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    public static String extractPort(String partialOutput) {
        if (partialOutput == null) return "unknown";
        var matcher = java.util.regex.Pattern.compile(
            "(?:port\\(?s?\\)?:?\\s*)(\\d{2,5})")
            .matcher(partialOutput);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
