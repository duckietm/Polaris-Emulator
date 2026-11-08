package polaris.messages.incoming.rooms.items;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.messages.ClientMessage;

/** The multi pick-up follows the single one for a Place Furni copy: it was ejected to whoever had rights. */
class RoomPickupChooserTemporaryCopyTest {

    private static final int ROOM_OWNER = 10;
    private static final int BOX_OWNER = 20;
    private static final int RIGHTS_HOLDER = 30;

    private static UserItem copy;

    private static Room pickUp(int userId) throws Exception {
        copy = mock(UserItem.class);
        when(copy.isTemporary()).thenReturn(true);
        when(copy.getTemporaryOwnerId()).thenReturn(BOX_OWNER);

        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(userId);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);

        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(ROOM_OWNER);
        when(room.getUserItem(anyInt())).thenReturn(copy);
        when(room.hasRights(user)).thenReturn(true);
        when(info.getCurrentRoom()).thenReturn(room);

        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);

        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(1);
        buffer.writeInt(5);

        RoomPickupChooserEvent event = new RoomPickupChooserEvent();
        event.client = client;
        event.packet = new ClientMessage(0, buffer);
        event.handle();

        verify(copy, never()).setUserId(anyInt());
        return room;
    }

    @Test
    void theRoomOwnerAndTheBoxOwnerRemoveTheCopy() throws Exception {
        verify(pickUp(ROOM_OWNER)).ejectUserItem(copy);
        verify(pickUp(BOX_OWNER)).ejectUserItem(copy);
    }

    @Test
    void anyoneElseWithRightsLeavesIt() throws Exception {
        Room room = pickUp(RIGHTS_HOLDER);
        verify(room, never()).ejectUserItem(any());
        verify(room, never()).pickUpItem(any(), any());
    }
}
