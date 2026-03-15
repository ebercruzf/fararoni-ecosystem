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
package dev.fararoni.core.core.bus;

import dev.fararoni.bus.agent.api.bus.SovereignEventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SovereignBusFactory {
    private static final Logger LOG = Logger.getLogger(SovereignBusFactory.class.getName());

    private static final int PRODUCTION_MIN_PRIORITY = 50;

    private static volatile SovereignEventBus resolvedBus;

    private static final List<SovereignEventBus> allDetectedBuses = new ArrayList<>();

    private static volatile SovereignEventBus activeBus;

    private static volatile boolean initialized = false;

    private SovereignBusFactory() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        synchronized (SovereignBusFactory.class) {
            if (initialized) {
                return;
            }

            LOG.info("[BUS-FACTORY] Inicializando SovereignBusFactory...");

            ServiceLoader<SovereignEventBus> loader = ServiceLoader.load(SovereignEventBus.class);
            allDetectedBuses.clear();

            for (SovereignEventBus bus : loader) {
                allDetectedBuses.add(bus);
                LOG.fine("[BUS-FACTORY] Detectado: " + bus.getClass().getSimpleName() +
                    " (P:" + bus.getPriority() + ")");
            }

            boolean hasInMemory = allDetectedBuses.stream()
                .anyMatch(b -> b instanceof InMemorySovereignBus);
            if (!hasInMemory) {
                allDetectedBuses.add(new InMemorySovereignBus());
            }

            LOG.info("[BUS-FACTORY] Total buses detectados: " + allDetectedBuses.size());

            refreshActiveBus();

            initialized = true;
        }
    }

    public static void refreshActiveBus() {
        synchronized (SovereignBusFactory.class) {
            SovereignEventBus previousActive = activeBus;

            activeBus = allDetectedBuses.stream()
                .filter(SovereignEventBus::isAvailable)
                .max(Comparator.comparingInt(SovereignEventBus::getPriority))
                .orElseGet(() -> {
                    LOG.warning("[BUS-FACTORY] Ningun bus disponible. Creando InMemory fallback.");
                    InMemorySovereignBus fallback = new InMemorySovereignBus();
                    if (!allDetectedBuses.contains(fallback)) {
                        allDetectedBuses.add(fallback);
                    }
                    return fallback;
                });

            if (previousActive != null && previousActive != activeBus) {
                LOG.info("[BUS-FACTORY] Cambio de bus activo: " +
                    previousActive.getClass().getSimpleName() + " -> " +
                    activeBus.getClass().getSimpleName());
            }

            resolvedBus = activeBus;
        }
    }

    public static List<SovereignEventBus> getAllDetectedBuses() {
        if (!initialized) {
            init();
        }
        return Collections.unmodifiableList(allDetectedBuses);
    }

    public static SovereignEventBus getActiveBus() {
        if (!initialized) {
            init();
        }
        return activeBus;
    }

    public static SovereignEventBus resolveBestBus() {
        if (resolvedBus != null) {
            return resolvedBus;
        }

        synchronized (SovereignBusFactory.class) {
            if (resolvedBus != null) {
                return resolvedBus;
            }

            ServiceLoader<SovereignEventBus> loader = ServiceLoader.load(SovereignEventBus.class);

            LOG.info("[BUS-FACTORY] Buscando implementaciones de SovereignEventBus...");

            SovereignEventBus selected = loader.stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparingInt(SovereignEventBus::getPriority).reversed())
                .filter(bus -> {
                    boolean healthy = bus.isAvailable();
                    int priority = bus.getPriority();
                    String name = bus.getClass().getSimpleName();

                    if (healthy) {
                        LOG.info("[BUS-FACTORY]    -> " + name + " (P:" + priority + ") - DISPONIBLE");
                    } else {
                        if (priority >= 100) {
                            LOG.severe("[BUS-FACTORY]    -> " + name + " (P:" + priority +
                                ") - NO DISPONIBLE (Servidor Enterprise Offline)");
                        } else {
                            LOG.warning("[BUS-FACTORY]    -> " + name + " (P:" + priority +
                                ") - NO DISPONIBLE");
                        }
                    }
                    return healthy;
                })
                .findFirst()
                .orElseGet(() -> {
                    LOG.warning("[BUS-FACTORY] Ningun bus SPI disponible. Usando fallback InMemory.");
                    return new InMemorySovereignBus();
                });

            int selectedPriority = selected.getPriority();
            String selectedName = selected.getClass().getSimpleName();

            if (selectedPriority >= 100) {
                LOG.info("[MILITARY-GRADE] Bus Enterprise detectado: " + selectedName +
                    " (P:" + selectedPriority + ")");
            } else if (selectedPriority >= PRODUCTION_MIN_PRIORITY) {
                LOG.info("[PRODUCTION] Bus Persistente Local: " + selectedName +
                    " (P:" + selectedPriority + ")");
            } else {
                LOG.warning("[DEVELOPMENT] Bus Volatil en uso: " + selectedName +
                    " (P:" + selectedPriority + ") - NO USAR EN PRODUCCION");
            }

            resolvedBus = selected;
            return selected;
        }
    }

    public static SovereignEventBus resolveGuardedBus() {
        ServiceLoader<SovereignEventBus> loader = ServiceLoader.load(SovereignEventBus.class);

        SovereignEventBus primary = null;
        SovereignEventBus standby = null;

        for (SovereignEventBus bus : loader) {
            int priority = bus.getPriority();
            LOG.info("[BUS-FACTORY] Detectado: " + bus.getClass().getSimpleName() + " (P:" + priority + ")");
            if (priority >= 100 && primary == null) {
                primary = bus;
            } else if (priority >= 50 && priority < 100 && standby == null) {
                standby = bus;
            }
        }

        if (primary == null) {
            LOG.info("[BUS-FACTORY] No hay bus Enterprise (P:100). Usando seleccion estandar.");
            return resolveBestBus();
        }

        if (standby == null) {
            LOG.info("[BUS-FACTORY] Creando ChronicleQueueBus como standby persistente...");
            standby = new ChronicleQueueBus(
                java.nio.file.Path.of(System.getProperty("user.home"), ".fararoni", "bus-buffer")
            );
        }

        LOG.info("[MILITARY-GRADE] Configurando SovereignBusGuard:");
        LOG.info("    Primary: " + primary.getClass().getSimpleName() + " (P:" + primary.getPriority() + ")");
        LOG.info("    Standby: " + standby.getClass().getSimpleName() + " (P:" + standby.getPriority() + ")");

        return new SovereignBusGuard(primary, standby);
    }

    public static void reset() {
        synchronized (SovereignBusFactory.class) {
            resolvedBus = null;
            activeBus = null;
            allDetectedBuses.clear();
            initialized = false;
        }
    }

    public static boolean isDevelopmentMode() {
        String env = System.getenv("FARARONI_ENV");
        return env == null || "development".equalsIgnoreCase(env) || "dev".equalsIgnoreCase(env);
    }
}
