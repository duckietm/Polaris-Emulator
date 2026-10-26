package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** @rotation counts in the furni's directions (observed on AIR WIN63-202610201032). */
class FurnitureDirectionsIndexTest {

    @Test
    void aRotationReadsAsItsPlaceAmongTheDirections() {
        int[] block = {0, 2, 4, 6};
        assertEquals(2, FurnitureDirections.indexOf(block, 4));
        int[] fridge = {2, 4};
        assertEquals(1, FurnitureDirections.indexOf(fridge, 4));
        // A rotation the furni lacks reads as the first direction at or after it, wrapping to 0.
        assertEquals(0, FurnitureDirections.indexOf(fridge, 1));
        assertEquals(0, FurnitureDirections.indexOf(fridge, 6));
        assertEquals(1, FurnitureDirections.indexOf(fridge, 3));
    }

    @Test
    void aFurniWithoutDirectionDataCountsInQuartersOrEighths() {
        assertArrayEquals(new int[] {0, 2, 4, 6}, FurnitureDirections.UNKNOWN.variableDirections(4));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7}, FurnitureDirections.UNKNOWN.variableDirections(8));
        assertArrayEquals(
                new int[] {2, 4}, FurnitureDirections.of(new int[] {2, 4}, 2).variableDirections(8));
    }
}
