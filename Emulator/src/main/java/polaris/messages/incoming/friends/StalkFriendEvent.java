package polaris.messages.incoming.friends;

import polaris.Emulator;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.StalkErrorComposer;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;

public class StalkFriendEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int friendId = this.packet.readInt();

        if (!FriendInputGuard.isPositiveId(friendId)) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.NOT_IN_FRIEND_LIST));
            return;
        }

        MessengerBuddy buddy = this.client.getUser().getMessenger().getFriend(friendId);

        if (buddy == null) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.NOT_IN_FRIEND_LIST));
            return;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(friendId);

        boolean canStalk = this.client.getUser().hasPermission("acc_can_stalk");

        // A friend who hides being online looks offline here too, so following does not give it away.
        if (user == null || !user.isOnline() || hiddenFrom(user.getUserStats().hideOnline, canStalk)) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_OFFLINE));
            return;
        }

        if (user.getUserStats().blockFollowing && !canStalk) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_BLOCKED_STALKING));
            return;
        }

        if (user.getUserInfo().getCurrentRoom() == null) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_NOT_IN_ROOM));
            return;
        }

        if (user.getUserInfo().getCurrentRoom()
                != this.client.getUser().getUserInfo().getCurrentRoom()) {
            this.client.sendResponse(new ForwardToRoomComposer(
                    user.getUserInfo().getCurrentRoom().getId()));
            polaris.hotel.quests.QuestProgressEvents.progress(
                    this.client.getUser(), polaris.hotel.quests.QuestGoalType.FOLLOW_FRIEND, 1);
        } else {
            this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                    Emulator.getTexts()
                            .getValue("stalk.failed.same.room")
                            .replace("%user%", user.getUserInfo().getUsername()),
                    this.client.getUser(),
                    this.client.getUser(),
                    RoomChatMessageBubbles.ALERT)));
        }
    }

    static boolean hiddenFrom(boolean hidesOnline, boolean canStalk) {
        return hidesOnline && !canStalk;
    }
}
