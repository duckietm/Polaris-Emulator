package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.messages.incoming.MessageHandler;

public class GameCenterEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // requested game type
    }
}
