package com.eu.habbo.messages.incoming.discord;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.discord.DiscordPreferencesComposer;

/**
 * Official {@code DiscordSettingsController.initComponent()} (header 1055): the client asks for
 * the stored Discord Rich Presence preferences when the component comes up.
 */
public class GetDiscordPreferencesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        this.client.sendResponse(new DiscordPreferencesComposer(
                this.client.getHabbo().getHabboStats().getDiscordPreferences()));
    }
}
