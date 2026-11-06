package polaris.messages.incoming.friends;

import polaris.Emulator;
import polaris.hotel.messenger.Message;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.messenger.StaffChatBuddy;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.FriendChatMessageComposer;
import polaris.messages.outgoing.friends.InstantMessageErrorComposer;
import polaris.plugin.events.users.friends.UserFriendChatEvent;

public class FriendPrivateMessageEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        String message = FriendInputGuard.normalizeMessage(this.packet.readString());
        User user = this.client.getUser();

        if (message.isEmpty()
                || !FriendInputGuard.isValidConsoleTarget(userId, user.hasPermission(StaffChatBuddy.PERMISSION_KEY))) {
            return;
        }

        // Refused messages are reported back, so the client does not show them as sent.
        if (!user.getUserStats().allowTalk()) {
            this.refuse(InstantMessageErrorComposer.SENDER_MUTED, userId, message);
            return;
        }

        if (user.getUserStats().consoleMessageFlooded(System.currentTimeMillis())) {
            this.refuse(InstantMessageErrorComposer.SEND_FAILED, userId, message);
            return;
        }

        MessengerBuddy buddy = user.getMessenger().getFriend(userId);
        if (buddy == null) {
            this.refuse(InstantMessageErrorComposer.NOT_FRIEND, userId, message);
            return;
        }

        UserFriendChatEvent event = new UserFriendChatEvent(user, buddy, message);
        if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return;

        if (userId > 0) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    user, polaris.hotel.quests.QuestGoalType.SEND_MESSENGER_MESSAGE, 1);
        }

        if (userId <= 0) {
            buddy.onMessageReceived(user, message);
        } else if (buddy.onMessageReceivedWithDeliveryStatus(user, message)) {
            Message confirmation = new Message(userId, user.getUserInfo().getId(), "");
            this.client.sendResponse(new FriendChatMessageComposer(confirmation, userId, userId, "offline-sent"));
        }
    }

    private void refuse(int errorCode, int userId, String message) {
        this.client.sendResponse(new InstantMessageErrorComposer(errorCode, userId, message));
    }
}
