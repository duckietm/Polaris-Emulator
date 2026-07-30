package com.eu.habbo.habbohotel.games.snowwar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarGameEvent;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItem;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarTile;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarPileObject;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnowWarAirParityTest {

    @Test
    void usesOfficialAirAvatarTimingConstants() {
        assertEquals(3, SnowWarConstants.SUBTURNS_PER_TICK);
        assertEquals(150, SnowWarConstants.SERVER_TICK_MS);
        assertEquals(50, SnowWarConstants.SUBTURN_MS);
        assertEquals(534, SnowWarConstants.SUBTURN_MOVEMENT);
        assertEquals(5, SnowWarConstants.INITIAL_HEALTH);
        assertEquals(5, SnowWarConstants.MAX_SNOWBALLS);
        assertEquals(20, SnowWarConstants.CREATING_TIMER);
        assertEquals(100, SnowWarConstants.STUNNED_TIMER);
        assertEquals(60, SnowWarConstants.INVINCIBILITY_TIMER);
    }

    @Test
    void usesOfficialAirMachineTimingAndEvents() {
        assertEquals(5, SnowWarConstants.MACHINE_MAX_SNOWBALL_CAPACITY);
        assertEquals(100, SnowWarConstants.MACHINE_SNOWBALL_GENERATOR_TIME);
        assertEquals(20, SnowWarConstants.SNOWBALL_SOURCE_PICKUP_TIME);
        assertEquals(11, SnowWarGameEvent.TYPE_MACHINE_ADD_SNOWBALL);
        assertEquals(12, SnowWarGameEvent.TYPE_MACHINE_TRANSFER_SNOWBALL);
        assertEquals(13, SnowWarGameEvent.TYPE_TREE_HIT);

        SnowWarAttributes attributes = new SnowWarAttributes();
        assertTrue(attributes.tickSnowballPickupTimer());
        attributes.resetSnowballPickupTimer();
        for (int frame = 1; frame < SnowWarConstants.SNOWBALL_SOURCE_PICKUP_TIME; frame++) {
            assertFalse(attributes.tickSnowballPickupTimer());
        }
        assertTrue(attributes.tickSnowballPickupTimer());
    }

    @Test
    void usesOfficialFiniteCardinalSnowballPiles() {
        SnowWarPileObject pile = new SnowWarPileObject(7, 4, 5);
        SnowWarAttributes attributes = new SnowWarAttributes();
        attributes.setCurrentPosition(new SnowWarPoint(4, 6));

        assertEquals(12, SnowWarConstants.PILE_MAX_SNOWBALL_CAPACITY);
        assertEquals(12, pile.getSnowballCount());
        assertTrue(pile.canPlayerPickup(attributes));

        pile.reservePickup();
        pile.transferReservedSnowballTo(attributes);
        assertEquals(11, pile.getSnowballCount());
        assertEquals(1, attributes.getSnowballCount().get());

        attributes.setCurrentPosition(new SnowWarPoint(5, 6));
        assertFalse(pile.canPlayerPickup(attributes));
    }

    @Test
    void usesOfficialAirStrictHitboxes() {
        assertEquals(400, SnowWarConstants.SNOWBALL_RADIUS);
        assertEquals(1600, SnowWarConstants.AVATAR_RADIUS);
        assertEquals(5000, SnowWarConstants.AVATAR_COLLISION_HEIGHT);
        assertEquals(2799, SnowWarConstants.TREE_RADIUS);
        assertEquals(3200, SnowWarConstants.TREE_COLLISION_HEIGHT);
        assertEquals(3, SnowWarConstants.TREE_MAX_HITS);

        assertTrue(SnowWarMath.circlesOverlap(0, 0, 400, 1999, 0, 1600));
        assertFalse(SnowWarMath.circlesOverlap(0, 0, 400, 2000, 0, 1600));
    }

    @Test
    void sumsSolidFurnitureHeightButLeavesTreesToTheirCircularHitbox() {
        SnowWarTile tile = new SnowWarTile(
                0,
                0,
                false,
                List.of(
                        new SnowWarItem("snst_block1", 0, 0, 0, 1, 2300),
                        new SnowWarItem("custom_wall", 0, 0, 0, 1, 1150),
                        new SnowWarItem("snst_tree1", 0, 0, 0, 1, 3200)));

        assertEquals(3450, tile.getSnowballCollisionHeight());
    }

    @Test
    void calculatesOfficialAirQuickThrow() {
        assertArrayEquals(
                new int[] {90, 10, 5, 2000, SnowWarConstants.TRAJECTORY_QUICK},
                SnowWarMath.calculateFlightPath(0, 0, 10, 0, SnowWarConstants.TRAJECTORY_QUICK));
    }

    @Test
    void calculatesOfficialAirShortAndLongLobs() {
        assertArrayEquals(
                new int[] {90, 17, 8, 1882, SnowWarConstants.TRAJECTORY_SHORT_LOB},
                SnowWarMath.calculateFlightPath(0, 0, 10, 0, SnowWarConstants.TRAJECTORY_SHORT_LOB));
        assertArrayEquals(
                new int[] {90, 22, 11, 1454, SnowWarConstants.TRAJECTORY_LONG_LOB},
                SnowWarMath.calculateFlightPath(0, 0, 10, 0, SnowWarConstants.TRAJECTORY_LONG_LOB));
    }

    @Test
    void resolvesAndCapsOfficialAirAdaptiveTrajectory() {
        assertEquals(
                SnowWarConstants.TRAJECTORY_QUICK,
                SnowWarMath.calculateFlightPath(0, 0, 10, 0, SnowWarConstants.TRAJECTORY_DEFAULT)[4]);
        assertEquals(
                SnowWarConstants.TRAJECTORY_SHORT_LOB,
                SnowWarMath.calculateFlightPath(0, 0, 15, 0, SnowWarConstants.TRAJECTORY_DEFAULT)[4]);
        assertEquals(
                SnowWarConstants.TRAJECTORY_LONG_LOB,
                SnowWarMath.calculateFlightPath(0, 0, 25, 0, SnowWarConstants.TRAJECTORY_DEFAULT)[4]);

        int[] cappedShort = SnowWarMath.calculateFlightPath(0, 0, 30, 0, SnowWarConstants.TRAJECTORY_SHORT_LOB);
        int[] cappedLong = SnowWarMath.calculateFlightPath(0, 0, 40, 0, SnowWarConstants.TRAJECTORY_LONG_LOB);
        assertArrayEquals(new int[] {90, 33, 16, 1818, SnowWarConstants.TRAJECTORY_SHORT_LOB}, cappedShort);
        assertArrayEquals(new int[] {90, 70, 35, 1428, SnowWarConstants.TRAJECTORY_LONG_LOB}, cappedLong);
    }

    @Test
    void preservesMidTileWorldCoordinatesForLiveThrows() {
        assertEquals(
                SnowWarConstants.TRAJECTORY_QUICK,
                SnowWarMath.calculateFlightPathWorld(500, 700, 42_500, 700, SnowWarConstants.TRAJECTORY_DEFAULT)[4]);
        assertEquals(
                SnowWarConstants.TRAJECTORY_SHORT_LOB,
                SnowWarMath.calculateFlightPathWorld(500, 700, 48_500, 700, SnowWarConstants.TRAJECTORY_DEFAULT)[4]);
    }
}
