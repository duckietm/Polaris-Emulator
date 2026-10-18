package com.eu.habbo.messages.incoming.rooms.items;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/** Only the room owner and the owner of the placing box pick up a Place Furni copy. */
class RoomPickupTemporaryCopyTest {

    private static final int ROOM_OWNER = 10;
    private static final int BOX_OWNER = 20;
    private static final int RIGHTS_HOLDER = 30;

    private static Room pickUp(int habboId) throws Exception {
        HabboItem copy = mock(HabboItem.class);
        when(copy.isTemporary()).thenReturn(true);
        when(copy.getTemporaryOwnerId()).thenReturn(BOX_OWNER);

        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(habboId);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);

        Room room = mock(Room.class);
        when(room.getOwnerId()).thenReturn(ROOM_OWNER);
        when(room.getHabboItem(anyInt())).thenReturn(copy);
        when(room.hasRights(habbo)).thenReturn(true);
        when(info.getCurrentRoom()).thenReturn(room);

        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);

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
