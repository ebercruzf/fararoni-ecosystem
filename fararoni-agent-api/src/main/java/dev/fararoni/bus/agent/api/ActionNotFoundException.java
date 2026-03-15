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
package dev.fararoni.bus.agent.api;

import java.util.List;

/**
 * Exception thrown when a requested action is not found in a skill.
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ToolRegistry#findAction(String, String)
 */
public class ActionNotFoundException extends RuntimeException {

    private final String skillName;
    private final String actionName;
    private final List<String> availableActions;

    /**
     * Creates a new exception with action name.
     *
     * @param actionName the name of the action that was not found
     */
    public ActionNotFoundException(String actionName) {
        super(String.format("Action not found: '%s'", actionName));
        this.skillName = null;
        this.actionName = actionName;
        this.availableActions = List.of();
    }

    /**
     * Creates a new exception with skill and action names.
     *
     * @param skillName  the skill name
     * @param actionName the action name
     */
    public ActionNotFoundException(String skillName, String actionName) {
        super(String.format("Action '%s' not found in skill '%s'", actionName, skillName));
        this.skillName = skillName;
        this.actionName = actionName;
        this.availableActions = List.of();
    }

    /**
     * Creates a new exception with available actions for context.
     *
     * @param skillName        the skill name
     * @param actionName       the action that was not found
     * @param availableActions list of available action names
     */
    public ActionNotFoundException(String skillName, String actionName, List<String> availableActions) {
        super(String.format("Action '%s' not found in skill '%s'. Available actions: %s",
            actionName, skillName, availableActions));
        this.skillName = skillName;
        this.actionName = actionName;
        this.availableActions = List.copyOf(availableActions);
    }

    /**
     * Returns the skill name.
     *
     * @return the skill name, or null if not specified
     */
    public String getSkillName() {
        return skillName;
    }

    /**
     * Returns the action name that was not found.
     *
     * @return the action name
     */
    public String getActionName() {
        return actionName;
    }

    /**
     * Returns the list of available actions.
     *
     * @return available action names
     */
    public List<String> getAvailableActions() {
        return availableActions;
    }
}
