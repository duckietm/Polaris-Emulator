package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.messenger.StaffChatBuddy;
import com.eu.habbo.habbohotel.messenger.history.MessengerHistoryServices;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageAckComposer;
import com.eu.habbo.messages.outgoing.friends.MessengerMessageFailedComposer;
import com.eu.habbo.messages.outgoing.unknown.UnknownMessengerErrorComposer;
import com.eu.habbo.plugin.PluginManager;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConsoleMessageRefusalTest {

    @Test
    void staffChatIsTheOnlyNonPositiveConsoleTarget() {
        assertTrue(FriendInputGuard.isValidConsoleTarget(5, false));
        assertFalse(FriendInputGuard.isValidConsoleTarget(StaffChatBuddy.BUDDY_ID, false));
        assertTrue(FriendInputGuard.isValidConsoleTarget(StaffChatBuddy.BUDDY_ID, true));
        assertFalse(FriendInputGuard.isValidConsoleTarget(0, true));
        assertFalse(FriendInputGuard.isValidConsoleTarget(-2, true));

        assertTrue(FriendInputGuard.isValidMessageTarget(0, StaffChatBuddy.BUDDY_ID, true));
        assertFalse(FriendInputGuard.isValidMessageTarget(0, StaffChatBuddy.BUDDY_ID, false));
        assertFalse(FriendInputGuard.isValidMessageTarget(0, -2, true));
        assertTrue(FriendInputGuard.isValidMessageTarget(3, 0, false));
    }

    @Test
    void mutedSenderGetsTheSenderMutedError() throws Exception {
        Fixture fixture = new Fixture(2, "hello");
        when(fixture.stats.allowTalk()).thenReturn(false);

        fixture.privateMessage();

        assertEquals(
                UnknownMessengerErrorComposer.SENDER_MUTED, fixture.refusal().getErrorCode());
        assertEquals(2, fixture.refusal().getUserId());
        assertEquals("hello", fixture.refusal().getMessage());
    }

    @Test
    void floodedConsoleGetsTheSendFailedError() throws Exception {
        Fixture fixture = new Fixture(2, "hello");
        when(fixture.stats.consoleMessageFlooded(anyLong())).thenReturn(true);

        fixture.privateMessage();

        assertEquals(
                UnknownMessengerErrorComposer.SEND_FAILED, fixture.refusal().getErrorCode());
    }

    @Test
    void messageToANonFriendGetsTheNotFriendError() throws Exception {
        Fixture fixture = new Fixture(2, "hello");

        fixture.privateMessage();

        assertEquals(UnknownMessengerErrorComposer.NOT_FRIEND, fixture.refusal().getErrorCode());
    }

    @Test
    void consoleFloodDoesNotUseTheRoomChatTimestamp() throws Exception {
        Fixture fixture = new Fixture(2, "hello");
        fixture.stats.lastChat = System.currentTimeMillis();
        MessengerBuddy buddy = mock(MessengerBuddy.class);
        when(fixture.messenger.getFriend(2)).thenReturn(buddy);

        try (var emulator = mockStatic(Emulator.class);
                var quests = mockStatic(com.eu.habbo.habbohotel.quests.QuestProgressEvents.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(fixture.plugins);
            fixture.privateMessage();
        }

        verify(buddy).onMessageReceivedWithDeliveryStatus(fixture.habbo, "hello");
        verify(fixture.client, never()).sendResponse(any(UnknownMessengerErrorComposer.class));
    }

    @Test
    void staffChatReachesTheStaffChatBuddyForStaff() throws Exception {
        Fixture fixture = new Fixture(StaffChatBuddy.BUDDY_ID, "hi staff");
        MessengerBuddy staffChat = mock(MessengerBuddy.class);
        when(fixture.habbo.hasPermission(StaffChatBuddy.PERMISSION_KEY)).thenReturn(true);
        when(fixture.messenger.getFriend(StaffChatBuddy.BUDDY_ID)).thenReturn(staffChat);

        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(fixture.plugins);
            fixture.privateMessage();
        }

        verify(staffChat).onMessageReceived(fixture.habbo, "hi staff");
    }

    @Test
    void staffChatIdIsIgnoredWithoutThePermission() throws Exception {
        Fixture fixture = new Fixture(StaffChatBuddy.BUDDY_ID, "hi staff");

        fixture.privateMessage();

        verify(fixture.messenger, never()).getFriend(StaffChatBuddy.BUDDY_ID);
        verify(fixture.client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void messengerPathAppliesTheConsoleFloodLimit() throws Exception {
        Fixture fixture = new Fixture(2, "hello");
        when(fixture.stats.consoleMessageFlooded(anyLong())).thenReturn(true);

        try (var histories = mockStatic(MessengerHistoryServices.class)) {
            fixture.messengerMessage(0, 2, 0, "hello");
            histories.verifyNoInteractions();
        }

        verify(fixture.client).sendResponse(any(MessengerMessageFailedComposer.class));
    }

    @Test
    void messengerPathSendsStaffChatWithoutStoringIt() throws Exception {
        Fixture fixture = new Fixture(StaffChatBuddy.BUDDY_ID, "hi staff");
        MessengerBuddy staffChat = mock(MessengerBuddy.class);
        when(fixture.habbo.hasPermission(StaffChatBuddy.PERMISSION_KEY)).thenReturn(true);
        when(fixture.messenger.getFriend(StaffChatBuddy.BUDDY_ID)).thenReturn(staffChat);

        try (var histories = mockStatic(MessengerHistoryServices.class)) {
            fixture.messengerMessage(0, StaffChatBuddy.BUDDY_ID, 0, "hi staff");
            histories.verifyNoInteractions();
        }

        verify(staffChat).onMessageReceived(fixture.habbo, "hi staff");
        verify(fixture.client).sendResponse(any(MessengerMessageAckComposer.class));
        verify(fixture.client, never()).sendResponse(any(MessengerMessageFailedComposer.class));
    }

    @Test
    void messengerPathRefusesHabbiconsInStaffChat() throws Exception {
        Fixture fixture = new Fixture(StaffChatBuddy.BUDDY_ID, "61");
        MessengerBuddy staffChat = mock(MessengerBuddy.class);
        when(fixture.habbo.hasPermission(StaffChatBuddy.PERMISSION_KEY)).thenReturn(true);
        when(fixture.messenger.getFriend(StaffChatBuddy.BUDDY_ID)).thenReturn(staffChat);

        fixture.messengerMessage(0, StaffChatBuddy.BUDDY_ID, 4, "61");

        verify(staffChat, never()).onMessageReceived(any(), anyString());
        verify(fixture.client).sendResponse(any(MessengerMessageFailedComposer.class));
    }

    private static final class Fixture {
        final GameClient client = mock(GameClient.class);
        final Habbo habbo = mock(Habbo.class);
        final HabboInfo info = mock(HabboInfo.class);
        final HabboStats stats = mock(HabboStats.class);
        final Messenger messenger = mock(Messenger.class);
        final PluginManager plugins = mock(PluginManager.class);
        final int targetId;
        final String message;

        Fixture(int targetId, String message) {
            this.targetId = targetId;
            this.message = message;
            when(client.getHabbo()).thenReturn(habbo);
            when(habbo.getHabboInfo()).thenReturn(info);
            when(habbo.getHabboStats()).thenReturn(stats);
            when(habbo.getMessenger()).thenReturn(messenger);
            when(info.getId()).thenReturn(1);
            when(stats.allowTalk()).thenReturn(true);
            doAnswer(invocation -> invocation.getArgument(0)).when(plugins).fireEvent(any());
        }

        void privateMessage() throws Exception {
            ServerMessage packet = new ServerMessage();
            packet.init(3567);
            packet.appendInt(this.targetId);
            packet.appendString(this.message);
            this.handle(new FriendPrivateMessageEvent(), 3567, packet);
        }

        void messengerMessage(int conversationId, int recipientId, int type, String text) throws Exception {
            ServerMessage packet = new ServerMessage();
            packet.init(4902);
            packet.appendInt(conversationId);
            packet.appendInt(recipientId);
            packet.appendInt(123);
            packet.appendInt(type);
            packet.appendString(text);
            packet.appendString("");
            this.handle(new SendMessengerMessageEvent(), 4902, packet);
        }

        private void handle(com.eu.habbo.messages.incoming.MessageHandler handler, int header, ServerMessage packet)
                throws Exception {
            ByteBuf buffer = packet.get();
            try {
                buffer.skipBytes(6);
                handler.client = this.client;
                handler.packet = new ClientMessage(header, buffer);
                handler.handle();
            } finally {
                buffer.release();
            }
        }

        UnknownMessengerErrorComposer refusal() {
            ArgumentCaptor<UnknownMessengerErrorComposer> captor =
                    ArgumentCaptor.forClass(UnknownMessengerErrorComposer.class);
            verify(this.client).sendResponse(captor.capture());
            return captor.getValue();
        }
    }
}
