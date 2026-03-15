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

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class BusFactory {
    private static final Logger LOG = Logger.getLogger(BusFactory.class.getName());

    private static final String ENV_BUS_TYPE = "FARARONI_BUS_TYPE";

    private static final String PROP_BUS_TYPE = "fararoni.bus.type";

    private static final Path DEFAULT_CHRONICLE_PATH = Path.of(
        System.getProperty("user.home"), ".fararoni", "bus-data"
    );

    private BusFactory() {
        throw new UnsupportedOperationException("Clase utilitaria - no instanciar");
    }

    public enum BusType {
        MEMORY,

        CHRONICLE
    }

    public static SovereignEventBus create() {
        BusType type = detectBusType();
        return create(type);
    }

    public static SovereignEventBus create(BusType type) {
        return switch (type) {
            case MEMORY -> createInMemory();
            case CHRONICLE -> createChronicle();
        };
    }

    public static SovereignEventBus createInMemory() {
        LOG.info("[BusFactory] Creando InMemorySovereignBus");
        return new InMemorySovereignBus();
    }

    public static SovereignEventBus createChronicle() {
        return createChronicle(DEFAULT_CHRONICLE_PATH);
    }

    public static SovereignEventBus createChronicle(Path dataPath) {
        LOG.info(() -> "[BusFactory] Creando ChronicleQueueBus en: " + dataPath);
        try {
            return new ChronicleQueueBus(dataPath);
        } catch (Exception | NoClassDefFoundError | IllegalAccessError e) {
            LOG.warning(() -> "[BusFactory] Chronicle Queue no disponible: " + e.getMessage() +
                ". Usando InMemorySovereignBus como fallback.");
            LOG.warning("[BusFactory] Para habilitar persistencia, agregar JVM args:");
            LOG.warning("  --add-exports=java.base/sun.nio.ch=ALL-UNNAMED");
            LOG.warning("  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
            return new InMemorySovereignBus();
        }
    }

    public static SovereignEventBus createForTesting() {
        LOG.fine("[BusFactory] Creando bus para testing (InMemory)");
        return new InMemorySovereignBus();
    }

    public static BusType detectBusType() {
        String sysProp = System.getProperty(PROP_BUS_TYPE);
        if (sysProp != null && !sysProp.isBlank()) {
            return parseBusType(sysProp, "system property");
        }

        String envVar = System.getenv(ENV_BUS_TYPE);
        if (envVar != null && !envVar.isBlank()) {
            return parseBusType(envVar, "environment variable");
        }

        LOG.fine("[BusFactory] Usando tipo por defecto: MEMORY");
        return BusType.MEMORY;
    }

    private static BusType parseBusType(String value, String source) {
        try {
            BusType type = BusType.valueOf(value.toUpperCase().trim());
            LOG.info(() -> "[BusFactory] Tipo de bus desde " + source + ": " + type);
            return type;
        } catch (IllegalArgumentException e) {
            LOG.warning(() -> "[BusFactory] Tipo de bus invalido '" + value +
                "' desde " + source + ". Usando MEMORY.");
            return BusType.MEMORY;
        }
    }

    public static Path getDefaultChroniclePath() {
        return DEFAULT_CHRONICLE_PATH;
    }

    public static boolean isChronicleAvailable() {
        try {
            Class.forName("net.openhft.chronicle.queue.ChronicleQueue");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
