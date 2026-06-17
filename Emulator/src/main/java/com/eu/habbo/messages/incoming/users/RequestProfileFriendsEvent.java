package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.ProfileFriendsComposer;

public class RequestProfileFriendsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        if (!UserInputGuard.isPositiveId(userId))
            return;

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (habbo != null)
            this.client.sendResponse(new ProfileFriendsComposer(habbo));
        else
            this.client.sendResponse(new ProfileFriendsComposer(Messenger.getFriends(userId), userId));
    }
}
