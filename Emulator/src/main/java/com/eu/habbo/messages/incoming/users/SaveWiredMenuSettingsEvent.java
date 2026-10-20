package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.rooms.RoomPlayTestSupport;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Official wired menu preferences (1226): the whisper switch in its own column, the rest of the
 * menu preferences as one stored text, sent back in the user settings.
 */
public class SaveWiredMenuSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        UserPreferencePackets.WiredMenuPreferences preferences =
                UserPreferencePackets.parseWiredMenuPreferences(this.packet);

        Habbo habbo = this.client.getHabbo();
        boolean wasPlayTestMode = habbo.getHabboStats().isPlayTestMode();

        habbo.getHabboStats().setWiredWhisperDisabled(preferences.wiredWhisperDisabled());
        habbo.getHabboStats().setWiredMenuPreferences(preferences.encode());

        // AIR sends the preferences on every playtest switch; the room applies it at once.
        if (wasPlayTestMode != habbo.getHabboStats().isPlayTestMode()) {
            RoomPlayTestSupport.onPlayTestModeChanged(habbo);
        }
    }
}
