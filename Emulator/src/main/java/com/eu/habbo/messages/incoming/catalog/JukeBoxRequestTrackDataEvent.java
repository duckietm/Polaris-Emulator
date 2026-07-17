package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.SoundTrack;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.items.jukebox.JukeBoxTrackDataComposer;
import com.eu.habbo.util.PacketGuard;

import java.util.ArrayList;
import java.util.List;

public class JukeBoxRequestTrackDataEvent extends MessageHandler {
    static final int MAX_TRACK_REQUESTS = 1_000;

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();

        if (!PacketGuard.isValidIntList(count, this.packet.bytesAvailable(), MAX_TRACK_REQUESTS)) {
            return;
        }

        List<SoundTrack> tracks = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            SoundTrack track = Emulator.getGameEnvironment().getItemManager().getSoundTrack(this.packet.readInt());

            if (track != null)
                tracks.add(track);
        }

        this.client.sendResponse(new JukeBoxTrackDataComposer(tracks));
    }
}
