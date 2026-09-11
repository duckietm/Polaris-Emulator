package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.BlockListComposer;
import java.util.ArrayList;
import java.util.List;

/**
 * Official {@code BlockedUsersManager.initBlockList()} (header 485): the client asks for the
 * whole block list once the session data is known.
 */
public class GetBlockedUsersEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        List<Integer> blocked =
                new ArrayList<>(this.client.getHabbo().getHabboStats().getBlockedUsers());

        this.client.sendResponse(new BlockListComposer(blocked));
    }
}
