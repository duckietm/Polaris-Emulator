package com.eu.habbo.messages.incoming.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.communitygoals.CommunityGoalManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewConcurrentUsersComposer;

/**
 * AIR 13 GetConcurrentUsersGoalProgress (1343): the state, the number of players
 * online and the goal, which the `element_concurrentusersinfo` element polls
 * every five seconds until the goal is reached.
 */
public class HotelViewRequestConcurrentUsersEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        CommunityGoalManager manager = Emulator.getGameEnvironment().getCommunityGoalManager();

        this.client.sendResponse(new HotelViewConcurrentUsersComposer(
                manager.getConcurrentUsersState(habbo.getHabboInfo().getId()),
                manager.getConcurrentUsersCount(),
                manager.getConcurrentUsersGoal()));
    }
}
