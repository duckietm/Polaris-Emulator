package polaris.messages.incoming.communitygoals;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.CommunityGoalEarnedPrizesComposer;

/**
 * AIR {@code GetCommunityGoalEarnedPrizes} (2688, no fields) -&gt;
 * {@code CommunityGoalEarnedPrizes} (3319): the prizes of finished community goals
 * the player ranked for and did not redeem yet.
 */
public class GetCommunityGoalEarnedPrizesEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null) return;

        this.client.sendResponse(new CommunityGoalEarnedPrizesComposer(Emulator.getGameEnvironment()
                .getCommunityGoalManager()
                .getEarnedPrizes(user.getUserInfo().getId())));
    }
}
