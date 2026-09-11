package com.eu.habbo.messages.incoming.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.communitygoals.CommunityGoalProgress;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewCommunityGoalComposer;

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
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        CommunityGoalProgress progress = Emulator.getGameEnvironment()
                .getCommunityGoalManager()
                .getProgress(habbo.getHabboInfo().getId());

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
