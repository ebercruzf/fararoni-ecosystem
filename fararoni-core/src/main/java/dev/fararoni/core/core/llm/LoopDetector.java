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
package dev.fararoni.core.core.llm;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class LoopDetector {
    private static final int REPEAT_THRESHOLD = 3;

    private static final int MIN_LINE_LENGTH = 10;

    private String lastLine = "";

    private int repeatCount = 0;

    public boolean shouldStop(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            return false;
        }

        String[] lines = fullText.split("\n");
        if (lines.length < REPEAT_THRESHOLD) {
            return false;
        }

        String currentLine = "";
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (trimmed.length() >= MIN_LINE_LENGTH) {
                currentLine = trimmed;
                break;
            }
        }

        if (currentLine.isEmpty()) {
            return false;
        }

        if (currentLine.equals(lastLine)) {
            repeatCount++;
            if (repeatCount >= REPEAT_THRESHOLD) {
                return true;
            }
        } else {
            repeatCount = 1;
            lastLine = currentLine;
        }

        return false;
    }

    public void reset() {
        lastLine = "";
        repeatCount = 0;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public boolean isNearThreshold() {
        return repeatCount >= REPEAT_THRESHOLD - 1;
    }
}
