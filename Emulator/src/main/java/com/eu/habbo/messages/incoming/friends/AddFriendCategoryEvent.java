package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.MessengerCategory;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.UpdateFriendComposer;

import java.util.Collections;

public class AddFriendCategoryEvent extends MessageHandler {
    @Override
    public void handle() {
        HabboInfo info = this.client.getHabbo().getHabboInfo();
        String name = FriendCategoryInputGuard.normalizeName(this.packet.readString());
        if (!FriendCategoryInputGuard.isValidName(name) || info.getMessengerCategories().size() >= FriendCategoryInputGuard.MAX_CATEGORIES) return;
        if (info.getMessengerCategories().stream().anyMatch(category -> category.getName().equalsIgnoreCase(name))) return;

        info.addMessengerCategory(new MessengerCategory(name, info.getId(), 0));
        this.client.sendResponse(new UpdateFriendComposer(this.client.getHabbo(), Collections.emptyList(), 0));
    }
}
