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
package dev.fararoni.core.core.react;

public class DefaultRepairDirective implements IRepairDirective {
    @Override
    public boolean applies(String buildOutput) {
        return true;
    }

    @Override
    public String initialProtocol() {
        return null;
    }

    @Override
    public String afterShellFailure() {
        return null;
    }

    @Override
    public String afterRead() {
        return null;
    }

    @Override
    public String afterPatch() {
        return null;
    }

    @Override
    public String certitudeAfterRead() {
        return "PROTOCOL-MANDATE: File analysis is complete. "
            + "You MUST now apply the fix using the 'fs_patch' tool. "
            + "Group ALL corrections (imports, methods, types) into a SINGLE fs_patch call. "
            + "Do NOT re-read the file. Do NOT compile yet. Apply the fix NOW. "
            + "ALTERNATIVE: If you prefer fs_write (full file rewrite), use EXACTLY this format: "
            + "{\"path\": \"relative/path/to/File.java\", \"content\": \"complete file content\"} "
            + "Only two fields: 'path' and 'content'. No other field names.";
    }

    @Override
    public String certitudeAfterPatch() {
        return null;
    }

    @Override
    public String certitudeAfterPatchFailure() {
        return "PROTOCOL-MANDATE: Your patch FAILED because the search block does not match "
            + "the actual file content (indentation differences or corrupted code). "
            + "Do NOT attempt fs_patch again — it will fail for the same reason. "
            + "INSTEAD: 1) Use fs_read to read the ENTIRE file. "
            + "2) Use fs_write to REWRITE the entire file with ALL corrections applied. "
            + "fs_write replaces the whole file — no search block needed. "
            + "EXACT FORMAT: {\"path\": \"src/main/java/...\", \"content\": \"full file content\"} "
            + "Only two fields: 'path' (relative file path) and 'content' (complete file). "
            + "Do NOT use 'file', 'filename', 'text', or any other field name. "
            + "CRITICAL: You MUST include the COMPLETE file content in fs_write — "
            + "every import, every method, every line. Do NOT abbreviate, skip, or summarize any code. "
            + "If the file has duplicated methods, keep ONE correct copy of each but include ALL unique methods. "
            + "The safety system will REJECT your write if the new content is less than 50% the size of the original.";
    }

    @Override
    public String certitudeAfterKillSwitchBlock() {
        return "PROTOCOL-MANDATE: The security system blocked your write because the file size reduction "
            + "exceeded the safety threshold. However, the Kernel has VALIDATED that this reduction is "
            + "INTENTIONAL (duplicated code cleanup). A privilege escalation has been authorized. "
            + "RE-EXECUTE the SAME fs_write command with the SAME corrected content. "
            + "The override is active — your write WILL succeed this time. "
            + "Do NOT change the content. Do NOT add filler comments. Just retry fs_write as-is.";
    }

    @Override
    public String certitudeAfterRuntimeFailure() {
        return "PROTOCOL-MANDATE: The application COMPILED SUCCESSFULLY but CRASHED at RUNTIME. "
            + "This is NOT a compilation error — it is a runtime exception (Java StackTrace). "
            + "STEPS: 1) Read the StackTrace in the output — find ALL 'Caused by:' lines. "
            + "2) The LAST 'Caused by:' is the ROOT CAUSE of the failure. "
            + "3) Look for the file and line number in the stack frames: 'at com.example.MyClass(MyClass.java:42)'. "
            + "4) Use fs_read on that file to understand the bug. "
            + "5) Fix the bug using fs_write with EXACT format: "
            + "{\"path\": \"relative/path/File.java\", \"content\": \"complete file content\"} "
            + "COMMON RUNTIME ERRORS: missing @Bean definition, wrong constructor injection, "
            + "null dependency, missing class in pom.xml, wrong application.properties config. "
            + "Do NOT re-run the build without fixing the root cause first.";
    }

    @Override
    public String certitudeAfterWriteArgsError() {
        return "PROTOCOL-MANDATE: Your fs_write FAILED because the arguments are MALFORMED. "
            + "fs_write requires EXACTLY two fields: 'path' and 'content'. "
            + "You MUST use this EXACT JSON structure: "
            + "{\"path\": \"src/main/java/com/example/MyFile.java\", \"content\": \"full file content here\"} "
            + "RULES: 1) 'path' = relative path to the file. "
            + "2) 'content' = the COMPLETE file content (every import, class, method, line). "
            + "Do NOT use 'file', 'filename', 'text', or any other field name. "
            + "Do NOT nest the content inside another object. "
            + "Do NOT abbreviate or skip any code in 'content'.";
    }
}
