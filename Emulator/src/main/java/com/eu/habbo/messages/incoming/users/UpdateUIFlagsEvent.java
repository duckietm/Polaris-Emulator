package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.messages.incoming.MessageHandler;

public class UpdateUIFlagsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int flags = UserInputGuard.sanitizeUiFlags(this.packet.readInt());

        this.client.getHabbo().getHabboStats().uiFlags = flags;
        this.client.getHabbo().getHabboStats().run();
    }
}
