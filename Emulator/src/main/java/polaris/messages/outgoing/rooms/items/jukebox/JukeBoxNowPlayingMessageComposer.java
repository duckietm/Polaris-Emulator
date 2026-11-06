package polaris.messages.outgoing.rooms.items.jukebox;

import polaris.hotel.items.SoundTrack;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class JukeBoxNowPlayingMessageComposer extends MessageComposer {
    private final SoundTrack track;
    private final int playListId;
    private final int msPlayed;

    public JukeBoxNowPlayingMessageComposer(SoundTrack track, int playListId, int msPlayed) {
        this.track = track;
        this.playListId = playListId;
        this.msPlayed = msPlayed;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.JukeBoxNowPlayingMessageComposer);

        if (this.track != null) {
            this.response.appendInt(this.track.getId());
            this.response.appendInt(this.playListId);
            this.response.appendInt(this.track.getId());
            this.response.appendInt(this.track.getLength());
            this.response.appendInt(this.msPlayed);
        } else {
            this.response.appendInt(-1);
            this.response.appendInt(-1);
            this.response.appendInt(-1);
            this.response.appendInt(-1);
            this.response.appendInt(-1);
        }
        return this.response;
    }

    public SoundTrack getTrack() {
        return track;
    }

    public int getPlayListId() {
        return playListId;
    }

    public int getMsPlayed() {
        return msPlayed;
    }
}
