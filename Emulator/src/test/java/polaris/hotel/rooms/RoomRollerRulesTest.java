package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.FurnitureUsagePolicy;
import polaris.hotel.items.interactions.InteractionRoller;

/**
 * Roller rules as in Habbo: a roller never lifts what rides it, furni a user just placed or moved (a roller
 * included) rests one room tick, and a roller is not a furni anybody uses.
 */
class RoomRollerRulesTest {
    @Test
    void aRollerNeverLiftsItsRider() {
        // Down or level: rolled.
        assertFalse(RoomRollerManager.wouldLift(0.45, 0.0, false));
        assertFalse(RoomRollerManager.wouldLift(0.45, 0.45, false));
        // A higher standing height in front stops the roll...
        assertTrue(RoomRollerManager.wouldLift(0.45, 1.0, false));
        // ...unless that is the next roller, whose top the rider lands on.
        assertFalse(RoomRollerManager.wouldLift(0.45, 1.0, true));
    }

    @Test
    void furniAUserJustMovedRestsOneTick() {
        ConcurrentHashMap<Integer, Long> moved = new ConcurrentHashMap<>();
        moved.put(5, 10_000L);
        moved.put(6, 9_000L);

        // Within the tick the furni rests; a mark that has run out is dropped.
        assertEquals(Set.of(5), RoomRollerManager.restingAt(moved, 10_200L));
        assertEquals(Set.of(5), moved.keySet());

        assertEquals(Set.of(), RoomRollerManager.restingAt(moved, 10_000L + RoomRollerManager.USER_MOVE_REST_MS));
        assertTrue(moved.isEmpty());
    }

    @Test
    void nobodyUsesARoller() {
        assertEquals(FurnitureUsagePolicy.NOBODY, FurnitureUsagePolicy.of(mock(InteractionRoller.class)));
    }

    @Test
    void theRulesAreWiredIntoTheCycleAndTheUserMoves() throws Exception {
        String manager = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomRollerManager.java"));
        // A resting roller carries nothing, resting furni is left on its roller, and the lift check spares the
        // "no rules" mode.
        assertTrue(manager.contains("if (resting.contains(roller.getId())) {"));
        assertTrue(manager.contains("itemsOnRoller.removeIf(item -> resting.contains(item.getId()));"));
        assertTrue(manager.contains("if (!InteractionRoller.NO_RULES\n"));

        for (String handler : new String[] {
            "rooms/items/RotateMoveItemEvent.java",
            "rooms/items/UpdateFurniturePositionEvent.java",
            "rooms/items/RoomPlaceItemEvent.java",
            "catalog/BuildersClubPlaceRoomItemEvent.java"
        }) {
            String source = Files.readString(Path.of("src/main/java/polaris/messages/incoming/" + handler));
            assertTrue(source.contains("room.getRollerManager().noteMovedByUser(item);"), handler);
        }

        String roller =
                Files.readString(Path.of("src/main/java/polaris/hotel/items/interactions/InteractionRoller.java"));
        assertFalse(roller.contains("super.onClick(client, room, objects);"));
    }
}
