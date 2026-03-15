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
package dev.fararoni.core.core.safety.mission;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record MissionReport(
    boolean isSuccess,
    String message,
    String buildOutput,
    String techStack,
    long durationMs
) {
    public static MissionReport success(String techStack, long durationMs) {
        return new MissionReport(
            true,
            "Compilación exitosa",
            "",
            techStack,
            durationMs
        );
    }

    public static MissionReport failure(String errorLog, String techStack, long durationMs) {
        return new MissionReport(
            false,
            "Error de compilación",
            errorLog,
            techStack,
            durationMs
        );
    }

    public static MissionReport timeout(String techStack, int timeoutMinutes) {
        return new MissionReport(
            false,
            "Timeout: La compilación excedió " + timeoutMinutes + " minutos",
            "BUILD TIMEOUT",
            techStack,
            timeoutMinutes * 60 * 1000L
        );
    }

    public static MissionReport skipped() {
        return new MissionReport(
            true,
            "Verificación omitida (tipo de proyecto no reconocido)",
            "",
            "unknown",
            0
        );
    }

    public static MissionReport infrastructureError(String errorMessage) {
        return new MissionReport(
            false,
            "Error de infraestructura: " + errorMessage,
            errorMessage,
            "unknown",
            0
        );
    }

    public boolean isSkipped() {
        return "unknown".equals(techStack) && isSuccess;
    }

    public String toLogString() {
        if (isSuccess) {
            return String.format("[BUILD] [OK] %s - %s (%dms)", techStack, message, durationMs);
        } else {
            String truncatedOutput = buildOutput.length() > 200
                ? buildOutput.substring(0, 200) + "..."
                : buildOutput;
            return String.format("[BUILD] [FAIL] %s - %s\n%s", techStack, message, truncatedOutput);
        }
    }

    public String getFirstLines(int maxLines) {
        if (buildOutput == null || buildOutput.isBlank()) return "";

        String[] lines = buildOutput.split("\n");
        if (lines.length <= maxLines) return buildOutput;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("... [").append(lines.length - maxLines).append(" líneas más]");
        return sb.toString();
    }
}
