package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RoomVoteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int points = this.packet.readInt();

        if (points != 1 || this.client.getHabbo().getHabboInfo().getCurrentRoom() == null)
            return;

        Emulator.getGameEnvironment().getRoomManager().voteForRoom(this.client.getHabbo(), this.client.getHabbo().getHabboInfo().getCurrentRoom());
    }
}
