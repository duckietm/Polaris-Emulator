package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryService;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.habbohotel.messenger.history.MessengerStoredMessage;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.incoming.friends.SendMessengerMessageEvent;
import com.eu.habbo.messages.incoming.habbicons.HabbiconRequestEvent;
import com.eu.habbo.messages.incoming.habbicons.ResetHabbiconUnseenEvent;
import com.eu.habbo.messages.incoming.rooms.users.RoomUserHabbiconEvent;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageAckComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageFailedComposer;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HabbiconRequestTest {
    @Test
    void eachMutationHeaderDispatchesItsTypedActionAndReturnsACorrelatedResult() throws Exception {
        HabbiconService service = mock(HabbiconService.class);
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabbiconService()).thenReturn(service);
        for (HabbiconService.Action action : HabbiconService.Action.values()) {
            var buffer = Unpooled.buffer().writeInt(61);
            try {
                HabbiconRequestEvent event = new HabbiconRequestEvent();
                event.client = client;
                assertEquals(
                        0, event.getRatelimit(), "Header-scoped network limits must allow the following shop refresh");
                event.packet = new ClientMessage(9462 + action.ordinal(), buffer);
                event.handle();
                verify(service).change(habbo, action, 61);
            } finally {
                buffer.release();
            }
        }
        var responses = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client, org.mockito.Mockito.times(5)).sendResponse(responses.capture());
        for (int index = 0; index < responses.getAllValues().size(); index++) {
            var packet = responses.getAllValues().get(index).compose().get();
            try {
                packet.skipBytes(6);
                assertEquals(index, packet.readInt());
                assertEquals(61, packet.readInt());
                assertEquals(0, packet.readInt());
            } finally {
                packet.release();
            }
        }
    }

    @Test
    void shopReadsNoPayloadAndUnseenResetDecodesOnlyCompleteCategoryEightLists() throws Exception {
        HabbiconService service = mock(HabbiconService.class);
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(1);
        when(service.load(1)).thenReturn(new HabbiconService.Snapshot(List.of(), Map.of(), List.of(), List.of()));
        when(habbo.getHabbiconService()).thenReturn(service);
        HabbiconRequestEvent shop = new HabbiconRequestEvent();
        shop.client = client;
        shop.packet = new ClientMessage(9460, Unpooled.EMPTY_BUFFER);
        shop.handle();
        verify(service).load(1);
        var buffer = Unpooled.buffer().writeInt(8).writeInt(2).writeInt(61).writeInt(62);
        try {
            ResetHabbiconUnseenEvent reset = new ResetHabbiconUnseenEvent();
            reset.client = client;
            reset.packet = new ClientMessage(2343, buffer);
            reset.handle();
            verify(service).clearUnseen(1, List.of(61, 62));
            buffer.clear().writeInt(8).writeInt(2).writeInt(71);
            reset.handle();
            verify(service, never()).clearUnseen(1, List.of(71));
            buffer.clear().writeInt(8);
            reset.packet = new ClientMessage(3493, buffer);
            reset.handle();
            verify(service).clearUnseen(1, List.of());
        } finally {
            buffer.release();
        }
    }

    @Test
    void failedRecentUpdateCannotTurnACommittedMessengerMessageIntoAFailure() throws Exception {
        HabbiconService service = mock(HabbiconService.class);
        MessengerHistoryService history = mock(MessengerHistoryService.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        HabboManager manager = mock(HabboManager.class);
        GameClient sender = mock(GameClient.class), recipient = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class), receiver = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        HabboStats stats = mock(HabboStats.class);
        when(sender.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(info.getId()).thenReturn(1);
        when(stats.allowTalk()).thenReturn(true);
        when(environment.getHabboManager()).thenReturn(manager);
        when(manager.getHabbo(2)).thenReturn(receiver);
        when(receiver.getClient()).thenReturn(recipient);
        var item = new HabbiconService.Item(61, "toast", 6, 2, 0, 0, 0);
        when(service.load(1))
                .thenReturn(new HabbiconService.Snapshot(List.of(), Map.of(61, item), List.of(), List.of()));
        when(service.use(1, 61)).thenThrow(new java.sql.SQLException("recents unavailable"));
        var stored = new MessengerStoredMessage(10, 9, 1, 4, "61", "", 100);
        when(history.sendMessage(9, 1, 0, 4, "61", "")).thenReturn(stored);
        when(history.listActiveMemberIds(9, 1)).thenReturn(List.of(1, 2));
        try (var histories = mockStatic(MessengerHistoryServices.class);
                var emulator = mockStatic(Emulator.class)) {
            when(habbo.getHabbiconService()).thenReturn(service);
            histories.when(MessengerHistoryServices::create).thenReturn(history);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            ServerMessage message = new ServerMessage();
            message.init(4902);
            message.appendInt(9);
            message.appendInt(0);
            message.appendInt(123);
            message.appendInt(4);
            message.appendString("61");
            message.appendString("");
            var buffer = message.get();
            try {
                buffer.skipBytes(6);
                SendMessengerMessageEvent event = new SendMessengerMessageEvent();
                event.client = sender;
                event.packet = new ClientMessage(4902, buffer);
                event.handle();
                verify(sender).sendResponse(any(MessengerMessageAckComposer.class));
                verify(recipient).sendResponse(any(MessengerMessageComposer.class));
                verify(sender, never()).sendResponse(any(MessengerMessageFailedComposer.class));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void roomUseRejectsUnownedAndMutedIconsBeforeBroadcasting() throws Exception {
        HabbiconService service = mock(HabbiconService.class);
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        HabboStats stats = mock(HabboStats.class);
        Room room = mock(Room.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(info.getId()).thenReturn(1);
        when(info.getCurrentRoom()).thenReturn(room);
        when(stats.allowTalk()).thenReturn(true);
        when(habbo.getHabbiconService()).thenReturn(service);
        var buffer = Unpooled.buffer().writeInt(61);
        try {
            RoomUserHabbiconEvent event = new RoomUserHabbiconEvent();
            event.client = client;
            event.packet = new ClientMessage(9410, buffer);
            event.handle();
            verify(service).use(1, 61);
            verify(room, never()).sendComposer(any(ServerMessage.class));
            buffer.readerIndex(0);
            when(stats.allowTalk()).thenReturn(false);
            event.handle();
            verify(service).use(1, 61);
            verify(room, never()).sendComposer(any(ServerMessage.class));
            buffer.readerIndex(0);
            when(stats.allowTalk()).thenReturn(true);
            when(room.isMuted(habbo)).thenReturn(true);
            event.handle();
            verify(service).use(1, 61);
            verify(room, never()).sendComposer(any(ServerMessage.class));
            when(room.isMuted(habbo)).thenReturn(false);
            buffer.readerIndex(0);
            when(service.use(1, 61)).thenReturn(true);
            var item = new HabbiconService.Item(61, "toast", 6, 2, 0, 0, 0);
            when(service.load(1))
                    .thenReturn(new HabbiconService.Snapshot(List.of(), Map.of(61, item), List.of(61), List.of()));
            event.handle();
            verify(room).sendComposer(any(ServerMessage.class));
            verify(client).sendResponse(any(UserHabbiconsComposer.class));
        } finally {
            buffer.release();
        }
    }
}
