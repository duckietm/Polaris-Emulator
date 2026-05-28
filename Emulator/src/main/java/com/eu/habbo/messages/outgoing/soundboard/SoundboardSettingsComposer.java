package com.eu.habbo.messages.outgoing.soundboard;

import com.eu.habbo.habbohotel.soundboard.SoundboardSound;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

// Sent on room enter (and on toggle): whether the soundboard is active in this
// room + the available pads. The client shows the toolbar icon only if enabled.
public class SoundboardSettingsComposer extends MessageComposer {
    private final boolean enabled;
    private final List<SoundboardSound> sounds;

    public SoundboardSettingsComposer(boolean enabled, List<SoundboardSound> sounds) {
        this.enabled = enabled;
        this.sounds = sounds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SoundboardSettingsComposer);
        this.response.appendBoolean(this.enabled);
        this.response.appendInt(this.sounds.size());
        for (SoundboardSound sound : this.sounds) {
            this.response.appendInt(sound.id);
            this.response.appendString(sound.name);
            this.response.appendString(sound.url);
        }
        return this.response;
    }
}
