package polaris.messages.outgoing.soundboard;

import java.util.List;
import polaris.hotel.soundboard.SoundboardSound;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

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

        // Trailing block, same reasoning as the settings packet: management
        // needs the classname to show which asset a pad points at, and to edit
        // it without typing a URL.
        for (SoundboardSound sound : this.sounds) {
            this.response.appendString(sound.classname);
        }

        // And after the classnames, one cooldown per pad in the same order.
        for (SoundboardSound sound : this.sounds) {
            this.response.appendInt(sound.cooldownSeconds);
        }
        return this.response;
    }
}
