package com.eu.habbo.messages.incoming.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.communitygoals.CommunityGoalManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewConcurrentUsersComposer;

/**
 * AIR 13 GetConcurrentUsersReward (3872): the player claims the badge of the
 * concurrent-users goal. The answer is the refreshed progress, so the button
 * turns itself off.
 */
public class HotelViewConcurrentUsersButtonEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        CommunityGoalManager manager = Emulator.getGameEnvironment().getCommunityGoalManager();
        manager.claimConcurrentUsersReward(habbo);

        this.client.sendResponse(new HotelViewConcurrentUsersComposer(
                manager.getConcurrentUsersState(habbo.getHabboInfo().getId()),
                manager.getConcurrentUsersCount(),
                manager.getConcurrentUsersGoal()));
    }
}
