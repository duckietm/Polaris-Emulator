package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.gamecenter.basejump.BaseJumpUnloadGameComposer;

public class GameCenterLeaveGameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // unloaded game type
        this.client.sendResponse(new BaseJumpUnloadGameComposer());
    }
}
