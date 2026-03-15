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
package dev.fararoni.core.core.swarm.context;

import dev.fararoni.core.core.index.IndexStore;
import dev.fararoni.core.core.kernel.HyperNativeKernel;
import dev.fararoni.core.core.swarm.MissionConfig;
import dev.fararoni.core.core.swarm.infra.MessageBus;
import dev.fararoni.core.core.swarm.infra.SwarmTransport;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SwarmContext {
    private static final Logger LOG = Logger.getLogger(SwarmContext.class.getName());

    public static final ScopedValue<HyperNativeKernel> KERNEL = ScopedValue.newInstance();

    public static final ScopedValue<String> MISSION_ID = ScopedValue.newInstance();

    public static final ScopedValue<Integer> RECURSION_DEPTH = ScopedValue.newInstance();

    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

    public static final ScopedValue<SwarmTransport> BUS = ScopedValue.newInstance();

    public static final ScopedValue<Path> WORKSPACE = ScopedValue.newInstance();

    public static final ScopedValue<MissionConfig> MISSION_CONFIG = ScopedValue.newInstance();

    public static final ScopedValue<IndexStore> INDEX_STORE = ScopedValue.newInstance();

    private static final int MAX_RECURSION_DEPTH = 10;

    private SwarmContext() {
    }

    @Deprecated
    public static void runWith(HyperNativeKernel kernel, String missionId, Runnable task) {
        runWith(kernel, MessageBus.getInstance(), missionId, task);
    }

    @Deprecated
    public static void runWith(HyperNativeKernel kernel, SwarmTransport bus, String missionId, Runnable task) {
        runWith(kernel, bus, Path.of(System.getProperty("user.dir")), missionId, task);
    }

    public static void runWith(HyperNativeKernel kernel, SwarmTransport bus, Path workspace, String missionId, Runnable task) {
        runWith(kernel, bus, workspace, missionId, MissionConfig.fastMode(), task);
    }

    public static void runWith(HyperNativeKernel kernel, SwarmTransport bus, Path workspace,
                               String missionId, MissionConfig config, Runnable task) {
        String traceId = generateTraceId();

        LOG.fine(() -> String.format("[SwarmContext] Starting mission %s (trace: %s, workspace: %s, config: %s)",
            missionId, traceId, workspace, config.level()));

        ScopedValue.where(KERNEL, kernel)
            .where(BUS, bus)
            .where(WORKSPACE, workspace)
            .where(MISSION_ID, missionId)
            .where(MISSION_CONFIG, config)
            .where(RECURSION_DEPTH, 0)
            .where(TRACE_ID, traceId)
            .run(task);
    }

    @Deprecated
    public static <T> T callWith(HyperNativeKernel kernel, String missionId,
            ScopedValue.CallableOp<T, Exception> task) throws Exception {
        return callWith(kernel, MessageBus.getInstance(), missionId, task);
    }

    @Deprecated
    public static <T> T callWith(HyperNativeKernel kernel, SwarmTransport bus, String missionId,
            ScopedValue.CallableOp<T, Exception> task) throws Exception {
        return callWith(kernel, bus, Path.of(System.getProperty("user.dir")), missionId, task);
    }

    public static <T> T callWith(HyperNativeKernel kernel, SwarmTransport bus, Path workspace, String missionId,
            ScopedValue.CallableOp<T, Exception> task) throws Exception {
        return callWith(kernel, bus, workspace, missionId, MissionConfig.fastMode(), task);
    }

    public static <T> T callWith(HyperNativeKernel kernel, SwarmTransport bus, Path workspace, String missionId,
            MissionConfig config, ScopedValue.CallableOp<T, Exception> task) throws Exception {
        return callWith(kernel, bus, workspace, missionId, config, null, task);
    }

    public static <T> T callWith(HyperNativeKernel kernel, SwarmTransport bus, Path workspace, String missionId,
            MissionConfig config, IndexStore indexStore, ScopedValue.CallableOp<T, Exception> task) throws Exception {
        String traceId = generateTraceId();

        var carrier = ScopedValue.where(KERNEL, kernel)
            .where(BUS, bus)
            .where(WORKSPACE, workspace)
            .where(MISSION_ID, missionId)
            .where(MISSION_CONFIG, config)
            .where(RECURSION_DEPTH, 0)
            .where(TRACE_ID, traceId);

        if (indexStore != null) {
            carrier = carrier.where(INDEX_STORE, indexStore);
        }

        return carrier.call(task);
    }

    public static void incrementDepth(Runnable task) {
        int currentDepth = RECURSION_DEPTH.orElse(0);

        if (currentDepth >= MAX_RECURSION_DEPTH) {
            String missionId = MISSION_ID.orElse("UNKNOWN");
            LOG.severe(() -> String.format(
                "[SwarmContext] MAX RECURSION DEPTH REACHED (%d) for mission %s",
                MAX_RECURSION_DEPTH, missionId));
            throw new IllegalStateException(
                "Profundidad máxima de recursión alcanzada: " + MAX_RECURSION_DEPTH);
        }

        ScopedValue.where(RECURSION_DEPTH, currentDepth + 1).run(task);
    }

    public static <T> T incrementDepthAndCall(ScopedValue.CallableOp<T, Exception> task)
            throws Exception {
        int currentDepth = RECURSION_DEPTH.orElse(0);

        if (currentDepth >= MAX_RECURSION_DEPTH) {
            throw new IllegalStateException(
                "Profundidad máxima de recursión alcanzada: " + MAX_RECURSION_DEPTH);
        }

        return ScopedValue.where(RECURSION_DEPTH, currentDepth + 1).call(task);
    }

    public static HyperNativeKernel kernel() {
        return KERNEL.orElseThrow(() ->
            new IllegalStateException("No hay Kernel en el contexto. ¿Ejecutaste dentro de runWith?"));
    }

    public static SwarmTransport bus() {
        return BUS.orElseThrow(() ->
            new IllegalStateException("No hay Bus en el contexto. ¿Ejecutaste dentro de runWith con bus?"));
    }

    @SuppressWarnings("deprecation")
    public static SwarmTransport busOrDefault() {
        return BUS.orElse(MessageBus.getInstance());
    }

    public static Path workspace() {
        return WORKSPACE.orElseThrow(() ->
            new IllegalStateException("No hay Workspace en el contexto. ¿Ejecutaste dentro de runWith con workspace?"));
    }

    public static Path workspaceOrDefault() {
        return WORKSPACE.orElse(Path.of(System.getProperty("user.dir")));
    }

    public static MissionConfig missionConfig() {
        return MISSION_CONFIG.orElseThrow(() ->
            new IllegalStateException("No hay MissionConfig en el contexto."));
    }

    public static MissionConfig missionConfigOrDefault() {
        return MISSION_CONFIG.orElse(MissionConfig.fastMode());
    }

    public static IndexStore indexStoreOrNull() {
        return INDEX_STORE.orElse(null);
    }

    public static void registerFileIfAvailable(java.nio.file.Path absolutePath) {
        IndexStore store = indexStoreOrNull();
        if (store != null && store.isAvailable()) {
            store.registerFile(absolutePath);
        }
    }

    public static String getNextAgent(String currentAgent) {
        return missionConfigOrDefault().getNextAgent(currentAgent);
    }

    public static String missionId() {
        return MISSION_ID.orElseThrow(() ->
            new IllegalStateException("No hay Mission ID en el contexto."));
    }

    public static int currentDepth() {
        return RECURSION_DEPTH.orElse(0);
    }

    public static String traceId() {
        return TRACE_ID.orElse("NO-TRACE");
    }

    public static boolean isInContext() {
        return KERNEL.isBound() && MISSION_ID.isBound();
    }

    public static boolean isNearRecursionLimit(int threshold) {
        int current = currentDepth();
        return current >= (MAX_RECURSION_DEPTH - threshold);
    }

    public static int getMaxRecursionDepth() {
        return MAX_RECURSION_DEPTH;
    }

    private static String generateTraceId() {
        return "T-" + Long.toHexString(System.nanoTime());
    }

    public static ContextDiagnostics getDiagnostics() {
        return new ContextDiagnostics(
            isInContext(),
            MISSION_ID.orElse(null),
            TRACE_ID.orElse(null),
            currentDepth(),
            MAX_RECURSION_DEPTH,
            KERNEL.isBound(),
            WORKSPACE.orElse(null)
        );
    }

    public record ContextDiagnostics(
        boolean inContext,
        String missionId,
        String traceId,
        int currentDepth,
        int maxDepth,
        boolean hasKernel,
        Path workspace
    ) {
        @Override
        public String toString() {
            return String.format(
                "SwarmContext[inContext=%b, mission=%s, trace=%s, depth=%d/%d, kernel=%b, workspace=%s]",
                inContext, missionId, traceId, currentDepth, maxDepth, hasKernel, workspace);
        }
    }
}
