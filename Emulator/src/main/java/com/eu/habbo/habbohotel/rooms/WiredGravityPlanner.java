package com.eu.habbo.habbohotel.rooms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, deterministic gravity planning over an immutable room snapshot. */
final class WiredGravityPlanner {
    static final double HEIGHT_EPSILON = 0.0001D;

    private WiredGravityPlanner() {}

    static Plan plan(Snapshot snapshot, Set<Integer> enabledItemIds, int maximumItems) {
        if (snapshot == null || enabledItemIds == null || enabledItemIds.isEmpty() || maximumItems <= 0) {
            return new Plan(List.of(), false, 0);
        }

        List<FurnitureSnapshot> candidates = snapshot.furniture().stream()
                .filter(item -> enabledItemIds.contains(item.id()))
                .sorted(Comparator.comparingDouble(FurnitureSnapshot::z).thenComparingInt(FurnitureSnapshot::id))
                .toList();
        boolean bounded = candidates.size() > maximumItems;
        int candidateCount = candidates.size();
        if (bounded) {
            candidates = candidates.subList(0, maximumItems);
        }

        List<Fall> falls = new ArrayList<>();
        for (FurnitureSnapshot candidate : candidates) {
            double targetZ = restingHeight(snapshot, candidate);
            if (candidate.z() - targetZ > HEIGHT_EPSILON) {
                falls.add(new Fall(candidate, targetZ));
            }
        }
        return new Plan(List.copyOf(falls), bounded, candidateCount);
    }

    private static double restingHeight(Snapshot snapshot, FurnitureSnapshot candidate) {
        if (candidate.footprint().isEmpty()) {
            return candidate.z();
        }

        double targetZ = Double.NEGATIVE_INFINITY;
        for (TilePosition tile : candidate.footprint()) {
            Double floorHeight = snapshot.floorHeights().get(tile);
            if (floorHeight == null) {
                return candidate.z();
            }

            double tileSupport = floorHeight;
            for (FurnitureSnapshot support : snapshot.furniture()) {
                if (support.id() == candidate.id()
                        || !support.footprint().contains(tile)
                        || support.z() + support.height() > candidate.z() + HEIGHT_EPSILON) {
                    continue;
                }
                tileSupport = Math.max(tileSupport, support.z() + support.height());
            }
            targetZ = Math.max(targetZ, tileSupport);
        }

        return !Double.isFinite(targetZ) || targetZ > candidate.z() ? candidate.z() : targetZ;
    }

    record TilePosition(short x, short y) {}

    record FurnitureSnapshot(
            int id, short x, short y, double z, int rotation, double height, Set<TilePosition> footprint) {
        FurnitureSnapshot {
            footprint = Set.copyOf(new LinkedHashSet<>(footprint));
        }
    }

    record Snapshot(List<FurnitureSnapshot> furniture, Map<TilePosition, Double> floorHeights) {
        Snapshot {
            furniture = List.copyOf(furniture);
            floorHeights = Map.copyOf(floorHeights);
        }
    }

    record Fall(FurnitureSnapshot item, double targetZ) {
        double distance() {
            return item.z() - targetZ;
        }
    }

    record Plan(List<Fall> falls, boolean bounded, int candidateCount) {}
}
