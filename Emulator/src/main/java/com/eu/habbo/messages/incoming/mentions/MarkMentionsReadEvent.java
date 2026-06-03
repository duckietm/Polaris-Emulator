package com.eu.habbo.messages.incoming.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class MarkMentionsReadEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.client.getHabbo().getHabboInfo().getId();
        int mode = this.packet.readInt();
        int mentionId = this.packet.readInt();

        Emulator.getGameEnvironment().getMentionManager().markRead(userId, mode, mentionId);
    }
}
