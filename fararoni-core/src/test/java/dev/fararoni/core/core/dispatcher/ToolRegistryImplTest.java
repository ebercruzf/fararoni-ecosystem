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
package dev.fararoni.core.core.dispatcher;

import dev.fararoni.bus.agent.api.ToolMetadata;
import dev.fararoni.bus.agent.api.ToolSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ToolRegistryImpl Tests")
class ToolRegistryImplTest {
    private ToolRegistryImpl registry;
    private TestSkill testSkill;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistryImpl();
        testSkill = new TestSkill();
    }

    @Test
    @DisplayName("Should register skill successfully")
    void shouldRegisterSkillSuccessfully() {
        registry.register(testSkill);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.hasSkill("TEST")).isTrue();
        assertThat(testSkill.getInitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find registered skill")
    void shouldFindRegisteredSkill() {
        registry.register(testSkill);

        Optional<ToolSkill> found = registry.findSkill("TEST");

        assertThat(found).isPresent();
        assertThat(found.get()).isSameAs(testSkill);
    }

    @Test
    @DisplayName("Should find skill case-insensitively")
    void shouldFindSkillCaseInsensitively() {
        registry.register(testSkill);

        assertThat(registry.findSkill("test")).isPresent();
        assertThat(registry.findSkill("Test")).isPresent();
        assertThat(registry.findSkill("TEST")).isPresent();
    }

    @Test
    @DisplayName("Should return empty for non-existent skill")
    void shouldReturnEmptyForNonExistentSkill() {
        Optional<ToolSkill> found = registry.findSkill("NONEXISTENT");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should throw on duplicate skill registration")
    void shouldThrowOnDuplicateSkillRegistration() {
        registry.register(testSkill);

        assertThatThrownBy(() -> registry.register(new TestSkill()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Should throw on null skill")
    void shouldThrowOnNullSkill() {
        assertThatThrownBy(() -> registry.register(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should find action by name")
    void shouldFindActionByName() {
        registry.register(testSkill);

        Optional<Method> method = registry.findAction("TEST", "echo");

        assertThat(method).isPresent();
        assertThat(method.get().getName()).isEqualTo("echo");
    }

    @Test
    @DisplayName("Should return empty for non-existent action")
    void shouldReturnEmptyForNonExistentAction() {
        registry.register(testSkill);

        Optional<Method> method = registry.findAction("TEST", "nonexistent");

        assertThat(method).isEmpty();
    }

    @Test
    @DisplayName("Should get all skill names")
    void shouldGetAllSkillNames() {
        registry.register(testSkill);

        List<String> names = registry.getAllSkillNames();

        assertThat(names).containsExactly("TEST");
    }

    @Test
    @DisplayName("Should get action names for skill")
    void shouldGetActionNamesForSkill() {
        registry.register(testSkill);

        List<String> actions = registry.getActionNames("TEST");

        assertThat(actions).contains("echo", "add", "get_time", "greet", "fail", "slow", "secure");
    }

    @Test
    @DisplayName("Should unregister skill")
    void shouldUnregisterSkill() {
        registry.register(testSkill);

        boolean removed = registry.unregister("TEST");

        assertThat(removed).isTrue();
        assertThat(registry.hasSkill("TEST")).isFalse();
        assertThat(testSkill.getShutdownCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return false when unregistering non-existent skill")
    void shouldReturnFalseWhenUnregisteringNonExistentSkill() {
        boolean removed = registry.unregister("NONEXISTENT");

        assertThat(removed).isFalse();
    }

    @Test
    @DisplayName("Should generate tools JSON")
    void shouldGenerateToolsJson() {
        registry.register(testSkill);

        String json = registry.generateToolsJson();

        assertThat(json).contains("\"tools\"");
        assertThat(json).contains("\"name\": \"TEST\"");
        assertThat(json).contains("\"echo\"");
        assertThat(json).contains("\"add\"");
    }

    @Test
    @DisplayName("Should generate tools summary")
    void shouldGenerateToolsSummary() {
        registry.register(testSkill);

        String summary = registry.generateToolsSummary();

        assertThat(summary).contains("SKILL: TEST");
        assertThat(summary).contains("echo:");
        assertThat(summary).contains("add:");
    }

    @Test
    @DisplayName("Should get all action metadata")
    void shouldGetAllActionMetadata() {
        registry.register(testSkill);

        List<ToolMetadata> metadata = registry.getAllActionMetadata();

        assertThat(metadata).isNotEmpty();
        assertThat(metadata.stream().map(ToolMetadata::name))
            .contains("echo", "add", "get_time");
    }

    @Test
    @DisplayName("Should shutdown all skills on clear")
    void shouldShutdownAllSkillsOnClear() {
        registry.register(testSkill);

        registry.clear();

        assertThat(registry.size()).isZero();
        assertThat(testSkill.getShutdownCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should register multiple skills")
    void shouldRegisterMultipleSkills() {
        TestSkill skill1 = new TestSkill() {
            @Override public String getSkillName() { return "SKILL1"; }
        };
        TestSkill skill2 = new TestSkill() {
            @Override public String getSkillName() { return "SKILL2"; }
        };

        registry.registerAll(skill1, skill2);

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.hasSkill("SKILL1")).isTrue();
        assertThat(registry.hasSkill("SKILL2")).isTrue();
    }
}
