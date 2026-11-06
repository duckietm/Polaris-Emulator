package polaris.messages.outgoing.rooms.items.jukebox;

import java.util.Collection;
import polaris.hotel.items.SoundTrack;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class JukeBoxPlayListUpdatedComposer extends MessageComposer {
    private final Collection<SoundTrack> tracks;

    public JukeBoxPlayListUpdatedComposer(Collection<SoundTrack> tracks) {
        this.tracks = tracks;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.JukeBoxPlayListUpdatedComposer);

        int length = 0;

        for (SoundTrack track : this.tracks) {
            length += track.getLength();
        }

        this.response.appendInt(length * 1000);
        this.response.appendInt(this.tracks.size());

        for (SoundTrack track : this.tracks) {
            this.response.appendInt(track.getId());
            this.response.appendInt(track.getLength() * 1000);
            this.response.appendString(track.getCode());
            this.response.appendString(track.getAuthor());
        }

        return this.response;
    }

    public Collection<SoundTrack> getTracks() {
        return tracks;
    }
}
