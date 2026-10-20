package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFurniHeightLimit;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** AIR's Set Furni Altitude slider runs to 80.00 (8000 hundredths), above the room's build ceiling. */
class WiredEffectSetAltitudeTest {
    private static WiredEffectSetAltitude box(String altitude) throws Exception {
        WiredEffectSetAltitude box = new WiredEffectSetAltitude(11, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"delay\":0,\"itemIds\":[],\"operator\":2,\"altitude\":\"" + altitude + "\",\"furniSource\":0}"),
                null);
        return box;
    }

    @Test
    void anAltitudeUpToEightyIsKept() throws Exception {
        assertEquals(8000, WiredEffectProgressAchievementTest.body(box("80")).params()[0]);
        assertEquals(6525, WiredEffectProgressAchievementTest.body(box("65.25")).params()[0]);
        assertEquals(2, WiredEffectProgressAchievementTest.body(box("65.25")).params()[1]);
    }

    @Test
    void anAltitudeAboveEightyIsClampedToIt() throws Exception {
        assertEquals(8000, WiredEffectProgressAchievementTest.body(box("99.5")).params()[0]);
    }

    @Test
    void theWiredCeilingOnlyHoldsInsideItsScope() {
        assertEquals(Room.MAXIMUM_FURNI_HEIGHT, RoomFurniHeightLimit.current());
        try (RoomFurniHeightLimit.Scope outer = RoomFurniHeightLimit.beginWiredAltitude()) {
            assertEquals(RoomFurniHeightLimit.WIRED_ALTITUDE_MAX, RoomFurniHeightLimit.current());
            try (RoomFurniHeightLimit.Scope inner = RoomFurniHeightLimit.beginWiredAltitude()) {
                assertEquals(RoomFurniHeightLimit.WIRED_ALTITUDE_MAX, RoomFurniHeightLimit.current());
            }
            assertEquals(RoomFurniHeightLimit.WIRED_ALTITUDE_MAX, RoomFurniHeightLimit.current(), "nested scopes");
        }
        assertEquals(Room.MAXIMUM_FURNI_HEIGHT, RoomFurniHeightLimit.current());
    }

    @Test
    void movesWithAnAltitudeAskTheLimitAndTheBoxOpensItsScope() throws Exception {
        String movement = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemMovementService.java"));
        String altitude = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects/WiredEffectSetAltitude.java"));

        assertEquals(2, movement.split("if \\(z > RoomFurniHeightLimit.current\\(\\)\\)", -1).length - 1);
        assertTrue(altitude.contains("RoomFurniHeightLimit.beginWiredAltitude()"));
    }
}
