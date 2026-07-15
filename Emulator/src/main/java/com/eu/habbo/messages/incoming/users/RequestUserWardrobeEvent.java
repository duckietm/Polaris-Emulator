package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserWardrobeComposer;

public class RequestUserWardrobeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // requested wardrobe page
        this.client.sendResponse(new UserWardrobeComposer(this.client.getHabbo().getInventory().getWardrobeComponent()));
    }
}
