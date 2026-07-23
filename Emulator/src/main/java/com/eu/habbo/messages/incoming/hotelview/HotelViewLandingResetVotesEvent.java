package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class HotelViewLandingResetVotesEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getHabboInfo().getRank().getId() < 7) return;

        Emulator.getGameEnvironment().getHotelViewManager().resetCommunityGoalVotes(this.packet.readInt());
    }
}
