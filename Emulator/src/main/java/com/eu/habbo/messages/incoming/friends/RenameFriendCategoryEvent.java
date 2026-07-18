package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.MessengerCategory;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.UpdateFriendComposer;

import java.util.Collections;

public class RenameFriendCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        HabboInfo info = this.client.getHabbo().getHabboInfo();
        int categoryId = this.packet.readInt();
        String name = FriendCategoryInputGuard.normalizeName(this.packet.readString());
        if (!FriendInputGuard.isPositiveId(categoryId)) return;
        MessengerCategory category = info.getMessengerCategory(categoryId);
        if (category == null || !FriendCategoryInputGuard.isValidName(name)) return;
        if (info.getMessengerCategories().stream().anyMatch(item -> item.getId() != categoryId && item.getName().equalsIgnoreCase(name))) return;
        if (!info.renameMessengerCategory(category, name)) return;

        this.client.sendResponse(new UpdateFriendComposer(this.client.getHabbo(), Collections.emptyList(), 0));
    }
}
