package com.eu.habbo.messages.incoming.traxeditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.traxeditor.TraxEditorManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.traxeditor.TraxEditorErrorComposer;
import com.eu.habbo.messages.outgoing.traxeditor.TraxEditorSongsComposer;

public class TraxEditorSaveSongEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        int songId = this.packet.readInt();
        String name = this.packet.readString();
        String data = this.packet.readString();

        TraxEditorManager manager = Emulator.getGameEnvironment().getTraxEditorManager();
        int result = manager.saveSong(habbo, songId, name, data);

        if (result != 0) {
            this.client.sendResponse(new TraxEditorErrorComposer(result));
            return;
        }

        this.client.sendResponse(new TraxEditorSongsComposer(
                manager.getMaxSongs(),
                manager.getCostCurrency(),
                manager.getCostAmount(),
                manager.getSongs(habbo.getHabboInfo().getId())));
    }
}
