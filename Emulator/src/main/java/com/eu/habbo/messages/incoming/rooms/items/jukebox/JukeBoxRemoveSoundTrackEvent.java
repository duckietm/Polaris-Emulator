package com.eu.habbo.messages.incoming.rooms.items.jukebox;

import com.eu.habbo.habbohotel.items.interactions.InteractionMusicDisc;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;

public class JukeBoxRemoveSoundTrackEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int index = this.packet.readInt();

        // Adding a disc needs rights; removing one did not, so any visitor emptied the playlist and
        // sent every disc back to its owner.
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || !room.hasRights(this.client.getHabbo())) return;

        if (index < 0 || index >= room.getTraxManager().getSongs().size()) return;

        InteractionMusicDisc musicDisc = room.getTraxManager().getSongs().get(index);

        if (musicDisc != null) {
            room.getTraxManager().removeSong(musicDisc.getId());
        }
    }
}
