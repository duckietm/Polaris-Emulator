package polaris.messages.incoming.communitygoals;

import polaris.Emulator;
import polaris.hotel.communitygoals.CommunityGoalManager;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.CommunityGoalEarnedPrizesComposer;

/**
 * AIR {@code RedeemCommunityGoalPrize} (90, {@code int communityGoalId}): hands out
 * the earned prize of that goal once, then answers with the remaining
 * {@code CommunityGoalEarnedPrizes} (3319) so the redeem button disappears.
 */
public class RedeemCommunityGoalPrizeEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null) return;

        int communityGoalId = this.packet.readInt();
        CommunityGoalManager manager = Emulator.getGameEnvironment().getCommunityGoalManager();

        manager.redeemPrize(user, communityGoalId);

        this.client.sendResponse(new CommunityGoalEarnedPrizesComposer(
                manager.getEarnedPrizes(user.getUserInfo().getId())));
    }
}
