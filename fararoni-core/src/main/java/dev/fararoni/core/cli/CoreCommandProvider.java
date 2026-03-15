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
import dev.fararoni.core.core.commands.AddCommand;
import dev.fararoni.core.core.commands.CommitCommand;
import dev.fararoni.core.core.commands.DiffCommand;
import dev.fararoni.core.core.commands.DropCommand;
import dev.fararoni.core.core.commands.DryRunCommand;
import dev.fararoni.core.core.commands.GitSubcommand;
import dev.fararoni.core.core.commands.IgnoreCommand;
import dev.fararoni.core.core.commands.LintCommand;
import dev.fararoni.core.core.commands.ModeCommand;
import dev.fararoni.core.core.commands.ModelCommand;
import dev.fararoni.core.core.commands.RoleCommand;
import dev.fararoni.core.core.commands.RunCommand;
import dev.fararoni.core.core.commands.TestCommand;
import dev.fararoni.core.core.commands.TreeCommand;
import dev.fararoni.core.core.commands.UndoCommand;
import dev.fararoni.core.core.commands.WebCommand;
import dev.fararoni.core.core.commands.DeepResearchCommand;
import dev.fararoni.core.core.commands.ReconfigCommand;
import dev.fararoni.core.core.commands.ReconfigTurtleCommand;
import dev.fararoni.core.core.commands.ThoughtsCommand;
import dev.fararoni.core.core.commands.WizardAgentCommand;
import dev.fararoni.core.core.commands.AskCommand;
import dev.fararoni.core.core.commands.QuartermasterPrimeCommand;
import dev.fararoni.core.core.commands.AgentCommand;
import dev.fararoni.core.core.commands.TaskCommand;
import dev.fararoni.core.core.commands.ChannelAccessCommand;
import dev.fararoni.core.core.plugin.PluginsCommand;

import java.util.List;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CoreCommandProvider implements CommandProvider {
    @Override
    public Class<?>[] getCommands() {
        return new Class<?>[] {
            ConfigCommand.class
        };
    }

    @Override
    public String getProviderName() {
        return "FARARONI Core";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getDescription() {
        return "Comandos principales de FARARONI Core";
    }

    @Override
    public List<ConsoleCommand> provideConsoleCommands() {
        return List.of(
            new WebCommand(),
            new TreeCommand(),
            new AddCommand(),
            new DropCommand(),
            new UndoCommand(),
            new CommitCommand(),
            new DiffCommand(),
            new RunCommand(),
            new TestCommand(),
            new DryRunCommand(),
            new ModeCommand(),
            new ModelCommand(),
            new IgnoreCommand(),
            new GitSubcommand(),
            new LintCommand(),
            new RoleCommand(),
            new PluginsCommand(),
            new DeepResearchCommand(),
            new ReconfigCommand(),
            new ReconfigTurtleCommand(),
            new ThoughtsCommand(),
            new WizardAgentCommand(),
            new AskCommand(),
            new QuartermasterPrimeCommand(),
            new AgentCommand(),
            new TaskCommand(),
            new ChannelAccessCommand()
        );
    }
}
