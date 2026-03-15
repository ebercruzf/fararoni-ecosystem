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
package dev.fararoni.core.ui.dashboard;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import dev.fararoni.core.ui.TerminalCapabilityDetector;
import dev.fararoni.core.ui.OutputCoordinator;
import dev.fararoni.bus.agent.api.ui.model.AgentState;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class AgentOrchestrator {
    private final TerminalCapabilityDetector terminalDetector;
    private final OutputCoordinator outputCoordinator;

    private AgentDashboard dashboard;
    private Terminal terminal;
    private ExecutorService executorService;

    public AgentOrchestrator(TerminalCapabilityDetector terminalDetector,
                           OutputCoordinator outputCoordinator) {
        this.terminalDetector = terminalDetector;
        this.outputCoordinator = outputCoordinator;
        this.executorService = Executors.newCachedThreadPool();
    }

    public CompletableFuture<OrchestrationResult> executeWithDashboard(String goal) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeComplexTaskInternal(goal);
            } catch (Exception e) {
                return new OrchestrationResult(false, "Execution failed: " + e.getMessage(), null);
            }
        }, executorService);
    }

    public CompletableFuture<OrchestrationResult> executeWithPlan(String goal, List<OrchestrationStep> steps) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeWithPlanInternal(goal, steps);
            } catch (Exception e) {
                return new OrchestrationResult(false, "Execution failed: " + e.getMessage(), null);
            }
        }, executorService);
    }

    private OrchestrationResult executeComplexTaskInternal(String goal) {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            this.terminal = terminal;

            AgentState state = createInitialState(goal);

            var displayMode = terminalDetector.detectBestMode();
            dashboard = AgentDashboard.createOptimal(terminal, state, displayMode);

            dashboard.start();

            state.setExecutionState(AgentState.AgentExecutionState.PLANNING);
            state.setCurrentAction("Creating execution plan...");

            Thread.sleep(1000);
            populatePlanningTasks(state, goal);

            state.setExecutionState(AgentState.AgentExecutionState.EXECUTING);
            return executeTasks(state);
        } catch (IOException e) {
            outputCoordinator.printError("Error configurando terminal: " + e.getMessage());
            return new OrchestrationResult(false, "Terminal error: " + e.getMessage(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OrchestrationResult(false, "Execution interrupted", null);
        } finally {
            cleanup();
        }
    }

    private OrchestrationResult executeWithPlanInternal(String goal, List<OrchestrationStep> steps) {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            this.terminal = terminal;

            AgentState state = createStateFromPlan(goal, steps);

            var displayMode = terminalDetector.detectBestMode();
            dashboard = AgentDashboard.createOptimal(terminal, state, displayMode);

            dashboard.start();
            return executeTasks(state);
        } catch (IOException e) {
            outputCoordinator.printError("Error configurando terminal: " + e.getMessage());
            return new OrchestrationResult(false, "Terminal error: " + e.getMessage(), null);
        } finally {
            cleanup();
        }
    }

    private AgentState createInitialState(String goal) {
        return new AgentState(goal);
    }

    private AgentState createStateFromPlan(String goal, List<OrchestrationStep> steps) {
        AgentState state = new AgentState(goal);

        for (OrchestrationStep step : steps) {
            String taskDescription = step.description();
            String details = step.type() + (step.critical() ? " - CRITICAL" : " - OPTIONAL");
            state.addTask(taskDescription, details);
        }

        return state;
    }

    private void populatePlanningTasks(AgentState state, String goal) {
        if (goal.toLowerCase().contains("implement") || goal.toLowerCase().contains("feature")) {
            populateFeatureImplementationTasks(state);
        } else if (goal.toLowerCase().contains("fix") || goal.toLowerCase().contains("bug")) {
            populateBugFixTasks(state);
        } else if (goal.toLowerCase().contains("refactor")) {
            populateRefactoringTasks(state);
        } else {
            populateGenericTasks(state);
        }
    }

    private void populateFeatureImplementationTasks(AgentState state) {
        state.addTask("Analyze requirements", "Understanding feature specifications");
        state.addTask("Design architecture", "Planning component structure");
        state.addTask("Implement core functionality", "Writing main feature code");
        state.addTask("Add error handling", "Implementing robust error management");
        state.addTask("Write unit tests", "Ensuring feature reliability");
        state.addTask("Integration testing", "Testing feature integration");
        state.addTask("Update documentation", "Documenting new functionality");
    }

    private void populateBugFixTasks(AgentState state) {
        state.addTask("Reproduce the issue", "Confirming bug behavior");
        state.addTask("Analyze root cause", "Identifying the source of the problem");
        state.addTask("Design fix strategy", "Planning the correction approach");
        state.addTask("Implement fix", "Applying the bug correction");
        state.addTask("Verify fix works", "Testing that the issue is resolved");
        state.addTask("Add regression tests", "Preventing future occurrences");
    }

    private void populateRefactoringTasks(AgentState state) {
        state.addTask("Analyze current code", "Understanding existing structure");
        state.addTask("Identify improvement opportunities", "Finding optimization areas");
        state.addTask("Plan refactoring approach", "Designing restructure strategy");
        state.addTask("Refactor incrementally", "Applying improvements step by step");
        state.addTask("Run full test suite", "Ensuring functionality preservation");
        state.addTask("Update documentation", "Reflecting structural changes");
    }

    private void populateGenericTasks(AgentState state) {
        state.addTask("Analyze task requirements", "Understanding the requested work");
        state.addTask("Create execution plan", "Breaking down into actionable steps");
        state.addTask("Execute primary actions", "Performing main task operations");
        state.addTask("Validate results", "Ensuring quality and correctness");
        state.addTask("Finalize and cleanup", "Completing and organizing outputs");
    }

    private OrchestrationResult executeTasks(AgentState state) {
        var tasks = state.getTasks();
        boolean allSuccessful = true;
        StringBuilder executionLog = new StringBuilder();

        for (int i = 0; i < tasks.size(); i++) {
            if (dashboard.isInterrupted()) {
                state.setExecutionState(AgentState.AgentExecutionState.INTERRUPTED);
                return new OrchestrationResult(false, "Execution interrupted by user", executionLog.toString());
            }

            var task = tasks.get(i);

            state.startTask(i);
            state.setCurrentAction(task.getDescription());

            try {
                boolean success = executeTask(task, state);

                if (success) {
                    state.completeTask(i);
                    executionLog.append("[OK] ").append(task.getDescription()).append("\n");
                } else {
                    state.failTask(i, "Task execution failed");
                    executionLog.append("[FAIL] ").append(task.getDescription()).append(" - Failed\n");
                    allSuccessful = false;
                }
            } catch (Exception e) {
                state.failTask(i, e.getMessage());
                executionLog.append("[FAIL] ").append(task.getDescription()).append(" - Error: ").append(e.getMessage()).append("\n");
                allSuccessful = false;
            }
        }

        if (allSuccessful) {
            state.setExecutionState(AgentState.AgentExecutionState.COMPLETED);
            state.setCurrentAction("All tasks completed successfully");
        } else {
            state.setExecutionState(AgentState.AgentExecutionState.FAILED);
            state.setCurrentAction("Some tasks failed");
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new OrchestrationResult(allSuccessful,
            allSuccessful ? "Execution completed successfully" : "Execution completed with errors",
            executionLog.toString());
    }

    private boolean executeTask(AgentState.Task task, AgentState state) throws InterruptedException {
        int steps = 3 + (int)(Math.random() * 5);

        for (int i = 0; i < steps; i++) {
            if (dashboard.isInterrupted()) {
                return false;
            }

            state.setCurrentAction(task.getDescription() + String.format(" (step %d/%d)", i + 1, steps));
            state.addTokensUsed(100 + (int)(Math.random() * 200));

            Thread.sleep(500 + (int)(Math.random() * 1000));
        }

        return Math.random() > 0.1;
    }

    private void cleanup() {
        if (dashboard != null) {
            dashboard.stop();
        }
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
            }
        }
    }

    public void shutdown() {
        cleanup();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    public static AgentOrchestrator create(TerminalCapabilityDetector terminalDetector,
                                         OutputCoordinator outputCoordinator) {
        return new AgentOrchestrator(terminalDetector, outputCoordinator);
    }

    public static AgentOrchestrator createDebug(OutputCoordinator outputCoordinator) {
        TerminalCapabilityDetector mockDetector = new TerminalCapabilityDetector();
        return new AgentOrchestrator(mockDetector, outputCoordinator);
    }

    public record OrchestrationResult(
        boolean success,
        String message,
        String executionLog
    ) {
        public String getFormattedResult() {
            return String.format("""
                Agent Orchestration Result:
                   - Status: %s
                   - Message: %s
                   - Log:
                %s
                """,
                success ? "SUCCESS" : "FAILED",
                message,
                executionLog != null ? executionLog : "No execution log available"
            );
        }
    }

    public record OrchestrationStep(
        String description,
        String type,
        boolean critical
    ) {}

    public OrchestratorStats getStats() {
        return new OrchestratorStats(
            dashboard != null && dashboard.getStats().isRunning(),
            dashboard != null ? dashboard.getStats() : null,
            terminalDetector.getEnvironmentInfo()
        );
    }

    public record OrchestratorStats(
        boolean dashboardRunning,
        AgentDashboard.DashboardStats dashboardStats,
        TerminalCapabilityDetector.EnvironmentInfo terminalInfo
    ) {
        public String getFormattedStats() {
            return String.format("""
                Agent Orchestrator Statistics:
                   - Dashboard Running: %s
                   - Terminal: %s
                   - Dashboard Stats: %s
                """,
                dashboardRunning ? "Yes" : "No",
                terminalInfo.getFormattedInfo(),
                dashboardStats != null ? dashboardStats.getFormattedStats() : "N/A"
            );
        }
    }
}
