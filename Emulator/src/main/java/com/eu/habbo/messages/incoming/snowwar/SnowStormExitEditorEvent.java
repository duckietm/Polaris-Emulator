package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Header 6014: the arena editor is closed. The manager releases this user's
 * matchmaking lock; once the last editor leaves, games may run again.
 */
public class SnowStormExitEditorEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        SnowWarManager.getInstance().exitEditor(habbo);
    }
}
