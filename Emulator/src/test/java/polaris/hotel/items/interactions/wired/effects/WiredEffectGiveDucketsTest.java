package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredState;

class WiredEffectGiveDucketsTest {

    @Test
    void usesThePixelGrantPath() {
        Room room = mock(Room.class);
        RoomUnit actor = mock(RoomUnit.class);
        User user = mock(User.class);
        when(room.getUser(actor)).thenReturn(user);

        WiredEffectGiveDuckets effect = new WiredEffectGiveDuckets(42, 7, mock(Item.class), "0", 0, 0);
        WiredSettings settings = new WiredSettings(new int[] {WiredSourceUtil.SOURCE_TRIGGER}, "25", new int[0], 0);
        WiredContext context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build(),
                null,
                mock(WiredServices.class),
                new WiredState(100));

        // Configuring a box that grants value needs the permission; this test is about the grant path.
        GameClient author = mock(GameClient.class);
        User authorUser = mock(User.class);
        when(author.getUser()).thenReturn(authorUser);
        when(authorUser.hasPermission(Permission.ACC_SUPERWIRED)).thenReturn(true);

        assertTrue(effect.saveData(settings, author));
        effect.execute(context);

        verify(user).givePixels(25);
        verify(user, never()).givePoints(0, 25);
    }
}
