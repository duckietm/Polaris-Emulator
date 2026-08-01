package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserSavedSettingsEvent;

public class SoundboardSaveVolumeEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        habbo.getHabboStats().saveSoundboardVolume(clampVolume(this.packet.readInt()));
        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(habbo));
    }

    static int clampVolume(int volume) {
        return Math.max(0, Math.min(100, volume));
    }
}
