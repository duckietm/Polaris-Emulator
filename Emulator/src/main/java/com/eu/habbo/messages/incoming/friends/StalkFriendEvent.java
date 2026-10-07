package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.StalkErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;

public class StalkFriendEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int friendId = this.packet.readInt();

        if (!FriendInputGuard.isPositiveId(friendId)) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.NOT_IN_FRIEND_LIST));
            return;
        }

        MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriend(friendId);

        if (buddy == null) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.NOT_IN_FRIEND_LIST));
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(friendId);

        boolean canStalk = this.client.getHabbo().hasPermission("acc_can_stalk");

        // A friend who hides being online looks offline here too, so following does not give it away.
        if (habbo == null || !habbo.isOnline() || hiddenFrom(habbo.getHabboStats().hideOnline, canStalk)) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_OFFLINE));
            return;
        }

        if (habbo.getHabboStats().blockFollowing && !canStalk) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_BLOCKED_STALKING));
            return;
        }

        if (habbo.getHabboInfo().getCurrentRoom() == null) {
            this.client.sendResponse(new StalkErrorComposer(StalkErrorComposer.FRIEND_NOT_IN_ROOM));
            return;
        }

        if (habbo.getHabboInfo().getCurrentRoom()
                != this.client.getHabbo().getHabboInfo().getCurrentRoom()) {
            this.client.sendResponse(new ForwardToRoomComposer(
                    habbo.getHabboInfo().getCurrentRoom().getId()));
            com.eu.habbo.habbohotel.quests.QuestProgressEvents.progress(
                    this.client.getHabbo(), com.eu.habbo.habbohotel.quests.QuestGoalType.FOLLOW_FRIEND, 1);
        } else {
            this.client.sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                    Emulator.getTexts()
                            .getValue("stalk.failed.same.room")
                            .replace("%user%", habbo.getHabboInfo().getUsername()),
                    this.client.getHabbo(),
                    this.client.getHabbo(),
                    RoomChatMessageBubbles.ALERT)));
        }
    }

    static boolean hiddenFrom(boolean hidesOnline, boolean canStalk) {
        return hidesOnline && !canStalk;
    }
}
