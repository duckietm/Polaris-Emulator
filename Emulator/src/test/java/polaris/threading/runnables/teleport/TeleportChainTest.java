package polaris.threading.runnables.teleport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

/**
 * The teleport steps held the GameClient, which dispose() empties when the session is suspended: the next step threw,
 * the chain stopped with isTeleporting set, and the user could not walk until they changed room.
 */
class TeleportChainTest {

    @Test
    void aStepThatThrowsGivesTheUserTheirWalkBackAndResetsTheDoor() {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        RoomUnit unit = mock(RoomUnit.class);
        unit.isTeleporting = true;
        when(user.getRoomUnit()).thenReturn(unit);
        UserItem teleport = mock(UserItem.class);
        Room room = mock(Room.class);

        TeleportChain.run(user, teleport, room, () -> {
            throw new NullPointerException("client gone");
        });

        assertFalse(unit.isTeleporting);
        verify(user.getUserInfo()).setLoadingRoom(0);
        verify(teleport).setExtradata("0");
    }

    @Test
    void aStepKeepsTheUserEvenWhenTheirOldClientIsEmptied() throws Exception {
        User user = mock(User.class);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);

        TeleportActionFour step = new TeleportActionFour(mock(UserItem.class), mock(Room.class), client);
        when(client.getUser()).thenReturn(null);

        Field field = TeleportActionFour.class.getDeclaredField("user");
        field.setAccessible(true);
        assertSame(user, field.get(step));
    }

    @Test
    void noStepReadsTheClientAnyMore() throws Exception {
        for (String step : List.of("One", "Two", "Three", "Four", "Five")) {
            String source = Files.readString(
                    Path.of("src/main/java/polaris/threading/runnables/teleport/TeleportAction" + step + ".java"));

            assertFalse(source.contains("this.client"), step);
            assertTrue(source.contains("TeleportChain.run(this.user"), step);
        }
    }
}
