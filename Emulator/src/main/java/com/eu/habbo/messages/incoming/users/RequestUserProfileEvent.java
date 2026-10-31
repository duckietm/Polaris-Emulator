package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserProfileComposer;

public class RequestUserProfileEvent extends MessageHandler {
    // An offline profile is read from the database (several queries), as often as the client asked.
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int habboId = this.packet.readInt();
        this.packet.readBoolean(); // UI display hint; profile visibility is enforced by the server response.

        if (!UserInputGuard.isPositiveId(habboId)) return;

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);

        if (habbo != null) this.client.sendResponse(new UserProfileComposer(habbo, this.client));
        else this.client.sendResponse(new UserProfileComposer(HabboManager.getOfflineHabboInfo(habboId), this.client));
    }
}
