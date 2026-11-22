package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;

/** An antenna sends its signal when someone with rights uses it, and only then. */
class InteractionAntennaTest {
    private final Room room = mock(Room.class);
    private final User user = mock(User.class);
    private final GameClient client = mock(GameClient.class);

    private void inRoom(boolean rights) {
        when(this.client.getUser()).thenReturn(this.user);
        when(this.user.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(this.room.hasRights(this.user)).thenReturn(rights);
    }

    @Test
    void aUseWithRightsSendsTheSignal() {
        this.inRoom(true);

        assertTrue(InteractionAntenna.sendsSignal(this.client, this.room, new Object[] {0}));
    }

    @Test
    void aUseWithoutRightsSendsNothing() {
        this.inRoom(false);

        assertFalse(InteractionAntenna.sendsSignal(this.client, this.room, new Object[] {0}));
    }

    @Test
    void aWiredToggleSendsNothing() {
        this.inRoom(true);

        assertFalse(InteractionAntenna.sendsSignal(null, this.room, new Object[] {0, WiredEffectType.TOGGLE_STATE}));
        assertFalse(
                InteractionAntenna.sendsSignal(this.client, this.room, new Object[] {0, WiredEffectType.TOGGLE_STATE}));
    }
}
