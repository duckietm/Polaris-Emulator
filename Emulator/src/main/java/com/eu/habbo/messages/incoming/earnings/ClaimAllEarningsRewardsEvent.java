package com.eu.habbo.messages.incoming.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.habbohotel.earnings.EarningsClaimResult;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.earnings.EarningsClaimResultComposer;
import java.util.List;

public class ClaimAllEarningsRewardsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 3000;
    }

    @Override
    public String getRatelimitGroup() {
        return "earnings.claim";
    }

    @Override
    public void handle() {
        EarningsCenterManager manager = new EarningsCenterManager();
        List<EarningsClaimResult> results = manager.claimAll(this.client.getHabbo());
        // No IncomeRewardNotification: it announces a new reward (AIR EarningsController), not a claim.
        this.client.sendResponse(new EarningsClaimResultComposer(results));
    }
}
