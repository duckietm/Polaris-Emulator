package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingUserDetailComposer;

public class HousekeepingFindUserByNameEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        String username = HousekeepingInputGuard.normalize(this.packet.readString());

        if (username.isEmpty()
                || !HousekeepingInputGuard.isWithinLimit(username, HousekeepingInputGuard.MAX_LOOKUP_LENGTH)) {
            this.client.sendResponse(new HousekeepingUserDetailComposer(null));
            return;
        }

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(username);
        HabboInfo info = online != null ? online.getHabboInfo() : HabboManager.getOfflineHabboInfo(username);

        this.client.sendResponse(new HousekeepingUserDetailComposer(info));
    }
}
