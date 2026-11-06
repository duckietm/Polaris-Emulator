package polaris.messages.incoming.earnings;

import java.util.List;
import polaris.hotel.earnings.EarningsCenterManager;
import polaris.hotel.earnings.EarningsClaimResult;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.earnings.EarningsClaimResultComposer;

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
        List<EarningsClaimResult> results = manager.claimAll(this.client.getUser());
        // No IncomeRewardNotification: it announces a new reward (AIR EarningsController), not a claim.
        this.client.sendResponse(new EarningsClaimResultComposer(results));
    }
}
