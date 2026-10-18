package com.eu.habbo.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.incoming.users.GetIgnoredUsersEvent;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import com.eu.habbo.messages.outgoing.unknown.IgnoredUsersComposer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IgnoreByUserIdTest {
    private static final int SELF = 7;

    @Test
    void unignoringByIdWorksForAUserWhoIsNotInTheRoom() throws Exception {
        HabboStats stats = mock(HabboStats.class);
        GameClient client = client(stats, null);

        UnIgnoreRoomUserEvent handler = new UnIgnoreRoomUserEvent();
        run(handler, client, Unpooled.buffer().writeInt(42));

        verify(stats).unignoreUser(42);
        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client).sendResponse(sent.capture());
        ByteBuf packet = sent.getValue().compose().get();
        packet.skipBytes(6);
        assertEquals(RoomUserIgnoredComposer.UNIGNORED, packet.readInt());
        assertEquals(42, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void aUserMutedInTheRoomStaysIgnored() throws Exception {
        HabboStats stats = mock(HabboStats.class);
        Room room = mock(Room.class);
        Habbo muted = mock(Habbo.class);
        HabboStats mutedStats = mock(HabboStats.class);
        when(muted.getHabboStats()).thenReturn(mutedStats);
        when(mutedStats.allowTalk()).thenReturn(false);
        when(room.getHabbo(42)).thenReturn(muted);
        GameClient client = client(stats, room);

        run(new UnIgnoreRoomUserEvent(), client, Unpooled.buffer().writeInt(42));

        verify(stats, never()).unignoreUser(42);
    }

    @Test
    void theIgnoreListIsSentAsUserIds() throws Exception {
        HabboStats stats = mock(HabboStats.class);
        when(stats.getIgnoredUsers()).thenReturn(List.of(14, 99));
        GameClient client = client(stats, null);

        run(new GetIgnoredUsersEvent(), client, Unpooled.buffer());

        ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client).sendResponse(sent.capture());
        ByteBuf packet = assertInstanceOf(IgnoredUsersComposer.class, sent.getValue())
                .compose()
                .get();
        packet.skipBytes(6);
        assertEquals(2, packet.readInt());
        assertEquals(14, packet.readInt());
        assertEquals(99, packet.readInt());
        assertFalse(packet.isReadable());
    }

    private static GameClient client(HabboStats stats, Room room) {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(info.getId()).thenReturn(SELF);
        when(info.getCurrentRoom()).thenReturn(room);
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);
        return client;
    }

    private static void run(com.eu.habbo.messages.incoming.MessageHandler handler, GameClient client, ByteBuf buffer)
            throws Exception {
        handler.client = client;
        handler.packet = new ClientMessage(1, buffer);
        try {
            handler.handle();
        } finally {
            buffer.release();
        }
    }
}
