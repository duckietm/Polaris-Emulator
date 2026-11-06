package polaris.messages.outgoing.traxeditor;

import java.util.List;
import polaris.hotel.items.SoundTrack;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TraxEditorSongsComposer extends MessageComposer {
    private final int maxSongs;
    private final int costCurrency;
    private final int costAmount;
    private final List<SoundTrack> songs;

    public TraxEditorSongsComposer(int maxSongs, int costCurrency, int costAmount, List<SoundTrack> songs) {
        this.maxSongs = maxSongs;
        this.costCurrency = costCurrency;
        this.costAmount = costAmount;
        this.songs = songs;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TraxEditorSongsComposer);
        this.response.appendInt(this.maxSongs);
        this.response.appendInt(this.costCurrency);
        this.response.appendInt(this.costAmount);
        this.response.appendInt(this.songs.size());
        for (SoundTrack song : this.songs) {
            this.response.appendInt(song.getId());
            this.response.appendString(song.getName());
            this.response.appendString(song.getData());
            this.response.appendInt(song.getLength());
        }
        return this.response;
    }
}
