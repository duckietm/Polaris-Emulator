package polaris.messages.outgoing.rooms.items.jukebox;

import java.util.List;
import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class JukeBoxPlayListComposer extends MessageComposer {
    private final List<InteractionMusicDisc> songs;
    private final int totalLength;

    public JukeBoxPlayListComposer(List<InteractionMusicDisc> songs, int totalLength) {
        this.songs = songs;
        this.totalLength = totalLength;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.JukeBoxPlayListComposer);
        this.response.appendInt(this.totalLength); // Total play length of all songs in seconds
        this.response.appendInt(this.songs.size());
        for (InteractionMusicDisc soundTrack : this.songs) {
            this.response.appendInt(soundTrack.getId());
            this.response.appendInt(soundTrack.getSongId());
        }
        return this.response;
    }

    public List<InteractionMusicDisc> getSongs() {
        return songs;
    }

    public int getTotalLength() {
        return totalLength;
    }
}
