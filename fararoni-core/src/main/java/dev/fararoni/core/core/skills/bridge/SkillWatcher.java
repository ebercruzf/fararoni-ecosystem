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
package dev.fararoni.core.core.skills.bridge;

import dev.fararoni.bus.agent.api.DynamicSkill;
import dev.fararoni.bus.agent.api.ToolSkill;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public class SkillWatcher {
    private static final Logger LOG = Logger.getLogger(SkillWatcher.class.getName());

    private static final int CHECK_INTERVAL_SECONDS = 5;

    private final List<DynamicSkill> dynamicSkills = new CopyOnWriteArrayList<>();

    private final Map<String, Boolean> previousStates = new ConcurrentHashMap<>();

    private final List<Consumer<StateChangeEvent>> stateChangeListeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong totalCheckTimeNanos = new AtomicLong(0);

    public SkillWatcher() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SkillWatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public SkillWatcher register(DynamicSkill skill) {
        if (skill != null && !dynamicSkills.contains(skill)) {
            dynamicSkills.add(skill);
            previousStates.put(skill.getSkillName(), skill.isAvailable());
            LOG.info("[SKILL-WATCHER] Registrado: " + skill.getSkillName() +
                " (" + skill.getSidecarEndpoint() + ")");
        }
        return this;
    }

    public SkillWatcher registerAll(List<? extends DynamicSkill> skills) {
        for (DynamicSkill skill : skills) {
            register(skill);
        }
        return this;
    }

    public boolean registerIfDynamic(ToolSkill skill) {
        if (skill instanceof DynamicSkill dynamicSkill) {
            register(dynamicSkill);
            return true;
        }
        return false;
    }

    public boolean unregister(String skillName) {
        boolean removed = dynamicSkills.removeIf(s -> s.getSkillName().equals(skillName));
        if (removed) {
            previousStates.remove(skillName);
            LOG.info("[SKILL-WATCHER] Eliminado: " + skillName);
        }
        return removed;
    }

    public SkillWatcher onStateChange(Consumer<StateChangeEvent> listener) {
        if (listener != null) {
            stateChangeListeners.add(listener);
        }
        return this;
    }

    public void removeListener(Consumer<StateChangeEvent> listener) {
        stateChangeListeners.remove(listener);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            performHealthChecks();

            scheduler.scheduleWithFixedDelay(
                this::performHealthChecks,
                CHECK_INTERVAL_SECONDS,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS
            );

            LOG.info("[SKILL-WATCHER] Iniciado - Monitoreando " + dynamicSkills.size() +
                " skills cada " + CHECK_INTERVAL_SECONDS + "s");
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            LOG.info("[SKILL-WATCHER] Detenido - Total checks: " + totalChecks.get());
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void performHealthChecks() {
        if (dynamicSkills.isEmpty()) {
            return;
        }

        long startTime = System.nanoTime();

        for (DynamicSkill skill : dynamicSkills) {
            try {
                String skillName = skill.getSkillName();
                boolean wasPreviouslyAvailable = previousStates.getOrDefault(skillName, false);

                skill.refreshAvailability();

                boolean isNowAvailable = skill.isAvailable();

                if (wasPreviouslyAvailable != isNowAvailable) {
                    previousStates.put(skillName, isNowAvailable);
                    notifyStateChange(skill, wasPreviouslyAvailable, isNowAvailable);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[SKILL-WATCHER] Error chequeando " +
                    skill.getSkillName() + ": " + e.getMessage());
            }
        }

        long elapsed = System.nanoTime() - startTime;
        totalChecks.incrementAndGet();
        totalCheckTimeNanos.addAndGet(elapsed);
    }

    private void notifyStateChange(DynamicSkill skill, boolean wasAvailable, boolean isNowAvailable) {
        SkillState oldState = wasAvailable ? SkillState.ONLINE : SkillState.OFFLINE;
        SkillState newState = isNowAvailable ? SkillState.ONLINE : SkillState.OFFLINE;

        StateChangeEvent event = new StateChangeEvent(
            skill,
            oldState,
            newState,
            Instant.now()
        );

        if (isNowAvailable) {
            LOG.info("[SKILL-WATCHER] " + skill.getSkillName() + " -> ONLINE");
        } else {
            LOG.warning("[SKILL-WATCHER] " + skill.getSkillName() + " -> OFFLINE");
        }

        for (Consumer<StateChangeEvent> listener : stateChangeListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[SKILL-WATCHER] Error en listener", e);
            }
        }
    }

    public List<DynamicSkill> getOnlineSkills() {
        List<DynamicSkill> online = new ArrayList<>();
        for (DynamicSkill skill : dynamicSkills) {
            if (skill.isAvailable()) {
                online.add(skill);
            }
        }
        return online;
    }

    public List<DynamicSkill> getOfflineSkills() {
        List<DynamicSkill> offline = new ArrayList<>();
        for (DynamicSkill skill : dynamicSkills) {
            if (!skill.isAvailable()) {
                offline.add(skill);
            }
        }
        return offline;
    }

    public List<DynamicSkill> getSkillsByCategory(DynamicSkill.SidecarCategory category) {
        List<DynamicSkill> result = new ArrayList<>();
        for (DynamicSkill skill : dynamicSkills) {
            if (skill.getCategory() == category) {
                result.add(skill);
            }
        }
        return result;
    }

    public int getTotalSkills() {
        return dynamicSkills.size();
    }

    public int getOnlineCount() {
        int count = 0;
        for (DynamicSkill skill : dynamicSkills) {
            if (skill.isAvailable()) {
                count++;
            }
        }
        return count;
    }

    public WatcherStats getStats() {
        long checks = totalChecks.get();
        double avgTimeMs = checks > 0
            ? TimeUnit.NANOSECONDS.toMillis(totalCheckTimeNanos.get()) / (double) checks
            : 0;

        return new WatcherStats(
            dynamicSkills.size(),
            getOnlineCount(),
            checks,
            avgTimeMs,
            running.get()
        );
    }

    public String generateSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SKILL WATCHER STATUS ===\n");
        sb.append(String.format("Skills: %d total, %d online, %d offline\n",
            getTotalSkills(), getOnlineCount(), getTotalSkills() - getOnlineCount()));
        sb.append("---\n");

        for (DynamicSkill skill : dynamicSkills) {
            String status = skill.isAvailable() ? "ONLINE " : "OFFLINE";
            sb.append(String.format("[%s] %s (%s) - %s\n",
                status,
                skill.getSkillName(),
                skill.getCategory(),
                skill.getSidecarEndpoint()
            ));
        }

        return sb.toString();
    }

    public enum SkillState {
        ONLINE, OFFLINE
    }

    public record StateChangeEvent(
        DynamicSkill skill,
        SkillState oldState,
        SkillState newState,
        Instant timestamp
    ) {}

    public record WatcherStats(
        int totalSkills,
        int onlineSkills,
        long totalChecks,
        double avgCheckTimeMs,
        boolean running
    ) {}
}
