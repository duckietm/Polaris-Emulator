package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryService;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.habbohotel.messenger.history.MessengerStoredMessage;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.friends.SendMessengerMessageEvent;
import com.eu.habbo.messages.incoming.habbicons.HabbiconRequestEvent;
import com.eu.habbo.messages.incoming.habbicons.TriggerHabbiconEvent;
import com.eu.habbo.messages.incoming.inventory.UnseenResetCategoryEvent;
import com.eu.habbo.messages.incoming.inventory.UnseenResetItemsEvent;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageAckComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageFailedComposer;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HabbiconRequestTest {
    @Test
    void eachMutationHeaderDispatchesItsTypedActionAndOnlyPurchasesAreAcknowledged() throws Exception {
        Map<Integer, HabbiconService.Action> headers = new LinkedHashMap<>();
        headers.put(Incoming.BuyHabbiconEvent, HabbiconService.Action.BUY);
        headers.put(Incoming.BuyHabbiconCollectionEvent, HabbiconService.Action.BUY_COLLECTION);
        headers.put(Incoming.ClaimHabbiconEvent, HabbiconService.Action.CLAIM);
        headers.put(Incoming.FavoriteHabbiconEvent, HabbiconService.Action.FAVORITE);
        headers.put(Incoming.UnfavoriteHabbiconEvent, HabbiconService.Action.UNFAVORITE);
        HabbiconService service = mock(HabbiconService.class);
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(1);
        when(habbo.getHabbiconService()).thenReturn(service);
        for (Map.Entry<Integer, HabbiconService.Action> entry : headers.entrySet()) {
            var buffer = Unpooled.buffer().writeInt(61);
            try {
                HabbiconRequestEvent event = new HabbiconRequestEvent();
                event.client = client;
                assertEquals(
                        0, event.getRatelimit(), "Header-scoped network limits must allow the following shop refresh");
                event.packet = new ClientMessage(entry.getKey(), buffer);
                event.handle();
                verify(service).change(habbo, entry.getValue(), 61);
            } finally {
                buffer.release();
            }
        }
        verify(client, org.mockito.Mockito.times(3)).sendResponse(any(PurchaseOKComposer.class));
        verify(client, never()).sendResponse(any(AlertPurchaseFailedComposer.class));
    }

    @Test
    void aRejectedPurchaseFailsTheCatalogPurchaseAndARejectedFavoriteStaysSilent() throws Exception {
        HabbiconService service = mock(HabbiconService.class);
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getId()).thenReturn(1);
        when(habbo.getHabbiconService()).thenReturn(service);
        when(service.change(any(Habbo.class), any(HabbiconService.Action.class), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new HabbiconService.Rejected(2));
        for (int header : List.of(Incoming.BuyHabbiconEvent, Incoming.FavoriteHabbiconEvent)) {
            var buffer = Unpooled.buffer().writeInt(61);
            try {
                HabbiconRequestEvent event = new HabbiconRequestEvent();
                event.client = client;
                event.packet = new ClientMessage(header, buffer);
                event.handle();
            } finally {
                buffer.release();
            }
        }
        var failures = ArgumentCaptor.forClass(AlertPurchaseFailedComposer.class);
        verify(client).sendResponse(failures.capture());
        assertEquals(2, failures.getValue().getError());
        verify(client, never()).sendResponse(any(PurchaseOKComposer.class));
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
            UnseenResetItemsEvent resetItems = new UnseenResetItemsEvent();
            resetItems.client = client;
            resetItems.packet = new ClientMessage(2343, buffer);
            resetItems.handle();
            verify(service).clearUnseen(1, List.of(61, 62));
            buffer.clear().writeInt(8).writeInt(2).writeInt(71);
            resetItems.packet = new ClientMessage(2343, buffer);
            resetItems.handle();
            verify(service, never()).clearUnseen(1, List.of(71));
            buffer.clear().writeInt(8);
            UnseenResetCategoryEvent resetCategory = new UnseenResetCategoryEvent();
            resetCategory.client = client;
            resetCategory.packet = new ClientMessage(3493, buffer);
            resetCategory.handle();
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
        // A stored conversation may only be written to while the other member is still a friend.
        Messenger messenger = new Messenger();
        messenger.addBuddy(new MessengerBuddy(2, "receiver", "", (short) 0, 1));
        when(habbo.getMessenger()).thenReturn(messenger);
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
    void storedConversationWithAFormerFriendIsRefused() throws Exception {
        MessengerHistoryService history = mock(MessengerHistoryService.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        GameClient sender = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        HabboStats stats = mock(HabboStats.class);
        when(sender.getHabbo()).thenReturn(habbo);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getMessenger()).thenReturn(new Messenger());
        when(info.getId()).thenReturn(1);
        when(stats.allowTalk()).thenReturn(true);
        when(history.listActiveMemberIds(9, 1)).thenReturn(List.of(1, 2));
        try (var histories = mockStatic(MessengerHistoryServices.class);
                var emulator = mockStatic(Emulator.class)) {
            histories.when(MessengerHistoryServices::create).thenReturn(history);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            ServerMessage message = new ServerMessage();
            message.init(4902);
            message.appendInt(9);
            message.appendInt(0);
            message.appendInt(124);
            message.appendInt(0);
            message.appendString("hi");
            message.appendString("");
            var buffer = message.get();
            try {
                buffer.skipBytes(6);
                SendMessengerMessageEvent event = new SendMessengerMessageEvent();
                event.client = sender;
                event.packet = new ClientMessage(4902, buffer);
                event.handle();
                verify(sender).sendResponse(any(MessengerMessageFailedComposer.class));
                verify(history, never()).sendMessage(anyLong(), anyInt(), anyInt(), anyInt(), any(), any());
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
            TriggerHabbiconEvent event = new TriggerHabbiconEvent();
            event.client = client;
            event.packet = new ClientMessage(9417, buffer);
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
