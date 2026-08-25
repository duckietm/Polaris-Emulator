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
    private final int cooldownSeconds;
    private final List<SoundboardSound> sounds;

    public SoundboardSettingsComposer(boolean enabled, List<SoundboardSound> sounds) {
        this(enabled, 60, sounds);
    }

    public SoundboardSettingsComposer(boolean enabled, int cooldownSeconds, List<SoundboardSound> sounds) {
        this.enabled = enabled;
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.sounds = sounds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SoundboardSettingsComposer);
        this.response.appendBoolean(this.enabled);
        this.response.appendInt(this.cooldownSeconds);
        this.response.appendInt(this.sounds.size());
        for (SoundboardSound sound : this.sounds) {
            this.response.appendInt(sound.id);
            this.response.appendString(sound.name);
            this.response.appendString(sound.url);
        }

        // Classnames go in a block AFTER the records, never as a trailing
        // field inside the loop: a client that predates them can stop reading
        // here, while one that reads a per-record optional field would steal
        // bytes from the next record and cascade-corrupt the list.
        // The client prefers the classname and resolves it against
        // gamedata/SoundData.json; url stays as the override for clips hosted
        // outside the asset tree.
        for (SoundboardSound sound : this.sounds) {
            this.response.appendString(sound.classname);
        }
        return this.response;
    }
}
