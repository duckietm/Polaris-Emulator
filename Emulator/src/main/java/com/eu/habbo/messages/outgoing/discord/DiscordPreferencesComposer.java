package com.eu.habbo.messages.outgoing.discord;

import com.eu.habbo.habbohotel.users.DiscordPreferences;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code class_2741} / {@code DiscordPreferences.readFromData} (official header 1600,
 * ours 9472). Version 0 means the user never saved the preferences, which is what makes the
 * official {@code DiscordSettingsController} fall back to the all-on defaults and offer the popup.
 */
public class DiscordPreferencesComposer extends MessageComposer {
    private final DiscordPreferences preferences;

    public DiscordPreferencesComposer(DiscordPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.DiscordPreferencesComposer);
        this.response.appendInt(this.preferences.getVersion());
        this.response.appendBoolean(this.preferences.isShowHabbo());
        this.response.appendBoolean(this.preferences.isShareActivity());
        this.response.appendBoolean(this.preferences.isHideInHiddenRooms());
        this.response.appendBoolean(this.preferences.isAllowJoining());
        return this.response;
    }
}
