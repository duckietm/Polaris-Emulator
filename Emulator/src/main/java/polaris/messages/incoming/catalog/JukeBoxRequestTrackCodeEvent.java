package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.items.SoundTrack;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.jukebox.JukeBoxTrackCodeComposer;

public class JukeBoxRequestTrackCodeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String songName = this.packet.readString();

        final SoundTrack track = Emulator.getGameEnvironment().getItemManager().getSoundTrack(songName);

        if (track != null) {
            this.client.sendResponse(new JukeBoxTrackCodeComposer(track));
        }
    }
}
