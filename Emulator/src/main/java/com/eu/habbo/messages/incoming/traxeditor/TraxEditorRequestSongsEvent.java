package com.eu.habbo.messages.incoming.traxeditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.traxeditor.TraxEditorManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.traxeditor.TraxEditorSongsComposer;

public class TraxEditorRequestSongsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        TraxEditorManager manager = Emulator.getGameEnvironment().getTraxEditorManager();

        this.client.sendResponse(new TraxEditorSongsComposer(
                manager.getMaxSongs(),
                manager.getCostCurrency(),
                manager.getCostAmount(),
                manager.getSongs(habbo.getHabboInfo().getId())));
    }
}
