package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.messenger.MessengerCategory;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.UpdateFriendComposer;

import java.util.ArrayList;
import java.util.List;

public class RemoveFriendCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        HabboInfo info = this.client.getHabbo().getHabboInfo();
        int categoryId = this.packet.readInt();
        MessengerCategory category = info.getMessengerCategory(categoryId);
        if (category == null) return;

        List<MessengerBuddy> changed = new ArrayList<>();
        for (MessengerBuddy buddy : this.client.getHabbo().getMessenger().getFriends().values()) {
            if (buddy.getCategoryId() != categoryId) continue;
            buddy.setCategoryId(0);
            changed.add(buddy);
        }
        info.deleteMessengerCategory(category);
        this.client.sendResponse(new UpdateFriendComposer(this.client.getHabbo(), changed, 0));
    }
}
