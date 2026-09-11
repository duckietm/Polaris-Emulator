package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BlockResultComposer;

/**
 * Official {@code BlockedUsersManager.blockUser(userId)} (header 697). The server answers with
 * the per-user result the client folds into its cache.
 */
public class BlockUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (this.client.getHabbo() == null) {
            return;
        }

        if (!this.client.getHabbo().getHabboStats().blockUser(userId)) {
            return;
        }

        this.client.sendResponse(new BlockResultComposer(BlockResultComposer.BLOCKED, userId));
    }
}
