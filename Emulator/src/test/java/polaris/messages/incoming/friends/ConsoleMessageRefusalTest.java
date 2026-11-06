package polaris.messages.incoming.friends;

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

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.messenger.StaffChatBuddy;
import polaris.hotel.messenger.history.MessengerHistoryServices;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.messages.ClientMessage;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.friends.InstantMessageErrorComposer;
import polaris.messages.outgoing.friends.MessengerMessageAckComposer;
import polaris.messages.outgoing.friends.MessengerMessageFailedComposer;
import polaris.plugin.PluginManager;

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

        assertEquals(InstantMessageErrorComposer.SENDER_MUTED, fixture.refusal().getErrorCode());
        assertEquals(2, fixture.refusal().getUserId());
        assertEquals("hello", fixture.refusal().getMessage());
    }

    @Test
    void floodedConsoleGetsTheSendFailedError() throws Exception {
        Fixture fixture = new Fixture(2, "hello");
        when(fixture.stats.consoleMessageFlooded(anyLong())).thenReturn(true);

        fixture.privateMessage();

        assertEquals(InstantMessageErrorComposer.SEND_FAILED, fixture.refusal().getErrorCode());
    }

    @Test
    void messageToANonFriendGetsTheNotFriendError() throws Exception {
        Fixture fixture = new Fixture(2, "hello");

        fixture.privateMessage();

        assertEquals(InstantMessageErrorComposer.NOT_FRIEND, fixture.refusal().getErrorCode());
    }

    @Test
    void consoleFloodDoesNotUseTheRoomChatTimestamp() throws Exception {
        Fixture fixture = new Fixture(2, "hello");
        fixture.stats.lastChat = System.currentTimeMillis();
        MessengerBuddy buddy = mock(MessengerBuddy.class);
        when(fixture.messenger.getFriend(2)).thenReturn(buddy);

        try (var emulator = mockStatic(Emulator.class);
                var quests = mockStatic(polaris.hotel.quests.QuestProgressEvents.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(fixture.plugins);
            fixture.privateMessage();
        }

        verify(buddy).onMessageReceivedWithDeliveryStatus(fixture.user, "hello");
        verify(fixture.client, never()).sendResponse(any(InstantMessageErrorComposer.class));
    }

    @Test
    void staffChatReachesTheStaffChatBuddyForStaff() throws Exception {
        Fixture fixture = new Fixture(StaffChatBuddy.BUDDY_ID, "hi staff");
        MessengerBuddy staffChat = mock(MessengerBuddy.class);
        when(fixture.user.hasPermission(StaffChatBuddy.PERMISSION_KEY)).thenReturn(true);
        when(fixture.messenger.getFriend(StaffChatBuddy.BUDDY_ID)).thenReturn(staffChat);

        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(fixture.plugins);
            fixture.privateMessage();
        }

        verify(staffChat).onMessageReceived(fixture.user, "hi staff");
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
        when(fixture.user.hasPermission(StaffChatBuddy.PERMISSION_KEY)).thenReturn(true);
        when(fixture.messenger.getFriend(StaffChatBuddy.BUDDY_ID)).thenReturn(staffChat);

        try (var histories = mockStatic(MessengerHistoryServices.class)) {
            fixture.messengerMessage(0, StaffChatBuddy.BUDDY_ID, 0, "hi staff");
            histories.verifyNoInteractions();
        }

        verify(staffChat).onMessageReceived(fixture.user, "hi staff");
        verify(fixture.client).sendResponse(any(MessengerMessageAckComposer.class));
        verify(fixture.client, never()).sendResponse(any(MessengerMessageFailedComposer.class));
    }

    @Test
    void messengerPathRefusesHabbiconsInStaffChat() throws Exception {
        Fixture fixture = new Fixture(StaffChatBuddy.BUDDY_ID, "61");
        MessengerBuddy staffChat = mock(MessengerBuddy.class);
        when(fixture.user.hasPermission(StaffChatBuddy.PERMISSION_KEY)).thenReturn(true);
        when(fixture.messenger.getFriend(StaffChatBuddy.BUDDY_ID)).thenReturn(staffChat);

        fixture.messengerMessage(0, StaffChatBuddy.BUDDY_ID, 4, "61");

        verify(staffChat, never()).onMessageReceived(any(), anyString());
        verify(fixture.client).sendResponse(any(MessengerMessageFailedComposer.class));
    }

    private static final class Fixture {
        final GameClient client = mock(GameClient.class);
        final User user = mock(User.class);
        final UserInfo info = mock(UserInfo.class);
        final UserStats stats = mock(UserStats.class);
        final Messenger messenger = mock(Messenger.class);
        final PluginManager plugins = mock(PluginManager.class);
        final int targetId;
        final String message;

        Fixture(int targetId, String message) {
            this.targetId = targetId;
            this.message = message;
            when(client.getUser()).thenReturn(user);
            when(user.getUserInfo()).thenReturn(info);
            when(user.getUserStats()).thenReturn(stats);
            when(user.getMessenger()).thenReturn(messenger);
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

        private void handle(polaris.messages.incoming.MessageHandler handler, int header, ServerMessage packet)
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

        InstantMessageErrorComposer refusal() {
            ArgumentCaptor<InstantMessageErrorComposer> captor =
                    ArgumentCaptor.forClass(InstantMessageErrorComposer.class);
            verify(this.client).sendResponse(captor.capture());
            return captor.getValue();
        }
    }
}
