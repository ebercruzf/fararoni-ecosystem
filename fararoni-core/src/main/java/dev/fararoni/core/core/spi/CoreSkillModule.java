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
package dev.fararoni.core.core.spi;

import dev.fararoni.bus.agent.api.ToolSkill;
import dev.fararoni.bus.agent.api.spi.SkillModule;
import dev.fararoni.core.core.skills.FileSystemSkillImpl;
import dev.fararoni.core.core.skills.GitSkillImpl;
import dev.fararoni.core.core.skills.TerminalSkillImpl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class CoreSkillModule implements SkillModule {
    private static final Logger LOG = Logger.getLogger(CoreSkillModule.class.getName());

    private static final String MODULE_NAME = "FARARONI Core";
    private static final String MODULE_VERSION = "2.0.0";
    private static final int MODULE_PRIORITY = 10;

    private Path workspacePath;

    public CoreSkillModule() {
    }

    @Override
    public void initialize() {
        this.workspacePath = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        LOG.fine(() -> "[CoreSkillModule] Initialized with workspace: " + workspacePath);
    }

    @Override
    public List<ToolSkill> provideSkills() {
        List<ToolSkill> skills = new ArrayList<>();

        try {
            skills.add(new FileSystemSkillImpl(workspacePath));
            LOG.fine("[CoreSkillModule] Registered FileSystemSkillImpl");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CoreSkillModule] Failed to create FileSystemSkillImpl", e);
        }

        try {
            skills.add(new TerminalSkillImpl(workspacePath));
            LOG.fine("[CoreSkillModule] Registered TerminalSkillImpl");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CoreSkillModule] Failed to create TerminalSkillImpl", e);
        }

        try {
            skills.add(new GitSkillImpl(workspacePath));
            LOG.fine("[CoreSkillModule] Registered GitSkillImpl");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CoreSkillModule] Failed to create GitSkillImpl", e);
        }

        LOG.info(() -> String.format("[CoreSkillModule] Providing %d skills", skills.size()));
        return skills;
    }

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public int priority() {
        return MODULE_PRIORITY;
    }

    @Override
    public String getDescription() {
        return "Core FNL skills: FileSystem, Terminal, Git";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void shutdown() {
        LOG.fine("[CoreSkillModule] Shutting down");
    }
}
