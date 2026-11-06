package polaris.messages.incoming.rooms.items;

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

/** Only the room owner and the owner of the placing box pick up a Place Furni copy. */
class RoomPickupTemporaryCopyTest {

    private static final int ROOM_OWNER = 10;
    private static final int BOX_OWNER = 20;
    private static final int RIGHTS_HOLDER = 30;

    private static Room pickUp(int userId) throws Exception {
        UserItem copy = mock(UserItem.class);
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
        buffer.writeInt(10);
        buffer.writeInt(5);

        RoomPickupItemEvent event = new RoomPickupItemEvent();
        event.client = client;
        event.packet = new ClientMessage(0, buffer);
        event.handle();

        verify(copy, never()).setUserId(anyInt());
        return room;
    }

    @Test
    void theRoomOwnerAndTheBoxOwnerRemoveTheCopy() throws Exception {
        verify(pickUp(ROOM_OWNER)).ejectUserItem(org.mockito.ArgumentMatchers.any());
        verify(pickUp(BOX_OWNER)).ejectUserItem(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aRightsHolderCannotPickItUp() throws Exception {
        verify(pickUp(RIGHTS_HOLDER), never()).ejectUserItem(org.mockito.ArgumentMatchers.any());
    }
}
