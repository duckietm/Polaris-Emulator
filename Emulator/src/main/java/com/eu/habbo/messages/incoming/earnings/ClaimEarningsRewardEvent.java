package com.eu.habbo.messages.incoming.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.earnings.EarningsClaimResultComposer;

public class ClaimEarningsRewardEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        String categoryKey = this.packet.readString();
        EarningsCenterManager manager = new EarningsCenterManager();
        this.client.sendResponse(new EarningsClaimResultComposer(manager.claim(this.client.getHabbo(), categoryKey)));
    }
}
