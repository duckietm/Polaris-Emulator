package com.eu.habbo.messages.incoming.snowwar;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.messages.incoming.MessageHandler;

public class SnowStormLeaveQueueEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        SnowWarManager.getInstance().leaveQueue(this.client.getHabbo());
    }
}
