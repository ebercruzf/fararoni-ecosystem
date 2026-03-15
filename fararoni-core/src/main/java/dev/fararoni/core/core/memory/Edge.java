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
package dev.fararoni.core.core.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public record Edge(
    String id,
    String source,
    String relation,
    String target,
    double weight,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant lastAccessed,
    int accessCount
) {
    public Edge {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(relation, "relation must not be null");
        Objects.requireNonNull(target, "target must not be null");

        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString().substring(0, 12);
        }
        if (weight < 0.0 || weight > 1.0) {
            weight = Math.max(0.0, Math.min(1.0, weight));
        }
        if (metadata == null) {
            metadata = Map.of();
        } else {
            metadata = Map.copyOf(metadata);
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastAccessed == null) {
            lastAccessed = createdAt;
        }
    }

    public static Edge of(String source, String relation, String target) {
        return new Edge(null, source, relation, target, 1.0, null, null, null, 0);
    }

    public static Edge of(String source, String relation, String target, double weight) {
        return new Edge(null, source, relation, target, weight, null, null, null, 0);
    }

    public static Builder builder(String source, String relation, String target) {
        return new Builder(source, relation, target);
    }

    public boolean connectsTo(String nodeId) {
        return source.equals(nodeId) || target.equals(nodeId);
    }

    public String getOpposite(String nodeId) {
        if (source.equals(nodeId)) return target;
        if (target.equals(nodeId)) return source;
        return null;
    }

    public boolean isRelation(String relationType) {
        return relation.equalsIgnoreCase(relationType);
    }

    public boolean isBidirectional() {
        return relation.equalsIgnoreCase("relates_to") ||
               relation.equalsIgnoreCase("connected_to") ||
               relation.equalsIgnoreCase("similar_to") ||
               relation.equalsIgnoreCase("associated_with");
    }

    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null || !type.isInstance(value)) {
            return null;
        }
        return (T) value;
    }

    public String getMetadataString(String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    public Edge withAccess() {
        return new Edge(id, source, relation, target, weight, metadata,
            createdAt, Instant.now(), accessCount + 1);
    }

    public Edge withWeight(double newWeight) {
        return new Edge(id, source, relation, target, newWeight, metadata,
            createdAt, lastAccessed, accessCount);
    }

    public long getAgeSeconds() {
        return Instant.now().getEpochSecond() - createdAt.getEpochSecond();
    }

    public double getEvictionScore() {
        long timeSinceAccess = Instant.now().getEpochSecond() - lastAccessed.getEpochSecond();
        double recency = 1.0 / (1.0 + timeSinceAccess / 3600.0);
        return (weight * 0.4) + (recency * 0.4) + (Math.min(accessCount, 10) / 10.0 * 0.2);
    }

    public String toTriplet() {
        return String.format("%s --%s--> %s", source, relation, target);
    }

    public String toNaturalLanguage() {
        return String.format("%s %s %s", source, relation.replace("_", " "), target);
    }

    @Override
    public String toString() {
        return String.format("Edge[%s: %s --%s(%.2f)--> %s]",
            id.substring(0, Math.min(8, id.length())),
            source, relation, weight, target);
    }

    public static final class Builder {
        private final String source;
        private final String relation;
        private final String target;
        private String id;
        private double weight = 1.0;
        private final java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();

        private Builder(String source, String relation, String target) {
            this.source = source;
            this.relation = relation;
            this.target = target;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder weight(double weight) {
            this.weight = weight;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Edge build() {
            return new Edge(id, source, relation, target, weight, metadata, null, null, 0);
        }
    }
}
