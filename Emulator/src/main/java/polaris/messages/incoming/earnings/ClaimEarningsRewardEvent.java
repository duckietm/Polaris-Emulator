package polaris.messages.incoming.earnings;

import polaris.hotel.earnings.EarningsCenterManager;
import polaris.hotel.earnings.EarningsClaimResult;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.earnings.EarningsClaimResultComposer;

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
        EarningsClaimResult result = manager.claim(this.client.getUser(), categoryKey);
        // No IncomeRewardNotification here: AIR EarningsController reads it as "a new reward arrived"
        // (notification.earning.new + the purse indicator), which a claim is not.
        this.client.sendResponse(new EarningsClaimResultComposer(result));
    }
}
