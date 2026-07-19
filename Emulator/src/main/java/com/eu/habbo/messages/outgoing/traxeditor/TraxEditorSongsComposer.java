package com.eu.habbo.messages.outgoing.traxeditor;

import com.eu.habbo.habbohotel.items.SoundTrack;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

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
