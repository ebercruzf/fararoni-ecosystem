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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

/**
 * @author Eber Cruz
 * @version 1.0.0
 * @since 1.0.0
 */
public final class InMemoryGraph {
    private static final int DEFAULT_CAPACITY = 10000;
    private static final double EVICTION_THRESHOLD = 0.8;

    private final int maxEdges;
    private final Map<String, Edge> edgesById = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> edgesBySource = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> edgesByTarget = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> edgesByRelation = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public InMemoryGraph() {
        this(DEFAULT_CAPACITY);
    }

    public InMemoryGraph(int maxEdges) {
        this.maxEdges = maxEdges > 0 ? maxEdges : DEFAULT_CAPACITY;
    }

    public boolean addEdge(Edge edge) {
        Objects.requireNonNull(edge, "edge must not be null");

        lock.writeLock().lock();
        try {
            if (edgesById.size() >= maxEdges * EVICTION_THRESHOLD) {
                evictLeastRelevant();
            }

            if (edgesById.containsKey(edge.id())) {
                return false;
            }

            edgesById.put(edge.id(), edge);

            edgesBySource.computeIfAbsent(edge.source(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.id());

            edgesByTarget.computeIfAbsent(edge.target(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.id());

            edgesByRelation.computeIfAbsent(edge.relation().toLowerCase(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.id());

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Edge addOrUpdate(Edge edge) {
        Objects.requireNonNull(edge, "edge must not be null");

        lock.writeLock().lock();
        try {
            Edge previous = edgesById.get(edge.id());

            if (previous != null) {
                removeFromIndices(previous);
            } else if (edgesById.size() >= maxEdges * EVICTION_THRESHOLD) {
                evictLeastRelevant();
            }

            edgesById.put(edge.id(), edge);

            edgesBySource.computeIfAbsent(edge.source(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.id());

            edgesByTarget.computeIfAbsent(edge.target(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.id());

            edgesByRelation.computeIfAbsent(edge.relation().toLowerCase(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.id());

            return previous;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Edge removeEdge(String edgeId) {
        if (edgeId == null) return null;

        lock.writeLock().lock();
        try {
            Edge edge = edgesById.remove(edgeId);
            if (edge != null) {
                removeFromIndices(edge);
            }
            return edge;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int removeNode(String nodeId) {
        if (nodeId == null) return 0;

        lock.writeLock().lock();
        try {
            Set<String> toRemove = new HashSet<>();

            Set<String> outgoing = edgesBySource.get(nodeId);
            if (outgoing != null) {
                toRemove.addAll(outgoing);
            }

            Set<String> incoming = edgesByTarget.get(nodeId);
            if (incoming != null) {
                toRemove.addAll(incoming);
            }

            for (String edgeId : toRemove) {
                Edge edge = edgesById.remove(edgeId);
                if (edge != null) {
                    removeFromIndices(edge);
                }
            }

            return toRemove.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void removeFromIndices(Edge edge) {
        Set<String> sourceEdges = edgesBySource.get(edge.source());
        if (sourceEdges != null) {
            sourceEdges.remove(edge.id());
        }

        Set<String> targetEdges = edgesByTarget.get(edge.target());
        if (targetEdges != null) {
            targetEdges.remove(edge.id());
        }

        Set<String> relationEdges = edgesByRelation.get(edge.relation().toLowerCase());
        if (relationEdges != null) {
            relationEdges.remove(edge.id());
        }
    }

    public Optional<Edge> getEdge(String edgeId) {
        if (edgeId == null) return Optional.empty();

        Edge edge = edgesById.get(edgeId);
        if (edge != null) {
            edge = edge.withAccess();
            edgesById.put(edgeId, edge);
        }
        return Optional.ofNullable(edge);
    }

    public List<Edge> getOutgoing(String sourceId) {
        if (sourceId == null) return List.of();

        lock.readLock().lock();
        try {
            Set<String> edgeIds = edgesBySource.get(sourceId);
            if (edgeIds == null || edgeIds.isEmpty()) {
                return List.of();
            }

            return edgeIds.stream()
                .map(edgesById::get)
                .filter(Objects::nonNull)
                .map(Edge::withAccess)
                .peek(e -> edgesById.put(e.id(), e))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Edge> getIncoming(String targetId) {
        if (targetId == null) return List.of();

        lock.readLock().lock();
        try {
            Set<String> edgeIds = edgesByTarget.get(targetId);
            if (edgeIds == null || edgeIds.isEmpty()) {
                return List.of();
            }

            return edgeIds.stream()
                .map(edgesById::get)
                .filter(Objects::nonNull)
                .map(Edge::withAccess)
                .peek(e -> edgesById.put(e.id(), e))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Edge> getConnected(String nodeId) {
        List<Edge> result = new ArrayList<>();
        result.addAll(getOutgoing(nodeId));
        result.addAll(getIncoming(nodeId));
        return result;
    }

    public List<Edge> findByRelation(String relation) {
        if (relation == null) return List.of();

        lock.readLock().lock();
        try {
            Set<String> edgeIds = edgesByRelation.get(relation.toLowerCase());
            if (edgeIds == null || edgeIds.isEmpty()) {
                return List.of();
            }

            return edgeIds.stream()
                .map(edgesById::get)
                .filter(Objects::nonNull)
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Edge> findBy(Predicate<Edge> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");

        lock.readLock().lock();
        try {
            return edgesById.values().stream()
                .filter(predicate)
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Edge> findBetween(String source, String target) {
        if (source == null || target == null) return List.of();

        lock.readLock().lock();
        try {
            Set<String> sourceEdges = edgesBySource.get(source);
            if (sourceEdges == null) return List.of();

            return sourceEdges.stream()
                .map(edgesById::get)
                .filter(e -> e != null && e.target().equals(target))
                .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> getNeighbors(String nodeId) {
        return getNeighbors(nodeId, 1);
    }

    public Set<String> getNeighbors(String nodeId, int depth) {
        if (nodeId == null || depth < 1) return Set.of();

        Set<String> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Set<String> current = new HashSet<>();
        current.add(nodeId);
        visited.add(nodeId);

        for (int d = 0; d < depth; d++) {
            Set<String> next = new HashSet<>();
            for (String node : current) {
                for (Edge edge : getConnected(node)) {
                    String neighbor = edge.getOpposite(node);
                    if (neighbor != null && !visited.contains(neighbor)) {
                        visited.add(neighbor);
                        result.add(neighbor);
                        next.add(neighbor);
                    }
                }
            }
            current = next;
        }

        return result;
    }

    public List<Edge> findPath(String source, String target) {
        if (source == null || target == null || source.equals(target)) {
            return List.of();
        }

        Map<String, Edge> parentEdge = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(source);
        visited.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            for (Edge edge : getOutgoing(current)) {
                String neighbor = edge.target();
                if (visited.contains(neighbor)) continue;

                parentEdge.put(neighbor, edge);
                if (neighbor.equals(target)) {
                    List<Edge> path = new ArrayList<>();
                    String node = target;
                    while (parentEdge.containsKey(node)) {
                        Edge e = parentEdge.get(node);
                        path.add(0, e);
                        node = e.source();
                    }
                    return path;
                }

                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        return List.of();
    }

    private void evictLeastRelevant() {
        int toEvict = (int) (maxEdges * 0.1);

        List<Edge> sortedByScore = edgesById.values().stream()
            .sorted(Comparator.comparingDouble(Edge::getEvictionScore))
            .limit(toEvict)
            .toList();

        for (Edge edge : sortedByScore) {
            edgesById.remove(edge.id());
            removeFromIndices(edge);
        }
    }

    public int evictOlderThan(long maxAgeSeconds) {
        Instant threshold = Instant.now().minusSeconds(maxAgeSeconds);

        List<Edge> toEvict = edgesById.values().stream()
            .filter(e -> e.createdAt().isBefore(threshold))
            .toList();

        for (Edge edge : toEvict) {
            edgesById.remove(edge.id());
            removeFromIndices(edge);
        }

        return toEvict.size();
    }

    public int evictBelowWeight(double minWeight) {
        List<Edge> toEvict = edgesById.values().stream()
            .filter(e -> e.weight() < minWeight)
            .toList();

        for (Edge edge : toEvict) {
            edgesById.remove(edge.id());
            removeFromIndices(edge);
        }

        return toEvict.size();
    }

    public int size() {
        return edgesById.size();
    }

    public boolean isEmpty() {
        return edgesById.isEmpty();
    }

    public int getCapacity() {
        return maxEdges;
    }

    public int getNodeCount() {
        Set<String> nodes = new HashSet<>();
        nodes.addAll(edgesBySource.keySet());
        nodes.addAll(edgesByTarget.keySet());
        return nodes.size();
    }

    public Set<String> getAllNodes() {
        Set<String> nodes = new HashSet<>();
        nodes.addAll(edgesBySource.keySet());
        nodes.addAll(edgesByTarget.keySet());
        return nodes;
    }

    public Map<String, Object> getStats() {
        return Map.of(
            "edges", size(),
            "nodes", getNodeCount(),
            "capacity", maxEdges,
            "utilization", (double) size() / maxEdges,
            "relations", edgesByRelation.keySet().size()
        );
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            edgesById.clear();
            edgesBySource.clear();
            edgesByTarget.clear();
            edgesByRelation.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static InMemoryGraph withCapacity(int capacity) {
        return new InMemoryGraph(capacity);
    }
}
