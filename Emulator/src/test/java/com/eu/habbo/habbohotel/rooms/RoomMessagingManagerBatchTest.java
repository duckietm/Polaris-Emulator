package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.ServerMessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomMessagingManagerBatchTest {

    @Test
    void filtersNullPacketsAndPreservesOrderForEveryConnectedClient() {
        Room room = mock(Room.class);
        Habbo firstHabbo = mock(Habbo.class);
        Habbo secondHabbo = mock(Habbo.class);
        Habbo disconnected = mock(Habbo.class);
        GameClient firstClient = mock(GameClient.class);
        GameClient secondClient = mock(GameClient.class);
        when(firstHabbo.getClient()).thenReturn(firstClient);
        when(secondHabbo.getClient()).thenReturn(secondClient);
        when(disconnected.getClient()).thenReturn(null);
        when(room.getHabbos()).thenReturn(
                List.of(firstHabbo, disconnected, secondHabbo));
        ServerMessage first = new ServerMessage(100);
        ServerMessage second = new ServerMessage(101);
        RoomMessagingManager messaging =
                new RoomMessagingManager(room);

        messaging.sendComposers(Arrays.asList(
                first,
                null,
                second));

        assertEquals(
                List.of(first, second),
                capturedBatch(firstClient));
        assertEquals(
                List.of(first, second),
                capturedBatch(secondClient));
        assertTrue(
                ServerMessageFrame.isBroadcastPrepared(first));
        assertTrue(
                ServerMessageFrame.isBroadcastPrepared(second));
    }

    @Test
    void nullOrEmptyBatchDoesNotVisitRoomClients() {
        Room room = mock(Room.class);
        RoomMessagingManager messaging =
                new RoomMessagingManager(room);

        messaging.sendComposers(null);
        messaging.sendComposers(List.of());

        verify(room, never()).getHabbos();
    }

    @Test
    void reusesTheFilteredBatchAcrossRecipients() {
        Room room = mock(Room.class);
        Habbo firstHabbo = mock(Habbo.class);
        Habbo secondHabbo = mock(Habbo.class);
        GameClient firstClient = mock(GameClient.class);
        GameClient secondClient = mock(GameClient.class);
        when(firstHabbo.getClient()).thenReturn(firstClient);
        when(secondHabbo.getClient()).thenReturn(secondClient);
        when(room.getHabbos()).thenReturn(
                List.of(firstHabbo, secondHabbo));
        RoomMessagingManager messaging =
                new RoomMessagingManager(room);

        messaging.sendComposers(
                List.of(new ServerMessage(100)));

        assertSame(
                capturedBatch(firstClient),
                capturedBatch(secondClient));
    }

    private static ArrayList<ServerMessage> capturedBatch(
            GameClient client) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ArrayList<ServerMessage>> captor =
                ArgumentCaptor.forClass(ArrayList.class);
        verify(client).sendResponses(captor.capture());
        return captor.getValue();
    }
}
