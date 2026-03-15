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
package dev.fararoni.core.core.telemetry;

import dev.fararoni.core.core.routing.RoutingPlan;

/**
 * @author Eber Cruz
 * @version 2.0.0
 * @since 1.0.0
 */
public interface ToolAwareTelemetry extends AutoCloseable {
    void onPhaseChange(String phaseName);

    void onModelSwitch(RoutingPlan.TargetModel targetModel);

    void onProcessingState(boolean isProcessing);

    void onToolStart(String toolName, String uiHint);

    void onToolFinish(String toolName, boolean success);

    @Override
    void close();

    static ToolAwareTelemetry noOp() {
        return NoOpToolAwareTelemetry.INSTANCE;
    }

    final class NoOpToolAwareTelemetry implements ToolAwareTelemetry {
        static final NoOpToolAwareTelemetry INSTANCE = new NoOpToolAwareTelemetry();

        private NoOpToolAwareTelemetry() {}

        @Override
        public void onPhaseChange(String phaseName) {
        }

        @Override
        public void onModelSwitch(RoutingPlan.TargetModel targetModel) {
        }

        @Override
        public void onProcessingState(boolean isProcessing) {
        }

        @Override
        public void onToolStart(String toolName, String uiHint) {
        }

        @Override
        public void onToolFinish(String toolName, boolean success) {
        }

        @Override
        public void close() {
        }
    }
}
