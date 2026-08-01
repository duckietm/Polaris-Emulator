package com.eu.habbo.messages.outgoing.soundboard;

import com.eu.habbo.habbohotel.soundboard.SoundboardSound;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class SoundboardCatalogComposer extends MessageComposer {
    private final List<SoundboardSound> sounds;

    public SoundboardCatalogComposer(List<SoundboardSound> sounds) {
        this.sounds = List.copyOf(sounds);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SoundboardCatalogComposer);
        this.response.appendInt(this.sounds.size());
        for (SoundboardSound sound : this.sounds) {
            this.response.appendInt(sound.id);
            this.response.appendString(sound.name);
            this.response.appendString(sound.url);
            this.response.appendBoolean(sound.enabled);
            this.response.appendInt(sound.sortOrder);
            this.response.appendInt(sound.minRank);
        }
        return this.response;
    }
}
