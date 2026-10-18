package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The direction rules, with made-up direction sets. */
class FurnitureDirectionsTest {

    private static FurnitureDirections dirs(int defaultDirection, int... directions) {
        return FurnitureDirections.of(directions, defaultDirection);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void logicDirectionsAreDegreesSortedIntoRotationUnits() {
        assertArrayEquals(
                new int[] {0, 2}, FurnitureDirections.parse(json("{\"logic\":{\"model\":{\"directions\":[90,0]}}}")));
        assertArrayEquals(
                new int[] {0, 2, 4, 6},
                FurnitureDirections.parse(json("{\"logic\":{\"model\":{\"directions\":[0,90,180,270]}}}")));
        assertArrayEquals(
                new int[] {0, 1, 2, 3, 4, 5, 6, 7},
                FurnitureDirections.parse(
                        json("{\"logic\":{\"model\":{\"directions\":[0,45,90,135,180,225,270,315,90]}}}")));
    }

    @Test
    void withoutLogicDirectionsTheSize64VisualizationDecides() {
        JsonObject library = json("{\"logic\":{\"model\":{}},\"visualizations\":["
                + "{\"size\":1,\"directions\":{\"0\":{}}},"
                + "{\"size\":64,\"directions\":{\"6\":{},\"2\":{}}}]}");

        assertArrayEquals(new int[] {2, 6}, FurnitureDirections.parse(library));
    }

    @Test
    void aLibraryWithoutDirectionsIsUnknown() {
        assertArrayEquals(new int[0], FurnitureDirections.parse(json("{\"name\":\"x\"}")));
        assertArrayEquals(new int[0], FurnitureDirections.parse(null));
        assertSame(FurnitureDirections.UNKNOWN, FurnitureDirections.of(new int[0], 2));
        assertSame(FurnitureDirections.UNKNOWN, FurnitureDirections.of(new int[] {9, -1}, 2));
        assertFalse(FurnitureDirections.UNKNOWN.isKnown());
    }

    // AIR rotates to the next entry of furniture_allowed_directions; a direction it lacks counts as
    // the first entry at or after it.
    @Test
    void turningStepsToTheNextDirectionInFortyFiveDegreeOrder() {
        FurnitureDirections stairs = dirs(0, 0, 1, 2, 3, 4, 5, 6, 7);
        assertEquals(List.of(1, 2, 3), walk(stairs, 0, true, 3));
        assertEquals(List.of(7, 6, 5), walk(stairs, 0, false, 3));

        FurnitureDirections counter = dirs(2, 2, 4);
        assertEquals(List.of(4, 2, 4), walk(counter, 2, true, 3));
        assertEquals(List.of(4, 2, 4), walk(counter, 2, false, 3));

        FurnitureDirections fourWay = dirs(0, 0, 2, 4, 6);
        assertEquals(List.of(2, 4, 6, 0), walk(fourWay, 0, true, 4));
        assertEquals(List.of(6, 4, 2, 0), walk(fourWay, 0, false, 4));
    }

    @Test
    void aRotationTheFurniLacksCountsAsTheDirectionAtOrAfterIt() {
        FurnitureDirections bed = dirs(2, 0, 2);
        // 1 counts as 2: clockwise goes on to 0, counterclockwise back to 0.
        assertEquals(0, bed.turn(1, true));
        assertEquals(0, bed.turn(1, false));
        // 5 is after the last direction: it wraps to 0.
        assertEquals(2, bed.turn(5, true));
        assertEquals(2, bed.turn(5, false));
    }

    @Test
    void aFurniWithOneDirectionNeverTurns() {
        FurnitureDirections tile = dirs(0, 0);
        assertEquals(0, tile.turn(0, true));
        assertEquals(0, tile.turn(0, false));
        assertEquals(0, tile.random(0, new Random(1)));
    }

    @Test
    void randomPicksAnotherDirectionTheFurniHas() {
        FurnitureDirections bed = dirs(2, 0, 2);
        Random random = new Random(7);
        for (int i = 0; i < 20; i++) {
            assertEquals(2, bed.random(0, random));
            assertEquals(0, bed.random(2, random));
        }

        FurnitureDirections fourWay = dirs(0, 0, 2, 4, 6);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(fourWay.random(0, random));
        }
        assertEquals(Set.of(2, 4, 6), seen);
    }

