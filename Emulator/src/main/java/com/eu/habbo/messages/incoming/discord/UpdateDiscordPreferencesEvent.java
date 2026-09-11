package com.eu.habbo.messages.incoming.discord;

import com.eu.habbo.habbohotel.users.DiscordPreferences;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.discord.DiscordPreferencesComposer;

/**
 * Official {@code DiscordSettingsController.updatePreferences} (header 2774):
 * {@code (preferenceGlobalVersion, showHabbo, shareActivity, hideInHiddenRooms, allowJoining)}.
 */
public class UpdateDiscordPreferencesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int version = this.packet.readInt();
        boolean showHabbo = this.packet.readBoolean();
        boolean shareActivity = this.packet.readBoolean();
        boolean hideInHiddenRooms = this.packet.readBoolean();
        boolean allowJoining = this.packet.readBoolean();

        if (this.client.getHabbo() == null) {
            return;
        }

        DiscordPreferences preferences =
                new DiscordPreferences(version, showHabbo, shareActivity, hideInHiddenRooms, allowJoining);

        this.client.getHabbo().getHabboStats().setDiscordPreferences(preferences);
        this.client.sendResponse(new DiscordPreferencesComposer(preferences));
    }
}
