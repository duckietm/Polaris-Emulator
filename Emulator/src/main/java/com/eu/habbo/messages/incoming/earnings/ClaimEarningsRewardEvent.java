package com.eu.habbo.messages.incoming.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.habbohotel.earnings.EarningsClaimResult;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.earnings.EarningsClaimResultComposer;
import com.eu.habbo.messages.outgoing.earnings.IncomeRewardNotificationComposer;

public class ClaimEarningsRewardEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public String getRatelimitGroup() {
        return "earnings.claim";
    }

    @Override
    public void handle() {
        String categoryKey = this.packet.readString();
        EarningsCenterManager manager = new EarningsCenterManager();
        EarningsClaimResult result = manager.claim(this.client.getHabbo(), categoryKey);
        this.client.sendResponse(new EarningsClaimResultComposer(result));

        // Official IncomeRewardNotification (1753): credited earnings raise the "earning" bubble
        // and refresh the purse indicators.
        if (result.isSuccess()) {
            this.client.sendResponse(new IncomeRewardNotificationComposer(result.getCategory()));
        }
    }
}
