package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.ServerMessageFrame;

class RoomMessagingManagerBatchTest {

    @Test
    void filtersNullPacketsAndPreservesOrderForEveryConnectedClient() {
        Room room = mock(Room.class);
        User firstUser = mock(User.class);
        User secondUser = mock(User.class);
        User disconnected = mock(User.class);
        GameClient firstClient = mock(GameClient.class);
        GameClient secondClient = mock(GameClient.class);
        when(firstUser.getClient()).thenReturn(firstClient);
        when(secondUser.getClient()).thenReturn(secondClient);
        when(disconnected.getClient()).thenReturn(null);
        when(room.getUsers()).thenReturn(List.of(firstUser, disconnected, secondUser));
        ServerMessage first = new ServerMessage(100);
        ServerMessage second = new ServerMessage(101);
        RoomMessagingManager messaging = new RoomMessagingManager(room);

        messaging.sendComposers(Arrays.asList(first, null, second));

        assertEquals(List.of(first, second), capturedBatch(firstClient));
        assertEquals(List.of(first, second), capturedBatch(secondClient));
        assertTrue(ServerMessageFrame.isBroadcastPrepared(first));
        assertTrue(ServerMessageFrame.isBroadcastPrepared(second));
    }

    @Test
    void nullOrEmptyBatchDoesNotVisitRoomClients() {
        Room room = mock(Room.class);
        RoomMessagingManager messaging = new RoomMessagingManager(room);

        messaging.sendComposers(null);
        messaging.sendComposers(List.of());

        verify(room, never()).getUsers();
    }

    @Test
    void reusesTheFilteredBatchAcrossRecipients() {
        Room room = mock(Room.class);
        User firstUser = mock(User.class);
        User secondUser = mock(User.class);
        GameClient firstClient = mock(GameClient.class);
        GameClient secondClient = mock(GameClient.class);
        when(firstUser.getClient()).thenReturn(firstClient);
        when(secondUser.getClient()).thenReturn(secondClient);
        when(room.getUsers()).thenReturn(List.of(firstUser, secondUser));
        RoomMessagingManager messaging = new RoomMessagingManager(room);

        messaging.sendComposers(List.of(new ServerMessage(100)));

        assertSame(capturedBatch(firstClient), capturedBatch(secondClient));
    }

    private static ArrayList<ServerMessage> capturedBatch(GameClient client) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ArrayList<ServerMessage>> captor = ArgumentCaptor.forClass(ArrayList.class);
        verify(client).sendResponses(captor.capture());
        return captor.getValue();
    }
}
