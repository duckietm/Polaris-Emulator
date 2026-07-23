package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class HotelViewLandingVoteEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        Emulator.getGameEnvironment().getHotelViewManager().voteCommunityGoal(
                this.packet.readInt(),
                this.packet.readInt(),
                habbo.getHabboInfo().getId()
        );
    }
}
