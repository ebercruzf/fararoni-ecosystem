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
package dev.fararoni.core.cli;

import dev.fararoni.bus.agent.api.command.ConsoleCommand;
import dev.fararoni.core.core.command.CommandRegistry;
import picocli.CommandLine.Command;

import java.util.Collections;
import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public interface CommandProvider {
    Class<?>[] getCommands();

    String getProviderName();

    String getVersion();

    default void initialize() {
    }

    default int getPriority() {
        return 0;
    }

    default boolean isEnabled() {
        return true;
    }

    default String getDescription() {
        return null;
    }

    default List<ConsoleCommand> provideConsoleCommands() {
        return Collections.emptyList();
    }
}
