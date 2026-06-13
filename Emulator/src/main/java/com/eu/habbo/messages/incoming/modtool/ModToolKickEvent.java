package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ModToolKickEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            Emulator.getGameEnvironment().getModToolManager().kick(this.client.getHabbo(), Emulator.getGameEnvironment().getHabboManager().getHabbo(this.packet.readInt()), this.packet.readString());
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.kick").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
