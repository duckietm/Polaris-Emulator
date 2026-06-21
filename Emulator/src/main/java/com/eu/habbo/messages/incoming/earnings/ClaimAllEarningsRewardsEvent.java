package com.eu.habbo.messages.incoming.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.earnings.EarningsClaimResultComposer;

public class ClaimAllEarningsRewardsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 3000;
    }

    @Override
    public void handle() {
        EarningsCenterManager manager = new EarningsCenterManager();
        this.client.sendResponse(new EarningsClaimResultComposer(manager.claimAll(this.client.getHabbo())));
    }
}
