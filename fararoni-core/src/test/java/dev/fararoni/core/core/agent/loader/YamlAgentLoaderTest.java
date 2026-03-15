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
package dev.fararoni.core.core.agent.loader;

import dev.fararoni.core.core.agent.model.AgentTemplate;
import dev.fararoni.core.core.agent.model.MissionManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
class YamlAgentLoaderTest {
    private YamlAgentLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new YamlAgentLoader();
    }

    @AfterEach
    void tearDown() {
        loader.clear();
    }

    @Test
    void loadTemplate_validYaml_success() throws IOException {
        String yaml = """
            templateId: test-template-v1
            roleName: TESTER
            systemPrompt: |
              Eres un agente de pruebas.
              Ejecutas tests automatizados.
            outputJsonSchema: |
              {"type": "object", "properties": {"status": {"type": "string"}}}
            capabilities:
              - RUN_TESTS
              - GENERATE_REPORT
            metadata:
              version: "1.0"
              author: test-team
            """;
        Path yamlFile = tempDir.resolve("test-template.yaml");
        Files.writeString(yamlFile, yaml);

        Optional<AgentTemplate> result = loader.loadTemplate(yamlFile);

        assertTrue(result.isPresent(), "Template deberia cargarse");
        AgentTemplate template = result.get();
        assertEquals("test-template-v1", template.templateId());
        assertEquals("TESTER", template.roleName());
        assertTrue(template.systemPrompt().contains("agente de pruebas"));
        assertTrue(template.hasOutputSchema());
        assertEquals(2, template.capabilities().size());
        assertTrue(template.hasCapability("RUN_TESTS"));
        assertEquals("1.0", template.metadata().get("version"));
    }

    @Test
    void loadTemplate_minimalYaml_success() throws IOException {
        String yaml = """
            templateId: minimal-template
            roleName: MINIMAL
            systemPrompt: Solo lo esencial
            """;
        Path yamlFile = tempDir.resolve("minimal.yaml");
        Files.writeString(yamlFile, yaml);

        Optional<AgentTemplate> result = loader.loadTemplate(yamlFile);

        assertTrue(result.isPresent());
        AgentTemplate template = result.get();
        assertEquals("minimal-template", template.templateId());
        assertFalse(template.hasOutputSchema());
        assertTrue(template.capabilities().isEmpty());
    }

    @Test
    void loadTemplate_missingRequiredField_fails() throws IOException {
        String yaml = """
            roleName: INCOMPLETE
            systemPrompt: Falta el ID
            """;
        Path yamlFile = tempDir.resolve("incomplete.yaml");
        Files.writeString(yamlFile, yaml);

        Optional<AgentTemplate> result = loader.loadTemplate(yamlFile);

        assertTrue(result.isEmpty(), "No deberia cargar sin templateId");
        assertFalse(loader.getLoadErrors().isEmpty());
        assertTrue(loader.getLoadErrors().get(0).message().contains("templateId"));
    }

    @Test
    void loadTemplate_emptyFile_fails() throws IOException {
        Path yamlFile = tempDir.resolve("empty.yaml");
        Files.writeString(yamlFile, "");

        Optional<AgentTemplate> result = loader.loadTemplate(yamlFile);

        assertTrue(result.isEmpty());
        assertEquals(1, loader.getLoadErrors().size());
    }

    @Test
    void loadTemplate_invalidYamlSyntax_fails() throws IOException {
        String yaml = "templateId: test\n  invalid indentation";
        Path yamlFile = tempDir.resolve("invalid.yaml");
        Files.writeString(yamlFile, yaml);

        Optional<AgentTemplate> result = loader.loadTemplate(yamlFile);

        assertTrue(result.isEmpty());
    }

    @Test
    void loadMission_validYaml_success() throws IOException {
        String yaml = """
            missionId: test-pipeline
            description: Pipeline de prueba
            version: "2.0"
            agents:
              - id: agent-1
                templateRef: template-v1
                wiring:
                  inputTopics:
                    - input.topic.1
                    - input.topic.2
                  outputTopic: output.agent1
                routing:
                  priority: 100
                  maxConcurrent: 5
                  timeoutMs: 60000

              - id: agent-2
                templateRef: template-v2
                dependsOn:
                  - agent-1
                variables:
                  region: LATAM
            metadata:
              owner: test-team
              sla: 5000ms
            """;
        Path yamlFile = tempDir.resolve("test-mission.yaml");
        Files.writeString(yamlFile, yaml);

        Optional<MissionManifest> result = loader.loadMission(yamlFile);

        assertTrue(result.isPresent(), "Mission deberia cargarse");
        MissionManifest mission = result.get();
        assertEquals("test-pipeline", mission.missionId());
        assertEquals("Pipeline de prueba", mission.description());
        assertEquals("2.0", mission.version());
        assertEquals(2, mission.agents().size());

        MissionManifest.AgentEntry agent1 = mission.getAgent("agent-1");
        assertNotNull(agent1);
        assertEquals("template-v1", agent1.templateRef());
        assertEquals(2, agent1.wiring().inputTopics().size());
        assertEquals("output.agent1", agent1.wiring().outputTopic());
        assertEquals(100, agent1.routing().priority());

        MissionManifest.AgentEntry agent2 = mission.getAgent("agent-2");
        assertNotNull(agent2);
        assertEquals(List.of("agent-1"), agent2.dependsOn());
        assertEquals("LATAM", agent2.variables().get("region"));
    }

    @Test
    void loadMission_minimalYaml_success() throws IOException {
        String yaml = """
            missionId: minimal-mission
            agents:
              - id: solo-agent
                templateRef: base-template
            """;
        Path yamlFile = tempDir.resolve("minimal-mission.yaml");
        Files.writeString(yamlFile, yaml);

        Optional<MissionManifest> result = loader.loadMission(yamlFile);

        assertTrue(result.isPresent());
        assertEquals("1.0", result.get().version());
        assertEquals(1, result.get().agents().size());
    }

    @Test
    void getMission_rootAgents_returnsAgentsWithoutDependencies() throws IOException {
        String yaml = """
            missionId: dag-mission
            agents:
              - id: root-1
                templateRef: t1
              - id: root-2
                templateRef: t2
              - id: dependent
                templateRef: t3
                dependsOn: [root-1, root-2]
            """;
        Path yamlFile = tempDir.resolve("dag.yaml");
        Files.writeString(yamlFile, yaml);
        loader.loadMission(yamlFile);

        MissionManifest mission = loader.getMission("dag-mission").orElseThrow();
        List<MissionManifest.AgentEntry> roots = mission.getRootAgents();

        assertEquals(2, roots.size());
        assertTrue(roots.stream().anyMatch(a -> a.id().equals("root-1")));
        assertTrue(roots.stream().anyMatch(a -> a.id().equals("root-2")));
    }

    @Test
    void isValidFlow_allDependenciesExist_returnsTrue() throws IOException {
        String yaml = """
            missionId: valid-flow
            agents:
              - id: step-1
                templateRef: t1
              - id: step-2
                templateRef: t2
                dependsOn: [step-1]
            """;
        Path yamlFile = tempDir.resolve("valid-flow.yaml");
        Files.writeString(yamlFile, yaml);

        MissionManifest mission = loader.loadMission(yamlFile).orElseThrow();

        assertTrue(mission.isValidFlow());
    }

    @Test
    void isValidFlow_missingDependency_returnsFalse() throws IOException {
        String yaml = """
            missionId: invalid-flow
            agents:
              - id: step-2
                templateRef: t2
                dependsOn: [step-1-missing]
            """;
        Path yamlFile = tempDir.resolve("invalid-flow.yaml");
        Files.writeString(yamlFile, yaml);

        MissionManifest mission = loader.loadMission(yamlFile).orElseThrow();

        assertFalse(mission.isValidFlow());
    }

    @Test
    void scanTemplates_multipleFiles_loadsAll() throws IOException {
        Path templatesDir = tempDir.resolve("templates");
        Files.createDirectories(templatesDir);

        for (int i = 1; i <= 3; i++) {
            String yaml = String.format("""
                templateId: template-%d
                roleName: ROLE_%d
                systemPrompt: Prompt %d
                """, i, i, i);
            Files.writeString(templatesDir.resolve("template-" + i + ".yaml"), yaml);
        }

        YamlAgentLoader customLoader = new YamlAgentLoaderForTesting(tempDir);
        int loaded = customLoader.scanTemplates();

        assertEquals(3, loaded);
        assertEquals(3, customLoader.getAllTemplates().size());
    }

    @Test
    void scanAll_mixedContent_loadsTemplatesAndMissions() throws IOException {
        Path templatesDir = tempDir.resolve("templates");
        Path missionsDir = tempDir.resolve("missions");
        Files.createDirectories(templatesDir);
        Files.createDirectories(missionsDir);

        Files.writeString(templatesDir.resolve("t1.yaml"), """
            templateId: t1
            roleName: R1
            systemPrompt: P1
            """);

        Files.writeString(missionsDir.resolve("m1.yaml"), """
            missionId: m1
            agents:
              - id: a1
                templateRef: t1
            """);

        YamlAgentLoader customLoader = new YamlAgentLoaderForTesting(tempDir);
        int total = customLoader.scanAll();

        assertEquals(2, total);
        assertTrue(customLoader.getTemplate("t1").isPresent());
        assertTrue(customLoader.getMission("m1").isPresent());
    }

    @Test
    void getTemplate_afterLoad_returnsCached() throws IOException {
        String yaml = """
            templateId: cached-template
            roleName: CACHED
            systemPrompt: Test cache
            """;
        Path yamlFile = tempDir.resolve("cached.yaml");
        Files.writeString(yamlFile, yaml);
        loader.loadTemplate(yamlFile);

        Optional<AgentTemplate> first = loader.getTemplate("cached-template");
        Optional<AgentTemplate> second = loader.getTemplate("cached-template");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertSame(first.get(), second.get(), "Deberia retornar misma instancia");
    }

    @Test
    void clear_removesAllCached() throws IOException {
        String yaml = """
            templateId: to-clear
            roleName: CLEAR
            systemPrompt: Will be cleared
            """;
        Path yamlFile = tempDir.resolve("clear.yaml");
        Files.writeString(yamlFile, yaml);
        loader.loadTemplate(yamlFile);
        assertTrue(loader.getTemplate("to-clear").isPresent());

        loader.clear();

        assertTrue(loader.getTemplate("to-clear").isEmpty());
        assertTrue(loader.getAllTemplates().isEmpty());
        assertTrue(loader.getLoadErrors().isEmpty());
    }

    static class YamlAgentLoaderForTesting extends YamlAgentLoader {
        private final Path testConfigBase;

        YamlAgentLoaderForTesting(Path testConfigBase) {
            this.testConfigBase = testConfigBase;
        }

        @Override
        public int scanTemplates() {
            return scanDirectoryInternal(testConfigBase.resolve("templates"));
        }

        @Override
        public int scanMissions() {
            return scanDirectoryInternal(testConfigBase.resolve("missions"));
        }

        @Override
        public int scanInstances() {
            return scanDirectoryInternal(testConfigBase.resolve("instances"));
        }

        private int scanDirectoryInternal(Path directory) {
            if (!Files.isDirectory(directory)) {
                return 0;
            }
            int loaded = 0;
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                    if (file.getParent().getFileName().toString().equals("templates")) {
                        if (loadTemplate(file).isPresent()) loaded++;
                    } else {
                        if (loadMission(file).isPresent()) loaded++;
                    }
                }
            } catch (IOException e) {
            }
            return loaded;
        }
    }
}
