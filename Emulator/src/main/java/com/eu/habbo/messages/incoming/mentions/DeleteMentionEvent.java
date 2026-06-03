package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class DeleteMentionEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.client.getHabbo().getHabboInfo().getId();
        int mentionId = this.packet.readInt();

        Emulator.getGameEnvironment().getMentionManager().delete(userId, mentionId);
    }
}
