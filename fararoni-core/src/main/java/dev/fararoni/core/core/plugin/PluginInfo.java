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
package dev.fararoni.core.core.plugin;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record PluginInfo(
    String id,
    String name,
    String description,
    String version,
    String downloadUrl,
    String sha256,
    long sizeBytes,
    List<String> commands,
    List<String> dependencies
) {

    public PluginInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin ID cannot be null or blank");
        }
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("Download URL cannot be null or blank");
        }
        if (commands == null) {
            commands = List.of();
        }
        if (dependencies == null) {
            dependencies = List.of();
        }
    }

    public String getFormattedSize() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        } else if (sizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public boolean providesCommand(String trigger) {
        return commands.stream()
            .anyMatch(cmd -> cmd.equalsIgnoreCase(trigger));
    }

    public String getJarFileName() {
        return "fararoni-extension-" + id + "-" + version + ".jar";
    }
}
