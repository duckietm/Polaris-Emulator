package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SnowStormSelectArenaEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        SnowWarManager manager = SnowWarManager.getInstance();
        int userId = this.client.getHabbo().getHabboInfo().getId();
        if (!manager.allowPacket(userId)) {
            return;
        }

        manager.selectArena(this.client.getHabbo(), this.packet.readInt());
    }
}
