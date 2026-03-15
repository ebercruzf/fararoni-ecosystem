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
package dev.fararoni.bus.agent.api.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent State v1.0.0 - Plan de Ejecucion Agentica en Tiempo Real.
 *
 * <h2>Proposito</h2>
 * <p>Representa el estado completo de un plan de ejecucion de agente IA.
 * Este modelo es compartido entre Enterprise (logica) y Core (renderizado).</p>
 *
 * <h2>Componentes</h2>
 * <ul>
 *   <li>Objetivo principal del agente</li>
 *   <li>Lista de tareas con estados (pending, in_progress, completed, failed)</li>
 *   <li>Estadisticas en tiempo real (tokens, tiempo, progreso)</li>
 *   <li>Estado actual de ejecucion</li>
 * </ul>
 *
 * <h2>Patron Render Server</h2>
 * <p>Este modelo es construido por Enterprise (logica de negocio) y consumido
 * por Core (rendering via JLine). Enterprise no sabe como se renderiza.</p>
 *
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class AgentState {

    /**
     * Estados posibles de cada tarea del plan.
     */

    public enum TaskStatus {
        PENDING("☐", "Pending execution"),
        IN_PROGRESS("⧗", "Currently executing"),
        COMPLETED("☒", "Successfully completed"),
        FAILED("✗", "Execution failed");

        private final String symbol;
        private final String description;

        TaskStatus(String symbol, String description) {
            this.symbol = symbol;
            this.description = description;
        }

        public String getSymbol() { return symbol; }
        public String getDescription() { return description; }
    }

    /**
     * Representa una tarea individual dentro del plan de ejecucion.
     */
    public static class Task {
        private String description;
        private TaskStatus status;
        private String details;
        private long startTime;
        private long endTime;
        private String errorMessage;

        public Task(String description) {
            this.description = description;
            this.status = TaskStatus.PENDING;
            this.details = "";
            this.startTime = 0;
            this.endTime = 0;
        }

        public Task(String description, String details) {
            this(description);
            this.details = details;
        }

        // Getters
        public String getDescription() { return description; }
        public TaskStatus getStatus() { return status; }
        public String getDetails() { return details; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public String getErrorMessage() { return errorMessage; }

        // Estado management
        public void start() {
            this.status = TaskStatus.IN_PROGRESS;
            this.startTime = System.currentTimeMillis();
        }

        public void complete() {
            this.status = TaskStatus.COMPLETED;
            this.endTime = System.currentTimeMillis();
        }

        public void fail(String errorMessage) {
            this.status = TaskStatus.FAILED;
            this.endTime = System.currentTimeMillis();
            this.errorMessage = errorMessage;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        public long getDurationMs() {
            if (startTime == 0) return 0;
            long end = endTime == 0 ? System.currentTimeMillis() : endTime;
            return end - startTime;
        }

        /**
         * Representacion visual de la tarea para el dashboard.
         */
        public String getFormattedLine() {
            String duration = getDurationMs() > 0 ? String.format(" (%.1fs)", getDurationMs() / 1000.0) : "";
            String errorInfo = status == TaskStatus.FAILED && errorMessage != null ? " - " + errorMessage : "";
            return status.getSymbol() + " " + description + duration + errorInfo;
        }
    }

    // ============================================================================
    // ESTADO PRINCIPAL DEL AGENTE
    // ============================================================================

    private final String mainGoal;
    private final AtomicReference<String> currentAction;
    private final List<Task> tasks;

    // Estadisticas en tiempo real
    private final AtomicInteger tokensUsed;
    private final AtomicLong startTime;
    private final AtomicInteger currentTaskIndex;

    // Estado de ejecucion
    private final AtomicReference<AgentExecutionState> executionState;

    /**
     * Estados de ejecucion del agente.
     */
    public enum AgentExecutionState {
        INITIALIZING("Initializing agent..."),
        PLANNING("Creating execution plan..."),
        EXECUTING("Executing plan..."),
        PAUSED("Execution paused"),
        COMPLETED("All tasks completed"),
        FAILED("Execution failed"),
        INTERRUPTED("Interrupted by user");

        private final String description;

        AgentExecutionState(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    // ============================================================================
    // CONSTRUCTOR Y METODOS BASICOS
    // ============================================================================

    public AgentState(String mainGoal) {
        this.mainGoal = mainGoal;
        this.currentAction = new AtomicReference<>("Initializing...");
        this.tasks = new ArrayList<>();
        this.tokensUsed = new AtomicInteger(0);
        this.startTime = new AtomicLong(System.currentTimeMillis());
        this.currentTaskIndex = new AtomicInteger(-1);
        this.executionState = new AtomicReference<>(AgentExecutionState.INITIALIZING);
    }

    // ============================================================================
    // GESTION DE TAREAS
    // ============================================================================

    /**
     * Agrega una nueva tarea al plan.
     */
    public synchronized Task addTask(String description) {
        Task task = new Task(description);
        tasks.add(task);
        return task;
    }

    /**
     * Agrega una nueva tarea con detalles.
     */
    public synchronized Task addTask(String description, String details) {
        Task task = new Task(description, details);
        tasks.add(task);
        return task;
    }

    /**
     * Marca una tarea como completada.
     */
    public synchronized void completeTask(int index) {
        if (index >= 0 && index < tasks.size()) {
            tasks.get(index).complete();
        }
    }

    /**
     * Inicia ejecucion de una tarea.
     */
    public synchronized void startTask(int index) {
        if (index >= 0 && index < tasks.size()) {
            tasks.get(index).start();
            currentTaskIndex.set(index);
            executionState.set(AgentExecutionState.EXECUTING);
        }
    }

    /**
     * Marca una tarea como fallida.
     */
    public synchronized void failTask(int index, String errorMessage) {
        if (index >= 0 && index < tasks.size()) {
            tasks.get(index).fail(errorMessage);
            executionState.set(AgentExecutionState.FAILED);
        }
    }

    /**
     * Obtiene la tarea actualmente en ejecucion.
     */
    public Task getCurrentTask() {
        int index = currentTaskIndex.get();
        if (index >= 0 && index < tasks.size()) {
            return tasks.get(index);
        }
        return null;
    }

    // ============================================================================
    // GESTION DE ESTADO
    // ============================================================================

    /**
     * Actualiza la accion actual del agente.
     */
    public void setCurrentAction(String action) {
        currentAction.set(action);
    }

    /**
     * Agrega tokens utilizados.
     */
    public void addTokensUsed(int tokens) {
        tokensUsed.addAndGet(tokens);
    }

    /**
     * Establece el estado de ejecucion.
     */
    public void setExecutionState(AgentExecutionState state) {
        executionState.set(state);
    }

    /**
     * Interrumpe la ejecucion del agente.
     */
    public void interrupt() {
        executionState.set(AgentExecutionState.INTERRUPTED);
        setCurrentAction("Interrupted by user");
    }

    /**
     * Pausa la ejecucion del agente.
     */
    public void pause() {
        executionState.set(AgentExecutionState.PAUSED);
        setCurrentAction("Execution paused");
    }

    /**
     * Reanuda la ejecucion del agente.
     */
    public void resume() {
        executionState.set(AgentExecutionState.EXECUTING);
        Task currentTask = getCurrentTask();
        if (currentTask != null) {
            setCurrentAction("Resuming: " + currentTask.getDescription());
        }
    }

    // ============================================================================
    // GETTERS Y ESTADISTICAS
    // ============================================================================

    public String getMainGoal() { return mainGoal; }
    public String getCurrentAction() { return currentAction.get(); }
    public List<Task> getTasks() { return new ArrayList<>(tasks); }
    public int getTokensUsed() { return tokensUsed.get(); }
    public long getStartTime() { return startTime.get(); }
    public AgentExecutionState getExecutionState() { return executionState.get(); }

    /**
     * Tiempo transcurrido desde el inicio en segundos.
     */
    public long getElapsedSeconds() {
        return (System.currentTimeMillis() - startTime.get()) / 1000;
    }

    /**
     * Progreso del plan como porcentaje.
     */
    public double getProgressPercentage() {
        if (tasks.isEmpty()) return 0.0;

        long completed = tasks.stream()
            .mapToLong(task -> task.getStatus() == TaskStatus.COMPLETED ? 1 : 0)
            .sum();

        return (double) completed / tasks.size() * 100.0;
    }

    /**
     * Estadisticas completas del estado.
     */
    public AgentStats getStats() {
        return new AgentStats(
            mainGoal,
            tasks.size(),
            (int) tasks.stream().mapToLong(t -> t.getStatus() == TaskStatus.COMPLETED ? 1 : 0).sum(),
            (int) tasks.stream().mapToLong(t -> t.getStatus() == TaskStatus.FAILED ? 1 : 0).sum(),
            getProgressPercentage(),
            getElapsedSeconds(),
            getTokensUsed(),
            executionState.get()
        );
    }

    /**
     * Record con estadisticas del agente para debugging y monitoring.
     */
    public record AgentStats(
        String mainGoal,
        int totalTasks,
        int completedTasks,
        int failedTasks,
        double progressPercentage,
        long elapsedSeconds,
        int tokensUsed,
        AgentExecutionState executionState
    ) {
        public String getFormattedStats() {
            return String.format("""
                Agent Statistics:
                   - Goal: %s
                   - Tasks: %d/%d completed (%.1f%%)
                   - Failed: %d
                   - Time: %ds
                   - Tokens: %d (%.1fk)
                   - State: %s
                """,
                mainGoal,
                completedTasks, totalTasks, progressPercentage,
                failedTasks,
                elapsedSeconds,
                tokensUsed, tokensUsed / 1000.0,
                executionState.getDescription()
            );
        }
    }

    // ============================================================================
    // FACTORY METHODS
    // ============================================================================

    /**
     * Crea un AgentState con un plan de ejemplo para testing.
     */
    public static AgentState createExample(String goal) {
        var state = new AgentState(goal);

        state.addTask("Analyze requirements", "Understanding the current codebase");
        state.addTask("Create implementation plan", "Breaking down the task into steps");
        state.addTask("Generate code", "Writing the actual implementation");
        state.addTask("Run tests", "Executing test suite");
        state.addTask("Deploy changes", "Applying changes to production");

        return state;
    }

    /**
     * Crea un AgentState para una tarea especifica basada en el tipo.
     */
    public static AgentState createForTask(String goal, TaskType taskType) {
        var state = new AgentState(goal);

        switch (taskType) {
            case FEATURE_IMPLEMENTATION -> {
                state.addTask("Analyze feature requirements");
                state.addTask("Design architecture");
                state.addTask("Implement core functionality");
                state.addTask("Add error handling");
                state.addTask("Write tests");
                state.addTask("Update documentation");
            }
            case BUG_FIX -> {
                state.addTask("Reproduce the bug");
                state.addTask("Identify root cause");
                state.addTask("Implement fix");
                state.addTask("Verify fix works");
                state.addTask("Add regression tests");
            }
            case REFACTORING -> {
                state.addTask("Analyze current code");
                state.addTask("Plan refactoring approach");
                state.addTask("Refactor code incrementally");
                state.addTask("Run full test suite");
                state.addTask("Update documentation");
            }
        }

        return state;
    }

    /**
     * Tipos de tareas predefinidos para crear planes especificos.
     */
    public enum TaskType {
        FEATURE_IMPLEMENTATION,
        BUG_FIX,
        REFACTORING,
        TESTING,
        DOCUMENTATION
    }
}
