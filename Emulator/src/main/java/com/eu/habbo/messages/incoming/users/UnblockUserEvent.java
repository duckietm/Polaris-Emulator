package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BlockResultComposer;

/** Official {@code BlockedUsersManager.unblockUser(userId)} (header 1886). */
public class UnblockUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (this.client.getHabbo() == null) {
            return;
        }

        if (!this.client.getHabbo().getHabboStats().unblockUser(userId)) {
            return;
        }

        this.client.sendResponse(new BlockResultComposer(BlockResultComposer.UNBLOCKED, userId));
    }
}
