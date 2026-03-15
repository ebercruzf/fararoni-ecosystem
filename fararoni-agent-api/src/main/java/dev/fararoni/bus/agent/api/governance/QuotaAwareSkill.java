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
package dev.fararoni.bus.agent.api.governance;

import dev.fararoni.bus.agent.api.ToolSkill;

/**
 * Interface for skills that support cost estimation and budget control.
 *
 * <p>Implement this interface when your skill has associated costs (API calls,
 * token usage, cloud resources) that need to be monitored and controlled.
 * This prevents runaway AI agents from generating surprise bills.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class OpenAISkillImpl implements OpenAISkill, QuotaAwareSkill {
 *
 *     @Override
 *     public double estimateCost(String action, Object... args) {
 *         return switch (action) {
 *             case "complete" -> {
 *                 String prompt = (String) args[0];
 *                 int tokens = countTokens(prompt);
 *                 yield tokens * 0.00002; // $0.02 per 1K tokens
 *             }
 *             case "embed" -> {
 *                 yield 0.0001; // Fixed cost per embedding
 *             }
 *             default -> -1; // Unknown
 *         };
 *     }
 *
 *     @Override
 *     public double getRemainingBudget() {
 *         return budgetManager.getRemaining();
 *     }
 * }
 *
 * // Usage in orchestrator
 * double cost = skill.estimateCost("complete", prompt);
 * if (cost > skill.getRemainingBudget()) {
 *     return FNLResult.failure("Budget exceeded: need $" + cost + ", have $" + budget);
 * }
 * }</pre>
 *
 * <h2>Enterprise Value</h2>
 * <ul>
 *   <li>Prevent surprise cloud bills from AI agents</li>
 *   <li>Enforce departmental budgets</li>
 *   <li>Audit resource consumption</li>
 *   <li>Implement tiered pricing (free/pro/enterprise)</li>
 * </ul>
 *
 * <h2>Module</h2>
 * <p><strong>fararoni-agent-api</strong> (Contract - Open Source)</p>
 *
 * @author Eber Cruz
 * @since 1.0.0
 */
public interface QuotaAwareSkill extends ToolSkill {

    /**
     * Estimates the cost of an operation before executing it.
     *
     * <p>The orchestrator calls this before invoking the actual method
     * to verify budget availability.</p>
     *
     * @param action the action/method name to estimate
     * @param args the arguments that will be passed to the action
     * @return estimated cost in credits/tokens/dollars, or -1 if unknown
     */
    double estimateCost(String action, Object... args);

    /**
     * Gets the remaining budget for this skill.
     *
     * <p>This could be API credits, token allowance, or monetary budget.</p>
     *
     * @return remaining budget, or Double.MAX_VALUE if unlimited
     */
    double getRemainingBudget();

    /**
     * Records that cost was consumed.
     *
     * <p>Called after successful execution to track actual usage.</p>
     *
     * @param cost the cost that was consumed
     */
    default void recordCost(double cost) {
        // Default no-op, override to track costs
    }

    /**
     * Gets the total cost consumed by this skill.
     *
     * @return total cost consumed
     */
    default double getTotalCostConsumed() {
        return 0;
    }

    /**
     * Checks if the skill has enough budget for an estimated cost.
     *
     * @param estimatedCost the estimated cost
     * @return true if budget is sufficient
     */
    default boolean hasBudget(double estimatedCost) {
        if (estimatedCost < 0) return true; // Unknown cost = allow
        return getRemainingBudget() >= estimatedCost;
    }

    /**
     * Gets the unit of cost measurement.
     *
     * @return cost unit (e.g., "tokens", "credits", "USD")
     */
    default String getCostUnit() {
        return "credits";
    }
}
