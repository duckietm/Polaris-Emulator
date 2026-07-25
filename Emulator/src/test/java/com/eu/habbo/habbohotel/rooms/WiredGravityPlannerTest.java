package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredGravityPlannerTest {

    @Test
    void unsupportedSingleAndMultiTileFurnitureUseTheHighestFootprintFloor() {
        WiredGravityPlanner.FurnitureSnapshot single = item(1, 5.0, 1.0, tile(1, 1));
        WiredGravityPlanner.FurnitureSnapshot multi = item(2, 7.0, 1.0, tile(2, 2), tile(3, 2));
        WiredGravityPlanner.Snapshot snapshot =
                snapshot(List.of(single, multi), Map.of(tile(1, 1), 0.0, tile(2, 2), 1.0, tile(3, 2), 2.0));

        WiredGravityPlanner.Plan plan = WiredGravityPlanner.plan(snapshot, Set.of(1, 2), 1000);

        assertEquals(
                List.of(1, 2),
                plan.falls().stream().map(fall -> fall.item().id()).toList());
        assertEquals(0.0, plan.falls().get(0).targetZ());
        assertEquals(2.0, plan.falls().get(1).targetZ());
    }

    @Test
    void supportIsEvaluatedFromOneSnapshotSoStacksSettleOneLayerAtATime() {
        WiredGravityPlanner.FurnitureSnapshot bottom = item(10, 3.0, 1.0, tile(4, 4));
        WiredGravityPlanner.FurnitureSnapshot top = item(11, 4.0, 1.0, tile(4, 4));
        WiredGravityPlanner.Snapshot snapshot = snapshot(List.of(top, bottom), Map.of(tile(4, 4), 0.0));

        WiredGravityPlanner.Plan firstLayer = WiredGravityPlanner.plan(snapshot, Set.of(10, 11), 1000);

        assertEquals(
                List.of(10),
                firstLayer.falls().stream().map(fall -> fall.item().id()).toList());
        assertEquals(0.0, firstLayer.falls().get(0).targetZ());
    }

    @Test
    void stableSupportAndInvalidFootprintsDoNotProduceFalls() {
        WiredGravityPlanner.FurnitureSnapshot support = item(20, 0.0, 2.0, tile(5, 5));
        WiredGravityPlanner.FurnitureSnapshot supported = item(21, 2.0, 1.0, tile(5, 5));
        WiredGravityPlanner.FurnitureSnapshot invalid = item(22, 9.0, 1.0);

        WiredGravityPlanner.Plan plan = WiredGravityPlanner.plan(
                snapshot(List.of(support, supported, invalid), Map.of(tile(5, 5), 0.0)), Set.of(21, 22), 1000);

        assertTrue(plan.falls().isEmpty());
        assertFalse(plan.bounded());
    }

    @Test
    void candidatesAreDeterministicByHeightThenId() {
        WiredGravityPlanner.FurnitureSnapshot high = item(1, 4.0, 1.0, tile(1, 1));
        WiredGravityPlanner.FurnitureSnapshot second = item(3, 2.0, 1.0, tile(3, 1));
        WiredGravityPlanner.FurnitureSnapshot first = item(2, 2.0, 1.0, tile(2, 1));

        WiredGravityPlanner.Plan plan = WiredGravityPlanner.plan(
                snapshot(List.of(high, second, first), Map.of(tile(1, 1), 0.0, tile(2, 1), 0.0, tile(3, 1), 0.0)),
                Set.of(1, 2, 3),
                1000);

        assertEquals(
                List.of(2, 3, 1),
                plan.falls().stream().map(fall -> fall.item().id()).toList());
    }

    @Test
    void workIsHardBoundedAtOneThousandGravityItems() {
        List<WiredGravityPlanner.FurnitureSnapshot> furniture = new ArrayList<>();
        Map<WiredGravityPlanner.TilePosition, Double> floors = new HashMap<>();
        Set<Integer> enabled = new HashSet<>();
        for (int id = 1100; id >= 1; id--) {
            WiredGravityPlanner.TilePosition tile = tile(id, 0);
            furniture.add(item(id, 1.0, 1.0, tile));
            floors.put(tile, 0.0);
            enabled.add(id);
        }

        WiredGravityPlanner.Plan plan = WiredGravityPlanner.plan(snapshot(furniture, floors), enabled, 1000);

        assertTrue(plan.bounded());
        assertEquals(1100, plan.candidateCount());
        assertEquals(1000, plan.falls().size());
        assertEquals(1, plan.falls().get(0).item().id());
        assertEquals(1000, plan.falls().get(999).item().id());
    }

    private static WiredGravityPlanner.FurnitureSnapshot item(
            int id, double z, double height, WiredGravityPlanner.TilePosition... footprint) {
        WiredGravityPlanner.TilePosition origin = footprint.length == 0 ? tile(0, 0) : footprint[0];
        return new WiredGravityPlanner.FurnitureSnapshot(id, origin.x(), origin.y(), z, 0, height, Set.of(footprint));
    }

    private static WiredGravityPlanner.TilePosition tile(int x, int y) {
        return new WiredGravityPlanner.TilePosition((short) x, (short) y);
    }

    private static WiredGravityPlanner.Snapshot snapshot(
            List<WiredGravityPlanner.FurnitureSnapshot> furniture,
            Map<WiredGravityPlanner.TilePosition, Double> floors) {
        return new WiredGravityPlanner.Snapshot(furniture, floors);
    }
}
