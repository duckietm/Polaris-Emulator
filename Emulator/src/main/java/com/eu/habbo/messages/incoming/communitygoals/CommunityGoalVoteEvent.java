package com.eu.habbo.messages.incoming.communitygoals;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewHideCommunityVoteButtonComposer;

/**
 * AIR 13 CommunityGoalVote (3536): one vote option id. The answer is the
 * "hide the vote button" packet (1435) the client reads as the acknowledgement.
 */
public class CommunityGoalVoteEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();
        int optionId = this.packet.readInt();

        if (habbo == null) return;

        boolean accepted = Emulator.getGameEnvironment()
                .getCommunityGoalManager()
                .vote(habbo.getHabboInfo().getId(), optionId);

        this.client.sendResponse(new HotelViewHideCommunityVoteButtonComposer(accepted));
    }
}
