package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SnowStormJoinQueueEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        if (!SnowWarManager.getInstance()
            .allowPacket(this.client.getHabbo().getHabboInfo().getId())) {
            return;
        }

        SnowWarManager.getInstance().joinQueue(this.client.getHabbo());
    }
}
