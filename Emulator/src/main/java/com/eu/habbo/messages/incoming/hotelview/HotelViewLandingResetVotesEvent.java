package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
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

        if (habbo == null || !habbo.hasPermission(Permission.ACC_HOTELVIEW_EDIT)) return;

        Emulator.getGameEnvironment().getHotelViewManager().resetCommunityGoalVotes(this.packet.readInt());
    }
}
