package polaris.messages.incoming.rooms.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.messages.ClientMessage;
import polaris.messages.incoming.users.GetIgnoredUsersEvent;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import polaris.messages.outgoing.users.IgnoredUsersComposer;

class IgnoreByUserIdTest {
    private static final int SELF = 7;

    @Test
    void unignoringByIdWorksForAUserWhoIsNotInTheRoom() throws Exception {
        UserStats stats = mock(UserStats.class);
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
    void aMutedUserCanStillBeUnignoredAsAMuteIsNotAnIgnore() throws Exception {
        UserStats stats = mock(UserStats.class);
        Room room = mock(Room.class);
        User muted = mock(User.class);
        UserStats mutedStats = mock(UserStats.class);
        when(muted.getUserStats()).thenReturn(mutedStats);
        when(mutedStats.allowTalk()).thenReturn(false);
        when(room.getUser(42)).thenReturn(muted);
        GameClient client = client(stats, room);

        run(new UnIgnoreRoomUserEvent(), client, Unpooled.buffer().writeInt(42));

        verify(stats).unignoreUser(42);
        verify(room, never()).unmuteUser(muted);
    }

    @Test
    void theIgnoreListIsSentAsUserIds() throws Exception {
        UserStats stats = mock(UserStats.class);
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

    private static GameClient client(UserStats stats, Room room) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getUserStats()).thenReturn(stats);
        when(info.getId()).thenReturn(SELF);
        when(info.getCurrentRoom()).thenReturn(room);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);
        return client;
    }

    private static void run(polaris.messages.incoming.MessageHandler handler, GameClient client, ByteBuf buffer)
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
