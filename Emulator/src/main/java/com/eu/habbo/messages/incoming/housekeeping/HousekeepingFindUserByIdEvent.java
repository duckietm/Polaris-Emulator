package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingUserDetailComposer;

public class HousekeepingFindUserByIdEvent extends HousekeepingHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int userId = this.packet.readInt();

        if (userId <= 0) {
            this.client.sendResponse(new HousekeepingUserDetailComposer(null));
            return;
        }

        // HabboManager.getHabboInfo(int) returns the in-memory record for
        // online users and falls through to the offline SQL lookup
        // otherwise, so a single call covers both branches.
        HabboInfo info = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);

        this.client.sendResponse(new HousekeepingUserDetailComposer(info));
    }
}
