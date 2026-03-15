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
package dev.fararoni.core.core.security.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GuestManager implements IGuestManager {
    private static final Logger LOG = Logger.getLogger(GuestManager.class.getName());

    private final Set<String> guestIds = Collections.synchronizedSet(new HashSet<>());

    public GuestManager() {
        loadFromDisk();
    }

    @Override
    public void addGuest(String targetId) {
        guestIds.add(targetId);
        saveToDisk();
        LOG.info("[GUEST-MANAGER] Invitado agregado: " + targetId);
    }

    @Override
    public boolean removeGuest(String targetId) {
        boolean removed = guestIds.remove(targetId);
        if (removed) {
            saveToDisk();
            LOG.info("[GUEST-MANAGER] Invitado revocado: " + targetId);
        }
        return removed;
    }

    @Override
    public boolean isGuest(String channelId) {
        if (guestIds.contains(channelId)) return true;
        if (channelId.contains(":")) {
            String incomingId = channelId.substring(channelId.indexOf(":") + 1);
            for (String guest : guestIds) {
                String guestId = guest.contains(":") ? guest.substring(guest.indexOf(":") + 1) : guest;
                if (guestId.equals(incomingId)) return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> getGuestList() {
        return new HashSet<>(guestIds);
    }

    private void loadFromDisk() {
        try {
            if (Files.exists(SecurityConstants.GUESTS_PATH)) {
                Set<String> loaded = Files.readAllLines(SecurityConstants.GUESTS_PATH)
                        .stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toSet());
                guestIds.addAll(loaded);
                LOG.info("[GUEST-MANAGER] Cargados " + loaded.size() + " invitados de disco");
            }
        } catch (IOException e) {
            LOG.warning("[GUEST-MANAGER] Error cargando guests.txt: " + e.getMessage());
        }
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(SecurityConstants.GUESTS_PATH.getParent());
            String content = String.join("\n", guestIds);
            Files.writeString(SecurityConstants.GUESTS_PATH, content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.setPosixFilePermissions(SecurityConstants.GUESTS_PATH,
                        PosixFilePermissions.fromString(SecurityConstants.POSIX_OWNER_ONLY));
            } catch (UnsupportedOperationException ignored) {
            }
        } catch (IOException e) {
            LOG.severe("[GUEST-MANAGER] Error guardando guests.txt: " + e.getMessage());
        }
    }
}
