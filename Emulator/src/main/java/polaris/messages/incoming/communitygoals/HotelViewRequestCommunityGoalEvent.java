package polaris.messages.incoming.communitygoals;

import polaris.Emulator;
import polaris.hotel.communitygoals.CommunityGoalProgress;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewCommunityGoalComposer;

/**
 * AIR 13 GetCommunityGoalProgress (1145): the hotel view asks for the tiered
 * community goal and gets {@code CommunityGoalProgress} (2525) back.
 */
public class HotelViewRequestCommunityGoalEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null) return;

        CommunityGoalProgress progress = Emulator.getGameEnvironment()
                .getCommunityGoalManager()
                .getProgress(user.getUserInfo().getId());

        this.client.sendResponse(new HotelViewCommunityGoalComposer(
                progress.hasGoalExpired(),
                progress.personalContributionScore(),
                progress.personalContributionRank(),
                progress.communityTotalScore(),
                progress.communityHighestAchievedLevel(),
                progress.scoreRemainingUntilNextLevel(),
                progress.percentCompletionTowardsNextLevel(),
                progress.goalCode(),
                progress.timeRemainingInSeconds(),
                progress.rewardUserLimits()));
    }
}
