package com.eu.habbo.threading.runnables.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The teleport steps held the GameClient, which dispose() empties when the session is suspended: the next step threw,
 * the chain stopped with isTeleporting set, and the user could not walk until they changed room.
 */
class TeleportChainTest {

    @Test
    void aStepThatThrowsGivesTheUserTheirWalkBackAndResetsTheDoor() {
        Habbo habbo = mock(Habbo.class, RETURNS_DEEP_STUBS);
        RoomUnit unit = mock(RoomUnit.class);
        unit.isTeleporting = true;
        when(habbo.getRoomUnit()).thenReturn(unit);
        HabboItem teleport = mock(HabboItem.class);
        Room room = mock(Room.class);

        TeleportChain.run(habbo, teleport, room, () -> {
            throw new NullPointerException("client gone");
        });

        assertFalse(unit.isTeleporting);
        verify(habbo.getHabboInfo()).setLoadingRoom(0);
        verify(teleport).setExtradata("0");
    }

    @Test
    void aStepKeepsTheUserEvenWhenTheirOldClientIsEmptied() throws Exception {
        Habbo habbo = mock(Habbo.class);
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);

        TeleportActionFour step = new TeleportActionFour(mock(HabboItem.class), mock(Room.class), client);
        when(client.getHabbo()).thenReturn(null);

        Field field = TeleportActionFour.class.getDeclaredField("habbo");
        field.setAccessible(true);
        assertSame(habbo, field.get(step));
    }

    @Test
    void noStepReadsTheClientAnyMore() throws Exception {
        for (String step : List.of("One", "Two", "Three", "Four", "Five")) {
            String source = Files.readString(
                    Path.of("src/main/java/com/eu/habbo/threading/runnables/teleport/TeleportAction" + step + ".java"));

            assertFalse(source.contains("this.client"), step);
            assertTrue(source.contains("TeleportChain.run(this.habbo"), step);
        }
    }
}
