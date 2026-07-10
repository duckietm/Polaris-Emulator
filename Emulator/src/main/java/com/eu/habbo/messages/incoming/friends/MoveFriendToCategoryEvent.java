package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.UpdateFriendComposer;

public class MoveFriendToCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        int friendId = this.packet.readInt();
        int categoryId = this.packet.readInt();
        if (friendId <= 0 || categoryId < 0) return;
        if (categoryId > 0 && this.client.getHabbo().getHabboInfo().getMessengerCategory(categoryId) == null) return;

        MessengerBuddy buddy = this.client.getHabbo().getMessenger().getFriend(friendId);
        if (buddy == null || !this.client.getHabbo().getHabboInfo().moveMessengerFriendToCategory(friendId, categoryId)) return;
        buddy.setCategoryId(categoryId);
        this.client.sendResponse(new UpdateFriendComposer(this.client.getHabbo(), buddy, 0));
    }
}
