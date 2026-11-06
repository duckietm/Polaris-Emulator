package polaris.messages.outgoing.rooms.items.jukebox;

import polaris.hotel.items.SoundTrack;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class JukeBoxTrackCodeComposer extends MessageComposer {
    private final SoundTrack track;

    public JukeBoxTrackCodeComposer(SoundTrack track) {
        this.track = track;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.JukeBoxTrackCodeComposer);
        this.response.appendString(this.track.getCode());
        this.response.appendInt(this.track.getId());
        return this.response;
    }

    public SoundTrack getTrack() {
        return track;
    }
}