    // Observed on AIR by PlusEMU (#344) on a furni with directions 0 and 2.
    @Test
    void aWriteTakesTheNearestDirectionWithinFortyFiveDegreesClockwiseFirst() {
        FurnitureDirections antenna = dirs(2, 0, 2);
        assertEquals(2, antenna.write(1));
        assertEquals(2, antenna.write(3));
        assertEquals(0, antenna.write(7));
        assertEquals(0, antenna.write(0));
        assertEquals(2, antenna.write(2));
        assertNull(antenna.write(4));
        assertNull(antenna.write(6));
        assertNull(antenna.write(8));
        assertNull(antenna.write(-1));

        FurnitureDirections sides = dirs(2, 2, 6);
        assertEquals(2, sides.write(3));
        assertEquals(6, sides.write(5));
        assertEquals(6, sides.write(7));
        assertEquals(2, sides.write(1));
        assertNull(sides.write(0));
        assertNull(sides.write(4));
    }

    @Test
    void theObservedAssignSequenceOnlyStoresTheZero() {
        FurnitureDirections antenna = dirs(2, 0, 2);
        int rotation = 2;
        List<Integer> stored = new ArrayList<>();
        for (int value : new int[] {4, 0, 6, 4}) {
            Integer written = antenna.write(value);
            if (written != null && written != rotation) {
                rotation = written;
                stored.add(written);
            }
        }
        assertEquals(List.of(0), stored);
    }

    @Test
    void aSnapshotMatchKeepsTheRotationWhenTheWriteIsRefused() {
        FurnitureDirections antenna = dirs(2, 0, 2);
        assertEquals(2, antenna.match(0, 1));
        assertEquals(0, antenna.match(0, 4));
        assertEquals(2, antenna.match(2, 4));
        assertEquals(2, antenna.match(2, 6));
        assertEquals(0, antenna.match(2, 0));
        // Without directions the snapshot's rotation is used as before.
        assertEquals(5, FurnitureDirections.UNKNOWN.match(0, 5));
    }

    @Test
    void aUserMoveToAMissingDirectionStoresTheDefaultDirection() {
        assertEquals(2, dirs(2, 0, 2).place(4));
        assertEquals(0, dirs(2, 0, 2).place(0));
        assertEquals(2, dirs(2, 0, 2).place(2));
        // A defaultdir the furni lacks (or none): its first direction, where the client starts the move.
        assertEquals(2, dirs(4, 2, 6).place(3));
        assertEquals(2, dirs(-1, 2, 6).place(0));
        // Without directions the request stands.
        assertEquals(5, FurnitureDirections.UNKNOWN.place(5));
    }

    @Test
    void aProjectileLandsOnItsHeadingOrItsDefaultDirection() {
        FurnitureDirections antenna = dirs(2, 0, 2);
        assertEquals(0, antenna.heading(0));
        assertEquals(2, antenna.heading(2));
        for (int heading : new int[] {1, 3, 4, 5, 6, 7}) {
            assertEquals(2, antenna.heading(heading), "heading " + heading);
        }
        assertEquals(2, dirs(2, 2, 6).heading(0));
        assertEquals(2, dirs(2, 2, 6).heading(4));
        assertEquals(6, dirs(2, 2, 6).heading(-2));
        // No usable defaultdir: the furni keeps its rotation.
        assertNull(dirs(-1, 0, 2).heading(4));
        assertNull(dirs(4, 0, 2).heading(6));
    }

    @Test
    void valuesAreACopy() {
        FurnitureDirections bed = dirs(2, 2, 0);
        int[] values = bed.values();
        values[0] = 7;
        assertArrayEquals(new int[] {0, 2}, bed.values());
        assertTrue(bed.has(0));
        assertEquals(2, bed.count());
        assertEquals(2, bed.defaultDirection());
    }

    private static List<Integer> walk(FurnitureDirections directions, int start, boolean clockwise, int steps) {
        List<Integer> out = new ArrayList<>();
        int rotation = start;
        for (int i = 0; i < steps; i++) {
            rotation = directions.turn(rotation, clockwise);
            out.add(rotation);
        }
        return out;
    }
}
