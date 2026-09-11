package com.eu.habbo.messages.incoming.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.habbohotel.earnings.EarningsClaimResult;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.earnings.EarningsClaimResultComposer;
import com.eu.habbo.messages.outgoing.earnings.IncomeRewardNotificationComposer;
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
        this.client.sendResponse(new EarningsClaimResultComposer(results));

        // Official IncomeRewardNotification (1753): one bubble per credited category.
        for (EarningsClaimResult result : results) {
            if (result.isSuccess()) {
                this.client.sendResponse(new IncomeRewardNotificationComposer(result.getCategory()));
            }
        }
    }
}
