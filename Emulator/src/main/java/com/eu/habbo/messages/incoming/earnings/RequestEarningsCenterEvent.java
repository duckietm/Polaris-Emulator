package com.eu.habbo.messages.incoming.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.earnings.EarningsCenterComposer;

public class RequestEarningsCenterEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        EarningsCenterManager manager = new EarningsCenterManager();
        this.client.sendResponse(new EarningsCenterComposer(manager.getEntries(this.client.getHabbo())));
    }
}
