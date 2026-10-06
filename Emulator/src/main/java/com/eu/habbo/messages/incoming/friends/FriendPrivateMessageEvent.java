package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Message;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.messenger.StaffChatBuddy;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendChatMessageComposer;
import com.eu.habbo.messages.outgoing.unknown.UnknownMessengerErrorComposer;
import com.eu.habbo.plugin.events.users.friends.UserFriendChatEvent;

public class FriendPrivateMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        String message = FriendInputGuard.normalizeMessage(this.packet.readString());
        Habbo habbo = this.client.getHabbo();

        if (message.isEmpty()
                || !FriendInputGuard.isValidConsoleTarget(userId, habbo.hasPermission(StaffChatBuddy.PERMISSION_KEY))) {
            return;
        }

        // Refused messages are reported back, so the client does not show them as sent.
        if (!habbo.getHabboStats().allowTalk()) {
            this.refuse(UnknownMessengerErrorComposer.SENDER_MUTED, userId, message);
            return;
        }

        if (habbo.getHabboStats().consoleMessageFlooded(System.currentTimeMillis())) {
            this.refuse(UnknownMessengerErrorComposer.SEND_FAILED, userId, message);
            return;
        }

        MessengerBuddy buddy = habbo.getMessenger().getFriend(userId);
        if (buddy == null) {
            this.refuse(UnknownMessengerErrorComposer.NOT_FRIEND, userId, message);
            return;
        }

        UserFriendChatEvent event = new UserFriendChatEvent(habbo, buddy, message);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return;

        if (userId > 0) {
            com.eu.habbo.habbohotel.quests.QuestProgressEvents.progress(
                    habbo, com.eu.habbo.habbohotel.quests.QuestGoalType.SEND_MESSENGER_MESSAGE, 1);
        }

        if (userId <= 0) {
            buddy.onMessageReceived(habbo, message);
        } else if (buddy.onMessageReceivedWithDeliveryStatus(habbo, message)) {
            Message confirmation = new Message(userId, habbo.getHabboInfo().getId(), "");
            this.client.sendResponse(new FriendChatMessageComposer(confirmation, userId, userId, "offline-sent"));
        }
    }

    private void refuse(int errorCode, int userId, String message) {
        this.client.sendResponse(new UnknownMessengerErrorComposer(errorCode, userId, message));
    }
}
