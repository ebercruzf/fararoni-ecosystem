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

import dev.fararoni.core.core.integration.FnlIntegrationService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public final class SkillProvider {
    private static final Logger LOG = Logger.getLogger(SkillProvider.class.getName());

    private static volatile FnlIntegrationService instance;
    private static volatile Path workspacePath;

    private SkillProvider() {
    }

    public static FnlIntegrationService getInstance() {
        return getInstance(Paths.get(System.getProperty("user.dir")));
    }

    public static synchronized FnlIntegrationService getInstance(Path workspace) {
        if (instance == null || !workspace.equals(workspacePath)) {
            instance = createInstance(workspace);
            workspacePath = workspace;
        }
        return instance;
    }

    public static FnlIntegrationService createInstance(Path workspace) {
        FnlIntegrationService service = new FnlIntegrationService();
        Path normalizedPath = workspace.toAbsolutePath().normalize();

        try {
            service.registerSkill(new GitSkill(normalizedPath));
            LOG.fine("[SkillProvider] Registered GitSkill");
        } catch (Exception e) {
            LOG.warning("[SkillProvider] Failed to register GitSkill: " + e.getMessage());
        }

        try {
            service.registerSkill(new SystemSkill(normalizedPath));
            LOG.fine("[SkillProvider] Registered SystemSkill");
        } catch (Exception e) {
            LOG.warning("[SkillProvider] Failed to register SystemSkill: " + e.getMessage());
        }

        try {
            service.registerSkill(new DateTimeSkill());
            LOG.fine("[SkillProvider] Registered DateTimeSkill");
        } catch (Exception e) {
            LOG.warning("[SkillProvider] Failed to register DateTimeSkill: " + e.getMessage());
        }

        LOG.info("[SkillProvider] Initialized with " + service.getSkillCount() + " skills: " +
                 String.join(", ", service.getSkillNames()));

        return service;
    }

    public static synchronized void reset() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
            workspacePath = null;
        }
    }

    public static Path getWorkspacePath() {
        return workspacePath;
    }
}
