package com.eu.habbo.messages.incoming.handshake;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class DisconnectEvent extends MessageHandler {
    @Override
    public void handle() {
        if (this.client == null) return;

        Emulator.getGameServer().getGameClientManager().forceDisposeClient(this.client);
    }
}
