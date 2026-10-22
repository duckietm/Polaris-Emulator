package com.eu.habbo.messages.incoming.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.CommunityGoalEarnedPrizesComposer;

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
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        this.client.sendResponse(new CommunityGoalEarnedPrizesComposer(Emulator.getGameEnvironment()
                .getCommunityGoalManager()
                .getEarnedPrizes(habbo.getHabboInfo().getId())));
    }
}
