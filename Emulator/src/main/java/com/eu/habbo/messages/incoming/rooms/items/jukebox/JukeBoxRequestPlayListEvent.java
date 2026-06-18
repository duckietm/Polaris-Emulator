package com.eu.habbo.messages.incoming.rooms.items.jukebox;

import com.eu.habbo.habbohotel.rooms.TraxManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.jukebox.JukeBoxMySongsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.jukebox.JukeBoxPlayListComposer;

public class JukeBoxRequestPlayListEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        TraxManager traxManager = room.getTraxManager();
        this.client.sendResponse(new JukeBoxPlayListComposer(traxManager.getSongs(), traxManager.totalLength()));
        this.client.sendResponse(new JukeBoxMySongsComposer(traxManager.myList(this.client.getHabbo())));
        traxManager.updateCurrentPlayingSong(this.client.getHabbo());
    }
}
