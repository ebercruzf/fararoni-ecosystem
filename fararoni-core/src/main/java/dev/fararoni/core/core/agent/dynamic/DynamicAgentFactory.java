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
package dev.fararoni.core.core.agent.dynamic;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;
import dev.fararoni.core.FararoniCore;
import dev.fararoni.core.core.agent.model.AgentInstanceConfig;
import dev.fararoni.core.core.agent.model.AgentTemplate;

import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class DynamicAgentFactory {
    private static final Logger LOG = Logger.getLogger(DynamicAgentFactory.class.getName());

    public DynamicSwarmAgent create(
            AgentTemplate template,
            AgentInstanceConfig config,
            FararoniCore core) {
        SovereignEventBus bus = core.getSovereignBus();
        if (bus == null) {
            throw new IllegalStateException(
                "SovereignBus no inicializado. Verifica que el Core haya arrancado."
            );
        }

        var llmProvider = (DynamicSwarmAgent.LlmInferenceProvider)
            (sys, user) -> core.chat(sys + "\n\n" + user);

        LOG.info(() -> String.format(
            "[FACTORY] Creando agente: %s [%s]",
            config.id(), template.roleName()
        ));

        return new DynamicSwarmAgent(
            config.id(),
            template,
            config.wiring(),
            bus,
            llmProvider
        );
    }
}
