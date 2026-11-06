package polaris.hotel.items.interactions.wired.effects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.installHotel;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;

/**
 * "Walk to furni" has a teleport checkbox that was saved, persisted and echoed back to the dialog,
 * while execute walked the user over whatever it said.
 */
class WiredEffectWalkToFurniTest {

    @Test
    void theTeleportOptionTeleportsInsteadOfWalking() throws Exception {
        Room room = mock(Room.class);
        RoomTile tile = furniAt(room, 301, 3, 4);
        RoomUnit unit = mock(RoomUnit.class);
        WiredEffectWalkToFurni box = box(room, true);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredEffectTeleport> teleport = mockStatic(WiredEffectTeleport.class)) {
            installHotel(emulator, room);
            emulator.when(Emulator::getRandom).thenReturn(new Random(7));

            box.execute(context(room, unit));

            teleport.verify(() -> WiredEffectTeleport.teleportUnitToTile(unit, tile, true));
            verify(unit, never()).setGoalLocation(any());
        }
    }

    @Test
    void withoutTheOptionTheUserWalks() throws Exception {
        Room room = mock(Room.class);
        RoomTile tile = furniAt(room, 301, 3, 4);
        RoomUnit unit = mock(RoomUnit.class);
        WiredEffectWalkToFurni box = box(room, false);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<WiredEffectTeleport> teleport = mockStatic(WiredEffectTeleport.class)) {
            installHotel(emulator, room);
            emulator.when(Emulator::getRandom).thenReturn(new Random(7));

            box.execute(context(room, unit));

            verify(unit).setGoalLocation(tile);
            teleport.verifyNoInteractions();
        }
    }

    private static WiredEffectWalkToFurni box(Room room, boolean fastTeleport) throws Exception {
        WiredEffectWalkToFurni box = new WiredEffectWalkToFurni(1, 1, base(), "", 0, 0);
        box.loadWiredData(
                row("{\"delay\":0,\"itemIds\":[301],\"fastTeleport\":" + fastTeleport
                        + ",\"furniSource\":100,\"userSource\":0}"),
                room);
        return box;
    }

    private static RoomTile furniAt(Room room, int id, int x, int y) {
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getX()).thenReturn((short) x);
        when(item.getY()).thenReturn((short) y);
        when(room.getUserItem(id)).thenReturn(item);

        RoomLayout layout = mock(RoomLayout.class);
        RoomTile tile = mock(RoomTile.class);
        when(layout.getTile((short) x, (short) y)).thenReturn(tile);
        when(room.getLayout()).thenReturn(layout);
        return tile;
    }
}
