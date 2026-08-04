package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.soundboard.SoundboardCatalogComposer;

public class SoundboardCatalogRequestEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (!SoundboardManagementAccess.canManage(habbo)) {
            return;
        }

        this.client.sendResponse(new SoundboardCatalogComposer(
                        Emulator.getGameEnvironment().getSoundboardManager().getCatalog())
                .compose());
    }
}
