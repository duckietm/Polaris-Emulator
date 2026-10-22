package com.eu.habbo.messages.incoming.earnings;

import com.eu.habbo.habbohotel.earnings.EarningsCenterManager;
import com.eu.habbo.habbohotel.earnings.EarningsClaimResult;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.earnings.EarningsClaimResultComposer;

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
        // No IncomeRewardNotification here: AIR EarningsController reads it as "a new reward arrived"
        // (notification.earning.new + the purse indicator), which a claim is not.
        this.client.sendResponse(new EarningsClaimResultComposer(result));
    }
}
