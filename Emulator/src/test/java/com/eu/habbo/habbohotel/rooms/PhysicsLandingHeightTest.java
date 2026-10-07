package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhysicsLandingHeightTest {

    @Test
    void aChairMovedThroughIsStackedOntoNotLandedIn() {
        assertEquals(1.0, RoomItemMovementService.landingHeight(0.0, 1.0, true, true));
        assertEquals(0.0, RoomItemMovementService.landingHeight(0.0, 1.0, true, false));
        assertEquals(1.5, RoomItemMovementService.landingHeight(0.5, 1.0, false, false));
        assertEquals(1.5, RoomItemMovementService.landingHeight(0.5, 1.0, false, true));
    }

    @Test
    void moveThroughFurniStillCountsThePassedFurniForTheHeight() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemMovementService.java"));
        int helper = source.indexOf("private double getPhysicsStackHeight(");

        assertTrue(source.indexOf("this.getTopPhysicsItemAt(x, y, exclude, null)", helper) > helper);
        assertTrue(source.indexOf("physics.shouldIgnoreFurni(topItem)", helper) > helper);
    }

    @Test
    void furniToFurniNoLongerForcesTheTargetHeight() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects/WiredEffectFurniToFurni.java"));

        assertTrue(!source.contains("targetItem.getZ()"), "A refused move must not be retried at the target's height");
        assertTrue(source.contains(
                "moveFurni(room, this, moveItem, targetTile, moveItem.getRotation(), null, false, ctx)"));
    }
}
