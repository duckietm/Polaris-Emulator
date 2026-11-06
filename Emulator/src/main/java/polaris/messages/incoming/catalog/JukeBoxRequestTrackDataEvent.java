package polaris.messages.incoming.catalog;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.items.SoundTrack;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.jukebox.JukeBoxTrackDataComposer;
import polaris.util.PacketGuard;

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

            if (track != null) tracks.add(track);
        }

        this.client.sendResponse(new JukeBoxTrackDataComposer(tracks));
    }
}
