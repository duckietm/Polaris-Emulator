package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class MeMenuSettingsComposer extends MessageComposer {
    private final Habbo habbo;

    public MeMenuSettingsComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MeMenuSettingsComposer);
        this.response.appendInt(this.habbo.getHabboStats().volumeSystem);
        this.response.appendInt(this.habbo.getHabboStats().volumeFurni);
        this.response.appendInt(this.habbo.getHabboStats().volumeTrax);
        this.response.appendBoolean(this.habbo.getHabboStats().preferOldChat);
        this.response.appendBoolean(this.habbo.getHabboStats().blockRoomInvites);
        this.response.appendBoolean(this.habbo.getHabboStats().blockCameraFollow);
        this.response.appendInt(this.habbo.getHabboStats().uiFlags);
        this.response.appendInt(this.habbo.getHabboStats().chatColor.getType());
        // Game privacy flags, broadcast in the positive form the client expects
        // (SaveGamePrivacySettingsEvent stores them inverted as hide/block).
        this.response.appendBoolean(!this.habbo.getHabboStats().hideOnline);
        this.response.appendBoolean(!this.habbo.getHabboStats().blockFollowing);
        this.response.appendBoolean(!this.habbo.getHabboStats().blockFriendRequests);
        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
