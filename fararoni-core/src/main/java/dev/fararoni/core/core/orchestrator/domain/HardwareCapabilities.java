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
package dev.fararoni.core.core.orchestrator.domain;

import java.util.Set;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public record HardwareCapabilities(

    Set<String> devices,

    String location,

    boolean hasGpuAcceleration

) {
    public static final String DEVICE_HUE_BRIDGE = "HUE_BRIDGE";

    public static final String DEVICE_SONOS = "SONOS_SPEAKER";

    public static final String DEVICE_CAMERA = "CAMERA_FRONT";

    public static final String DEVICE_MIC_ARRAY = "MIC_ARRAY";

    public static final String DEVICE_ZIGBEE_HUB = "ZIGBEE_HUB";

    public static final String DEVICE_NVIDIA_GPU = "NVIDIA_GPU";

    public static final String LOC_HOME_LAN = "HOME_LAN";

    public static final String LOC_CLOUD = "CLOUD";

    public static final String LOC_CLOUD_US_EAST = "CLOUD_US_EAST";

    public boolean hasDevice(String device) {
        return devices != null && devices.contains(device);
    }

    public boolean isAtLocation(String targetLocation) {
        if (targetLocation == null || location == null) {
            return false;
        }
        return location.equals(targetLocation);
    }

    public static HardwareCapabilities empty() {
        return new HardwareCapabilities(Set.of(), LOC_CLOUD, false);
    }

    public static HardwareCapabilities cloudWithGpu(String location) {
        return new HardwareCapabilities(Set.of(DEVICE_NVIDIA_GPU), location, true);
    }
}
