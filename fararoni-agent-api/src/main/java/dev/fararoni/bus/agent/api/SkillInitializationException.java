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

/**
 * Exception thrown when a skill fails to initialize.
 *
 * <p>This exception is thrown during the registration process when
 * {@link ToolSkill#initialize()} encounters an error.</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 * @see ToolSkill#initialize()
 */
public class SkillInitializationException extends RuntimeException {

    private final String skillName;

    /**
     * Creates a new exception with a message.
     *
     * @param skillName the name of the skill that failed
     * @param message   the error message
     */
    public SkillInitializationException(String skillName, String message) {
        super(String.format("Failed to initialize skill '%s': %s", skillName, message));
        this.skillName = skillName;
    }

    /**
     * Creates a new exception with a cause.
     *
     * @param skillName the name of the skill that failed
     * @param message   the error message
     * @param cause     the underlying cause
     */
    public SkillInitializationException(String skillName, String message, Throwable cause) {
        super(String.format("Failed to initialize skill '%s': %s", skillName, message), cause);
        this.skillName = skillName;
    }

    /**
     * Returns the name of the skill that failed to initialize.
     *
     * @return the skill name
     */
    public String getSkillName() {
        return skillName;
    }
}
