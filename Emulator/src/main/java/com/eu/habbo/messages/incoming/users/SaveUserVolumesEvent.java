package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserSavedSettingsEvent;

public class SaveUserVolumesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int system = UserInputGuard.clampVolume(this.packet.readInt());
        int furni = UserInputGuard.clampVolume(this.packet.readInt());
        int trax = UserInputGuard.clampVolume(this.packet.readInt());

        this.client.getHabbo().getHabboStats().volumeSystem = system;
        this.client.getHabbo().getHabboStats().volumeFurni = furni;
        this.client.getHabbo().getHabboStats().volumeTrax = trax;

        Emulator.getPluginManager().fireEvent(new UserSavedSettingsEvent(this.client.getHabbo()));
    }
}
