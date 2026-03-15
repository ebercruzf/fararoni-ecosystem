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
package dev.fararoni.core.agent;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class ProtocolStrategyFactory {
    private static final long SMALL_MODEL_THRESHOLD = 4_000_000_000L;
    private static final String ENV_FORCE_STRATEGY = "FARARONI_PROTOCOL_STRATEGY";

    private ProtocolStrategyFactory() {}

    public static OutputProtocolStrategy selectDefault() {
        String forced = System.getenv(ENV_FORCE_STRATEGY);
        if (forced != null && !forced.isBlank()) {
            return forceStrategy(forced);
        }

        return new ActionBlockStrategy();
    }

    public static OutputProtocolStrategy selectStrategy(long parameterCount, boolean isRemote) {
        String forced = System.getenv(ENV_FORCE_STRATEGY);
        if (forced != null && !forced.isBlank()) {
            return forceStrategy(forced);
        }

        if (isRemote) {
            return new JsonToolStrategy();
        }

        if (parameterCount < SMALL_MODEL_THRESHOLD) {
            return new ActionBlockStrategy();
        }

        return new JsonToolStrategy();
    }

    public static OutputProtocolStrategy forceStrategy(String strategyName) {
        if (strategyName == null) {
            return new ActionBlockStrategy();
        }

        return switch (strategyName.toLowerCase().trim()) {
            case "blocks", "action-blocks", "actionblocks", "lite" ->
                new ActionBlockStrategy();
            case "json", "tool-use", "tooluse", "full" ->
                new JsonToolStrategy();
            default ->
                throw new IllegalArgumentException("Unknown protocol strategy: " + strategyName +
                    ". Valid options: blocks, json");
        };
    }

    public static OutputProtocolStrategy actionBlocks() {
        return new ActionBlockStrategy();
    }

    public static OutputProtocolStrategy jsonToolUse() {
        return new JsonToolStrategy();
    }

    public static boolean isValidStrategy(String name) {
        if (name == null) return false;
        return switch (name.toLowerCase().trim()) {
            case "blocks", "action-blocks", "actionblocks", "lite",
                 "json", "tool-use", "tooluse", "full" -> true;
            default -> false;
        };
    }
}
